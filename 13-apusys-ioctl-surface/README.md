# APUSYS ioctl surface

## Summary

`/dev/apusys` is reachable from `uid=1000(system)` on the target device:

```text
crw-rw---- 1 system camera u:object_r:apusys_device:s0 /dev/apusys
[OPEN] /dev/apusys  fd=5
```

The current result is **reachable through APUSYS memory import, normal-VPU algorithm lookup, command parsing, scheduler handoff, and exact-size VPU request acceptance from `system_app`**. The VPU request ABI is now tied back to the target wrapper's preferred `/system/lib64/libvpu5.so`: `request+0x35` is `buffer_count`, `request+0x38/+0x40` are settings length/IOVA when the libvpu property path is used, `request+0x50` starts the per-buffer descriptor array, and the outer APUSYS subcommand `cb_info_size` must be exactly `0xb70` for this provider path. Runtime now proves a controlled descriptor-0 state writeback boundary and the midware set/clear copyback mechanism, but not an information leak, APUNN output completion, or timeout lifetime misuse. The direct ioctl probes deliberately fill `setting_iova` with the recovered `libneuron_platform.vpu.so` XRP command-buffer layout, but current target-side static evidence shows `XrpVpuStream::CreateVpuRequest()` itself builds the request through five `vpuRequest_addBuffer()` calls and does not visibly call `vpuRequest_setProperty()` in that function; see [`APUNN_SETTINGS_ABI.md`](APUNN_SETTINGS_ABI.md). IDA analysis shows the device is the MTK APUSYS midware character device. Its main ioctl handler is now named `mdw_ioctl` in the IDB at `0xffffffc00878a0ec`.

The directory has no CVE number yet. The repository uses CVE-numbered directories when a test is tied to a specific public CVE or confirmed bug class. APUSYS is currently an exposed proprietary ioctl surface with confirmed VPU hardware dispatch reachability from an unprivileged `system_app` context, but no confirmed CVE match or memory corruption primitive.

This directory documents the ioctl surface and current runtime probes. The Java probe covers reject/query paths, negative memory-create cases, optional device-control reachability checks, direct dmabuf-source checks, a candidate-fd scan for the memory import path, HardwareBuffer-backed dmabuf import, controlled `ucmd` gate tests, zero-header / invalid-subcommand `run_cmd_async` parser probes, a normal-VPU valid-type request-size guard probe, a full-size (`0xb70`) VPU execution probe, a chained IOVA-import + VPU request probe, an XRP-shaped APUNN settings probe, no-dispatch IOVA controls, a small APUNN/XRP opcode/operand matrix mode, and two-native-buffer internal-command-shaped modes with minimal/libvpu-style descriptor metadata, wrapper send-state command flags, output-first descriptor order, wrapper-sized settings, output-ready header state, one standard APUNN data descriptor, an Xtensa operation operand-list offset matrix, and a target-wrapper-shaped five-descriptor/no-settings-property replay with explicit wait, descriptor-0 first-word, descriptor-size, request-priority, request-buffer-count, descriptor-port-id, descriptor-format, descriptor-plane-count, descriptor-height, XRP output-operand-id, XRP input/output-count, XRP opcode, and outer APUSYS `cb_info_size` matrix variants. The remaining closure work is to recover the firmware output/completion contract and test timeout lifetime handling directly.

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

The outer APUSYS `cb_info_size` is an exact request-size contract for this path,
not just a copyback length. The
`--run-cmd-vpu-xrp-target-code5-no-settings-codebuf-size-matrix-iova` probe keeps
the same target-wrapper-shaped five-descriptor request body in command memory and
varies only subcommand `+0x20`:

| `cb_info_size` | Wait result | Visible state |
|---:|---|---|
| `0x20` | `-EIO` | code word unchanged; no request-tail copyback |
| `0x90` | `-EIO` | code word unchanged; no request-tail copyback |
| `0x1c8` | `-EIO` | code word unchanged; no request-tail copyback |
| `0xb6c` | `-EIO` | code word unchanged; no request-tail copyback |
| `0xb70` | `0` | code word `0x2713 -> 0x271b`; request-tail copyback word appears |
| `0xb80` | `-EIO` | code word unchanged; no request-tail copyback |

