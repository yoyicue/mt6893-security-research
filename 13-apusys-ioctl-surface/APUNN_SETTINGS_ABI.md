# APUNN settings ABI notes

This file tracks the current model for how `apu_lib_apunn` receives request
parameters. It separates the kernel APUSYS command wrapper, the native
`libvpu.so` VPU request, and the `libneuron_platform.vpu.so` XRP command buffer
that is passed behind `request+0x40` (`setting_iova`).

## Layering

Normal execution uses three nested formats:

1. APUSYS `run_cmd_async` command buffer: top-level APUSYS header, one normal
   VPU subcommand (`type=0x03`), and an inline code buffer.
2. Native VPU request: the inline code buffer is exactly `0xb70` bytes and
   matches `libvpu.so::VpuRequestImp+0x38`.
3. APUNN/XRP settings buffer: `request+0x38/+0x40` point to a separately
   allocated settings/property buffer. For `apu_lib_apunn`, `libneuron` fills
   this buffer with an XRP command descriptor that points to code, output, and
   data sections.

The current `--run-cmd-vpu-iova` probe validates layer 2 and dispatch
reachability. It intentionally uses a minimal malformed settings payload by
pointing `setting_iova` at the imported HardwareBuffer base. The newer
`--run-cmd-vpu-xrp-iova` mode builds the layer-3 XRP shape in that imported
buffer so runtime output can distinguish settings, output, data descriptor,
data-payload, and native VPU plane-MVA writeback.

## Native VPU request link

`libvpu.so::VpuRequestImp` owns a native request at object offset `+0x38`.
`VpuStreamImp::runReq()` and `packRequest()` copy exactly `0xb70` bytes from
that native request into APUSYS command memory before dispatch. Relevant fields:

| Request offset | Meaning |
|---:|---|
| `+0x04` | Algorithm name, `apu_lib_apunn` for the tested path |
| `+0x28` | Flags; the current probes use `0` to take normal `vpu_execute` |
| `+0x35` | `buffer_count`, bounded by the kernel to `< 0x21` |
| `+0x38` | Settings/property buffer length |
| `+0x40` | Settings/property buffer MVA/IOVA |
| `+0x50 + i*0x40` | Per-buffer descriptor array |
| `+0x68 + i*0x40 + p*0x10` | Per-plane MVA/IOVA after `mmapMVA()` |

`prepareSettBuf(size)` allocates the settings memory and writes
`request+0x38/+0x40` from the allocated memory object. `setProperty(ptr, size)`
then copies `size` bytes from the caller's property buffer into that settings
memory and cache-syncs it. Therefore `setting_iova` is not a generic image
plane. It is the firmware-facing settings/property payload.

## Kernel-to-firmware D2D handoff

The APUSYS VPU 4.0 kernel path adds one more important translation layer before
`apu_lib_apunn` sees the request. In the local kernel source mirror,
`drivers/misc/mediatek/apusys/vpu/4.0/vpu_main.c::vpu_req_check()` accepts an
exact `sizeof(struct vpu_request)` request, validates only the known flag bits,
and bounds `buffer_count` to `VPU_MAX_NUM_PORTS`.

`drivers/misc/mediatek/apusys/vpu/p1/vpu_hw.c::vpu_execute_d2d()` then prepares
the firmware request:

| Firmware input | Filled from |
|---|---|
| D2D command buffer | `memcpy(req->buffers, sizeof(struct vpu_buffer) * req->buffer_count)` into the per-priority kernel command IOVA |
| `XTENSA_INFO01` | `0x22` (`DO_D2D`) or `0x24` (`DO_D2D_EXT`) |
| `XTENSA_INFO11` | Request priority for preload/D2D_EXT |
| `XTENSA_INFO12` | `req->buffer_count` |
| `XTENSA_INFO13` | IOVA of the copied `struct vpu_buffer[]` command buffer |
| `XTENSA_INFO14` | `req->sett_ptr` |
| `XTENSA_INFO15` | `req->sett_length` |
| `XTENSA_INFO16` | Preload entry address for D2D_EXT |
| `XTENSA_INFO19` | Preload IRAM MVA for D2D_EXT |

The firmware therefore does not receive the user-provided `struct vpu_request`
directly. It receives a register tuple plus a firmware-readable copy of
`struct vpu_buffer[]`. The plane pointers inside those copied descriptors are
the IOVAs that lead to the imported HardwareBuffer windows used by the probe.

This explains the current writeback boundary: when the two-native-buffer probe
points native buffer `0` at the APUNN code/input window, the visible
`0x2713 -> 0x271b` delta follows that buffer's plane IOVA. When the
output-first variant points descriptor `0` at the output window instead, the
visible delta moves to `output[0]` (`0xffffffff -> 0xfffffffd`). That is
stronger than ioctl or scheduler reachability, but it is still not proof that
the APUNN XRP output section has been consumed through the normal wrapper
contract. The missing piece is the firmware-side meaning of the copied buffer
descriptors plus the completion/output contract that turns a D2D_EXT command
into a finished request.

## APUNN/XRP settings buffer

`libneuron_platform.vpu.so::XrpCommandInfo` constructs the settings payload.
The caller-visible flow is:

1. `XrpCommandInfo::Initialize()` clears the main XRP command buffer, writes
   command flags, and writes a 16-byte constant at `+0x40`.
2. `InitCodeSection()`, `InitOutputSection()`, and `InitDataSection()` write
   section sizes and low 32-bit IOVA values into the main command buffer.
3. `PrepareDataSection()` builds 12-byte data descriptors.
4. `XrpIntrinsicExecutor::CreateVpuRequest()` passes the main XRP command
   buffer size and VA to `XrpVpuStream::CreateVpuRequest()`.
5. `XrpVpuStream::CreateVpuRequest()` calls `vpuRequest_setProperty()` with
   the prepared property object, causing `libvpu.so` to allocate/copy/sync the
   settings buffer and write `request+0x38/+0x40`.

Main XRP command buffer fields recovered from userland wrappers:

| Offset | Size | Meaning |
|---:|---:|---|
| `+0x00` | 4 | Command flags; initialized to `4` |
| `+0x04` | 4 | Code section size |
| `+0x08` | 4 | Output section size |
| `+0x0c` | 4 | Data descriptor section size |
| `+0x10` | 4 | Low 32 bits of code section IOVA |
| `+0x20` | 4 | Low 32 bits of output section IOVA |
| `+0x30` | 4 | Low 32 bits of data descriptor section IOVA |
| `+0x40` | 16 | Constant `de 63 db be 4a 99 48 89 90 83 f0 7b f8 61 09 7a` |
| `+0x50` | 8 | Multicore parameters written by `SetMulticoreParams()` |

The output section has a small header when `InitOutputSection()` takes the
normal path:

| Output offset | Value / meaning |
|---:|---|
| `+0x00` | `0xffffffff` |
| `+0x04` | `0x40` |
| `+0x08` | `4` |
| `+0x0c` | Output buffer size |
| `+0x10` | Low bit of the wrapper flag argument |

The data descriptor section is an array of 12-byte entries:

| Entry offset | Meaning |
|---:|---|
| `+0x00` | Kind/type, observed as `3` |
| `+0x04` | Buffer size |
| `+0x08` | Low 32 bits of buffer IOVA |

## Current probe shape

`ApusysIoctlProbe --run-cmd-vpu-xrp-iova` uses one imported HardwareBuffer as
the firmware-facing memory and partitions it like this:

| Imported buffer offset | Use |
|---:|---|
| `0x000` | Main XRP settings command buffer (`setting_iova`) |
| `0x100` | Code section placeholder, size currently `0` |
| `0x200` | Output section, size `0x80` |
| `0x300` | One 12-byte data descriptor |
| `0x400` | Data payload, size `0x80` |
| `0x600` | Split-test VPU plane0 MVA target, size `0x80` |

The mode prints the same windows before and after dispatch:

- `xrp_before_settings` / `xrp_after_settings`
- `xrp_before_output` / `xrp_after_output`
- `xrp_before_data_desc` / `xrp_after_data_desc`
- `xrp_before_data_payload` / `xrp_after_data_payload`
- `xrp_before_plane_payload` / `xrp_after_plane_payload`

That lets the next run classify the previous `0xb` signal:

- settings window changed: firmware or wrapper is consuming `setting_iova`
  directly;
- output window changed: APUNN recognized the XRP output section;
- data descriptor changed: firmware rewrites the descriptor list;
- data payload changed: firmware followed the APUNN/XRP data descriptor;
- plane payload changed: the visible writeback follows the native VPU
  plane0 MVA descriptor;
- command HardwareBuffer changed while imported buffer does not: the signal is
  likely command-buffer copyback through `mdw_cmd_sc_clr_hnd`.

## Runtime result

The 2026-06-14 `--run-cmd-vpu-xrp-iova-control` run validates the probe shape:
without final `run_cmd_async`, all four imported-buffer windows remain
unchanged across the 3-second wait.

The 2026-06-14 `--run-cmd-vpu-xrp-iova` dispatch run returns success from
`run_cmd_async`, reaches VPU boot/map-side kernel logs, and shows this
before/after delta when the XRP data payload and native VPU plane0 MVA point to
the same imported-buffer offset:

| Window | Before | After | Interpretation |
|---|---:|---:|---|
| `settings+0x00` | `0x00000004` | `0x00000004` | Main XRP settings header unchanged |
| `output+0x00` | `0xffffffff` | `0xffffffff` | Output section header unchanged |
| `data_desc+0x00` | `0x00000003` | `0x00000003` | Data descriptor unchanged |
| `data_payload/plane0+0x00` | `0x41505530` | `0x41505531` | First shared target word incremented by dispatch |
| command request head/tail | unchanged | unchanged | Not explained by visible command-buffer copyback |

The follow-up `--run-cmd-vpu-xrp-split-iova` run separates the APUNN/XRP data
descriptor target from the native VPU plane0 MVA:

| Target | Before | After | Interpretation |
|---|---:|---:|---|
| `data_payload+0x00` at `0x400` | `0x41505530` | `0x41505530` | APUNN/XRP data descriptor target unchanged |
| `plane_payload+0x00` at `0x600` | `0x504c4e30` | `0x504c4e31` | Visible `+1` follows native VPU plane0 MVA |
| command request head/tail | unchanged | unchanged | Not explained by visible command-buffer copyback |

The exact result files are:

- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_iova.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_iova_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_iova_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_iova_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_split_iova.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_split_iova_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_split_iova_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_split_iova_control_kernel.txt`

This moves the lower-bound APUNN interpretation from "the request dispatches"
to "the recovered settings header is accepted by the normal VPU path, but the
visible `+1` writeback follows the native VPU plane0 MVA rather than the
APUNN/XRP data descriptor." With the current zero-length code section, there is
still no recovered APUNN operation encoding and no semantic label for the `+1`
plane-MVA writeback. The earlier minimal IOVA mode wrote `0xb` at
imported-buffer offset `0`; that shape pointed settings and plane MVA at the
same malformed buffer base, so the split XRP-shaped result supersedes it for
section-routing attribution.

The follow-up `--run-cmd-vpu-xrp-ann-version-iova` run uses the same
split-target VPU request, but sets a nonzero target-side code section:

| Field | Value |
|---|---:|
| `code_size` | `0x1c8` |
| first opcode | `10003` / `0x2713` (`XTENSA_ANN_VERSION` in the recovered helper table) |
| first operation stride | `0x1c8` |
| input count | `0` |
| output count | `1` |
| output operand id | `0` |

Runtime accepts this request at the APUSYS/VPU level:

| Target | Before | After | Interpretation |
|---|---:|---:|---|
| `code+0x00` at `0x100` | `0x00002713` | `0x00002713` | Code section unchanged |
| `output+0x00` at `0x300` | `0xffffffff` | `0xffffffff` | Output section unchanged |
| `data_desc+0x00` at `0x400` | `0x00000003` | `0x00000003` | Data descriptor unchanged |
| `data_payload+0x00` at `0x500` | `0x41505530` | `0x41505530` | APUNN/XRP data descriptor target unchanged |
| `plane_payload+0x00` at `0x700` | `0x504c4e30` | `0x504c4e31` | Visible `+1` still follows native VPU plane0 MVA |
| command request head/tail | unchanged | unchanged | Not explained by visible command-buffer copyback |

The matching no-dispatch control leaves every window unchanged. Kernel logs for
the dispatch run show VPU map/boot activity (`vpu_map_sg_to_iova` and
`vpu_dev_boot_sequence`) without an APUNN-specific error in the filtered output.
This proves the target tolerates a one-entry `0x1c8` code section for this
request shape, but it does not prove that APUNN executed the operation or
consumed the APUNN data descriptor.

Additional result files:

- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_ann_version_iova.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_ann_version_iova_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_ann_version_iova_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_ann_version_iova_control_kernel.txt`

## Code-section clues

`/tmp/mtk-apu-artifacts/libneuron_platform.so` contains host-side XRP debug
helpers that describe the Xtensa operation table used by APUNN-style command
buffers. `XrpCommandInfo::GetNumXtensaOPs()` at `0x12ac4` reads the first
entry's stride from `code_base+0x04`; `XrpDebugger::PrintXtensaOperations()` at
`0x13664` walks each operation and prints operand ids from the list area:

| Entry offset | Size | Meaning |
|---:|---:|---|
| `+0x00` | 2 | Operation id / opcode |
| `+0x04` | 4 | Per-operation stride; `GetNumXtensaOPs()` divides code size by this value from the first entry |
| `+0x08` | 4 | Operand-list offset relative to `entry+0x48` |
| `+0x0c` | 4 | Input operand count |
| `+0x10` | 4 | Output operand count |
| `+0x48 + operand_off` | 2 each | Input operand ids followed by output operand ids |

The target-side `/tmp/mtk-apu-artifacts/device/libneuron_platform.vpu.so`
wrapper does not use this host/debug stride rule in
`XrpCommandInfo::GetNumXtensaOPs()` at `0xcd18`. It derives the number of
operations from the code-section size divided by a fixed `0x1c8` entry size.
That makes `0x1c8` the current target entry size for runtime probes; the
host/debug table above is useful for field names, but not sufficient as the full
firmware ABI.

