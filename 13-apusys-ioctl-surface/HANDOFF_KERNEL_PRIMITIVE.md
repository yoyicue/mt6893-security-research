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

## Current APUNN request interpretation model

This is the strongest model available before recovering the raw
`apu_lib_apunn` firmware body. It combines kernel handoff, wrapper static
analysis, and direct runtime matrices; fields not listed here are not proven
firmware semantics yet.

| Layer | Field / structure | Current interpretation |
|---|---|---|
| Native VPU request | `request+0x04` algo string | Kernel lookup key. `apu_lib_apunn` misses the Normal set and is retried through the Preload set; firmware receives the selected Preload entry/IRAM registers, not the original string as parser input. |
| Native VPU request | `request+0x28` flags | Kernel execution selector. Bit `2` selects the Preload/slot path and changes `request+0xb68` copyback, but the tested direct trigger also reaches Preload after Normal lookup miss with flags `0`. |
| Native VPU request | `request+0x35` `buffer_count` | Firmware-visible liveness input through `INFO12`. `0` suppresses descriptor-following state writeback and returns `-EIO`; nonzero tested counts reach the descriptor path. The stable wrapper replay uses `5`. |
| Native VPU request | `request+0x38/+0x40` settings length/IOVA | Firmware-visible through `INFO15/INFO14` when libvpu property mode is used. The target wrapper replay completes with both clear, so the required APUNN command/settings buffer is carried by native descriptors instead. |
| Native VPU request | `request+0x50 + i*0x40` `struct vpu_buffer[]` | Firmware-visible copied descriptor array through `INFO13`. In incomplete shapes, descriptor slot `0` determines the visible status/writeback target. The completed target shape points all five descriptors at the same DSP command/settings buffer. |
| Native descriptor | `port_id`, `format`, `plane_count`, `height` | Accepted metadata in the tested matrices, not hard role or completion gates for the current oracle. |
| Native descriptor | plane MVA/IOVA | Actual firmware-accessible target window. Descriptor-slot ordering, not the settings output pointer, explains the earlier code/output/plane first-word writebacks. |
| DSP settings buffer | `settings+0x00` flags | Completion state. The completed replay changes `0x5 -> 0x7`, satisfying the host predicate `(flags & 0x0a) == 0x02`; pre-seeding flags alone does not trigger completion. |
| DSP settings buffer | `settings+0x04` code size | Real APUNN code-section acceptance gate. Sizes `0/4/8/0xc/0x10` fail; `0x11` is the smallest tested completed size. |
| DSP settings buffer | `settings+0x08` output size | Maximum output-fill bound. Larger values allow longer completion-style output fill; small values leave initialized output-header words intact. |
| DSP settings buffer | `settings+0x0c/+0x30` data descriptor size/pointer | Data-descriptor section contract. Standard one-entry size `0x0c` is consumed and clears `settings+0x30`; larger/two-entry cases remain target/order-sensitive and do not copy tested payload bytes into output. |
| Xtensa code section | operation entry fields | Wrapper debug/static evidence maps stride at code `+0x04`, opcode at entry `+0x00`, operand-list offset at `+0x08`, input/output counts at `+0x0c/+0x10`, and operand ids at `entry+0x48+operand_off`. Runtime confirms these fields are accepted by the completed parser. |
| Xtensa code section | opcodes `10001..10009` | In the completed settings-backed shape, all tested opcodes complete. `10004` fills output through `0x3c`; the others fill through `0x40`. The earlier `10005..10007` timeout/error class was descriptor-target dependent, not the completed opcode contract. |
| Xtensa code section | operand ids / operand offsets / op counts | Accepted metadata under the current oracle. Tested operand ids `0/1/2/3/0xffff`, offsets `0/0x10/0x40/0x100/0x17e/0x180`, and count tuples all complete; offset `0x180` is just outside the advertised `0x1c8` operation entry and is not a visible bounds gate here. |
| Output/data windows | output fill and data payloads | Output currently looks like deterministic completion data with tail variability. Tested data payload patterns and target windows are preserved and do not produce source-sensitive output. |
| Command copyback | `request+0x34`, `+0xb60`, `+0xb68`, `+0xb6c` | Provider/kernel completion state copied back by midware. Current completed diffs expose scalar status/timing/slot-like words only, not kernel pointers or imported-buffer IOVAs. |

