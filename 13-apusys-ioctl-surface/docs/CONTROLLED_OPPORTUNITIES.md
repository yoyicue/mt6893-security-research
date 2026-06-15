# APUSYS current controllable opportunities

Date: 2026-06-15

Scope: MT6893 APUSYS direct ioctl work from `uid=1000(system)` /
`u:r:system_app:s0`. "Controllable opportunity" here means an input or lifetime
edge that the current lab setup can drive repeatably and that can still change a
kernel primitive decision. It does not mean a proven kernel read/write
primitive.

## Current bottom line

The largest current opportunity is still APUSYS command-memory lifetime:

```
run_cmd_async(completed APUNN request)
mem_free(shared_iova)
mem_create(type2, replacement dmabuf)
check whether late firmware completion writes land on replacement memory
```

This is stronger than pure delay racing because exact IOVA reuse would turn the
lifetime gap into a cross-buffer firmware write. The first simple reuse
pressure sweeps did not produce that primitive: firmware reuse pressure got
`exact_reuse=0/12`, and the standalone immediate-reuse profiler expanded this
to `exact_reuse=0/2720` across 1K, 4K, and 64K import sizes.

The profiler did show nearby allocator behavior: 4K and 64K replacements often
land one buffer below the freed IOVA (`closest_delta=-0x4000` or `-0x10000`),
but not at the exact freed IOVA.

That changed with targeted gap shaping. If a pool contains a target IOVA and
the exact lower neighbor, freeing `target` first and the lower neighbor second
can produce exact target reuse:

```
4K target_then_lower gap:  exact_target=2/448
64K target_then_lower gap: exact_target=1/96
```

The follow-up gap-control profiler repeated the result with denser 4K cases:
`p16/r16` produced `exact_target=3/1248`, `p12/r20` produced
`exact_target=4/1180`, and the first exact-hit indexes were spread across
`4,7,9,10,12,16,18`. Selecting the highest adjacent target produced no exact
target reuse in that run. This makes exact reuse repeatable, but not yet
replacement-index stable.

The firmware-coupled version of the 4K shape produced exact target reuse in two
runs, but neither produced APUNN completion bytes in the replacement. The
latest follow-up had `p16/r16 exact_target=1/208` with `completion_like=0` and
`wait=-EIO`; the `p12/r20` firmware shape had `exact_target=0/340`. This keeps
the APUSYS surface below "proven cross-buffer write", but it raises the
allocator side from "not observed" to "repeatable with target/lower shaping".

The latest follow-up runs also tested the two natural window-amplification
ideas. Two queued commands sharing the same IOVA did not produce exact
replacement reuse or stale APUNN bytes in replacement buffers. The completed
latency matrix kept all tested completed shapes in the `1..14 ms` range, with
clean kernel logs. That leaves exact-reuse firmware timing, or a lower-level
scheduler signal outside Java timing, as the only remaining ways to change this
classification.

Current practical ranking (updated 2026-06-15 after firmware iDMA timing analysis):

1. **plane-redirect + exact IOVA reuse (untested combination)**: descriptor-plane
   redirect produces ~9 s timeout/EIO write — currently the only identified firmware
   write path slower than the Java `mem_free` round-trip. Not yet combined with
   `target_then_lower` gap shaping.
2. **completed-path exact-reuse firmware timing** (demoted): firmware byte analysis
   of `0x70044b74` shows INPUT DMA is synchronous (schedule→wait consecutive FLIX128
   bundles, 9-byte gap) and OUTPUT write is likely CPU-store inside `0x700a21b8`.
   Both complete before VPU signals done. The `free_after=0ms` Java race is always
   lost. The `ann_output40` 14 ms anomaly (inverse of `ann_output100` 1 ms) is
   unexplained and may indicate a slower branch worth probing.
3. kernel-side scheduler/lifetime evidence that Java cannot observe directly,
4. `dev_ctrl` / `ucmd` side effects as lower-probability alternate ioctl paths.

## Evidence-backed controls