The target wrapper also keeps the code bytes opaque. The relevant handoff is:

| Function | Address | Static behavior |
|---|---:|---|
| `XrpIntrinsicWrapper::FinalizeCommand()` | `0x1213c` | Copies caller `cXrpBufferInfo` records of size `0x30` into a temporary vector after validating the buffer index at `+0x08` |
| `XrpIntrinsicExecutor::PrepareCodeSection()` | `0xf7f0` | Resolves the code buffer by the `cXrpBufferInfo+0x08` index and passes the resulting `XrpBufferDesc` to `InitCodeSection()` |
| `XrpCommandInfo::InitCodeSection()` | `0xc728` | Reads `XrpBufferDesc+0x10` as code size and `XrpBufferDesc+0x28` as code IOVA, then writes those values to settings `+0x04` and `+0x10` |
| `XrpPatternDump::DumpXtensaOperations()` | `0x157b4` | Writes the raw code-section bytes to `/data/local/tmp/xrp_xtensa_operation.bin`; it does not parse operation fields |

That puts the next unresolved parser boundary on the VPU/APUNN side: the
normal VPU request and `libneuron_platform.vpu.so` wrapper route a raw
code-section IOVA/size pair, while the `0x1c8` entry fields after the basic
debug-visible header are consumed outside these userland helpers.

## Output header and writeback clues

`/tmp/mtk-apu-artifacts/libneuron_platform.so` and
`libneuron_platform.vpu.so` both initialize the XRP output section header in
the output buffer itself:

| Field | Static writer | Meaning |
|---:|---|---|
| `output+0x00` | constant qword write | first u32 starts at `0xffffffff`, second u32 at `0x40` |
| `output+0x08` | constant `4` | header/result word size |
| `output+0x0c` | output section size | copied from the output buffer descriptor size |
| `output+0x10` | bool argument | output sync/header flag byte |

`XrpCommandInfo::PrepareOutputHeader(bool)` at `0x129bc` writes these fields
from the host-side command object. The target-side
`XrpCommandInfo::InitOutputSection()` at `0xc848` writes the same header unless
its skip-header argument is set, then writes the settings output size/IOVA at
settings `+0x08/+0x20`. The large wrapper's `WritebackCommand()` at `0x22660`
does not treat this output header as a completion predicate. It first requires
the command settings flags to satisfy `(settings[0] & 0x0a) == 0x02`; only after
that predicate passes does it copy output bytes and record the first output word
as command status.

The `output_ready` runtime case sets only `output+0x10` to `1`, matching the
static `PrepareOutputHeader(true)` form, while keeping code-first descriptor
order, libvpu metadata, and wrapper send-state flags. Dispatch still returns
`0`, settings remain `0x5`, output word `0` remains `0xffffffff`, and code word
`0` changes `0x2713 -> 0x271b`. The output header sync/flag byte is therefore
not the missing APUNN completion condition.

The same helper separates operation ids into several namespaces:

| Opcode range / rule | Name source | Observed names |
|---|---|---|
| `0..992`, indexed by `opcode >> 4` | builtin-like table | `CONV2D`, `RELU`, `RESHAPE`, `CAST`, `GET_ALGO_INFO`, `LOCAL_MEM_INFO`, `XTENSA_ANN_VERSION`, `GET_DETAILED_OP_INFO`, `unknown`, `apu_lib_apunn`, `apu_lib_custom`, `apunn_dynamic`, `custom_dynamic` |
| `10001..10009`, indexed by `opcode - 10001` | internal table | `GET_ALGO_INFO`, `LOCAL_MEM_INFO`, `XTENSA_ANN_VERSION`, `GET_DETAILED_OP_INFO`, `unknown`, `apu_lib_apunn`, `apu_lib_custom`, `apunn_dynamic`, `custom_dynamic` |
| `15001` | special case | `custom op` |
| `15002` | special case | `builtin cv op` |

No confirmed no-op appears in the recovered name tables. The `10003`
`XTENSA_ANN_VERSION` probe above shows that a minimal one-entry target code
section can be submitted without a user-visible crash, but the unchanged
output/data windows mean the expected output operand and remaining `0x1c8` entry
fields are still unmapped.

The fixed matrix mode (`--run-cmd-vpu-xrp-op-matrix-iova`) now has one dispatch
run and one no-dispatch control. It keeps the same split-target layout and
varies only the internal query/status opcode and simple output operand shape:

| Case label | Opcode | Name | Inputs | Outputs | Operand ids | Dispatch-visible delta |
|---|---:|---|---:|---:|---|---|
| `get_algo_info_out0` | `10001` | `GET_ALGO_INFO` | `0` | `1` | `[0]` | only native `plane_payload[0]`: `0x504c4e30 -> 0x504c4e31` |
| `local_mem_info_out0` | `10002` | `LOCAL_MEM_INFO` | `0` | `1` | `[0]` | only native `plane_payload[0]`: `0x504c4e30 -> 0x504c4e31` |
| `ann_version_out0` | `10003` | `XTENSA_ANN_VERSION` | `0` | `1` | `[0]` | only native `plane_payload[0]`: `0x504c4e30 -> 0x504c4e31` |
| `detailed_op_info_out0` | `10004` | `GET_DETAILED_OP_INFO` | `0` | `1` | `[0]` | only native `plane_payload[0]`: `0x504c4e30 -> 0x504c4e31` |
| `ann_version_no_output` | `10003` | `XTENSA_ANN_VERSION` | `0` | `0` | `[]` | only native `plane_payload[0]`: `0x504c4e30 -> 0x504c4e31` |
| `ann_version_out1` | `10003` | `XTENSA_ANN_VERSION` | `0` | `1` | `[1]` | only native `plane_payload[0]`: `0x504c4e30 -> 0x504c4e31` |

The no-dispatch control leaves every window unchanged. In the dispatch run, all
six cases return `run_async_vpu_iova ret=0`, and all APUNN-facing windows stay
unchanged: code, output, data descriptor, APUNN data payload, and command
request head/tail. The batch kernel log records VPU map/boot activity,
`mdw_sched_trace ... ret(-110)`, `request (D2D_EXT) timeout, priority: 0,
algo: apu_lib_apunn`, and APUSYS devapc read-violation warnings. The current
log shape is enough to classify the data windows, but not enough to attribute
the timeout/devapc sequence to an individual matrix case.

The follow-up single-case mode (`--run-cmd-vpu-xrp-op-case-iova=<case>`) clears
kernel logs per case and waits longer before dumping buffers. It confirms the
stable per-case behavior:

| Case label | Wait | Stable result |
|---|---:|---|
| `get_algo_info_out0` | 10s | Only native plane payload changes; VPU worker times out |
| `local_mem_info_out0` | 10s | Only native plane payload changes; VPU worker times out |
| `ann_version_out0` | 10s | Only native plane payload changes; VPU worker times out |
| `detailed_op_info_out0` | 10s | Only native plane payload changes; VPU worker times out |
| `ann_version_no_output` | 20s | Only native plane payload changes; VPU worker times out |
| `ann_version_out1` | 20s | Only native plane payload changes; VPU worker times out |

