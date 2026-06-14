# uid=1000 System Privilege Risk Assessment

## Summary

The target device reports Android security patch level `2023-06-05` on MT6893/MT8797. CVE-2024-31317 has already provided code execution as `uid=1000(system)` in the `system_app` SELinux domain.

This changes the risk model for MediaTek vendor vulnerabilities whose advisory precondition is `System execution privileges needed` or `already obtained the System privilege`. Those bugs were not practically reachable from ordinary app/shell contexts, but the privilege precondition is now partially satisfied.

This does not mean every listed CVE is exploitable. Each candidate still needs a reachable entry point, compatible SELinux allow rules, correct device node or service access, and a matching vulnerable code path in the actual firmware binary.

Runtime update: after running the safe PoCs and reachability probes on 2026-06-14, the post-run ranking is recorded in [`poc-run-results/2026-06-14-batch/README.md`](poc-run-results/2026-06-14-batch/README.md). That result supersedes the theoretical ordering below when they differ.

## Confirmed Runtime Access

Current `system_app` context:

```text
uid=1000(system) gid=1000(system) groups=1000(system),3003(inet)
context=u:r:system_app:s0
```

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
| CVE-2024-31317 Framework/Zygote | Confirmed exploited | Runtime `uid=1000 system_app` shell obtained. This is now the privilege bridge for second-stage vendor bugs. |
| CVE-2023-32834 secmem | High increase | MediaTek 2023-11 lists High EoP, System privileges required, MT6893/MT8797/Android 13 affected. |
| CVE-2023-32835 keyinstall | High increase | Same bulletin class as secmem: High EoP, System privileges required, MT6893/MT8797/Android 13 affected. |
| CVE-2023-32836 display | Access precondition satisfied, tested direct paths not vulnerable | `/dev/dri/card0` is reachable, but current `CREATE_DUMB` path uses MTK 64-bit multiply and returns `-EINVAL`; `mtk_plane_atomic_update` lacks the historical MVA offset pattern; direct `DRM_MODE_ATOMIC TEST_ONLY` returns `-EACCES`. |
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
| CVE-2023-32867 | display drm | OOB write; MT6893 and Android 13 affected. |
| CVE-2023-32868 | display drm | OOB write; MT6893 and Android 13 affected. |

The current CVE-2023-32836 checks disprove both tested direct paths on this firmware: `CREATE_DUMB` uses the MTK 64-bit size calculation, and direct atomic commit from `system_app` is denied with `-EACCES`. This does not prove the rest of the display driver is safe.

## Medium-High Priority Candidates

| CVE / Area | Risk Change | Rationale |
|---|---|---|
| CVE-2023-32849 cmdq | Increased | MT6893/MT8797/Android 13 affected. CMDQ is commonly tied to display/MDP paths. |
| CVE-2023-32866 mmp | Increased | MT6893/Android 13 affected; needs `/dev/mmp` or related service/path validation. |
| CVE-2024-20032 aee | Increased | MT6893/Android 13 affected; System privilege prerequisite satisfied. |
| CVE-2024-20037 pq | Increased | MT6893/Android 13 affected; advisory describes write-what-where condition. |
| CVE-2023-20761 ril | Increased, likely service path | MT6893/MT8797/Android 13 affected; likely binder/socket/service rather than direct device-node ioctl. |
| CVE-2023-20766 gps | Increased, likely service path | MT6893/MT8797/Android 13 affected; likely service path. |
| CVE-2023-20768 ion | Lower for direct node access | `/dev/ion` is visible and DAC-permissive, but dedicated old-ION probing from `system_app` returns `EACCES` at open. Revisit only through framework/HAL dmabuf paths or another lab context. |
| APUSYS ioctl surface | Medium-high, rising | `/dev/apusys` opens from `system_app`; provider opcode-0 dispatch, memory-create, and normal VPU opcode-7 `ucmd` are mapped. Direct node fd sources are constrained, but `app_process64` can create a HardwareBuffer dmabuf, both APUSYS type-2/type-3 memory-create paths import it successfully, and normal VPU `ucmd` reaches beyond the `0x8001` mapped-buffer gate to a Normal/Preload lookup miss (`ENOENT`). |

## Not Significantly Changed

| Area | Reason |
|---|---|
| Mali CVE-2022-38181 / CVE-2023-4211 | `/dev/mali0` was already world-writable from shell. Existing analysis shows `no_user_free_count` blocks the key JIT free/change path. |
| CVE-2022-36449 Mali | Existing refcount retraction still stands: `MEM_FREE` drops the GPU ref but the user mmap still holds the page. |
| CVE-2023-4622 AF_UNIX | Not gated by `uid=1000`; existing blocker is the `SOCK_DEAD` ordering constraint. |
| sk_buff offset divergence | This is an exploit-porting correction, not a newly reachable bug. |
| GPU WRITE_VALUE boundary | Primitive already available from shell via `/dev/mali0`; still bounded to GPU-mapped userspace VA. |

## Recommended Next Triage

1. Display/display-drm: enumerate ioctls and symbol paths for CVE-2023-20775 and CVE-2023-32860/32867/32868. For CVE-2023-32863, CVE-2023-32864, and CVE-2023-32865, prioritize locating the exact `ALPS07326314` / `ALPS07292187` / `ALPS07363456` patched handlers because the first probes did not confirm exploitable paths.
2. APUSYS: resolve the raw VPU algo ops address form after the HardwareBuffer-backed `0x8001` gate, then confirm the candidate `payload+4` lookup ABI before any matching-key runtime test.
3. secmem/keyinstall: identify runtime entry points, likely trusted execution / key management interfaces, then test reachability from `system_app`.
4. CMDQ/PQ/MMP: map device nodes, binder services, and ioctl numbers; prioritize any path accessible from `system_app`.
5. DA/AEE/LK: determine whether the advisory entry point exists at runtime or only during update/boot flows.
6. RIL/GPS: check binder/socket/service permissions under `system_app`, not just device nodes.

## Sources

- Android Security Bulletin, June 2024: `https://source.android.com/docs/security/bulletin/2024-06-01`
- MediaTek Product Security Bulletin, July 2023: `https://corp.mediatek.com/product-security-bulletin/July-2023`
- MediaTek Product Security Bulletin, November 2023: `https://corp.mediatek.com/product-security-bulletin/November-2023`
- MediaTek Product Security Bulletin, December 2023: `https://corp.mediatek.com/product-security-bulletin/December-2023`
- MediaTek Product Security Bulletin, March 2024: `https://corp.mediatek.com/product-security-bulletin/March-2024`
