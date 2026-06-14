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
`0x2713 -> 0x271b` delta follows that buffer's plane IOVA. That is stronger
than ioctl or scheduler reachability, but it is still not proof that the APUNN
XRP output section has been consumed. The missing piece is the firmware-side
meaning of the copied buffer descriptors plus the completion/output contract
that turns a D2D_EXT command into a finished request.

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

The single-case logs do not reproduce the batch `apusys_devapc_isr`
read-violation warning. The current parser conclusion is therefore that the
debug-visible opcode/count/operand fields are sufficient for request acceptance
but not sufficient for APUNN output/data binding or successful completion.

## Internal command buffer shape

The host-side `/tmp/mtk-apu-artifacts/libneuron_platform.so` has a separate
internal-command path that explains the timeout boundary in the one-buffer
runtime probes:

| Function | Address | Static behavior |
|---|---:|---|
| `xrp::XrpIntrinsic::PrepareInternalCommand(unsigned int, unsigned int)` | `0x1728c` | Allocates two internal command buffers, hints buffer `0` as command input, hints buffer `0` as command output, then calls `PrepareInternalCommandBuffer()` |
| `xrp::XrpIntrinsicExecutor::PrepareInternalCommandBuffer()` | `0x1ff48` | Requires exactly two internal command records, writes first buffer size/IOVA to settings `+0x04/+0x10`, and writes second buffer size/IOVA to settings `+0x08/+0x20` |

That means APUNN internal query/status commands are not represented well by the
earlier one-native-buffer request. The real wrapper binds an input/code buffer
and an output buffer at the native VPU layer before the firmware sees the
settings buffer.

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
firmware-side completion/output contract: the command flags, settings fields,
internal input contents, or output-header semantics that make APUNN signal done
and write to the settings output section.

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
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc5.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc5_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc5_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_iova_libvpu_desc5_control_kernel.txt`

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
| `libneuron_platform.vpu.so` | `XrpIntrinsicExecutor::PrepareDataSection` | `0xfacc` | Builds 12-byte data entries |
| `libneuron_platform.vpu.so` | `XrpVpuStream::CreateVpuRequest` | `0x16d54` | Calls `vpuRequest_setProperty()` for the prepared settings payload |
| `libneuron_platform.so` | `XrpIntrinsic::PrepareInternalCommand` | `0x1728c` | Allocates internal input and output buffers before APUNN dispatch |
| `libneuron_platform.so` | `XrpIntrinsicExecutor::PrepareInternalCommandBuffer` | `0x1ff48` | Binds first internal buffer to settings code fields and second internal buffer to settings output fields |
| `libneuron_platform.so` | `XrpVpuStream::DefaultCreateVpuRequest` | `0x2ad64` | Creates libvpu-style descriptors with `port_id=1`, `height=1`, and `stride=size`; default path calls `addBuffer()` five times |

Kernel handoff evidence:

| Source | Function | Evidence |
|---|---|---|
| `drivers/misc/mediatek/apusys/vpu/4.0/vpu_ioctl.h` | `struct vpu_request` | Defines `algo`, `flags`, `buffer_count`, `sett_length`, `sett_ptr`, `buffers[]`, `status`, and `algo_ret` |
| `drivers/misc/mediatek/apusys/vpu/4.0/vpu_main.c` | `vpu_req_check()` | Requires exact VPU request size, validates flags, and bounds `buffer_count` |
| `drivers/misc/mediatek/apusys/vpu/p1/vpu_hw.c` | `vpu_execute_d2d()` | Copies `req->buffers[]` into the D2D command buffer and writes `XTENSA_INFO12..15`; D2D_EXT also writes preload entry/IRAM registers |
| `drivers/misc/mediatek/apusys/vpu/p1/vpu_hw.h` | register table | Documents `DO_D2D` as INFO12 buffer count, INFO13 buffer-array pointer, INFO14 setting pointer, and INFO15 setting size |
| IDA `vmlinux.bin` | `vpu_execute` at `0xffffffc0087a7974` | Kernel execution entry that reaches the D2D/D2D_EXT request path |

Runtime evidence so far proves `apu_lib_apunn` lookup, normal VPU request
acceptance, VPU boot/map activity, XRP-shaped settings header tolerance,
target-side nonzero code-section tolerance for six internal query/status
operation shapes, a controlled native VPU buffer writeback, a per-case timeout
for the earlier one-buffer shape, and a two-native-buffer `XTENSA_ANN_VERSION`
dispatch where copied native buffer descriptor `0` points at the code/input
window and the kernel logs residual command state instead of the earlier D2D
timeout. Libvpu-style descriptor metadata and a five-descriptor alias shape do
not change that boundary. It does not yet prove APUNN data descriptor
consumption, APUNN output-section writeback, the missing completion parameter,
or the full semantic meaning of the observed native-buffer writeback. The
batch-level devapc warning remains non-attributed because isolated single-case
runs did not reproduce it.
