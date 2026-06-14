# 2026-06-14 Batch PoC Risk Ranking After Runtime Tests

## Scope

This file records the actual risk ordering after running the current safe PoCs and reachability probes on the MT6893/MT8797 target.

Target state:

```text
Android SPL: 2023-06-05
SoC / hardware: mt6893 / mt8797
Privilege: uid=1000(system), context=u:r:system_app:s0
```

Raw outputs from this run are archived in this directory.

## Commands / Evidence Files

| Evidence | Purpose |
|---|---|
| `07_drm_trigger.txt` | system_app Java syscall + DRM `CREATE_DUMB` trigger test |
| `07_atomicprobe.txt` | safe DRM atomic cap/resource/empty `TEST_ONLY` probe |
| `07_mtkprobe.txt` | safe MTK private DRM getter reachability probe |
| `07_mtkwriteguard.txt` | invalid-input MTK register read/write guard probe |
| `10_32865_color_transform.txt` | non-destructive `MTK_SUPPORT_COLOR_TRANSFORM` reachability/guard probe |
| `07_devprobe.txt` | system_app raw `openat(O_RDWR)` device-node reachability |
| `systemapp_service_probe.txt` | binder/service and sensitive device-node visibility from system_app |
| `13_apusys_dev_ctrl.txt` | APUSYS `0x400C4109` provider opcode-0 reachability from system_app |
| `13_apusys_mem_dmabuf.txt` | APUSYS memory-create fd-source check using DRM dumb buffer plus PRIME export |
| `13_apusys_mem_ion.txt` | APUSYS memory-create fd-source check using old ION allocation/share path |
| `13_apusys_fd_scan.txt` | APUSYS memory-create candidate-fd scan for dma-heap and ordinary openable fds |
| `13_apusys_ucmd_negative.txt` | APUSYS normal VPU `ucmd` negative with offset 0, nonzero length, and bad fd |
| `13_apusys_hardwarebuffer.txt` | APUSYS HardwareBuffer fd-source negative control under plain `dalvikvm64` |
| `13_apusys_hardwarebuffer_app_process.txt` | APUSYS HardwareBuffer fd-source positive import under `app_process64` |
| `13_apusys_ucmd_hardwarebuffer.txt` | APUSYS normal VPU `ucmd` gate check using ImageWriter-backed HardwareBuffer contents |
| `13_apusys_ucmd_key_normal.txt` | APUSYS normal VPU `ucmd` lookup check with `0x8001 + "Normal"` payload |
| `13_apusys_ucmd_key_neuron_algos.txt` | APUSYS normal VPU `ucmd` lookup check with `libneuron_platform.vpu.so` `kAlgoNames` candidates |
| `13_apusys_ucmd_keydump_apunn.txt` | APUSYS `apu_lib_apunn` success-path keydump before/after core `0` and `1` lookup returns |
| `13_apusys_run_cmd_hardwarebuffer.txt` | APUSYS `run_cmd_async` parser check using ImageWriter-backed HardwareBuffer contents |
| `02_vuln_check_jit.txt` | Mali JIT `DONT_NEED` check for CVE-2022-38181 style chain |
| `02_diag_dont_need.txt` | Mali non-JIT `DONT_NEED` behavior |
| `03_diag_refcount.txt` | CVE-2022-36449 page refcount diagnostic |
| `05_probe_write_value.txt` | Mali WRITE_VALUE primitive baseline |
| `05_probe_mprotect_bypass.txt` | Mali WRITE_VALUE boundary / mprotect bypass probe |
| `04_skb_spray_demo.txt` | sk_buff offset divergence demo |
| `01_trigger_race_3s.txt` | short AF_UNIX race run; not a long exploit attempt |

No destructive `overflow` mode was run.

## Actual Risk Ranking After PoC Runs

### 1. Batch 1: Display / DRM Cluster

Risk after test: **highest practical next target, but CVE-2023-32836 dumb-create path is downgraded**.

Evidence:

```text
[OPEN] /dev/dri/card0  fd=5
[*] openat ret=5
[*] ioctl ret=-22 (EINVAL)
TRIGGER_EXIT:0
```