| Control | What is controllable | Current signal | Primitive status |
|---|---|---|---|
| `/dev/apusys` access from `system_app` | Open fd and issue reject/query/provider ioctls from app-process Java | Reject paths return controlled `EINVAL`; provider paths are reachable | Access is established |
| HardwareBuffer import | Create RGBA `HardwareBuffer`, extract dmabuf fd, import with APUSYS `mem_create2`, receive APUSYS IOVA and mem id | Stable `0x4000` imports through ioctl `0xC0384103` | Required building block |
| APUNN completed request | Build wrapper-like `settings5/no-settings` VPU request and run `apu_lib_apunn` | `run_cmd_async=0`, `wait=0`, settings `0x5 -> 0x7`, bounded output fill | Stable trigger, not a primitive by itself |
| APUNN output size | `settings+0x08` bounds the output fill | Output changes stay within requested window | Bounded firmware write, not arbitrary write |
| APUNN data descriptor shape | Standard one-entry descriptor clears `settings+0x30`; two-entry/target/order matrices change descriptor cleanup behavior | Payload bytes do not flow into output in tested matrices | Good oracle, no leak shown |
| Command-buffer copyback | Kernel/provider updates copied back to user command buffer after provider return | Full `0xb70` diff shows scalar tail state only | No kernel pointer or imported IOVA leak shown |
| In-flight `mem_free` | Same fd can `mem_free(shared_iova)` after async submit while command is outstanding | Timeout shape returns controlled `wait=-EIO`; completed shape still returns `wait=0` | Real lifetime gap, not yet won as UAF/write |
| Replacement import pressure | After `mem_free`, import same-size replacement HardwareBuffers and dump them after completion | Firmware race: `exact_reuse=0/12`; standalone profiler: `exact_reuse=0/2720`; no stale APUNN write in replacement buffers | Immediate same-size reuse is negative; superseded by target/lower gap shaping |
| Target/lower gap reuse | Import a pool, find target plus exact lower neighbor, free `target` then lower, then import replacements | Initial 4K/64K exact reuse plus control follow-up: 4K `p16/r16 exact=3/1248`, 4K `p12/r20 exact=4/1180`; hit indexes spread across several replacement slots | Exact IOVA reuse is repeatable but not index-stable |
| Firmware gap reuse | Same target/lower gap, but target is the live APUNN settings/output IOVA after `run_cmd_async` | Follow-up: 4K `p16/r16 exact=1/208`, `completion_like=0`, exact-hit wait `-EIO`; 4K `p12/r20 exact=0/340`; no IOMMU/devapc/Oops | Allocator half works; firmware write timing not won |
| Two-command shared IOVA | Submit two commands referencing one imported IOVA, free it, import replacements, then wait both commands | `completed/completed`, `completed/timeout`, and `timeout/completed` all submitted; replacement exact reuse `0/12`; completed command still writes original shared buffer; timeout command returns `-EIO` | Window amplification tested, no primitive signal |
| Completed latency variants | Change output size/opcode while keeping completed settings5/no-settings shape | `ANN_VERSION` output `0x40/0x100/0x400/0x1000`, `LOCAL_MEM_INFO`, and `GET_DETAILED_OP_INFO` all complete with wait time `1..14 ms` | No slower completed writeback window found |
| Completion write poll | Busy-poll selected settings/output/data-desc fields after `run_cmd_async` before `wait_cmd` | `ANN_VERSION` output `0x40` and `0x1000` show `changed_fields=0` during a 10 ms post-async poll, then normal completion after wait | No Java-visible pre-wait field sequencing |
| fd close teardown | Close APUSYS fd after async submit and leave residual command cleanup to `mdw_usr_destroy` | Residual teardown reachable; no crash/oops/KASAN | Lower confidence than explicit `mem_free` |
| `dev_ctrl` provider path | ioctl `0x400C4109` reaches provider opcode `0` for VPU power/control bookkeeping | In-flight control matrix over `0/1/2/3/0xff` tested: completed shape returns `dev_ctrl=0, wait=0`; timeout shape returns `dev_ctrl=0, wait=-EIO`; no IOMMU/devapc/Oops | Reachable but now a controlled negative for this primitive search |
| `ucmd` algorithm lookup | HardwareBuffer-backed `ucmd` opcode path reaches `vpu_alg_get` / `vpu_alg_put` for `apu_lib_apunn` | Success path exists; keydump does not mutate the first 64 bytes | Low-cost side-effect/refcount candidate |
| iDMA write timing (firmware static) | FLIX128 boundary byte scan of `0x70044b74` (3880 B, iDMA orchestrator) | INPUT DMA: schedule+wait in consecutive FLIX128 bundles [e2e..e3e) and [e47..e57), only 9-byte gap; OUTPUT write: `0x70044850` (dmaif utility, 804 B, zero L32R to .rodata) called before 4th `0x700a21b8` compute pass; CPU-store inside `0x700a21b8` (20 KB, 4×) is most likely output fill path | Q1 answer for `XTENSA_ANN_VERSION`: fully synchronous, no Java-layer race window; `ann_output40=14ms` anomaly still unexplained |
| Plane-redirect + IOVA reuse (proposed) | Combine descriptor `+0x20..+0x3b` plane fuzz (~9 s timeout write path) with `target_then_lower` gap allocator shape | Plane redirect: confirmed first-dword write of imported plane window (PLN0→PLN1), ~9 s timeout/EIO; target/lower gap: confirmed 5% hit rate 64K; **combination not yet tested** | New rank-1 candidate for cross-buffer write: plane-redirect write is ≫ Java `mem_free` latency, making the timing race winnable if exact IOVA reuse occurs |

