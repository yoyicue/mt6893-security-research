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
lifetime gap into a cross-buffer firmware write. The current implementation
does not yet produce that primitive. The first firmware reuse-pressure sweep
created 12 replacement imports and got `exact_reuse=0/12`; the standalone IOVA
reuse profiler later expanded this to `exact_reuse=0/2720` across 1K, 4K, and
64K import sizes.

The profiler did show nearby allocator behavior: 4K and 64K replacements often
land one buffer below the freed IOVA (`closest_delta=-0x4000` or `-0x10000`),
but not at the exact freed IOVA. That downgrades the direct cross-buffer-write
route on this build unless a new allocation pattern can force exact reuse.

The latest follow-up runs also tested the two natural window-amplification
ideas. Two queued commands sharing the same IOVA did not produce exact
replacement reuse or stale APUNN bytes in replacement buffers. The completed
latency matrix kept all tested completed shapes in the `1..14 ms` range, with
clean kernel logs. That leaves exact allocator reuse, or a lower-level scheduler
signal outside Java timing, as the only remaining ways to change this
classification.

Current practical ranking:

1. exact allocator reuse after `mem_free(shared_iova)`,
2. kernel-side scheduler/lifetime evidence that Java cannot observe directly,
3. `dev_ctrl` / `ucmd` side effects as lower-probability alternate ioctl paths.

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
| Replacement import pressure | After `mem_free`, import same-size replacement HardwareBuffers and dump them after completion | Firmware race: `exact_reuse=0/12`; standalone profiler: `exact_reuse=0/2720`; no stale APUNN write in replacement buffers | Largest theoretical opportunity, downgraded on this allocator profile |
| Two-command shared IOVA | Submit two commands referencing one imported IOVA, free it, import replacements, then wait both commands | `completed/completed`, `completed/timeout`, and `timeout/completed` all submitted; replacement exact reuse `0/12`; completed command still writes original shared buffer; timeout command returns `-EIO` | Window amplification tested, no primitive signal |
| Completed latency variants | Change output size/opcode while keeping completed settings5/no-settings shape | `ANN_VERSION` output `0x40/0x100/0x400/0x1000`, `LOCAL_MEM_INFO`, and `GET_DETAILED_OP_INFO` all complete with wait time `1..14 ms` | No slower completed writeback window found |
| fd close teardown | Close APUSYS fd after async submit and leave residual command cleanup to `mdw_usr_destroy` | Residual teardown reachable; no crash/oops/KASAN | Lower confidence than explicit `mem_free` |
| `dev_ctrl` provider path | ioctl `0x400C4109` reaches provider opcode `0` for VPU control bookkeeping | In-flight race tested: completed shape returns `dev_ctrl=0, wait=0`; timeout shape returns `dev_ctrl=0, wait=-EIO`; no IOMMU/devapc/Oops | Reachable but no new primitive signal |
| `ucmd` algorithm lookup | HardwareBuffer-backed `ucmd` opcode path reaches `vpu_alg_get` / `vpu_alg_put` for `apu_lib_apunn` | Success path exists; keydump does not mutate the first 64 bytes | Low-cost side-effect/refcount candidate |

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
result=poc-run-results/2026-06-15-batch/13_apusys_iova_reuse_profiler.txt
kernel=poc-run-results/2026-06-15-batch/13_apusys_iova_reuse_profiler_kernel_relevant.txt

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

Status: deferred after the profiler. Pre-creating replacement buffers removes
Java allocation overhead, but the no-firmware profiler already shows exact IOVA
reuse is not happening with immediate same-size imports on this build. This
mode should be implemented only if a new allocator pattern first demonstrates
exact reuse, or if the two-command shared-IOVA experiment creates a wider
lifetime window worth retesting.

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

Do not treat this as broad fuzzing. The only useful shape is one that remains
stable, writes back, and takes longer than the current fast completion.

Status: implemented and run as:

```
poc/ApusysIoctlProbe.java --run-cmd-vpu-xrp-completed-latency-matrix-iova
```

Result:

```
result=poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_completed_latency_matrix_iova.txt
kernel=poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_completed_latency_matrix_iova_kernel_relevant.txt

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
result=poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_two_command_shared_iova.txt
kernel=poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_two_command_shared_iova_kernel_relevant.txt

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

Priority: after the IOVA reuse profiler unless profiler shows exact reuse.

Status: implemented and run as:

```
poc/ApusysIoctlProbe.java --run-cmd-vpu-xrp-dev-ctrl-race-iova
```

Result:

```
result=poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_dev_ctrl_race_iova.txt
kernel=poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_dev_ctrl_race_iova_kernel_relevant.txt

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
It currently ranks below exact allocator reuse and kernel-side scheduler
instrumentation, but remains a low-cost alternate ioctl side-effect path.

## Current stop conditions

Stop spending time on these unless new evidence changes the decision:

- more APUNN operand/opcode matrices that only prove completion acceptance,
- synthetic payload leak tests after the current data-payload matrices,
- display ioctl paths for this APUSYS objective,
- delay-only `mem_free` sweeps without allocator pressure.
- two-command shared-IOVA retries with the same 4K same-fd allocation pattern,
  unless exact reuse is first observed in a standalone allocator profile.
- completed latency variants under the same Java app-process timing, unless a
  lower-level timestamp shows firmware work lasting beyond the current ioctl
  round-trip.

## Files to keep linked

- Main handoff: `13-apusys-ioctl-surface/HANDOFF_KERNEL_PRIMITIVE.md`
- ABI log: `13-apusys-ioctl-surface/APUNN_SETTINGS_ABI.md`
- Current probe: `13-apusys-ioctl-surface/poc/ApusysIoctlProbe.java`
- Latest reuse-pressure result:
  `poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_reuse_iova.txt`
- Latest reuse-pressure kernel log:
  `poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_reuse_iova_kernel_relevant.txt`
- IOVA reuse profiler:
  `poc-run-results/2026-06-15-batch/13_apusys_iova_reuse_profiler.txt`
- IOVA reuse profiler kernel log:
  `poc-run-results/2026-06-15-batch/13_apusys_iova_reuse_profiler_kernel_relevant.txt`
- Dev-ctrl race:
  `poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_dev_ctrl_race_iova.txt`
- Dev-ctrl race kernel log:
  `poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_dev_ctrl_race_iova_kernel_relevant.txt`
- Two-command shared IOVA:
  `poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_two_command_shared_iova.txt`
- Two-command shared IOVA kernel log:
  `poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_two_command_shared_iova_kernel_relevant.txt`
- Completed latency matrix:
  `poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_completed_latency_matrix_iova.txt`
- Completed latency matrix kernel log:
  `poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_completed_latency_matrix_iova_kernel_relevant.txt`
