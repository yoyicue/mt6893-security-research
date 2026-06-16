# uid=1000 System Privilege Risk Assessment

## Summary

The target device reports Android security patch level `2023-06-05` on MT6893/MT8797. CVE-2024-31317 has already provided code execution as `uid=1000(system)` in the `system_app` SELinux domain.

This changes the risk model for MediaTek vendor vulnerabilities whose advisory precondition is `System execution privileges needed` or `already obtained the System privilege`. Those bugs were not practically reachable from ordinary app/shell contexts, but the privilege precondition is now partially satisfied.

This does not mean every listed CVE is exploitable. Each candidate still needs a reachable entry point, compatible SELinux allow rules, correct device node or service access, and a matching vulnerable code path in the actual firmware binary.

Runtime update (2026-06-16): Two capabilities are now confirmed; one remains open.

- **KASLR CONFIRMED**: `system_app` triggers `MTK_SET_PQPARAM.u4PartialY=0xffffffff` → kernel panic → `platform_app` reads `/data/vendor/aee_exp/db.fatal.*.KE.dbg` directly (no reboot-chmod wait; `platform_app_31_0` policy grants `aee_exp_vendor_file` read). Extracted backtrace contains raw `%px` kernel text VAs. Current boot: `selected_runtime_base=0xffffff9cfde00000`, delta=`-0x230a180000`, `phase4_direct_write_args=-D -b 0xffffff9cfde00000`. Tools: `20-cve-2024-20032-aee/tools/aee_ke_collect.py`, `aee_dbg_unpack.py`, `kaslr_summary_addr.py`.
- **AEE permission bypass CONFIRMED**: `system_app` connects to `@android:aee_aed` (uid=0 peer), drives full session, triggers ANR dumps; `system_server` trace obtained at `/data/anr/trace_*`. Wire protocol reversed (24-byte AE_IND/REQ/RSP headers). Coredump paths blocked on this build (`persist.vendor.aeev.core.dump=disable`).
- **APUSYS VPU dispatch CONFIRMED**: `/dev/apusys` open, HardwareBuffer-backed dmabuf import, `apu_lib_apunn` algorithm success, full-size 0xb70 request, APUNN completion (settings `0x5→0x7`), descriptor-plane redirect (+1 plane writeback). Exact IOVA reuse (`target_then_lower`) is the live write-primitive track.
- **Write primitive: OPEN** — APUSYS IOVA reuse and mms write-what-where (CVE-2024-20118/119 via SurfaceFlinger) are the two candidates.

These runtime results supersede the earlier theoretical ordering when they differ.

## Confirmed Runtime Access

Current `system_app` context:

```text
uid=1000(system) gid=1000(system) groups=1000(system),3003(inet)
context=u:r:system_app:s0
```

Current `platform_app` context (CVE-2024-31317 variant, `exploit_platform.py`):

```text
uid=10028(u0_a28) context=u:r:platform_app:s0:c512,c768
```

Additional `platform_app` access confirmed:
- `/data/vendor/aee_exp/` read/search (`aee_exp_vendor_file`) — critical for KE DB extraction
- All `system_app` device access is also available

Device state:

```text
ro.build.version.security_patch = 2023-06-05
ro.board.platform = mt6893
ro.hardware = mt8797
ro.product.board = ls12_mt8797_wifi_64
```

Confirmed useful device access from `system_app`:

```text
/dev/dri/card0
/dev/mali0
/dev/apusys
```

Visible but direct-open denied from `system_app`:

```text
/dev/ion
/dev/ashmem
```

Important permissions observed on device:

```text
/dev/dri/card0  crw-rw---- system system  u:object_r:dri_device:s0
/dev/ion        crw-rw-rw- system graphics u:object_r:ion_device:s0
/dev/ashmem     crw-rw-rw- root   root     u:object_r:ashmem_device:s0
/dev/mali0      crw-rw-rw- root   root     u:object_r:gpu_device:s0
/dev/apusys     crw-rw---- system camera   u:object_r:apusys_device:s0
```

## Highest Priority Candidates

