# APUSYS ioctl surface

## Summary

`/dev/apusys` is reachable from `uid=1000(system)` on the target device:

```text
crw-rw---- 1 system camera u:object_r:apusys_device:s0 /dev/apusys
[OPEN] /dev/apusys  fd=5
```

The current result is **reachable, with a confirmed VPU algorithm lookup hit and a confirmed normal-VPU `run_cmd_async` request-size guard hit, but not yet mapped to a confirmed vulnerability**. IDA analysis shows the device is the MTK APUSYS midware character device. Its main ioctl handler is now named `mdw_ioctl` in the IDB at `0xffffffc00878a0ec`.

The directory has no CVE number yet. The repository uses CVE-numbered directories when a test is tied to a specific public CVE or confirmed bug class. APUSYS is currently an exposed proprietary ioctl surface with handler-level mapping and runtime reachability evidence, but no confirmed CVE match or vulnerability primitive.

This directory documents the ioctl surface and current runtime probes. The Java probe covers reject/query paths, negative memory-create cases, optional device-control reachability checks, direct dmabuf-source checks, a candidate-fd scan for the memory import path, HardwareBuffer-backed dmabuf import, controlled `ucmd` gate tests, zero-header / invalid-subcommand `run_cmd_async` parser probes, and a normal-VPU valid-type request-size guard probe. Heap shaping and real execution-path inputs remain separate experiment tracks.

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
| `mdw_usr_get_cmd_ops` | `0xffffffc008791b04` | Returns the static command-ops table at `0xffffffc0097966b8`; `mdw_usr_init` stores it into runtime global `0xffffffc00a188e58` |
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
| `vpu_read_iova_dts_tuple` | `0xffffffc0087a8750` | Reads a 3-u32 VPU DT tuple `{iova, size, bin_offset}` |
| `vpu_mem_alloc_from_bin_or_dynamic` | `0xffffffc0087a84b0` | Maps/copies VPU memory from the global bin region when `bin_offset != -1`, otherwise dynamically allocates backing memory |
| `vpu_mem_release_region` | `0xffffffc0087a8658` | Releases a mapped VPU memory region |
| `vpu_map_kva_to_sgt` | `0xffffffc0087a8994` | Builds a scatterlist for a KVA range |
| `vpu_map_sg_to_iova` | `0xffffffc0087a8be4` | Maps a scatterlist into VPU IOVA |
| `vpu_proc_dump_driver_info` | `0xffffffc0087a30e0` | Procfs driver dump path for VPU bin/iova metadata |
| `vpu_map_named_iomem_resource` | `0xffffffc0087a1528` | Maps named iomem resources such as `reg`, `dmem`, `imem`, and `dbg` |

Command-ops correction: `mdw_usr_get_cmd_ops` returns rodata table `0xffffffc0097966b8`; `mdw_usr_init` stores that pointer into runtime global `0xffffffc00a188e58`, and `mdw_usr_run_cmd_async`, `mdw_wait_cmd`, and `mdw_usr_destroy` all dispatch through that global. The table is confirmed as the APUSYS user-command ops table. The entries are raw flat-Image pointers, not directly loaded IDB addresses. Built-in kallsyms gives the correct raw-to-IDB delta: raw `_stext = 0xffffff8008080800`, IDB `_stext = 0xffffffc008000800`, so raw function pointers normalize with `+0x3ffff80000`.

| Command ops entry | Raw value | IDB callback |
|---:|---:|---|
| `+0x00` | `0xffffff8008810010` | `mdw_cmd_create_cmd` at `0xffffffc008790010` |
| `+0x08` | `0xffffff80088105e8` | `mdw_cmd_delete_cmd` at `0xffffffc0087905e8` |
| `+0x10` | `0xffffff80088107bc` | `mdw_cmd_abort_cmd` at `0xffffffc0087907bc` |
| `+0x18` | `0xffffff8008810938` | `mdw_cmd_parse_cmd` at `0xffffffc008790938` |
| `+0x20` | `0xffffff8008811290` | `mdw_cmd_end_sc` at `0xffffffc008791290` |
| `+0x28` | `0xffffff800880fc54` | `mdw_cmd_get_ctx` at `0xffffffc00878fc54` |
| `+0x30` | `0xffffff800880fe60` | `mdw_cmd_put_ctx` at `0xffffffc00878fe60` |
| `+0x38` | `0xffffff80088115cc` | `mdw_cmd_sc_exec_num` at `0xffffffc0087915cc` |
| `+0x40` | `0xffffff800881163c` | `mdw_cmd_sc_set_hnd` at `0xffffffc00879163c` |
| `+0x48` | `0xffffff8008811ab0` | `mdw_cmd_sc_clr_hnd` at `0xffffffc008791ab0` |
| `+0x50` | `0xffffff8008811af4` | `mdw_cmd_is_deadline` at `0xffffffc008791af4` |
| `+0x58` | `0xffffff80088134ec` | `trace_raw_output_mdw_cmd` at `0xffffffc0087934ec` |

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

`mdw_usr_ucmd` preserves the provider return value in `W20` across the following memory cleanup calls (`+0x38` IOVA unmap and `+0x28` KVA unmap). Those cleanup return values are ignored. Therefore the observed `-ENOENT` from the `0x8001` HardwareBuffer case is not a cleanup artifact; it comes from the provider opcode-7 path.

The normal VPU provider opcode-7 branch at `0xffffffc0087a093c` then applies its own gate:

- provider argument pointer is non-null;
- provider argument `+0x08` is nonzero;
- provider argument `+0x0c` is nonzero, which corresponds to user `+0x10`;
- first u32 at the mapped KVA is `0x8001`.

