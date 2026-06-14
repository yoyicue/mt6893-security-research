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

The first command-lifetime close-race pass is also implemented. It proves that
`system_app` can leave residual in-flight APUSYS commands by closing the fd
after `run_cmd_async`, but the tested completed and timeout windows did not
produce a crash, KASAN report, panic, or kernel-pointer copyback.

This handoff note is now the APUSYS closure artifact. `APUNN_SETTINGS_ABI.md`
stays as the long-form ABI/evidence log; new APUNN facts belong here only when
they change a kernel primitive decision.

## Kernel primitive triage

The direct ioctl work is past reachability. Current risk is concentrated in
whether APUSYS midware returns attacker-useful kernel state or mishandles
command lifetime; broad APUNN parser matrices should not be the default next
step.

| Track | Current decision | Evidence |
|---|---|---|
| Firmware interaction | Keep as a stable trigger, not the primary primitive | `settings5/no-settings` completes from `system_app`, with exact `0xb70` VPU request size, five native descriptors, settings `0x5 -> 0x7`, output write, and standard data descriptor cleanup |
| Completed command-buffer copyback leak | De-prioritize | Full `0xb70` before/after diff changes only scalar tail state (`request+0xb60`, and preload-slot `request+0xb68 = 1`); no kernel pointer, slab address, imported-buffer IOVA, or pointer-shaped copyback |
| Completed output/writeback leak | De-prioritize for synthetic payloads | Output is bounded by `settings+0x08`; tested data payload patterns and data/plane target windows do not flow into output; descriptor-slot deltas behave like provider status words |
| Timeout/abort lifetime | Remaining kernel-side candidate, currently low confidence | fd close after async dispatch reliably reaches residual command teardown, including the timeout shape, but the first completed and timeout windows show no oops/KASAN/panic and no stale object copyback |
| Service-wrapper path | Supportive, not a blocker for kernel primitive triage | Direct ioctl already reproduces the wrapper-shaped completed request. A positive wrapper dump is useful for real NN binding semantics, but not required to classify completed-path copyback or the first teardown race |
| Concurrent submissions | Next cheap kernel experiment if more time is spent | Two in-flight commands sharing one imported IOVA can test scheduler/memory lifetime without adding more firmware parser uncertainty |

## Firmware-visible boundary for primitive work

IDA and runtime evidence now place the direct ioctl trigger at the final
kernel/firmware boundary. The firmware does not receive the original userland
`struct vpu_request`; the VPU driver validates the `0xb70` request, copies
`request+0x50` descriptors into a kernel-owned D2D command buffer, writes the
copied descriptor-array IOVA and settings tuple to VPU MMIO, and dispatches
`apu_lib_apunn` through the Preload / `D2D_EXT` path on this build.

| Firmware-visible input | Kernel/user source | Primitive relevance |
|---|---|---|
| D2D command id `0x24` | `vpu_execute()` misses the Normal set, ORs bit `2` into `request+0x28`, then retries the Preload set | The current Java trigger reaches the same Preload/D2D_EXT class that runtime logs show; caller-supplied bit `2` is mainly a slot/lifetime selector |
| D2D_EXT entry / IRAM | Kernel-selected Preload object from packed firmware metadata: `INFO16 = pre->a.mva + pre->a.entry_off`, `INFO19 = pre->a.iram_mva` | Userland selects the `apu_lib_apunn` key, but does not directly control the firmware entry MVA or optional IRAM MVA |
| `INFO12` / buffer count | `request+0x35`, bounded below `0x21` by the provider gate | `buffer_count=0` suppresses descriptor-following state writeback; the completed wrapper replay uses `5` |
| `INFO13` / descriptor-array IOVA | Kernel copy of `request+0x50 + i*0x40`, not the original user buffer | Descriptor slot order and descriptor-0 target explain the observed status/writeback deltas; this is a real firmware input but not yet an arbitrary write primitive |
| `INFO14/INFO15` / settings tuple | `request+0x40` and `request+0x38` | The completed wrapper-shaped request clears this tuple, so it is not the required completion path for the target wrapper replay |
| Descriptor-backed DSP command/settings buffer | Five native descriptors all point at the same imported DSP command/settings buffer | This is the current stable completion trigger: settings `0x5 -> 0x7`, standard data descriptor pointer clear, and bounded output fill |
| Output/data descriptor sections | APUNN settings fields inside the descriptor-backed DSP buffer | `settings+0x08` bounds output fill; tested data payloads, data targets, and plane windows do not flow into source-sensitive output |
| APUSYS command-buffer copyback | `mdw_cmd_sc_clr_hnd()` copies the provider-updated temporary `0xb70` request back to user-mapped command memory | Completed copies currently expose only scalar tail state (`request+0xb60`, preload slot in `request+0xb68`), not kernel pointers or imported-buffer IOVAs |

