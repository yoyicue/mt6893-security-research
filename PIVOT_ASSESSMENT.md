# Pivot Assessment: Next Target for Kernel Write

Updated: 2026-06-16

## Post-CVE-Search Update (2026-06-16)

A full CVE search was run covering MediaTek bulletins from January 2024 through
March 2025 for MT6893 / Android 13 / System privilege. Full results in
[`docs/CVE_SEARCH_2024_2026.md`](docs/CVE_SEARCH_2024_2026.md).

**New write-what-where primitives found (CWE-123 — all unpatched on target):**

| CVE | Driver | Type | Fix SPL | Directory |
|---|---|---|---|---|
| CVE-2024-20037 | pq | write-what-where | Mar 2024 | `16-cve-2024-20037-pq/` |
| CVE-2024-20118 | mms | write-what-where | Nov 2024 | `18-cve-2024-20118-mms/` |
| CVE-2024-20119 | mms | write-what-where (2nd) | Nov 2024 | `18-cve-2024-20118-mms/` |

**Other high-value new candidates:**

| CVE | Driver | Type | Fix SPL | Directory |
|---|---|---|---|---|
| CVE-2023-32849 | cmdq | OOB write (type confusion) | Oct 2023 | `19-cve-2023-32849-cmdq/` |
| CVE-2024-20032 | aee | Permission bypass | Mar 2024 | `20-cve-2024-20032-aee/` |
| CVE-2024-20109–20115 | ccu | OOB write ×6 | Nov 2024 | `21-cve-2024-20109-ccu/` |

**KASLR defeat primitives (info leaks, all unpatched):**
CVE-2024-20084/85 (power), CVE-2024-20088 (keyinstall), CVE-2024-20095/96 (m4u).

**Updated decision** (supersedes the "Decision" section below):

CVE-2024-20037 **DOWNGRADED** after exhaustive IDA analysis (Tasks 1–6):
- All PQ/AAL/CCORR/GAMMA/COLOR write paths confirmed as cmdq display MMIO writes
- `sub_FFFFFFC0084C089C` confirmed: MMIO-only cmdq write, never kernel memory
- `u4PartialY` OOB is the only user-controlled index; sink is display MMIO
- write-what-where NOT confirmed in any analyzed kernel path
- Remaining candidates (Tasks 7–8: SLD path, set_pqindex→consumer chain)
  are lower probability than mms

**⚠️ ATTRIBUTION CORRECTION (2026-06-16 deep research)**:
CVE-2024-20118/119 are in **`mms`** component (multimedia scheduler), NOT GED.
GED CVE is **CVE-2024-20016** (integer overflow → DoS only, ALPS07835901).
Android 11 public source shows GED KPI has proper ring wrap; our IDA finding
at `ged_kpi_record_POSSIBLE_CVE_2024_20016_DoS` (0x860dc68) is re-attributed.

**Priority 1 → CVE-2024-20118/119 mms write-what-where (corrected)**:
- Component: `mms` multimedia scheduler kernel driver
- Path: `system_app` → SurfaceFlinger binder → MMS HIDL service → `/dev/mtk-mdp`
  (system_app CANNOT directly access mtk_mdp_device; indirect via surfaceflinger)
- GED `/proc/ged` confirms CVE-2024-20016 access (DoS) but not EoP primitive
- No ALPS patch ID published ("External report source") — source mapping unclear
Priority 2 → **CVE-2024-20032 aee permission bypass CONFIRMED** (2026-06-16):
  system_app connects to @android:aee_aed (root, uid=0) — 6/12 sockets accessible
  IDA: aed_ioctl_handler_NO_UID_CHECK at 0xffffffc0087b1868 — no UID check
  Exploit path: send AE_REQ_COREDUMP via socket → read /data/vendor/aee_exp → KASLR
  Next: reverse libaedv.so AE_REQ_* wire protocol, send valid request
Priority 3 → CVE-2024-20037 remaining tasks (cmdq buffer overflow possibility).
Mali candidates are all confirmed dead; APUSYS plane-redirect is suspended.

---

## Current Position

- uid=1000 system_app bind shell (CVE-2024-31317) ✅
- ART ArtMethod syscall primitive (arbitrary ioctl from pure Java) ✅
- Accessible devices: `/dev/binder`, `/dev/hwbinder`, `/dev/mali0`, `/dev/dri/card0`, `/dev/apusys`, `/dev/aw_smartpa`
- Visible but direct-open denied from `system_app`: `/dev/ion`, `/dev/ashmem`
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
| 2 | `/dev/apusys` | **Medium-high research priority** | Confirmed open proprietary MTK ioctl surface; provider opcode-0 dispatch is live for MDLA, normal VPU, EDMA, and MDLA RT. HardwareBuffer under `app_process64` supplies a dmabuf fd that both APUSYS memory-create variants import successfully; normal VPU `ucmd` reaches the userspace-compatible `0x8001 + key` lookup path, and `run_cmd_async` reaches parser, queue, scheduler, and normal VPU provider request-size guard paths. |
| 3 | `/dev/ion` MTK heap/ioctl family | **Medium-low for direct system_app node access** | DAC bits look permissive, but dedicated old-ION probing confirms direct `/dev/ion` open returns `EACCES` from `system_app`. |
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

