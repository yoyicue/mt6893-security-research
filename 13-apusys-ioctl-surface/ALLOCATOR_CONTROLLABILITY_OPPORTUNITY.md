# APUSYS allocator controllability opportunity

Date: 2026-06-15

Scope: MT6893 APUSYS IOVA allocation behavior reachable from
`uid=1000(system)` / `u:r:system_app:s0` through HardwareBuffer-backed
`mem_create2` imports. This document tracks the allocator-control work needed
before firmware-coupled lifetime tests can produce a meaningful primitive.

## Target

Turn the observed `target_then_lower` adjacent-gap behavior from a repeatable
low-rate allocator event into a measurable control surface:

```
import pool
find target IOVA with exact lower neighbor
mem_free(target)
mem_free(lower)
import same-size replacements
measure whether replacement imports hit target exactly
```

The immediate objective is not a kernel read/write primitive. The objective is
to decide whether exact target IOVA reuse can be made frequent enough and
slot-stable enough to justify more firmware writeback timing work.

## Current Classification

Current status: allocator opportunity confirmed, primitive not proven.

The exact-reuse allocator condition exists on this build:

- immediate same-size reuse without gap shaping: `exact_reuse=0/2720`
- 4K `target_then_lower` gap profiler: `exact_target=2/448`
- 64K `target_then_lower` gap profiler: `exact_target=1/96`
- 4K gap-control follow-up, `p16/r16`: `exact_target=3/1248`
- 4K gap-control follow-up, `p12/r20`: `exact_target=4/1180`
- 4K gap-control follow-up, `p20/r12`: `exact_target=1/792`
- 64K gap-control follow-up, `p12/r12`: `exact_target=1/480`

The exact-reuse condition is not yet slot-stable:

```
first exact replacement indexes observed:
4, 7, 9, 10, 12, 16, 18
```

The firmware-coupled path has not produced a cross-buffer write:

- initial 4K firmware gap run: `exact_target=1/464`,
  `completion_like_hits=0`, exact-hit wait `-EIO`
- follow-up 4K `p16/r16`: `exact_target=1/208`,
  `completion_like_hits=0`, exact-hit wait `-EIO`
- follow-up 4K `p12/r20`: `exact_target=0/340`

Kernel logs for these runs contain expected timeout-class VPU messages in
failed firmware cases, but no `devapc`, IOMMU fault, panic/Oops, `BUG`, or
`KASAN`.

## Working Hypothesis

The APUSYS IOVA allocator often returns nearby lower addresses for same-size
imports. When both a target and its exact lower neighbor are freed in
`target_then_lower` order, the next replacement-import sequence can sometimes
walk back onto the target IOVA.

The useful gap is therefore allocator-shaped, not delay-shaped. More Java
sleep/delay sweeps are unlikely to improve the result unless they change the
free/import ordering or the allocator pressure around the adjacent hole.

The current firmware-coupled exact hits likely import the replacement before
firmware has consumed a valid APUNN settings buffer. That turns the command into
`wait=-EIO` instead of producing APUNN completion bytes in the replacement.

## Control Metrics

Every allocator follow-up should report these fields:

| Metric | Why it matters |
|---|---|
| `adjacent_found / iterations` | Measures how often the pool construction creates a usable target/lower pair |
| `exact_target_iterations / adjacent_found` | Main exact-reuse probability per usable pair |
| `exact_target_total / replacement_imports` | Main exact-reuse probability per replacement import |
| `first_exact_hist` | Shows whether exact reuse converges to a predictable replacement index |
| `exact_hist` | Shows duplicate exact hits in one iteration |
| `lower_hist` | Shows whether the lower neighbor is consumed before or after target reuse |
| `closest_delta_to_target` | Distinguishes near misses from unrelated allocator movement |
| `pool_import_fail_total` | Detects fd/object pressure changing the allocator profile |
| kernel filtered log | Confirms no IOMMU/devapc/Oops/BUG/KASAN side effect is being hidden |

The most important success metric is not raw `exact_target_total`. The useful
metric is concentration:

```
exact_target_iterations >= 10% of adjacent_found
and first_exact_hist has a dominant narrow replacement-index window
```

A broad index spread is still useful for owning a replacement set, but it is not
enough for precise firmware writeback timing.

## Next Experiment Matrix

### 1. Pair Selection

Keep `target_then_lower` as the primary free order. Compare target choice:

| Target mode | Status |
|---|---|
| first adjacent pair | Best current mode; keep |
| lowest adjacent target | Not yet measured |
| highest adjacent target | Measured negative in one run: `0/576` |
| pair with upper neighbor also present | Not yet measured |
| longest contiguous run around target | Not yet measured |

Expected output: exact-hit rate plus `first_exact_hist` for each target mode.