The raw firmware is still required for the exact internal parser: opcode switch
targets, per-opcode output schemas, true operand bounds checks, descriptor
cleanup state machine, and any source-buffer binding paths not exercised by the
current `XTENSA_ANN_VERSION`-style replay.

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

The highest-risk P3 race is **explicit APUSYS mem_free of an imported IOVA
while a VPU command that references that IOVA is still inside provider opcode
4**. This is sharper than generic "two commands share one IOVA": `mem_free`
does not abort or wait for commands, and the command/sc objects do not hold a
reference to the imported `mdw_mem` object whose IOVA is embedded in the VPU
request/settings descriptors.

Evidence chain:

- `mdw_usr_mem_free()` removes the matching object from `u->mem_list` and
  deletes the IDR handle under `u->mtx`, then calls `mdw_usr_mem_delete(mm)`.
  It does not scan `u->cmds_idr`, the scheduler queue, or in-flight
  subcommands before releasing the memory object.
- For imported type-2 memory, deletion reaches `mdw_mem_unimport()` →
  `mdw_mem_ion_unmap_iova()` → `ion_free(khandle)`. There is no in-flight VPU
  DMA guard in that path.
- `mdw_cmd_create_sc()` maps the APUSYS command buffer KVA and copies/parses the
  subcommand, but descriptor/settings IOVAs inside the `0xb70` VPU request are
  just firmware-visible values. The sc/cmd lifetime references protect the
  command object, not every imported IOVA referenced by APUNN descriptors.
- `mdw_sched_dev_routine()` calls provider opcode `4` with the copied provider
  buffer and only runs `clr_hnd` / completion after the provider returns. During
  the VPU wait window, a second user thread can still issue `mem_free` on the
  same `/dev/apusys` fd because the memory free path is separate from
  `wait_cmd`.

Risk ranking after the first timeout sweep:

| Rank | Race shape | Why it is interesting | Expected signal |
|---:|---|---|---|
| 1 | `run_cmd_async(completed settings5/no-settings)` → `mem_free(settings/output/data IOVA)` at `0/1/5/10/50 ms` → delayed dump/wait | Largest current race window: the request is known to complete and write back through the same imported IOVA, so `mem_free` races an actual provider writeback path rather than a timeout-only path. | stale writeback into a freed/reused dmabuf, VPU/IOMMU/devapc fault, wait result changing from success to `-EIO`, oops/panic |
| 2 | `run_cmd_async(timeout shape)` → immediately `mem_free(settings/descriptor IOVA)` → delayed `wait_cmd` or fd close | Proves the kernel allows explicit free of the exact IOVA used by `INFO13` descriptors while the command is outstanding, but the malformed timeout request does not force useful post-free writeback. | controlled timeout/EIO, new VPU warning, IOMMU fault only if firmware still touches the freed IOVA |
| 3 | Two `run_cmd_async` commands on the same fd, both referencing the same imported IOVA, then `mem_free` after the first dispatch window | Amplifies rank 1 by keeping one command queued/running while another may finish or timeout; tests scheduler ordering plus shared-IOMMU lifetime. | one command completes and the other faults, residual cmd count > 1, list/refcount warning, freed-IOVA DMA symptom |
| 4 | Two commands sharing IOVA, no explicit `mem_free`, then close fd without waits | Exercises residual command abort and mem teardown together, but fd close already calls `abort_cmd` before residual memory cleanup. Current close-race runs make this lower confidence than explicit mem_free. | residual cmd/mem ordering, UAF only if abort fails to serialize provider return |

Rank 1 timeout-shape experiment is now implemented as
`poc/ApusysIoctlProbe.java --run-cmd-vpu-xrp-mem-free-race-iova`. It runs the
same timeout-prone minimal/split `XTENSA_ANN_VERSION` APUNN request with
`mem_free(shared_iova)` after `0/10/50/100/500 ms`, keeps the HardwareBuffers
alive for the timeout window, then calls `wait_cmd`.