After the `0x8001` check, normal VPU uses `mapped_kva + 4` as the payload pointer. It locks the global VPU driver object, walks the registered VPU core list at `g_vpu_drv+0xb0`, and tries the per-core Normal algorithm set first, then the Preload set. A zero lookup result from both sets reaches `0xffffffc0087a0c70`, which unlocks and returns `-ENOENT`. The empty-list path is different: it reaches `0xffffffc0087a0a88` and returns success. The empty and `Normal` payload tests therefore mean the fd import, KVA mapping, size check, provider dispatch, `0x8001` header gate, and at least one Normal/Preload lookup attempt all succeeded before a key miss. The later `apu_lib_apunn` test shows the same path can also return provider success with a real key.

`vpu_init_dev_algo_sets` initializes those sets at core offsets:

| Set | Core fields | Notes |
|---|---|---|
| Normal | name at `core+0x2c0`, list head at `core+0x2e8`, ops pointer at `core+0x308` | Populated from firmware/bin normal-algo metadata |
| Preload | name at `core+0x310`, list head at `core+0x338`, ops pointer at `core+0x358` | Populated from firmware/bin preload-entry metadata |

The same `ops+0x10` lookup interface is also used while building Preload entries in `vpu_init_dev_algo_sets`. In that path, the call at `0xffffffc0087a556c` passes `X0 = core+0x310`, `X1 = X26`, and `X2 = 0`; `X26` points at the Preload firmware entry's name/key field, while the entry metadata lives in the preceding 0x20 bytes and each entry advances by `0x40`. If lookup misses, the code allocates a new algorithm object and copies up to `0x1f` bytes from `X26` into that object.

That gives a stronger lower-bound ABI for `mdw_usr_ucmd`: after the header word, `mapped_kva+4` is the provider payload pointer and likely starts with the algorithm lookup key itself. The all-zero HardwareBuffer case supplies an empty key after a valid `0x8001` header, which is consistent with the observed lookup miss. The callback identities are now resolved; fields after the likely key remain to be mapped.

The raw ops tables are named `vpu_normal_algo_ops_raw` at `0xffffffc00979ae70` and `vpu_preload_algo_ops_raw` at `0xffffffc00979aeb0` in IDA. They use the same raw flat-Image pointer form as the command ops table. The correct kallsyms-derived normalization is `+0x3ffff80000`; the earlier simple `+0x4000000000` normalization was off by the image text offset and incorrectly landed in the nearby thermal/battery cooling-device cluster.

| Table entry | Raw value | IDB callback |
|---:|---:|---|
| Normal/Preload `+0x00` | `0xffffff8008821758` | `vpu_alg_load` at `0xffffffc0087a1758` |
| Normal/Preload `+0x08` | `0xffffff8008821858` | `vpu_alg_unload` at `0xffffffc0087a1858` |
| Normal/Preload `+0x10` | `0xffffff80088218a4` | `vpu_alg_get` at `0xffffffc0087a18a4` |
| Normal/Preload `+0x18` | `0xffffff80088219e0` | `vpu_alg_put` at `0xffffffc0087a19e0` |
| Normal/Preload `+0x20` | `0xffffff8008821a64` | `vpu_alg_release` at `0xffffffc0087a1a64` |
| Normal-only `+0x28` | `0xffffff8008827d40` | `vpu_hw_alg_init` at `0xffffffc0087a7d40` |

For the `ucmd` path, the relevant lookup callback is `vpu_alg_get`. The branch reached from opcode `7` calls it first on the Normal set and then on the Preload set. The observed `-ENOENT` result for empty, `Normal`, `unknown`, and `apu_lib_custom` payloads means the mapped payload passed the `0x8001` gate, reached the algorithm lookup interface, and missed both sets for those keys. The observed `0` result for `apu_lib_apunn` confirms a loaded key on this firmware. Static analysis of the success branch shows the hit object is immediately passed to the table `+0x18` callback (`vpu_alg_put`) and the provider then returns success after walking the core list; the opcode-7 path does not visibly populate the mapped user-command buffer.

The userspace `/vendor/lib64/libvpu.so` wrapper independently confirms this payload ABI. `VpuStreamImp::getAlgo(char const *name)` allocates a 0x24-byte user-command buffer, zeroes it, writes `0x8001` at offset `+0x00`, copies `name` to offset `+0x04` with `strncpy(..., 0x1f)`, and sends it through `apusysEngine::sendUserCmdBuf(..., device 3)`. The kernel-side `vpu_alg_get` compares that key with algorithm object names using `strcmp`: the object name starts at object `+0x00`, the list node is at object `+0x1040`, and the refcount is at object `+0x103c`.

### VPU algorithm metadata source

The VPU algorithm keys are initialized from the reserved VPU binary metadata, not from the public `/vendor/lib64/libvpu.so` strings alone. `vpu_probe` reads named DT tuples through `vpu_read_iova_dts_tuple`; on the test device, only `vpu_core0@19030000` carries the global bin metadata and `algo` tuple:

| DT property | Value |
|---|---|
| `/reserved-memory/mblock-18-vpu_binary/reg` | physical `0xbe510000`, size `0x018f0000` |
| `vpu_core0/bin-phy-addr` | `0xbe510000` |
| `vpu_core0/bin-size` | `0x018f0000` |
| `vpu_core0/img-head` | `0x00cb1000` |
| `vpu_core0/pre-bin` | `0x00cbf000` |
| `vpu_core0/algo` | `{iova=0x00000000, size=0x00080000, bin_offset=0x00c00000}` |
| `vpu_core0/reset-vector` | `{iova=0x7da00000, size=0x00100000, bin_offset=0x00000000}` |
| `vpu_core0/main-prog` | `{iova=0x7db00000, size=0x00300000, bin_offset=0x00100000}` |
| `vpu_core0/kernel-lib` | `{iova=0x7de00000, size=0x00500000, bin_offset=0xffffffff}` |

