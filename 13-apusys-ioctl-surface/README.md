# APUSYS ioctl surface

## Summary

`/dev/apusys` is reachable from `uid=1000(system)` on the target device:

```text
crw-rw---- 1 system camera u:object_r:apusys_device:s0 /dev/apusys
[OPEN] /dev/apusys  fd=5
```

The current result is **reachable but not yet mapped to a confirmed vulnerability**. IDA analysis shows the device is the MTK APUSYS midware character device. Its main ioctl handler is now named `mdw_ioctl` in the IDB at `0xffffffc00878a0ec`.

The directory has no CVE number yet. The repository uses CVE-numbered directories when a test is tied to a specific public CVE or confirmed bug class. APUSYS is currently an exposed proprietary ioctl surface with handler-level mapping and runtime reachability evidence, but no confirmed CVE match or vulnerability primitive.

This directory documents the ioctl surface and current runtime probes. The Java probe covers reject/query paths, negative memory-create cases, optional device-control reachability checks, direct dmabuf-source checks, a candidate-fd scan for the memory import path, and controlled `ucmd` negative tests. Valid command buffers, usable dmabuf descriptors, heap shaping, and execution-path inputs are separate experiment tracks.

## IDA handler map

The kernel image under analysis is `07-cve-2023-32836-display-overflow/vmlinux.bin.i64`. The relevant APUSYS functions were renamed in IDA and the IDB was saved.

| Function | Address | Role |
|---|---:|---|
| `mdw_ioctl` | `0xffffffc00878a0ec` | `/dev/apusys` `unlocked_ioctl` dispatcher |
| `apusys_midware_register_driver` | `0xffffffc008796d28` | Registers the APUSYS midware device |
| `mdw_usr_mem_create` | `0xffffffc00878bc10` | Allocates/imports APUSYS user memory metadata |
| `mdw_usr_mem_free` | `0xffffffc00878bd14` | Frees APUSYS user memory metadata |
| `mdw_usr_mem_create_type2` | `0xffffffc00878bec4` | Ioctl wrapper for type-2 memory create, links the created object into the user context |
| `mdw_usr_mem_create_type3` | `0xffffffc00878bf68` | Ioctl wrapper for type-3 memory create, links the created object into the user context |
| `mdw_usr_dev_sec_alloc` | `0xffffffc00878c00c` | Secure-device allocation path |
| `mdw_usr_dev_sec_free` | `0xffffffc00878c418` | Secure-device free path, includes an id `< 0x40` guard |
| `mdw_usr_ucmd` | `0xffffffc00878c6ac` | User-command control path |
| `mdw_usr_dev_ctrl_4109` | `0xffffffc00878c7b4` | Handler for ioctl `0x400C4109`; looks up a device/core and calls `mdw_rsc_dev_op0_ctrl` at `+0x70` with timeout `0xbb8` |
| `mdw_usr_run_cmd_async` | `0xffffffc00878c7f8` | Parses and queues an APUSYS command |
| `mdw_usr_get_cmd_ops` | `0xffffffc008791b04` | Returns a static object stored at `0xffffffc00a188e58`; **not yet confirmed as the user command parser ops** |
| `mdw_wait_cmd` | `0xffffffc00878ca68` | Internal wait helper |
| `mdw_usr_wait_cmd` | `0xffffffc00878cd50` | User wait-command path |
| `mdw_usr_run_cmd_sync` | `0xffffffc00878ce18` | `run_cmd_async` followed by wait |
| `mdw_usr_init` | `0xffffffc00878d2c8` | APUSYS user subsystem init |
| `mdw_usr_destroy` | `0xffffffc00878cfc4` | APUSYS user teardown |

Hex-Rays did not decompile the main APUSYS functions cleanly, so this mapping comes from disassembly, string xrefs, ioctl constants, and call targets.

Additional functions named during the APUSYS follow-up pass:

| Function | Address | Role |
|---|---:|---|
| `mdw_mem_copy_user_desc` | `0xffffffc0087961b4` | Copies the 0x38 user memory descriptor into a 0x60 kernel object |
| `mdw_mem_import_type2` | `0xffffffc00879658c` | Type-2 memory import wrapper, calls registered midware memory ops at `+0x30` |
| `mdw_mem_import_type3` | `0xffffffc0087966a0` | Type-3 memory import wrapper, calls memory ops at `+0x20` and `+0x30` |
| `mdw_mem_mgr_init` | `0xffffffc00879698c` | Initializes APUSYS memory manager state and stores the registered midware driver pointer at `0xffffffc00a189078` |
| `apusys_mem_query_kva` | `0xffffffc008796a44` | Looks up imported memory by KVA range |
| `apusys_mem_query_iova` | `0xffffffc008796b88` | Looks up imported memory by IOVA range |
| `mdw_mem_ion_flush` | `0xffffffc008796dfc` | Registered memory op at `+0x10` |
| `mdw_mem_ion_invalidate` | `0xffffffc008796f18` | Registered memory op at `+0x18` |
| `mdw_mem_ion_map_kva` | `0xffffffc008797008` | Registered memory op at `+0x20`, imports a dmabuf fd and maps KVA |
| `mdw_mem_ion_unmap_kva` | `0xffffffc0087971d8` | Registered memory op at `+0x28` |
| `mdw_mem_ion_map_iova` | `0xffffffc008797290` | Registered memory op at `+0x30`, imports a dmabuf fd and maps IOVA |
| `mdw_mem_ion_unmap_iova` | `0xffffffc008797400` | Registered memory op at `+0x38` |
| `mdw_mem_ion_destroy_client` | `0xffffffc0087974a8` | Registered memory op at `+0x40`, tears down the ION client |
| `mdw_mem_ion_check` | `0xffffffc0087974e4` | Validates APUSYS memory descriptor alignment/type before IOVA mapping |
| `ion_import_dma_buf_fd_wrapper` | `0xffffffc008be9318` | ION wrapper used by APUSYS KVA/IOVA map paths |
| `ion_sys_ioctl_cache_sync_wrapper` | `0xffffffc008bfa61c` | Small wrapper into the ION sys-ioctl handler |
| `ion_sys_ioctl_handler` | `0xffffffc008bfa634` | ION sys-ioctl handler reached from the APUSYS IOVA path |
| `mdw_dev_lookup_core` | `0xffffffc00878eb6c` | Looks up a registered APUSYS device/core from the global device table |
| `mdw_dev_get_core_count` | `0xffffffc00878ebb8` | Returns the registered core count for a device id |
| `mdw_rsc_get_dev` | `0xffffffc00878dfcc` | Selects APUSYS device resources for a request |
| `mdw_dev_get_table` | `0xffffffc00878d678` | Returns the per-device table entry from the APUSYS global device table |
| `mdw_dev_get_queue` | `0xffffffc00878d6c0` | Returns a registered device queue pointer from the per-device table |
| `mdw_rsc_update_avl_bmp` | `0xffffffc00878d77c` | Updates APUSYS resource availability bitmap state |
| `mdw_rsc_add_dev` | `0xffffffc00878f14c` | Registers provider device descriptors into the APUSYS resource table and installs midware callbacks |
| `apusys_unregister_device` | `0xffffffc00878f520` | Unregisters APUSYS provider device descriptors |
| `mdw_queue_init` | `0xffffffc008793b14` | Initializes APUSYS command queue state |
| `mdw_queue_insert` | `0xffffffc008793834` | Queue insertion helper |
| `mdw_queue_pop` | `0xffffffc00879376c` | Queue pop helper |
| `mdw_sched_init` | `0xffffffc008792e9c` | Initializes scheduler state and scheduler thread |
| `mdw_sched_routine` | `0xffffffc008792f9c` | Main APUSYS scheduler thread routine |
| `mdw_sched_dev_routine` | `0xffffffc0087920ec` | Per-device scheduler routine |
| `vpu_init_dev_algo_sets` | `0xffffffc0087a5214` | Initializes per-VPU-core Normal and Preload algorithm sets from firmware/bin metadata |
| `vpu_exit_dev_algo_sets` | `0xffffffc0087a56f0` | Tears down the Normal and Preload algorithm lists |

Important correction: reading the static object returned by `mdw_usr_get_cmd_ops` as 64-bit pointers produces `0xffffff800881...` values. After normalizing them to the IDB base, they land around `0xffffffc00881....`, but the referenced locations do not look like normal APUSYS parser function entries and include unrelated MMDVFS/clock strings. `mdw_usr_get_cmd_ops` remains unresolved until its runtime registration/use is proven; parser-target attribution requires independent registration/use evidence.

Memory-ops correction: the APUSYS memory ops behind `0xffffffc00a189078` are now resolved. `mdw_mem_mgr_init` stores the object returned by `apusys_midware_register_driver`; that registration path creates the `"apusys midware"` ION client and installs the `mdw_mem_ion_*` callbacks above. The type-2 create path calls the registered `+0x30` IOVA map op. The type-3 create path calls `+0x20` KVA map first, then `+0x30` IOVA map, and unwinds KVA on IOVA failure.

The 0x38 user memory descriptor is not an fd at offset `+0x00`. `mdw_mem_copy_user_desc` copies the user fd from descriptor offset `+0x20` into the kernel object at `+0x28`; this field is what the ION import wrapper receives. Other relevant checked fields are copied from user `+0x10`, `+0x14`, and `+0x18` into kernel `+0x18`, `+0x1c`, and `+0x20` for alignment/type validation in `mdw_mem_ion_check`.

## Device callback model

`mdw_rsc_add_dev` is the registration point for APUSYS provider devices. It receives a provider descriptor, allocates an internal per-core object of size `0x138`, stores the original provider descriptor pointer at internal offset `+0x30`, and installs a fixed midware callback table into the internal object.

