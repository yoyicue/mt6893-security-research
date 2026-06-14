# APUSYS kernel primitive — handoff notes

Date: 2026-06-15

## Current status

The stable trigger is the `settings5/no-settings` shape. It reaches APUNN
completion from `system_app`: submit and wait return `0`, settings flags change
`0x5 -> 0x7`, settings `+0x30` is cleared for the standard one-entry data
descriptor, the code window stays unchanged, and the output section is written.

The completion ABI is now mapped far enough to use as a repeatable kernel-path
trigger:

- outer APUSYS VPU `cb_info_size` must be exactly `0xb70`
- normal wrapper-style request uses five native descriptors pointing at the DSP
  command/settings buffer, with `request+0x38/+0x40` clear
- `settings+0x04` is a real code-section acceptance gate:
  `0/4/8/0xc/0x10` fail, `0x11` is the smallest tested completed size
- `settings+0x08` bounds the maximum output fill
- standard `data_desc_size=0x0c` clears `settings+0x30`; two-entry data
  descriptors keep it nonzero with target/order-sensitive flag cleanup
- opcodes `10001..10009`, output operand ids `0/1/2/3/0xffff`, input/output
  count combinations, output sizes, data descriptor sizes, explicit data
  entries, target sections, payload patterns, and operand-list offsets
  `0/0x10/0x40/0x100/0x17e/0x180` all complete under the current oracle
- `0x180` places the operand word just outside the advertised `0x1c8` operation
  entry and still completes, so operand-list offset is not a visible completion
  or bounds gate for `XTENSA_ANN_VERSION`

Current negative primitive evidence:

- completed output does not reflect tested input data payload patterns
- data/plane payload windows are preserved in data-entry/data-target matrices
- descriptor-slot writebacks are firmware/provider-decided status words, not an
  arbitrary write primitive
- IOMMU limits VPU DMA targets to mapped/imported buffers

The remaining kernel primitive gap is therefore in **midware copyback,
information leakage, and command lifetime**, not in basic APUNN completion
reachability. Firmware-side work should now be limited to experiments that feed
the kernel primitive questions directly, such as wrapper-generated real
operation bindings.

## Priority 1 — command buffer copyback pointer scan

Current probes already dump the APUSYS command header plus the VPU request
head/tail from the dmabuf-backed command buffer. In completed
`settings5/no-settings` runs, the request head and summary are stable, while
the copied-back tail gains a nonzero word around `request+0xb60`; `request+0xb68`
usually remains zero in the normal completed shape. This is not yet a full
`0xb70` diff or pointer classification pass.

`mdw_cmd_sc_clr_hnd` (IDA: `0xffffffc008791ab0`) copies a provider-updated
temporary kernel buffer back to the command buffer KVA after provider opcode 4
returns. The command buffer is user-mapped through the dmabuf fd passed in
`run_cmd` user arg `+0x08`, so Java can read the full `0xb70` region back.

### What to do

Add a full command-buffer diff mode for the completed `settings5/no-settings`
shape:

1. Before `run_cmd_async`: dump the full `0xb70` request region from the
   command buffer, not only the current head/tail windows.
2. After `wait_cmd` returns `0`: dump the same region again.
3. Print changed dwords/qwords with offsets and classify:
   - low 32-bit IOVA-like values in the imported HardwareBuffer range
   - kernel-pointer-shaped values
   - provider result/status fields
   - slot/core/priority words around `request+0xb5c..0xb70`
4. Repeat once with the preload-slot request flag that previously produced a
   tail word at `request+0xb68`, because that path may expose different slot
   state.

If the copyback region contains a kernel heap pointer or a slab address, that
is a direct info leak reachable from `system_app`.

Current known command-buffer copyback evidence:

- completed `settings5/no-settings` runs: request head and summary stay stable;
  tail word at `request+0xb60` becomes nonzero; `request+0xb68` stays zero
- preload-slot variant: tail gains a slot-like copyback word at `request+0xb68`
- older same-target XRP run: request head/tail unchanged, which separates
  command-buffer copyback from imported-buffer descriptor writeback

### IDA reference

```
mdw_cmd_sc_set_hnd (0xffffffc00879163c):
  allocates temp buffer, copies sc codebuf into it, passes to provider

mdw_cmd_sc_clr_hnd (0xffffffc008791ab0):
  memcpy(provider_arg+0x38, sc+0x28_bytes, temp_buffer)  → back to cmd KVA
  kfree(temp_buffer)

The copy size is sc+0x28, which for VPU is 0xb70.
```

## Priority 2 — timeout/abort lifetime probe

### The race

```
Thread A (user):                    Thread B (scheduler):
  run_cmd_async → ret 0
  close(fd)
    → mdw_usr_destroy
      → walk user_ctx+0x60 list
      → mdw_cmd_abort_cmd(cmd)     ← provider opcode 4 still running
        → sets cmd+0x198 = 1          on the same sc object
        → walks cmd+0x50 sc list
        → calls sc cleanup
      → mdw_cmd_delete_cmd(cmd)
        → kfree(cmd)               ← scheduler thread still holds
                                      a pointer to freed cmd/sc
```

