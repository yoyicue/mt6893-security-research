# Pivot Assessment: Next Target for Kernel Write

Updated: 2026-06-14

## Current Position

- uid=1000 system_app bind shell (CVE-2024-31317) ✅
- ART ArtMethod syscall primitive (arbitrary ioctl from pure Java) ✅
- Accessible devices: `/dev/binder`, `/dev/hwbinder`, `/dev/ion`, `/dev/mali0`, `/dev/dri/card0`, `/dev/apusys`, `/dev/aw_smartpa`
- SPL: 2023-06-05

## Exhausted / Dead-End Paths

| Path | Why Dead |
|------|----------|
| CVE-2023-32836 DRM overflow | ioctl reaches 64-bit MUL safe variant |
| CVE-2022-38181 Mali JIT UAF | `no_user_free_count` permanently blocks `DONT_NEED` on JIT |
| CVE-2023-4211 Mali tracking page | same `no_user_free_count` guard, `MEM_FREE` blocked |
| CVE-2022-36449 Mali refcount | user mmap holds page ref, free doesn't release |
| Mali WRITE_VALUE → kernel write | bounded to GPU SAME_VA userspace regions |
| **CVE-2022-22706 Mali write-readonly** | **PATCHED** — IDA confirms `TST #6` (CPU_WR\|GPU_WR) at both GUP call sites; earlier `#0xA` search was wrong bit namespace (UAPI vs internal) |
| DRM display OOB (SET_PQPARAM) | bug confirmed, but sink is HW register only + readback blocked by power gating |
| DRM READ_REG info leak | MMSYS power domain gated, all regs read 0 |
| CVE-2023-4622 AF_UNIX | SOCK_DEAD ordering constraint |

## Current ioctl risk ranking

This ranking assumes the current post-CVE-2024-31317 position: `uid=1000(system)` / `system_app`, working arbitrary `ioctl` from the Java/ART syscall primitive, and SPL `2023-06-05`.

| Rank | Surface | Current risk | Why |
|---:|---|---|---|
| 1 | `/dev/mali0` CVE-2023-33200 | **Highest confirmed** | Required ioctls are reachable, the path is not JIT-based, and IDA confirms the soft-event `vmap` vs sticky-resource USER_BUF unmap race shape. |
| 2 | `/dev/ion` MTK heap/ioctl family | **Unknown-high** | Device permissions look promising for `system_app`, and old SPL predates several MTK ION fixes, but this repo has not yet confirmed open/ioctl semantics from the target context. |
| 3 | `/dev/apusys` | **Unknown-medium/high** | Confirmed open proprietary MTK ioctl surface with little existing analysis. Promising for bug hunting, but no mapped CVE or primitive yet. |
| 4 | Display DRM `SET_PQPARAM` / adjacent PQ-AAL-SLD paths | **Medium** | `/dev/dri/card0` and many MTK private ioctls are reachable. The strongest current bug shape is display-state/MMIO-oriented, with no confirmed kernel-memory write primitive. |
| 5 | Display DRM register and GEM paths | **Low** | `WRITE_REG`/`READ_REG` have visible validation, and CVE-2023-32836 `CREATE_DUMB` reaches the MTK 64-bit size path rather than the vulnerable generic 32-bit multiply path. |
| 6 | Patched/dead Mali candidates | **Low / removed** | CVE-2022-22706 is patched in the binary; CVE-2022-38181/CVE-2023-4211 JIT paths are blocked by `no_user_free_count`; CVE-2022-36449 was retracted for this target. |

## Remaining Candidates — Ranked by Likelihood of Kernel Write

### Tier 1: Most Promising (unexplored, confirmed-open device, known CVEs)

#### A. `/dev/mali0` — Non-JIT Mali CVE paths

**CVE-2023-33200** (SPL fix: 2023-10, unpatched by SPL) is now the top confirmed ioctl candidate.

IDA confirms the race shape:

- `kbase_soft_event_update` maps and writes the soft-event byte while holding `jctx.lock`.
- `kbase_vmap_prot` releases `reg_lock` before the mapped byte is written and unmapped.
- `STICKY_RESOURCE_UNMAP` holds `reg_lock`, but not `jctx.lock`, and reaches imported USER_BUF page release.
- `kbase_vmap_phy_pages` did not show per-backing-page references that would protect the live mapping.

The binary does use `kbase_vmap_prot(..., KBASE_REG_CPU_WR, ...)`, so the permission-check part is partially hardened. That does not remove the lifetime race. See `12-cve-2023-33200-mali-race-uaf/IDA_HANDOFF.md`.

**CVE-2023-6241** (SPL fix: 2024-03, unpatched by SPL) remains secondary.

Key question: do these CVEs require the JIT path that `no_user_free_count` blocks?

- CVE-2023-6241: race condition in `kbase_jit_grow()`. This IS a JIT code path. If `no_user_free_count` blocks `jit_grow` or its prerequisite allocation, this is also dead. **Needs IDA verification.**
- CVE-2023-33200: confirmed non-JIT imported-user-buffer soft-event/sticky-resource race. **IDA verified vulnerable race shape.**

**Why promising**: `/dev/mali0` is open, has full ioctl capability via our ART syscall primitive, and these CVEs are confirmed unpatched. If the trigger path is NOT JIT-based, `no_user_free_count` wouldn't protect.

