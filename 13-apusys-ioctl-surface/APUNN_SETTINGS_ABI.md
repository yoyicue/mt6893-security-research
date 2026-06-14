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
buffer so runtime output can distinguish settings, output, data descriptor, and
data-payload writeback.

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
| `0x400` | Data payload and VPU plane0 MVA target, size `0x80` |

The mode prints the same windows before and after dispatch:

- `xrp_before_settings` / `xrp_after_settings`
- `xrp_before_output` / `xrp_after_output`
- `xrp_before_data_desc` / `xrp_after_data_desc`
- `xrp_before_data_payload` / `xrp_after_data_payload`

That lets the next run classify the previous `0xb` signal:

- settings window changed: firmware or wrapper is consuming `setting_iova`
  directly;
- output window changed: APUNN recognized the XRP output section;
- data descriptor changed: firmware rewrites the descriptor list;
- data payload changed: firmware followed a data descriptor or VPU plane MVA;
- command HardwareBuffer changed while imported buffer does not: the signal is
  likely command-buffer copyback through `mdw_cmd_sc_clr_hnd`.

## Runtime result

The 2026-06-14 `--run-cmd-vpu-xrp-iova-control` run validates the probe shape:
without final `run_cmd_async`, all four imported-buffer windows remain
unchanged across the 3-second wait.

The 2026-06-14 `--run-cmd-vpu-xrp-iova` dispatch run returns success from
`run_cmd_async`, reaches VPU boot/map-side kernel logs, and shows this
before/after delta:

| Window | Before | After | Interpretation |
|---|---:|---:|---|
| `settings+0x00` | `0x00000004` | `0x00000004` | Main XRP settings header unchanged |
| `output+0x00` | `0xffffffff` | `0xffffffff` | Output section header unchanged |
| `data_desc+0x00` | `0x00000003` | `0x00000003` | Data descriptor unchanged |
| `data_payload+0x00` | `0x41505530` | `0x41505531` | First data-payload word incremented by dispatch |
| command request head/tail | unchanged | unchanged | Not explained by visible command-buffer copyback |

The exact result files are:

- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_iova.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_iova_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_iova_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_iova_control_kernel.txt`

This moves the lower-bound APUNN interpretation from "the request dispatches"
to "the firmware path follows the recovered settings/data layout far enough to
write the first word of the data payload target." With the current zero-length
code section, there is still no recovered APUNN operation encoding and no
semantic label for the `+1` writeback. The earlier minimal IOVA mode wrote
`0xb` at imported-buffer offset `0`; that shape pointed settings and plane MVA
at the same malformed buffer base, so the new XRP-shaped result supersedes it
for section-routing attribution.

## Evidence map

Userland wrapper evidence:

| Binary | Function | Address | Evidence |
|---|---|---:|---|
| `libvpu.so` | `VpuRequestImp::prepareSettBuf` | `0x73fc` | Allocates settings memory and writes request `+0x38/+0x40` |
| `libvpu.so` | `VpuRequestImp::setProperty` | `0x7b6c` | Copies caller property bytes into settings memory and cache-syncs |
| `libvpu.so` | `VpuRequestImp::addBuffer` | `0x7624` | Populates `request+0x35` and descriptor array |
| `libvpu.so` | `VpuRequestImp::mmapMVA` | `0x7858` | Fills per-plane MVA/IOVA entries |
| `libvpu.so` | `VpuStreamImp::runReq` | `0x91fc` | Copies native `0xb70` request into APUSYS command memory |
| `libneuron_platform.vpu.so` | `XrpCommandInfo::Initialize` | `0xc5e4` | Initializes main XRP command buffer and magic |
| `libneuron_platform.vpu.so` | `XrpCommandInfo::InitCodeSection` | `0xc728` | Writes code size/IOVA fields |
| `libneuron_platform.vpu.so` | `XrpCommandInfo::InitOutputSection` | `0xc848` | Initializes output header and writes output size/IOVA |
| `libneuron_platform.vpu.so` | `XrpCommandInfo::InitDataSection` | `0xcaa0` | Writes data descriptor size/IOVA |
| `libneuron_platform.vpu.so` | `XrpIntrinsicExecutor::PrepareDataSection` | `0xfacc` | Builds 12-byte data entries |
| `libneuron_platform.vpu.so` | `XrpVpuStream::CreateVpuRequest` | `0x16d54` | Calls `vpuRequest_setProperty()` for the prepared settings payload |

Runtime evidence so far proves `apu_lib_apunn` lookup, normal VPU request
acceptance, VPU boot/map activity, XRP-shaped settings section routing, and a
controlled data-payload writeback. It does not yet prove the APUNN code-section
operation encoding or the semantic meaning of the observed `+1` data-payload
change.