`mdw_cmd_abort_cmd` locks `cmd+0x1B0` and sets an abort flag, but the
scheduler's `mdw_sched_dev_routine` calls the provider with `W0=4` at
`0xffffffc008792284` — the provider call itself (VPU execute with ~1s firmware
timeout) holds no cmd-level lock.

### What to do

Write a new probe mode, e.g. `--run-cmd-close-race`:

**Case A — completed shape, immediate close:**
```
mem_create(type2, hwb_fd) → iova
build settings5/no-settings cmd buffer
mem_create(type2, cmd_fd)
run_cmd_async → ret 0
// do NOT call wait_cmd
Thread.sleep(100)   // let scheduler pick it up
close(fd)           // triggers mdw_usr_destroy
Thread.sleep(5000)  // let VPU finish or timeout
// check kernel log for oops/KASAN/residual
```

**Case B — timeout shape, immediate close (the interesting one):**
```
mem_create(type2, hwb_fd) → iova
build minimal/malformed settings cmd buffer (old shape that causes D2D_EXT timeout)
mem_create(type2, cmd_fd)
run_cmd_async → ret 0
// do NOT call wait_cmd
Thread.sleep(500)   // scheduler dispatches to VPU, firmware starts ~9s timeout
close(fd)           // mdw_usr_destroy while VPU is still executing
Thread.sleep(15000) // wait for VPU timeout + scheduler cleanup
// check kernel log
```

Kernel log signals to classify:
- `Unable to handle kernel paging request` — UAF confirmed
- KASAN: `use-after-free in mdw_sched_dev_routine` — UAF confirmed
- `mdw_usr_destroy residual cmd` followed by `mdw_cmd_done` on same cmd id —
  indicates destroy ran before scheduler finished, potential dangling pointer
- Normal "residual cmd" then clean exit — abort path's locking is sufficient,
  UAF does not exist for this window

Even without a crash, the timing relationship between "residual cmd" and
"D2D_EXT timeout" / completion logs tells us whether abort waits for provider
completion or races past it.

### IDA reference

```
mdw_usr_destroy:        0xffffffc00878cfc4
mdw_cmd_abort_cmd:      0xffffffc0087907bc  (locks cmd+0x1B0)
mdw_sched_dev_routine:  0xffffffc0087920ec  (locks sc+0xF8, calls provider)
mdw_cmd_end_sc:         0xffffffc008791290  (completion callback from scheduler)
mdw_cmd_delete_cmd:     0xffffffc0087905e8  (frees cmd object)
```

## Priority 3 — concurrent command submission (exploratory)

Submit multiple `run_cmd_async` on the same fd without waiting:

- Two commands referencing the same IOVA import. If the first command's cleanup
  path (`mdw_mem_ion_unmap_iova`) releases the IOVA mapping while the second
  command's VPU DMA is still targeting that IOVA, the DMA hits freed pages.
- Three+ commands to stress the command id allocation at `user_ctx+0x60` and
  the scheduler queue. Look for list corruption or double-free in the teardown
  path.

This is lower priority because the attack surface is less clear and depends on
scheduler ordering. Start with two commands; if "residual cmd" count > 1 in
teardown, that confirms concurrent in-flight commands are reachable.

## What to stop doing

- Broad firmware parameter matrices that only test completion acceptance. They
  have produced a stable APUNN completion trigger but not a source-sensitive
  leak.
- More `XTENSA_ANN_VERSION` operand/code-format exploration unless it directly
  feeds the copyback or lifetime probes.
- Wrapper path alignment for its own sake. Keep wrapper-generated real
  operation bindings only if they can change source-sensitive output or command
  lifetime behavior.

## Object layout reference

```
APUSYS command object (allocated by mdw_cmd_create_cmd):
  +0x00   mapped KVA of command buffer
  +0x18   pointer to parent cmd context
  +0x20   mapped length
  +0x24   command id (positive, returned to user)
  +0x28   debug id
  +0x38   user context field
  +0x3C   user context field
  +0x48   sc execution bitmap
  +0x50   sc list head
  +0x60   per-user command tree node
  +0x198  abort flag (set by mdw_cmd_abort_cmd)
  +0x1A0  failed sc pointer (checked by mdw_wait_cmd)
  +0x1A8  user context pointer
  +0x1B0  cmd mutex
  +0x1D0  completion waitqueue

Subcommand (sc) object (allocated during parse, linked at cmd+0x50):
  +0x18   parent cmd pointer
  +0x28   copy size for clr_hnd
  +0x70   sc index
  +0x78   list node (in cmd+0x50)
  +0xB8   provider return code
  +0xBC   error flag
  +0xC0   available core count
  +0xD0   done flag / completion node
  +0xF8   sc mutex (locked by mdw_sched_dev_routine)
```
