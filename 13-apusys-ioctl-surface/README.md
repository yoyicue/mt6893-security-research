# APUSYS ioctl surface

## Summary

`/dev/apusys` is reachable from `uid=1000(system)` on the target device:

```text
crw-rw---- 1 system camera u:object_r:apusys_device:s0 /dev/apusys
[OPEN] /dev/apusys  fd=5
```

The current result is **reachable but not yet mapped to a confirmed vulnerability**. IDA analysis shows the device is the MTK APUSYS midware character device. Its main ioctl handler is now named `mdw_ioctl` in the IDB at `0xffffffc00878a0ec`.

This directory documents the ioctl surface and provides only non-destructive probes. It does not include command buffers, device execution payloads, heap shaping, or privilege-escalation logic.

## IDA handler map

The kernel image under analysis is `07-cve-2023-32836-display-overflow/vmlinux.bin.i64`. The relevant APUSYS functions were renamed in IDA and the IDB was saved.

| Function | Address | Role |
|---|---:|---|
| `mdw_ioctl` | `0xffffffc00878a0ec` | `/dev/apusys` `unlocked_ioctl` dispatcher |
| `apusys_midware_register_driver` | `0xffffffc008796d28` | Registers the APUSYS midware device |
| `mdw_usr_mem_create` | `0xffffffc00878bc10` | Allocates/imports APUSYS user memory metadata |
| `mdw_usr_mem_free` | `0xffffffc00878bd14` | Frees APUSYS user memory metadata |
| `mdw_usr_dev_sec_alloc` | `0xffffffc00878c00c` | Secure-device allocation path |
| `mdw_usr_dev_sec_free` | `0xffffffc00878c418` | Secure-device free path, includes an id `< 0x40` guard |
| `mdw_usr_ucmd` | `0xffffffc00878c6ac` | User-command control path |
| `mdw_usr_run_cmd_async` | `0xffffffc00878c7f8` | Parses and queues an APUSYS command |
| `mdw_usr_get_cmd_ops` | `0xffffffc008791b04` | Returns the ops table stored at `0xffffffc00a188e58` |
| `mdw_wait_cmd` | `0xffffffc00878ca68` | Internal wait helper |
| `mdw_usr_wait_cmd` | `0xffffffc00878cd50` | User wait-command path |
| `mdw_usr_run_cmd_sync` | `0xffffffc00878ce18` | `run_cmd_async` followed by wait |
| `mdw_usr_init` | `0xffffffc00878d2c8` | APUSYS user subsystem init |
| `mdw_usr_destroy` | `0xffffffc00878cfc4` | APUSYS user teardown |

Hex-Rays did not decompile the main APUSYS functions cleanly, so this mapping comes from disassembly, string xrefs, ioctl constants, and call targets.

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
| `0x400C4109` | `0x0c` | Small command-control helper | Low-medium; needs naming |
| `0x4038410C` | `0x38` | Disabled/error path | Low |
| `0x4038410D` | `0x38` | Disabled/error path | Low |
| `0x4014410E` | `0x14` | `mdw_usr_ucmd` | Medium; rejects early if field at `+0x0c` is nonzero |
| `0x4004413B` | `0x04` | Threshold constant, no active handler observed | Low |
| `0x4004413C` | `0x04` | Copies **0x20** bytes, calls `mdw_usr_dev_sec_alloc` | Medium; `_IOC_SIZE` mismatch but guarded copy |
| `0x4004413D` | `0x04` | Copies **0x20** bytes, calls `mdw_usr_dev_sec_free` after id `< 0x40` | Medium |

The `0x4004413C/3D` size mismatch is important for testing: the command encoding advertises 4 bytes, but `mdw_ioctl` actually validates and copies a 0x20-byte user buffer. This is not by itself a vulnerability because the copy length is fixed and guarded, but a PoC that passes only `_IOC_SIZE(cmd)` bytes will exercise the wrong failure mode.

## Current risk ranking

1. **Command parsing/execution path**: `0xC0184107` and `0x40184106` reach `mdw_usr_run_cmd_async`, which allocates an internal command object, calls an ops-table parser, and queues work. Related strings include `mdw_usr_par_apu_cmd`, `vpu_ucmd_handle`, `edma_execute`, `mdla_*`, and `reviser_*`.
2. **Memory import / IOVA mapping path**: `0xC0384103` and `0xC038410F` lead to `mdw_usr_mem_create`. Related strings include `mdw_mem_ion_map_iova`, `vpu_map_sg_to_iova`, `reviser_get_iova`, and `prepare_dmabuf`.
3. **Secure device allocation path**: `0x4004413C/3D` call `mdw_usr_dev_sec_alloc/free`. The free path has an id `< 0x40` guard, but the alloc path still needs deeper review.
4. **Handshake/wait/simple control paths**: useful for reachability, lower standalone risk.

The surface is more promising than the current display OOB family because it is a directly open proprietary device from `system_app` and reaches several hardware subsystems. It is still below a confirmed exploitable CVE because no memory corruption, UAF, OOB access, or privilege crossing has been confirmed.

## Safe experiments

[`poc/ApusysIoctlProbe.java`](poc/ApusysIoctlProbe.java) is the preferred `system_app` probe because it reuses the pure-Java syscall helper from `DrmTrigger.java`. By default it:

- Opens `/dev/apusys` with `O_RDWR`.
- Sends one unknown command to confirm generic rejection.
- Sends disabled/error-only APUSYS commands.
- Sends `run_cmd_async`, `run_cmd_sync`, and `mdw_usr_ucmd` buffers with the early-reject field at offset `+0x0c` set to `1`.

It deliberately does not call the memory-create, memory-free, secure-alloc, secure-free, or real command-buffer execution paths.

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

`--query` sets the handshake mode field at offset `+0x0c` to `1` and prints the returned 0x28-byte buffer. It should remain non-destructive, but it is not needed for basic handler reachability.

[`poc/apusys_ioctl_probe.c`](poc/apusys_ioctl_probe.c) is a native version of the same reject-only checks for root/permissive lab contexts. It compiles successfully, but on the current device direct `adb shell` execution returns `open(/dev/apusys) failed: EACCES` because shell is not `system` or `camera`, and `system_app` cannot execute native ELF files from app data under SELinux enforcing.

Current local negative-control result:

```text
adb shell dalvikvm64 -cp /data/local/tmp/apusys_ioctl_probe.dex ApusysIoctlProbe
...
[+] Syscall infrastructure ready
Exception in thread "main" java.lang.RuntimeException: open(/dev/apusys) failed: errno=13
```

That is expected for `uid=2000(shell)`. It validates the Java probe startup path but not APUSYS handler reachability. The meaningful runtime test must run from the `uid=1000(system)` / `u:r:system_app:s0` context that previously opened `/dev/apusys`.

## Next analysis steps

- Follow the APUSYS parser ops table returned by `mdw_usr_get_cmd_ops` and stored at `0xffffffc00a188e58` from `mdw_usr_init`.
- Name and inspect the parser function that logs `mdw_usr_par_apu_cmd`.
- Map `vpu_ucmd_handle` and EDMA/MDLA command validators before attempting any valid command-buffer experiment.
- Only after the parser bounds are understood, add a "no cmdbuf" or invalid-device command probe that is expected to fail before hardware execution.