`vpu_proc_dump_driver_info` confirms the global driver fields used for this bin: `bin_pa` at `+0x08`, `bin_size` at `+0x10`, `bin_head_ofs` at `+0x14`, `bin_preload_ofs` at `+0x18`, and `mva_algo` at `+0xa8`. From `system_app`, `/proc/vpu/vpu0/mesg`, `/proc/vpu/vpu*/mesg_level`, and `/proc/vpu/vpu_memory` exist but return `Permission denied`, so this context cannot dump the live key list through procfs.

The `/system/lib64/libneuron_platform.vpu.so` `XrpVpuStream::kAlgoNames` table gives three userland candidates: `unknown`, `apu_lib_apunn`, and `apu_lib_custom`. Runtime lookup shows that only `apu_lib_apunn` is a loaded key on this target:

```text
[*] ucmd_hwb_key_apu_lib_apunn_c0_pos388 cmd=0x4014410e ret=0
[*] ucmd_hwb_key_apu_lib_apunn_c1_pos388 cmd=0x4014410e ret=0
[*] ucmd_hwb_key_apu_lib_custom_c0_pos388 cmd=0x4014410e ret=-2 (ENOENT)
[*] ucmd_hwb_key_apu_lib_custom_c1_pos388 cmd=0x4014410e ret=-2 (ENOENT)
[*] ucmd_hwb_key_unknown_c0_pos388 cmd=0x4014410e ret=-2 (ENOENT)
[*] ucmd_hwb_key_unknown_c1_pos388 cmd=0x4014410e ret=-2 (ENOENT)
```

This is a material risk change for APUSYS: the PoC no longer stops at a header-gate lookup miss. It now reaches the real Normal/Preload lookup path with a known key and gets a provider success return. The current probe still does not submit a VPU execution request or build a valid APUSYS command buffer.

The follow-up keydump run confirms the userspace-visible payload is unchanged across the success return:

```text
[*] payload_before_c0_hwb_keydump_apu_lib_apunn_pos388 first_64_hex=01 80 00 00 61 70 75 5f 6c 69 62 5f 61 70 75 6e 6e 00 ...
[*] ucmd_hwb_keydump_apu_lib_apunn_c0_pos388 cmd=0x4014410e ret=0
[*] payload_after_c0_hwb_keydump_apu_lib_apunn_pos388 first_64_hex=01 80 00 00 61 70 75 5f 6c 69 62 5f 61 70 75 6e 6e 00 ...
[*] ucmd_hwb_keydump_apu_lib_apunn_c1_pos388 cmd=0x4014410e ret=0
[*] payload_after_c1_hwb_keydump_apu_lib_apunn_pos388 first_64_hex=01 80 00 00 61 70 75 5f 6c 69 62 5f 61 70 75 6e 6e 00 ...
```

The `libvpu.so` ABI uses a 0x24-byte buffer for this command, so the first-64-byte dump covers the command buffer and padding visible through the Java Image plane. The current boundary is a confirmed algorithm lookup/refcount path, not a command submission path or a mapped-buffer writeback primitive.

### APUSYS run_cmd_async / run_cmd_sync path

`0xC0184107` / `mdw_usr_run_cmd_async` and `0x40184106` / `mdw_usr_run_cmd_sync` use a 0x18-byte user argument. The minimum verified layout is:

| Offset | Use |
|---:|---|
| `+0x00` | command handle/output id; async writes the queued command id here on success, and wait/sync uses it as the lookup key |
| `+0x08` | first input to command-ops `+0x00`, loaded as `W0` |
| `+0x0c` | early gate; must be zero or `mdw_usr_run_cmd_async` returns `EINVAL` before command-ops dispatch |
| `+0x10` | second input to command-ops `+0x00`, loaded as `W1` |

The run path is:

1. `mdw_usr_run_cmd_async` rejects immediately if user `+0x0c` is nonzero. The current default Java probe intentionally uses this reject path.
2. If `+0x0c == 0`, it loads command ops from runtime global `0xffffffc00a188e58`, calls `mdw_cmd_create_cmd` with `{W0 = user+0x08, W1 = user+0x10, W2 = user+0x0c, X3 = user context}`, and expects a command object pointer back.
3. It calls `mdw_cmd_parse_cmd` with `{X0 = command object, X1 = stack scratch}` to parse/populate the command. On parse failure it logs `mdw_usr_par_apu_cmd` and calls `mdw_cmd_abort_cmd` to release the object.
4. On parse success, it inserts the command object into the per-user command tree/list rooted at user-context `+0x60`, stores the resulting positive id in command object `+0x24`, and copies that id back to user argument `+0x00`.
5. `mdw_usr_run_cmd_sync` calls async first, then `mdw_usr_wait_cmd`; `mdw_usr_wait_cmd` reads user argument `+0x00`, looks up the command object in the same per-user tree/list, and calls `mdw_wait_cmd`.

`mdw_cmd_create_cmd` requires `offset + 0x30 <= length`; with the current wrapper offset is the zero field at user `+0x0c`. It allocates a command object, builds a memory object with fd at `+0x28` and length at `+0x14`, maps the command buffer through the APUSYS memory op at `+0x20`, stores the mapped KVA in command object `+0x00`, stores the mapped length in command object `+0x20`, and copies the first 0x30 bytes from `mapped_kva + offset` into command object `+0x08`.

The recovered top-level command header is:

| Offset | Field / constraint |
|---:|---|
| `+0x00` | 64-bit magic, must be `0x3d2070ece309c231` |
| `+0x08` | uid field, copied for debug |
| `+0x10` | version byte, must be `1` |
| `+0x11` | priority byte, must be `<= 0x1f` |
| `+0x12` | hard_limit, debug-printed as 16-bit |
| `+0x14` | soft_limit, debug-printed as 16-bit; if nonzero and a `type+0x20` device table exists, parse remaps the subcommand type to `type+0x20` and derives a deadline |
| `+0x16` | pid, debug-printed as 16-bit |
| `+0x18` | flags, top two bits must not both be set |
| `+0x20` | `num_sc`, must be `1..64` |
| `+0x24` | `ofs_scr_list`, must be within the mapped command length |
| `+0x28` | `ofs_pdr_cnt_list`, must be within the mapped command length |
| `+0x2c` | start of u32 subcommand-offset table, one entry per `num_sc` |

`mdw_cmd_parse_cmd` then walks the subcommand-offset table. For each subcommand index, it reads `*(u32 *)(cmd_base + 0x2c + index * 4)` as the subcommand header offset. The generic minimum header size is 0x28 bytes; type `2` needs the longer 0x34-byte form. The current recovered 0x28-byte subcommand header is:

| Offset | Field / constraint |
|---:|---|
| `+0x00` | `type`, must be `< 0x20` before queue selection |
| `+0x04` | `driver_time` |
| `+0x08` | `ip_time` |
| `+0x0c` | `suggest_time` |
| `+0x10` | `bandwidth` |
| `+0x14` | `tcm_usage` |
| `+0x18` | `tcm_force` byte |
| `+0x19` | `boost_val` byte, capped at `100` for runtime state |
| `+0x1a` | `pack_id` byte, must be `< 0x40` |
| `+0x1b` | reserved byte |
| `+0x1c` | `mem_ctx`, must be `< 0x40` |
| `+0x20` | `cb_info_size` |
| `+0x24` | `ofs_cb_info`; non-negative means inline `cmd_base + ofs_cb_info`, negative means external memory context `(ofs_cb_info & 0x7fffffff)` mapped through memory op `+0x20` |

For inline code-buffer info, parser verifies `cmd_base + ofs_cb_info + cb_info_size <= cmd_base + mapped_length`. It also validates SCR/PDR-derived dependency state before queue insertion; invalid dependency bitmaps return through the parser failure path and release the command object.

The queued execution path is now mapped through the provider handoff:

1. `mdw_cmd_parse_cmd` stores the parsed code-buffer KVA at `sc+0x20` and the code-buffer size at `sc+0x28`.
2. `mdw_queue_task_start` queues the subcommand; provider handlers are not called in the ioctl thread.
3. `mdw_sched_routine` pops the subcommand, computes the usable core count, and dispatches the normal case through `sub_FFFFFFC00879550C`.
4. The normal dispatcher calls `mdw_rsc_get_dev`, then `mdw_rsc_dev_exec` (`core+0x68`), which stores the `sc` at `core+0xc0` and wakes `mdw_sched_dev_routine`.
5. `mdw_sched_dev_routine` calls command-ops `+0x40`, which is `mdw_cmd_sc_set_hnd(sc, core_id, &provider_arg)`.
6. `mdw_cmd_sc_set_hnd` fills the provider argument object, allocates a temporary kernel buffer of `sc+0x28` bytes, copies from the selected code-buffer source KVA, stores the copied buffer pointer at `provider_arg+0x00`, stores the size at `provider_arg+0x0c`, stores the original source KVA at `provider_arg+0x38`, and stores the APUSYS core id at `provider_arg+0x2c`.
7. The worker calls the provider descriptor handler at `provider_desc+0x18` as `{W0 = 4, X1 = &provider_arg, X2 = provider_desc}`.
8. After the provider returns, command-ops `+0x48` (`mdw_cmd_sc_clr_hnd`) copies the temporary handle buffer back to the source KVA and releases the temporary allocation.

For normal VPU execution, the provider opcode-4 check consumes that copied handle buffer. The normal VPU handler expects provider argument `+0x00` to point at a VPU request object and provider argument `+0x0c` to equal `0xb70`. It then requires request field `+0x28 < 0x10` and byte `+0x35 < 0x21`, clears request `+0xb68`, stores the APUSYS core id into request `+0xb5c`, and dispatches `vpu_execute` unless request `+0x28` bit `2` selects `vpu_execute_with_slot`. `vpu_execute` treats request `+0x04` as the algorithm name string and request `+0x28` as flags, matching the separate `ucmd` evidence that algorithm state is name-keyed.

The optional `--run-cmd-invalid-sc` probe uses the same fd source but writes a non-executing command buffer that satisfies the top-level magic/version/count/offset gates and deliberately sets the first subcommand type to `0x20`. Runtime and kernel-log evidence show this reaches `mdw_cmd_sc_valid` and fails at `invalid type(32)`, then returns through `mdw_usr_par_apu_cmd parse cmd fail(-22)`.

The optional `--run-cmd-vpu-guard` probe advances one stage further. It uses a valid normal VPU subcommand type (`type=0x03`) and an inline code buffer of only `0x20` bytes. Runtime evidence shows `run_cmd_async` returns `0`, the scheduler queues the command, `apusys_vpu0` reaches `vpu_req_check: invalid size of vpu request`, and the scheduler trace records `dev(3/vpu-#0)` with `ret(-22)`. This validates the static provider handoff and stops at the intended request-size guard before `vpu_execute`.

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