Interpretation:

- `system_app` can open `/dev/dri/card0` with `O_RDWR`.
- Java syscall infrastructure can issue DRM ioctls from `system_app`.
- Current CVE-2023-32836 `CREATE_DUMB` overflow candidate is rejected with `-EINVAL`.
- IDA/runtime evidence says this path uses the MTK 64-bit multiply implementation, not the vulnerable 32-bit helper.
- The plane/atomic entry is only partially reachable: `SET_CLIENT_CAP ATOMIC` and plane enumeration work, but empty `DRM_MODE_ATOMIC_TEST_ONLY` returns `-EACCES`.
- MTK private DRM getter probing is also partial: `GET_DISPLAY_CAPS` and `GET_SESSION_INFO` return `-EACCES`, while `GET_MASTER_INFO`, `GET_LCM_INDEX`, and `AAL_GET_SIZE` succeed.
- MTK private register write probing shows the non-auth register ioctls are reachable, but invalid physical register addresses are rejected before dispatch: `MTK_WRITE_REG(0xffffffff)` returns `-EFAULT`.
- CVE-2023-32865 adjacent color-transform probing confirms `MTK_SUPPORT_COLOR_TRANSFORM` is reachable: an all-zero matrix returns `0`, and an unsupported matrix offset returns `-EFAULT` before state update.

Resulting CVE priority:

| CVE | Post-run Risk | Reason |
|---|---|---|
| CVE-2023-32863 | Medium-High | display-drm OOB read class; `/dev/dri/card0` reachable, but tested private getter path is partially permission-gated and no OOB-read shape has been identified yet. |
| CVE-2023-32864 | Medium-High | display-drm OOB write class; register-write ioctls are reachable, but current `WRITE_REG` path has physical-address validation and invalid probes are rejected. |
| CVE-2023-32865 | Medium-High | display-drm OOB write class; `MTK_SUPPORT_COLOR_TRANSFORM` validation path is reachable and guarded, while adjacent PQ/AAL write paths are state-changing and no exploitable OOB-write shape is confirmed yet. |
| CVE-2023-32867 | High | display-drm OOB write class; DRM device is reachable. |
| CVE-2023-32868 | High | display-drm OOB write class; DRM device is reachable. |
| CVE-2023-20775 | Medium-High | display classic buffer overflow; exact entry point not yet mapped. |
| CVE-2023-32860 | Medium-High | display classic buffer overflow; exact entry point not yet mapped. |
| CVE-2023-32836 | Low for tested direct paths | `CREATE_DUMB` returns `-EINVAL`; IDA does not show the historical `mtk_plane_atomic_update` MVA offset pattern; direct atomic commit returns `-EACCES`. |

Next experiment:

- Continue display-specific private ioctl mapping in IDA, focusing on handlers with user-controlled indexes or lengths.
- Move next to `CVE-2023-32867` and `CVE-2023-32868`; 32865 now has reachability evidence but no confirmed vulnerable write path.
- For `CVE-2023-32863` / `CVE-2023-32864` / `CVE-2023-32865`, obtain or reconstruct the exact ALPS patches before adding any more PoC logic.

### 2. Batch 4: APU / ION

Risk after test: **APUSYS moves up inside the medium-high tier; direct ION from system_app moves down**.

Evidence:

```text
[OPEN] /dev/apusys  fd=5
[*] devctl_mdla_c0     cmd=0x400c4109 ret=0
[*] devctl_vpu_c0      cmd=0x400c4109 ret=0
[*] devctl_edma_c0     cmd=0x400c4109 ret=0
[*] devctl_mdla_rt_c0  cmd=0x400c4109 ret=0
[*] devctl_vpu_rt_c0   cmd=0x400c4109 ret=-13 (EACCES)
[+] Opened /dev/dri/card0 fd=7
[*] drm_create_dumb   cmd=0xc02064b2 ret=0
[*] drm_prime_to_fd   cmd=0xc00c642d ret=-13 (EACCES)
[-] open /dev/ion failed: open(/dev/ion) failed: errno=13
[-] fdscan_open_dma_heap_system failed: open(/dev/dma_heap/system) failed: errno=2
[-] fdscan_open_ashmem failed: open(/dev/ashmem) failed: errno=13
[*] fdscan2_dri_card0  cmd=0xc0384103 ret=-12 (ENOMEM)
[*] ucmd_vpu_c0_badfd  cmd=0x4014410e ret=-22 (EINVAL)
[+] HardwareBuffer created: id=<java.lang.NoSuchMethodException: android.hardware.HardwareBuffer.getId []> width=64 height=64 format=1 layers=1 usage=0x133
[*] HardwareBuffer parcel: dataSize=436 hasFd=true describe=0x1
[+] parcel_fd_pos_388 fd=57
[*] hwb2_pos388        cmd=0xc0384103 ret=0
[+] hwb2_pos388 succeeded; id=0x2d, cleaning up
[*] hwb3_pos388        cmd=0xc038410f ret=0
[+] hwb3_pos388 succeeded; id=0x2d, cleaning up
[*] ucmd_hwb_zero_c0_pos388 cmd=0x4014410e ret=-22 (EINVAL)
[*] ucmd_hwb_zero_c1_pos388 cmd=0x4014410e ret=-22 (EINVAL)
[*] ucmd_hwb_gate8001_c0_pos388 cmd=0x4014410e ret=-2 (ENOENT)
[*] ucmd_hwb_gate8001_c1_pos388 cmd=0x4014410e ret=-2 (ENOENT)
[*] ucmd_hwb_key_Normal_c0_pos388 cmd=0x4014410e ret=-2 (ENOENT)
[*] ucmd_hwb_key_Normal_c1_pos388 cmd=0x4014410e ret=-2 (ENOENT)
[*] ucmd_hwb_key_apu_lib_apunn_c0_pos388 cmd=0x4014410e ret=0
[*] ucmd_hwb_key_apu_lib_apunn_c1_pos388 cmd=0x4014410e ret=0
[*] ucmd_hwb_key_apu_lib_custom_c0_pos388 cmd=0x4014410e ret=-2 (ENOENT)
[*] ucmd_hwb_key_apu_lib_custom_c1_pos388 cmd=0x4014410e ret=-2 (ENOENT)
[*] run_async_hwb_run_zero_pos388 cmd=0xc0184107 ret=-22 (EINVAL)
```

`systemapp_service_probe.txt` shows `/dev/ion` exists and is world-readable/writable at DAC level:

```text
crw-rw-rw- 1 system graphics u:object_r:ion_device:s0 /dev/ion
```

`07_devprobe.txt` did not list `/dev/ion` as successfully opened with raw `openat(O_RDWR)`, and `13_apusys_mem_ion.txt` confirms the dedicated old-ION path also fails at open with `EACCES` from `system_app`.

Interpretation:

- `/dev/apusys` is a real reachable kernel surface from `system_app`.
- APUSYS device-control ioctl `0x400C4109` reaches live provider opcode-0 paths for MDLA, normal VPU, EDMA, and MDLA RT. VPU RT returns `EACCES` on the same opcode.
- APUSYS memory-create type-2/type-3 is now the highest APUSYS subpath with a working fd source: `app_process64` can create an `android.hardware.HardwareBuffer`, extract a fd-bearing Parcel entry, and import it through both memory-create variants.
- APUSYS `mdw_usr_ucmd` now has a runtime-confirmed normal VPU opcode-7 content gate: with the HardwareBuffer fd source, offset `0`, nonzero length, device id `3`, and a live core id, first mapped u32 `0` returns `EINVAL`, while first mapped u32 `0x8001` moves into the Normal/Preload key lookup path. Empty, `Normal`, `unknown`, and `apu_lib_custom` keys return `ENOENT`, while `apu_lib_apunn` returns `0` for core `0` and `1`.
- Userspace `/vendor/lib64/libvpu.so` confirms the lookup payload shape: `VpuStreamImp::getAlgo()` sends `0x8001` plus up to 31 key bytes at payload offset `+4`. `/system/lib64/libneuron_platform.vpu.so::XrpVpuStream::kAlgoNames` provides the tested candidates `unknown`, `apu_lib_apunn`, and `apu_lib_custom`; `apu_lib_apunn` is a loaded algorithm key on this target.
- The `apu_lib_apunn` keydump run shows the first 64 Image-plane bytes are unchanged before core `0`, after core `0`, and after core `1` success returns. IDA maps the success branch to `vpu_alg_get` followed by `vpu_alg_put`, so the current opcode-7 result is an algorithm lookup/refcount path rather than a visible user-buffer writeback.
- APUSYS `mdw_usr_run_cmd_async` command ops are now resolved through kallsyms-derived raw-pointer normalization. With the same HardwareBuffer fd source, a zero command buffer reaches memory import and returns `EINVAL` from the parser path before queue insertion.
- Candidate-fd scanning shows no tested `/dev/dma_heap/*` nodes, `/dev/ashmem` open is denied, and ordinary openable non-dmabuf fds fail memory-create with `ENOMEM`.
- Normal VPU `ucmd` with offset `0`, nonzero length, and bad fd fails cleanly with `EINVAL` for core `0` and `1`.
- DRM dumb buffer creation works from `system_app`, but PRIME fd export returns `EACCES`.
- Direct `/dev/ion` allocation/share is blocked at open with `EACCES` despite permissive-looking DAC bits on the node.
- Plain `dalvikvm64` cannot create `HardwareBuffer` because framework native libraries are not available in its classloader namespace. The same dex under `app_process64` has working `HwBinder/HwParcel` JNI and can create/import the buffer.
- Mali WRITE_VALUE works, but it was already reachable from uid=2000 shell and remains bounded to GPU-mapped userspace VA. It does not become a kernel primitive from uid=1000 alone.

Resulting CVE priority:

| CVE / Area | Post-run Risk | Reason |
|---|---|---|
| APUSYS CVE candidates | High research priority | `/dev/apusys` opens with `O_RDWR`; `0x400C4109` provider opcode-0 dispatch is live for MDLA, normal VPU, EDMA, and MDLA RT. HardwareBuffer under `app_process64` provides a usable dmabuf fd, both APUSYS memory-create variants import it successfully, normal VPU `ucmd` reaches the `0x8001 + key` lookup path, `apu_lib_apunn` returns provider success without visible first-64-byte writeback, and `run_cmd_async` reaches the parser path with a zero-header buffer. |
| CVE-2023-20768 / ION | Medium-Low for direct system_app node access | Dedicated old-ION path confirms `/dev/ion` open returns `EACCES` from `system_app`. |
| Mali WRITE_VALUE boundary | Low for kernel LPE | WRITE_VALUE confirmed, but USER_BUFFER/kernel reachability is blocked. |

Mali evidence:

```text
WRITE_IMM64 -> DONE, WRITE CONFIRMED
USER_BUFFER import -> event=0x4, target unchanged
GPU can only reach SAME_VA regions (userspace only)
```

### 3. Batch 2: secmem / keyinstall / TEE-mediated Key Paths

Risk after test: **still important, but below directly reachable DRM/APUSYS paths**.

Evidence:

`systemapp_service_probe.txt` shows key/security services are visible:

```text
android.security.authorization
android.security.legacykeystore
android.security.maintenance
android.service.gatekeeper.IGateKeeperService
android.system.keystore2.IKeystoreService/default
sec_key_att_app_id_provider
secure_element
```

But sensitive TEE/secure device nodes are blocked from `system_app`:

```text
ls: /dev/gz_kree: Permission denied
ls: /dev/trusty-ipc-dev0: Permission denied
ls: /dev/sec: Permission denied
ls: /dev/rpmb0: Permission denied
```

Interpretation:

- Direct TEE device access is not available from this `system_app` shell.
- secmem/keyinstall candidates remain plausible only through framework/binder/keystore-mediated entry points.
- This is worth testing, but requires service-specific PoCs, not simple device-node ioctl probes.

Resulting CVE priority:

| CVE | Post-run Risk | Reason |
|---|---|---|
| CVE-2023-32834 secmem | Medium-High | System privilege prerequisite satisfied, but direct secure device access blocked. |
| CVE-2023-32835 keyinstall | Medium-High | Key services visible; direct secure nodes blocked; needs service-level trigger. |

Next experiment:

- Build a non-destructive keystore/keymint/gatekeeper service reachability probe.
- Avoid assuming `/dev/gz_kree` access; current evidence contradicts direct-node access.

### 4. Batch 3: PQ / CMDQ / MMP

Risk after test: **downgraded for direct device-node attack from `system_app`**.

Evidence:

```text
ls: /dev/mtk_mdp: Permission denied
ls: /dev/mmp: Permission denied
ls: /dev/mdp_sync: Permission denied
```

`07_devprobe.txt` did not show any of these nodes as `O_RDWR`-openable.

Interpretation:

- Direct `/dev/mtk_mdp`, `/dev/mmp`, and `/dev/mdp_sync` probing is blocked.
- CMDQ/PQ/MMP bugs may still be reachable indirectly through display/media services, but current direct runtime evidence is weaker than DRM/APUSYS.

Resulting CVE priority:

| CVE / Area | Post-run Risk | Reason |
|---|---|---|
| CVE-2023-32849 cmdq | Medium | Possible indirect display/MDP path; direct node not confirmed. |
| CVE-2023-32866 mmp | Low-Medium | `/dev/mmp` blocked from system_app in current probe. |
| CVE-2024-20037 pq | Medium | Advisory looks interesting, but no direct entry point confirmed yet. |

## Existing PoC Results That Do Not Raise uid=1000 Risk

| Area | Runtime Result | Risk Effect |
|---|---|---|
| Mali CVE-2022-38181 / CVE-2023-4211 style JIT chain | `DONT_NEED on JIT: EINVAL`; `no_user_free_count` blocks it | Keep downgraded |
| CVE-2022-36449 | USER_BUFFER `MEM_FREE` returns OK, but user page sentinel remains; page not freed | Keep downgraded |
| Mali WRITE_VALUE | Writes SAME_VA and bypasses CPU mprotect, but USER_BUFFER target is unchanged | Keep bounded to userspace |
| CVE-2023-4622 AF_UNIX | 3s run: 114 iterations, 97.4% `EPIPE`, no bypass signal | No uid=1000-specific uplift |
| sk_buff offsets | Demo confirms x86 offsets crash on ARM64; correct offsets needed | Porting constraint, not new risk |

## Final Post-Run Order

1. **Display / DRM OOB read/write cluster**: `CVE-2023-32867`, `32868`, then `32865`, `32864`, `32863`, `20775`, `32860`. `32864` and `32865` remain reachable but the first guard probes did not confirm exploitable write paths.
2. **APUSYS reachable surface**: APUSYS-related CVEs should be mapped next because `/dev/apusys` opens from `system_app`, provider dispatch is live, HardwareBuffer under `app_process64` supplies a dmabuf fd that APUSYS imports successfully, normal VPU `ucmd` reaches the userspace-compatible `0x8001 + key` lookup path, `apu_lib_apunn` returns provider success without visible first-64-byte writeback, and `run_cmd_async` reaches the parser path. The next APUSYS step is recovering the request and command-buffer layouts that lead into provider execution.
3. **secmem / keyinstall via service paths**: `CVE-2023-32834`, `CVE-2023-32835`; direct secure nodes are blocked, so service PoC needed.
4. **CMDQ / PQ / MMP indirect paths**: `CVE-2023-32849`, `CVE-2024-20037`, `CVE-2023-32866`; direct nodes blocked.
5. **ION**: keep as pending until strict open/ioctl reachability is resolved.
6. **Mali old chains / AF_UNIX / sk_buff offset issue**: not promoted by `uid=1000`.

## Notes

- This ranking is based on safe PoCs and reachability probes only.
- No kernel-corrupting OOB write was executed.
- A candidate can move up again if IDA identifies a reachable ioctl/service path, or if an existing framework/HAL fd source unlocks a deeper path.