The `0xb6c` case covers `request+0xb68` but still fails, while `0xb80` fails as
an oversize request. That pins the accepted outer copy size to exactly `0xb70`.
The matching no-dispatch control leaves all cases unchanged.

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_codebuf_size_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_codebuf_size_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_codebuf_size_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_codebuf_size_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_codebuf_size_matrix_repeat.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_codebuf_size_matrix_repeat_kernel.txt`

The optional `--run-cmd-invalid-sc` probe uses the same fd source but writes a non-executing command buffer that satisfies the top-level magic/version/count/offset gates and deliberately sets the first subcommand type to `0x20`. Runtime and kernel-log evidence show this reaches `mdw_cmd_sc_valid` and fails at `invalid type(32)`, then returns through `mdw_usr_par_apu_cmd parse cmd fail(-22)`.

The optional `--run-cmd-vpu-guard` probe advances one stage further. It uses a valid normal VPU subcommand type (`type=0x03`) and an inline code buffer of only `0x20` bytes. Runtime evidence shows `run_cmd_async` returns `0`, the scheduler queues the command, `apusys_vpu0` reaches `vpu_req_check: invalid size of vpu request`, and the scheduler trace records `dev(3/vpu-#0)` with `ret(-22)`. This validates the static provider handoff and stops at the intended request-size guard before `vpu_execute`.

### VPU request structure (0xb70 bytes)

The VPU request object is the codebuf contents that `mdw_cmd_sc_set_hnd` copies into a temporary kernel buffer and passes to the provider handler as `provider_arg+0x00`. The userspace source of the same blob on this target is `libvpu5.so::VpuRequestImp`: the native request starts at `VpuRequestImp+0x48`, has size `0xb70`, and is copied into APUSYS command memory by `VpuStreamImp::runReq()` / `packRequest()`. The older `libvpu.so` implementation has the same raw request layout but a different object base offset.

| Offset | Size | Field | Constraint / use |
|---:|---:|---|---|
| `+0x00` | 4 | (unused in opcode-4 path) | — |
| `+0x04` | 31 | `algo_name` | NUL-terminated algorithm key; `vpu_execute` passes `request+4` to `vpu_alg_get` / `vpu_alg_load` for Normal/Preload lookup |
| `+0x28` | 8 | `flags` (u64) | Must be `< 0x10`; bit 2 selects `vpu_execute_with_slot` over `vpu_execute`; bit 0/2 also gate preload vs normal algo set selection |
| `+0x34` | 1 | `result_status` | Written by hw completion path; `sub_FFFFFFC0087A5B74` stores completion code here |
| `+0x35` | 1 | `buffer_count` | Must be `< 0x21`; `libvpu.so::addBuffer()` increments this byte, max normal value is 32 |
| `+0x38` | 4 | `setting_length` | Written by `prepareSettBuf()` from the settings buffer size |
| `+0x40` | 8 | `setting_iova` | Written by `prepareSettBuf()` from the settings buffer MVA/IOVA |
| `+0x50 + i*0x40` | 0x40 | buffer descriptor | One descriptor per `buffer_count`; populated by `VpuRequestImp::addBuffer()` |
| `+0x50 + i*0x40 + 0x00` | 1 | buffer port/tag | From `VpuBuffer+0x40` |
| `+0x50 + i*0x40 + 0x01` | 1 | buffer kind/direction | From `VpuBuffer+0x34`, clamped by userspace |
| `+0x50 + i*0x40 + 0x02` | 1 | plane_count | Max 3 in `libvpu.so::addBuffer()` |
| `+0x50 + i*0x40 + 0x04` | 4 | buffer field 0 | From `VpuBuffer+0x38` |
| `+0x50 + i*0x40 + 0x08` | 4 | buffer field 1 | From `VpuBuffer+0x3c` |
| `+0x60 + i*0x40 + p*0x10` | 4 | plane field 0 | From the per-plane descriptor |
| `+0x64 + i*0x40 + p*0x10` | 4 | plane field 1 / size | Used by userspace memory import |
| `+0x68 + i*0x40 + p*0x10` | 8 | plane MVA/IOVA | Initialized to `-1`, then filled by `mmapMVA()` after importing the plane fd |
| `+0xB54` | 4 | debug field | Printed in trace logs |
| `+0xB5C` | 1 | `core_id` | Written by kernel from `provider_arg+0x2c`; not user-controlled |
| `+0xB60` | 8 | `exec_time_ns` | Computed post-execution; kernel writes elapsed time in nanoseconds, then divides to microseconds and stores truncated result in `provider_arg+0x28` |
| `+0xB68` | 4 | `slot_id` | Used by `vpu_execute_with_slot` for slot allocation; read/written throughout the execution path |
| `+0xB6C` | 4 | `post_exec_state` | Written by `sub_FFFFFFC0087A5B74` post-completion |

`request+0x35` is not priority. Priority/boost state lives outside the native `0xb70` request in `VpuRequestImp` bookkeeping and in the APUSYS command header (`+0x11`) / EARA fields.

When the libvpu property path is used, `request+0x38/+0x40` is the settings length/IOVA pair rather than a generic image plane. `libvpu5.so::prepareSettBuf()` allocates and records that settings memory, and `vpuRequest_setProperty()` copies the caller property bytes into it. The direct ioctl probes fill this slot with the recovered `libneuron_platform.vpu.so` XRP command-buffer layout: command flags, code/output/data section sizes, section IOVAs, a 16-byte magic, an output header, and 12-byte data descriptors. Current target-side static evidence has not found a `vpuRequest_setProperty()` call inside `XrpVpuStream::CreateVpuRequest()` itself; that function builds the request through five `vpuRequest_addBuffer()` calls. The recovered layout and this target-wrapper caveat are documented in [`APUNN_SETTINGS_ABI.md`](APUNN_SETTINGS_ABI.md).

`vpu_execute` execution flow after guard checks:

1. `sub_FFFFFFC0087A1D58(core, slot_id)` — pre-execution initialization
2. Lock `core+0x28` mutex
3. `sub_FFFFFFC0087A2150(core, slot_id, core_id)` → `sub_FFFFFFC0087A8EA8` — hardware configuration
4. `sub_FFFFFFC0087A651C(core)` — secondary configuration on hw-config success
5. `sub_FFFFFFC0087A5B74(core, request)` — core execution: loads algorithm, writes MMIO registers through `core+0xA0` mapped region, triggers VPU hardware dispatch via XOS command interface at `core+0x9C`, waits for completion with `1s` timeout
6. Post-execution: unlock mutex, restore `request+0x28` original flags, call `sub_FFFFFFC0087A1D9C` cleanup

### VPU full-size execution request

The optional `--run-cmd-vpu-exec` probe constructs a complete APUSYS command buffer with:

- Top-level header: magic `0x3d2070ece309c231`, version `1`, `num_sc=1`
- Subcommand: `type=0x03` (normal VPU), `cb_info_size=0xb70`, inline codebuf at offset `0x60`
- VPU request (codebuf): `+0x04` = `apu_lib_apunn\0`, `+0x28` flags = `0`, `+0x35` buffer_count = `0`, all other fields zeroed

Observed from the `system_app` bind shell on 2026-06-14:

```text
command=CLASSPATH=/data/data/com.android.settings/cache/apusys_ioctl_probe.dex app_process64 /system/bin ApusysIoctlProbe --run-cmd-vpu-exec

[+] Opened /dev/apusys fd=48
[*] run_async_reject   cmd=0xc0184107 ret=-22 (EINVAL)

[*] --- run_cmd HardwareBuffer case: vpu_exec_apunn first_u32=0x0 payload_mode=3 ---
[+] input run_cmd vpu_exec_apunn payload: magic=0x3d2070ece309c231 version=1 num_sc=1 sc0_off=0x30 sc0_type=0x3 cb_info_size=0xb70 cb_info_off=0x60 algo=apu_lib_apunn req_flags=0x0 req_buffer_count=0
[+] parcel_fd_pos_388 fd=60
[*] hwb_run_vpu_exec_apunn_mem2_pos388 cmd=0xc0384103 ret=0
[*] hwb_run_vpu_exec_apunn_mem3_pos388 cmd=0xc038410f ret=0
[*] run_async_hwb_run_vpu_exec_apunn_pos388 cmd=0xc0184107 ret=0
```

Interpretation: `run_cmd_async` returned `0` with a valid normal-VPU subcommand and exact `0xb70` request size. The saved filtered kernel log for this run only contains launch-side APUSYS messages, so the worker-side completion/timeout result needs a rerun with a wider kernel filter.

### VPU IOVA chained request

The `--run-cmd-vpu-iova` probe chains memory import with a VPU request to test whether a pre-mapped IOVA can be used as settings memory and as a request plane:

1. Create a HardwareBuffer (64x64 RGBA), extract dmabuf fd from Parcel offset 388
2. `mem_create` type-2 import -> kernel returns an APUSYS IOVA, size `0x4000`, mem_id `0x2d`
3. Keep that mem_create alive to preserve the IOVA mapping
4. Create a second HardwareBuffer containing a VPU `0xb70` request with `apu_lib_apunn`, one buffer descriptor, setting length/IOVA, and plane0 MVA set from the imported IOVA
5. `mem_create` type-2 for the command buffer itself
6. `run_cmd_async` dispatches the command referencing the imported IOVA

Observed from the `system_app` bind shell on 2026-06-14:

```text
command=CLASSPATH=... app_process64 /system/bin ApusysIoctlProbe --run-cmd-vpu-iova
dex_md5=069383857aa917a63d43d5a03fa4fb00

[+] dmabuf fd=60
[*] mem_create2_iova cmd=0xc0384103 ret=0
    mem_create2_iova_desc: [0]=0x0 [4]=0x0 [8]=0xfd784000 [12]=0x4000 [16]=0x4000 ... [40]=0x2d ...
[+] IOVA=0xfd784000 size=0x4000 mem_id=0x2d
[+] input run_cmd vpu_iova_apunn payload: magic=0x3d2070ece309c231 version=1 num_sc=1 sc0_off=0x30 sc0_type=0x3 cb_info_size=0xb70 cb_info_off=0x60 algo=apu_lib_apunn req_buffer_count=1 iova=0xfd784000 iova_size=0x4000 setting_len_at=0x98 setting_iova_at=0xa0 plane0_mva_at=0xc8
[+] cmd dmabuf fd=65
[*] mem_create2_cmd cmd=0xc0384103 ret=0
[*] run_async_vpu_iova cmd=0xc0184107 ret=0
[*] Dumping original IOVA buffer post-execution:
    original_iova_buf: [0]=0xb [4]=0x0 [8]=0x0 [12]=0x0 ...
```

The matching control run (`--run-cmd-vpu-iova-control`) imports the original buffer and the command buffer, skips only `run_cmd_async`, waits the same 3 seconds, and keeps `original_iova_buf[0]=0x0`. The dispatch run therefore has a reproducible visible writeback signal: the first word of the imported buffer changes from `0` to `0xb` only when the VPU command is submitted.

Filtered kernel evidence from the final run:

```text
vpu 19030000.vpu_core0: vpu_map_sg_to_iova: sg_dma_address: size: 500000, mapped iova: 0x37de00000 (static alloc)
vpu_dev_boot_sequence: vpu0: ALTRESETVEC: 0x7da00000
[apusys][warn] mdw_usr_destroy residual cmd(0xffffff80211a5000)
```

Interpretation: the corrected `libvpu.so` request layout is accepted, the worker reaches the VPU boot/map path, and the imported user buffer receives a small status-like writeback under the VPU dispatch path. This is not yet an arbitrary DMA primitive. This minimal mode leaves the APUNN/XRP settings payload malformed; the follow-up XRP-shaped mode below is the better attribution run.

### APUNN/XRP-shaped IOVA request

The `--run-cmd-vpu-xrp-iova` probe uses the same APUSYS memory import and normal-VPU request chain, but lays out the imported IOVA as a recovered `libneuron` XRP settings buffer:

| Imported offset | Use |
|---:|---|
| `0x000` | Main XRP settings command buffer (`setting_iova`) |
| `0x100` | Code section placeholder, size `0` in this probe |
| `0x200` | Output section, size `0x80` |
| `0x300` | One 12-byte data descriptor |
| `0x400` | Data payload, size `0x80` |
| `0x600` | Split-test VPU plane0 MVA target, size `0x80` |

Observed same-target dispatch result from `system_app` on 2026-06-14:

```text
[*] run_async_vpu_iova cmd=0xc0184107 ret=0
xrp_after_settings    unchanged
xrp_after_output      unchanged
xrp_after_data_desc   unchanged
xrp_after_data_payload[0x400]: [0] 0x41505530 -> 0x41505531
vpu_cmd_after_request_head/tail unchanged
```

The matching `--run-cmd-vpu-xrp-iova-control` run skips only `run_cmd_async` and leaves all XRP windows unchanged. The command-buffer before/after dump also stays unchanged in the dispatch run. This rules out the main settings header, output header, data descriptor, and visible `mdw_cmd_sc_clr_hnd` command-buffer copyback for the same-target delta.

The follow-up `--run-cmd-vpu-xrp-split-iova` run separates the APUNN/XRP data descriptor target at `0x400` from the native VPU plane0 MVA target at `0x600`:

```text
[*] run_async_vpu_iova cmd=0xc0184107 ret=0
xrp_after_data_payload[0x400]: [0] 0x41505530 -> 0x41505530
xrp_after_plane_payload[0x600]: [0] 0x504c4e30 -> 0x504c4e31
vpu_cmd_after_request_head/tail unchanged
```

The split-target control leaves both windows unchanged. The current attribution is therefore precise: the visible `+1` writeback follows the native VPU plane0 MVA descriptor, not the APUNN/XRP data descriptor. With `code_size=0`, this still does not prove APUNN code-section operation execution.

The follow-up `--run-cmd-vpu-xrp-ann-version-iova` mode keeps the split-target
layout, shifts output/data windows to avoid overlap with the larger code
section, and submits one target-sized Xtensa operation entry:

```text
code_size=0x1c8 opcode=10003 stride=0x1c8 inputs=0 outputs=1 output_operand=0
```

Observed dispatch result:

```text
[*] run_async_vpu_iova cmd=0xc0184107 ret=0
xrp_after_code[0x100]          unchanged
xrp_after_output[0x300]        unchanged
xrp_after_data_desc[0x400]     unchanged
xrp_after_data_payload[0x500]  unchanged
xrp_after_plane_payload[0x700]: [0] 0x504c4e30 -> 0x504c4e31
vpu_cmd_after_request_head/tail unchanged
```

The matching `--run-cmd-vpu-xrp-ann-version-iova-control` run leaves every
window unchanged. Kernel logs for the dispatch run show VPU map/boot activity.
This proves that the normal-VPU/APUNN request path tolerates a nonzero
`0x1c8` code entry for this minimal `XTENSA_ANN_VERSION` shape, but APUNN
operation execution and APUNN data-descriptor consumption remain unproven.

The follow-up `--run-cmd-vpu-xrp-op-matrix-iova` mode ran six fixed
query/status operation shapes against the same split-target layout:

| Case | Opcode | Shape | Dispatch result | Visible data delta |
|---|---:|---|---|---|
| `get_algo_info_out0` | `10001` | outputs `[0]` | `run_async_vpu_iova ret=0` | only `plane_payload[0]`: `0x504c4e30 -> 0x504c4e31` |
| `local_mem_info_out0` | `10002` | outputs `[0]` | `run_async_vpu_iova ret=0` | only `plane_payload[0]`: `0x504c4e30 -> 0x504c4e31` |
| `ann_version_out0` | `10003` | outputs `[0]` | `run_async_vpu_iova ret=0` | only `plane_payload[0]`: `0x504c4e30 -> 0x504c4e31` |
| `detailed_op_info_out0` | `10004` | outputs `[0]` | `run_async_vpu_iova ret=0` | only `plane_payload[0]`: `0x504c4e30 -> 0x504c4e31` |
| `ann_version_no_output` | `10003` | no outputs | `run_async_vpu_iova ret=0` | only `plane_payload[0]`: `0x504c4e30 -> 0x504c4e31` |
| `ann_version_out1` | `10003` | outputs `[1]` | `run_async_vpu_iova ret=0` | only `plane_payload[0]`: `0x504c4e30 -> 0x504c4e31` |

The matching matrix control leaves every window unchanged. In the dispatch run,
the APUNN output window, data descriptor, APUNN data payload, code section, and
command-buffer request head/tail remain unchanged for all six cases. The batch
kernel log records VPU map/boot activity, `mdw_sched_trace ... ret(-110)`,
`request (D2D_EXT) timeout, priority: 0, algo: apu_lib_apunn`, and APUSYS
devapc read-violation warnings under `apusys_devapc_isr`. That signal is
currently batch-level because the stdout result does not include absolute
per-case timestamps.

Additional matrix result files:

- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_matrix_iova.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_matrix_iova_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_matrix_iova_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_matrix_iova_control_kernel.txt`

The single-case mode `--run-cmd-vpu-xrp-op-case-iova=<case>` reruns one matrix
case with a longer wait before dumping buffers. The first four cases already
show timeout within a 10s wait; the last two were rerun with a 20s wait to avoid
missing a late worker timeout:

| Case | Wait | Data-window result | Kernel result |
|---|---:|---|---|
| `get_algo_info_out0` | 10s | Only native `plane_payload[0]` changes | VPU map/boot, `request (D2D_EXT) timeout`, `ret(-110)` |
| `local_mem_info_out0` | 10s | Only native `plane_payload[0]` changes | VPU map/boot, `ret(-110)` |
| `ann_version_out0` | 10s | Only native `plane_payload[0]` changes | VPU boot, `request (D2D_EXT) timeout`, `ret(-110)` |
| `detailed_op_info_out0` | 10s | Only native `plane_payload[0]` changes | VPU map/boot, `request (D2D_EXT) timeout` |
| `ann_version_no_output` | 20s | Only native `plane_payload[0]` changes | VPU map/boot, `request (D2D_EXT) timeout`, `ret(-110)` |
| `ann_version_out1` | 20s | Only native `plane_payload[0]` changes | VPU map/boot, `ret(-110)` |

The isolated single-case logs do not reproduce the batch `apusys_devapc_isr`
read-violation warning. Treat the devapc lines from the batch run as a
non-attributed signal until a case-specific reproduction exists. The stable
single-case result is simpler: each submitted `0x1c8` operation shape reaches
VPU dispatch, causes the same native plane-MVA `+1` writeback, leaves APUNN
output/data windows unchanged, and times out on the worker side.

Additional single-case result files:

- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_case_get_algo_info_out0.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_case_get_algo_info_out0_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_case_local_mem_info_out0.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_case_local_mem_info_out0_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_case_ann_version_out0.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_case_ann_version_out0_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_case_detailed_op_info_out0.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_case_detailed_op_info_out0_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_case_ann_version_no_output_20s.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_case_ann_version_no_output_20s_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_case_ann_version_out1_20s.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_case_ann_version_out1_20s_kernel.txt`

The earlier 10s `ann_version_no_output` and `ann_version_out1` single-case
captures are retained in the result directory, but the `_20s` reruns supersede
them for timeout attribution.

The `--run-cmd-vpu-xrp-ann-version-wrapper-zero-data-iova` mode tests the
standard-wrapper default-output/zero-data shape against the same direct ioctl
path. It keeps the `0x1c8` `XTENSA_ANN_VERSION` code section, but writes
settings `+0x08 = 0x40`, settings `+0x0c = 0`, and settings `+0x30 = 0`; no
12-byte APUNN data descriptor is emitted. The no-dispatch control is unchanged.
The dispatch run still returns `run_async_vpu_iova ret=0`, logs
`request (D2D_EXT) timeout` / `ret(-110)`, leaves settings/code/output/data
windows unchanged, and changes only native descriptor `0`'s plane payload word
`0x504c4e30 -> 0x504c4e31`. The command-buffer copyback additionally changes
`request+0x34` from `0` to `2` in the dispatch run, while `request+0x35`
remains the submitted `buffer_count=1`. Treat that as driver/request-status
copyback, not APUNN output-section completion.

The `--run-cmd-vpu-xrp-ann-version-wrapper-zero-data-wait-iova` variant calls
`mdw_usr_wait_cmd` immediately after a successful async submit. Runtime confirms
the async copyout contract: `runCmd+0x00` changes from `0` to command id `1`,
and the same 0x18-byte argument is then valid for `0x40184108`. The wait ioctl
returns `-5 (EIO)`, leaves `runCmd+0x00` unchanged, avoids the later
`mdw_usr_destroy residual cmd` teardown warning, and still leaves APUNN
settings/code/output/data windows unchanged while descriptor `0`'s native plane
payload changes `0x504c4e30 -> 0x504c4e31`. IDA ties this `-EIO` result to
`mdw_wait_cmd` seeing a failed subcommand pointer at command object `+0x1a0`,
logging the command/subcommand failure, and mapping the result to `-EIO`.

Additional wrapper-default result files:

- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_ann_version_wrapper_zero_data_iova.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_ann_version_wrapper_zero_data_iova_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_ann_version_wrapper_zero_data_iova_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_ann_version_wrapper_zero_data_iova_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_ann_version_wrapper_zero_data_wait_iova.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_ann_version_wrapper_zero_data_wait_iova_kernel.txt`

The internal `XTENSA_ANN_VERSION` follow-up switches from one native VPU buffer
to host-wrapper-style input/output descriptors. The minimal two-buffer run and
the libvpu metadata variants all return `run_async_vpu_iova ret=0`, leave
APUNN output/data windows unchanged, and log residual command teardown instead
of a captured `D2D_EXT timeout`. The visible writeback follows copied descriptor
`0` into the code/input window (`0x2713 -> 0x271b`). The libvpu metadata variant
sets `port_id=1`, DATA format, `height=1`, `stride=size`, and `length=size`;
the `desc5` variant additionally sets `buffer_count=5` with code/output aliases.
Neither variant changes the completion/output boundary.

Additional internal-descriptor result files:

- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc5.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc5_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc5_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc5_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_output_first.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_output_first_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_output_first_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_output_first_control_kernel.txt`

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

## Kernel-to-firmware D2D handoff

The VPU request is translated once more inside the kernel before `apu_lib_apunn`
sees it. In the APUSYS VPU 4.0/p1 source path, `vpu_req_check()` requires an
exact `sizeof(struct vpu_request)`, validates only the known flag mask, and
bounds `buffer_count`. `vpu_execute_d2d()` then copies
`req->buffers[0..buffer_count)` into a per-priority command buffer and passes
that command-buffer IOVA to firmware. The firmware-facing register tuple is:

| Register / input | Meaning |
|---|---|
| `XTENSA_INFO01` | `0x22` for `DO_D2D`, `0x24` for `DO_D2D_EXT` |
| `XTENSA_INFO11` | request priority for preload / D2D_EXT |
| `XTENSA_INFO12` | `req->buffer_count` |
| `XTENSA_INFO13` | IOVA of the copied `struct vpu_buffer[]` array |
| `XTENSA_INFO14` | `req->sett_ptr` |
| `XTENSA_INFO15` | `req->sett_length` |
| `XTENSA_INFO16` | preload entry address for D2D_EXT |
| `XTENSA_INFO19` | preload IRAM MVA for D2D_EXT |

The firmware therefore does not parse the original userland
`struct vpu_request`. It gets a register tuple, a firmware-readable copy of
`struct vpu_buffer[]`, and any imported windows reachable through the copied
plane IOVAs. IDA confirms the kernel execution entry as `vpu_execute` at
`0xffffffc0087a7974`; the source mirror explains the downstream D2D/D2D_EXT
register handoff.

The current IDA view pins down the handoff more tightly:

| IDA item | Address | Meaning |
|---|---:|---|
| normal-VPU opcode-4 gate | `0xffffffc0087a08f8` | Requires request size `0xb70`, `request+0x28 < 0x10`, and `request+0x35 < 0x21` |
| `vpu_execute` | `0xffffffc0087a7974` | Looks up `request+0x04` first in Normal, then in Preload if the Normal lookup misses |
| D2D copy helper | `0xffffffc0087a20e8` / `0xffffffc0087a20f8` | Copies `request+0x50` into the per-priority VPU command buffer |
| D2D array IOVA helper | `0xffffffc0087a20c8` | Returns the copied descriptor-array IOVA from `core + priority * 0xb0 + 0x400` |
| D2D_EXT preload selector | `0xffffffc0087a1f40` | Clamps priority to `0..2` and loads the selected Preload object from `core + priority * 0xb0 + 0x3c8` |
| D2D handoff | `0xffffffc0087a5b74` | Writes the copied descriptor-array IOVA and settings tuple to VPU MMIO |

This also explains the `D2D_EXT` runtime logs. The probe submits
`request+0x28 == 0`, but `vpu_execute` ORs bit `2` into `request+0x28` when the
Normal set misses and the Preload set is tried. `apu_lib_apunn` is therefore
being executed through the Preload / `D2D_EXT` path on this build. In that path
the selected Preload object contributes additional firmware inputs: the driver
writes `preload_obj+0xfb0 + preload_obj+0xfb8` to MMIO `+0x290` and
`preload_obj+0xfc0` to MMIO `+0x29c` before the common buffer/settings tuple.

This changes the APUNN interpretation model. The two-native-buffer probe is not
just "two Java buffers"; it is two copied VPU buffer descriptors. Its visible
`0x2713 -> 0x271b` delta follows copied descriptor `0` when that descriptor's
plane IOVA is bound to the code/input window. In the split-target one-buffer
opcode cases, descriptor `0` points at the native plane payload instead, and the
stable writeback is `plane_payload[0]`: `0x504c4e30 -> 0x504c4e31` while the
code, settings, output, data descriptor, and data payload windows remain
unchanged. That proves descriptor-following inside the VPU path.

The `ann_version_status_bit3_out0` case pre-sets the first operation word to
`10003 | 0x8` (`0x271b`). Its control leaves all windows unchanged. The dispatch
run still returns `0`, logs a `D2D_EXT` timeout plus residual command cleanup,
keeps the code word at `0x271b`, and only changes descriptor `0`'s native plane
payload word `0` from `0x504c4e30` to `0x504c4e31`. Bit `3` in the first
operation word is therefore not the missing APUNN completion signal. The
firmware path is touching descriptor-backed memory, but it still does not mark
the XRP settings as complete or write the APUNN output section.

The output-first two-buffer variant keeps the settings buffer unchanged
(`code_iova = base+0x100`, `output_iova = base+0x300`) but swaps the copied
native VPU descriptor order so descriptor `0` points to output and descriptor
`1` points to code/input. The no-dispatch control is unchanged. The dispatch
run returns `0`, leaves settings at `0x5`, leaves code word `0x2713` unchanged,
and changes output word `0` from `0xffffffff` to `0xfffffffd`. This confirms
the current visible writeback follows native descriptor `0`; it is not yet
APUNN's normal settings/output completion contract.

The `settings68` two-buffer variant keeps descriptor `0` bound to code/input,
keeps wrapper send-state flags (`settings[0] = 0x5`) and libvpu descriptor
metadata, and changes only `request+0x38` from `0x100` to `0x68`, matching the
DSP command buffer size allocated by `XrpIntrinsicExecutor::CreateXrpCommand()`.
The dispatch result is equivalent to the `0x100` code-first baseline:
`run_cmd_async` returns `0`, kernel logs VPU boot/map activity and residual
command cleanup, settings remain `0x5`, output remains `0xffffffff`, and code
word `0` changes `0x2713 -> 0x271b`. The firmware-visible settings length is
not the missing APUNN completion condition.

Static analysis after this run recovered the standard data descriptor writer:
the descriptor section is `12 * data_buffer_count` bytes, and each entry is
`{kind/type, size, iova_low32}`. The new
`--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-wrapper-data`
mode keeps the `settings68` request shape but restores one 12-byte data
descriptor (`type=3`, size `0x80`, IOVA = APUNN data payload). Its control
variant skips dispatch and leaves settings/code/output/data windows unchanged.
The dispatch run returns `0`, logs VPU map/boot activity without a captured
`D2D_EXT` timeout in the 20-second window, keeps settings at `0x5`, leaves the
output header/data descriptor/data payload unchanged, and again changes code
word `0` from `0x2713` to `0x271b`. Restoring the standard one-data-descriptor
section is therefore not the missing APUNN completion condition.

Static wrapper analysis also shows `CalculateOutputSize()` has a dynamic
output-sizing path: for one `0x1c8` Xtensa operation it returns
`0x40 + 4 * 1 = 0x44` instead of the default `0x40`. The
`wrapper-data-output44` variant keeps `settings_len=0x68`, wrapper send-state
flags, one standard data descriptor, and code-first native descriptors, but
sets output size `0x44` and the output header flag to `1`. Its control is
unchanged. Dispatch still returns `0`, settings remain `0x5`,
output/data descriptor/data payload remain unchanged, and code word `0`
changes `0x2713 -> 0x271b`. The wrapper dynamic-output size/header shape is
therefore not the missing APUNN completion condition.

Static analysis of the target `XrpVpuStream::CreateVpuRequest()` shows this
request builder adds the same native VPU buffer descriptor five times. The
`wrapper-data-code5` variant keeps `settings_len=0x68`, wrapper send-state
flags, wrapper-default output size `0x40`, and one standard data descriptor,
but sets `buffer_count=5` with all five native descriptors pointing at the
code/input window. Its control is unchanged. Dispatch still returns `0`,
settings remain `0x5`, output/data descriptor/data payload remain unchanged,
and code word `0` changes `0x2713 -> 0x271b`. The five-identical-code-buffer
native descriptor shape is therefore not the missing APUNN completion
condition.

The closer target-wrapper replay
`--run-cmd-vpu-xrp-target-code5-no-settings-iova` keeps the same five copied
code/input descriptors but clears the native VPU settings tuple
(`request+0x38 = 0`, `request+0x40 = 0`) because the target
`CreateVpuRequest()` path has no visible `vpuRequest_setProperty()` call. Its
control leaves all windows unchanged. Dispatch returns `0`, VPU boot/map logs
are present, settings/output/data windows remain unchanged, and code word `0`
changes `0x2713 -> 0x271b`. The explicit wait variant
`--run-cmd-vpu-xrp-target-code5-no-settings-wait-iova` also returns `0` from
`mdw_usr_wait_cmd` and consumes the command id without producing APUNN
settings completion or output writeback. Therefore the no-settings-property
five-descriptor shape is accepted by the APUSYS/VPU path and reaches the same
descriptor-0 writeback boundary, but still does not expose the APUNN normal
output contract.

The descriptor-0 first-word matrix
`--run-cmd-vpu-xrp-target-code5-no-settings-word-matrix-iova` keeps that same
target-wrapper-shaped request and varies only code/input word `0`: `0`,
`0x2713`, `0x271b`, `0x504c4e30`, and `0xffffffff`. Its no-dispatch control
keeps every tested word unchanged. Dispatch plus immediate wait leaves
settings/output/data windows unchanged in every case. For non-all-ones inputs,
the descriptor-0 word behaves like a status/flags OR with `0xb`:
`0 -> 0xb`, `0x2713 -> 0x271b`, `0x271b -> 0x271b`, and
`0x504c4e30 -> 0x504c4e3b`. The `0xffffffff` case changes to
`0xfffffffd` and returns `-EIO` from wait while the kernel log records a VPU
scheduler timeout. This makes the current visible writeback look like a
firmware/worker state word, not an APUNN output-section copyback or leak.

The descriptor-size matrix
`--run-cmd-vpu-xrp-target-code5-no-settings-size-matrix-iova` keeps the same
target-wrapper-shaped five-descriptor/no-settings request, keeps code/input word
`0` at `0x2713`, and varies only the native descriptor payload size fields
(`width`, `stride`, and `length`) across `0`, `1`, `2`, `3`, `4`, `5`, `6`,
`7`, `8`, `9`, `0xc`, `0x10`, `0x20`, `0x40`, and `0x1c8`. The no-dispatch
control preserves `0x2713` for every size. Dispatch plus wait returns `0` for
every size, including size `0`, and leaves settings/output/data windows
unchanged. The first dispatch run missed visible descriptor-0 writeback for
`7`, `9`, `0xc`, and `0x10`, but the immediate repeat wrote `0x2713 -> 0x271b`
for every tested size. Therefore the descriptor size fields are not a hard
bounds/acceptance gate for this state-word writeback. The visible status write
can follow descriptor-0's MVA even when the descriptor advertises length `0`;
the occasional no-write cases make it unsuitable as a normal APUNN completion
oracle.

The request-priority matrix
`--run-cmd-vpu-xrp-target-code5-no-settings-priority-matrix-iova` varies only
native request `+0xb68` across `0`, `1`, `2`, `3`, and `0xffffffff`. IDA shows
this field is read by `vpu_execute()`, passed to the algorithm lookup path, and
used by the D2D_EXT helpers after signed clamping to priority `0..2` for preload
metadata and per-priority descriptor-copy buffers. The no-dispatch control
preserves the requested value in the command-buffer tail. Dispatch plus wait
returns `0` for every value and leaves settings/output/data windows unchanged.
The first dispatch run missed visible descriptor-0 writeback for priorities `1`
and `2`, but the repeat wrote `0x2713 -> 0x271b` for every tested priority,
including `0xffffffff`. The command-buffer copyback clears tail word `40`
(`request+0xb68`) after dispatch. Therefore this field is a real
kernel-to-firmware priority/slot input and command-lifetime field, but it is not
the missing APUNN completion/output condition.

The request-buffer-count matrix
`--run-cmd-vpu-xrp-target-code5-no-settings-buffer-count-matrix-iova` keeps the
same target-wrapper-shaped five-descriptor/no-settings request and varies only
native request byte `+0x35` across `0`, `1`, `2`, `3`, `4`, `5`, and `0x20`.
This byte controls both the kernel-side copied descriptor count and firmware
`XTENSA_INFO12`. The no-dispatch control leaves every tested code word at
`0x2713` and leaves APUNN settings/output/data windows unchanged. Dispatch plus
wait was run twice with identical user-visible results:

| Request `+0x35` | Dispatch + wait result |
|---:|---|
| `0` | `run_async_vpu_iova ret=0`, `wait_vpu_iova ret=-EIO`; code word remains `0x2713`; settings/output/data windows unchanged |
| `1,2,3,4,5,0x20` | `run_async_vpu_iova ret=0`, `wait_vpu_iova ret=0`; code word becomes `0x271b`; settings/output/data windows unchanged |

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_buffer_count_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_buffer_count_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_buffer_count_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_buffer_count_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_buffer_count_matrix_repeat.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_buffer_count_matrix_repeat_kernel.txt`

Therefore `buffer_count=0` is a real liveness gate for the current descriptor-0
state writeback path. Once at least one descriptor is copied, the exact count up
to the kernel-accepted maximum `0x20` does not produce APUNN settings completion
or output writeback.

The descriptor-port-id matrix
`--run-cmd-vpu-xrp-target-code5-no-settings-port-matrix-iova` keeps the same
target-wrapper-shaped five-descriptor/no-settings request, keeps
`buffer_count=5`, and varies only descriptor byte `+0x00` across `0`, `1`, `2`,
`3`, `4`, and `0xff`. The no-dispatch control preserves every code word at
`0x2713` and records the requested port byte in the copied payload. Dispatch plus
wait returns `0` for every tested port and leaves APUNN settings/output/data
windows unchanged. The first dispatch run missed visible descriptor-0 writeback
for the default `port_id=1`, but the immediate repeat wrote `0x2713 -> 0x271b`
for every port, including `1`.

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_port_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_port_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_port_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_port_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_port_matrix_repeat.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_port_matrix_repeat_kernel.txt`

Therefore descriptor `+0x00` is not a hard role/acceptance gate for the current
state writeback path. The stable signal still follows copied descriptor `0`, but
the port byte does not reveal the missing APUNN completion/output condition.

The descriptor-format matrix
`--run-cmd-vpu-xrp-target-code5-no-settings-format-matrix-iova` keeps the same
target-wrapper-shaped five-descriptor/no-settings request, keeps
`buffer_count=5`, and varies only descriptor byte `+0x01` across `0`, `1`, `2`,
`3`, `4`, and `0xff`. The no-dispatch control preserves every code word at
`0x2713` and records the requested format byte in the copied payload. Dispatch
plus wait was run twice with identical user-visible results: every tested format
returns `0`, changes code word `0` from `0x2713` to `0x271b`, and leaves APUNN
settings/output/data windows unchanged.

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_format_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_format_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_format_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_format_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_format_matrix_repeat.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_format_matrix_repeat_kernel.txt`

Therefore descriptor `+0x01` is not a hard format/direction gate for the current
state writeback path and does not reveal the missing APUNN completion/output
condition.

The descriptor-plane-count matrix
`--run-cmd-vpu-xrp-target-code5-no-settings-plane-count-matrix-iova` keeps the
same target-wrapper-shaped five-descriptor/no-settings request, keeps
`buffer_count=5`, and varies only descriptor byte `+0x02` across `0`, `1`, `2`,
`3`, `4`, and `0xff`. The no-dispatch control preserves every code word at
`0x2713` and records the requested plane-count byte in the copied payload.
Dispatch plus wait returns `0` for every tested plane count and leaves APUNN
settings/output/data windows unchanged. The first dispatch run missed visible
descriptor-0 writeback for `plane_count=4`, but the immediate repeat wrote
`0x2713 -> 0x271b` for every tested value, including `0` and `0xff`.

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_plane_count_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_plane_count_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_plane_count_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_plane_count_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_plane_count_matrix_repeat.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_plane_count_matrix_repeat_kernel.txt`

Therefore descriptor `+0x02` is not the liveness gate for the current
descriptor-0 state writeback path. It also does not reveal the missing APUNN
completion/output condition.

The descriptor-height matrix
`--run-cmd-vpu-xrp-target-code5-no-settings-height-matrix-iova` keeps the same
target-wrapper-shaped five-descriptor/no-settings request, keeps
`buffer_count=5`, and varies descriptor word `+0x08` across `0`, `1`, `2`, `3`,
`0x40`, and `0xffffffff`. The no-dispatch control preserves every code word at
`0x2713` and records the requested height word in the copied payload. Dispatch
plus wait returns `0` for every tested height and leaves APUNN settings/output/data
windows unchanged. The first dispatch run wrote `0x2713 -> 0x271b` for every
height. The repeat runs show the familiar unstable state-write oracle: some
heights miss visible descriptor-0 writeback in a given run, but every tested
height remains accepted and no APUNN completion/output signal appears.

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_height_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_height_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_height_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_height_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_height_matrix_repeat.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_height_matrix_repeat_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_height_matrix_repeat2.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_height_matrix_repeat2_kernel.txt`

Therefore descriptor `+0x08` is accepted as ordinary copied metadata, but it is
not the missing APUNN completion/output condition. The height matrix reinforces
that descriptor-0 first-word writeback is useful for reachability attribution,
but not stable enough to serve as a completion oracle.

The output-operand-id matrix
`--run-cmd-vpu-xrp-target-code5-no-settings-operand-id-matrix-iova` keeps the
same target-wrapper-shaped five-descriptor/no-settings request, keeps
`buffer_count=5`, keeps `XTENSA_ANN_VERSION` with one output operand, and varies
only the 16-bit operand id at code entry `+0x48` across `0`, `1`, `2`, `3`, and
`0xffff`. The no-dispatch control preserves the requested operand id and leaves
all APUNN windows unchanged. The first dispatch run returns `0` from both
`run_cmd_async` and explicit wait for every tested operand id, writes
`0x2713 -> 0x271b` for every case, and leaves APUNN settings/output/data
unchanged. The repeat returns `0` for every tested operand id and leaves
settings/output/data unchanged again; it misses visible descriptor-0 writeback
for `1` and `0xffff`, matching the already-observed unstable state-write oracle.

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_operand_id_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_operand_id_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_operand_id_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_operand_id_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_operand_id_matrix_repeat.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_operand_id_matrix_repeat_kernel.txt`

Therefore the output operand id is accepted as part of the copied XRP operation
description, but the tested ids are not the missing APUNN completion/output
condition and do not produce source-sensitive data descriptor/output consumption.

The operation-shape matrix
`--run-cmd-vpu-xrp-target-code5-no-settings-op-shape-matrix-iova` keeps the
same target-wrapper-shaped five-descriptor/no-settings request, keeps
`buffer_count=5`, keeps opcode `XTENSA_ANN_VERSION`, and varies only the
operation input/output counts plus the matching operand list:

| Case | Inputs | Outputs | Operand list |
|---|---:|---:|---|
| `ann_version_counts_0_0` | 0 | 0 | `[]` |
| `ann_version_counts_0_1_out0` | 0 | 1 | `[0]` |
| `ann_version_counts_0_2_out0_1` | 0 | 2 | `[0,1]` |
| `ann_version_counts_1_0_in0` | 1 | 0 | `[0]` |
| `ann_version_counts_1_1_in0_out1` | 1 | 1 | `[0,1]` |
| `ann_version_counts_2_1_in0_1_out2` | 2 | 1 | `[0,1,2]` |

The no-dispatch control preserves every requested count and operand-list word.
Two dispatch runs return `0` from `run_cmd_async` and explicit wait for every
case, write `0x2713 -> 0x271b` in every case, preserve the requested
input/output counts, and leave APUNN settings/output/data unchanged.

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_op_shape_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_op_shape_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_op_shape_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_op_shape_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_op_shape_matrix_repeat.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_op_shape_matrix_repeat_kernel.txt`

Therefore the tested input/output count combinations are accepted operation
metadata for this request shape, but they are not the missing APUNN
completion/output condition.

The target-wrapper opcode matrix
`--run-cmd-vpu-xrp-target-code5-no-settings-opcode-matrix-iova` keeps the same
target-wrapper-shaped five-descriptor/no-settings request, keeps
`buffer_count=5`, keeps one output operand `[0]`, and varies the first operation
opcode across the statically recovered internal APUNN range `10001..10009`.
Unlike the ordinary descriptor/operand metadata matrices, this one is visibly
opcode-sensitive:

| Opcode | Label | Wait result | Code word result |
|---:|---|---|---|
| `10001` / `0x2711` | `GET_ALGO_INFO` | `0` | `0x2711 -> 0x271b` in both dispatch runs |
| `10002` / `0x2712` | `LOCAL_MEM_INFO` | `0` | `0x2712 -> 0x271b` in the first run; no visible write in the repeat |
| `10003` / `0x2713` | `XTENSA_ANN_VERSION` | `0` | `0x2713 -> 0x271b` in both dispatch runs |
| `10004` / `0x2714` | `GET_DETAILED_OP_INFO` | `0` | no visible write in either dispatch run |
| `10005` / `0x2715` | `UNKNOWN_10005` | `-EIO` | no visible write |
| `10006` / `0x2716` | `APU_LIB_APUNN_10006` | `-EIO` | `0x2716 -> 0x2715` |
| `10007` / `0x2717` | `APU_LIB_CUSTOM_10007` | `-EIO` | `0x2717 -> 0x2715` |
| `10008` / `0x2718` | `APUNN_DYNAMIC_10008` | `0` | `0x2718 -> 0x271b` in both dispatch runs |
| `10009` / `0x2719` | `CUSTOM_DYNAMIC_10009` | `0` | `0x2719 -> 0x271b` in both dispatch runs |

The no-dispatch control preserves each requested opcode. Both dispatch runs
leave APUNN settings/output/data unchanged for every opcode. Kernel logs for the
dispatch runs contain D2D_EXT timeouts and `mdw_wait_cmd` failures aligned with
the `10005..10007` wait-failure cases.

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_opcode_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_opcode_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_opcode_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_opcode_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_opcode_matrix_repeat.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_opcode_matrix_repeat_kernel.txt`

Therefore `apu_lib_apunn` does parse the operation opcode in this request
shape. The current request is still missing the normal completion/output
contract, but the firmware-side opcode classes are now observable: `10005` is a
timeout/error opcode, `10006/10007` normalize their first word to `10005` and
timeout, `10004` completes without the descriptor-0 state write, and
`10001/10003/10008/10009` use the familiar `0x271b` state word.

IDA confirms `request+0x28` bit `2` is not just firmware payload: the normal
VPU provider uses it to select `vpu_execute_with_slot()`, and `vpu_execute()`
uses it to choose the Preload algorithm set instead of trying Normal first. The
`wrapper-data-preload-slot` variant keeps the wrapper-data request shape but
writes `request+0x28 = 0x4`. Its control is unchanged. Dispatch still returns
`0`, settings remain `0x5`, output/data descriptor/data payload remain
unchanged, and code word `0` changes `0x2713 -> 0x271b`. The command-buffer
tail gains a slot-like copyback word (`request_tail[40] = 1`), so bit `2`
does alter kernel lifetime/slot bookkeeping, but it does not produce normal
APUNN completion or output writeback.

The flags-matrix variant keeps the wrapper-one-data request shape and varies
only `settings[0]`: `0x4`, `0x2`, `0x3`, `0x5`, and `0x0d`. Its control is
unchanged. Dispatch returns `0` for all five cases; settings keep the exact
pre-seeded value, output/data remain unchanged, and code word `0` changes
`0x2713 -> 0x271b` in every case. Pre-seeding the host wrapper's completion
predicate with `0x2` or `0x3` therefore does not produce APUNN output
completion.

The `output_ready` two-buffer variant keeps the same code-first request and
sets only the wrapper-controlled output header byte at `output+0x10` from `0`
to `1`, matching `XrpCommandInfo::PrepareOutputHeader(true)`. Dispatch still
returns `0`, settings remain `0x5`, output word `0` remains `0xffffffff`, and
code word `0` changes `0x2713 -> 0x271b`. That output header flag is not the
missing APUNN completion condition.

The `operand_offset_matrix` variant keeps the current wrapper-one-data,
libvpu-descriptor, `settings_len=0x68`, code-first request shape and varies
only the Xtensa operation operand-list offset at code entry `+0x08`:
`0`, `0x10`, `0x40`, and `0x100`. Each case relocates the zero output operand
id to `entry+0x48+operand_off`. The no-dispatch control leaves all windows
unchanged. Dispatch returns `0` for all four cases; settings stay `0x5`, output
and data windows stay unchanged, and code word `0` changes `0x2713 -> 0x271b`
in every case. The kernel log records VPU map/boot activity and four residual
commands at teardown, without captured APUNN output completion. This rules out
the operand-list offset value and relocated zero output operand as a standalone
completion trigger. The target-wrapper/no-settings output-operand-id matrix
extends this to nonzero operand ids for the current incomplete request shape,
but still does not prove the completed APUNN data-binding contract.

## Current risk ranking

Current primitive closure status:

| Candidate | Status | Current evidence | Missing evidence |
|---|---|---|---|
| Firmware interaction | Partially complete | `system_app` can import HardwareBuffer dmabufs, submit full-size VPU requests, reach `apu_lib_apunn`, and drive the D2D_EXT handoff with firmware-visible descriptor/settings IOVAs. Host debug helpers also pin the basic Xtensa operation fields and confirm `10003` as `XTENSA_ANN_VERSION`. The flags matrix shows `settings[0]` is preserved and not a standalone completion trigger. The operand-offset matrix shows `entry+0x08` values `0/0x10/0x40/0x100` are accepted but do not change the incomplete code-first boundary. The target-wrapper-shaped `buffer_count=5`, no-settings-property replay is accepted, produces descriptor-0 writeback, and its wait variant returns `0`; the first-word matrix shows the writeback behaves like a status/flags word for ordinary inputs and an error/timeout state for `0xffffffff`. The descriptor-size matrix shows the writeback is not bounded by native descriptor length, because a repeat writes even with advertised size `0`. The priority matrix confirms `request+0xb68` is accepted as a D2D_EXT priority/slot input but does not change completion/output behavior. The buffer-count matrix shows `request+0x35 = 0` suppresses the state write and returns `-EIO` from wait, while `1..5` and `0x20` all produce the same descriptor-0 state write without APUNN output. The descriptor-port-id, descriptor-format, descriptor-plane-count, descriptor-height, XRP output-operand-id, and XRP input/output-count matrices show ordinary descriptor/operation metadata is accepted across wide values and does not change the APUNN output boundary. The target-wrapper opcode matrix proves firmware-side opcode interpretation: `10005..10007` enter timeout/EIO classes, `10006/10007` normalize code word `0` to `10005`, `10004` completes without the descriptor-0 state write, and `10001/10003/10008/10009` use the familiar `0x271b` state word. The fixed descriptor-layout matrix confirms slot `0` controls the visible state write: code-first layouts write code, while output-first writes output and returns `-EIO`; later output/data descriptor slots are not enough to trigger normal APUNN output. The output-first opcode matrix shows all `10001..10009` converge to output word `0xfffffffd`, leave code words unchanged, and return `-EIO`, so opcode-class status only appears when slot `0` points at the code/input operation window. This is enough for parser, descriptor-binding, opcode-class, and command-lifetime experiments. | Normal APUNN completion/output contract: `XTENSA_INFO00/02`, firmware-driven settings transition to `(flags & 0x0a) == 0x02`, and wrapper `WritebackCommand()` output flow. |
| Writeback leak | Not established | Split-target, output-first, descriptor-layout, output-first opcode, first-word, descriptor-size, priority, buffer-count, port-id, format, plane-count, height, output-operand-id, op-shape, and opcode matrix runs prove descriptor-backed first-word deltas follow copied native VPU descriptors when at least one descriptor is advertised. Opcode cases show source-sensitive status/error rewriting of descriptor slot `0` when slot `0` points at code/input, but settings/output/data remain unchanged. Current deltas still look status/side-effect-like, not a leak primitive. | A source-sensitive copyback or leak from firmware/APUNN output memory into attacker-readable memory. |
| Timeout UAF | Not established | Async, wait-after-async, teardown, and timeout runs map a real command lifetime edge; wait consumes the command id and converts the current request to `-EIO`. | Any stale command reuse, corrupted object access, refcount imbalance, or crash tied to fd close/process teardown/abort timing. |

1. **VPU D2D_EXT parameter ABI and IOVA-chain validation**: Highest current APUSYS task. `system_app` can import HardwareBuffer dmabufs, get APUSYS IOVA values, submit full-size normal-VPU requests, and reach `apu_lib_apunn`. IDA confirms the firmware handoff in `vpu_execute_d2d_handoff`: `request+0x50` is copied as `buffer_count * 0x40` bytes into the per-priority D2D buffer, then firmware receives `buffer_count`, the copied `struct vpu_buffer[]` IOVA, `sett_ptr`, and `sett_length` through `XTENSA_INFO12..15`; D2D_EXT also carries `request+0xb68` priority/slot plus preload entry/IRAM through `XTENSA_INFO11/16/19`. Runtime shows VPU boot/map activity, descriptor-following into imported memory, accepted priority values `0/1/2/3/0xffffffff`, and a clear `buffer_count` threshold where `0` fails but any tested nonzero value through `0x20` reaches the state-write path. APUNN output/data consumption is still not proven.
2. **Firmware completion/output contract under VPU dispatch**: The one-buffer internal query/status shapes timeout in the VPU worker. The two-native-buffer internal-command-shaped probe changes lifecycle behavior: `run_cmd_async` returns `0`, teardown logs residual command state, and the visible writeback follows copied native buffer descriptor `0`. Libvpu-style descriptor metadata (`port_id=1`, `format=0`, `plane_count=1`, `height=1`, `stride=size`), a five-descriptor alias shape, five identical code/input descriptors, five descriptor roles split as code/output/data-desc/data/plane, wrapper send-state settings `+0x00 = 0x5`, pre-seeded completion-like settings `+0x00 = 0x2/0x3`, output-first descriptor order, `request+0x38 = 0x68`, clearing the settings tuple to match the target wrapper's no-`setProperty` path, output header `+0x10 = 1`, wrapper-default output size `0x40`, wrapper dynamic output size `0x44`, one standard APUNN data descriptor, caller-supplied `request+0x28` bit `2`, relocated zero-output operand-list offsets `0/0x10/0x40/0x100`, output operand ids `0/1/2/3/0xffff`, input/output count combinations `0/0`, `0/1`, `0/2`, `1/0`, `1/1`, and `2/1`, descriptor-0 first-word values `0/0x2713/0x271b/0x504c4e30/0xffffffff`, descriptor size values `0..9/0xc/0x10/0x20/0x40/0x1c8`, request priority/slot values `0/1/2/3/0xffffffff`, nonzero request buffer counts `1/2/3/4/5/0x20`, descriptor port ids `0/1/2/3/4/0xff`, descriptor format bytes `0/1/2/3/4/0xff`, descriptor plane counts `0/1/2/3/4/0xff`, and descriptor heights `0/1/2/3/0x40/0xffffffff` do not change the completion/output boundary. The opcode matrix does change firmware behavior: `10005/10006/10007` timeout and `10006/10007` rewrite to `10005`; however, no opcode writes APUNN settings/output/data in the current request shape. The fixed descriptor-layout matrix shows code-first and mixed code-first layouts wait successfully and write only code word `0`, while output-first moves the write to output word `0` and returns `-EIO`; later descriptor slots are not treated as APUNN output/data completion targets by themselves. The output-first opcode matrix shows that with output in descriptor slot `0`, every recovered internal opcode `10001..10009` produces the same `-EIO` / `0xfffffffd` output-slot state and leaves the code window untouched. `buffer_count=0` is a wait-failure/no-write gate, not a completion trigger. The wrapper-zero-data wait-after-async variant maps to `-EIO`; the target-wrapper no-settings wait variant returns `0` for ordinary first words but still leaves APUNN settings/output unchanged. Static wrapper analysis now separates the property-path probes from the ordinary target `CreateVpuRequest()` path. The remaining contract is what makes firmware signal done through `XTENSA_INFO00/02`, actively transition settings flags to `(settings[0] & 0x0a) == 0x02`, and cause APUNN output writeback through the wrapper's normal output path.
3. **Timeout-path command object lifetime**: `run_cmd_async` returns before the worker completes. Guard, async-only, and two-buffer runs show residual command cleanup boundaries; the wait-after-async run consumes the command id and avoids `mdw_usr_destroy residual cmd`, but returns `-EIO`. The lifetime edge is therefore real enough for fd close, process teardown, timeout, and abort experiments, but UAF has not been demonstrated.
4. **Writeback attribution and command-buffer copyback**: Split-target, two-buffer, output-first, descriptor-layout, output-first opcode, wrapper-zero-data, first-word, descriptor-size, priority, buffer-count, port-id, format, plane-count, height, output-operand-id, op-shape, and opcode matrix runs localize the visible imported-buffer deltas to native VPU plane IOVAs reached through copied descriptors. The `ann_version_status_bit3_out0` run separates the earlier apparent `+8` operation-word write from the stable descriptor `0` writeback, and the output-first/descriptor-layout runs show that moving descriptor `0` to output moves the delta to the output header. The first-word matrix adds the current semantic clue: ordinary words become `old | 0xb`, while `0xffffffff` enters a timeout/error path. The opcode matrix adds a stronger semantic clue: `10005` is a timeout/error class, while `10006/10007` are rewritten to `10005` before the same wait-failure boundary. The output-first opcode matrix adds the inverse clue: when descriptor slot `0` points at output, all recovered internal opcodes produce the same `0xfffffffd` output-slot state and leave the code/opcode word unchanged. The descriptor-size repeat shows native descriptor length is not a hard bound for the state write. The priority repeat shows `request+0xb68` is not a hard bound either; after dispatch, the command-buffer tail copyback clears the priority/slot word. The buffer-count repeat shows the state write requires `request+0x35` to advertise at least one copied descriptor, but counts above one do not change the output boundary. The port-id, format, plane-count, height, output-operand-id, op-shape, and descriptor-layout repeats show ordinary descriptor/operation metadata and later descriptor slots are not hard role gates. A general writeback leak or APUNN output-section copyback is not established.
5. **Memory import / IOVA mapping path**: `0xC0384103` and `0xC038410F` import HardwareBuffer fds through APUSYS type-2/type-3 memory-create and copy out IOVA-like descriptor fields. This is the input path for any VPU request that references user-controlled memory.
6. **Command parsing/execution and ucmd paths**: Validated at zero-header, invalid SC type, request-size guard, full-size VPU request acceptance, and `apu_lib_apunn` lookup success.
7. **Device/resource control and secure alloc paths**: `0x4004413C/3D` call `mdw_usr_dev_sec_alloc/free`. These may influence APU power/security state. `0x400C4109` dispatches provider opcode `0`.

The surface is beyond simple ioctl reachability: `system_app` reaches APUSYS memory import, command parsing, scheduler handoff, and normal-VPU provider code. A kernel read/write primitive or privilege crossing is still not established.

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

Wrapper request inspection helper:

```sh
/opt/homebrew/share/android-commandlinetools/ndk/27.2.12479018/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android35-clang++ \
  -std=c++17 -Wall -Wextra -O2 -fPIE -pie -static-libstdc++ \
  13-apusys-ioctl-surface/poc/xrp_wrapper_inspect.cpp -ldl \
  -o /tmp/xrp_wrapper_inspect
adb -s 7FPE0824B0801372 push /tmp/xrp_wrapper_inspect /data/local/tmp/xrp_wrapper_inspect
adb -s 7FPE0824B0801372 shell chmod 755 /data/local/tmp/xrp_wrapper_inspect
adb -s 7FPE0824B0801372 shell /data/local/tmp/xrp_wrapper_inspect neuron
```

The helper now creates a direct Neuron `cXrpOptions` block with size `0x18` and,
by default, tries to obtain a `libapu_mdw.so` `apusys_session*` for
`cXrpOptions+0x10`. Static analysis shows the device wrapper requires that
session pointer before `XrpIntrinsicExecutor::InitDriver()` can create the
`vpu_xrp` stream and memory manager.

Current direct-Neuron controls:

- `--no-create-apusys-session`: reproduces `XRP_Create status=4`.
- default session path from shell: loads `/system/lib64/libapu_mdw.so`, but
  `apusysSession_createInstance()` returns null and `XRP_Create` remains status
  `4`. Static analysis now shows this helper first opens `/dev/apusys`; failure
  before constructing the `0xe8` byte `apusysSession` returns null.
- Java `app_process64` path: native-call stub works, but `System.load()` and
  direct `dlopen()` of `libapu_mdw.so` both fail from the `system_app` linker
  namespace. Loading `libvpu5.so` as a dependency-carrier fails for the same
  `libvndksupport.so` / `libdl_android.so` namespace reason, and `/proc/self/maps`
  does not show an already mapped `libapu_mdw.so`; `XRP_Create` remains status
  `4`.

The current interpretation is that direct `libneuron_platform.vpu.so`
wrapper-generated request dumping requires a process context that already has a
valid `libapu_mdw` session, or a hook inside such a process. Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_neuron_no_session_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_neuron_apu_mdw_session.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_java_neuron_native_dlopen_session_system_app.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_java_neuron_maps_session_system_app.txt`

The same helper can also target the APUWARE HIDL wrapper:

```sh
adb -s 7FPE0824B0801372 shell \
  'timeout 25 /data/local/tmp/xrp_wrapper_inspect apuware 2>&1; echo STATUS:$?'
```

Useful APUWARE variants:

```sh
adb -s 7FPE0824B0801372 shell \
  'timeout 25 /data/local/tmp/xrp_wrapper_inspect apuware --finalize-slot-index 2>&1; echo STATUS:$?'

adb -s 7FPE0824B0801372 shell \
  'timeout 25 /data/local/tmp/xrp_wrapper_inspect apuware --dlopen-lazy --finalize-slot-index 2>&1; echo STATUS:$?'

adb -s 7FPE0824B0801372 shell \
  '/data/local/tmp/xrp_wrapper_inspect apuware --finalize-slot-index --dlopen-timeout-sec=8 2>&1; echo STATUS:$?'
```

Earlier saved APUWARE runs loaded
`/system/system_ext/lib64/libapuwarexrp_v2.mtk.so`, proxied to
`android.hardware.neuralnetworks@1.3-service-mtk-neuron`, and reached
`XRP_Create`, `XRP_CreateCommand`, `XRP_AllocateBuffer`,
`XRP_UseInputBuffer`, and `XRP_UseOutputBuffer` successfully from shell. Those
runs expose the service-allocated `cXrpBufferInfo` records: fd at `+0x18`,
low 32-bit IOVA at `+0x20`, and host VA at `+0x28`.

The saved positive APUWARE boundary is `XRP_FinalizeCommand status=2`; a forced
`XRP_GetPreparedRequests()` after that failed finalize blocks in HIDL, so the
helper now skips request dumping unless finalize succeeds. Result files:

Static inspection of the local Neuron XRP wrapper shows that
`XrpIntrinsicWrapper::FinalizeCommand()` treats `cXrpBufferInfo+0x08` in the
finalize output vector as an output slot index and rejects entries whose index
is `>= output_count` with status `2`. The saved APUWARE run passed the raw
allocated output info (`+0x08 = 2`) with `output_count = 1`, so the old
`status=2` is best interpreted as an output-vector ABI mismatch, not as proof
that `apu_lib_apunn` rejected the command. The native helper now supports
`--finalize-slot-index` to rewrite that field to vector slot `0` before
finalize, plus `--dlopen-lazy` to isolate loader behavior. Current reruns stop
inside `dlopen(libapuwarexrp_v2.mtk.so)` before the helper reaches the wrapper
calls. The explicit 2026-06-15 in-process timeout run prints:

```text
mode=apuware handle=1 finalize_count=1 sync=0 finalize_index_mode=slot dlopen=now create_apusys_session=0 dlopen_timeout_sec=8
dlopen begin xrp path=/system/system_ext/lib64/libapuwarexrp_v2.mtk.so timeout_sec=8
dlopen timeout
STATUS:124
```

Static ELF inspection explains this boundary: `.init_array` at `0xe2f8`
contains `0xccf0`, which constructs the global `XrpIntrinsicExecutor` before
`dlopen()` returns; the constructor at `0x7100` calls
`INeuronXrp::tryGetService("default", false)` and retries after `usleep(5000)`.
The saved logcat for the latest rerun contains only a binder ioctl `-EINVAL`
from the helper process and no VPU/APUNN worker activity, so this is a
wrapper/HIDL initialization boundary rather than firmware request parsing.

- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_apuware_shell.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_apuware_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_apuware_buffer_id_retry.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_apuware_finalize_slot_index.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_apuware_finalize_slot_index_lazy.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_apuware_finalize_slot_index_rerun.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_apuware_finalize_slot_index_rerun_logcat.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_xrp_wrapper_inspect_apuware_dlopen_timeout.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_xrp_wrapper_inspect_apuware_dlopen_timeout_logcat.txt`

The pure Java `app_process64` inspector is `poc/XrpWrapperInspect.java`. It
loads the installed system wrapper, resolves exported function addresses from
ELF metadata plus `/proc/self/maps`, and calls them through the `DrmTrigger`
ART native-call stub, without executing native code from app data. The
native-call stub now preserves `LR/X30` around `BLR`; its libc `getpid()`
self-test returns the expected process id in `uid=1000(system)` /
`u:r:system_app:s0`.

The current `system_app` result still returns `XRP_Create status=4`. The newer
run additionally proves that `System.load()` and native `dlopen()` cannot bring
`libapu_mdw.so` into this app_process linker namespace, so the required
`cXrpOptions+0x10` session pointer is still missing. The positive comparison
still needs a context, option set, or hook point where `XRP_Create()` returns
`0` and the wrapper's memory manager exists before `XRP_CreateCommand()`.

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

Optional full-size VPU execution dispatch:

```sh
CLASSPATH=/data/data/com.android.settings/cache/apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --run-cmd-vpu-exec
```

`--run-cmd-vpu-exec` writes a valid APUSYS command header, a normal VPU subcommand (`type=0x03`) with `cb_info_size=0xb70` (the exact size `vpu_req_check` requires), and a VPU request containing `apu_lib_apunn` at `+0x04`, flags `0` at `+0x28`, and buffer_count `0` at `+0x35`. This is the minimal full-size request shape.

`--run-cmd-vpu-iova` chains `mem_create` type-2 import (to get an IOVA in the APU IOMMU address space) with a full-size VPU request that references that IOVA in the `libvpu.so` settings and plane descriptor fields. `--run-cmd-vpu-iova-control` follows the same setup but skips `run_cmd_async`, which isolates the post-dispatch buffer change.

`--run-cmd-vpu-xrp-iova` uses the same import and dispatch chain, but first writes the recovered APUNN/XRP settings layout into the imported IOVA: settings at `+0x000`, output at `+0x200`, data descriptor at `+0x300`, and a shared APUNN-data/native-plane target at `+0x400`. It dumps those windows before and after dispatch. `--run-cmd-vpu-xrp-iova-control` performs the same setup without final dispatch.

`--run-cmd-vpu-xrp-split-iova` keeps the same APUNN/XRP settings layout but separates the APUNN data descriptor target at `+0x400` from the native VPU plane0 MVA target at `+0x600`. This is the current writeback-attribution mode. `--run-cmd-vpu-xrp-split-iova-control` performs the same setup without final dispatch.

`--run-cmd-vpu-xrp-ann-version-iova` keeps the split-target attribution shape
and uses a nonzero target-side code section: `code_size=0x1c8`, opcode `10003`
(`XTENSA_ANN_VERSION` in the recovered helper names), no inputs, one output, and
output operand id `0`. It moves output/data/plane payload windows to
`+0x300/+0x400/+0x500/+0x700` so the larger code entry does not overlap them.
`--run-cmd-vpu-xrp-ann-version-iova-control` performs the same setup without
final dispatch.

`--run-cmd-vpu-xrp-ann-version-wrapper-zero-data-iova` tests the standard
wrapper default-output/zero-data shape with `code_size=0x1c8`, output size
`0x40`, and no APUNN data descriptors. Its `-control` variant skips dispatch.
`--run-cmd-vpu-xrp-ann-version-wrapper-zero-data-wait-iova` submits the same
request asynchronously and then passes the copied-out command id to
`mdw_usr_wait_cmd`.

`--run-cmd-vpu-xrp-internal-ann-version-iova` mirrors the host-wrapper
`PrepareInternalCommand()` shape: settings still describe the APUNN code/input
and output sections, while the native VPU request supplies two buffer
descriptors. Buffer `0` points at the code/input window and buffer `1` points
at the output window. The matching control,
`--run-cmd-vpu-xrp-internal-ann-version-iova-control`, performs the same setup
without final dispatch.

`--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc` keeps the same
two-buffer internal-command shape but fills the copied `struct vpu_buffer`
metadata the way the target `libvpu5.so::VpuRequestImp::addBuffer()` does:
`port_id=1`, DATA format, `plane_count=1`, `height=1`, `stride=size`, and
`length=size`. The matching `-control` mode skips dispatch. The
`--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-settings68`
variant keeps the code-first descriptor order and wrapper send-state flags but
sets the firmware-visible VPU request settings length to `0x68`; its
`-control` variant skips dispatch. The
`--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-wrapper-data`
variant combines that `0x68` settings length with wrapper-default output size
`0x40` and one standard 12-byte APUNN data descriptor; its `-control` variant
skips dispatch, and the saved dispatch/control pair keeps the same incomplete
code-first boundary. The matching
`--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-wrapper-data-output44`
variant keeps the one-data shape but uses the host wrapper's dynamic output
size for one operation (`0x44`) and sets the output header flag to `1`; its
`-control` variant skips dispatch. The saved pair still leaves settings,
output, data descriptor, and data payload unchanged. The matching
`--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-wrapper-data-code5`
variant keeps the one-data shape but sets `buffer_count=5` and points all five
native descriptors at the code/input window, matching the repeated-addBuffer
pattern recovered from `XrpVpuStream::CreateVpuRequest()`; its `-control`
variant skips dispatch. The saved pair still leaves settings, output, data
descriptor, and data payload unchanged. The matching
`--run-cmd-vpu-xrp-target-code5-no-settings-iova` variant keeps the five
code/input descriptors but clears `request+0x38/+0x40` so the request no
longer references the direct-probe settings-property buffer; its `-control`
variant skips dispatch, and `--run-cmd-vpu-xrp-target-code5-no-settings-wait-iova`
adds an immediate `mdw_usr_wait_cmd` call after async submit. The saved wait run
returns `0` from both async and wait, changes only code/input word `0`
`0x2713 -> 0x271b`, and leaves settings/output/data unchanged. The
`--run-cmd-vpu-xrp-target-code5-no-settings-codebuf-size-matrix-iova` and
`-control` variants keep that same request body but vary only the outer APUSYS
subcommand `cb_info_size` across `0x20`, `0x90`, `0x1c8`, `0xb6c`, `0xb70`, and
`0xb80`. Dispatch succeeds only at exact `0xb70`; the other sizes return `-EIO`
from wait and leave the request/code windows unchanged. The
`--run-cmd-vpu-xrp-target-code5-no-settings-word-matrix-iova` and `-control`
variants keep the same target request shape and vary only code/input word `0`.
Control preserves every word. Dispatch plus wait produces `old | 0xb` for
ordinary words and `0xffffffff -> 0xfffffffd` with wait `-EIO` for the all-ones
word. The `--run-cmd-vpu-xrp-target-code5-no-settings-size-matrix-iova` and
`-control` variants keep the same request and first word, but vary descriptor
payload size fields across `0..9`, `0xc`, `0x10`, `0x20`, `0x40`, and `0x1c8`.
Control preserves every word. Dispatch plus wait accepts every size; the repeat
run writes `0x2713 -> 0x271b` for every size, including `0`. The
`--run-cmd-vpu-xrp-target-code5-no-settings-buffer-count-matrix-iova` and
`-control` variants keep the same descriptors but vary request `+0x35` across
`0`, `1`, `2`, `3`, `4`, `5`, and `0x20`. Control is unchanged. Two dispatch
runs show `buffer_count=0` returns `-EIO` from wait and keeps code word `0x2713`,
while every tested nonzero count writes `0x2713 -> 0x271b` and returns `0` from
wait. The
`--run-cmd-vpu-xrp-target-code5-no-settings-port-matrix-iova` and `-control`
variants keep `buffer_count=5` and vary descriptor byte `+0x00` across `0`, `1`,
`2`, `3`, `4`, and `0xff`. Control is unchanged; dispatch plus wait returns `0`
for every tested port, and the repeat writes `0x2713 -> 0x271b` for all of them.
The `--run-cmd-vpu-xrp-target-code5-no-settings-format-matrix-iova` and
`-control` variants keep the same request and vary descriptor byte `+0x01`
across `0`, `1`, `2`, `3`, `4`, and `0xff`. Control is unchanged; two dispatch
runs return `0` from wait and write `0x2713 -> 0x271b` for every tested format.
The `--run-cmd-vpu-xrp-target-code5-no-settings-plane-count-matrix-iova` and
`-control` variants keep the same request and vary descriptor byte `+0x02`
across `0`, `1`, `2`, `3`, `4`, and `0xff`. Control is unchanged; dispatch plus
wait returns `0` for every tested plane count, and the repeat writes
`0x2713 -> 0x271b` for all of them.
The `--run-cmd-vpu-xrp-target-code5-no-settings-height-matrix-iova` and
`-control` variants keep the same request and vary descriptor word `+0x08`
across `0`, `1`, `2`, `3`, `0x40`, and `0xffffffff`. Control is unchanged.
Dispatch plus wait returns `0` for every tested height and never changes APUNN
settings/output/data. The first dispatch writes every case; later repeats miss
some visible descriptor-0 state writes, reinforcing that this first-word signal is
not a stable completion oracle.
The matching
`--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-wrapper-data-preload-slot`
variant also sets native VPU request flags `request+0x28 = 0x4`, entering the
kernel Preload/slot path directly; its `-control` variant skips dispatch. The
`--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-flags-matrix`
variant keeps the wrapper-one-data request shape and varies only
`settings[0]` across `0x4`, `0x2`, `0x3`, `0x5`, and `0x0d`; its `-control`
variant skips dispatch. The saved pair shows that even pre-seeded completion
states `0x2` and `0x3` are preserved as-is after dispatch, while output and
data stay unchanged and code word `0` still changes `0x2713 -> 0x271b`. The
settings flags word is therefore not a standalone APUNN completion trigger in
this direct request shape. The
`--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-operand-offset-matrix`
variant keeps the same wrapper-one-data request shape and varies the Xtensa
operation operand-list offset at entry `+0x08` across `0`, `0x10`, `0x40`, and
`0x100`; its `-control` variant skips dispatch. The saved pair shows all four
offsets are accepted but preserve the same incomplete code-first boundary. The
`--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-output-ready`
variant keeps the same request but sets the output header flag byte at
`output+0x10` to `1`; its `-control` variant skips dispatch. The
`--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-output-first`
variant keeps wrapper send-state flags and libvpu metadata but swaps descriptor
order so buffer `0` points at output and buffer `1` points at code/input; its
`-control` variant skips dispatch. The
`--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc5` variant also sets
`buffer_count=5` with code/output descriptor aliases, matching the static
observation that `XrpVpuStream::DefaultCreateVpuRequest()` calls `addBuffer()`
five times. Its `-control` variant skips dispatch.

`--run-cmd-vpu-xrp-target-code5-no-settings-descriptor-layout-matrix-iova`
keeps the target-wrapper no-settings-property shape and compares three
five-descriptor layouts: all descriptors pointing at code/input, a split
code/output/data-descriptor/data-payload/plane-payload layout, and the same
split layout with output in descriptor slot `0`. Its `-control` variant skips
dispatch. The saved dispatch and repeat runs confirm that descriptor slot `0`
controls the visible first-word state write; later output/data slots do not
create normal APUNN output completion.

`--run-cmd-vpu-xrp-target-code5-no-settings-output-first-opcode-matrix-iova`
keeps the output/code/data-descriptor/data-payload/plane-payload descriptor
layout and varies the internal opcode range `10001..10009`. Its `-control`
variant skips dispatch. The saved dispatch and repeat runs show a uniform
output-slot failure state for every tested opcode, which separates opcode
parsing from the descriptor-slot-0 status target.

`--run-cmd-vpu-xrp-op-matrix-iova` keeps the same split-target layout and runs
a fixed matrix of internal query/status operation shapes. Each case has a
matching before/after dump for settings, code, output, data descriptor,
APUNN-data payload, native VPU plane payload, and command-buffer windows.
`--run-cmd-vpu-xrp-op-matrix-iova-control` performs the same cases without
final dispatch.

`--run-cmd-vpu-xrp-op-case-iova=<case>` runs one case from the same matrix with
a 20s wait before the after-dump, which is long enough to catch the observed
VPU worker timeout. `--run-cmd-vpu-xrp-op-case-iova-control=<case>` performs
the same setup without final dispatch.

| Case label | Opcode | Name | Inputs | Outputs | Operand ids |
|---|---:|---|---:|---:|---|
| `get_algo_info_out0` | `10001` | `GET_ALGO_INFO` | `0` | `1` | `[0]` |
| `local_mem_info_out0` | `10002` | `LOCAL_MEM_INFO` | `0` | `1` | `[0]` |
| `ann_version_out0` | `10003` | `XTENSA_ANN_VERSION` | `0` | `1` | `[0]` |
| `detailed_op_info_out0` | `10004` | `GET_DETAILED_OP_INFO` | `0` | `1` | `[0]` |
| `ann_version_no_output` | `10003` | `XTENSA_ANN_VERSION` | `0` | `0` | `[]` |
| `ann_version_out1` | `10003` | `XTENSA_ANN_VERSION` | `0` | `1` | `[1]` |

```sh
# VPU exec without IOVA import:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --run-cmd-vpu-exec

# VPU request with IOVA import:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --run-cmd-vpu-iova

# Same import setup, no final run_cmd_async dispatch:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --run-cmd-vpu-iova-control

# VPU request with APUNN/XRP-shaped settings:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --run-cmd-vpu-xrp-iova

# Same APUNN/XRP setup, no final run_cmd_async dispatch:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --run-cmd-vpu-xrp-iova-control

# APUNN/XRP setup with data descriptor and plane0 MVA split:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --run-cmd-vpu-xrp-split-iova

# Same split-target APUNN/XRP setup, no final run_cmd_async dispatch:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --run-cmd-vpu-xrp-split-iova-control

# Nonzero code-section APUNN/XRP setup:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --run-cmd-vpu-xrp-ann-version-iova

# Same nonzero code-section setup, no final run_cmd_async dispatch:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --run-cmd-vpu-xrp-ann-version-iova-control

# Host-wrapper-style two-native-buffer internal command:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --run-cmd-vpu-xrp-internal-ann-version-iova

# Same two-buffer setup, no final run_cmd_async dispatch:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --run-cmd-vpu-xrp-internal-ann-version-iova-control

# Two-buffer internal command with libvpu-style descriptor metadata:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe \
  --run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc

# Same libvpu metadata setup, no final run_cmd_async dispatch:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe \
  --run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-control

# Two-buffer libvpu metadata setup with wrapper send-state settings[0]=0x5:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe \
  --run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags

# Same send-state setup, no final run_cmd_async dispatch:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe \
  --run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-control

# Libvpu metadata plus five copied descriptors:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe \
  --run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc5

# Same five-descriptor setup, no final run_cmd_async dispatch:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe \
  --run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc5-control

# Closest target-wrapper replay: five code/input descriptors, no settings property:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe \
  --run-cmd-vpu-xrp-target-code5-no-settings-iova

# Same target-wrapper replay with explicit mdw_usr_wait_cmd:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe \
  --run-cmd-vpu-xrp-target-code5-no-settings-wait-iova

# Target-wrapper five-descriptor layout matrix:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe \
  --run-cmd-vpu-xrp-target-code5-no-settings-descriptor-layout-matrix-iova

# Same descriptor-layout matrix, no final run_cmd_async dispatch:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe \
  --run-cmd-vpu-xrp-target-code5-no-settings-descriptor-layout-matrix-iova-control

# Target-wrapper output-first descriptor layout with opcode matrix:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe \
  --run-cmd-vpu-xrp-target-code5-no-settings-output-first-opcode-matrix-iova

# Same output-first opcode matrix, no final run_cmd_async dispatch:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe \
  --run-cmd-vpu-xrp-target-code5-no-settings-output-first-opcode-matrix-iova-control

# Target-wrapper outer APUSYS codebuf-size matrix:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe \
  --run-cmd-vpu-xrp-target-code5-no-settings-codebuf-size-matrix-iova

# Same outer codebuf-size matrix, no final run_cmd_async dispatch:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe \
  --run-cmd-vpu-xrp-target-code5-no-settings-codebuf-size-matrix-iova-control

# APUNN/XRP internal query/status opcode and operand matrix:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --run-cmd-vpu-xrp-op-matrix-iova

# Same matrix setup, no final run_cmd_async dispatch:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe --run-cmd-vpu-xrp-op-matrix-iova-control

# One APUNN/XRP matrix case with a longer timeout attribution window:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe \
  --run-cmd-vpu-xrp-op-case-iova=get_algo_info_out0

# Same single-case setup, no final run_cmd_async dispatch:
CLASSPATH=.../apusys_ioctl_probe.dex \
  app_process64 /system/bin ApusysIoctlProbe \
  --run-cmd-vpu-xrp-op-case-iova-control=get_algo_info_out0
```

Automated run:

```sh
# --run-cmd-vpu-exec:
python3 13-apusys-ioctl-surface/poc/run_system_app_probe.py \
  -s 7FPE0824B0801372 --local-port 48888 \
  --mode=--run-cmd-vpu-exec \
  --result-name 13_apusys_run_cmd_vpu_exec.txt \
  --kernel-result-name 13_apusys_run_cmd_vpu_exec_kernel.txt \
  --kernel-pattern "apusys|vpu|mdw|xos|devapc|iommu|vpu_req_check|vpu_execute|sched_trace|cmd_done|timeout|Timeout|TIMEOUT|oops|panic" \
  --timeout 180

# --run-cmd-vpu-iova:
python3 13-apusys-ioctl-surface/poc/run_system_app_probe.py \
  -s 7FPE0824B0801372 --local-port 48888 \
  --mode=--run-cmd-vpu-iova \
  --result-name 13_apusys_run_cmd_vpu_iova_final.txt \
  --kernel-result-name 13_apusys_run_cmd_vpu_iova_final_kernel.txt \
  --kernel-pattern "apusys|vpu|mdw|xos|devapc|iommu|vpu_req_check|vpu_execute|sched_trace|cmd_done|timeout|Timeout|TIMEOUT|oops|panic" \
  --timeout 180

# --run-cmd-vpu-iova-control:
python3 13-apusys-ioctl-surface/poc/run_system_app_probe.py \
  -s 7FPE0824B0801372 --local-port 48888 \
  --mode=--run-cmd-vpu-iova-control \
  --result-name 13_apusys_run_cmd_vpu_iova_control.txt \
  --kernel-result-name 13_apusys_run_cmd_vpu_iova_control_kernel.txt \
  --kernel-pattern "apusys|vpu|mdw|xos|devapc|iommu|vpu_req_check|vpu_execute|sched_trace|cmd_done|timeout|Timeout|TIMEOUT|oops|panic" \
  --timeout 180

# --run-cmd-vpu-xrp-iova:
python3 13-apusys-ioctl-surface/poc/run_system_app_probe.py \
  -s 7FPE0824B0801372 --local-port 48888 \
  --mode=--run-cmd-vpu-xrp-iova \
  --result-name 13_apusys_run_cmd_vpu_xrp_iova.txt \
  --kernel-result-name 13_apusys_run_cmd_vpu_xrp_iova_kernel.txt \
  --kernel-pattern "apusys|vpu|mdw|xos|devapc|iommu|vpu_req_check|vpu_execute|sched_trace|cmd_done|timeout|Timeout|TIMEOUT|oops|panic" \
  --timeout 180

# --run-cmd-vpu-xrp-iova-control:
python3 13-apusys-ioctl-surface/poc/run_system_app_probe.py \
  -s 7FPE0824B0801372 --local-port 48888 \
  --mode=--run-cmd-vpu-xrp-iova-control \
  --result-name 13_apusys_run_cmd_vpu_xrp_iova_control.txt \
  --kernel-result-name 13_apusys_run_cmd_vpu_xrp_iova_control_kernel.txt \
  --kernel-pattern "apusys|vpu|mdw|xos|devapc|iommu|vpu_req_check|vpu_execute|sched_trace|cmd_done|timeout|Timeout|TIMEOUT|oops|panic" \
  --timeout 180

# --run-cmd-vpu-xrp-split-iova:
python3 13-apusys-ioctl-surface/poc/run_system_app_probe.py \
  -s 7FPE0824B0801372 --local-port 48888 \
  --mode=--run-cmd-vpu-xrp-split-iova \
  --result-name 13_apusys_run_cmd_vpu_xrp_split_iova.txt \
  --kernel-result-name 13_apusys_run_cmd_vpu_xrp_split_iova_kernel.txt \
  --kernel-pattern "apusys|vpu|mdw|xos|devapc|iommu|vpu_req_check|vpu_execute|sched_trace|cmd_done|timeout|Timeout|TIMEOUT|oops|panic" \
  --timeout 180

# --run-cmd-vpu-xrp-split-iova-control:
python3 13-apusys-ioctl-surface/poc/run_system_app_probe.py \
  -s 7FPE0824B0801372 --local-port 48888 \
  --mode=--run-cmd-vpu-xrp-split-iova-control \
  --result-name 13_apusys_run_cmd_vpu_xrp_split_iova_control.txt \
  --kernel-result-name 13_apusys_run_cmd_vpu_xrp_split_iova_control_kernel.txt \
  --kernel-pattern "apusys|vpu|mdw|xos|devapc|iommu|vpu_req_check|vpu_execute|sched_trace|cmd_done|timeout|Timeout|TIMEOUT|oops|panic" \
  --timeout 180

# --run-cmd-vpu-xrp-ann-version-iova:
python3 13-apusys-ioctl-surface/poc/run_system_app_probe.py \
  -s 7FPE0824B0801372 --local-port 48888 \
  --mode=--run-cmd-vpu-xrp-ann-version-iova \
  --result-name 13_apusys_run_cmd_vpu_xrp_ann_version_iova.txt \
  --kernel-result-name 13_apusys_run_cmd_vpu_xrp_ann_version_iova_kernel.txt \
  --kernel-pattern "apusys|vpu|mdw|xos|devapc|iommu|vpu_req_check|vpu_execute|sched_trace|cmd_done|timeout|Timeout|TIMEOUT|oops|panic" \
  --timeout 180

# --run-cmd-vpu-xrp-ann-version-iova-control:
python3 13-apusys-ioctl-surface/poc/run_system_app_probe.py \
  -s 7FPE0824B0801372 --local-port 48888 \
  --mode=--run-cmd-vpu-xrp-ann-version-iova-control \
  --result-name 13_apusys_run_cmd_vpu_xrp_ann_version_iova_control.txt \
  --kernel-result-name 13_apusys_run_cmd_vpu_xrp_ann_version_iova_control_kernel.txt \
  --kernel-pattern "apusys|vpu|mdw|xos|devapc|iommu|vpu_req_check|vpu_execute|sched_trace|cmd_done|timeout|Timeout|TIMEOUT|oops|panic" \
  --timeout 180

# --run-cmd-vpu-xrp-op-matrix-iova-control:
python3 13-apusys-ioctl-surface/poc/run_system_app_probe.py \
  -s 7FPE0824B0801372 --local-port 48888 \
  --mode=--run-cmd-vpu-xrp-op-matrix-iova-control \
  --result-name 13_apusys_run_cmd_vpu_xrp_op_matrix_iova_control.txt \
  --kernel-result-name 13_apusys_run_cmd_vpu_xrp_op_matrix_iova_control_kernel.txt \
  --kernel-pattern "apusys|vpu|mdw|xos|devapc|iommu|vpu_req_check|vpu_execute|sched_trace|cmd_done|timeout|Timeout|TIMEOUT|oops|panic" \
  --timeout 360

# --run-cmd-vpu-xrp-op-matrix-iova:
python3 13-apusys-ioctl-surface/poc/run_system_app_probe.py \
  -s 7FPE0824B0801372 --local-port 48888 \
  --mode=--run-cmd-vpu-xrp-op-matrix-iova \
  --result-name 13_apusys_run_cmd_vpu_xrp_op_matrix_iova.txt \
  --kernel-result-name 13_apusys_run_cmd_vpu_xrp_op_matrix_iova_kernel.txt \
  --kernel-pattern "apusys|vpu|mdw|xos|devapc|iommu|vpu_req_check|vpu_execute|sched_trace|cmd_done|timeout|Timeout|TIMEOUT|oops|panic" \
  --timeout 360
```

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

The remaining APUSYS closure items are:

- Finish the positive wrapper-generated request dump. Direct
  `libneuron_platform.vpu.so` still returns `XRP_Create status=4`. The APUWARE
  HIDL wrapper previously reached allocation/use-buffer and returned
  `XRP_FinalizeCommand status=2`; local wrapper static analysis now points to a
  likely output-vector index mismatch (`out_info+0x08 = 2` with
  `output_count = 1`). The current device state now blocks inside
  `dlopen(libapuwarexrp_v2.mtk.so)` even with `RTLD_LAZY`, so the next positive
  wrapper step is to recover that service/library initialization state, rerun
  APUWARE with `--finalize-slot-index`, and then dump
  `XRP_GetPreparedRequests()`.
- Map how `apu_lib_apunn` uses the copied `struct vpu_buffer[]` beyond ordinary libvpu metadata. The outer APUSYS `cb_info_size` gate is now reduced to exact `0xb70`; tested non-exact sizes `0x20/0x90/0x1c8/0xb6c/0xb80` fail before the visible descriptor state writeback. `port_id=1`, DATA format, `plane_count=1`, `height=1`, `stride=size`, `length=size`, `buffer_count=5` aliases, five identical code/input descriptors, target-wrapper no-settings-property descriptors, five-descriptor layouts split as code/output/data-desc/data/plane with both code-first and output-first slot `0`, wrapper send-state settings `+0x00 = 0x5`, pre-seeded completion-like settings `+0x00 = 0x2/0x3`, output-first descriptor order, output-first descriptor layout across opcodes `10001..10009`, `request+0x38 = 0x68`, `request+0x38/+0x40 = 0/0`, output header `+0x10 = 1`, wrapper dynamic output size `0x44`, the standard `{type=3, size, iova}` data descriptor under `settings_len=0x68`, direct `request+0x28` bit `2` Preload/slot selection, zero-output operand-list offsets `0/0x10/0x40/0x100`, output operand ids `0/1/2/3/0xffff`, input/output count combinations `0/0`, `0/1`, `0/2`, `1/0`, `1/1`, and `2/1`, opcodes `10001..10009`, descriptor-0 first-word values `0/0x2713/0x271b/0x504c4e30/0xffffffff`, descriptor payload sizes `0..9/0xc/0x10/0x20/0x40/0x1c8`, request buffer counts `0/1/2/3/4/5/0x20`, descriptor port ids `0/1/2/3/4/0xff`, descriptor format bytes `0/1/2/3/4/0xff`, descriptor plane counts `0/1/2/3/4/0xff`, and descriptor heights `0/1/2/3/0x40/0xffffffff` have been tested without producing normal completion behavior.
- Determine the firmware completion/output contract: which APUNN settings and buffer descriptor fields cause `DS_PREEMPT_DONE` / `DS_ALG_DONE`, `XTENSA_INFO00`, and `XTENSA_INFO02` to be produced, which run changes settings flags to satisfy `(settings[0] & 0x0a) == 0x02`, and which path maps to host `WritebackCommand()` output handling. The `ann_version_status_bit3_out0` op-word experiment has ruled out pre-setting bit `3` in opcode `10003` as the missing completion condition. The target-wrapper opcode matrix proves `10001..10009` are parsed differently, and the output-first opcode matrix proves those opcode classes do not cause APUNN output completion when descriptor slot `0` points at output.
- Shift the next matrix toward the standard wrapper path recovered from `libneuron_platform.so`: command-buffer id input binding, `PrepareXtensaCommandBuffer()`, output allocation/binding, `PrepareOutputBuffer()`, and `PrepareDataBuffer()` / `FinalizeDataBuffer()`. The current descriptor shapes prove descriptor-following; the `0x68` settings-length run, `output+0x10 = 1` run, wrapper dynamic output size `0x44` run, and flags matrix rule out several wrapper-visible candidates as the missing completion condition. Firmware still does not actively transition settings to `(settings[0] & 0x0a) == 0x02` or produce the wrapper's normal APUNN output writeback.
- Map the remaining completion-dependent VPU/APUNN-side operation-entry semantics. Host/debug helpers now statically pin opcode, stride, operand-list offset, input count, output count, operand-id layout, and the `10003` / `XTENSA_ANN_VERSION` mapping; runtime now also proves opcode-class differences for `10001..10009`. What remains is how a normal completed request binds those operation entries to APUNN output/data writes.
- Attribute the native plane first-word writebacks semantically: the matrix points to a status/flags word, but the owner is still unresolved between firmware status, driver-side status, request-result fields, and internal command side effects.
- Test `mdw_cmd_sc_clr_hnd` copyback behavior specifically on timeout/abort paths. The normal provider-return copyback mechanism is mapped; the lifetime/race behavior around delayed failure is not.
- Test timeout lifecycle races around fd close / `mdw_usr_destroy` / scheduler cleanup.
- Continue `mdla_run_command_sync` and `edma_execute` input structure mapping for non-VPU provider execution paths.