1. **Memory import / IOVA mapping path**: `0xC0384103` and `0xC038410F` lead to `mdw_usr_mem_create`, then into APUSYS ION KVA/IOVA callbacks. Negative tests confirm that type-2 and type-3 descriptors reach deeper than the initial user-copy guard without creating an object or crashing. Direct node sources remain constrained: no `/dev/dma_heap/*` nodes on this device, DRM dumb buffer creation succeeds but PRIME fd export returns `EACCES`, direct `/dev/ion` open returns `EACCES`, `/dev/ashmem` open returns `EACCES`, and ordinary openable fds such as `/dev/dri/card0`, `/dev/mali0`, `/dev/zero`, `/dev/null`, and `/dev/apusys` all fail APUSYS memory-create with `ENOMEM`. The usable fd source is now the framework path: running the probe through `app_process64` can create an `android.hardware.HardwareBuffer`, read a fd-bearing Parcel entry, and import that fd successfully through both APUSYS type-2 and type-3 memory-create. This is the highest APUSYS priority.
2. **Command parsing/execution and ucmd paths**: `0xC0184107` and `0x40184106` reach `mdw_usr_run_cmd_async`. Static analysis resolves the command ops callbacks: clearing user `+0x0c` calls `mdw_cmd_create_cmd`, then `mdw_cmd_parse_cmd`, and only then queues the object and writes a handle to user `+0x00`. Runtime now confirms the HardwareBuffer fd source can reach this path at three depths: a zero-header ImageWriter-backed buffer imports successfully and returns parser `EINVAL`; a valid header with invalid `type=0x20` reaches `mdw_cmd_sc_valid`; and a valid normal VPU `type=0x03` subcommand with a short inline code buffer queues successfully, reaches `apusys_vpu0`, and stops at `vpu_req_check: invalid size of vpu request`. `0x4014410E` reaches `mdw_usr_ucmd`; with `+0x0c == 0` it imports the fd as APUSYS memory, maps KVA/IOVA, bounds-checks the requested length, then calls `mdw_rsc_ucmd_dispatch` at `core+0x98`. The normal VPU opcode-7 `0x8001` mapped-buffer gate is runtime-confirmed, and `--ucmd-key=apu_lib_apunn` now returns `0` for core `0` and `1`. Empty, `Normal`, `unknown`, and `apu_lib_custom` payloads still return `EINVAL` or `ENOENT` as expected. EDMA and MDLA do not show the same opcode-7 command path.
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

One-command rebuild/upload/run from an existing `system_app` bind shell:

```sh
python3 13-apusys-ioctl-surface/poc/run_system_app_probe.py \
  -s 7FPE0824B0801372 --local-port 48888
```

Full clean/rebuild/upload/run, including the CVE-2024-31317 `system_app` bind shell:

```sh
python3 13-apusys-ioctl-surface/poc/run_system_app_probe.py \
  -s 7FPE0824B0801372 --local-port 48888 --rebuild-shell
```

The runner can call `06-cve-2024-31317-zygote-injection/poc/rebuild_bind_shell.py`, then builds the APUSYS dex, uploads it through the `uid=1000(system)` shell into `/data/data/com.android.settings/cache/apusys_ioctl_probe.dex`, verifies the remote md5, runs `app_process64`, and saves stdout plus filtered kernel logs under `poc-run-results/2026-06-14-batch/`. Use `--rebuild-if-needed` instead of `--rebuild-shell` when the existing shell should be reused if it is still valid.

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

No valid dmabuf or APUSYS command buffer is supplied in this mode. If a memory-create call succeeds, the probe attempts cleanup with the matching memory-free ioctl using the returned descriptor.

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

Optional HardwareBuffer-backed dmabuf import:

```sh
CLASSPATH=/data/data/com.android.settings/cache/apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --hardwarebuffer
```

`--hardwarebuffer` allocates a small `android.hardware.HardwareBuffer`, writes it to a Parcel, enumerates fd-bearing Parcel offsets, and passes discovered fds through APUSYS type-2 and type-3 memory-create descriptors. Use `app_process64` for this mode. Plain `dalvikvm64` can load the Java classes but does not have the required framework JNI registration for `HardwareBuffer.nCreateHardwareBuffer()` or HIDL `HwBinder/HwParcel`.

Optional HardwareBuffer-backed `ucmd` gate test:

```sh
CLASSPATH=/data/data/com.android.settings/cache/apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --ucmd-hardwarebuffer
```

`--ucmd-hardwarebuffer` creates a writeable RGBA `ImageReader` / `ImageWriter` pair, writes the first u32 of the image plane, obtains the output image's `HardwareBuffer`, and reuses the same Parcel-fd path for APUSYS. It runs two cases: first u32 `0`, then first u32 `0x8001`. It only calls normal VPU `mdw_usr_ucmd` for fd offsets that first pass APUSYS memory-create.

Optional HardwareBuffer-backed `ucmd` key lookup test:

```sh
CLASSPATH=/data/data/com.android.settings/cache/apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --ucmd-key=Normal
```

`--ucmd-key=<ascii>` emulates the non-executing `libvpu.so` `VpuStreamImp::getAlgo()` user-command shape: first u32 `0x8001`, up to 31 printable ASCII key bytes at payload offset `+0x04`, then normal VPU `mdw_usr_ucmd` on core `0` and `1`. It only runs after the HardwareBuffer fd first passes APUSYS memory-create. This is a lookup probe, not a VPU execute request.

Optional key lookup plus mapped Image-plane dump:

```sh
CLASSPATH=/data/data/com.android.settings/cache/apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --ucmd-key-dump=apu_lib_apunn
```

`--ucmd-key-dump=<ascii>` uses the same payload as `--ucmd-key`, then prints the first bytes of the Java-readable Image plane before core `0`, after core `0`, and after core `1`. This mode is for checking visible writeback around the lookup return.

Optional HardwareBuffer-backed `run_cmd_async` parser test:

```sh
CLASSPATH=/data/data/com.android.settings/cache/apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --run-cmd-hardwarebuffer
```

`--run-cmd-hardwarebuffer` creates the same writeable `ImageReader` / `ImageWriter` backed buffer, leaves the first command word as zero, imports the discovered HardwareBuffer fd through APUSYS memory-create, and then calls `run_cmd_async` only for fd offsets that first pass memory-create. It sets the run-command user argument as `{fd at +0x08, offset 0 at +0x0c, length 0x4000 at +0x10}`. This mode is a parser reachability check; it does not construct a valid APUSYS command buffer.