The ioctl paths therefore do not call MDLA/VPU/EDMA handlers directly. They first call fixed midware wrappers from the internal core object, and those wrappers either dispatch into the provider descriptor handler at `provider_desc+0x18` with an opcode or perform internal APUSYS resource bookkeeping.

| Internal core offset | Function | Behavior |
|---:|---|---|
| `+0x68` | `mdw_rsc_dev_exec` | Stores a request pointer at `core+0xc0` and wakes the resource waitqueue |
| `+0x70` | `mdw_rsc_dev_op0_ctrl` | Calls provider handler `+0x18` with opcode `0` and control arguments |
| `+0x78` | `mdw_rsc_dev_op1` | Calls provider handler `+0x18` with opcode `1` |
| `+0x80` | `mdw_rsc_suspend` | Calls provider handler `+0x18` with opcode `3` after resource checks |
| `+0x88` | `mdw_rsc_dev_op2` | Calls provider handler `+0x18` with opcode `2` |
| `+0x90` | `mdw_rsc_dev_op6` | Calls provider handler `+0x18` with opcode `6` |
| `+0x98` | `mdw_rsc_ucmd_dispatch` | Calls provider handler `+0x18` with opcode `7` and ucmd arguments |
| `+0xa0` | `mdw_rsc_sec_on` | Internal secure-resource gate |
| `+0xa8` | `mdw_rsc_sec_off` | Internal secure-resource gate |
| `+0xb0` | `mdw_rsc_lock_dev` | Internal resource lock/refcount path |
| `+0xb8` | `mdw_rsc_unlock_dev` | Internal resource unlock/refcount path |

Provider registration sites identified so far:

| Provider | Registration function | Provider handler | Notes |
|---|---|---|---|
| MDLA | `mdla_probe` at `0xffffffc0087990d8` | `apusys_mdla_handler` at `0xffffffc008798de0` | Registers MDLA and MDLA RT descriptors through `mdw_rsc_add_dev` |
| VPU | `vpu_probe` at `0xffffffc0087a0f18` | `vpu_send_cmd_rt_handler` at `0xffffffc0087a041c`; normal VPU entry at `0xffffffc0087a077c` | Registers provider descriptors for device ids `3` and `0x23`; IDA keeps the normal VPU entry inside the RT handler function range |
| EDMA | `edma_probe` at `0xffffffc0087ab510` | `edma_send_cmd_handler` at `0xffffffc0087ab430` | The handler reaches `edma_execute` at `0xffffffc0087ac584` for the execution opcode |
| Sample | `apusys_sample_device_init` at `0xffffffc0087983f4` | sample handler still lower priority | Sample-device registration path, useful as a structure reference |

This changes the interpretation of the ioctl callback offsets. `0x400C4109` reaches `core+0x70`, then `mdw_rsc_dev_op0_ctrl`, then a provider handler with opcode `0`. `mdw_usr_ucmd` reaches `core+0x98`, then `mdw_rsc_ucmd_dispatch`, then a provider handler with opcode `7`. Secure on/off offsets `+0xa0` and `+0xa8` are internal APUSYS gates rather than provider dispatch calls.

Provider opcode notes from the current static pass:

| Provider path | Opcode behavior |
|---|---|
| `apusys_mdla_handler` | For device id `2`, opcode `0` calls `mdla_pwr_on`, opcode `1`/`3` go through `mdla_pwr_off`, opcode `2` is a success return, and opcode `4` reaches `mdla_run_command_sync`. For device id `0x22`, opcode `4` reaches `mdla_run_command_sync`; other opcodes return without the same command path. |
| normal VPU entry `0xffffffc0087a077c` | Opcode `0` reaches `vpu_send_cmd_op0`, opcode `1` reaches `vpu_send_cmd_op1`, opcode `2` returns success, opcode `3` is suspend/resume bookkeeping, opcode `4` reaches `vpu_execute` / `vpu_execute_with_slot`, and opcode `7` handles mapped ucmd buffers whose first u32 is `0x8001`. |
| `vpu_send_cmd_rt_handler` | Opcode `4` and `5` are execute/preempt paths with request checks; opcode `0`, `1`, `6`, and `7` return through early access/error paths. |
| `edma_send_cmd_handler` | Opcode `0` calls `edma_power_on`, opcode `3` calls `edma_power_off`, opcode `2`/`5` return success, and opcode `4` reaches `edma_execute` only when the argument pointer is non-null and argument field `+0x0c` equals `0x15`. |

`mdw_rsc_dev_op0_ctrl` passes a small stack argument object to providers as `{0, control_value, 0xbb8}`. `mdw_rsc_ucmd_dispatch` passes `{mapped_kva, mem_field_0x10_from_iova_setup, user_field_0x10}`. For `mdw_usr_ucmd`, the APUSYS memory KVA/IOVA mapping and range check must succeed before provider opcode `7` is reached.

### Normal VPU opcode-7 ucmd path