### 2. Pool And Replacement Pressure

Prioritize 4K first. Keep total pool plus replacement count at or below the
current scratch descriptor capacity unless the probe is extended.

Recommended first sweep:

| Size | Pool | Replacements | Reason |
|---:|---:|---:|---|
| 4K | 8 | 24 | More replacement walk depth |
| 4K | 10 | 22 | Between current `p12/r20` and deeper replacement pressure |
| 4K | 12 | 20 | Current best raw exact total |
| 4K | 14 | 18 | Balance pair formation and replacement depth |
| 4K | 16 | 16 | Current stable baseline |
| 4K | 20 | 12 | Current lower-rate comparison |
| 64K | 12 | 12 | Current 64K baseline |
| 64K | 16 | 8 | Compare to earlier 64K shape |

The first pass should use at least 100 usable adjacent pairs per case when the
device remains stable.

### 3. Replacement Source

Compare replacement allocation timing:

| Source | Question |
|---|---|
| pre-created replacements | Current baseline; removes Java allocation overhead from post-free window |
| fresh replacements after free | Tests whether HardwareBuffer allocation itself changes IOVA reuse |
| mixed guard plus replacement imports | Tests whether unrelated same-size imports steer the replacement index |

Fresh replacement tests should be allocator-only first. Do not combine them with
firmware until exact-hit concentration improves.

### 4. Free Neighborhood

Current positive shape frees exactly two buffers: target, then lower neighbor.
Follow-up should test whether freeing more local structure improves reuse:

| Free shape | Purpose |
|---|---|
| target, lower | Baseline positive |
| target, lower, lower-2 | Tests contiguous lower run behavior |
| upper, target, lower | Tests whether an upper hole disturbs target reuse |
| target, unrelated same-size, lower | Tests list insertion ordering |
| target, lower, import one guard, import replacements | Tests index steering |

The success condition remains exact target reuse, not lower-neighbor reuse.

## Firmware Re-entry Criteria

Do not spend more cycles on firmware-coupled APUNN writeback until allocator-only
runs meet at least one of these conditions:

- `exact_target_iterations / adjacent_found >= 10%` in a repeated 4K case,
- first exact hits cluster into a narrow index window across repeated runs,
- one case produces multiple exact hits per iteration often enough to treat the
  replacement set as a reliable target,
- kernel-side timing or tracing shows firmware has consumed the APUNN settings
  before `mem_free(target)`.

When re-entering firmware, the run should log:

- exact replacement indexes before wait,
- exact replacement contents before and after wait,
- original target contents before free and after wait,
- `wait` result,
- `completion_like` result,
- kernel timeout / IOMMU / devapc / Oops / BUG / KASAN lines.

The desired firmware signal is specific:

```
exact replacement at freed target IOVA
replacement changes from marker/zero to APUNN completion-like bytes
wait result is 0, or the replacement shows completion-like bytes before timeout
```

`exact_target=1` plus `wait=-EIO` and unchanged replacement bytes is an
allocator hit, not a firmware primitive.

## Stop Conditions

Stop treating exact reuse as the primary opportunity if:

- expanded allocator-only sweeps keep exact reuse below 1% of usable adjacent
  pairs,
- first exact-hit indexes remain broadly distributed after pair-selection and
  pressure sweeps,
- exact hits continue to correlate with `wait=-EIO` and unchanged replacement
  buffers in firmware-coupled runs,
- kernel-side APUSYS tracing shows the firmware always consumes the target
  before the replacement import can be installed.

At that point, the remaining APUSYS work should shift to kernel-side scheduler
lifetime instrumentation or alternate ioctl side-effect paths such as
`dev_ctrl` / `ucmd`.

## Current Probe Entry Points

- Allocator baseline:
  `poc/ApusysIoctlProbe.java --apusys-iova-reuse-profiler`
- Target/lower gap baseline:
  `poc/ApusysIoctlProbe.java --apusys-iova-gap-profiler`
- Target/lower control follow-up:
  `poc/ApusysIoctlProbe.java --apusys-iova-gap-control-profiler`
- Firmware-coupled gap reuse:
  `poc/ApusysIoctlProbe.java --run-cmd-vpu-xrp-mem-free-race-completed-gap-reuse-iova`

## Evidence Files

- `poc-run-results/2026-06-15-batch/13_apusys_iova_reuse_profiler.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_iova_reuse_profiler_kernel_relevant.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_iova_gap_profiler.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_iova_gap_profiler_kernel_relevant.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_iova_gap_control_profiler.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_iova_gap_control_profiler_kernel_relevant.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_gap_reuse_iova.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_gap_reuse_iova_kernel_relevant.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_gap_reuse_iova_followup.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_gap_reuse_iova_followup_kernel_relevant.txt`
