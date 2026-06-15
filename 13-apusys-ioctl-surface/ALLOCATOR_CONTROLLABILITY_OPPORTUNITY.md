# APUSYS allocator controllability program

Date: 2026-06-15

This document adapts the experiment-loop style from
<https://github.com/karpathy/autoresearch/blob/master/program.md> to the MT6893
APUSYS allocator problem. The aim is to make allocator controllability research
repeatable: define the setup, fixed metric, output format, result log, and
keep/discard criteria before running more probes.

Scope: APUSYS IOVA allocation behavior reachable from `uid=1000(system)` /
`u:r:system_app:s0` through HardwareBuffer-backed `mem_create2` imports.

## Objective

Turn the observed `target_then_lower` adjacent-gap behavior from a low-rate
allocator event into a measurable control surface:

```text
import pool
find target IOVA with exact lower neighbor
mem_free(target)
mem_free(lower)
import same-size replacements
measure whether replacements hit target exactly
```

The research objective is not a kernel read/write primitive. The objective is
to decide whether exact target IOVA reuse can be made frequent enough and
slot-stable enough to justify more firmware writeback timing work.

The primary score is:

```text
score = exact_target_iterations / adjacent_found
```

The tie-breakers are:

1. narrower `first_exact_hist` concentration,
2. higher `exact_target_total / replacement_imports`,
3. lower `pool_import_fail_total`,
4. simpler probe shape.

## Setup

Before starting a new allocator-control run:

1. Pick a run tag based on date and focus, for example
   `2026-06-15-gap-control-pair-selection`.
2. Confirm git state with `git status --short` and note the current commit.
3. Read the in-scope files:
   - `13-apusys-ioctl-surface/README.md`
   - `13-apusys-ioctl-surface/HANDOFF_KERNEL_PRIMITIVE.md`
   - `13-apusys-ioctl-surface/CONTROLLED_OPPORTUNITIES.md`
   - `13-apusys-ioctl-surface/ALLOCATOR_CONTROLLABILITY_OPPORTUNITY.md`
   - `13-apusys-ioctl-surface/poc/ApusysIoctlProbe.java`
   - `13-apusys-ioctl-surface/poc/run_system_app_probe.py`
4. Confirm the device-side system app shell still works:

   ```sh
   python3 13-apusys-ioctl-surface/poc/run_system_app_probe.py \
     -s 7FPE0824B0801372 --rebuild-if-needed \
     --mode=--query --timeout 60 \
     --result-dir poc-run-results/2026-06-15-batch \
     --result-name 13_apusys_allocator_setup_query.txt \
     --kernel-result-name 13_apusys_allocator_setup_query_kernel_relevant.txt \
     --kernel-pattern 'apusys|mdw|devapc|iommu|Unable to handle kernel|BUG|KASAN'
   ```

5. Initialize an untracked TSV result log for the run:

   ```text
   run_tag	commit	mode	size	pool	replacements	iterations	adjacent_found	exact_iter_rate	exact_import_rate	first_exact_hist	status	description
   ```

   Recommended path:
   `poc-run-results/2026-06-15-batch/13_apusys_allocator_results.tsv`.

## Fixed Boundaries

What can change:

- `ApusysIoctlProbe.java` allocator-only profiler cases and logging,
- wrapper result names, timeout, and kernel log filters,
- pool size, replacement count, target-selection strategy, free-neighborhood
  strategy, and replacement allocation timing,
- allocator-only analysis scripts that parse result logs.

What should stay fixed unless the research question changes:

- APUSYS ioctl ABI constants,
- `DrmTrigger.java` syscall helpers,
- APUNN firmware-coupled completion oracle,
- kernel result filter terms for `devapc`, IOMMU, panic/Oops, `BUG`, `KASAN`,
- the primary score formula.

Do not combine new allocator variables with firmware writeback in the same
first run. Prove the allocator shape first, then re-enter firmware only when
the re-entry criteria below are met.

## Current Baseline

Current status: allocator opportunity confirmed, primitive not proven.

Immediate same-size reuse without gap shaping is negative:

```text
--apusys-iova-reuse-profiler
exact_reuse=0/2720
```

Adjacent-gap shaping is positive:

```text
--apusys-iova-gap-profiler
4K target_then_lower:  exact_target=2/448
64K target_then_lower: exact_target=1/96
```

Gap-control follow-up:

```text
--apusys-iova-gap-control-profiler
4K p16/r16 first:   exact_target=3/1248, first_exact_hist=[7:1,9:1,12:1]
4K p12/r20 first:   exact_target=4/1180, first_exact_hist=[4:1,16:1,18:1]
4K p20/r12 first:   exact_target=1/792,  first_exact_hist=[10:1]
4K p16/r16 highest: exact_target=0/576
64K p12/r12 first:  exact_target=1/480,  first_exact_hist=[7:1]
```

First exact replacement indexes observed so far:

```text
4, 7, 9, 10, 12, 16, 18
```

Firmware-coupled status:

```text
initial 4K firmware gap:
  exact_target=1/464, completion_like_hits=0, exact-hit wait=-EIO

follow-up 4K p16/r16:
  exact_target=1/208, completion_like_hits=0, exact-hit wait=-EIO

follow-up 4K p12/r20:
  exact_target=0/340
```

Kernel logs contain expected timeout-class VPU messages in failed firmware
cases, but no `devapc`, IOMMU fault, panic/Oops, `BUG`, or `KASAN`.

## Output Format

Every allocator-control case must print one machine-greppable summary line:

```text
[+] iova_gap_control_summary <label> \
  size=0x4000 \
  pool=16 \
  replacements=16 \
  iterations=80 \
  target_mode=first \
  adjacent_found=78/80 \
  no_adjacent=2 \
  exact_target_total=3/1248 \
  exact_target_iterations=3/78 \
  lower_hit_total=2/1248 \
  import_fail_total=0 \
  pool_import_fail_total=29 \
  first_exact_hist=[7:1,9:1,12:1] \
  exact_hist=[7:1,9:1,12:1] \
  lower_hist=[10:1,13:1] \
  closest_iter=49 \
  closest_idx=12 \
  closest_delta_to_target=0x0
```

The wrapper should write:

- full stdout:
  `poc-run-results/<batch>/<run_tag>.txt`
- filtered kernel log:
  `poc-run-results/<batch>/<run_tag>_kernel_relevant.txt`

The kernel filter must include:

```text
mdw_usr_mem_free|mdw_mem_ion_unmap_iova|ion_free|devapc|iommu|Unable to handle kernel|BUG|KASAN|APUSYS|apusys
```

Firmware-coupled runs should additionally include:

```text
request (D2D_EXT) timeout|mdw_sched_trace|mdw_wait_cmd|vpu
```

## Result Log

Append each completed allocator case to the TSV. Keep the TSV untracked unless
the team explicitly wants to snapshot a batch.

Columns:

```text
run_tag	commit	mode	size	pool	replacements	iterations	adjacent_found	exact_iter_rate	exact_import_rate	first_exact_hist	status	description
```

Column rules:

- `commit`: short 7-character commit or `dirty` if the probe was not committed.
- `mode`: probe flag, for example `--apusys-iova-gap-control-profiler`.
- `size`: import size such as `0x4000`.
- `exact_iter_rate`: decimal `exact_target_iterations / adjacent_found`.
- `exact_import_rate`: decimal `exact_target_total / replacement_imports`.
- `status`: `keep`, `discard`, or `crash`.
- `description`: no tabs; short text naming the variable under test.

Example:

```text
run_tag	commit	mode	size	pool	replacements	iterations	adjacent_found	exact_iter_rate	exact_import_rate	first_exact_hist	status	description
baseline-p16-r16	89dc53f	--apusys-iova-gap-control-profiler	0x4000	16	16	80	78/80	0.0385	0.0024	[7:1,9:1,12:1]	keep	baseline first target pair
highest-target	89dc53f	--apusys-iova-gap-control-profiler	0x4000	16	16	60	36/60	0.0000	0.0000	[-]	discard	highest adjacent target selection
```

## Experiment Loop

Each allocator experiment should follow this loop:

1. Look at git state and record the starting commit.
2. Choose one allocator idea from the matrix below.
3. Modify only the minimum code needed to express that idea.
4. Run `git diff --check`.
5. Run the probe with stdout redirected by `run_system_app_probe.py`.
6. Extract summaries:

   ```sh
   rg "iova_gap_control_summary|gap_fw_summary" <result>.txt
   ```

7. Check kernel signal:

   ```sh
   rg "devapc|iommu|Unable to handle|BUG|KASAN|Oops|panic" \
     <kernel_relevant>.txt
   ```