Observed from `system_app` on 2026-06-15:

```
result=poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_iova.txt
kernel=poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_iova_kernel_relevant.txt

free_after=0ms:   run_async=0, mem_free=0, request result_status=0x2, wait=-EIO
free_after=10ms:  run_async=0, mem_free=0, request result_status=0x2, wait=-EIO
free_after=50ms:  run_async=0, mem_free=0, request result_status=0x2, wait=-EIO
free_after=100ms: run_async=0, mem_free=0, request result_status=0x2, wait=-EIO
free_after=500ms: run_async=0, mem_free=0, request result_status=0x2, wait=-EIO
```

Kernel-side signal for the run is controlled provider failure: repeated
`request (D2D_EXT) timeout, priority: 0, algo: apu_lib_apunn`,
`mdw_sched_trace ... ret(-110)`, and `mdw_wait_cmd ... fail`. No `devapc`,
IOMMU fault, panic/Oops, or `BUG` line is present in the preserved filtered log.

Interpretation: the lifetime gap is real enough to measure because the same fd
can successfully `mem_free` the imported shared IOVA while the submitted VPU
command is still outstanding. The timeout/minimal shape does not by itself turn
that gap into a kernel fault or visible stale writeback on this device; it
degrades to provider timeout and `wait_cmd=-EIO`. This closes only the
timeout-shaped subcase; it does not close P3.

Rank 1 experiment shape:

```
mem_create(type2, shared_hwb_fd) -> shared_iova
build timeout-prone APUNN/VPU request whose descriptor/settings point at shared_iova
mem_create(type2, cmd_hwb_fd) -> cmd_iova
run_cmd_async(cmd_fd) -> ret 0, cmd_id
sleep 0/10/50/100/500 ms
mem_free(shared_iova handle)          // same fd, command still in-flight
sleep until VPU timeout/completion window
wait_cmd(cmd_id) or close(fd)
dump shared HardwareBuffer and cmd buffer; collect kernel log
```

### Rank 1 completed-shape variant (current experiment)

The timeout-shape result above is expected to be negative: firmware times out
without producing a DMA write, so freeing the IOVA during the timeout window
does not race with an actual data store. The **completed `settings5/no-settings`
shape** is the right current variant because firmware actually writes to the
descriptor-backed buffer (settings `0x5 → 0x7`, output fill) within the fast
completion window.

Implemented mode:

```
poc/ApusysIoctlProbe.java --run-cmd-vpu-xrp-mem-free-race-completed-iova
```

The race hypothesis: `mem_free` releases the IOVA mapping while APUNN firmware
is still performing its completion writes (settings flag flip, output fill,
`settings+0x30` clear). If the IOMMU unmap wins, the firmware DMA hits an
unmapped page and faults. If the firmware write wins but the kernel-side
`mdw_cmd_sc_clr_hnd` copyback reads from a freed/reused dmabuf page, the
copyback may contain stale or attacker-controlled data.

```
mem_create(type2, shared_hwb_fd) -> shared_iova
build settings5/no-settings completed-shape request
  (five descriptors at shared_iova, code_size=0x48, output_size=0x40,
   one standard data descriptor, opcode 10003)
mem_create(type2, cmd_hwb_fd) -> cmd_iova
run_cmd_async(cmd_fd) -> ret 0, cmd_id
sleep 0/1/5/10/50 ms               // tight delays — completion is fast
mem_free(shared_iova handle)        // free while firmware may still be writing
sleep 1000 ms                       // let completion or fault propagate
wait_cmd(cmd_id)
dump shared HardwareBuffer and cmd buffer; collect kernel log
```

Why shorter delays than the timeout variant: the completed shape finishes in
well under 1 second. The interesting window is the first ~50 ms after dispatch
where firmware is actively writing. Use `0/1/5/10/50 ms` instead of the
`0/10/50/100/500 ms` timeout-shape sweep.

What to look for in the result:

| Signal | Meaning |
|---|---|
| `wait_cmd = 0` and settings `0x7` | completion won the race; `mem_free` happened after firmware finished — not useful |
| `wait_cmd = -EIO` and settings still `0x5` | `mem_free` landed during or before firmware write; check if output/settings window shows partial/corrupt fill |
| IOMMU fault or `devapc` in kernel log | firmware DMA hit unmapped page — confirms the IOVA lifetime gap is exploitable |
| `request+0xb60` or other copyback fields show pointer-shaped values | `clr_hnd` read from freed/reused page — info leak through the race |
| kernel panic/oops/KASAN | UAF confirmed |
| clean `-EIO` with no fault, no pointer, settings unchanged | `mem_free` succeeded but unmap completed before firmware touched the page — race exists but window is too narrow at this delay |

Observed from `system_app` on 2026-06-15:

```
result=poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_iova.txt
kernel=poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_iova_kernel_relevant.txt

free_after=0ms:  run_async=0, mem_free=0, settings=0x7, wait=0
free_after=1ms:  run_async=0, mem_free=0, settings=0x7, wait=0
free_after=5ms:  run_async=0, mem_free=0, settings=0x7, wait=0
free_after=10ms: run_async=0, mem_free=0, settings=0x7, wait=0
free_after=50ms: run_async=0, mem_free=0, settings=0x7, wait=0
```

Kernel-side signal is clean for this sweep: the filtered log contains VPU boot
noise only, with no `devapc`, IOMMU fault, panic/Oops, `BUG`, `KASAN`, timeout,
or `mdw_wait_cmd` failure. Interpretation: the completed writeback is faster
than the Java-layer `mem_free` ioctl round-trip, even at 0 ms. The lifetime gap
still exists, but this delay-only completed sweep does not win the race.

Allocator pressure after `mem_free` is now implemented as
`--run-cmd-vpu-xrp-mem-free-race-completed-reuse-iova`; see the first
additional candidate below for the result.

Kernel log terms to preserve unfiltered: `mdw_usr_mem_free`,
`mdw_mem_ion_unmap_iova`, `ion_free`, `request (D2D_EXT) timeout`,
`mdw_usr_destroy residual cmd`, `devapc`, `iommu`, `Unable to handle kernel`,
`BUG`, and `KASAN`.

## Additional attack surface candidates

The following four directions are outside the current APUNN completion/lifetime
focus but exploit other APUSYS ioctl paths from the same `system_app` context.
They are ranked by proximity to a kernel primitive.

### 1. Allocator-reuse pressure on freed IOVA (extends rank 1)

The completed `mem_free` race above showed that the delay-only sweep loses the
race. The direct escalation is allocator pressure: after `mem_free`,
immediately `mem_create` new type-2 imports to reclaim the freed IOVA backing
pages, then check whether the still-in-flight firmware completion writes
(`settings 0x5 -> 0x7`, output fill) land on a **new** buffer instead of the
original.

If the IOMMU mapping is torn down but the physical page is reused before
firmware finishes, this turns the lifetime gap into either:
- a **cross-buffer write** (firmware writes completion data into attacker's
  fresh buffer — confused deputy), or
- a **stale-page read** if `mdw_cmd_sc_clr_hnd` copyback reads from the
  replacement page.

Implemented mode:

```
poc/ApusysIoctlProbe.java --run-cmd-vpu-xrp-mem-free-race-completed-reuse-iova
```

Experiment shape:

```
run_cmd_async(completed settings5/no-settings)
sleep 0/1/5 ms
mem_free(shared_iova)
immediately mem_create(type2, replacement_hwb_fd) x 4
sleep 1000 ms
dump original buffer, replacement buffers, then wait_cmd
```

Observed from `system_app` on 2026-06-15:

```
result=poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_reuse_iova.txt
kernel=poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_reuse_iova_kernel_relevant.txt

free_after=0ms:
  original_iova=0xfd2c1000
  replacement_iovas=0xfd2cd000,0xfcf71000,0xfd361000,0xfd385000
  exact_reuse=0/4, original settings=0x7, wait=0

free_after=1ms:
  original_iova=0xfd371000
  replacement_iovas=0xfd375000,0xfcf55000,0xfcf45000,0xfcf69000
  exact_reuse=0/4, original settings=0x7, wait=0

free_after=5ms:
  original_iova=0xfcf51000
  replacement_iovas=0xfcf2d000,0xfcf39000,0xfcf29000,0xfcf49000
  exact_reuse=0/4, original settings=0x7, wait=0
```

All replacement buffers kept their marker/header (`0x52555030 + index`) and
zeroed output windows after the completion wait. No old APUNN completion write
landed in a replacement buffer. Kernel-side signal was clean: the filtered log
contains one VPU boot line and no `devapc`, IOMMU fault, panic/Oops, `BUG`,
`KASAN`, timeout, or `mdw_wait_cmd` failure.

Interpretation: this remains the largest APUSYS risk shape because exact IOVA
reuse would turn the lifetime gap into a cross-buffer write. On this run, the
allocator did not hand the freed IOVA back to the immediate same-size
replacement imports, and the firmware completion path still finished cleanly.
Current evidence does not show a reusable kernel primitive from this shape.

### 2. `dev_ctrl` (ioctl `0x400C4109`) during in-flight VPU command

`mdw_usr_dev_ctrl_4109` reaches `core+0x70` → `mdw_rsc_dev_op0_ctrl` →
provider handler opcode `0` with timeout `0xbb8`. For VPU, opcode `0` is
`vpu_send_cmd_op0` — a power-on/control path.

Hypothesis: issuing `dev_ctrl` while a VPU command is executing may force a
power cycle, reset, or state flush that collides with the in-flight D2D_EXT
provider call. The scheduler holds `sc+0xF8` during provider opcode 4, but
`dev_ctrl` goes through a separate ioctl path that does not acquire the sc
mutex.

Experiment:
```
run_cmd_async (timeout or completed shape)
sleep 10 ms
dev_ctrl(device=3, core=0)   // ioctl 0x400C4109
collect kernel log: timeout/reset/fault/residual interleaving
```

Signals: VPU reset during active D2D causing IOMMU fault, stale completion
after reset, scheduler state confusion (done callback on a reset core).

### 3. `ucmd` opcode-7 side effects beyond algorithm lookup

The current `ucmd` evidence shows opcode `7` reaches `vpu_alg_get` (refcount
increment) then `vpu_alg_put` (decrement) and returns. But the full opcode-7
branch at `0xffffffc0087a093c` has not been exhaustively traced past the
refcount path.

Questions:
- Does repeated `ucmd` with `apu_lib_apunn` key leak refcount (no matching
  `put` on error paths)? A refcount overflow or underflow on the algorithm
  object at `+0x103c` would corrupt its list node at `+0x1040`.
- Does `ucmd` with a crafted payload past offset `+0x04` (the key) reach any
  firmware control path? The current 0x24-byte `libvpu.so` ABI only uses
  `+0x00` (magic) and `+0x04` (key), but the kernel maps the full
  `user+0x10` requested length into KVA.

Experiment: loop `ucmd` with `apu_lib_apunn` key 10000× and check
`/proc/slabinfo` or kernel log for refcount warnings. Then try `ucmd` with
length `0x100` and non-zero bytes past offset `+0x24` to see if the provider
reads beyond the key.

Low-cost smoke test; not expected to be a direct primitive but could expose an
integer overflow or unbalanced refcount.

### 4. Secure alloc/free (`mdw_usr_dev_sec_alloc` / `mdw_usr_dev_sec_free`)

`mdw_usr_dev_sec_alloc` at `0xffffffc00878c00c` and `mdw_usr_dev_sec_free` at
`0xffffffc00878c418` are separate ioctl paths. `sec_free` has an
`id < 0x40` guard, suggesting a fixed-size table. If the alloc/free pair does
not properly serialize with VPU command dispatch or memory import, it could
corrupt the secure-resource table.

This has the least evidence of the four. Run a single alloc/free cycle from
`system_app` to confirm reachability (it may be gated by SELinux or a
capability check), then decide whether to pursue.

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