## Best next experiments

### 1. IOVA reuse profiler

Purpose: decide whether exact IOVA reuse is feasible before spending more time
on firmware races.

Shape:

```
for many iterations:
  mem_create(type2, original_hwb) -> iova A
  mem_free(A)
  mem_create(type2, replacement_hwb) x N
  record exact reuse, nearby reuse, mem id, size, fd mode
```

Variables:

| Variable | Values |
|---|---|
| replacement count | `4`, `8`, `16`, `32` |
| import size | `0x1000`, `0x4000`, `0x10000` |
| fd lifetime | same APUSYS fd, reopen APUSYS fd per iteration |
| replacement source | pre-created HardwareBuffers, freshly created HardwareBuffers |
| free/import delay | `0`, `1`, `10 ms` |

Decision:

- If exact reuse appears, move directly back to the completed firmware
  writeback race with pre-created replacements.
- If exact reuse stays at zero across a meaningful loop, downgrade the
  cross-buffer-write path on this build and keep only allocator patterns or
  lower-level scheduler evidence that can change the exact-reuse result.

Status: implemented and run as:

```
poc/ApusysIoctlProbe.java --apusys-iova-reuse-profiler
```

Result:

```
result=../../poc-run-results/2026-06-15-batch/13_apusys_iova_reuse_profiler.txt
kernel=../../poc-run-results/2026-06-15-batch/13_apusys_iova_reuse_profiler_kernel_relevant.txt

prealloc_1k_c32_i20:  exact=0/640,  nearby=0/640,   closest_delta=0x266000
prealloc_4k_c32_i50:  exact=0/1600, nearby=950/1600, closest_delta=-0x4000
prealloc_64k_c16_i20: exact=0/320,  nearby=320/320, closest_delta=-0x10000
fresh_4k_c16_i10:     exact=0/160,  nearby=95/160,  closest_delta=-0x4000
```

Kernel log: no `devapc`, IOMMU fault, panic/Oops, `BUG`, or `KASAN`.

Interpretation: exact IOVA reuse did not occur in 2720 replacement imports.
Nearby reuse is common for 4K and 64K, which may be useful for allocator
modeling, but it does not let the current firmware completion write land in the
fresh replacement object.

### 2. Pre-created replacement pool

Purpose: remove Java `ImageReader` / `ImageWriter` / `HardwareBuffer` creation
from the vulnerable window.

Current reuse-pressure mode does this after `mem_free`:

```
create replacement HardwareBuffer
extract dmabuf fd
mem_create2(replacement fd)
```

The next mode should do this before `run_cmd_async`:

```
pre-create replacement HardwareBuffers
pre-extract replacement dmabuf fds
run_cmd_async
mem_free(shared_iova)
mem_create2(replacement fd) x N
dump replacements
wait_cmd
```

Suggested mode name:

```
--run-cmd-vpu-xrp-mem-free-race-completed-prealloc-reuse-iova
```