The primitive interpretation is therefore narrow. APUNN is a stable firmware
executor and completion oracle, but the demonstrated mutable channels are
bounded output, settings state, descriptor-following status words, and APUSYS
request tail state. More opcode/operand matrices are useful only if they create
source-sensitive output, change host copyback contents, or alter async command
lifetime.

## Firmware image acquisition status

The raw `apu_lib_apunn` firmware parser is not recovered yet. The current model
is kernel-handoff accurate, but firmware-internal request interpretation still
depends on acquiring a readable VPU preload image.

Kernel source and device evidence now narrow the acquisition path:

- `vpu_init_bin()` maps the VPU binary from device-tree properties
  `bin-phy-addr`, `bin-size`, `img-head`, and `pre-bin`; this build does not
  expose `apu_lib_apunn` as a standalone `/vendor/firmware` file.
- The connected target exposes `cam_vpu1_a`, `cam_vpu2_a`, `cam_vpu3_a` and
  matching `_b` slots under `/dev/block/by-name`; those are the current static
  partition candidates for the packed VPU image.
- The live reserved-memory candidate is
  `/sys/firmware/devicetree/base/reserved-memory/mblock-18-vpu_binary`.
- The current `user=1000` shell cannot read the partition block nodes,
  reserved-memory properties, `/proc/vpu/vpu_memory`, or `/proc/vpu/vpu*/mesg`,
  and `su` is not available. This is an acquisition blocker, not a parser
  blocker.

`tools/parse_vpu_image.py` scripts the next static step once a readable dump is
available. It accepts either one merged VPU image or split `cam_vpu*` images in
LK merge order, parses the packed `struct vpu_image_header` array, walks
`struct vpu_pre_info`, locates `apu_lib_apunn`, computes the PROG entry offset
from `pAddr - (start_addr & 0xffff0000)`, and carves the declared PROG/IRAM
ranges.

Merged dump:

```
13-apusys-ioctl-surface/tools/parse_vpu_image.py cam_vpu.bin \
  --auto --algo apu_lib_apunn --json apunn_preload.json \
  --carve-dir apunn_carve
```

Split partition dumps:

```
13-apusys-ioctl-surface/tools/parse_vpu_image.py \
  cam_vpu1_a.bin cam_vpu2_a.bin cam_vpu3_a.bin \
  --auto --algo apu_lib_apunn --json apunn_preload.json \
  --carve-dir apunn_carve
```

The parser is verified on a synthetic preload-header sample, including split
image concatenation. The goal is not complete until a real readable VPU image
is parsed or another source provides the raw `apu_lib_apunn` payload.

Practical next step: run at most one focused kernel-lifetime batch before
closing this surface for now. Use timeout/abort copyback diff plus a small
close-delay loop (`0/10/50/100/500/1000 ms`), then optionally two concurrent
commands sharing the same imported IOVA. A crash, KASAN report, stale command
completion after destroy, pointer-shaped copyback, or freed-IOVA DMA symptom
would reopen the primitive. Clean residual teardown keeps APUSYS at
"reachable firmware execution without demonstrated kernel primitive".

## Priority 1 — command buffer copyback pointer scan