Optional deeper parser guard check:

```sh
CLASSPATH=/data/data/com.android.settings/cache/apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --run-cmd-invalid-sc
```

`--run-cmd-invalid-sc` writes a valid top-level APUSYS command header plus one invalid 0x28-byte subcommand header with `type=0x20`. The expected result is still `EINVAL`, but kernel logs should identify the deeper guard as `mdw_cmd_sc_valid ... invalid type(32)`.

Optional valid-type provider request-size guard check:

```sh
CLASSPATH=/data/data/com.android.settings/cache/apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --run-cmd-vpu-guard
```

`--run-cmd-vpu-guard` writes a valid top-level command header plus a normal VPU subcommand (`type=0x03`) with inline `cb_info_size=0x20`. It is intentionally too short for a real VPU request (`0xb70`), so the queued worker should stop at the normal VPU provider opcode-4 size guard before `vpu_execute`.

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

Observed optional `--hardwarebuffer` result from the same `system_app` context on 2026-06-14:

```text
CLASSPATH=/data/data/com.android.settings/cache/apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --hardwarebuffer
...
[+] HardwareBuffer created: id=<java.lang.NoSuchMethodException: android.hardware.HardwareBuffer.getId []> width=64 height=64 format=1 layers=1 usage=0x133
[*] HardwareBuffer parcel: dataSize=436 hasFd=true describe=0x1
[+] AIDL HardwareBuffer readFromParcel succeeded
[+] parcel_fd_pos_364 fd=57
[*] hwb2_pos364        cmd=0xc0384103 ret=-12 (ENOMEM)
[*] hwb3_pos364        cmd=0xc038410f ret=-12 (ENOMEM)
[+] parcel_fd_pos_388 fd=57
[*] hwb2_pos388        cmd=0xc0384103 ret=0
    hwb2_pos388_desc: [0]=0x0 [4]=0x0 [8]=0xfd80c000 [12]=0x1000 [16]=0x4000 [20]=0x0 [24]=0x0 [28]=0x0 [32]=0x39 [36]=0x0 [40]=0x2d [44]=0x0 [48]=0x0 [52]=0x0
[+] hwb2_pos388 succeeded; id=0x2d, cleaning up
[*] hwb2_pos388_cleanup cmd=0xc0384102 ret=0
[*] hwb3_pos388        cmd=0xc038410f ret=0
    hwb3_pos388_desc: [0]=0x0 [4]=0x0 [8]=0xfd80c000 [12]=0x1000 [16]=0x4000 [20]=0x0 [24]=0x0 [28]=0x0 [32]=0x39 [36]=0x0 [40]=0x2d [44]=0x0 [48]=0x0 [52]=0x0
[+] hwb3_pos388 succeeded; id=0x2d, cleaning up
[*] hwb3_pos388_cleanup cmd=0xc0384110 ret=0
[+] parcel_fd_pos_412 fd=57
[*] hwb2_pos412        cmd=0xc0384103 ret=-12 (ENOMEM)
[*] hwb3_pos412        cmd=0xc038410f ret=-12 (ENOMEM)
```

Interpretation: the `app_process64` runtime path provides a valid framework-created dmabuf fd to the probe. The APUSYS type-2 and type-3 memory-create paths both import the fd successfully and return an object id, and the matching free ioctls clean up successfully. Offsets `364` and `412` read fd objects from the Parcel but fail APUSYS memory-create with `ENOMEM`; offset `388` is the useful descriptor read in this run.

Observed optional `--ucmd-hardwarebuffer` result from the same `system_app` context on 2026-06-14:

```text
CLASSPATH=/data/data/com.android.settings/cache/apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --ucmd-hardwarebuffer
...
[*] --- ucmd HardwareBuffer case: zero first_u32=0x0 ---
[+] input image payload: first_u32=0x0 plane_count=1 cap=16384 rowStride=256 pixelStride=4
[+] parcel_fd_pos_388 fd=61
[*] hwb_zero_mem2_pos388 cmd=0xc0384103 ret=0
[*] hwb_zero_mem3_pos388 cmd=0xc038410f ret=0
[*] ucmd_hwb_zero_c0_pos388 cmd=0x4014410e ret=-22 (EINVAL)
[*] ucmd_hwb_zero_c1_pos388 cmd=0x4014410e ret=-22 (EINVAL)
...
[*] --- ucmd HardwareBuffer case: gate8001 first_u32=0x8001 ---
[+] input image payload: first_u32=0x8001 plane_count=1 cap=16384 rowStride=256 pixelStride=4
[+] parcel_fd_pos_388 fd=62
[*] hwb_gate8001_mem2_pos388 cmd=0xc0384103 ret=0
[*] hwb_gate8001_mem3_pos388 cmd=0xc038410f ret=0
[*] ucmd_hwb_gate8001_c0_pos388 cmd=0x4014410e ret=-2 (ENOENT)
[*] ucmd_hwb_gate8001_c1_pos388 cmd=0x4014410e ret=-2 (ENOENT)
```

Interpretation: a valid HardwareBuffer dmabuf and offset `0` are enough to reach the normal VPU `ucmd` content gate. Keeping the mapped buffer's first u32 at `0` returns `EINVAL`; changing only that first u32 to `0x8001` changes the result to `ENOENT` on both tested cores. That matches the static model where `0x8001` passes the first mapped-buffer check and the next stage walks Normal/Preload algorithm state. Current IDA evidence maps `ENOENT` to both lookup callbacks returning no object for the provided payload. The probe still does not construct a complete VPU algorithm payload.

