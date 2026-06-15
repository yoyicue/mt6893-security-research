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

Current next-round experiment:

```text
64K p16/r8, target_mode=first, replacement_source=precreated
free target, then lower, then lower-2
attempt to collect at least 100 usable lower-2 pairs
promote only if exact_target_iterations/adjacent_found >= 10%
or first_exact_hist collapses into a narrow replacement-index window
```

This replaced broad spray expansion for the next allocator-only pass. The run
completed with a negative result: the focused 64K `p16/r8 target, lower,
lower-2` mode reached the configured cap at `5000` iterations with only
`23` usable lower-2 pairs and no exact target reuse:

```text
adjacent_found=23/5000
exact_target_iterations=0/23
exact_target_total=0/184
first_exact_hist=[-]
```

The result does not meet the firmware re-entry gate. Treat `target, lower,
lower-2` as measured-negative for exact amplification unless a new allocator
path variable changes usable-pair yield or exact-hit behavior.

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

Pair-selection follow-up:

```text
--apusys-iova-gap-pair-selection-profiler
4K p16/r16 first:   exact_target=2/1024, first_exact_hist=[7:1,15:1]
4K p16/r16 lowest:  exact_target=0/864
4K p16/r16 highest: exact_target=0/560
4K p16/r16 upper:   adjacent_found=0/120
4K p16/r16 longest: exact_target=1/416, first_exact_hist=[4:1]
```

Pool/replacement pressure follow-up:

```text
--apusys-iova-gap-pressure-profiler
4K p8/r24 first:    exact_target=0/264
4K p10/r22 first:   exact_target=0/308
4K p12/r20 first:   exact_target=0/160
4K p14/r18 first:   exact_target=1/378, first_exact_hist=[6:1]
4K p16/r16 first:   exact_target=0/256
4K p20/r12 first:   exact_target=1/444, first_exact_hist=[11:1]
64K p12/r12 first:  exact_target=3/936, first_exact_hist=[5:1,7:1,10:1]
64K p16/r8 first:   exact_target=4/640, first_exact_hist=[0:1,1:1,4:1,7:1]
```

Replacement-source follow-up:

```text
--apusys-iova-gap-source-profiler
4K p16/r16 precreated: exact_target=0/144
4K p16/r16 fresh:      exact_target=0/240
4K p16/r15 guard:      exact_target=0/225
64K p16/r8 precreated: exact_target=1/608, first_exact_hist=[6:1]
64K p16/r8 fresh:      exact_target=0/544
64K p16/r8 guard:      exact_target=1/480, first_exact_hist=[5:1]
```

Free-neighborhood follow-up:

```text
--apusys-iova-gap-free-neighborhood-profiler
64K p16/r8 target, lower:              exact_target=1/584
64K p16/r8 target, lower, lower-2:     exact_target=1/112, first_exact_hist=[3:1]
64K p16/r8 upper, target, lower:       exact_target=0/24
64K p16/r8 target, unrelated, lower:   exact_target=1/400, first_exact_hist=[6:1]
64K p16/r8 target, lower, guard import: exact_target=0/344
```

Focused lower-2 follow-up:

```text
--apusys-iova-gap-lower2-focus-profiler
64K p16/r8 target, lower, lower-2 pilot:
  iterations=600, adjacent_found=17/600, exact_target=0/136

64K p16/r8 target, lower, lower-2 until100:
  iterations=5000/5000, target_adjacent_found=100
  adjacent_found=23/5000
  no_target_lower=3821
  free_shape_unavailable=1156
  exact_target=0/184
  closest_delta_to_target=0x20000
```

First exact replacement indexes observed so far:

```text
0, 1, 3, 4, 5, 6, 7, 9, 10, 11, 12, 15, 16, 18
```

Current allocator decision: `first` remains the baseline target-selection mode.
`lowest`, `highest`, and `upper-neighbor-required` do not improve exact reuse in
the current 4K `p16/r16` profile. `longest local run` produced one exact hit,
but with low adjacent-pair yield, so it is not a new baseline. 4K pressure
changes did not improve control. 64K pressure is the strongest current
allocator-only signal, especially `p16/r8`, but exact-hit indexes are still
broad and below the firmware re-entry threshold. Fresh replacement allocation
and guard-before-replacement import do not improve the 4K or 64K profiles.
The expanded lower-2 focus run demotes 64K `target, lower, lower-2`: the initial
`1/14` hit did not reproduce, the focused run got only `23/5000` usable pairs,
and exact target reuse stayed `0/23`.

## Best-Practice Refresh

Current external model, checked against primary sources on 2026-06-15:

- Treat allocator-path identification as the first task. Android's DMA-BUF heap
  transition documentation notes that ION and DMA-BUF heaps can keep
  heap-specific allocators and DMA-BUF ops, and this device's APUSYS path still
  goes through `mdw_mem_ion_*` wrappers. Do not assume generic Linux page
  allocator behavior just because the userland source is `HardwareBuffer`.
  Source: <https://source.android.com/docs/core/architecture/kernel/dma-buf-heaps>
- Model exact reuse as an IOVA allocator/cache problem, not as a larger spray
  problem. Linux `drivers/iommu/iova.c` has a fast path (`alloc_iova_fast`) with
  reusable cached IOVA ranges before falling back to the tree allocator. That
  makes size class, free order, CPU/context locality, and cache state more
  important than raw replacement count. Source:
  <https://github.com/torvalds/linux/blob/master/drivers/iommu/iova.c>
- Keep hole-shaping policy-specific. The Linux generic allocator documents
  first-fit, best-fit, fixed-offset, and alignment-aware policies; APUSYS/ION may
  not use genalloc directly, but the policy lesson applies: exact reuse improves
  by making a particular hole the allocator's easiest legal answer, not by
  adding unrelated allocations. Source:
  <https://docs.kernel.org/core-api/genalloc.html>
- Preserve map/unmap ordering evidence. The DMA API's IOVA path keeps explicit
  IOVA state through unmap; for this project, every exact-reuse claim must still
  log APUSYS `mem_free`, IOVA unmap, replacement import, and kernel fault
  filters. Source: <https://docs.kernel.org/core-api/dma-api.html>

Local best practice from the current APUSYS runs:

- Use 64K as the next primary size. It matches the strongest broader case:
  `p16/r8 exact_target_iterations=4/80 = 5%` with hits at replacement indexes
  `[0,1,4,7]`. Keep 4K only as a comparison line.
- Keep replacements pre-created and same-size. Fresh replacement allocation and
  guard-before-replacement did not improve exact reuse in the current runs.
- Keep the Java probe's allocation/free/import sequence in one stable process
  and one tight loop. If a native helper is later added, pinning to a stable CPU
  is worth testing because IOVA caches can be per-CPU/per-domain, but that should
  be a separate variable.
- Prefer targeted hole shaping over wider pressure, but require a focused sample
  before promotion. The 64K `p16/r8 target, lower, lower-2` sample did not
  amplify exact reuse: it stopped at `5000` iterations with `23` usable pairs and
  `exact_target_iterations=0/23`.
- Do not re-enter firmware on a single exact hit. The focused lower-2 run failed
  the re-entry gate; further firmware work needs a different allocator variable
  or kernel-side timing evidence.
- Treat index spread as a real blocker. `[0,1,4,7]` means the replacement set is
  attacker-owned but not slot-predictable. Promote a shape only if the hit rate
  improves or the histogram collapses.

Completed focused-run recipe:

```text
size=0x10000
pool=16
replacements=8
target_mode=first
replacement_source=precreated
free_shape=target_lower_lower2
run_until=100 usable lower-2 pairs, or max_iterations=5000
firmware_reentry_gate=exact_target_iterations/adjacent_found >= 10%
```

The focused profiler records why iterations were unusable: missing target/lower
pair (`no_target_lower`), missing lower-2 (`free_shape_unavailable`), pool import
failure, replacement import failure, and closest non-exact delta. In the current
run, the dominant miss reasons were `no_target_lower=3821` and
`free_shape_unavailable=1156`.

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
| lowest adjacent target | Measured negative: `0/864` | Discard for current 4K `p16/r16` profile |
| highest adjacent target | Measured negative twice: `0/576`, `0/560` | De-prioritize unless other variables change |
| pair with upper neighbor also present | No usable pairs in first pass: `adjacent_found=0/120` | Revisit only with different pool pressure |
| longest contiguous run around target | One exact hit: `1/416`, but low adjacent yield | Do not promote to baseline yet |

### 2. Pool And Replacement Pressure

Prioritize 64K first for the next exact-amplification pass. 4K remains useful as
a comparison baseline, but the current 64K signal is stronger. Keep total pool
plus replacement count at or below the current scratch descriptor capacity
unless the probe is extended.

| Size | Pool | Replacements | Reason |
|---:|---:|---:|---|
| 4K | 8 | 24 | Measured negative: `0/264` |
| 4K | 10 | 22 | Measured negative: `0/308` |
| 4K | 12 | 20 | Measured negative in pressure run: `0/160` |
| 4K | 14 | 18 | One hit, low yield: `1/378` |
| 4K | 16 | 16 | Measured negative in pressure run: `0/256` |
| 4K | 20 | 12 | One hit, below baseline: `1/444` |
| 64K | 12 | 12 | Keep as 64K baseline: `3/936` |
| 64K | 16 | 8 | Best current broader pressure score: `4/640`, iteration score `4/80 = 5%`, histogram `[0:1,1:1,4:1,7:1]` |