`0x4014410E` / `mdw_usr_ucmd` uses a compact 0x14-byte user argument:

| Offset | Use |
|---:|---|
| `+0x00` | device id passed to `mdw_dev_lookup_core` |
| `+0x04` | core id passed to `mdw_dev_lookup_core` |
| `+0x08` | dmabuf fd, copied into the temporary APUSYS mem object at `+0x28` |
| `+0x0c` | mapped-buffer offset; this build rejects nonzero values before memory mapping |
| `+0x10` | requested mapped length; copied into the temporary APUSYS mem object at `+0x14` and later passed to the provider wrapper |

The path is:

1. `mdw_usr_ucmd` rejects immediately with `EINVAL` if user `+0x0c` is nonzero.
2. It builds a temporary APUSYS memory object on the stack, imports the fd through memory op `+0x20` for KVA mapping, then memory op `+0x30` for IOVA/cache setup.
3. It verifies `offset + length <= mapped_length`; because offset must be zero in this build, the requested length must fit inside the mapped object.
4. It looks up `{device_id, core_id}` and calls `core+0x98` (`mdw_rsc_ucmd_dispatch`), which invokes the provider handler with opcode `7`.

The normal VPU provider opcode-7 branch at `0xffffffc0087a093c` then applies its own gate:

- provider argument pointer is non-null;
- provider argument `+0x08` is nonzero;
- provider argument `+0x0c` is nonzero, which corresponds to user `+0x10`;
- first u32 at the mapped KVA is `0x8001`.

After the `0x8001` check, normal VPU uses `mapped_kva + 4` as the payload pointer. It locks the global VPU driver object, walks the registered VPU core list at `g_vpu_drv+0xb0`, and tries the per-core Normal algorithm set first, then the Preload set. `vpu_init_dev_algo_sets` initializes those sets at core offsets:

| Set | Core fields | Notes |
|---|---|---|
| Normal | name at `core+0x2c0`, list head at `core+0x2e8`, ops pointer at `core+0x308` | Populated from firmware/bin normal-algo metadata |
| Preload | name at `core+0x310`, list head at `core+0x338`, ops pointer at `core+0x358` | Populated from firmware/bin preload-entry metadata |

The raw ops tables are named `vpu_normal_algo_ops_raw` at `0xffffffc00979ae70` and `vpu_preload_algo_ops_raw` at `0xffffffc00979aeb0` in IDA. Their entries are stored in the kernel runtime address form (`0xffffff80...`), while this IDB is loaded under `0xffffffc0...`. A simple address normalization lands several entries inside larger IDA functions, so the exact callback entry semantics remain a follow-up item. One candidate callback sequence compares `payload+4` against thermal cooling-device names such as `mtk-cl-bcct02`, `mtk-cl-bcct01`, and `mtk-cl-bcct00`, but that should not be used as a final ucmd payload contract until the raw ops table addressing is resolved.

## Ioctl command map

The ioctl magic byte is `0x41` (`'A'`). The dispatcher uses fixed internal copy sizes before calling sub-handlers; it does not trust arbitrary user sizes.

| Command | Encoded size | Dispatcher behavior | Risk note |
|---:|---:|---|---|
| `0xC0284100` | `0x28` | `mdw_handshake` path, copies 0x28 in and conditionally copies 0x28 out | Low; useful reachability probe |
| `0xC0384101` | `0x38` | Disabled/error path | Low |
| `0xC0384102` | `0x38` | `mdw_usr_mem_free` | Medium; memory object lifetime surface |
| `0xC0384103` | `0x38` | `mdw_usr_mem_create` wrapper, type 2, copyout on success | Medium-high; likely dmabuf/ION/IOVA path |
| `0xC0384104` | `0x38` | `mdw_usr_mem_free` | Medium |
| `0xC0384105..0xC038410E` | `0x38` | No active handler in this build | Low |
| `0xC038410F` | `0x38` | `mdw_usr_mem_create` wrapper, type 3, copyout on success | Medium-high |
| `0xC0384110` | `0x38` | `mdw_usr_mem_free` | Medium |
| `0x40184106` | `0x18` | `mdw_usr_run_cmd_sync` | High research priority; command parser plus wait |
| `0xC0184107` | `0x18` | `mdw_usr_run_cmd_async`, copyout on success | High research priority; command parser and device queue |
| `0x40184108` | `0x18` | `mdw_usr_wait_cmd` | Medium; depends on command lifecycle |
| `0x400C4109` | `0x0c` | `mdw_usr_dev_ctrl_4109`, device/core lookup then `mdw_rsc_dev_op0_ctrl` at `+0x70` | Medium-high; reaches provider opcode `0` through the midware wrapper |
| `0x4038410C` | `0x38` | Disabled/error path | Low |
| `0x4038410D` | `0x38` | Disabled/error path | Low |
| `0x4014410E` | `0x14` | `mdw_usr_ucmd` | Medium; rejects early if field at `+0x0c` is nonzero |
| `0x4004413B` | `0x04` | Threshold constant, no active handler observed | Low |
| `0x4004413C` | `0x04` | Copies **0x20** bytes, calls `mdw_usr_dev_sec_alloc` | Medium; `_IOC_SIZE` mismatch but guarded copy |
| `0x4004413D` | `0x04` | Copies **0x20** bytes, calls `mdw_usr_dev_sec_free` after id `< 0x40` | Medium |