`/dev/ion` DAC is `crw-rw-rw-` (world-writable), SELinux label `ion_device`, but runtime probing from `system_app` returns `EACCES` on direct open.

**Runtime result**: `13_apusys_mem_ion.txt` uses the old ION ABI observed in IDA (`ALLOC 0xc0204900`, `SHARE 0xc0084904`, `FREE 0xc0044901`) and fails before ioctl because `/dev/ion` cannot be opened from `system_app`.

**Why still tracked**: ION is a large attack surface with custom MTK heap implementations, and SPL 2023-06-05 predates several ION fixes. Direct node access is not currently available from this context.

**Action**: Treat direct `/dev/ion` as blocked for `system_app`; revisit via a framework/HAL path that can hand back a dmabuf fd or via a different lab context.

### Tier 2: Medium Promise

#### D. `/dev/apusys` — MTK AI Processing Unit

Confirmed open from system_app. IDA now maps the main midware ioctl dispatcher: `mdw_ioctl` at `0xffffffc00878a0ec`.

**Why interesting**: Private MTK driver, directly open from `uid=1000(system)`, and routes into APUSYS command parsing, VPU/MDLA/EDMA execution, memory import/IOVA, and Reviser resource paths.

**Risk**: Medium-high research priority, with a working framework fd source for memory import and runtime-confirmed `ucmd` / `run_cmd_async` reachability. The dispatcher uses fixed `copy_from_user` sizes and several early validation gates, but `app_process64` can create a HardwareBuffer dmabuf, both APUSYS type-2/type-3 memory-create paths import it successfully, and changing the mapped buffer's first u32 from `0` to `0x8001` changes normal VPU `ucmd` from `EINVAL` to `ENOENT`. Static analysis maps that `ENOENT` to the Normal/Preload lookup-miss path at `0xffffffc0087a0c70`; the empty-list path returns success instead. Userspace `/vendor/lib64/libvpu.so` confirms the lookup payload shape: `VpuStreamImp::getAlgo()` sends `0x8001` plus up to 31 key bytes at payload offset `+4`. The `--ucmd-key=Normal` probe uses that shape and still returns `ENOENT`, while `--ucmd-key=apu_lib_apunn` returns provider success on this target. The command ops table is resolved through kallsyms-derived raw-pointer normalization: `mdw_usr_run_cmd_async` calls `mdw_cmd_create_cmd`, then `mdw_cmd_parse_cmd`, and success queues a handle back to user `+0x00`. Runtime now covers three run-command depths: zero-header parser rejection, valid-header invalid-subcommand rejection at `mdw_cmd_sc_valid invalid type(32)`, and valid normal VPU `type=0x03` provider handoff that stops at `vpu_req_check: invalid size of vpu request`. The most interesting remaining paths are provider request structures and command lifetime/cleanup, not basic reachability.

**Action**: Continue from [`13-apusys-ioctl-surface/README.md`](13-apusys-ioctl-surface/README.md). Map the normal VPU, MDLA, and EDMA request structures before running any command that can submit real hardware work.

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

**Priority 2: `/dev/apusys` memory import / ucmd mapping**
- `/dev/apusys` opens from `system_app`
- Provider opcode-0 dispatch is live
- HardwareBuffer under `app_process64` supplies a usable dmabuf fd; APUSYS type-2/type-3 memory-create both import it and cleanup succeeds
- Direct DRM PRIME, direct ION, direct ashmem, and tested dma-heap paths are blocked or unavailable
- Normal VPU opcode-7 `ucmd` reaches the userspace-compatible `0x8001 + key` lookup path and returns `ENOENT` for empty and `Normal` keys; current static analysis interprets this as a Normal/Preload lookup miss through the resolved `vpu_alg_get` callback
- `mdw_usr_run_cmd_async` command ops are resolved: user `+0x0c` must be zero, `mdw_cmd_create_cmd` maps the fd-backed command buffer, `mdw_cmd_parse_cmd` validates it, and a zero-header HardwareBuffer command buffer returns `EINVAL` before queue insertion

**Priority 3: ION CVEs via non-direct paths**
- Direct `/dev/ion` open is `EACCES` from `system_app`
- Direct node access remains blocked; framework-created dmabufs are the practical path from `system_app`
- CVE-2023-20768 remains interesting for broader device contexts, not for direct node access here

**Priority 4: Display DRM remaining OOB cluster**
- SET_PQPARAM u4PartialY confirmed triggerable but limited exploitation ceiling
- SET_SLD_PARAM and other handlers need deeper IDA work
- Possible indirect effects through display register corruption

## Decision

Next target remains **CVE-2023-33200** for Mali-specific work. For APUSYS, the immediate branch is now recovering actual Normal/Preload VPU algorithm keys and valid APUSYS command-buffer layout after the HardwareBuffer-backed `0x8001 + key` and `run_cmd_async` parser gates. Direct `/dev/ion` and `/dev/ashmem` reachability have now been checked and are blocked from `system_app`; tested `/dev/dma_heap/*` nodes are absent.