The next focused pass should target at least 100 usable `target, lower, lower-2`
pairs when the device remains stable. If the focused shape cannot produce usable
pairs, record the miss reason instead of widening pressure blindly.

### 3. Replacement Source

| Source | Question |
|---|---|
| pre-created replacements | Current baseline; strongest 64K signal came from pressure run, not source run |
| fresh replacements after free | Measured negative: 4K `0/240`, 64K `0/544` |
| mixed guard plus replacement imports | Measured negative for improvement: 4K `0/225`, 64K `1/480` |

Source variants do not justify firmware re-entry. Keep pre-created replacements
as the baseline unless a later free-neighborhood shape changes the allocator
profile.

### 4. Free Neighborhood

| Free shape | Purpose |
|---|---|
| target, lower | Baseline positive; latest 64K repeat `1/584` |
| target, lower, lower-2 | Expanded negative: pilot `0/17`, focused until100 `0/23`, maxed at `23/5000` usable pairs; demote unless a new allocator variable changes usable-pair yield |
| upper, target, lower | Measured negative and often unavailable: `0/24` |
| target, unrelated same-size, lower | Measured below candidate: `1/400` |
| target, lower, import one guard, import replacements | Measured negative: `0/344` |

The success condition remains exact target reuse, not lower-neighbor reuse.
`target, lower, lower-2` is no longer the next allocator-only focus by itself:
the expanded run did not reach 100 usable pairs and produced no exact target
reuse.

## Firmware Re-entry Criteria

Do not spend more cycles on APUNN writeback until allocator-only runs meet at
least one condition:

- `exact_target_iterations / adjacent_found >= 10%` in a repeated 4K case,
- `exact_target_iterations / adjacent_found >= 10%` in the focused 64K
  `p16/r8 target, lower, lower-2` case,
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

Current gate result: do not re-enter firmware yet. The focused 64K `p16/r8
target, lower, lower-2` run produced `exact_target_iterations=0/23`, so it fails
the `>=10%` gate and has no histogram to promote. The best broader 64K pressure
case remains `p16/r8` at `4/80`, still below 10% and with a broad
`first_exact_hist=[0:1,1:1,4:1,7:1]`.

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
- Target/lower pair-selection follow-up:
  `poc/ApusysIoctlProbe.java --apusys-iova-gap-pair-selection-profiler`
- Target/lower pressure follow-up:
  `poc/ApusysIoctlProbe.java --apusys-iova-gap-pressure-profiler`
- Target/lower replacement-source follow-up:
  `poc/ApusysIoctlProbe.java --apusys-iova-gap-source-profiler`
- Target/lower free-neighborhood follow-up:
  `poc/ApusysIoctlProbe.java --apusys-iova-gap-free-neighborhood-profiler`
- Focused lower-2 next-round run:
  `poc/ApusysIoctlProbe.java --apusys-iova-gap-lower2-focus-profiler`
- Firmware-coupled gap reuse:
  `poc/ApusysIoctlProbe.java --run-cmd-vpu-xrp-mem-free-race-completed-gap-reuse-iova`

## Evidence Files

- `poc-run-results/2026-06-15-batch/13_apusys_iova_reuse_profiler.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_iova_reuse_profiler_kernel_relevant.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_iova_gap_profiler.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_iova_gap_profiler_kernel_relevant.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_iova_gap_control_profiler.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_iova_gap_control_profiler_kernel_relevant.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_iova_gap_pair_selection_profiler.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_iova_gap_pair_selection_profiler_kernel_relevant.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_iova_gap_pressure_profiler.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_iova_gap_pressure_profiler_kernel_relevant.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_iova_gap_source_profiler.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_iova_gap_source_profiler_kernel_relevant.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_iova_gap_free_neighborhood_profiler.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_iova_gap_free_neighborhood_profiler_kernel_relevant.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_iova_gap_lower2_focus_profiler.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_iova_gap_lower2_focus_profiler_kernel_relevant.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_iova_gap_lower2_focus_until100_profiler.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_iova_gap_lower2_focus_until100_profiler_kernel_relevant.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_allocator_setup_query.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_allocator_setup_query_kernel_relevant.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_allocator_results.tsv`
- `poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_gap_reuse_iova.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_gap_reuse_iova_kernel_relevant.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_gap_reuse_iova_followup.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_run_cmd_vpu_xrp_mem_free_race_completed_gap_reuse_iova_followup_kernel_relevant.txt`