Success signal:

- replacement IOVA equals the freed IOVA, and
- replacement buffer changes from marker/zero to APUNN completion pattern:
  settings `0x7`, output fill, or `settings+0x30` cleanup.

Negative signal:

- no exact IOVA reuse, or
- exact reuse occurs but replacement bytes remain unchanged and kernel log is
  clean.

Status: superseded by targeted gap reuse. Pre-creating replacement buffers alone
does not force exact reuse. The useful allocator variable is the free order of
an exact lower neighbor next to the target IOVA.

### 2a. Target/lower gap reuse profiler

Purpose: test whether the nearby `-size` reuse pattern can be turned into exact
reuse by explicitly freeing both the target and its lower neighbor.

Implemented and run as:

```
poc/ApusysIoctlProbe.java --apusys-iova-gap-profiler
```

Result:

```
result=../../poc-run-results/2026-06-15-batch/13_apusys_iova_gap_profiler.txt
kernel=../../poc-run-results/2026-06-15-batch/13_apusys_iova_gap_profiler_kernel_relevant.txt

gap_4k_p16_r16_i30_lower_then_target:
  adjacent_found=30/30, exact_target=0/480

gap_4k_p16_r16_i30_target_then_lower:
  adjacent_found=28/30, exact_target=2/448

gap_64k_p12_r8_i12_lower_then_target:
  adjacent_found=12/12, exact_target=0/96

gap_64k_p12_r8_i12_target_then_lower:
  adjacent_found=12/12, exact_target=1/96
```

Kernel log: no `devapc`, IOMMU fault, panic/Oops, `BUG`, or `KASAN`.

Interpretation: exact target IOVA reuse is possible, but only with the
target-then-lower free order in this run. This reopens the firmware-coupled
cross-buffer-write hypothesis.

### 2b. Target/lower gap control follow-up

Purpose: measure whether the exact reuse can be made slot-predictable by
changing pool pressure, replacement pressure, and target selection.

Implemented and run as:

```
poc/ApusysIoctlProbe.java --apusys-iova-gap-control-profiler
```

Result:

```
result=../../poc-run-results/2026-06-15-batch/13_apusys_iova_gap_control_profiler.txt
kernel=../../poc-run-results/2026-06-15-batch/13_apusys_iova_gap_control_profiler_kernel_relevant.txt

4K p16/r16 first:
  adjacent_found=78/80, exact_target=3/1248
  first_exact_hist=[7:1,9:1,12:1]

4K p12/r20 first:
  adjacent_found=59/80, exact_target=4/1180
  first_exact_hist=[4:1,16:1,18:1]

4K p20/r12 first:
  adjacent_found=66/80, exact_target=1/792
  first_exact_hist=[10:1]

4K p16/r16 highest:
  adjacent_found=36/60, exact_target=0/576

64K p12/r12 first:
  adjacent_found=40/40, exact_target=1/480
  first_exact_hist=[7:1]
```

Kernel log: no `devapc`, IOMMU fault, panic/Oops, `BUG`, or `KASAN`.

Interpretation: `target_then_lower` exact reuse is repeatable, but the hit slot
is not stable. Replacement indexes `4`, `7`, `9`, `10`, `12`, `16`, and `18`
all appeared as first exact hits across the control run. The attacker can own
the replacement buffers, but this is not yet a selected-slot primitive.

### 2c. Firmware-coupled target/lower gap reuse

Purpose: combine the exact-reuse allocator shape with the live APUNN completion
writeback path.

Implemented and run as:

```
poc/ApusysIoctlProbe.java --run-cmd-vpu-xrp-mem-free-race-completed-gap-reuse-iova
```

Current implementation focus: the same mode now runs the old `output_size=0x40`
baseline plus `output_size=0x1000` gap-reuse cases, including a `250 us`
post-`run_cmd_async` delay before freeing target/lower. This directly tests the
best remaining window: firmware has consumed valid APUNN settings, but output
writeback may not have finished before exact replacement reuse.

Result:

```
result=../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_gap_reuse_iova.txt
kernel=../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_gap_reuse_iova_kernel_relevant.txt

pair_found=29/30
run_ok=29
exact_target=1/464
completion_like_hits=0
wait_ok=24
wait_eio=5
```