| CVE / Area | Risk Change | Rationale |
|---|---|---|
| CVE-2024-31317 Framework/Zygote | Confirmed exploited | `uid=1000 system_app` shell + `platform_app` shell both obtained. `platform_app` reads `aee_exp_vendor_file`, completing the KE-first KASLR path. |
| CVE-2023-32834 secmem | High increase | MediaTek 2023-11 lists High EoP, System privileges required, MT6893/MT8797/Android 13 affected. |
| CVE-2023-32835 keyinstall | High increase | Same bulletin class as secmem: High EoP, System privileges required, MT6893/MT8797/Android 13 affected. |
| CVE-2023-32836 display | Access precondition satisfied, tested direct paths not vulnerable | `/dev/dri/card0` is reachable, but current `CREATE_DUMB` path uses MTK 64-bit multiply and returns `-EINVAL`; `mtk_plane_atomic_update` lacks the historical MVA offset pattern; direct `DRM_MODE_ATOMIC TEST_ONLY` returns `-EACCES`. |
| APUSYS ioctl surface | **Live write-primitive track** | VPU dispatch (0xb70 request), `apu_lib_apunn` algorithm success, and APUNN completion (settings `0x5→0x7`) confirmed. Descriptor-plane redirect produces `plane_payload[0]+1` writeback following native descriptor 0. iDMA is synchronous — `free_after=0ms` race always lost. Best remaining primitive: descriptor-plane redirect + `~9s` firmware timeout window + exact `target_then_lower` IOVA reuse. See `13-apusys-ioctl-surface/docs/ALLOCATOR_CONTROLLABILITY_OPPORTUNITY.md`. |
| Display DRM `MTK_SET_PQPARAM` | **KASLR trigger ✅** | `u4PartialY=0xffffffff` reliably triggers kernel panic (`DpEngine_COLORonConfig+0x360/0xcf4`). AEE captures `PC is at [<xxxxxxxx>]` + `Kernel Offset` in `/data/vendor/aee_exp/db.fatal.*.KE.dbg` readable by `platform_app`. Two independent boots confirmed slide changes. Not a kernel write primitive (sink is display MMIO). |
| CVE-2024-20005 DA | High increase, entry point unknown | MediaTek 2024-03 says exploitation requires already having System privilege; MT6893/Android 13 affected. |
| CVE-2024-20022 LK | High increase, special entry point | Affects MT8797/Android 13 and requires System privilege, but LK/bootloader paths may not be ordinary runtime ioctls. |
| CVE-2024-20025 / 20027 / 20028 DA | High increase, entry point unknown | MediaTek 2024-03 lists EoP after obtaining System privilege; MT6893/Android 13 affected. |

## Display / DRM Priority Set

Because `/dev/dri/card0` is now reachable from `system_app`, display/display-drm CVEs with System privilege requirements deserve a higher priority than before.

| CVE | Area | Why It Matters |
|---|---|---|
| CVE-2023-20775 | display | Classic buffer overflow; MT6893 and Android 13 affected in the MediaTek 2023-07 bulletin. |
| CVE-2023-32860 | display | Classic buffer overflow; MT6893 and Android 13 affected in the MediaTek 2023-12 bulletin. |
| CVE-2023-32863 | display drm | OOB read; MT6893 and Android 13 affected. Current safe probe shows partial reachability: `GET_DISPLAY_CAPS`/`GET_SESSION_INFO` return `EACCES`, while `GET_MASTER_INFO`/`GET_LCM_INDEX`/`AAL_GET_SIZE` succeed. No OOB-read shape confirmed yet. |
| CVE-2023-32864 | display drm | OOB write; MT6893 and Android 13 affected. Current register-write guard probe shows `WRITE_REG`/`READ_REG` are reachable but invalid physical addresses are rejected with `EFAULT`; `WRITE_SW_REG` unknown ids do not hit bounded table writes. |
| CVE-2023-32865 | display drm | OOB write; MT6893 and Android 13 affected. Current safe probe confirms `MTK_SUPPORT_COLOR_TRANSFORM` is reachable from `system_app`: zero matrix returns success, unsupported offset returns `EFAULT`. No exploitable write path is confirmed yet. |
| CVE-2023-32867 | display drm | Likely maps to the confirmed `MTK_SET_PQPARAM` missing-bounds class. Runtime accepts `u4PartialY >= 22`, causing OOB reads from display global tables and cmdq writes into DISP_COLOR Y-slope registers. Current readback/leak path is blocked by clock gating. |
| CVE-2023-32868 | display drm | Same current treatment as CVE-2023-32867 until the exact ALPS fix mapping is pinned down: real PQPARAM bug confirmed, but current sink is display register state, not kernel memory. |

The current CVE-2023-32836 checks disprove both tested direct paths on this firmware: `CREATE_DUMB` uses the MTK 64-bit size calculation, and direct atomic commit from `system_app` is denied with `-EACCES`. The PQPARAM finding keeps the broader display DRM family security-relevant, but its current exploitation ceiling is limited by the MMIO-only sink and blocked CPU readback.

## Medium-High Priority Candidates