The extra case `ann_version_status_bit3_out0` pre-sets the first operation word
to `10003 | 0x8` (`0x271b`) before dispatch. Its no-dispatch control leaves all
windows unchanged. The dispatch run still returns `run_async_vpu_iova ret=0`,
keeps settings/code/output/data-desc/data-payload unchanged, changes only native
descriptor `0`'s plane payload word `0` from `0x504c4e30` to `0x504c4e31`, and
logs `request (D2D_EXT) timeout` plus a residual command at teardown. This
separates opcode parsing from descriptor writeback: in the split-target
one-buffer layout, pre-setting bit `3` does not produce completion and does not
cause additional code-window writeback.

The single-case logs do not reproduce the batch `apusys_devapc_isr`
read-violation warning. The current parser conclusion is therefore that the
debug-visible opcode/count/operand fields are sufficient for request acceptance
but not sufficient for APUNN output/data binding or successful completion.

## Standard wrapper request path

The host-side `/tmp/mtk-apu-artifacts/libneuron_platform.so` separates the
ordinary XRP command path from a standalone internal-command helper. The
ordinary `XRP_CreateCommand` / `XRP_UseInputBuffer` /
`XRP_UseOutputBuffer` / `XRP_FinalizeCommand` flow does not call
`PrepareInternalCommand()`.

The standard flow recovered from the same library is:

| Function | Address | Static behavior |
|---|---:|---|
| `xrp::XrpIntrinsic::PrepareXrpCommand(unsigned long, long)` | `0x16ac0` | Resolves the supplied command-buffer id through `GetBuffer()`, calls `HintXrpCommandInput()`, `PrepareXtensaCommandBuffer()`, computes/allocates output through `CalculateOutputSize()` and `AllocCmdOutputBuffer()`, then calls `HintXrpCommandOutput()` and `PrepareOutputBuffer()` |
| `xrp::XrpIntrinsic::FinalizeXrpCommand(unsigned long)` | `0x1789c` | For nonzero handles, calls `PrepareDataBuffer(handle)` and `FinalizeDataBuffer(handle)`, then always calls `CreateVpuRequest(handle)` |

The `PrepareXrpCommand()` calls show how the wrapper interprets request
parameters before firmware dispatch:

| Call | Firmware-facing effect |
|---|---|
| `HintXrpCommandInput(handle, size, host_va, access=3)` | Binds the selected user buffer as the Xtensa/code input for the command |
| `PrepareXtensaCommandBuffer(handle)` | Writes the settings code size/IOVA fields from that hinted buffer |
| `AllocCmdOutputBuffer(handle, CalculateOutputSize(handle))` | Allocates the command output backing buffer |
| `HintXrpCommandOutput(handle, size, host_va, access)` | Binds the output buffer to the command |
| `PrepareOutputBuffer(handle)` | Writes output size/IOVA and prepares the output header |
| `PrepareDataBuffer()` / `FinalizeDataBuffer()` | Builds and finalizes the 12-byte data descriptor section before `CreateVpuRequest()` copies the settings payload into the VPU request |

The relevant standard-wrapper field interpretation is now:

| Field / step | Interpretation |
|---|---|
| `XrpBufferDesc+0x08` | Buffer size copied into the settings section-size fields |
| `XrpBufferDesc+0x18` | Host VA used for header initialization and optional data copies |
| `XrpBufferDesc+0x20` | Access flags; copied into data descriptor entry `+0x00` |
| `XrpBufferDesc+0x24..+0x38` | Physical/import metadata populated by allocation/import; the low 32-bit IOVA fields are used for settings/data entries |
| `PrepareXtensaCommandBuffer()` | Writes settings `+0x04 = code_size`, `+0x10 = code_iova_low32` |
| `CalculateOutputSize()` | Returns `0x40` in the default wrapper mode; when the wrapper output-sizing option is set, it derives a larger output size from the code/input layout and adds the same `0x40` header base |
| `PrepareOutputBuffer()` | Writes settings `+0x08 = output_size`, `+0x20 = output_iova_low32`, then prepares the output header |
| `PrepareOutputHeader(bool)` | Writes output `+0x00/+0x04 = 0xffffffff/0x40`, `+0x08 = 4`, `+0x0c = output_size`, `+0x10 = bool flag` |
| `PrepareDataBuffer()` | Allocates a data-descriptor section sized as `data_buffer_count * 0x0c`; the zero-data-buffer path is valid and leaves no data section to consume |
| `FinalizeDataBuffer()` | Fills each 12-byte data descriptor as `{access_flags, buffer_size, iova_low32}` from registered data buffers |
| `CreateVpuRequest()` | Creates the VPU request from the prepared command/settings buffer; this is where the settings payload becomes `request+0x38/+0x40` |

This correction changes the interpretation of the two-native-buffer Java
experiments below: they are useful descriptor-following probes, but they are not
a faithful replay of the ordinary wrapper path unless an external service entry
explicitly invokes `PrepareInternalCommand()`.

The 2026-06-14 `--run-cmd-vpu-xrp-ann-version-wrapper-zero-data-iova` probe
tested the default-output/zero-data part of this contract against the direct
ioctl path:

| Settings field | Value |
|---:|---|
| `+0x04` code size | `0x1c8` |
| `+0x08` output size | `0x40` |
| `+0x0c` data descriptor size | `0` |
| `+0x10` code IOVA | `base+0x100` |
| `+0x20` output IOVA | `base+0x300` |
| `+0x30` data descriptor IOVA | `0` |

The dispatch run still returns `run_async_vpu_iova ret=0`, logs a
`request (D2D_EXT) timeout`, leaves settings/code/output/data windows unchanged,
and changes only native descriptor `0`'s plane payload word from `0x504c4e30` to
`0x504c4e31`. The no-dispatch control is unchanged. Therefore the previous
non-completion is not explained by using output size `0x80` or by always
providing one APUNN data descriptor.

## Internal command buffer shape

The host-side library also exports a separate internal-command path:

| Function | Address | Static behavior |
|---|---:|---|
| `xrp::XrpIntrinsic::PrepareInternalCommand(unsigned int, unsigned int)` | `0x1728c` | Allocates two internal command buffers using the two size arguments, hints the first as command input and the second as command output, then calls `PrepareInternalCommandBuffer()` |
| `xrp::XrpIntrinsicExecutor::PrepareInternalCommandBuffer()` | `0x1ff48` | Requires exactly two internal command records, writes first buffer size/IOVA to settings `+0x04/+0x10`, and writes second buffer size/IOVA to settings `+0x08/+0x20` |

No direct internal xref from `PrepareXrpCommand()` or `FinalizeXrpCommand()` to
`PrepareInternalCommand()` is present in this library. If this helper is used on
the target, it is reached through a separate service-side entry point or another
library, not through the standard finalize path above.