Exact-hit iteration:

```
iter=25 repl=6 same_as_freed=1
replacement before wait: marker/zero
replacement after wait:  marker/zero
completion_like=0
wait=-EIO
```

The original target buffer still shows the APUNN input/header state, but in the
exact-hit iteration settings remain `0x5` and the replacement does not receive
settings `0x7`, output fill, or data descriptor cleanup.

Kernel log: timeout cases show expected `request (D2D_EXT) timeout`,
`mdw_sched_trace ret(-110)`, and `mdw_wait_cmd ... fail`. There is no
`devapc`, IOMMU fault, panic/Oops, `BUG`, or `KASAN`.

Interpretation: the allocator half of the primitive is now real, but the
firmware writeback half is not won. In the exact-hit case, replacement import
appears to happen before firmware consumes a valid APUNN settings buffer, so the
command times out instead of writing completion bytes into the replacement.

Follow-up with exact-index histograms:

```
result=../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_gap_reuse_iova_followup.txt
kernel=../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_gap_reuse_iova_followup_kernel_relevant.txt

4K p16/r16:
  pair_found=13/30
  exact_target=1/208
  first_exact_hist=[9:1]
  completion_like_hits=0
  wait_ok=12
  wait_eio=1

4K p12/r20:
  pair_found=17/40
  exact_target=0/340
  completion_like_hits=0
  wait_ok=13
  wait_eio=4
```

The exact p16/r16 hit was again marker/zero before and after wait, with
`completion_like=0` and `wait=-EIO`. The p12/r20 allocator shape did not carry
over to a firmware exact hit in this run. Kernel log contains timeout-class VPU
messages for failed waits, but no `devapc`, IOMMU fault, panic/Oops, `BUG`, or
`KASAN`.

Timing-variant run:

```
result=../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_gap_reuse_iova_timing.txt
kernel=../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_gap_reuse_iova_timing_kernel_relevant.txt

4K p16/r16 output40 delay0:    pair_found=6/30, exact_target=0/96, completion_like=0, wait=6/0
4K p12/r20 output40 delay0:    pair_found=3/40, exact_target=0/60, completion_like=0, wait=3/0
4K p16/r16 output1000 delay0:  pair_found=7/30, exact_target=0/112, completion_like=0, wait=6/1
4K p16/r16 output1000 delay250us:
  pair_found=5/30, exact_target=0/80, completion_like=0, wait=5/0
```

Interpretation: the larger `settings+0x08` output window and `250 us` free
delay did not reproduce exact target reuse in this run, so they did not produce
a replacement-buffer completion write. Kernel signal stayed controlled: one
timeout-class `mdw_sched_trace ret(-110)`, no `devapc`, IOMMU fault,
panic/Oops, `BUG`, or `KASAN`.

### 3. Slower completed writeback shape

Purpose: increase the useful race window while keeping `wait=0` and preserving
the APUNN completion writeback oracle.

Try only shapes that still complete:

| Knob | Why it may help |
|---|---|
| larger output size (`0x100`, `0x400`, `0x1000`) | More firmware writeback work |
| multiple data descriptors | More descriptor cleanup and data binding work |
| `GET_DETAILED_OP_INFO` / `LOCAL_MEM_INFO` | May have heavier internal query path than `XTENSA_ANN_VERSION` |
| two queued commands sharing one IOVA | Keeps the shared IOVA referenced across more scheduler time |

Useful shapes are the ones that remain stable, write back, and take longer than
the current fast completion.

Status: implemented and run as:

```
poc/ApusysIoctlProbe.java --run-cmd-vpu-xrp-completed-latency-matrix-iova
```

Result:

```
result=../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_completed_latency_matrix_iova.txt
kernel=../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_completed_latency_matrix_iova_kernel_relevant.txt

ann_output40:              run_async=0 in 1 ms, wait=0 in 14 ms
ann_output100:             run_async=0 in 1 ms, wait=0 in 1 ms
ann_output400:             run_async=0 in 2 ms, wait=0 in 2 ms
ann_output1000:            run_async=0 in 2 ms, wait=0 in 3 ms
local_mem_info_output40:   run_async=0 in 3 ms, wait=0 in 1 ms
detailed_op_info_output40: run_async=0 in 3 ms, wait=0 in 2 ms
```