The `0x4004413C/3D` size mismatch is important for testing: the command encoding advertises 4 bytes, but `mdw_ioctl` actually validates and copies a 0x20-byte user buffer. This is not by itself a vulnerability because the copy length is fixed and guarded, but a PoC that passes only `_IOC_SIZE(cmd)` bytes will exercise the wrong failure mode.

## Current risk ranking

1. **Memory import / IOVA mapping path**: `0xC0384103` and `0xC038410F` lead to `mdw_usr_mem_create`, then into APUSYS ION KVA/IOVA callbacks. Negative tests confirm that type-2 and type-3 descriptors reach deeper than the initial user-copy guard without creating an object or crashing. Direct dmabuf-source checks from `system_app` now show: no `/dev/dma_heap/*` nodes on this device, DRM dumb buffer creation succeeds but PRIME fd export returns `EACCES`, direct `/dev/ion` open returns `EACCES`, `/dev/ashmem` open returns `EACCES`, and ordinary openable fds such as `/dev/dri/card0`, `/dev/mali0`, `/dev/zero`, `/dev/null`, and `/dev/apusys` all fail APUSYS memory-create with `ENOMEM`. This remains the highest APUSYS priority, but the current blocker is obtaining a usable dmabuf fd in this context.
2. **Command parsing/execution and ucmd paths**: `0xC0184107` and `0x40184106` reach `mdw_usr_run_cmd_async`. The current probe sets the user field at `+0x0c` to `1`, which makes this function return early before the indirect ops calls. `0x4014410E` reaches `mdw_usr_ucmd`; with `+0x0c == 0` it imports the fd as APUSYS memory, maps KVA/IOVA, bounds-checks the requested length, then calls `mdw_rsc_ucmd_dispatch` at `core+0x98`. The provider opcode `7` path is now narrowed: normal VPU has a concrete `0x8001` mapped-buffer gate and walks per-core Normal/Preload algorithm sets, while EDMA and MDLA do not show the same opcode-7 command path.
3. **Device/resource control paths**: `0x4004413C/3D` call `mdw_usr_dev_sec_alloc/free`, and `0x400C4109` calls `mdw_usr_dev_ctrl_4109`. The free path has an id `< 0x40` guard; the `0x400C4109` path looks up a device/core and dispatches provider opcode `0` through `mdw_rsc_dev_op0_ctrl` with only a 0x0c user input. Static mapping shows MDLA/EDMA opcode `0` reaches power-on paths, normal VPU opcode `0` reaches control bookkeeping, and VPU RT opcode `0` returns early.
4. **Handshake/wait/simple rejection paths**: useful for reachability, lower standalone risk.

The surface is more promising than the current display OOB family because it is a directly open proprietary device from `system_app` and reaches several hardware subsystems. Current evidence is reachability plus path mapping; memory corruption, UAF, OOB access, and privilege crossing remain unproven.

## Runtime Probes

[`poc/ApusysIoctlProbe.java`](poc/ApusysIoctlProbe.java) is the preferred `system_app` probe because it reuses the pure-Java syscall helper from `DrmTrigger.java`. By default it:

- Opens `/dev/apusys` with `O_RDWR`.
- Sends one unknown command to confirm generic rejection.
- Sends disabled/error-only APUSYS commands.
- Sends `run_cmd_async`, `run_cmd_sync`, and `mdw_usr_ucmd` buffers with the early-reject field at offset `+0x0c` set to `1`.

Default mode covers reject paths only. It omits memory-create, memory-free, secure-alloc, secure-free, `0x400C4109` device-control dispatch, and real command-buffer execution paths.

Build:

```sh
ANDROID_JAR=/opt/homebrew/share/android-commandlinetools/platforms/android-34/android.jar
rm -rf /tmp/apusys-build /tmp/apusys-dex
mkdir -p /tmp/apusys-build /tmp/apusys-dex
javac -source 8 -target 8 -cp "$ANDROID_JAR" -d /tmp/apusys-build \
  07-cve-2023-32836-display-overflow/poc/DrmTrigger.java \
  13-apusys-ioctl-surface/poc/ApusysIoctlProbe.java
d8 --min-api 29 --output /tmp/apusys-dex /tmp/apusys-build/*.class
adb push /tmp/apusys-dex/classes.dex /data/local/tmp/apusys_ioctl_probe.dex
```

Run from the existing `uid=1000(system)` / `u:r:system_app:s0` dalvikvm context:

```sh
dalvikvm64 -cp /data/data/com.android.settings/cache/apusys_ioctl_probe.dex ApusysIoctlProbe
```