Observed optional `--ucmd-key=Normal` result from the same `system_app` context on 2026-06-14:

```text
CLASSPATH=/data/data/com.android.settings/cache/apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --ucmd-key=Normal
...
[*] --- ucmd HardwareBuffer case: key_Normal first_u32=0x8001 key="Normal" ---
[+] input image payload: first_u32=0x8001 key_bytes=6 plane_count=1 cap=16384 rowStride=256 pixelStride=4
[+] parcel_fd_pos_388 fd=60
[*] hwb_key_Normal_mem2_pos388 cmd=0xc0384103 ret=0
[*] hwb_key_Normal_mem3_pos388 cmd=0xc038410f ret=0
[*] ucmd_hwb_key_Normal_c0_pos388 cmd=0x4014410e ret=-2 (ENOENT)
[*] ucmd_hwb_key_Normal_c1_pos388 cmd=0x4014410e ret=-2 (ENOENT)
```

Interpretation: the payload now matches the userspace `getAlgo()` shape, including a printable key string at `mapped_kva+4`. `Normal` is the set name, not a loaded algorithm key on this target, so both tested cores still return `ENOENT`. The following `apu_lib_apunn` result shows the expected success return without queueing a VPU execution request.

Observed optional `--ucmd-key` result with candidates recovered from `/system/lib64/libneuron_platform.vpu.so::XrpVpuStream::kAlgoNames`:

```text
Candidates: unknown, apu_lib_apunn, apu_lib_custom

CLASSPATH=/data/data/com.android.settings/cache/apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --ucmd-key=apu_lib_apunn
...
[*] --- ucmd HardwareBuffer case: key_apu_lib_apunn first_u32=0x8001 key="apu_lib_apunn" ---
[+] input image payload: first_u32=0x8001 key_bytes=13 plane_count=1 cap=16384 rowStride=256 pixelStride=4
[+] parcel_fd_pos_388 fd=60
[*] hwb_key_apu_lib_apunn_mem2_pos388 cmd=0xc0384103 ret=0
[*] hwb_key_apu_lib_apunn_mem3_pos388 cmd=0xc038410f ret=0
[*] ucmd_hwb_key_apu_lib_apunn_c0_pos388 cmd=0x4014410e ret=0
[*] ucmd_hwb_key_apu_lib_apunn_c1_pos388 cmd=0x4014410e ret=0

--ucmd-key=apu_lib_custom:
[*] ucmd_hwb_key_apu_lib_custom_c0_pos388 cmd=0x4014410e ret=-2 (ENOENT)
[*] ucmd_hwb_key_apu_lib_custom_c1_pos388 cmd=0x4014410e ret=-2 (ENOENT)

--ucmd-key=unknown:
[*] ucmd_hwb_key_unknown_c0_pos388 cmd=0x4014410e ret=-2 (ENOENT)
[*] ucmd_hwb_key_unknown_c1_pos388 cmd=0x4014410e ret=-2 (ENOENT)
```

Interpretation: `apu_lib_apunn` is a real loaded VPU algorithm key on this firmware and produces a provider success return through the same HardwareBuffer-backed `mdw_usr_ucmd` path. IDA maps the success branch to `vpu_alg_get` followed by `vpu_alg_put`; the runtime keydump shows no change in the first 64 bytes of the mapped Image plane before and after core `0` / core `1` success returns. The current question moves to how the normal APUSYS command-buffer path references algorithm state during actual request execution.

Observed optional `--run-cmd-hardwarebuffer` result from the same `system_app` context on 2026-06-14:

```text
CLASSPATH=/data/data/com.android.settings/cache/apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --run-cmd-hardwarebuffer
...
[*] --- run_cmd HardwareBuffer case: zero first_u32=0x0 ---
[+] input image payload: first_u32=0x0 plane_count=1 cap=16384 rowStride=256 pixelStride=4
[+] parcel_fd_pos_388 fd=61
[*] hwb_run_zero_mem2_pos388 cmd=0xc0384103 ret=0
[*] hwb_run_zero_mem3_pos388 cmd=0xc038410f ret=0
[*] run_async_hwb_run_zero_pos388 cmd=0xc0184107 ret=-22 (EINVAL)
```

Interpretation: the same HardwareBuffer fd source can also reach the `run_cmd_async` command-buffer parser when user `+0x0c` is cleared and user `+0x10` is set to a nonzero length. The all-zero mapped buffer is rejected with `EINVAL` after memory import succeeds, which matches the static `mdw_cmd_parse_cmd` early command-header checks. The probe still does not queue a command or exercise provider execution.

Observed optional `--run-cmd-invalid-sc` result from the same `system_app` context on 2026-06-14:

```text
[*] --- run_cmd HardwareBuffer case: invalid_sc_type20 first_u32=0x0 payload_mode=1 ---
[+] input run_cmd invalid_sc_type20 payload: magic=0x3d2070ece309c231 version=1 num_sc=1 sc0_off=0x30 sc0_type=0x20 cb_info_size=0x0 pdr_cnt_off=0x58 scr_off=0x5c cb_info_off=0x60 ...
[*] hwb_run_invalid_sc_type20_mem2_pos388 cmd=0xc0384103 ret=0
[*] hwb_run_invalid_sc_type20_mem3_pos388 cmd=0xc038410f ret=0
[*] run_async_hwb_run_invalid_sc_type20_pos388 cmd=0xc0184107 ret=-22 (EINVAL)
```

Kernel log excerpt:

```text
[apusys][error] mdw_cmd_sc_valid sc(...-#0) invalid type(32)
[apusys][error] mdw_usr_par_apu_cmd parse cmd fail(-22)
```