All six shapes preserve the normal APUNN completion oracle: request
`result_status=0`, settings become `0x7`, and output is bounded by the requested
size. The preserved kernel log has no `devapc`, IOMMU fault, panic/Oops, `BUG`,
`KASAN`, timeout, or `mdw_wait_cmd` failure.

Interpretation: these variants do not create a useful wider window from Java.
They remain good completion oracles, but they do not change the lifetime risk
ranking.

Follow-up field-order polling is implemented and run as:

```
poc/ApusysIoctlProbe.java --run-cmd-vpu-xrp-completion-poll-iova
```

Result:

```
result=../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_completion_poll_iova.txt
kernel=../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_completion_poll_iova_kernel_relevant.txt

ann_output40:   run_async=0 in 1 ms, 10 ms poll, changed_fields=0, wait=0 in 1 ms
ann_output1000: run_async=0 in 2 ms, 10 ms poll, changed_fields=0, wait=0 in 1 ms
```

The polled fields are settings flags, `settings+0x30`, representative output
words, and data-desc word 0. They remain at the pre-async snapshot until
`wait_cmd`; after wait, the standard completion bytes are visible. This moves
the current Java-layer lifetime race below allocator work and leaves FLIX/iDMA
internal store order as a separate firmware instrumentation branch.

### 4. Two-command shared IOVA

Purpose: amplify the lifetime edge without depending only on a single fast
completion.

Shape:

```
mem_create(shared_hwb) -> shared_iova
run_cmd_async(cmd1 -> shared_iova)
run_cmd_async(cmd2 -> shared_iova)
mem_free(shared_iova)
mem_create2(precreated replacement fd) x N
wait cmd1/cmd2
dump original and replacement buffers
```

Interesting signals:

- one command succeeds and the other fails,
- replacement gets APUNN completion bytes,
- residual command count or scheduler state differs,
- kernel log shows VPU/IOMMU/devapc faults or abort/wait inconsistency.

Status: implemented and run as:

```
poc/ApusysIoctlProbe.java --run-cmd-vpu-xrp-two-command-shared-iova
```

Result:

```
result=../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_two_command_shared_iova.txt
kernel=../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_two_command_shared_iova_kernel_relevant.txt

completed_completed:
  cmd1 run_async=0, cmd2 run_async=0
  shared mem_free=0
  replacement exact_reuse=0/4
  wait cmd1=0, wait cmd2=0

completed_timeout:
  cmd1 run_async=0, cmd2 run_async=0
  shared mem_free=0
  replacement exact_reuse=0/4
  wait cmd1=0, wait cmd2=-EIO

timeout_completed:
  cmd1 run_async=0, cmd2 run_async=0
  shared mem_free=0
  replacement exact_reuse=0/4
  wait cmd1=-EIO, wait cmd2=0
```

All replacement buffers kept their marker/header and zeroed output windows. The
completed command wrote the original shared buffer as expected: settings `0x7`,
bounded output fill, and standard data descriptor cleanup. Timeout commands
reported `result_status=0x2` and `wait=-EIO`.

Kernel log: only expected VPU boot/static allocation noise and timeout-class
`mdw_sched_trace ret(-110)` lines for the timeout cases. No `devapc`, IOMMU
fault, panic/Oops, `BUG`, or `KASAN`.

Interpretation: two-command sharing proves scheduler ordering can mix a
successful and timeout command over one IOVA, but it does not win the freed-IOVA
reuse path or expose a new copyback/lifetime primitive on this build.

### 5. `dev_ctrl` during in-flight VPU command

Purpose: test a separate concurrency class: provider control/reset while opcode
`4` D2D_EXT execution is in-flight.

Shape:

```
run_cmd_async(timeout or completed shape)
sleep 0/1/10/50 ms
dev_ctrl(device=3, core=0)
wait/dump/kernel log
```

Priority: lowered after the control matrix. Keep it only as a reference
side-effect path unless new provider-control state appears.

Status: implemented and run as:

```
poc/ApusysIoctlProbe.java --run-cmd-vpu-xrp-dev-ctrl-race-iova
```

Result:

```
result=../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_dev_ctrl_race_iova.txt
kernel=../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_dev_ctrl_race_iova_kernel_relevant.txt

completed settings5/no-settings, dev_ctrl_after=0/1/10/50ms:
  run_async=0, dev_ctrl=0, settings=0x7, wait=0

timeout minimal/split, dev_ctrl_after=0/1/10/50ms:
  run_async=0, dev_ctrl=0, request result_status=0x2, wait=-EIO
```

Kernel log: timeout cases show the expected `request (D2D_EXT) timeout`,
`mdw_sched_trace ret(-110)`, and `mdw_wait_cmd ... fail` lines. There are VPU
power/idle messages such as `vpu_pwr_off_locked: vpu0: not in idle state`, but
no `devapc`, IOMMU fault, panic/Oops, `BUG`, or `KASAN`.

Interpretation: `dev_ctrl` is reachable during the command lifetime window, but
the tested control value does not produce a reset/copyback/lifetime primitive.
It currently ranks below exact-reuse firmware timing and kernel-side scheduler
instrumentation, but remains a low-cost alternate ioctl side-effect path.

Follow-up matrix:

```
result=../../poc-run-results/2026-06-15-batch/13_apusys_xrp_dev_ctrl_control_matrix.txt
kernel=../../poc-run-results/2026-06-15-batch/13_apusys_xrp_dev_ctrl_control_matrix_kernel_relevant.txt

controls: 0/1/2/3/0xff
completed delays: 0/1/10 ms
timeout delays: 0/10 ms
```

All 25 `dev_ctrl` calls returned `0`. Completed cases had 15/15 `wait=0` and
the usual single tail scalar copyback. Timeout cases had 10/10 `wait=-EIO` and
the usual `result_status=0x2` copyback. The filtered kernel log contains
expected timeout lines and a few `vpu_pwr_off_locked: vpu0: not in idle state`
messages, with no `devapc`, IOMMU fault, panic/Oops, `BUG`, or `KASAN`.

Interpretation: nonzero provider controls are reachable during command
lifetime, but the tested control-state race does not change the completion
class, widen copyback, or produce a kernel fault.

### 6. Plane-redirect + target/lower IOVA reuse (new, highest-priority)

**Background**: iDMA timing firmware analysis (2026-06-15) shows the completed
`XTENSA_ANN_VERSION` write path is fully synchronous and sub-Java-latency. The
descriptor-plane redirect path produces ~9 s timeout/EIO but still writes the
first dword of the imported plane window (PLN0→PLN1). This is the only
identified firmware write path that is **slower than the Java `mem_free`
round-trip**.

**Hypothesis**: if the plane-redirect shape can be combined with the
`target_then_lower` allocator shape, the firmware write (which takes ~9 s) will
still be in-flight when the replacement import takes the freed IOVA. The
replacement buffer would then receive the plane-redirect write instead of the
original buffer.

**Shape**:

```
// Set up the plane-redirect descriptor (fuzz field +0x20/+0x24 to redirect to PLN1)
mem_create(type2, hwb_plane0_fd) -> iova_plane0
mem_create(type2, hwb_plane1_fd) -> iova_plane1
build plane-redirect VPU request pointing at iova_plane0 as descriptor plane

// Apply target/lower gap shape around iova_plane1 (the write target)
import pool of same-size buffers around iova_plane1
find adjacent target/lower pair at or near iova_plane1
run_cmd_async(plane-redirect request)      // firmware starts, will write to iova_plane1
mem_free(iova_plane1_target)               // release write target IOVA
mem_free(iova_plane1_lower)                // release lower neighbor
mem_create2(replacement_fds) x N           // attempt exact reuse at freed iova_plane1
wait_cmd / check replacement buffers
```

**Success signal**: replacement at exact `iova_plane1` IOVA, and replacement
first dword changes from marker to `0x504c4e31` (PLN0→PLN1 redirect byte).

**Negative signal**: exact reuse absent, or replacement first dword unchanged
(write never arrived), or IOMMU fault in kernel log.