Optional handshake query:

```sh
dalvikvm64 -cp /data/data/com.android.settings/cache/apusys_ioctl_probe.dex ApusysIoctlProbe --query
```

`--query` sets the handshake mode field at offset `+0x0c` to `1` and prints the returned 0x28-byte buffer. Basic handler reachability does not depend on this mode.

Optional memory negative tests:

```sh
dalvikvm64 -cp /data/data/com.android.settings/cache/apusys_ioctl_probe.dex ApusysIoctlProbe --mem-negative
```

`--mem-negative` sends:

- `mem_create2_null` / `mem_create3_null` with a NULL user pointer, expected to fail before import.
- `mem_create2_zero` / `mem_create3_zero` with an all-zero 0x38 descriptor, including fd `0`.
- `mem_create2_badfd` / `mem_create3_badfd` with a 0x38 descriptor whose fd field at user offset `+0x20` is `-1`.

No valid dmabuf or APUSYS command buffer is supplied in this mode. If a memory-create call unexpectedly succeeds, the probe attempts cleanup with the matching memory-free ioctl using the returned descriptor.

Optional device-control tests:

```sh
dalvikvm64 -cp /data/data/com.android.settings/cache/apusys_ioctl_probe.dex ApusysIoctlProbe --dev-ctrl
```

`--dev-ctrl` sends ioctl `0x400C4109` with a 0x0c argument `{device_id, core_id, control}`. The candidate table uses known provider ids from static analysis and tests core `0` and `1` with control value `0`:

| Provider label | Device id | Cores tested | Static expectation |
|---|---:|---:|---|
| `mdla` | `0x02` | `0,1` | opcode `0` reaches `mdla_pwr_on` |
| `vpu` | `0x03` | `0,1` | opcode `0` reaches normal VPU control bookkeeping |
| `edma` | `0x04` | `0,1` | opcode `0` reaches `edma_power_on` |
| `mdla_rt` | `0x22` | `0,1` | opcode `0` returns without the MDLA command path |
| `vpu_rt` | `0x23` | `0,1` | opcode `0` returns through the VPU RT early path |

This mode does not construct command buffers and does not call `mdw_usr_ucmd`. Its value is mapping live device/core ids and provider return codes for opcode `0`.

Optional DRM-backed dmabuf memory-create test:

```sh
dalvikvm64 -cp /data/data/com.android.settings/cache/apusys_ioctl_probe.dex ApusysIoctlProbe --mem-dmabuf
```

`--mem-dmabuf` creates a small DRM dumb buffer on `/dev/dri/card0`, tries to export it with `DRM_IOCTL_PRIME_HANDLE_TO_FD`, and would pass the returned dmabuf fd into APUSYS type-2/type-3 memory descriptors if export succeeds. On the current target, dumb creation succeeds and PRIME export returns `EACCES`, so APUSYS memory import is not reached through this source.

Optional ION-backed dmabuf memory-create test:

```sh
dalvikvm64 -cp /data/data/com.android.settings/cache/apusys_ioctl_probe.dex ApusysIoctlProbe --mem-ion
```

`--mem-ion` uses the old ION ABI observed in IDA: `ION_IOC_ALLOC = 0xc0204900`, `ION_IOC_SHARE = 0xc0084904`, and `ION_IOC_FREE = 0xc0044901`. It starts with heap mask `0x4`, matching an internal MTK ION allocation helper in this kernel, then tries a small set of common masks. On the current target, `/dev/ion` open from `system_app` returns `EACCES`, so APUSYS memory import is not reached through this source either.

Optional candidate-fd scan:

```sh
dalvikvm64 -cp /data/data/com.android.settings/cache/apusys_ioctl_probe.dex ApusysIoctlProbe --fd-scan
```

`--fd-scan` opens a fixed list of candidate paths and feeds each open fd into APUSYS type-2 and type-3 memory-create descriptors. It does not call `mdw_usr_ucmd`. This mode is for ruling out accidental non-dmabuf fd acceptance and checking whether dma-heap nodes exist in the current device context.

Optional `ucmd` negative test:

```sh
dalvikvm64 -cp /data/data/com.android.settings/cache/apusys_ioctl_probe.dex ApusysIoctlProbe --ucmd-negative
```

`--ucmd-negative` sends normal VPU `mdw_usr_ucmd` arguments for device id `3`, core `0` and `1`, bad fd `-1`, offset `0`, and nonzero length `0x1000`. It does not provide a valid mapped command buffer. This verifies the controlled failure mode after clearing the `+0x0c` early-reject field.

[`poc/apusys_ioctl_probe.c`](poc/apusys_ioctl_probe.c) is a native version of the same reject checks for root/permissive lab contexts. It compiles successfully, but on the current device direct `adb shell` execution returns `open(/dev/apusys) failed: EACCES` because shell is not `system` or `camera`, and `system_app` cannot execute native ELF files from app data under SELinux enforcing.