8. Record the TSV row.
9. Keep the code change only if it improves the primary score, improves
   first-hit concentration, or makes the profiler simpler without losing
   signal.
10. Discard or rewrite the idea if the score is worse, the slot distribution is
    broader, or the change mostly increases noise.

Use one variable per run. If a run changes target selection, pool pressure, and
replacement timing at once, the result is hard to interpret and should not be
used as a new baseline.

## Keep And Discard Rules

Keep a profiler change when at least one is true:

- `exact_target_iterations / adjacent_found` improves by at least 2x over the
  current comparable baseline,
- `first_exact_hist` collapses into a narrow replacement-index window,
- the change increases `adjacent_found / iterations` without reducing exact-hit
  rate,
- the code becomes simpler while preserving the same allocator signal.

Discard a profiler change when any is true:

- exact-hit rate is equal or worse and the implementation is more complex,
- `first_exact_hist` becomes broader,
- pool/import failures dominate the run,
- the run produces kernel fault noise unrelated to the allocator question,
- the change only makes firmware timeouts more frequent without new replacement
  writes.

Crash handling:

- If the crash is a typo, stale descriptor offset, or logging bug, fix and
  rerun.
- If the crash is tied to the idea itself, log `crash` and move to a different
  allocator variable.

Timeout handling:

- Allocator-only runs should finish quickly. If one exceeds the wrapper timeout,
  treat it as a failed experiment and reduce iterations or object pressure.
- Firmware-coupled runs can timeout at the APUNN level; that is a measured
  result, not a host-side failure.

## Experiment Matrix

### 1. Pair Selection

Keep `target_then_lower` as the primary free order.

| Target mode | Status | Next action |
|---|---|---|
| first adjacent pair | Best current mode | Keep as baseline |
| lowest adjacent target | Not measured | Implement and compare |
| highest adjacent target | `0/576` in one run | De-prioritize unless other variables change |
| pair with upper neighbor also present | Not measured | Add scanner for upper neighbor |
| longest contiguous run around target | Not measured | Add scanner for local run length |

### 2. Pool And Replacement Pressure

Prioritize 4K first. Keep total pool plus replacement count at or below the
current scratch descriptor capacity unless the probe is extended.

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

The first pass should target at least 100 usable adjacent pairs per case when
the device remains stable.

### 3. Replacement Source

| Source | Question |
|---|---|
| pre-created replacements | Current baseline; removes Java allocation overhead from post-free window |
| fresh replacements after free | Tests whether HardwareBuffer allocation itself changes IOVA reuse |
| mixed guard plus replacement imports | Tests whether unrelated same-size imports steer the replacement index |

Fresh replacement tests should be allocator-only first. Do not combine them with
firmware until exact-hit concentration improves.

### 4. Free Neighborhood

| Free shape | Purpose |
|---|---|
| target, lower | Baseline positive |
| target, lower, lower-2 | Tests contiguous lower run behavior |
| upper, target, lower | Tests whether an upper hole disturbs target reuse |
| target, unrelated same-size, lower | Tests list insertion ordering |
| target, lower, import one guard, import replacements | Tests index steering |

The success condition remains exact target reuse, not lower-neighbor reuse.

## Firmware Re-entry Criteria

Do not spend more cycles on APUNN writeback until allocator-only runs meet at
least one condition:

- `exact_target_iterations / adjacent_found >= 10%` in a repeated 4K case,
- first exact hits cluster into a narrow index window across repeated runs,
- one case produces multiple exact hits per iteration often enough to treat the
  replacement set as reliable,
- kernel-side timing or tracing shows firmware has consumed APUNN settings
  before `mem_free(target)`.

When re-entering firmware, log:

- exact replacement indexes before wait,
- exact replacement contents before and after wait,
- original target contents before free and after wait,
- `wait` result,
- `completion_like` result,
- kernel timeout / IOMMU / devapc / Oops / BUG / KASAN lines.

The desired firmware signal is:

```text
exact replacement at freed target IOVA
replacement changes from marker/zero to APUNN completion-like bytes
wait result is 0, or replacement shows completion-like bytes before timeout
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
- kernel-side APUSYS tracing shows firmware always consumes the target before
  the replacement import can be installed.

At that point, shift APUSYS work to kernel-side scheduler lifetime
instrumentation or alternate ioctl side-effect paths such as `dev_ctrl` /
`ucmd`.

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