The new
`--run-cmd-vpu-xrp-internal-ann-version-iova` mode keeps the same
`XTENSA_ANN_VERSION` XRP settings/code/data descriptor, but changes the native
VPU request to:

| Native request field | Value |
|---|---|
| `request+0x35` | `2` buffers |
| buffer `0` plane MVA | XRP code/input window (`base+0x100`) |
| buffer `1` plane MVA | XRP output window (`base+0x300`) |
| settings `+0x04/+0x10` | code/input size `0x1c8`, code/input IOVA |
| settings `+0x08/+0x20` | output size `0x80`, output IOVA |

At the kernel boundary, those two native buffers are copied as two
`struct vpu_buffer` descriptors into the per-priority D2D command buffer. The
actual code/input and output windows are then reached only if firmware follows
the copied plane IOVAs.

The no-dispatch control leaves every imported-buffer and command-buffer window
unchanged. The dispatch run returns `run_async_vpu_iova ret=0`, shows VPU
map/boot activity, and does not show `D2D_EXT timeout` in the captured 20-second
window. At process teardown, the kernel logs `mdw_usr_destroy residual cmd`,
so the command is still not proven complete. The visible data delta moves with
native buffer `0`: `code+0x00` changes from `0x2713` to `0x271b`. The XRP
output header, data descriptor, data payload, and unused plane-payload sentinel
remain unchanged.

This is the strongest runtime boundary so far:

- the two-buffer native VPU shape changes APUNN lifecycle behavior from the
  previous per-case worker timeout to a residual-command teardown without a
  captured `D2D_EXT timeout`;
- firmware-visible writeback follows native buffer `0` when the copied
  `struct vpu_buffer[0]` descriptor points at the code/input IOVA;
- settings `+0x08/+0x20` plus native buffer `1` are still not enough to observe
  APUNN output-section writeback.

Follow-up static analysis narrowed the normal `struct vpu_buffer` metadata:
`libvpu.so::VpuRequestImp::addBuffer()` writes `port_id`, DATA format,
`plane_count`, width, height, stride, length, and the final plane MVA into the
same raw kernel descriptor layout. The host wrapper
`xrp::XrpVpuStream::DefaultCreateVpuRequest()` builds a libvpu-style descriptor
with `port_id=1`, `format=0`, `plane_count=1`, `width=size`, `height=1`,
`stride=size`, and `length=size`, and calls `addBuffer()` five times for its
default request shape.

The 2026-06-14 `libvpu_metadata` and `libvpu_metadata_alias5` probe variants
tested both deltas against the internal `XTENSA_ANN_VERSION` request:

| Variant | Descriptor change | Runtime result |
|---|---|---|
| `libvpu_metadata` | Keeps `buffer_count=2`, but uses libvpu-style `port_id=1`, `height=1`, `stride=size`, `length=size` descriptors for code/input and output | Control unchanged; dispatch returns `0`, code/input first word changes `0x2713 -> 0x271b`, output/data windows unchanged, teardown logs residual command |
| `libvpu_metadata_alias5` | Uses libvpu-style metadata and `buffer_count=5` with code/output aliases | Same result: code/input first word changes `0x2713 -> 0x271b`, output/data windows unchanged, teardown logs residual command |

The next unresolved field is therefore not ordinary VPU descriptor metadata,
descriptor count, or basic `0x1c8` opcode/count routing. It is the APUNN
firmware-side completion/output contract: the standard wrapper's
code/output/data buffer contents, command flags, settings fields, or
output-header semantics that make APUNN signal done and write to the settings
output section. The internal-command contents remain relevant only if a
service-side entry point is found that reaches `PrepareInternalCommand()`.

## Command flags and completion state

Target-side `libneuron_platform.vpu.so` adds one wrapper state transition that
the direct ioctl probe originally skipped. `XrpIntrinsicExecutor::SendRequest()`
at `0x10044` finds the command info, clears bit `0x2`, sets bit `0x1`, and then
calls `XrpVpuStream::RunRequest()`. A normally sent command therefore enters
the kernel/VPU path with settings `+0x00 == 0x5`, not the post-initialize value
`0x4`.

`XrpIntrinsicExecutor::WaitRequest()` at `0x101e0` calls
`XrpVpuStream::WaitRequest()` and then reads the same command flags. Its success
predicate is:

```text
(settings[0] & 0x0a) == 0x02
```

So the APUNN completion contract must eventually set bit `0x2` and leave bit
`0x8` clear. The host-side wrapper has the same model in a more explicit
writeback path: `libneuron_platform.so::WritebackCommand()` at `0x22660`
requires the same `(flags & 0x0a) == 0x02` predicate before it copies output and
records the first output word as command status.

The `--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags`
variant tests this missing wrapper state against the current strongest request
shape: two native buffers, libvpu-style descriptors, opcode `10003`, and
settings `+0x00 == 0x5`.

| Variant | Runtime result |
|---|---|
| `send_flags` no-dispatch control | All settings/code/output/data windows stay unchanged |
| `send_flags` dispatch | `run_async_vpu_iova ret=0`; settings remain `0x5`, not completion state; code/input first word changes `0x2713 -> 0x271b`; output/data windows remain unchanged |

This rules out the wrapper send-state flag as the only missing condition. It
also gives a concrete firmware-side success oracle for future runs: a completed
APUNN command should visibly change settings `+0x00` so that
`(flags & 0x0a) == 0x02`, and the output section should no longer retain the
initial `0xffffffff, 0x40, 4, size` header.

The direct ioctl wait experiment adds the midware-side completion view. In
`--run-cmd-vpu-xrp-ann-version-wrapper-zero-data-wait-iova`, async submit writes
command id `1` to `runCmd+0x00`; passing the same 0x18-byte argument to
`0x40184108` returns `-EIO`. IDA maps this branch to `mdw_wait_cmd`: command
object `+0x1a0` is a failed subcommand pointer, and a nonzero value logs the
command/subcommand failure and returns `-EIO`. Wait does not copy APUNN
completion status into the user argument. It consumes the command object, which
is why this run avoids the later `mdw_usr_destroy residual cmd` warning. The
APUNN settings/output windows remain unchanged, so this is a midware failure
status, not the wrapper completion state.

The follow-up
`--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-output-first`
variant keeps the same settings buffer (`code_iova = base+0x100`,
`output_iova = base+0x300`) and wrapper send-state flags, but swaps the native
VPU descriptor order: descriptor `0` points to the output window and descriptor
`1` points to the code/input window.

| Variant | Runtime result |
|---|---|
| `output_first` no-dispatch control | Settings/code/output/data windows and command buffer stay unchanged |
| `output_first` dispatch | `run_async_vpu_iova ret=0`; settings remain `0x5`; code/input first word stays `0x2713`; output first word changes `0xffffffff -> 0xfffffffd`; data windows remain unchanged; teardown logs residual command cleanup |

This pins the visible imported-buffer writeback to native descriptor `0`.
Changing descriptor `0` from code/input to output moves the first-word delta
with it, even though the settings buffer still advertises the same code and
output IOVAs. The observed first-word deltas are therefore descriptor-index
effects on the current incomplete command path, not proof that APUNN has
completed its settings/output contract.