| CVE / Area | Risk Change | Rationale |
|---|---|---|
| CVE-2023-32849 cmdq | Increased | MT6893/MT8797/Android 13 affected. CMDQ is commonly tied to display/MDP paths. |
| CVE-2023-32866 mmp | Increased | MT6893/Android 13 affected; needs `/dev/mmp` or related service/path validation. |
| CVE-2024-20032 aee | **Permission bypass + KASLR path CONFIRMED** | Socket to root-running `aee_aed` (uid=0) works; wire protocol reversed; ANR leak demonstrated. Coredump gate blocked. Used as KASLR trigger chain via display KE → `platform_app` DB read. No further gate work needed unless coredump-path is required for another primitive. |
| CVE-2024-20037 pq | **DOWNGRADED — MMIO sink only** | IDA confirmed all analyzed write paths (PQ/AAL/CCORR/GAMMA/COLOR) are cmdq display MMIO writes, not kernel memory. `u4PartialY` OOB is useful as a KE trigger for KASLR but not as a write-what-where primitive. |
| CVE-2023-20761 ril | Increased, likely service path | MT6893/MT8797/Android 13 affected; likely binder/socket/service rather than direct device-node ioctl. |
| CVE-2023-20766 gps | Increased, likely service path | MT6893/MT8797/Android 13 affected; likely service path. |
| CVE-2023-20768 ion | Lower for direct node access | `/dev/ion` is visible and DAC-permissive, but dedicated old-ION probing from `system_app` returns `EACCES` at open. Revisit only through framework/HAL dmabuf paths or another lab context. |
| APUSYS ioctl surface | **Priority 1 write-primitive track** | Full dispatch chain confirmed: dmabuf import → IOVA → 0xb70 VPU request → `apu_lib_apunn` → APUNN completion (settings `0x5→0x7`). Descriptor-plane redirect (`PLN0→PLN1`) produces `plane_payload[0]+1` writeback but stays inside imported IOVA. Timeout/close/free and dev_ctrl races: no crashes produced. Next experiment: `target_then_lower` exact IOVA reuse during descriptor-plane redirect + `~9s` firmware timeout. See `docs/ALLOCATOR_CONTROLLABILITY_OPPORTUNITY.md` and `docs/CONTROLLED_OPPORTUNITIES.md §6`. |

## Not Significantly Changed

| Area | Reason |
|---|---|
| Mali CVE-2022-38181 / CVE-2023-4211 | `/dev/mali0` was already world-writable from shell. Existing analysis shows `no_user_free_count` blocks the key JIT free/change path. |
| CVE-2022-36449 Mali | Existing refcount retraction still stands: `MEM_FREE` drops the GPU ref but the user mmap still holds the page. |
| CVE-2023-33200 Mali | Downgraded to patched/dead on this target. `kbase_vmap_prot` rejects non-NATIVE allocations, so imported USER_BUF regions cannot enter the soft-event vmap path required for the race. |
| CVE-2023-4622 AF_UNIX | Not gated by `uid=1000`; existing blocker is the `SOCK_DEAD` ordering constraint. |
| sk_buff offset divergence | This is an exploit-porting correction, not a newly reachable bug. |
| GPU WRITE_VALUE boundary | Primitive already available from shell via `/dev/mali0`; still bounded to GPU-mapped userspace VA. |

## Recommended Next Triage (2026-06-16)

KASLR is solved. The gap is a kernel write primitive. Priority order:

1. **APUSYS exact IOVA reuse** (`13-apusys-ioctl-surface/`): Implement `target_then_lower` probe — run descriptor-plane redirect request (produces `~9s` firmware timeout), call `mem_free(shared_iova)` during that window, then import a replacement HardwareBuffer and verify whether it lands at the same IOVA. If replacement-buffer mutation is observed after firmware completes into it, this is a confirmed cross-buffer write primitive. Shape documented in `docs/CONTROLLED_OPPORTUNITIES.md §6`.

2. **mms write-what-where** (`18-cve-2024-20118-mms/`): CVE-2024-20118/119 are in the `mms` multimedia scheduler kernel driver, reached via SurfaceFlinger binder → `vendor.mediatek.hardware.mms@1.6` → `/dev/mtk-mdp`. Identify which SurfaceFlinger layer/buffer API triggers the vulnerable mms handler. The GED path (CVE-2024-20016) is a DoS-only dead end — do not revisit.

3. **KASLR integration**: Once a write primitive is found, feed `selected_runtime_base=0xffffff9cfde00000` (or freshly collected current-boot base via `aee_ke_collect.py`) into the write path. Tools are ready: `kaslr_summary_addr.py --phase4` emits `phase4_direct_write_args=-D -b 0xffffff9cfde00000`.

4. (Lower) **AEE vendor relay for coredump gate**: If a separate non-KE KASLR path is needed, look for a `platform_app`-reachable camera or RIL bridge that calls `rtt_aee_*_enable_disable()` to flip `persist.vendor.aeev.core.dump`. Not currently needed since KE path works.

5. (Parked) secmem, keyinstall, CMDQ/MMP, DA/LK, RIL/GPS: deprioritized until the write primitive gap is closed.

## Sources

- Android Security Bulletin, June 2024: `https://source.android.com/docs/security/bulletin/2024-06-01`
- MediaTek Product Security Bulletin, July 2023: `https://corp.mediatek.com/product-security-bulletin/July-2023`
- MediaTek Product Security Bulletin, November 2023: `https://corp.mediatek.com/product-security-bulletin/November-2023`
- MediaTek Product Security Bulletin, December 2023: `https://corp.mediatek.com/product-security-bulletin/December-2023`
- MediaTek Product Security Bulletin, March 2024: `https://corp.mediatek.com/product-security-bulletin/March-2024`
