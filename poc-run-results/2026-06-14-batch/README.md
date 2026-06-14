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

Risk after test: **APUSYS remains medium-high; direct ION from system_app moves down**.

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
```

`systemapp_service_probe.txt` shows `/dev/ion` exists and is world-readable/writable at DAC level:

```text
crw-rw-rw- 1 system graphics u:object_r:ion_device:s0 /dev/ion
```

`07_devprobe.txt` did not list `/dev/ion` as successfully opened with raw `openat(O_RDWR)`, and `13_apusys_mem_ion.txt` confirms the dedicated old-ION path also fails at open with `EACCES` from `system_app`.

Interpretation:

- `/dev/apusys` is a real reachable kernel surface from `system_app`.
- APUSYS device-control ioctl `0x400C4109` reaches live provider opcode-0 paths for MDLA, normal VPU, EDMA, and MDLA RT. VPU RT returns `EACCES` on the same opcode.
- APUSYS memory-create type-2/type-3 remains the highest APUSYS subpath, but the current experiment lacks a valid dmabuf fd source in this context.
- DRM dumb buffer creation works from `system_app`, but PRIME fd export returns `EACCES`.
- Direct `/dev/ion` allocation/share is blocked at open with `EACCES` despite permissive-looking DAC bits on the node.
- Mali WRITE_VALUE works, but it was already reachable from uid=2000 shell and remains bounded to GPU-mapped userspace VA. It does not become a kernel primitive from uid=1000 alone.

Resulting CVE priority:

| CVE / Area | Post-run Risk | Reason |
|---|---|---|
| APUSYS CVE candidates | Medium-High | `/dev/apusys` opens with `O_RDWR`; `0x400C4109` provider opcode-0 dispatch is live for MDLA, normal VPU, EDMA, and MDLA RT. Memory-create is mapped but still needs a usable dmabuf fd source. |
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
2. **APUSYS reachable surface**: APUSYS-related CVEs should be mapped next because `/dev/apusys` opens from `system_app`.
3. **secmem / keyinstall via service paths**: `CVE-2023-32834`, `CVE-2023-32835`; direct secure nodes are blocked, so service PoC needed.
4. **CMDQ / PQ / MMP indirect paths**: `CVE-2023-32849`, `CVE-2024-20037`, `CVE-2023-32866`; direct nodes blocked.
5. **ION**: keep as pending until strict open/ioctl reachability is resolved.
6. **Mali old chains / AF_UNIX / sk_buff offset issue**: not promoted by `uid=1000`.

## Notes

- This ranking is based on safe PoCs and reachability probes only.
- No kernel-corrupting OOB write was executed.
- A candidate can move up again if IDA identifies a reachable ioctl/service path that does not require blocked device nodes.