## Wrapper-generated request inspection

`poc/xrp_wrapper_inspect.cpp` is a native wrapper inspector for comparing the
Java-built request with a request produced by `libneuron_platform.vpu.so`
itself. It loads the target wrapper, creates an XRP command, allocates one
code/input buffer and one output buffer, finalizes the command, and dumps the
`cXrpBufferInfo`, `cVpuRequestInfo`, and first VPU request bytes. The tool does
not call `XRP_RunCommand()`.

Static analysis of the device library
`/system/lib64/libneuron_platform.vpu.so`
(`sha256=3e2f37bab5a6dc30973f42ca72f73b3d02798bff61f9763521b34d25e6aae0f3`)
adds these wrapper constraints:

| Wrapper item | Evidence |
|---|---|
| `cXrpOptions` | `XrpIntrinsicWrapper::XrpIntrinsicWrapper()` expects option size `0x18`, reads byte `+0x05`, and copies the qword at `+0x10` into internal options |
| `XrpIntrinsicExecutor::InitDriver()` | Creates the VPU stream instance first; failure returns a nonzero XRP status before memory-manager setup |
| `XrpIntrinsicExecutor::CreateXrpCommand()` | Allocates a `0x68` `xrp_dsp_cmd`/DSP command buffer through the memory manager before command initialization |

The shell-domain run is a negative control, not a valid APUNN wrapper ABI
dump:

```text
context=u:r:shell:s0
dlopen path=/system/lib64/libneuron_platform.vpu.so
XRP_Create status=4
XRP_Create did not initialize the wrapper; skip follow-up calls.
```

The earlier crash after continuing past status `4` is explained by the same
static path: `XRP_CreateCommand()` reaches
`XrpMemoryManager::AllocateBuffer(0x68)`, but `InitDriver()` did not create the
memory manager, so the call locks a null-object mutex at `0x10`. The corrected
inspector stops after nonzero `XRP_Create`.

The `system_app` `app_process64` route now works as a second negative control.
`poc/XrpWrapperInspect.java` loads the already-installed
`/system/lib64/libneuron_platform.vpu.so`, resolves exported function addresses
from ELF metadata plus `/proc/self/maps`, and calls them through the
`DrmTrigger` ART native-call stub. The stub self-test calls libc `getpid()` and
returns the expected process id, proving that the function-call primitive is not
the blocking point:

```text
uid=1000(system) context=u:r:system_app:s0
native getpid()=610
XRP_Create status=4 device=0xb400006db9a2af10
XRP_Create did not initialize; stop.
```

This keeps the wrapper-generated request comparison open, but narrows the
missing condition. Both shell and direct `system_app` wrapper initialization
return status `4`; follow-up calls are intentionally skipped because the memory
manager is not initialized on that path. A useful positive dump still needs a
context or option set where `XRP_Create()` returns `0`, or a different hook point
inside an already successful Neuron/VPU client.

The APUWARE HIDL wrapper gives a separate positive initialization path through
`/system/system_ext/lib64/libapuwarexrp_v2.mtk.so`. It proxies to
`vendor.mediatek.hardware.apuware.xrp@2.0` in
`android.hardware.neuralnetworks@1.3-service-mtk-neuron`, and from a shell
process it reaches the service successfully:

```text
dlopen path=/system/system_ext/lib64/libapuwarexrp_v2.mtk.so
XRP_Create status=0 device=0x1
XRP_CreateCommand status=0 handle=1
XRP_AllocateBuffer(code) status=0
XRP_AllocateBuffer(output) status=0
XRP_UseInputBuffer status=0
XRP_UseOutputBuffer status=0
XRP_FinalizeCommand status=2
```

This path adds two concrete ABI facts. First, the APUWARE client wrapper reads
the caller-provided `cXrpBufferInfo+0x18` fd before `XRP_AllocateBuffer()`.
Initializing that field to `-1` is required for the service-allocation path;
leaving the struct zeroed makes the wrapper duplicate fd `0` and send it over
HIDL, which aborts after a Binder `FAILED_TRANSACTION`. Second, the APUWARE
client converts the public 0x30-byte `cXrpBufferInfo` into a 0x38-byte HIDL
`XRP_cXrpBufferInfo_HD` record before `XRP_FinalizeCommand()`.

The service-allocated buffer info observed from the positive path is:

| `cXrpBufferInfo` offset | Code buffer | Output buffer | Meaning |
|---:|---:|---:|---|
| `+0x00` | `2` | `2` | Buffer kind/type returned by APUWARE |
| `+0x08` | `1` | `2` | Service buffer id |
| `+0x10` | `0x1c8` | `0x80` | Requested size |
| `+0x14` | `0x1c8` | `0x80` | Mapped size |
| `+0x18` | `0x0b` | `0x0c` | Shared fd |
| `+0x20` | `0xfd643000` | `0xfd641000` | Low 32-bit IOVA/PA-style value |
| `+0x28` | user VA | user VA | Host mapping used by the client |

The recorded positive APUWARE route stopped at
`XRP_FinalizeCommand status=2`. It occurs after code/output allocation and
`UseInputBuffer`/`UseOutputBuffer` both return success, so the remaining
condition is in the finalize output-vector semantics or service-side command
validation, not in service lookup or buffer allocation. A forced
`XRP_GetPreparedRequests()` after the failed finalize blocks in the HIDL path;
the helper now skips request dumping unless finalize returns `0`.

Static inspection of the local `/system/lib64/libneuron_platform.vpu.so`
wrapper changes the interpretation of that `status=2`. Its
`XrpIntrinsicWrapper::FinalizeCommand()` copies each caller
`cXrpBufferInfo` into a temporary data-buffer vector only after checking the
qword at `cXrpBufferInfo+0x08` against `output_count`; if the value is
greater than or equal to `output_count`, the wrapper returns status `2`.
The saved APUWARE positive run passed the raw output buffer returned by
`XRP_AllocateBuffer()`, whose `+0x08` field was `2`, together with
`output_count=1`. That is a direct match for an output-vector index mismatch:
the finalize vector wants output slot indices, while the allocation result
contains a service buffer id. This means the old `status=2` result is weak
evidence for firmware rejection and strong evidence that the wrapper finalize
ABI was still wrong.

`poc/xrp_wrapper_inspect.cpp` now has `--finalize-slot-index`, which sends a
copy of the output info with `+0x08` rewritten to the vector slot number before
calling `XRP_FinalizeCommand()`, and `--dlopen-lazy` for isolating loader
behavior. Current reruns cannot yet validate the slot-index hypothesis because
this device state stops inside `dlopen(libapuwarexrp_v2.mtk.so)` before the
helper prints the loaded path, even with `RTLD_LAZY`.

Wrapper-inspection result files:

- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_shell.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_app_process.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_neuron_shell_latest.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_neuron_skip_finalize_after_index_patch.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_apuware_shell.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_apuware_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_apuware_buffer_id_retry.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_apuware_finalize_slot_index.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_apuware_finalize_slot_index_lazy.txt`

## Firmware-visible request model

The readable `apu_lib_apunn` artifact is still not available from this device
state. The firmware-side model can still be pinned down to the last kernel/user
boundary: the wrapper builds the XRP settings and native VPU request, `mdw`
copies the request into kernel memory, and the VPU driver translates it into a
D2D/D2D_EXT register tuple plus a copied buffer-descriptor array.

The normal-VPU provider gate is now IDA-confirmed in `vmlinux.bin`:

| Function / address | Boundary |
|---|---|
| `vpu_send_cmd_rt_handler+0x4dc` / `0xffffffc0087a08f8` | Requires provider argument `+0x0c == 0xb70` |
| `vpu_send_cmd_rt_handler+0x4e8` / `0xffffffc0087a0904` | Requires `request+0x28 < 0x10` |
| `vpu_send_cmd_rt_handler+0x4f4` / `0xffffffc0087a0910` | Requires `request+0x35 < 0x21` |
| `vpu_send_cmd_rt_handler+0x500` / `0xffffffc0087a091c` | Clears `request+0xb68`, stores core id at `request+0xb5c` |
| `vpu_send_cmd_rt_handler+0x510` / `0xffffffc0087a092c` | Dispatches `vpu_execute()` unless original `request+0x28` bit `2` selects `vpu_execute_with_slot()` |

`vpu_execute()` explains why the runtime logs show `D2D_EXT` even though the
probe submits `request+0x28 == 0`. It first looks up `request+0x04` in the
Normal algorithm set. If that lookup returns `-ENOENT` while bit `2` is not
already set, the driver ORs `request+0x28` with `0x4` and retries the Preload
set. The observed `apu_lib_apunn` requests therefore reach firmware as Preload
`D2D_EXT` work after a Normal-set miss, not because the Java request directly
sets the D2D_EXT flag.

`sub_FFFFFFC0087A5B74()` is the kernel-to-firmware handoff. Its first step is
to copy `request+0x50` into the per-priority VPU command buffer:

| IDA address | Operation |
|---|---|
| `0xffffffc0087a5bd0` | Reads `request+0x35` as `buffer_count` |
| `0xffffffc0087a5bdc` | Uses `request+0x50` as the source descriptor array |
| `sub_FFFFFFC0087A20E8` / `0xffffffc0087a20e8` | Rejects copies larger than `0x2000`, then copies `buffer_count * 0x40` bytes |
| `0xffffffc0087a20f8` | Destination is the per-priority D2D buffer at `core + priority * 0xb0 + 0x3f8` |
| `sub_FFFFFFC0087A20C8` / `0xffffffc0087a20c8` | Returns the copied descriptor-array IOVA from `core + priority * 0xb0 + 0x400` |

The firmware-visible MMIO writes in the same function are:

| MMIO offset from `core+0xa0` | Firmware input |
|---:|---|
| `+0x280` | `request+0x35` buffer count |
| `+0x284` | IOVA of the copied `struct vpu_buffer[]` array |
| `+0x288` | Low 32 bits of `request+0x40` settings IOVA |
| `+0x28c` | `request+0x38` settings length |
| `+0x254` | command id: `0x22` for D2D, `0x24` for D2D_EXT |

For D2D_EXT, the driver also writes preload state before the common
`INFO12..15` tuple:

| MMIO offset from `core+0xa0` | D2D_EXT input |
|---:|---|
| `+0x27c` | `request+0xb68` priority/slot value |
| `+0x290` | sum of preload object fields `+0xfb0` and `+0xfb8` |
| `+0x29c` | preload object `+0xfc0` |

The trigger sequence then clears a status bit at MMIO `+0x910`, sets bit `0` at
MMIO `+0x204`, waits for completion, maps the per-priority status word through
`sub_FFFFFFC0087A2040()`, and copies the driver-side algorithm return into
`request+0xb6c` through `sub_FFFFFFC0087A20A8()`.

This gives the current interpretation boundary:

- `apu_lib_apunn` sees a copied descriptor array, not the original userland
  `struct vpu_request`.
- The settings buffer is reached through `INFO14/INFO15`; native buffers are
  reached through the copied descriptor-array IOVA in `INFO13`.
- The split-target one-buffer opcode cases show the firmware path following
  descriptor `0` into the native plane payload and changing word `0` from
  `0x504c4e30` to `0x504c4e31`, while settings/code/output/data-desc/data
  windows remain unchanged.
- The two-native-buffer result where code/input word `0` changes
  `0x2713 -> 0x271b` is the same descriptor-following behavior with descriptor
  `0` bound to the code/input window. The low halfword started as opcode
  `10003` (`XTENSA_ANN_VERSION`), so that layout made the descriptor writeback
  look like bit `3` being set in the first operation word.
- The `ann_version_status_bit3_out0` result pre-seeds that bit (`0x271b`) and
  still only changes native descriptor `0`'s plane payload in the split-target
  layout. Bit `3` alone is therefore not the missing completion signal.
- The `output_first` result swaps only the two copied native descriptor slots.
  The visible first-word delta moves from code/input to output
  (`0xffffffff -> 0xfffffffd`) while settings still point to the same output
  IOVA. This confirms descriptor `0`, not the settings output pointer, is the
  current writeback attribution target.
- The `settings68` result keeps the code-first two-buffer shape, wrapper
  send-state flags, and libvpu descriptor metadata, but changes only
  `request+0x38` from `0x100` to the wrapper-allocated DSP command buffer size
  `0x68`. Dispatch still returns `0`, kernel logs VPU boot/map activity and
  residual command cleanup, settings remain `0x5`, output remains
  `0xffffffff`, and code word `0` changes `0x2713 -> 0x271b`. The request
  settings length is therefore not the missing APUNN completion condition.
- The `output_ready` result keeps the same code-first two-buffer shape and sets
  the wrapper-controlled output header byte at `output+0x10` from `0` to `1`.
  Dispatch still returns `0`, settings remain `0x5`, output remains
  `0xffffffff`, and code word `0` changes `0x2713 -> 0x271b`. That output
  header flag is also not the missing APUNN completion condition.
- The missing piece is now narrower: APUNN parses enough of the descriptor and
  first operation entry to modify it, but the command still lacks the
  firmware-side condition that marks settings `(flags & 0x0a) == 0x02` and
  writes the output section.

`ApusysIoctlProbe.java` includes the single-case label
`ann_version_status_bit3_out0` for this pre-seeded operation-word check.

Additional matrix result files:

- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_matrix_iova.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_matrix_iova_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_matrix_iova_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_matrix_iova_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_case_ann_version_status_bit3_out0.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_case_ann_version_status_bit3_out0_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_case_ann_version_status_bit3_out0_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_op_case_ann_version_status_bit3_out0_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_output_first.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_output_first_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_output_first_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_output_first_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc5.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc5_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc5_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc5_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc_send_flags.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc_send_flags_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc_send_flags_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc_send_flags_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_settings68.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_settings68_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_settings68_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_settings68_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_output_ready.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_output_ready_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_output_ready_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_output_ready_control_kernel.txt`

## Evidence map

Userland wrapper evidence:

| Binary | Function | Address | Evidence |
|---|---|---:|---|
| `libvpu.so` | `VpuRequestImp::prepareSettBuf` | `0x73fc` | Allocates settings memory and writes request `+0x38/+0x40` |
| `libvpu.so` | `VpuRequestImp::setProperty` | `0x7b6c` | Copies caller property bytes into settings memory and cache-syncs |
| `libvpu.so` | `VpuRequestImp::addBuffer` | `0x7624` | Populates `request+0x35` and the raw `struct vpu_buffer` descriptor fields |
| `libvpu.so` | `VpuRequestImp::mmapMVA` | `0x7858` | Fills per-plane MVA/IOVA entries |
| `libvpu.so` | `VpuStreamImp::runReq` | `0x91fc` | Copies native `0xb70` request into APUSYS command memory |
| `libneuron_platform.vpu.so` | `XrpCommandInfo::Initialize` | `0xc5e4` | Initializes main XRP command buffer and magic |
| `libneuron_platform.vpu.so` | `XrpCommandInfo::GetNumXtensaOPs` | `0xcd18` | Counts target operations using fixed `0x1c8` code entries |
| `libneuron_platform.vpu.so` | `XrpCommandInfo::InitCodeSection` | `0xc728` | Writes code size/IOVA fields |
| `libneuron_platform.vpu.so` | `XrpCommandInfo::InitOutputSection` | `0xc848` | Initializes output header and writes output size/IOVA |
| `libneuron_platform.vpu.so` | `XrpCommandInfo::InitDataSection` | `0xcaa0` | Writes data descriptor size/IOVA |
| `libneuron_platform.vpu.so` | `XrpIntrinsicWrapper::XrpIntrinsicWrapper` | `0x1139c` | Uses `cXrpOptions` size `0x18`, option byte `+0x05`, and qword `+0x10` |
| `libneuron_platform.vpu.so` | `XrpIntrinsicExecutor::InitDriver` | `0xeb04` | Creates the VPU stream instance and memory manager; shell-domain failure returns status `4` before request construction is usable |
| `libneuron_platform.vpu.so` | `XrpIntrinsicExecutor::CreateXrpCommand` | `0xf1d4` | Allocates the `0x68` DSP command buffer through the memory manager before XRP command initialization |
| `libneuron_platform.vpu.so` | `XrpIntrinsicExecutor::PrepareDataSection` | `0xfacc` | Builds 12-byte data entries |
| `libneuron_platform.vpu.so` | `XrpIntrinsicExecutor::SendRequest` | `0x10044` | Transitions command flags from initialize state toward send state by clearing `0x2` and setting `0x1` |
| `libneuron_platform.vpu.so` | `XrpIntrinsicExecutor::WaitRequest` | `0x101e0` | Requires `(settings[0] & 0x0a) == 0x02` after VPU wait before treating the XRP command as completed |
| `libneuron_platform.vpu.so` | `XrpVpuStream::CreateVpuRequest` | `0x16d54` | Calls `vpuRequest_setProperty()` for the prepared settings payload |
| `libneuron_platform.so` | `XrpCommandInfo::PrepareOutputHeader` | `0x129bc` | Writes the output header qword, result size `4`, output size, and output sync/header flag byte |
| `libneuron_platform.so` | `XrpIntrinsic::PrepareXrpCommand` | `0x16ac0` | Standard path: binds input/code, prepares Xtensa command fields, allocates/binds output, and prepares output fields |
| `libneuron_platform.so` | `XrpIntrinsic::FinalizeXrpCommand` | `0x1789c` | Standard path: prepares/finalizes data descriptors, then creates the VPU request |
| `libneuron_platform.so` | `XrpIntrinsic::PrepareInternalCommand` | `0x1728c` | Separate exported path; no direct xref from standard prepare/finalize flow in this library |
| `libneuron_platform.so` | `XrpIntrinsicExecutor::PrepareInternalCommandBuffer` | `0x1ff48` | Separate internal path: binds first internal buffer to settings code fields and second internal buffer to settings output fields |
| `libneuron_platform.so` | `XrpIntrinsicExecutor::WritebackCommand` | `0x22660` | Host wrapper requires the same completion-flag predicate and records the first output word as command status |
| `libneuron_platform.so` | `XrpVpuStream::DefaultCreateVpuRequest` | `0x2ad64` | Creates libvpu-style descriptors with `port_id=1`, `height=1`, and `stride=size`; default path calls `addBuffer()` five times |

Kernel handoff evidence:

| Source | Function | Evidence |
|---|---|---|
| `drivers/misc/mediatek/apusys/vpu/4.0/vpu_ioctl.h` | `struct vpu_request` | Defines `algo`, `flags`, `buffer_count`, `sett_length`, `sett_ptr`, `buffers[]`, `status`, and `algo_ret` |
| `drivers/misc/mediatek/apusys/vpu/4.0/vpu_main.c` | `vpu_req_check()` | Requires exact VPU request size, validates flags, and bounds `buffer_count` |
| `drivers/misc/mediatek/apusys/vpu/p1/vpu_hw.c` | `vpu_execute_d2d()` | Copies `req->buffers[]` into the D2D command buffer and writes `XTENSA_INFO12..15`; D2D_EXT also writes preload entry/IRAM registers |
| `drivers/misc/mediatek/apusys/vpu/p1/vpu_hw.h` | register table | Documents `DO_D2D` as INFO12 buffer count, INFO13 buffer-array pointer, INFO14 setting pointer, and INFO15 setting size |
| IDA `vmlinux.bin` | `vpu_execute` at `0xffffffc0087a7974` | Kernel execution entry that reaches the D2D/D2D_EXT request path |
| IDA `vmlinux.bin` | `sub_FFFFFFC0087A5B74` at `0xffffffc0087a5b74` | Copies `request+0x50` descriptors, writes MMIO `+0x280/+0x284/+0x288/+0x28c`, triggers command id `0x22/0x24`, and waits for completion |

Runtime evidence so far proves `apu_lib_apunn` lookup, normal VPU request
acceptance, VPU boot/map activity, XRP-shaped settings header tolerance,
target-side nonzero code-section tolerance for six internal query/status
operation shapes, a controlled native VPU buffer writeback, a per-case timeout
for the earlier one-buffer shape, and a two-native-buffer `XTENSA_ANN_VERSION`
dispatch where copied native buffer descriptor `0` points at the code/input
window and the kernel logs residual command state instead of the earlier D2D
timeout. Libvpu-style descriptor metadata, a five-descriptor alias shape, and
the wrapper send-state command flag value `0x5` do not change that boundary.
Changing the firmware-visible settings length from `0x100` to the wrapper DSP
command buffer size `0x68` also leaves the same boundary in place. Setting the
wrapper-controlled output header flag byte at `output+0x10` to `1` does not
produce settings completion or APUNN output writeback either.
It does not yet prove APUNN data descriptor consumption, APUNN output-section
writeback, the missing completion parameter, or the full semantic meaning of
the observed native-buffer writeback. The batch-level devapc warning remains
non-attributed because isolated single-case runs did not reproduce it.