**Effort**: High (Mali driver is ~1MB of code in the kernel). Need to map the specific functions affected by each CVE.

**Action**: Keep 33200 as the primary static-analysis target for primitive characterization. Treat 6241 as secondary until IDA proves it escapes the JIT blocker.

#### ~~B. `/dev/mali0` — CVE-2022-22706 write-to-readonly-pages~~ — ELIMINATED

**Status**: IDA confirmed **PATCHED**. Both GUP call sites (`kbase_jd_user_buf_pin_pages` at `0x8620eac` and `kbase_mem_import` at `0x862f3c4`) use `TST reg_flags, #6` (internal `CPU_WR|GPU_WR`), not `GPU_WR`-only.

The original handoff search for `TST/AND #0xA` was based on UAPI `BASE_MEM_PROT_*` bit numbering. The internal `kbase_va_region.flags` uses different bit positions: `CPU_WR = 1<<1 = 0x2`, `GPU_WR = 1<<2 = 0x4`, so the patched mask is `#6` not `#0xA`.

See `11-cve-2022-22706-mali-write-readonly/IDA_HANDOFF.md` for full details.

#### C. `/dev/ion` — MTK ION heap vulnerabilities

**CVE-2023-20768** and potentially others.

`/dev/ion` DAC is `crw-rw-rw-` (world-writable), SELinux label `ion_device`. system_app should be able to open it.

**Anomaly**: runtime devprobe from DrmTrigger ioctlscan didn't confirm `/dev/ion` open. Need dedicated re-test with O_RDWR and O_RDONLY.

**Why promising**: ION is a large attack surface with custom MTK heap implementations. SPL 2023-06-05 predates many ION fixes.

**Action**: Verify `/dev/ion` open from system_app. If yes, enumerate ION heap IDs and custom ioctls.

### Tier 2: Medium Promise

#### D. `/dev/apusys` — MTK AI Processing Unit

Confirmed open from system_app. Completely unexplored ioctl surface.

**Why interesting**: Private MTK driver, likely less audited than display/GPU. Separate code base from the well-studied Mali/DRM paths.

**Risk**: Very little public CVE/research exists for APUSYS. Pure black-box analysis needed.

**Action**: IDA: find apusys ioctl handler table, enumerate commands, look for common bug patterns (unchecked copy_from_user sizes, array index without bounds).

#### E. Binder service-mediated kernel bugs

`/dev/binder` and `/dev/hwbinder` are open. system_app can reach Android system services.

CVE-2023-32834 (secmem) and CVE-2023-32835 (keyinstall) require System privileges and go through TEE-mediated binder services.

**Why medium**: Direct TEE device nodes (`/dev/gz_kree`, `/dev/trusty-ipc-dev0`) are blocked. The vulnerability must be reachable through the binder service layer, which adds API-level filtering.

**Action**: Build service-level probe for keystore2/gatekeeper binder interfaces.

### Tier 3: Low Promise

#### F. Display DRM remaining OOB candidates

The IDA-based risk ranking from the handoff identified SET_SLD_PARAM (0x57) and PQ_SET_WINDOW (0x2b) as worth deeper static analysis. However, given that even the confirmed OOB (SET_PQPARAM) only reaches HW registers, the exploitation ceiling for the entire display DRM OOB family is limited on this device.

**Possible exception**: if an OOB write corrupts a **kernel-side data structure** (not just HW register), the ceiling changes. Look for display handler that writes OOB data to a kmalloc'd buffer or global struct that is later used as a pointer/index.

#### G. DRM WRITE_REG fencepost / boundary edge cases

`validate_reg_addr` has a whitelist. If there's a fencepost error at a whitelist boundary, one could write just past a valid display MMIO window into adjacent MMIO space (possibly another SoC subsystem's registers).

**Action**: In IDA, dump the exact whitelist intervals and check for off-by-one in comparison operators.

## Recommendation

**~~Priority 1: CVE-2022-22706~~ — ELIMINATED** (patched, `TST #6` confirmed in binary)

**Priority 1 (revised): CVE-2023-33200 (Mali imported-user-buffer race)**
- Confirmed unpatched (SPL 2023-06-05 << fix SPL 2023-10-05 / 2024-03-05)
- `/dev/mali0` fully reachable + kbase context init works
- IDA confirms the race shape and confirms it avoids the JIT + `no_user_free_count` blocker
- Remaining work is exploitability engineering analysis, not initial reachability/patch-state triage

**Priority 2: `/dev/ion` reachability + ION CVEs**
- DAC world-writable, SELinux needs confirmation
- CVE-2023-20768 + other MTK ION heap bugs
- Quick to verify open

**Priority 3: `/dev/apusys` black-box ioctl analysis**
- Confirmed open from system_app
- Novel surface, likely unaudited MTK proprietary code
- High effort but high potential

**Priority 4: Display DRM remaining OOB cluster**
- SET_PQPARAM u4PartialY confirmed triggerable but limited exploitation ceiling
- SET_SLD_PARAM and other handlers need deeper IDA work
- Possible indirect effects through display register corruption

## Decision

Next target remains **CVE-2023-33200**. IDA now confirms it avoids the JIT/no_user_free_count blocker and has the expected imported-user-buffer lifetime race. The next decision point is whether to continue non-destructive primitive characterization or pivot to the quicker `/dev/ion` reachability check.