**Implementation notes**:
- Use 64K `p16/r8` target/lower gap shape (best allocator baseline: 5% hit rate)
- Plane-redirect write window is ~9 s; `mem_free + replacement imports` need only
  ~2 ms → timing is easily winnable once exact IOVA reuse occurs
- Suggested mode name:
  `--run-cmd-vpu-xrp-plane-redirect-gap-reuse-iova`

**Status**: not yet implemented.

## Current stop conditions

Stop spending time on these unless new evidence changes the decision:

- more APUNN operand/opcode matrices that only prove completion acceptance,
- synthetic payload leak tests after the current data-payload matrices,
- display ioctl paths for this APUSYS objective,
- delay-only `mem_free` sweeps without allocator pressure.
- two-command shared-IOVA retries without target/lower gap shaping or a new
  scheduler timing signal.
- **completed-path exact-reuse firmware timing without a new opcode or timing
  signal**: firmware byte analysis confirms `XTENSA_ANN_VERSION` INPUT DMA and
  OUTPUT write are synchronous sub-Java-latency; repeating the
  `--run-cmd-vpu-xrp-mem-free-race-completed-gap-reuse-iova` probe with the
  same opcode shape will not produce a different result. Only re-enter this
  path if: (a) TIE/FLIX analysis of `0x700a21b8` reveals an async DMA branch,
  or (b) the `ann_output40` 14 ms anomaly is confirmed as a different code
  path that can be raced.
- completed latency variants under the same Java app-process timing, unless a
  lower-level timestamp shows firmware work lasting beyond the current ioctl
  round-trip.
- allocator-only gap profiling with the same `target_then_lower` order; exact
  reuse has already been observed. Further work must combine that shape with a
  wider firmware/scheduler window.

## Files to keep linked

- Main handoff: `HANDOFF_KERNEL_PRIMITIVE.md`
- Allocator controllability target:
  `ALLOCATOR_CONTROLLABILITY_OPPORTUNITY.md`
- ABI log: `APUNN_SETTINGS_ABI.md`
- Current probe: `../poc/ApusysIoctlProbe.java`
- Latest reuse-pressure result:
  `../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_reuse_iova.txt`
- Latest reuse-pressure kernel log:
  `../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_reuse_iova_kernel_relevant.txt`
- IOVA reuse profiler:
  `../../poc-run-results/2026-06-15-batch/13_apusys_iova_reuse_profiler.txt`
- IOVA reuse profiler kernel log:
  `../../poc-run-results/2026-06-15-batch/13_apusys_iova_reuse_profiler_kernel_relevant.txt`
- IOVA gap profiler:
  `../../poc-run-results/2026-06-15-batch/13_apusys_iova_gap_profiler.txt`
- IOVA gap profiler kernel log:
  `../../poc-run-results/2026-06-15-batch/13_apusys_iova_gap_profiler_kernel_relevant.txt`
- IOVA gap control profiler:
  `../../poc-run-results/2026-06-15-batch/13_apusys_iova_gap_control_profiler.txt`
- IOVA gap control profiler kernel log:
  `../../poc-run-results/2026-06-15-batch/13_apusys_iova_gap_control_profiler_kernel_relevant.txt`
- Firmware-coupled gap reuse:
  `../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_gap_reuse_iova.txt`
- Firmware-coupled gap reuse kernel log:
  `../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_gap_reuse_iova_kernel_relevant.txt`
- Firmware-coupled gap reuse follow-up:
  `../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_gap_reuse_iova_followup.txt`
- Firmware-coupled gap reuse follow-up kernel log:
  `../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_gap_reuse_iova_followup_kernel_relevant.txt`
- Dev-ctrl race:
  `../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_dev_ctrl_race_iova.txt`
- Dev-ctrl race kernel log:
  `../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_dev_ctrl_race_iova_kernel_relevant.txt`
- Two-command shared IOVA:
  `../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_two_command_shared_iova.txt`
- Two-command shared IOVA kernel log:
  `../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_two_command_shared_iova_kernel_relevant.txt`
- Completed latency matrix:
  `../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_completed_latency_matrix_iova.txt`
- Completed latency matrix kernel log:
  `../../poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_completed_latency_matrix_iova_kernel_relevant.txt`
