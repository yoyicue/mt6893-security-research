# Experiment Closure Status

This document summarizes the current end-to-end state of the MT6893 / MT8797 `uid=1000(system)` experiment batch as of 2026-06-14.

## Closed Loops

| Area | Status | Evidence |
|---|---|---|
| `system_app` execution channel | Closed for current probes. CVE-2024-31317 bind shell rebuild is scripted, verified as `uid=1000(system)` / `u:r:system_app:s0`, and APUSYS runner can invoke it with `--rebuild-shell`. | `06-cve-2024-31317-zygote-injection/poc/rebuild_bind_shell.py`, `13-apusys-ioctl-surface/poc/run_system_app_probe.py` |
| APUSYS fd source | Closed. Direct `/dev/ion`, `/dev/ashmem`, dma-heap nodes, and DRM PRIME export are blocked or absent from this context; `app_process64` + `HardwareBuffer` supplies the working dmabuf fd. | `poc-run-results/2026-06-14-batch/13_apusys_hardwarebuffer_app_process.txt`, `13_apusys_mem_ion.txt`, `13_apusys_mem_dmabuf.txt`, `13_apusys_fd_scan.txt` |
| APUSYS memory import | Closed for reachability. Both APUSYS type-2 and type-3 memory-create paths import the HardwareBuffer fd and cleanup succeeds. | `poc-run-results/2026-06-14-batch/13_apusys_hardwarebuffer_app_process.txt` |
| APUSYS provider opcode-0 dispatch | Closed for reachability. `0x400C4109` reaches MDLA, normal VPU, EDMA, and MDLA RT provider opcode-0 paths; VPU RT returns `EACCES`. | `poc-run-results/2026-06-14-batch/13_apusys_dev_ctrl.txt` |
| APUSYS normal VPU `ucmd` lookup | Closed for lookup depth. `0x8001 + key` reaches the userspace-compatible lookup path; `apu_lib_apunn` returns provider success on core `0` and `1` without visible first-64-byte Image-plane writeback. | `poc-run-results/2026-06-14-batch/13_apusys_ucmd_keydump_apunn.txt`, `13-apusys-ioctl-surface/README.md` |
| APUSYS `run_cmd_async` parser depth | Closed. Zero-header reaches parser rejection, valid header plus invalid subcommand reaches `mdw_cmd_sc_valid invalid type(32)`. | `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_hardwarebuffer.txt`, `13_apusys_run_cmd_invalid_sc.txt`, `13_apusys_run_cmd_invalid_sc_kernel.txt` |
| APUSYS `run_cmd_async` provider handoff | Closed for non-executing guard depth. A valid normal VPU `type=0x03` subcommand with `cb_info_size=0x20` queues successfully and reaches `vpu_req_check: invalid size of vpu request` in `apusys_vpu0`. | `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_guard.txt`, `13_apusys_run_cmd_vpu_guard_kernel.txt` |
| Display / DRM safe probes | Closed for current direct-path ranking. `/dev/dri/card0` is reachable; tested `CREATE_DUMB`, atomic, private getter, register, and color-transform probes are mapped to guarded or permission-gated behavior. | `poc-run-results/2026-06-14-batch/07_*.txt`, `10_32865_color_transform.txt`, `poc-run-results/2026-06-14-batch/README.md` |
| Mali legacy candidates | Closed for current target ranking. JIT `DONT_NEED`, CVE-2022-36449 refcount, write-readonly, WRITE_VALUE boundary, and CVE-2023-33200 imported-buffer paths are downgraded or dead on this firmware. | `02-*`, `03-*`, `05-*`, `11-cve-2022-22706-mali-write-readonly/`, `12-cve-2023-33200-mali-race-uaf/` |

## Current Ranking Boundary

The current experiment batch proves reachability and guard depth, not a kernel write primitive.

1. Display / DRM OOB cluster remains the highest practical target family, with `32867` / `32868` ahead of the already probed guarded paths.
2. APUSYS remains medium-high research priority because the chain now reaches memory import, `ucmd`, queue, scheduler, and normal VPU provider request-size guard paths from `system_app`.
3. Direct ION from `system_app` is downgraded because `/dev/ion` open returns `EACCES`.
4. Mali old chains, AF_UNIX, and sk_buff offset work do not gain new risk from `uid=1000(system)`.

The detailed ranking is in `poc-run-results/2026-06-14-batch/README.md` and `uid1000-risk-assessment.md`.

## Separate Follow-Up Tracks

These are next research tasks, not missing pieces of the APUSYS guard experiment:

| Track | Next work |
|---|---|
| APUSYS request structures | Map normal VPU request `0xb70`, MDLA, and EDMA request layouts before any command that can submit real hardware work. |
| APUSYS command lifetime | Inspect async command cleanup after worker-side rejection, including the observed residual-command warning. |
| Display `32867` / `32868` | Map exact MTK DRM handlers and patched ALPS deltas before adding new PoC logic. |
| Service-mediated bugs | Check secmem/keyinstall, CMDQ/PQ/MMP, RIL, and GPS through service or HAL entry points rather than direct device nodes. |

## Repro Commands

Existing `system_app` shell:

```sh
python3 13-apusys-ioctl-surface/poc/run_system_app_probe.py \
  -s 7FPE0824B0801372 --local-port 48888
```

Clean rebuild of the `system_app` shell plus APUSYS guard run:

```sh
python3 13-apusys-ioctl-surface/poc/run_system_app_probe.py \
  -s 7FPE0824B0801372 --local-port 48888 --rebuild-shell
```