Current local negative-control result:

```text
adb shell dalvikvm64 -cp /data/local/tmp/apusys_ioctl_probe.dex ApusysIoctlProbe
...
[+] Syscall infrastructure ready
Exception in thread "main" java.lang.RuntimeException: open(/dev/apusys) failed: errno=13
```

That is expected for `uid=2000(shell)`. It validates the Java probe startup path but not APUSYS handler reachability. Handler reachability was measured from the `uid=1000(system)` / `u:r:system_app:s0` context that previously opened `/dev/apusys`.

Observed from the rebuilt `system_app` bind shell on 2026-06-14:

```text
[+] Opened /dev/apusys fd=5
[*] unknown            cmd=0x41414141 ret=-22 (EINVAL)
[*] disabled_0c        cmd=0x4038410c ret=-22 (EINVAL)
[*] disabled_0d        cmd=0x4038410d ret=-22 (EINVAL)
[*] handshake_reject   cmd=0xc0284100 ret=-22 (EINVAL)
[*] run_async_reject   cmd=0xc0184107 ret=-22 (EINVAL)
[*] run_sync_reject    cmd=0x40184106 ret=-22 (EINVAL)
[*] ucmd_reject        cmd=0x4014410e ret=-22 (EINVAL)
```

Optional `--query` handshake result:

```text
[*] handshake_query    cmd=0xc0284100 ret=0
    handshake: [0]=0xe309c231 [4]=0x3d2070ec [8]=0x1 [12]=0x1
               [16]=0x1e [20]=0xc [24]=0x40 [28]=0x5
               [32]=0x1d800000 [36]=0x100000
```

Observed optional `--mem-negative` result from the same `system_app` context on 2026-06-14:

```text
[*] mem_create2_null   cmd=0xc0384103 ret=-22 (EINVAL)
[*] mem_create3_null   cmd=0xc038410f ret=-22 (EINVAL)
[*] mem_create2_zero   cmd=0xc0384103 ret=-12 (ENOMEM)
[*] mem_create2_badfd  cmd=0xc0384103 ret=-12 (ENOMEM)
[*] mem_create3_zero   cmd=0xc038410f ret=-12 (ENOMEM)
[*] mem_create3_badfd  cmd=0xc038410f ret=-12 (ENOMEM)
```

Interpretation: `/dev/apusys` ioctl is confirmed reachable from `system_app`, generic invalid commands and disabled paths return controlled `EINVAL`, and handshake mode `1` returns structured data. The memory-create negative tests did not create an object or crash the device. NULL user pointers fail at the early copy/argument guard with `EINVAL`. Both the all-zero descriptor and explicit bad-fd descriptor return `ENOMEM`, which means errno alone does not distinguish fd validation from later ION resource-preparation failure. Static analysis now shows the intended downstream path is APUSYS memory import into ION KVA/IOVA mapping via the registered `mdw_mem_ion_*` ops.

Observed optional `--dev-ctrl` result from the same `system_app` context on 2026-06-14:

```text
[*] devctl_mdla_c0     cmd=0x400c4109 ret=0
[*] devctl_mdla_c1     cmd=0x400c4109 ret=0
[*] devctl_vpu_c0      cmd=0x400c4109 ret=0
[*] devctl_vpu_c1      cmd=0x400c4109 ret=0
[*] devctl_edma_c0     cmd=0x400c4109 ret=0
[*] devctl_edma_c1     cmd=0x400c4109 ret=0
[*] devctl_mdla_rt_c0  cmd=0x400c4109 ret=0
[*] devctl_mdla_rt_c1  cmd=0x400c4109 ret=0
[*] devctl_vpu_rt_c0   cmd=0x400c4109 ret=-13 (EACCES)
[*] devctl_vpu_rt_c1   cmd=0x400c4109 ret=-13 (EACCES)
```

Interpretation: the live ids for MDLA, normal VPU, EDMA, and MDLA RT match the static registration map and reach provider opcode `0` with a success return for core `0` and `1`. VPU RT is also present, but opcode `0` returns `EACCES`, matching the static observation that this entry takes an early access/error path rather than a normal control path. This confirms runtime reachability of APUSYS provider dispatch, but does not by itself establish memory corruption or a privilege-crossing primitive.

Observed optional `--mem-dmabuf` result from the same `system_app` context on 2026-06-14:

```text
[+] Opened /dev/dri/card0 fd=7
[*] drm_create_dumb   cmd=0xc02064b2 ret=0
    dumb: handle=1 pitch=256 size=0x4000
[*] drm_prime_to_fd   cmd=0xc00c642d ret=-13 (EACCES)
[*] drm_destroy_dumb cmd=0xc00464b4 ret=0
```

Observed optional `--mem-ion` result from the same `system_app` context on 2026-06-14:

```text
[*] === Optional APUSYS ION memory-create tests ===
[*] Mode: allocate old-ION buffer, share dmabuf fd, import through APUSYS type2/type3 descriptors
[-] open /dev/ion failed: open(/dev/ion) failed: errno=13
```