Interpretation: the command buffer is no longer rejected only at the zero-header gate. It reaches the recovered top-level command-header parser, selects subcommand `#0`, copies the 0x28-byte subcommand header, reaches `mdw_cmd_sc_valid`, and fails at the deliberate invalid `type=0x20` guard. This still does not queue a command or enter VPU/MDLA/EDMA provider execution.

Observed optional `--run-cmd-vpu-guard` result from the same `system_app` context on 2026-06-14:

```text
command=CLASSPATH=/data/data/com.android.settings/cache/apusys_ioctl_probe.dex app_process64 /system/bin ApusysIoctlProbe --run-cmd-vpu-guard
dex_md5=135ab9a6f6671439697133f96a0962cb
...
[*] --- run_cmd HardwareBuffer case: vpu_guard_size20 first_u32=0x0 payload_mode=2 ---
[+] input run_cmd vpu_guard_type3_size20 payload: magic=0x3d2070ece309c231 version=1 num_sc=1 sc0_off=0x30 sc0_type=0x3 cb_info_size=0x20 pdr_cnt_off=0x58 scr_off=0x5c cb_info_off=0x60 ...
[*] hwb_run_vpu_guard_size20_mem2_pos388 cmd=0xc0384103 ret=0
[*] hwb_run_vpu_guard_size20_mem3_pos388 cmd=0xc038410f ret=0
[*] run_async_hwb_run_vpu_guard_size20_pos388 cmd=0xc0184107 ret=0
```

Kernel log excerpt:

```text
vpu_req_check: invalid size of vpu request
[apusys][error] mdw_sched_trace fail : pid(.../...) cmd(...-#0/1) dev(3/vpu-#0) ... ret(-22)
[apusys][warn] mdw_usr_destroy residual cmd(...)
```

Interpretation: the command reaches the queued normal VPU provider path and stops at the request-size guard. The ioctl thread returns success because the async command was accepted before the worker rejects the provider request. This is provider reachability and guard validation, not a VPU execution request.

Plain `dalvikvm64` is a negative control for this mode:

```text
dalvikvm64 -cp /data/data/com.android.settings/cache/apusys_ioctl_probe.dex ApusysIoctlProbe --hardwarebuffer
...
[-] load /system/lib64/libandroid_runtime.so failed: java.lang.UnsatisfiedLinkError: dlopen failed: library "/system/lib64/libandroid_runtime.so" needed or dlopened by "/apex/com.android.art/lib64/libnativeloader.so" is not accessible for the namespace "classloader-namespace"
[-] class android.os.HwBinder unavailable: java.lang.UnsatisfiedLinkError: No implementation found for long android.os.HwBinder.native_init()
[-] HardwareBuffer probe failed: java.lang.UnsatisfiedLinkError: No implementation found for long android.hardware.HardwareBuffer.nCreateHardwareBuffer(int, int, int, int, long)
```

Observed optional `--ucmd-negative` result from the same `system_app` context on 2026-06-14:

```text
[*] === Optional APUSYS ucmd negative tests ===
[*] Mode: normal VPU device id 3, core 0/1, bad fd, offset 0, nonzero length
[*] ucmd_vpu_c0_badfd  cmd=0x4014410e ret=-22 (EINVAL)
[*] ucmd_vpu_c1_badfd  cmd=0x4014410e ret=-22 (EINVAL)
```

Interpretation: APUSYS memory-create is now a mapped and runtime-confirmed import path from `system_app`. Direct node fd sources are still constrained: DRM can allocate a dumb buffer but cannot export a PRIME fd, direct old-ION allocation cannot start because `/dev/ion` open is denied, tested `/dev/dma_heap/*` nodes are absent, `/dev/ashmem` open is denied, and ordinary non-dmabuf fds fail memory-create with `ENOMEM`. The usable source is the framework path: `app_process64` can create a HardwareBuffer dmabuf and import it through APUSYS. The `ucmd` HardwareBuffer test confirms that normal VPU arguments with offset `0`, nonzero length, and first mapped u32 `0x8001` move beyond the zero-header `EINVAL` gate to the Normal/Preload lookup miss.

## Next analysis steps

- Keep the current Java probe scoped to reject/query/negative-memory/device-control, fd-source scans, HardwareBuffer fd import, and controlled `ucmd` gate tests.
- Use the `app_process64` HardwareBuffer path as the baseline fd source. Direct `/dev/ion`, `/dev/ashmem`, dma-heap device nodes, and DRM PRIME export remain blocked or unavailable in the current context.
- Use kernel logs, if available from the lab context, to distinguish whether the `ENOMEM` path comes from ION import, cache sync, or IOVA map setup.
- Keep the `run_cmd` experiments at non-executing guard probes until provider request structures are mapped. Command ops `+0x00`, `+0x10`, and `+0x18` are resolved as `mdw_cmd_create_cmd`, `mdw_cmd_abort_cmd`, and `mdw_cmd_parse_cmd`; top-level and 0x28-byte subcommand headers are runtime-confirmed through the `mdw_cmd_sc_valid invalid type(32)` guard, and the normal VPU provider handoff is runtime-confirmed through `vpu_req_check: invalid size of vpu request`.
- For `0x400C4109`, optional follow-up is a small control-value sweep on the live-success providers while watching return codes and kernel logs. The current control value `0` already confirms provider opcode `0` reachability.
- For `mdw_usr_ucmd`, the `apu_lib_apunn` opcode-7 lookup/writeback check is complete for the visible 0x24-byte `libvpu.so::getAlgo()` command buffer: success returns `0`, then `vpu_alg_put`, with no first-64-byte Image-plane change.
- Continue mapping `mdla_run_command_sync`, `vpu_execute`, and `edma_execute` input structures before any request that can submit real work.
- Continue scheduler/queue analysis around command lifetime and cleanup, including the observed async residual-command warning after the worker rejects the short VPU request.