The first full `0xb70` request diff is now implemented in
`--run-cmd-vpu-xrp-target-settings5-no-settings-cmd-copyback-diff-iova`.
It snapshots the dmabuf-backed VPU request before dispatch, waits for the
completed shape, then prints changed dwords/qwords with simple IOVA/pointer
classification. The mode runs both the normal request and the request-flag
`0x4` preload-slot variant. The matching control mode skips dispatch and proves
the diff is execution-induced.

Observed copyback:

| Shape | Dispatch/wait | Full request diff | Interpretation |
|---|---|---|---|
| control, flags `0` | no dispatch | no changed dwords/qwords | Snapshot/diff is stable without provider execution |
| control, flags `0x4` | no dispatch | no changed dwords/qwords | Preload flag alone does not mutate the command buffer |
| completed `settings5/no-settings`, flags `0` | `run_async=0`, `wait=0` | only `request+0xb60: 0 -> 0xd965d` | Low-32 scalar tail status/provider state; no pointer-shaped value |
| completed `settings5/no-settings`, flags `0x4` | `run_async=0`, `wait=0` | `request+0xb60: 0 -> 0x1411eb`, `request+0xb68: 0 -> 1` | Low-32 scalar tail state plus slot-like word; no pointer-shaped value |

The current full-diff result does **not** show a kernel heap pointer, slab
address, imported-buffer IOVA, or other direct info leak in the VPU request
copyback. It confirms that `mdw_cmd_sc_clr_hnd` returns small provider/tail
state words to user-mapped command memory, with `request+0xb68` reflecting the
preload-slot path.

`mdw_cmd_sc_clr_hnd` (IDA: `0xffffffc008791ab0`) copies a provider-updated
temporary kernel buffer back to the command buffer KVA after provider opcode 4
returns. The command buffer is user-mapped through the dmabuf fd passed in
`run_cmd` user arg `+0x08`, so Java can read the full `0xb70` region back.

Result files:

- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_cmd_copyback_diff.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_cmd_copyback_diff_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_cmd_copyback_diff_control.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_cmd_copyback_diff_control_kernel.txt`

Next copyback work should target timeout/abort paths, because normal completed
provider return currently copies back only scalar state.

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

### Test shape

The implemented close-race mode follows this shape:

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

### Implemented probe

`--run-cmd-vpu-xrp-close-race-iova` now runs both cases with a temporary
`/dev/apusys` fd per case. After successful `run_cmd_async`, the probe skips
`wait_cmd`, sleeps for the configured delay, closes that APUSYS fd, keeps the
HardwareBuffers alive, then dumps the imported IOVA buffer and command buffer.
Because the fd is intentionally closed, the probe skips explicit APUSYS
`mem_free` ioctls and lets `mdw_usr_destroy` own residual command/memory
teardown.

| Case | Shape | Close timing | User-visible result | Kernel signal | Interpretation |
|---|---|---|---|---|---|
| A | completed `settings5/no-settings` | close 100 ms after `run_async=0`, dump after 5 s | output completes: settings `0x5 -> 0x7`, output filled, request tail `+0xb60 = 0xe699a` | `mdw_usr_destroy residual cmd(0xffffff8018275000)`, residual mem for original IOVA and cmd IOVA; no oops/KASAN/panic | fd close races with an in-flight or just-finished command, but this completed path is cleaned up without visible UAF |
| B | timeout/minimal split ANN_VERSION | close 500 ms after `run_async=0`, dump after 15 s | settings/output mostly unchanged, command request has `result_status=0x2`, tail `+0xb60 = 0x215606bb`, `+0xb64 = 0x2` | `mdw_usr_destroy residual cmd(0xffffff8018705000)`, residual cmd mem; no oops/KASAN/panic | abort/timeout cleanup reaches user-mapped cmd status without a visible dangling scheduler use in this single run |

This first pass moves the lifetime finding from "theoretical race" to
"reachable residual command teardown". The tested windows do not confirm a UAF.
If this path is pursued further, vary close delay (`0/10/50/100/500/1000 ms`)
and loop the timeout shape while collecting less-filtered scheduler/VPU logs.

Result files:

- `poc-run-results/2026-06-15-batch/13_apusys_xrp_close_race.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_xrp_close_race_kernel.txt`

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