Observed optional `--fd-scan` result from the same `system_app` context on 2026-06-14:

```text
[-] fdscan_open_dma_heap_system failed: open(/dev/dma_heap/system) failed: errno=2
[-] fdscan_open_dma_heap_system_uncached failed: open(/dev/dma_heap/system-uncached) failed: errno=2
[-] fdscan_open_dma_heap_mtk_mm failed: open(/dev/dma_heap/mtk_mm) failed: errno=2
[-] fdscan_open_dma_heap_mtk_mm_uncached failed: open(/dev/dma_heap/mtk_mm-uncached) failed: errno=2
[+] fdscan_open_dri_card0 fd=7
[*] fdscan2_dri_card0  cmd=0xc0384103 ret=-12 (ENOMEM)
[*] fdscan3_dri_card0  cmd=0xc038410f ret=-12 (ENOMEM)
[+] fdscan_open_mali0 fd=7
[*] fdscan2_mali0      cmd=0xc0384103 ret=-12 (ENOMEM)
[*] fdscan3_mali0      cmd=0xc038410f ret=-12 (ENOMEM)
[-] fdscan_open_ashmem failed: open(/dev/ashmem) failed: errno=13
[+] fdscan_open_zero fd=7
[*] fdscan2_zero       cmd=0xc0384103 ret=-12 (ENOMEM)
[*] fdscan3_zero       cmd=0xc038410f ret=-12 (ENOMEM)
[+] fdscan_open_null fd=7
[*] fdscan2_null       cmd=0xc0384103 ret=-12 (ENOMEM)
[*] fdscan3_null       cmd=0xc038410f ret=-12 (ENOMEM)
[+] fdscan_open_apusys fd=7
[*] fdscan2_apusys     cmd=0xc0384103 ret=-12 (ENOMEM)
[*] fdscan3_apusys     cmd=0xc038410f ret=-12 (ENOMEM)
[-] fdscan_open_ion failed: open(/dev/ion) failed: errno=13
```

Observed optional `--ucmd-negative` result from the same `system_app` context on 2026-06-14:

```text
[*] === Optional APUSYS ucmd negative tests ===
[*] Mode: normal VPU device id 3, core 0/1, bad fd, offset 0, nonzero length
[*] ucmd_vpu_c0_badfd  cmd=0x4014410e ret=-22 (EINVAL)
[*] ucmd_vpu_c1_badfd  cmd=0x4014410e ret=-22 (EINVAL)
```

Interpretation: APUSYS memory-create remains a mapped high-interest path, but current direct fd sources do not supply a valid dmabuf from `system_app`. DRM can allocate a dumb buffer but cannot export a PRIME fd. Direct old-ION allocation cannot start because `/dev/ion` open is denied in this SELinux context. This device also does not expose the tested `/dev/dma_heap/*` nodes, `/dev/ashmem` open is denied, and ordinary non-dmabuf fds fail APUSYS memory-create with `ENOMEM`. The `ucmd` negative test confirms that normal VPU arguments with offset `0` and nonzero length fail cleanly with a bad fd before any valid opcode-7 payload is possible. A later memory-import or VPU `ucmd` experiment needs a framework-mediated dmabuf allocation path or a lab context that can provide a benign dmabuf fd.

## Next analysis steps

- Keep the current Java probe scoped to reject/query/negative-memory/device-control, fd-source scans, and controlled `ucmd` negatives. A valid dmabuf-backed descriptor is still the next step for real ION/APUSYS mapping behavior.
- Look for a `system_app`-reachable framework or HAL path that returns a dmabuf-backed object. Direct `/dev/ion`, `/dev/ashmem`, dma-heap device nodes, and DRM PRIME export are blocked or unavailable in the current context.
- Use kernel logs, if available from the lab context, to distinguish whether the `ENOMEM` path comes from ION import, cache sync, or IOVA map setup.
- Keep `mdw_usr_get_cmd_ops` / `0xffffffc00a188e58` marked unresolved. Leave the `run_cmd` `+0x0c` early-reject field set while resolving the indirect call targets.
- For `0x400C4109`, optional follow-up is a small control-value sweep on the live-success providers while watching return codes and kernel logs. The current control value `0` already confirms provider opcode `0` reachability.
- For `mdw_usr_ucmd`, the next concrete experiment is a valid dmabuf fd plus a mapped buffer whose first u32 is `0x8001`, using device id `3` for normal VPU and a live core id observed through `--dev-ctrl`.
- Resolve the raw VPU algo ops table address form before treating the `payload+4` cooling-device-name comparisons as the final opcode-7 payload ABI.
- Continue mapping `mdla_run_command_sync`, `vpu_execute`, and `edma_execute` input structures before valid command-buffer experiments.
- Continue scheduler/queue analysis after command parser targets are known. Valid command-buffer experiments come after that mapping.
