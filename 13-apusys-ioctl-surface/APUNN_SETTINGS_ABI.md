# APUNN settings ABI notes

This file tracks the current model for how `apu_lib_apunn` receives request
parameters. It separates the kernel APUSYS command wrapper, the native
`libvpu5.so` VPU request, and the `libneuron_platform.vpu.so` XRP command
buffer used by the direct settings-property experiments.

## Current interpretation

The strongest runtime model is the target-wrapper-shaped
`settings5/no-settings` request. In that shape the firmware is not handed the
raw user `struct vpu_request`; the kernel copies the five native VPU buffer
descriptors into a D2D command buffer, writes the D2D register tuple, and the
five descriptors all point at the same DSP command/settings buffer. The native
request settings tuple (`request+0x38/+0x40`) is clear, matching the target
`XrpVpuStream::CreateVpuRequest()` path that calls `vpuRequest_addBuffer()` five
times and does not call `vpuRequest_setProperty()`.

APUNN completion is visible as settings/output mutation, not merely as
`wait_cmd` success. The stable completion signature is:

- `run_cmd_async == 0` and `wait_cmd == 0`
- settings flags change from `0x5` to `0x7`, satisfying the host predicate
  `(settings[0] & 0x0a) == 0x02`
- standard one-entry data descriptors clear settings `+0x30`
- output bytes are written through the settings output IOVA and bounded by
  settings `+0x08`
- the code/input window and data payload window remain preserved for the tested
  `XTENSA_ANN_VERSION` and internal-query shapes

Current field interpretation in the completed shape:

| Field | Runtime interpretation |
|---:|---|
| outer `cb_info_size` | Must be exactly `0xb70` for the VPU provider request path |
| native `request+0x35` | Buffer count; completed wrapper replay uses `5` descriptors |
| native `request+0x38/+0x40` | Real libvpu settings-property tuple, but unused by this target wrapper path |
| native `request+0x50...` | Firmware-visible descriptor source after kernel D2D copy; descriptor target selection determines whether the request completes |
| settings `+0x00` | Command/completion flags; APUNN sets bit `0x2` in the stable completed shape |
| settings `+0x04` | Code-section size and a real firmware acceptance gate; `0..0x10` fail, `0x11` is the smallest tested completed size |
| settings `+0x08` | Output-section size and maximum output-fill bound |
| settings `+0x0c` | Data-descriptor byte size; `0x0c` is the standard one-entry consumption size |
| settings `+0x10` | Code-section IOVA; code bytes are consumed for acceptance, but tested opcode/operand fields do not produce source-sensitive output |
| settings `+0x20` | Output-section IOVA; APUNN writes completion/output bytes here |
| settings `+0x30` | Data-descriptor IOVA; standard one-entry descriptors are consumed/cleared, two-entry descriptors have target/order-sensitive cleanup |
| code entry `+0x00` | Operation id; `10001..10009` all complete in the stable shape |
| code entry `+0x08` and operands | Operand-list offset and operand ids are accepted across tested in-bounds and one OOB placement; not a visible completion gate |
| data payload contents | Preserved for tested patterns; no source-sensitive copy into output was observed |
| command request tail | Provider/midware scalar state copyback, not a pointer leak in completed runs |

This is enough to describe how the tested APUNN request is interpreted at the
field level. It is not yet enough to name the semantic meaning of the output
bytes or to prove an information leak: completed output currently looks like
operation/status completion data, while tested data payload, descriptor target,
and operand variations do not flow into output in a source-sensitive way.

## Layering

The direct ioctl experiments use three nested formats:

1. APUSYS `run_cmd_async` command buffer: top-level APUSYS header, one normal
   VPU subcommand (`type=0x03`), and an inline code buffer.
2. Native VPU request: the inline code buffer is exactly `0xb70` bytes and
   matches the target `libvpu5.so::VpuRequestImp+0x48` native request.
3. Optional APUNN/XRP settings buffer: the libvpu property path can make
   `request+0x38/+0x40` point to a separately allocated settings/property
   buffer. The direct ioctl probes fill this slot with an XRP command
   descriptor that points to code, output, and data sections.

Target-side static analysis currently separates this direct-probe layout from
`libneuron_platform.vpu.so::XrpVpuStream::CreateVpuRequest()`: that function
creates/acquires a libvpu request and calls `vpuRequest_addBuffer()` five times,
while leaving the resolved `vpuRequest_setProperty()` slot unused. This means
the firmware-facing `request+0x38/+0x40` tuple is still a real kernel input and
a valid libvpu property-path shape, while this target wrapper's ordinary
`CreateVpuRequest()` path submits APUNN through five copied native descriptors.

The direct ioctl replay now covers both sides of that split. The property-path
probes set `request+0x38/+0x40` to the XRP settings buffer. The target-wrapper
replay sets `buffer_count=5`, repeats the code/input descriptor five times, and
zeros `request+0x38/+0x40` to match the target wrapper's no-property path.

The outer APUSYS subcommand layer is now pinned too. `mdw_cmd_parse_cmd` copies
subcommand `+0x20` into `sc+0x28` as `cb_info_size` and records the selected
code-buffer KVA at `sc+0x20`. `mdw_cmd_sc_set_hnd` allocates a temporary provider
buffer of exactly `sc+0x28` bytes, copies from that KVA, and passes the temporary
buffer to the VPU provider as `provider_arg+0x00` with size `provider_arg+0x0c`.
After provider return, `mdw_cmd_sc_clr_hnd` copies the same temporary buffer back
to the original KVA and frees it. Runtime now confirms that this size must be
exactly `0xb70` for the VPU request path; non-exact sizes fail before the current
visible descriptor writeback.

The current `--run-cmd-vpu-iova` probe validates layer 2 and dispatch
reachability. It intentionally uses a minimal malformed settings payload by
pointing `setting_iova` at the imported HardwareBuffer base. The newer
`--run-cmd-vpu-xrp-iova` mode builds the layer-3 XRP shape in that imported
buffer so runtime output can distinguish settings, output, data descriptor,
data-payload, and native VPU plane-MVA writeback.

## Native VPU request link

`libvpu5.so::VpuRequestImp` owns a native request at object offset `+0x48`.
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
plane when this property path is used. The unresolved target-wrapper question is
whether the normal APUNN service path reaches this setter or relies on copied
native buffer descriptors only.

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
4. `XrpIntrinsicExecutor::CreateVpuRequest()` passes a tuple derived from
   `XrpCommandInfo+0x28/+0x38` to `XrpVpuStream::CreateVpuRequest()`.
5. On this target, `XrpVpuStream::CreateVpuRequest()` creates a libvpu request
   and calls the `stream+0x88` function pointer five times. The
   `VpuStreamLibManager` constructor resolves `stream+0x88` to
   `vpuRequest_addBuffer`. The same constructor resolves `stream+0x90` to
   `vpuRequest_setProperty`, but `CreateVpuRequest()` does not call that slot.
   Therefore the direct ioctl probe's `request+0x38/+0x40` settings
   payload is a kernel/firmware-facing experiment and a valid libvpu
   property-path shape, but it is not emitted by the target wrapper's
   `CreateVpuRequest()` path seen in `libneuron_platform.vpu.so`.

Static wrapper resolution from `/tmp/mtk-apu-artifacts/device/libneuron_platform.vpu.so`:

| Evidence | Result |
|---|---|
| `VpuStreamLibManagerC2()` loads `libvpu5.so` with `dlopen()` | The wrapper targets the same `libvpu5.so` request ABI used by the direct probes |
| `stream+0x88` `dlsym()` string | `vpuRequest_addBuffer` |
| `stream+0x90` `dlsym()` string | `vpuRequest_setProperty` |
| `XrpVpuStream::CreateVpuRequest()` at `0x16d54` | Calls `stream+0x88` five times with the same stack descriptor; no `stream+0x90` call |
| `XrpIntrinsicExecutor::CreateVpuRequest()` at `0xf788` | Passes `XrpCommandInfo+0x28/+0x38`-derived code descriptor fields to `XrpVpuStream::CreateVpuRequest()` |

This turns the target-wrapper lower-bound model into:

1. APUSYS/VPU firmware receives `XTENSA_INFO12 = 5` and `XTENSA_INFO13` pointing
   to the kernel-copied five-entry `struct vpu_buffer[]`.
2. The first copied native descriptor is the currently observed status/writeback
   target in the incomplete path.
3. `XTENSA_INFO14/15` remain a real kernel input, but the normal wrapper path
   does not rely on them for the XRP command buffer on this build.

Main XRP command buffer fields recovered from userland wrappers:

| Offset | Size | Meaning |
|---:|---:|---|
| `+0x00` | 4 | Command flags; `Initialize()` writes `4`, then `CreateXrpCommand()` sets bit `0x1`, so the normal pre-dispatch value is `5` |
| `+0x04` | 4 | Code section size; runtime now shows this is a firmware acceptance gate |
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

If `InitOutputSection()` is asked to write this header and the output buffer is
smaller than `0x40`, it returns status `2` before the VPU request is built. In
the host-side standard path, `PrepareOutputBuffer()` writes settings
`+0x08/+0x20` from the allocated output buffer and calls
`PrepareOutputHeader()` for nonzero command handles.

The data descriptor section is an array of 12-byte entries:

| Entry offset | Meaning |
|---:|---|
| `+0x00` | Access/metadata flags; wrapper-generated standard entry is observed as `3` |
| `+0x04` | Buffer size |
| `+0x08` | Low 32 bits of buffer IOVA |

The device wrapper's `PrepareDataSection()` allocates `12 * vector_length`
bytes for this section, zeroes it, then fills valid `cXrpBufferInfo` records by
their `+0x08` slot index. For each valid slot it writes `3` at entry `+0x00`,
`cXrpBufferInfo+0x10` at entry `+0x04`, and `cXrpBufferInfo+0x20` at entry
`+0x08`. `XrpCommandInfo::UpdateDataBuffer()` uses the same slot index and
updates only the size/IOVA words after checking that the slot is below
`data_desc_size / 12`.

The host-side standard path matches this layout. `PrepareDataBuffer()` writes
settings `+0x0c` and `+0x30` from an allocated descriptor buffer sized as
`12 * data_buffer_count`. Static analysis of
`libneuron_platform.so::XrpIntrinsicExecutor::FinalizeDataBuffer()` at
`0x205bc` shows the finalizer computes `data_buffer_count` from the executor's
data-buffer vector length, then writes each entry from `GetBuffer(index)`:
`XrpBufferDesc+0x20` to entry `+0x00`, `XrpBufferDesc+0x08` to entry `+0x04`,
and `XrpBufferDesc+0x30` to entry `+0x08`, before calling
`XrpCommandInfo::RegisterDataBuffer(index)`. This means a zero-data probe is a
useful control, but it does not exercise the standard data descriptor population
path.

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

The completed `settings5/no-settings` runtime matrix now accepts operand-list
offsets `0/0x10/0x40/0x100/0x17e/0x180` under `XTENSA_ANN_VERSION`. Offset
`0x17e` ends exactly at the advertised `0x1c8` operation entry boundary, while
`0x180` places the operand word just outside that entry. Both still reach the
same APUNN completion state, so this field is not a visible completion or bounds
gate for the current opcode/oracle. It is still not proven as a source-sensitive
operand-list dereference.

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

The completed-shape code-size matrices make settings `+0x04` a runtime gate, not
just a wrapper field. In the settings-backed five-descriptor/no-settings shape,
code sizes `0`, `4`, `8`, `0xc`, and `0x10` reach dispatch but wait returns
`-EIO`, settings remain `0x5`, settings `+0x30` stays nonzero, output stays at
the initialized header, and native request `result_status` becomes `0x2`. Code
size `0x11` is the smallest tested value that reaches normal APUNN completion.
Clean/success-only reruns complete every selected valid size
`0x11/0x14/0x1c/0x20/0x40/0x48/0x4c/0x1c9`. Full range runs show a batch-state
caveat after the failing low-size prefix: some later valid sizes can return wait
success and native `result_status=0` while settings/output remain unchanged.
APUNN settings/output mutation is therefore the completion oracle, not host wait
status alone.

## MVPU embedded-kernel side evidence

`/tmp/mtk-apu-artifacts/device/libmvpuop_mtk_nn.mtk.so` embeds many 32-bit
RISC-V MVPU ELF blobs. Eighteen of those inner ELFs carry a
`.mvpu.kernel.info` section. The section is a short ASCII argument type
signature, for example:

| Inner ELF group | `.mvpu.kernel.info` examples |
|---|---|
| small point kernels | `s8p,s32,s32,s32,s32,s32,s32p` and `u8p,s32,s32,s32,s32,s32,s32p` |
| GLSU/check kernels | `u8p,s32p,s32p,u8p,s32,s32,s32,s32,s32`, plus matching `u16p` and `u32p` variants |
| larger GLSU kernels | `u8p,s32p,s32p,u8p,s32,s32,s32,s32,s32,s32,s32,s32,s32`, plus matching `u16p` and `u32p` variants |

The symbol tables are generic: `entry_function`, `_start`, `kernel_entry`,
`kernel_wrapper`, `memset`, and for the GLSU-like blobs `check_GLSU_2D`,
`check_GLSU_3D`, and `check_GLSU_4D`. These blobs show the MVPU firmware/kernel
toolchain's parameter style: a firmware-visible command points at a kernel blob,
and the blob carries an ordered type signature for its arguments. They do not,
by themselves, decode the `apu_lib_apunn` XRP command header or the internal
`10001..10009` APUNN operation namespace. The APUNN parser boundary therefore
remains the raw code-section bytes consumed behind `apu_lib_apunn`, not the MVPU
kernel-info metadata in `libmvpuop_mtk_nn.mtk.so`.

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
| `(opcode >> 4) <= 0x270`, indexed by the full opcode | builtin-like table at `.data.rel.ro+0x08` | `CONV2D`, `RELU`, `RESHAPE`, `CAST`, `GET_ALGO_INFO`, `LOCAL_MEM_INFO`, `XTENSA_ANN_VERSION`, `GET_DETAILED_OP_INFO`, `unknown`, `apu_lib_apunn`, `apu_lib_custom`, `apunn_dynamic`, `custom_dynamic` |
| `10001..10009`, indexed by `opcode - 10001` | internal table at `.data.rel.ro+0x200` | `GET_ALGO_INFO`, `LOCAL_MEM_INFO`, `XTENSA_ANN_VERSION`, `GET_DETAILED_OP_INFO`, `unknown`, `apu_lib_apunn`, `apu_lib_custom`, `apunn_dynamic`, `custom_dynamic` |
| `15001` | special case | `custom op` |
| `15002` | special case | `builtin cv op` |

`10003` is `0x2713`, which fails the builtin guard because `0x2713 >> 4` is
`0x271`, then lands in the internal table as index `2`. That statically pins
the current probe opcode to `XTENSA_ANN_VERSION`. No confirmed no-op appears in
the recovered name tables. The `10003` probe above shows that a minimal
one-entry target code section can be submitted without a user-visible crash, but
the unchanged output/data windows mean the expected output operand and remaining
`0x1c8` entry fields are still unmapped.

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

The 2026-06-14 operand-offset matrix moved the debug-visible operand-list
offset in the current wrapper-data request shape. It keeps the two native
libvpu-style descriptors, `settings_len=0x68`, wrapper send-state flags
`settings[0]=0x5`, one standard APUNN data descriptor, and the
`XTENSA_ANN_VERSION` one-output operation. It varies only operation
`entry+0x08` and relocates the zero output operand id to
`entry+0x48+operand_off`:

| Operand-list offset | Control result | Dispatch result |
|---:|---|---|
| `0x00` | All windows unchanged | `run_async_vpu_iova ret=0`; settings stay `0x5`; code word `0` changes `0x2713 -> 0x271b`; output/data unchanged |
| `0x10` | All windows unchanged | Same result |
| `0x40` | All windows unchanged | Same result |
| `0x100` | All windows unchanged | Same result |

The dispatch kernel log records VPU map/boot activity and four residual
commands at process teardown, with no captured APUNN output completion. This
rules out the operand-list offset field value and relocated zero output operand
as the missing APUNN completion trigger in the direct ioctl request shape. The
target-wrapper/no-settings output-operand-id matrix extends this to nonzero
operand ids for the current incomplete request shape, but still does not prove
the completed APUNN data-binding contract.

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
| `XrpBufferDesc+0x10` | Section size copied into settings section-size fields by the target `Init*Section()` helpers |
| `XrpBufferDesc+0x18` | Host VA used for header initialization and optional data copies |
| `XrpBufferDesc+0x20` | Access/descriptor metadata; the data finalizer uses this class of metadata for descriptor entry `+0x00` |
| `XrpBufferDesc+0x28` | Device VA / IOVA field copied into settings code/output/data IOVA slots by the target `Init*Section()` helpers |
| `XrpCommandInfo+0x28/+0x38` | Tuple later consumed by target `XrpVpuStream::CreateVpuRequest()` to build the repeated libvpu buffer descriptor |
| `PrepareXtensaCommandBuffer()` | Writes settings `+0x04 = code_size`, `+0x10 = code_iova_low32` |
| `CalculateOutputSize()` | Returns `0x40` in the default wrapper mode; when the wrapper output-sizing option is set, it computes `0x40 + 4 * (code_size / first_entry_stride)` |
| `PrepareOutputBuffer()` | Writes settings `+0x08 = output_size`, `+0x20 = output_iova_low32`, then prepares the output header |
| `PrepareOutputHeader(bool)` | Writes output `+0x00/+0x04 = 0xffffffff/0x40`, `+0x08 = 4`, `+0x0c = output_size`, `+0x10 = bool flag` |
| `PrepareDataBuffer()` | Allocates a data-descriptor section sized as `data_buffer_count * 0x0c`; the zero-data-buffer path is valid and leaves no data section to consume |
| `FinalizeDataBuffer()` | Computes the registered data-buffer count from the executor vector, then fills descriptor entries as `{XrpBufferDesc+0x20, XrpBufferDesc+0x08, XrpBufferDesc+0x30}` and registers each slot with `XrpCommandInfo` |
| `CreateVpuRequest()` | Creates the target VPU request through repeated `vpuRequest_addBuffer()` calls; `vpuRequest_setProperty()` is resolved in the binding table but not used by this function |

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
window. At process teardown, the kernel logs `mdw_usr_destroy residual cmd`, so
this older shape did not prove completion. The visible data delta moves with
native buffer `0`: `code+0x00` changes from `0x2713` to `0x271b`. The XRP output
header, data descriptor, data payload, and unused plane-payload sentinel remain
unchanged.

This older boundary established:

- the two-buffer native VPU shape changes APUNN lifecycle behavior from the
  previous per-case worker timeout to a residual-command teardown without a
  captured `D2D_EXT timeout`;
- firmware-visible writeback follows native buffer `0` when the copied
  `struct vpu_buffer[0]` descriptor points at the code/input IOVA;
- settings `+0x08/+0x20` plus native buffer `1` are still not enough to observe
  APUNN output-section writeback.

Follow-up static analysis narrowed the normal `struct vpu_buffer` metadata:
the target `libvpu5.so::VpuRequestImp::addBuffer()` writes `port_id`, DATA format,
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

The 2026-06-15 target-wrapper replay then tested the static
`CreateVpuRequest()` correction directly. It keeps the wrapper-one-data
observability windows in the imported buffer, but the native VPU request no
longer points at them through the settings tuple:

| Variant | Native request shape | Runtime result |
|---|---|---|
| `target_code5_no_settings` control | `buffer_count=5`, all five descriptors point at code/input, `request+0x38 = 0`, `request+0x40 = 0`, no dispatch | All settings/code/output/data windows and command buffer stay unchanged |
| `target_code5_no_settings` dispatch | Same request, with async submit | `run_async_vpu_iova ret=0`; VPU boot/map logs present; settings/output/data windows unchanged; code/input first word changes `0x2713 -> 0x271b`; teardown logs residual command |
| `target_code5_no_settings_wait` | Same request, followed by `mdw_usr_wait_cmd` | `run_async_vpu_iova ret=0`; `wait_vpu_iova ret=0`; settings/output/data windows unchanged; code/input first word changes `0x2713 -> 0x271b`; no residual command warning in the captured kernel log |
| `target_code5_no_settings_wait_summary` | Same wait variant with semantic request-field dumps | Before and after dispatch: `result_status=0`, `slot_b68=0`, `algo_ret_b6c=0`, `buffer_count=5`, and `settings_len/settings_iova=0`; code/input first word still changes `0x2713 -> 0x271b` |
| `target_settings5_no_settings_wait` control | `buffer_count=5`, all five descriptors point at the DSP command/settings buffer, `request+0x38 = 0`, `request+0x40 = 0`, no dispatch | Settings stay `0x5`, settings `+0x30` keeps the data-descriptor pointer, output keeps its initialized header, and code/input stays unchanged |
| `target_settings5_no_settings_wait` | Same settings-backed request, followed by `mdw_usr_wait_cmd` | `run_async_vpu_iova ret=0`; `wait_vpu_iova ret=0`; settings change `0x5 -> 0x7`; settings `+0x30` is cleared; code/input is unchanged; output words through offset `0x40` become `0xffffffff` |
| `target_settings5_no_settings_opcode_matrix` control | Same settings-backed request, opcodes `10001..10009`, no dispatch | Every case preserves settings `0x5`, settings `+0x30`, code/opcode words, and initialized output header |
| `target_settings5_no_settings_opcode_matrix` | Same settings-backed request, opcodes `10001..10009`, followed by `mdw_usr_wait_cmd` | Every case returns `0` from dispatch and wait, changes settings `0x5 -> 0x7`, clears settings `+0x30`, leaves the code/opcode window unchanged, and writes output; `10004` writes through output offset `0x3c`, while the other tested opcodes write through offset `0x40` |
| `target_settings5_no_settings_operand_id_matrix` control | Same settings-backed request, opcode `10003`, output operand ids `0/1/2/3/0xffff`, no dispatch | Every case preserves settings `0x5`, settings `+0x30`, code/operand-list words, and initialized output/data windows |
| `target_settings5_no_settings_operand_id_matrix` | Same settings-backed request, output operand ids `0/1/2/3/0xffff`, followed by `mdw_usr_wait_cmd` | Every case returns `0` from dispatch and wait, changes settings `0x5 -> 0x7`, clears settings `+0x30`, and leaves the code/operand-list words unchanged; data descriptor and data payload remain unchanged; output tail varies across repeats and is not operand-id-stable |
| `target_settings5_no_settings_operand_offset_matrix` control | Same settings-backed request, opcode `10003`, one output operand id `0`, operand-list offsets `0/0x10/0x40/0x100/0x17e/0x180`, no dispatch | Every case preserves settings `0x5`, settings `+0x30`, requested offset words, and initialized output/data windows |
| `target_settings5_no_settings_operand_offset_matrix` | Same settings-backed request, tested operand-list offsets, followed by `mdw_usr_wait_cmd` | Every case returns `0` from dispatch and wait, changes settings `0x5 -> 0x7`, clears settings `+0x30`, leaves the code window unchanged, and writes output through offset `0x40`; `0x180` places the operand outside the advertised op entry and still completes |
| `target_settings5_no_settings_op_shape_matrix` control | Same settings-backed request, opcode `10003`, input/output counts `0/0`, `0/1`, `0/2`, `1/0`, `1/1`, and `2/1`, no dispatch | Every case preserves settings `0x5`, settings `+0x30`, code/count/operand-list words, and initialized output/data windows |
| `target_settings5_no_settings_op_shape_matrix` | Same settings-backed request, tested input/output count combinations, followed by `mdw_usr_wait_cmd` | Every case returns `0` from dispatch and wait, changes settings `0x5 -> 0x7`, clears settings `+0x30`, and leaves the code window unchanged; data descriptor and data payload remain unchanged; output tail varies across repeats and is not count-stable |
| `target_settings5_no_settings_code_size_matrix` control | Same settings-backed request, opcode `10003`, code sizes `0/4/0x48/0x1c7/0x1c8/0x1c9/0x390`, no dispatch | Every case preserves the initialized code-size field, settings `0x5`, settings `+0x30`, and output/data windows |
| `target_settings5_no_settings_code_size_matrix` | Same settings-backed request, varied `settings+0x04`, followed by `mdw_usr_wait_cmd` | Code sizes `0` and `4` fail with `-EIO` and do not complete APUNN settings/output. Sizes `0x48`, `0x1c7`, `0x1c8`, and `0x390` complete in the original matrix; one `0x1c9` repeat returned wait success without settings/output completion |
| `target_settings5_no_settings_code_size_range_matrix` control | Same settings-backed request, opcode `10003`, code sizes `0/4/8/0xc/0x10/0x11/0x12/0x13/0x14/0x18/0x1c/0x20/0x3f/0x40/0x44/0x47/0x48/0x49/0x4c/0x1c7/0x1c8/0x1c9/0x1ca/0x1cc`, no dispatch | Every case preserves the initialized code-size field, settings `0x5`, settings `+0x30`, and output/data windows |
| `target_settings5_no_settings_code_size_range_matrix` | Same settings-backed request, varied `settings+0x04`, followed by `mdw_usr_wait_cmd` | Sizes `0/4/8/0xc/0x10` fail with `-EIO` and `result_status=0x2`. Size `0x11` is the smallest tested completed size. Later valid sizes generally complete, but after the failing prefix some cases (`0x1c`, `0x40`, `0x48`, or `0x4c` depending on run) can return wait success without APUNN settings/output mutation |
| `target_settings5_no_settings_code_size_clean_matrix` control | Same settings-backed request, opcode `10003`, code sizes `0x11/0x14/0x1c/0x20/0x40/0x48/0x4c/0x1c9`, no dispatch | Every case preserves the initialized code-size field, settings `0x5`, settings `+0x30`, and output/data windows |
| `target_settings5_no_settings_code_size_clean_matrix` | Same settings-backed request, success-looking code sizes only, followed by `mdw_usr_wait_cmd` | Every selected size completes in both dispatch runs: wait returns `0`, settings change `0x5 -> 0x7`, `settings+0x30` clears, native `result_status=0`, and output is written |
| `target_settings5_no_settings_output_shape_matrix` control | Same settings-backed request, opcode `10003`, output sizes `0/4/0x10/0x3c/0x40/0x44/0x80` plus header flag `1` cases, no dispatch | Every case preserves the initialized output header, settings `0x5`, standard data descriptor pointer, and data payload |
| `target_settings5_no_settings_output_shape_matrix` | Same settings-backed request, varied output size/header, followed by `mdw_usr_wait_cmd` | Every case returns `0`, changes settings `0x5 -> 0x7`, and clears `settings+0x30`; small sizes leave header words intact, larger sizes show `settings+0x08` as the maximum output-fill bound, with some repeat-time tail skips |
| `target_settings5_no_settings_data_desc_matrix` control | Same settings-backed request, opcode `10003`, varied data descriptor size and pointer presence, no dispatch | Every case preserves settings, output header, data descriptor, and data payload |
| `target_settings5_no_settings_data_desc_matrix` | Same settings-backed request, varied data descriptor size/pointer, followed by `mdw_usr_wait_cmd` | Every case returns `0` and writes output; no pointer stays zero, non-null size `0` is preserved, standard size `0x0c` clears `settings+0x30`, and larger sizes `0x18/0x80` preserve `settings+0x30` but clear descriptor word `0` |
| `target_settings5_no_settings_data_entry_matrix` control | Same settings-backed request, opcode `10003`, explicit one-entry and two-entry descriptor contents, no dispatch | Every case preserves the explicitly initialized descriptor entries, data payload, plane payload, and output header |
| `target_settings5_no_settings_data_entry_matrix` | Same settings-backed request, explicit flags `1/2/3`, size-0 entry, and two-entry payload/plane orderings, followed by `mdw_usr_wait_cmd` | Every case returns `0`, changes settings `0x5 -> 0x7`, and writes output. Single-entry flags `1/2/3` and entry size `0` clear `settings+0x30` and leave entries unchanged. Two-entry cases keep `settings+0x30` nonzero; payload-then-plane clears both entry flags, while plane-then-payload leaves both entries unchanged. Data and plane payload bytes remain unchanged |
| `target_settings5_no_settings_data_target_matrix` control | Same settings-backed request, opcode `10003`, descriptor entries pointing at settings/code/output/data-desc/data-payload/plane-payload, no dispatch | Every case preserves the explicitly initialized descriptor entries and target windows |
| `target_settings5_no_settings_data_target_matrix` | Same settings-backed request, single-entry section targets and two-entry payload/settings/code/output orderings, followed by `mdw_usr_wait_cmd` | Every case returns `0`, changes settings `0x5 -> 0x7`, and writes output. Single-entry targets clear `settings+0x30` and leave entries unchanged. Two-entry payload/settings/code/output pairs keep `settings+0x30` nonzero and clear both entry flags while preserving size/IOVA. Target windows remain unchanged outside normal completion/output state |
| `target_settings5_no_settings_data_payload_matrix` control | Same settings-backed request, opcode `10003`, standard data descriptor, varied data payload word bases, no dispatch | Every case preserves the chosen payload pattern and initialized output header |
| `target_settings5_no_settings_data_payload_matrix` | Same settings-backed request, payload word bases `0/0x41505530/0x5a5a0000/0x7f000000`, followed by `mdw_usr_wait_cmd` | Every case returns `0`, changes settings `0x5 -> 0x7`, clears `settings+0x30`, leaves data descriptor and payload unchanged, and writes completion-style output that does not reflect the payload pattern |

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_wait.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_wait_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_code5_no_settings_wait_summary.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_code5_no_settings_wait_summary_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_wait_repeat.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_wait_repeat_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_wait_control_repeat.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_wait_control_repeat_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_opcode_matrix.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_opcode_matrix_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_opcode_matrix_control.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_opcode_matrix_control_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_operand_id_matrix.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_operand_id_matrix_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_operand_id_matrix_control.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_operand_id_matrix_control_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_operand_id_matrix_repeat.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_operand_id_matrix_repeat_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_operand_offset_matrix.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_operand_offset_matrix_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_operand_offset_matrix_control.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_operand_offset_matrix_control_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_operand_offset_matrix_repeat.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_operand_offset_matrix_repeat_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_op_shape_matrix.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_op_shape_matrix_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_op_shape_matrix_control.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_op_shape_matrix_control_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_op_shape_matrix_repeat.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_op_shape_matrix_repeat_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_code_size_matrix.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_code_size_matrix_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_code_size_matrix_control.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_code_size_matrix_control_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_code_size_matrix_repeat.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_code_size_matrix_repeat_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_code_size_range_matrix.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_code_size_range_matrix_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_code_size_range_matrix_control.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_code_size_range_matrix_control_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_code_size_range_matrix_repeat.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_code_size_range_matrix_repeat_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_code_size_clean_matrix.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_code_size_clean_matrix_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_code_size_clean_matrix_control.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_code_size_clean_matrix_control_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_code_size_clean_matrix_repeat.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_code_size_clean_matrix_repeat_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_output_shape_matrix.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_output_shape_matrix_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_output_shape_matrix_control.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_output_shape_matrix_control_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_output_shape_matrix_repeat.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_output_shape_matrix_repeat_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_data_desc_matrix.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_data_desc_matrix_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_data_desc_matrix_control.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_data_desc_matrix_control_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_data_desc_matrix_repeat.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_data_desc_matrix_repeat_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_data_entry_matrix.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_data_entry_matrix_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_data_entry_matrix_control.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_data_entry_matrix_control_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_data_target_matrix.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_data_target_matrix_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_data_target_matrix_control.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_data_target_matrix_control_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_data_payload_matrix.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_data_payload_matrix_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_data_payload_matrix_control.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_data_payload_matrix_control_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_data_payload_matrix_repeat.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_settings5_no_settings_data_payload_matrix_repeat_kernel.txt`

The code5 summary rerun shows that the copied-back native request fields
`result_status` (`+0x34`), slot (`+0xb68`), and `algo_ret` (`+0xb6c`) remain zero
in that successful wait case. Those fields therefore do not carry the observed
descriptor-0 `0x2713 -> 0x271b` state write. The settings5 rerun is the
completed wrapper-shaped case: firmware acts on the DSP command/settings buffer
descriptor, not on the code/input descriptor, and produces the wrapper-visible
settings/output completion state. The settings5 opcode matrix extends that from
`XTENSA_ANN_VERSION` to the recovered `10001..10009` opcode range: once the
descriptor target is the DSP command/settings buffer, the previous code-first
`10005..10007` timeout/error classes disappear and all tested opcodes complete.
The only visible opcode distinction in this shape is output length: `10004`
leaves output offset `0x40` unchanged, while the other tested opcodes write
through it.

The follow-up descriptor-0 first-word matrix keeps the same
target-wrapper-shaped request and varies only code/input word `0`.

| Input word | Control result | Dispatch + wait result |
|---:|---|---|
| `0x00000000` | Code word remains `0x00000000` | Code word becomes `0x0000000b`; async `0`, wait `0` |
| `0x00002713` | Code word remains `0x00002713` | Code word becomes `0x0000271b`; async `0`, wait `0` |
| `0x0000271b` | Code word remains `0x0000271b` | Code word remains `0x0000271b`; async `0`, wait `0` |
| `0x504c4e30` | Code word remains `0x504c4e30` | Code word becomes `0x504c4e3b`; async `0`, wait `0` |
| `0xffffffff` | Code word remains `0xffffffff` | Code word becomes `0xfffffffd`; async `0`, wait `-EIO`; kernel log records `ret(-110)` and `mdw_wait_cmd ... fail` |

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_word_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_word_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_word_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_word_matrix_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_code5_no_settings_word_matrix_summary.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_target_code5_no_settings_word_matrix_summary_kernel.txt`

Settings remain `0x5`, output remains `0xffffffff`, and data descriptor/data
payload windows remain unchanged in every case. For ordinary inputs, the
descriptor-0 first word behaves like `old | 0xb`; the all-ones case enters an
error/timeout state and clears bit `1`. This pins the current visible writeback
as state-word behavior on copied native descriptor `0`. It still does not show
APUNN settings completion, APUNN output-section copyback, or a source-sensitive
leak.

The 2026-06-15 summary rerun adds request-field visibility to the same matrix.
`slot_b68` and `algo_ret_b6c` remain `0` in every case. Successful-wait cases
also keep `result_status=0`; the all-ones `-EIO` case changes
`result_status=0x2` while leaving `algo_ret_b6c=0`. The rerun also misses the
`0x2713 -> 0x271b` write for that single case, reinforcing the earlier
timing/state sensitivity. Request `+0x34` is therefore a provider status byte for
the error path, while `+0xb6c` is not the missing APUNN output signal in this
shape.

The descriptor-size matrix keeps the same target-wrapper-shaped request, keeps
code/input word `0` at `0x2713`, and varies only the copied native descriptor
payload size fields (`width`, `stride`, and `length`) across all five
code/input descriptors:

| Descriptor size | Control result | First dispatch + wait | Repeat dispatch + wait |
|---:|---|---|---|
| `0,1,2,3,4,5,6,8,0x20,0x40,0x1c8` | Code word remains `0x2713`; APUNN windows unchanged | Code word becomes `0x271b`; async `0`, wait `0` | Code word becomes `0x271b`; async `0`, wait `0` |
| `7,9,0xc,0x10` | Code word remains `0x2713`; APUNN windows unchanged | Code word remains `0x2713`; async `0`, wait `0` | Code word becomes `0x271b`; async `0`, wait `0` |

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_size_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_size_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_size_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_size_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_size_matrix_repeat.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_size_matrix_repeat_kernel.txt`

Every tested descriptor size is accepted by the kernel/worker path. The repeat
run shows the visible descriptor-0 state write can happen even when the native
descriptor advertises payload size `0`, so those size fields are not a hard
bounds gate for this state-word writeback. The first run's no-write cases also
show that this signal is timing/state/cache-sensitive and should not be treated
as a normal APUNN completion oracle.

The request-priority matrix keeps the same target-wrapper-shaped request and
first word `0x2713`, but varies native request `+0xb68`:

| Request `+0xb68` | Control result | First dispatch + wait | Repeat dispatch + wait |
|---:|---|---|---|
| `0` | Code word remains `0x2713`; command-buffer tail word `40` remains `0` | Code word becomes `0x271b`; async `0`, wait `0`; tail word `40` cleared to `0` | Code word becomes `0x271b`; async `0`, wait `0`; tail word `40` cleared to `0` |
| `1` | Code word remains `0x2713`; command-buffer tail word `40` remains `1` | Code word remains `0x2713`; async `0`, wait `0`; tail word `40` cleared to `0` | Code word becomes `0x271b`; async `0`, wait `0`; tail word `40` cleared to `0` |
| `2` | Code word remains `0x2713`; command-buffer tail word `40` remains `2` | Code word remains `0x2713`; async `0`, wait `0`; tail word `40` cleared to `0` | Code word becomes `0x271b`; async `0`, wait `0`; tail word `40` cleared to `0` |
| `3` | Code word remains `0x2713`; command-buffer tail word `40` remains `3` | Code word becomes `0x271b`; async `0`, wait `0`; tail word `40` cleared to `0` | Code word becomes `0x271b`; async `0`, wait `0`; tail word `40` cleared to `0` |
| `0xffffffff` | Code word remains `0x2713`; command-buffer tail word `40` remains `0xffffffff` | Code word becomes `0x271b`; async `0`, wait `0`; tail word `40` cleared to `0` | Code word becomes `0x271b`; async `0`, wait `0`; tail word `40` cleared to `0` |

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_priority_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_priority_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_priority_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_priority_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_priority_matrix_repeat.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_priority_matrix_repeat_kernel.txt`

IDA now separates the user-submitted field from the value that reaches the D2D
helpers. The normal-VPU opcode-4 provider clears `request+0xb68` before
`vpu_execute()`, so the default path reaches D2D_EXT with slot `0` after the
Normal lookup misses and the Preload retry sets bit `2`. If the caller sets
`request+0x28` bit `2` up front, `vpu_execute_with_slot()` stores its internal
slot allocator result at `request+0xb68` before `vpu_execute()`. The
descriptor-copy, preload-object, copied-array-IOVA, status, and algo-ret helpers
clamp that value into `0..2`; the wait helper indexes with the raw slot value,
but the upstream provider has already replaced the user field with a
kernel-owned value on the execution paths seen here.

The matrix therefore proves that the original `request+0xb68` word is not the
missing APUNN completion condition. The dispatch copyback clearing the tail word
matches the provider rewrite, and the first run's no-write cases for `1` and `2`
again show the visible state write is not a stable APUNN completion oracle.

The request-buffer-count matrix keeps the same target-wrapper-shaped request and
first word `0x2713`, but varies native request byte `+0x35`. This byte controls
both the kernel-side descriptor-copy length and firmware `XTENSA_INFO12`.

| Request `+0x35` | Control result | First dispatch + wait | Repeat dispatch + wait |
|---:|---|---|---|
| `0` | Code word remains `0x2713`; APUNN windows unchanged | Code word remains `0x2713`; async `0`, wait `-EIO`; APUNN windows unchanged | Code word remains `0x2713`; async `0`, wait `-EIO`; APUNN windows unchanged |
| `1,2,3,4,5,0x20` | Code word remains `0x2713`; APUNN windows unchanged | Code word becomes `0x271b`; async `0`, wait `0`; APUNN windows unchanged | Code word becomes `0x271b`; async `0`, wait `0`; APUNN windows unchanged |

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_buffer_count_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_buffer_count_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_buffer_count_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_buffer_count_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_buffer_count_matrix_repeat.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_buffer_count_matrix_repeat_kernel.txt`

Runtime therefore confirms `buffer_count=0` is a liveness gate for the current
descriptor-0 state writeback path. Once one copied descriptor is advertised,
larger counts up to the kernel-accepted maximum `0x20` do not alter the same
writeback boundary or produce APUNN settings/output completion.

The descriptor-port-id matrix keeps the same target-wrapper-shaped request,
`buffer_count=5`, and first word `0x2713`, but varies descriptor byte `+0x00`
through `0,1,2,3,4,0xff`. Control preserves the requested byte and leaves APUNN
windows unchanged. Dispatch plus wait returns `0` for every tested port. The
first dispatch misses visible descriptor-0 writeback for default `port_id=1`,
but the repeat writes `0x2713 -> 0x271b` for every tested port. Descriptor port
id is therefore not a hard role/acceptance gate for this state writeback.

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_port_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_port_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_port_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_port_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_port_matrix_repeat.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_port_matrix_repeat_kernel.txt`

The descriptor-format matrix keeps the same target-wrapper-shaped request,
`buffer_count=5`, and first word `0x2713`, but varies descriptor byte `+0x01`
through `0,1,2,3,4,0xff`. Control preserves the requested byte and leaves APUNN
windows unchanged. Two dispatch runs return `0` from wait, write
`0x2713 -> 0x271b` for every tested format, and leave APUNN settings/output/data
windows unchanged. Descriptor format/direction is therefore not a hard
role/acceptance gate for this state writeback.

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_format_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_format_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_format_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_format_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_format_matrix_repeat.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_format_matrix_repeat_kernel.txt`

The descriptor-plane-count matrix keeps the same target-wrapper-shaped request,
`buffer_count=5`, and first word `0x2713`, but varies descriptor byte `+0x02`
through `0,1,2,3,4,0xff`. Control preserves the requested byte and leaves APUNN
windows unchanged. Dispatch plus wait returns `0` for every tested plane count.
The first dispatch run missed visible descriptor-0 writeback for `plane_count=4`,
but the repeat writes `0x2713 -> 0x271b` for every tested value, including `0`
and `0xff`. Descriptor plane count is therefore not the liveness gate for this
state writeback.

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_plane_count_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_plane_count_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_plane_count_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_plane_count_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_plane_count_matrix_repeat.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_plane_count_matrix_repeat_kernel.txt`

The descriptor-height matrix keeps the same target-wrapper-shaped request,
`buffer_count=5`, and first word `0x2713`, but varies descriptor word `+0x08`
through `0,1,2,3,0x40,0xffffffff`. Control preserves the requested word and
leaves APUNN windows unchanged. Dispatch plus wait returns `0` for every tested
height and leaves APUNN settings/output/data windows unchanged. The first
dispatch run writes `0x2713 -> 0x271b` for every tested height. The two repeat
runs miss visible descriptor-0 writeback for different subsets, so height is not
a hard writeback gate and the first-word writeback is not a stable completion
oracle.

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_height_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_height_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_height_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_height_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_height_matrix_repeat.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_height_matrix_repeat_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_height_matrix_repeat2.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_height_matrix_repeat2_kernel.txt`

The output-operand-id matrix keeps the same target-wrapper-shaped request,
`buffer_count=5`, first word `0x2713`, and one `XTENSA_ANN_VERSION` output, but
varies the 16-bit output operand id at code entry `+0x48` through
`0,1,2,3,0xffff`. No-dispatch controls preserve the requested operand id and
leave APUNN windows unchanged. Dispatch plus wait returns `0` for every tested
operand id and leaves APUNN settings/output/data windows unchanged. The first
dispatch writes `0x2713 -> 0x271b` for every tested operand id. The repeat
misses visible descriptor-0 writeback for `1` and `0xffff`, matching the
cross-run instability already seen in the descriptor metadata matrices.

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_operand_id_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_operand_id_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_operand_id_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_operand_id_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_operand_id_matrix_repeat.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_operand_id_matrix_repeat_kernel.txt`

The operation-shape matrix keeps the same target-wrapper-shaped request,
`buffer_count=5`, first word `0x2713`, and opcode `XTENSA_ANN_VERSION`, but
varies the operation input/output counts plus the matching operand list:

| Case | Inputs | Outputs | Operand list |
|---|---:|---:|---|
| `ann_version_counts_0_0` | 0 | 0 | `[]` |
| `ann_version_counts_0_1_out0` | 0 | 1 | `[0]` |
| `ann_version_counts_0_2_out0_1` | 0 | 2 | `[0,1]` |
| `ann_version_counts_1_0_in0` | 1 | 0 | `[0]` |
| `ann_version_counts_1_1_in0_out1` | 1 | 1 | `[0,1]` |
| `ann_version_counts_2_1_in0_1_out2` | 2 | 1 | `[0,1,2]` |

No-dispatch controls preserve every requested count and operand-list word. Two
dispatch runs return `0` from `run_cmd_async` and explicit wait for every case,
write `0x2713 -> 0x271b` in every case, preserve the requested input/output
counts, and leave APUNN settings/output/data unchanged.

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_op_shape_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_op_shape_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_op_shape_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_op_shape_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_op_shape_matrix_repeat.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_op_shape_matrix_repeat_kernel.txt`

The target-wrapper opcode matrix keeps the same target-wrapper-shaped request,
`buffer_count=5`, and one output operand `[0]`, but varies the first operation
opcode through the recovered internal APUNN opcode range `10001..10009`.
This is the first target-wrapper/no-settings matrix that clearly changes
firmware behavior by opcode class:

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

No-dispatch controls preserve every requested opcode. Both dispatch runs leave
APUNN settings/output/data unchanged for every opcode. Kernel logs contain
D2D_EXT timeout and `mdw_wait_cmd` failure lines for the `10005..10007`
wait-failure cases.

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_opcode_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_opcode_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_opcode_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_opcode_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_opcode_matrix_repeat.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_opcode_matrix_repeat_kernel.txt`

The target-wrapper descriptor-layout matrix keeps opcode `10003`, wrapper
send-state flags, one output operand, one data descriptor, `buffer_count=5`,
and no `request+0x38/+0x40` settings property, then varies the five copied
native VPU descriptors:

| Layout | Descriptor slots | Wait result | Visible result |
|---|---|---|---|
| `libvpu_metadata_code5` | code, code, code, code, code | `0` | code word `0x2713 -> 0x271b`; output/data unchanged |
| `libvpu_metadata_mixed5` | code, output, data descriptor, data payload, plane payload | `0` | code word `0x2713 -> 0x271b`; output/data unchanged |
| `libvpu_metadata_mixed5_output_first` | output, code, data descriptor, data payload, plane payload | `-EIO` | output word `0xffffffff -> 0xfffffffd`; code/data unchanged |

The no-dispatch control preserves every window. The repeat dispatch run
reproduces the same three outcomes. This confirms that the visible imported
buffer write follows copied native VPU descriptor slot `0`, while merely
placing output/data/data-descriptor buffers into later descriptor slots does not
make APUNN consume the normal output/data contract.

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_descriptor_layout_matrix_fixed_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_descriptor_layout_matrix_fixed_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_descriptor_layout_matrix_fixed.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_descriptor_layout_matrix_fixed_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_descriptor_layout_matrix_fixed_repeat.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_descriptor_layout_matrix_fixed_repeat_kernel.txt`

The output-first opcode matrix fixes descriptor slot `0` at output and slot `1`
at code/input, then repeats the `10001..10009` opcode set:

| Opcode | Label | Wait result | Code word result | Output word result |
|---:|---|---|---|---|
| `10001` / `0x2711` | `GET_ALGO_INFO` | `-EIO` | unchanged `0x2711` | `0xffffffff -> 0xfffffffd` |
| `10002` / `0x2712` | `LOCAL_MEM_INFO` | `-EIO` | unchanged `0x2712` | `0xffffffff -> 0xfffffffd` |
| `10003` / `0x2713` | `XTENSA_ANN_VERSION` | `-EIO` | unchanged `0x2713` | `0xffffffff -> 0xfffffffd` |
| `10004` / `0x2714` | `GET_DETAILED_OP_INFO` | `-EIO` | unchanged `0x2714` | `0xffffffff -> 0xfffffffd` |
| `10005` / `0x2715` | `UNKNOWN_10005` | `-EIO` | unchanged `0x2715` | `0xffffffff -> 0xfffffffd` |
| `10006` / `0x2716` | `APU_LIB_APUNN_10006` | `-EIO` | unchanged `0x2716` | `0xffffffff -> 0xfffffffd` |
| `10007` / `0x2717` | `APU_LIB_CUSTOM_10007` | `-EIO` | unchanged `0x2717` | `0xffffffff -> 0xfffffffd` |
| `10008` / `0x2718` | `APUNN_DYNAMIC_10008` | `-EIO` | unchanged `0x2718` | `0xffffffff -> 0xfffffffd` |
| `10009` / `0x2719` | `CUSTOM_DYNAMIC_10009` | `-EIO` | unchanged `0x2719` | `0xffffffff -> 0xfffffffd` |

The repeat dispatch run reproduces the same table. In the code-first opcode
matrix, opcode classes select different code-window status behavior. In the
output-first opcode matrix, every opcode converges to the same output-slot
failure status, and the code window is not rewritten. This makes descriptor
slot `0` the active status/writeback target in the current incomplete path; the
opcode parser's normal status classes are only visible when slot `0` points at
the code/input operation window.

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_output_first_opcode_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_output_first_opcode_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_output_first_opcode_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_output_first_opcode_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_output_first_opcode_matrix_repeat.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_output_first_opcode_matrix_repeat_kernel.txt`

## Outer APUSYS codebuf-size matrix

The 2026-06-15 `--run-cmd-vpu-xrp-target-code5-no-settings-codebuf-size-matrix-iova`
run varies only the APUSYS subcommand `cb_info_size` (`sc+0x20` in the printed
probe output, stored by the kernel at `sc+0x28`). The command memory still
contains the full `0xb70` target-wrapper-shaped request, so this isolates the
midware/provider copy size from the request contents.

| Outer `cb_info_size` | Dispatch wait | Code/input word | Request-tail copyback | Interpretation |
|---:|---|---|---|---|
| `0x20` | `-EIO` | unchanged `0x2713` | none | Too short; rejected before visible provider state write |
| `0x90` | `-EIO` | unchanged `0x2713` | none | Too short; `vpu_req_check` logs invalid request size |
| `0x1c8` | `-EIO` | unchanged `0x2713` | none | Too short; `vpu_req_check` logs invalid request size |
| `0xb6c` | `-EIO` | unchanged `0x2713` | none | Covers `request+0xb68`, but still not an accepted request |
| `0xb70` | `0` | `0x2713 -> 0x271b` | nonzero word at tail `+0x20` | Exact accepted request size |
| `0xb80` | `-EIO` | unchanged `0x2713` | none | Oversize request rejected |

The repeat run reproduces the same boundary. The exact `0xb70` case writes a
different nonzero tail word in each run (`0xb3980`, then `0xbda8f`), which is
consistent with a post-provider copyback value rather than a fixed userland
constant. The no-dispatch control leaves all command/request windows unchanged.
This means the observed request-tail deltas are mediated by
`mdw_cmd_sc_clr_hnd`, while the native descriptor first-word deltas require both
an accepted exact-size request and provider/firmware execution.

Result files:

- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_codebuf_size_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_codebuf_size_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_codebuf_size_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_codebuf_size_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_codebuf_size_matrix_repeat.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_target_code5_no_settings_codebuf_size_matrix_repeat_kernel.txt`

This rules out the presence of a direct settings-property tuple as the cause of
the code-first diagnostic boundary. The code5 no-settings request is accepted by
APUSYS/VPU and can be waited successfully when at least one native descriptor is
advertised, but it remains a descriptor-0 status/writeback shape. The later
settings5 no-settings replay shows the completion condition is the native
descriptor target: descriptor slot `0` must point at the wrapper DSP
command/settings buffer. The next unresolved field is therefore not the outer
APUSYS `cb_info_size`, ordinary VPU descriptor metadata,
nonzero descriptor count, `request+0x38/+0x40` presence, native descriptor
payload size, submitted `request+0xb68`, descriptor port/format/plane-count bytes,
descriptor height, output operand id, tested input/output count combinations,
basic `0x1c8` count routing, simple five-descriptor role ordering, or pairing
output slot `0` with all recovered internal opcodes. The opcode word is parsed
and does select different APUNN firmware-side status/timeout classes, but those
classes depend on the descriptor slot `0` target and still do not produce the
normal completion/output contract. The remaining gap is the standard wrapper's
code/output/data buffer contents, command flags, or output-header semantics that
make APUNN signal done and write to the settings output section.

## Command flags and completion state

Target-side `libneuron_platform.vpu.so` adds wrapper state transitions that the
direct ioctl probe originally skipped. `XrpCommandInfo::Initialize()` writes
settings `+0x00 == 0x4`; `XrpIntrinsicExecutor::CreateXrpCommand()` then sets
bit `0x1`, making the standard pre-request value `0x5`.
`XrpIntrinsicExecutor::SendRequest()` at `0x10044` clears bit `0x2`, sets bit
`0x1`, and then calls `XrpVpuStream::RunRequest()`. For the current direct
probe shape this keeps the pre-dispatch flags at `0x5`.

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

The 2026-06-14 flags matrix then tested whether pre-seeding the host-side
completion bits changes the same wrapper-data request. It used the
`settings_len=0x68`, code-first two-buffer, libvpu-descriptor,
one-data-descriptor shape and varied only settings `+0x00`:

| Initial settings flags | Wrapper predicate before dispatch | Dispatch result |
|---:|---|---|
| `0x4` | false | `run_async_vpu_iova ret=0`; settings stay `0x4`; code word `0` changes `0x2713 -> 0x271b`; output/data unchanged |
| `0x2` | true | `run_async_vpu_iova ret=0`; settings stay `0x2`; code word `0` changes `0x2713 -> 0x271b`; output/data unchanged |
| `0x3` | true | `run_async_vpu_iova ret=0`; settings stay `0x3`; code word `0` changes `0x2713 -> 0x271b`; output/data unchanged |
| `0x5` | false | `run_async_vpu_iova ret=0`; settings stay `0x5`; code word `0` changes `0x2713 -> 0x271b`; output/data unchanged |
| `0x0d` | false | `run_async_vpu_iova ret=0`; settings stay `0x0d`; code word `0` changes `0x2713 -> 0x271b`; output/data unchanged |

The no-dispatch control preserves the original code word `0x2713` for all five
flag values. The dispatch kernel log shows VPU boot activity and residual
command cleanup, but no captured APUNN output completion. Therefore
`settings[0]` is not a standalone firmware trigger in this direct-request
shape: pre-seeding the wrapper's completion predicate makes the host-side oracle
true in memory, but the firmware path neither consumes it as completion nor
overwrites it into a new output state.

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

The
`--run-cmd-vpu-xrp-internal-ann-version-iova-libvpu-desc-send-flags-wrapper-data`
variant keeps the `settings68` request shape, wrapper send-state flags, and
code-first native descriptor order, but restores the standard one-entry data
descriptor section: settings `+0x0c = 0x0c`, settings `+0x30 = data_desc_iova`,
and descriptor `{type=3, size=0x80, iova_low32=data_payload_iova}`. The
no-dispatch control leaves settings/code/output/data windows unchanged. The
dispatch run returns `0`, logs VPU map/boot activity, keeps settings at `0x5`,
leaves output, the data descriptor, and the data payload unchanged, and changes
code/input word `0` from `0x2713` to `0x271b`. Restoring one ordinary APUNN
data descriptor is therefore not the missing completion/output condition.

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
| `cXrpOptions+0x10` | Device wrapper treats this qword as an existing `apusys_session*`; `XrpVpuStream::CreateVpuInstance()` returns status `4` when it is zero |
| `XrpIntrinsicExecutor::InitDriver()` | Creates the VPU stream instance first with name `vpu_xrp`; failure returns a nonzero XRP status before memory-manager setup |
| `XrpIntrinsicExecutor::CreateXrpCommand()` | Allocates a `0x68` `xrp_dsp_cmd`/DSP command buffer through the memory manager before command initialization |
| `XrpVpuStream::kAlgoNames` | The static table contains `apu_lib_apunn` and `apu_lib_custom`; `InitDriver()` tries custom when `debug.vpu.custom.algo.support` is set, otherwise/fallback APUNN |

The `cXrpOptions+0x10` interpretation is now also confirmed from the direct
VPU stack. `/system/lib64/libapu_mdw.so::apusysSession_createInstance()` first
opens `/dev/apusys`; if that open fails it logs the errno path and returns
null. On success it allocates a `0xe8` byte `apusysSession` object. The session
stores the APUSYS fd at object `+0x00`, the midware version at `+0x08`, and the
selected executor object at `+0xe0`. Version `2` uses
`apusysExecutor_v2`, whose constructor issues ioctl `0xc0284120` to enumerate
APUSYS device metadata and memory-info records.

`/system/lib64/libvpu5.so::createInstanceImp(char const*, apusys_session*)`
accepts either a caller-supplied `apusysSession*` or, when the second argument
is null, calls `apusysSession_createInstance()` itself. It then queries device
type `3`, obtains the metadata size through `queryInfo(1)`, requests device
metadata for type `3`, allocates a `0xe0` byte `VpuStreamImp`, and stores the
session pointer at stream `+0xc8`. The ownership flag at `+0xd0` is set only
when `createInstanceImp()` created the session itself. This makes the wrapper
requirement concrete: `libneuron_platform.vpu.so` is not asking for a generic
handle or fd at `cXrpOptions+0x10`; it is asking for a live `apusysSession`
object compatible with `libapu_mdw.so`.

The same `libvpu5.so` pass pins the native request builder used behind
`XrpVpuStream::CreateVpuRequest()`:

| Object / request field | Evidence |
|---|---|
| `VpuRequestImp+0x48` | Start of the copied `0xb70` `vpu_user_request` blob |
| `VpuRequestImp+0x7d` / request `+0x35` | Buffer count updated by `VpuRequestImp::addBuffer()` |
| `VpuRequestImp+0x80` / request `+0x38` | Settings length written by `prepareSettBuf()` |
| `VpuRequestImp+0x88` / request `+0x40` | Settings device VA/IOVA from `apusysSession_memGetInfoFromHostPtr(..., 1)` |
| `VpuRequestImp+0x98` / request `+0x50` | First native `struct vpu_buffer` descriptor slot |
| `VpuRequestImp+0xb0` / request `+0x68` | First descriptor's first plane MVA/IOVA slot after `mmapMVA()` |
| `VpuStreamImp::runReq()` | Allocates a `0xb70` APUSYS command buffer, copies `VpuRequestImp+0x48` into it before dispatch, and copies it back into `VpuRequestImp+0x48` after sync/wait |

This supports the Java request layout already used by the direct ioctl probe:
the remaining APUNN gap is not an off-by-base error in the `0xb70` request
size, settings pointer, or descriptor-array start.

The target `libneuron_platform.vpu.so` binding table also resolves the
`XrpVpuStream` function-pointer slots used by request construction:

| Slot | Resolved symbol | Use in `XrpVpuStream::CreateVpuRequest()` |
|---:|---|---|
| `stream+0x20` | `vpuStream_acquire` | Creates/acquires the libvpu request object for the selected algorithm |
| `stream+0x88` | `vpuRequest_addBuffer` | Called five consecutive times with the same stack `VpuBuffer` object |
| `stream+0x90` | `vpuRequest_setProperty` | Resolved by the constructor, but unused by `CreateVpuRequest()` |

`libvpu5.so::VpuRequestImp::addBuffer()` maps that stack `VpuBuffer` into the
firmware-visible `struct vpu_buffer` slots as follows:

| Source field | Native request field |
|---:|---:|
| `VpuBuffer+0x8c` | descriptor `+0x00` / request `+0x50` port id |
| `VpuBuffer+0x80` | descriptor `+0x01` / request `+0x51` format or direction |
| `VpuBuffer+0x00` | descriptor `+0x02` / request `+0x52` plane count |
| `VpuBuffer+0x84` | descriptor `+0x04` / request `+0x54` width/size-like field |
| `VpuBuffer+0x88` | descriptor `+0x08` / request `+0x58` height-like field |
| `VpuBuffer+0x10/+0x14` | plane `+0x00/+0x04` / request `+0x60/+0x64` |
| `mmapMVA()` result | plane `+0x08` / request `+0x68` MVA/IOVA |

For the target `CreateVpuRequest()` stack object, the static values are
`plane_count=1`, descriptor port id `1`, and descriptor format/direction `0`.
`XrpIntrinsicExecutor::Initialize()` records the main DSP command/settings
buffer in the command-info object, and
`XrpIntrinsicExecutor::CreateVpuRequest()` loads that command-info entry before
calling `XrpVpuStream::CreateVpuRequest(session, size, sharedFd, hostVa)`.
Because the unordered-map node stores the `XrpCommandInfo` at `node+0x18`, the
loads from `node+0x28`, `node+0x2c`, and `node+0x38` are the command/settings
buffer size, shared-fd-like word, and host VA. They are not code-section
fields.

The direct-ioctl consequence is that `code5` is only a useful diagnostic for
descriptor-0 status attribution. The closest replay of the ordinary target
wrapper path is `settings5/no-settings`: five repeated libvpu-style descriptors
pointing at the wrapper DSP command/settings buffer, with
`request+0x38/+0x40` cleared because the resolved `vpuRequest_setProperty()`
slot is unused by this `CreateVpuRequest()` path.

The shell-domain run without an APUSYS session is a negative control, not a
valid APUNN wrapper ABI dump:

```text
context=u:r:shell:s0
dlopen path=/system/lib64/libneuron_platform.vpu.so
create_apusys_session=0
XRP_Create status=4
XRP_Create did not initialize the wrapper; skip follow-up calls.
```

The earlier crash after continuing past status `4` is explained by the same
static path: `XRP_CreateCommand()` reaches
`XrpMemoryManager::AllocateBuffer(0x68)`, but `InitDriver()` did not create the
memory manager, so the call locks a null-object mutex at `0x10`. The corrected
inspector stops after nonzero `XRP_Create`.

The native inspector now has a `libapu_mdw.so` session path for the direct
Neuron wrapper. It loads `libapu_mdw.so`, calls
`apusysSession_createInstance()`, and writes the returned pointer to
`cXrpOptions+0x10` before `XRP_Create()`. From the shell domain, library loading
succeeds through `/system/lib64/libapu_mdw.so`, but session creation returns
null, so the wrapper still returns status `4`:

```text
context=u:r:shell:s0
dlopen apusys path=/system/lib64/libapu_mdw.so
apusysSession_createInstance session=0x0
XRP_Create status=4
```

The `system_app` `app_process64` route now works as a second negative control.
`poc/XrpWrapperInspect.java` loads the already-installed
`/system/lib64/libneuron_platform.vpu.so`, resolves exported function addresses
from ELF metadata plus `/proc/self/maps`, and calls them through the
`DrmTrigger` ART native-call stub. The stub self-test calls libc `getpid()` and
returns the expected process id, proving that the function-call primitive is not
the blocking point:

```text
uid=1000(system) context=u:r:system_app:s0
native getpid()=12021
native dlopen path=/system/lib64/libapu_mdw.so handle=0x0
native dlopen path=/system/vendor/lib64/libapu_mdw.so handle=0x0
native dlopen path=/vendor/lib64/libapu_mdw.so handle=0x0
XRP_Create status=4 device=0xb400006d90f50690
XRP_Create did not initialize; stop.
```

The Java inspector now also tries two non-dlopen fallbacks before the native
`dlopen()` control: `System.load()` of `libapu_mdw.so`, then `System.load()` of
`libvpu5.so` followed by `/proc/self/maps` lookup for an already-mapped
`libapu_mdw.so`. On this device both `System.load()` paths fail in the
`system_app` classloader namespace because `libvndksupport.so` needs or dlopens
`libdl_android.so`, which is not namespace-accessible; no mapped
`libapu_mdw.so` is found, and native `dlopen()` remains null. The exact result
file is:

- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_java_neuron_maps_session_system_app.txt`

This keeps the wrapper-generated request comparison open, but narrows the
missing condition. Direct `libneuron_platform.vpu.so` wrapper initialization
needs a real `libapu_mdw` `apusys_session*`. Shell can load `libapu_mdw.so` but
does not get a session; `app_process64` can call installed wrapper functions but
cannot load or map `libapu_mdw.so` from its linker namespace. A useful positive
dump therefore needs one of: a process context where `libapu_mdw` creates a
session, a hook point inside an already successful Neuron/VPU client after it
has a session, or a service-side APUWARE prepared-request dump.

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

Static inspection of `/system/system_ext/lib64/libapuwarexrp_v2.mtk.so`
narrows what this HIDL wrapper actually forwards. `XRP_UseInputBuffer()`
at `0x8ad0`, `XRP_UseOutputBuffer()` at `0x9070`, and
`XRP_FinalizeCommand()` at `0x93b0` all convert the public 0x30-byte
`cXrpBufferInfo` into the same 0x38-byte `XRP_cXrpBufferInfo_HD` shape:

| HIDL record offset | Source `cXrpBufferInfo` field | Meaning |
|---:|---:|---|
| `+0x00` | `+0x00` u32 | Buffer kind/type |
| `+0x08` | `+0x08` qword | Service buffer id or caller-provided output slot value |
| `+0x10` | `+0x10/+0x14` u32 pair | Requested size and mapped size |
| `+0x18` | `+0x18` fd, duplicated into a native handle when nonnegative | Shared buffer fd |
| `+0x28` | `+0x1c` u32 | Buffer offset |
| `+0x30` | `+0x20` qword | Low IOVA/PA-style address returned by the service |

The host VA at `cXrpBufferInfo+0x28` is not copied into the HIDL record when a
valid fd is present. In `UseInputBuffer()` / `UseOutputBuffer()`, that host VA
is only used on the `fd == -1` fallback path: the wrapper looks up the buffer id
from `+0x08`, uses size `+0x10`, and writes the host memory payload through the
HIDL FMQ before calling the service method. The service-allocated positive path
therefore forwards fd, size, offset, id, and IOVA-style fields, not the local
host VA, toward request preparation.

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
calling `XRP_FinalizeCommand()`, `--dlopen-lazy` for isolating loader behavior,
and `--dlopen-timeout-sec=N` for cutting off constructor-level loader hangs.
Current reruns cannot yet validate the slot-index hypothesis because this
device state stops inside `dlopen(libapuwarexrp_v2.mtk.so)` before the helper
reaches any wrapper API. The 2026-06-15 in-process timeout run shows the exact
boundary:

```text
mode=apuware handle=1 finalize_count=1 sync=0 finalize_index_mode=slot dlopen=now create_apusys_session=0 dlopen_timeout_sec=8
dlopen begin xrp path=/system/system_ext/lib64/libapuwarexrp_v2.mtk.so timeout_sec=8
dlopen timeout
STATUS:124
```

Static ELF inspection explains why `dlopen()` itself can block. The dynamic
section has `.init_array` at `0xe2f8` with one entry, `0xccf0`; that initializer
allocates and constructs a global `XrpIntrinsicExecutor`, stores it in the
global executor pointer, and registers the destructor. The constructor at
`0x7100` calls
`vendor.mediatek.hardware.apuware.xrp@2.0::INeuronXrp::tryGetService("default",
false)` and retries after `usleep(5000)` while logging
`can't get INeuronXrp2 service tried(%d) times`. The only kernel-side clue in
the timeout rerun is a binder ioctl returning `-EINVAL` from the helper process.
This moves the current APUWARE blocker before finalize and before firmware
request parsing.

The Java `app_process64` inspector now has a separate Neuron-wrapper negative
control. With `--no-create-apusys-session`, `XRP_Create()` returns status `4`
and logs the same initialization reason as the native path: `libvpu5.so does not
exist`, `null apusys session`, and failure to create the VPU stream instance.
It still stores a nonzero device pointer, but forcing execution past that status
with `--force-after-create --skip-finalize --force-get-prepared` crashes on the
next exported wrapper call. The saved repeat logcat backtrace is
`XrpMemoryManager::AllocateBuffer()` ->
`XrpIntrinsicExecutor::CreateXrpCommand()` ->
`XrpIntrinsicWrapper::CreateCommand()` -> `XRP_CreateCommand`, with a
null-pointer fault at `0x10`. That makes the partial device pointer unsuitable
for dumping prepared requests: the command map / memory-manager side of the
wrapper has not been initialized.

The `--create-apusys-session` Java rerun confirms why `system_app` cannot build
the positive wrapper request today. `System.load()` of `libapu_mdw.so` and
`libvpu5.so` fails in the app_process classloader namespace because
`libvndksupport.so` needs inaccessible `libdl_android.so`; direct `dlopen()`
through the ART native-call stub returns `0` for the same candidate
`libapu_mdw.so` paths. This leaves `cXrpOptions+0x10` at zero and keeps
`XRP_Create status=4`. Therefore the missing positive `XRP_GetPreparedRequests`
dump requires either a process that already owns a valid `apusys_session`, a
linker namespace that can load `libapu_mdw/libvpu5`, or a hook inside an
initialized wrapper process.

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
- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_apuware_finalize_matrix_rerun.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_xrp_wrapper_inspect_apuware_dlopen_lazy_rerun.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_xrp_wrapper_inspect_apuware_dlopen_timeout.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_xrp_wrapper_inspect_apuware_dlopen_timeout_logcat.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_xrp_wrapper_inspect_neuron_no_session_java.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_xrp_wrapper_inspect_neuron_no_session_java_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_xrp_wrapper_inspect_neuron_no_session_force_skip_finalize_java.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_xrp_wrapper_inspect_neuron_no_session_force_skip_finalize_java_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_xrp_wrapper_inspect_neuron_no_session_force_skip_finalize_java_repeat.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_xrp_wrapper_inspect_neuron_no_session_force_skip_finalize_java_repeat_kernel.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_xrp_wrapper_inspect_neuron_no_session_force_skip_finalize_java_repeat_logcat.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_xrp_wrapper_inspect_neuron_create_session_java.txt`
- `poc-run-results/2026-06-15-batch/13_apusys_xrp_wrapper_inspect_neuron_create_session_java_kernel.txt`

## Firmware-visible request model

The readable `apu_lib_apunn` artifact is still not available from this device
state. The firmware-side model can still be pinned down to the last kernel/user
boundary: the wrapper builds the XRP settings and native VPU request, `mdw`
copies the request into kernel memory, and the VPU driver translates it into a
D2D/D2D_EXT register tuple plus a copied buffer-descriptor array.
Device-side file searches under `/vendor/firmware`, `/system_ext`, `/odm`, and
the pulled APU/Neuron artifacts currently expose wrapper libraries and service
libraries, but not a standalone `apu_lib_apunn` firmware image.

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

If the caller supplies `request+0x28` bit `2` up front, the provider dispatches
`vpu_execute_with_slot()` instead of `vpu_execute()` directly. That helper
allocates a slot, stores it at `request+0xb68`, and then calls `vpu_execute()`.
Inside `vpu_execute()`, the same bit selects the Preload algorithm set for the
first lookup. This makes bit `2` a kernel execution/lifetime selector as well
as the switch that lets `vpu_execute_with_slot()` put an internal slot id into
the D2D_EXT firmware tuple.

`vpu_execute_d2d_handoff()` is the kernel-to-firmware handoff. Its first step is
to copy `request+0x50` into the clamped-slot VPU command buffer:

| IDA address | Operation |
|---|---|
| `0xffffffc0087a5bd0` | Reads `request+0x35` as `buffer_count` |
| `0xffffffc0087a5bdc` | Uses `request+0x50` as the source descriptor array |
| `vpu_copy_req_buffers_to_d2d` / `0xffffffc0087a20e8` | Rejects copies larger than `0x2000`, then copies `buffer_count * 0x40` bytes |
| `vpu_copy_req_buffers_to_d2d_inner` / `0xffffffc0087a20f8` | Destination is the clamped-priority D2D buffer at `core + slot * 0xb0 + 0x3f8` |
| `vpu_get_d2d_buffer_array_iova` / `0xffffffc0087a20c8` | Returns the copied descriptor-array IOVA from `core + slot * 0xb0 + 0x400` |

The slot value used in those helpers is bounded by
`vpu_get_preload_entry_for_priority()` at `0xffffffc0087a1f40`: values above
`2` use slot `2`, negative values become `0`, and the selected Preload object is
loaded from `core + slot * 0xb0 + 0x3c8`. This object is kernel-owned firmware
metadata, not the original user request. The default provider path clears
`request+0xb68` to `0`; the explicit Preload/slot path writes the allocator
result there before execution.

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
| `+0x27c` | executed slot value from `request+0xb68` after provider rewrite |
| `+0x290` | sum of preload object fields `+0xfb0` and `+0xfb8` |
| `+0x29c` | preload object `+0xfc0` |

Those preload fields come from `vpu_init_dev_algo_sets()`. On a Preload lookup
miss, the driver copies the firmware entry key/name from the metadata pointer
`X26` and stores allocated preload state in the algorithm object: `+0xfb0` is
the program/entry allocation returned by `vpu_preload_iova_alloc()`, `+0xfb8`
is the entry adjustment computed from metadata fields at `X26-0x18` and
`X26-0x02`, and `+0xfc0` is populated by a separate preload allocation path
when the lookup hits an existing algorithm object and the entry metadata permits
another allocation. The exact firmware-side semantic of `+0xfc0` remains a
preload input, but the dispatch boundary is fixed: firmware receives only the
resulting register values and copied descriptor/settings IOVAs.

The trigger sequence then clears a status bit at MMIO `+0x910`, sets bit `0` at
MMIO `+0x204`, waits for completion through `vpu_wait_d2d_completion()`, maps
the firmware status word through `vpu_map_d2d_status_to_errno()`, and copies the
driver-side algorithm return into `request+0xb6c` through
`vpu_get_d2d_algo_ret()`. The status map is concrete: firmware status `1` or
`2` maps to success, `0` or `8` maps to `-EIO`, `3`, `5..7`, and `9..15` map to
`-EINVAL`, `4` maps to `-EBUSY`, and `16` or `0xff` maps to an `-EBADFD`-class
error.

This gives the current interpretation boundary:

- `apu_lib_apunn` sees a copied descriptor array, not the original userland
  `struct vpu_request`.
- The default execution path clears the user-supplied `request+0xb68`; firmware
  sees slot `0` unless the explicit Preload/slot path writes an internal slot id.
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
- The `wrapper_one_data` result keeps `settings_len=0x68`, wrapper send-state
  flags, wrapper-default output size `0x40`, and code-first native descriptors,
  but restores one standard APUNN data descriptor. Dispatch still returns `0`,
  settings remain `0x5`, output/data descriptor/data payload remain unchanged,
  and code word `0` changes `0x2713 -> 0x271b`. A single ordinary data
  descriptor is also not the missing APUNN completion condition.
- The `flags_matrix` result varies only settings `+0x00` across `0x4`, `0x2`,
  `0x3`, `0x5`, and `0x0d` on the same wrapper-one-data request. Even the
  pre-seeded completion states `0x2` and `0x3` are preserved unchanged after
  dispatch, while output/data stay unchanged and code word `0` still changes
  `0x2713 -> 0x271b`. The settings flags word is therefore not a standalone
  APUNN completion trigger in this direct request shape.
- Static wrapper analysis of the target `XrpVpuStream::CreateVpuRequest()` shows
  this request builder adds the same native VPU buffer descriptor five
  times. The `wrapper_one_data_code5` result keeps `settings_len=0x68`,
  wrapper send-state flags, wrapper-default output size `0x40`, and one
  standard APUNN data descriptor, but sets `buffer_count=5` with all five
  copied native descriptors pointing at the code/input window. Dispatch still
  returns `0`, settings remain `0x5`, output/data descriptor/data payload
  remain unchanged, and code word `0` changes `0x2713 -> 0x271b`. Repeating the
  code/input native descriptor five times is therefore not the missing APUNN
  completion condition.
- The `target_code5_no_settings` result keeps those five code/input native
  descriptors but clears the native settings tuple (`request+0x38/+0x40 = 0`),
  matching the target `CreateVpuRequest()` no-property path. Dispatch still
  returns `0`, settings/output/data remain
  unchanged, and code word `0` changes `0x2713 -> 0x271b`. The explicit wait
  variant returns `0` from `mdw_usr_wait_cmd`, consumes the command, and still
  leaves APUNN settings/output/data unchanged. The settings-property tuple is
  therefore not required for the descriptor-0 writeback and is not the missing
  APUNN completion condition.
- The `target_settings5_no_settings` result is the closer direct replay of the
  target wrapper: all five native descriptors point at the DSP
  command/settings buffer at base `+0`, descriptor size `0x68`, and the native
  settings tuple is cleared. Dispatch returns `0`, the explicit wait returns
  `0`, and the no-dispatch control leaves all APUNN windows unchanged. The
  dispatch run changes settings flags `0x5 -> 0x7`, satisfying
  `(settings[0] & 0x0a) == 0x02`, clears the standard data-descriptor pointer
  at settings `+0x30`, leaves the code/input window unchanged, and fills output
  words through offset `0x40` with `0xffffffff`. This proves the normal
  APUNN settings/output completion contract for the tested
  `XTENSA_ANN_VERSION` request shape. It does not prove a source-sensitive
  leak or timeout lifetime misuse.
- The `target_settings5_no_settings_opcode_matrix` result keeps that completed
  settings-backed shape and varies the first operation opcode through
  `10001..10009`. Every dispatch case returns `0`, every wait returns `0`,
  every case changes settings `0x5 -> 0x7`, clears settings `+0x30`, and leaves
  the code/opcode window unchanged. The no-dispatch control preserves the
  initialized settings/output state for every opcode. Output is deterministic:
  `10001`, `10002`, `10003`, `10005`, `10006`, `10007`, `10008`, and `10009`
  write `0xffffffff` through output offset `0x40`, while `10004` writes through
  offset `0x3c` and leaves offset `0x40` at zero. Therefore the earlier
  code-first `10005..10007` timeout/error behavior is not the completed opcode
  contract; it is a consequence of pointing descriptor slot `0` at the wrong
  buffer.
- The `target_settings5_no_settings_operand_id_matrix` result keeps the
  completed settings-backed shape, keeps opcode `10003`, and varies the single
  output operand id through `0`, `1`, `2`, `3`, and `0xffff`. Every dispatch
  case returns `0`, every wait returns `0`, every case changes settings
  `0x5 -> 0x7`, clears settings `+0x30`, and leaves the code/operand-list words
  unchanged. Data descriptor and data payload windows stay unchanged. Output
  tail length is not stable per operand id across the dispatch and repeat runs,
  so operand id is accepted metadata but not yet a stable output-semantics
  selector.
- The `target_settings5_no_settings_operand_offset_matrix` result keeps the
  completed settings-backed shape, keeps opcode `10003`, and varies the
  operand-list offset through `0`, `0x10`, `0x40`, `0x100`, `0x17e`, and
  `0x180`. The no-dispatch control preserves the requested offset words. Every
  dispatch and repeat case returns `0`, changes settings `0x5 -> 0x7`, clears
  settings `+0x30`, leaves the code window unchanged, and writes output through
  offset `0x40`. Offset `0x180` places the operand just outside the advertised
  `0x1c8` operation entry and still completes, so this field is not a visible
  completion or bounds gate for this opcode/oracle.
- The `target_settings5_no_settings_op_shape_matrix` result keeps the completed
  settings-backed shape, keeps opcode `10003`, and varies input/output counts
  across `0/0`, `0/1`, `0/2`, `1/0`, `1/1`, and `2/1` with matching operand
  lists. Every dispatch case returns `0`, every wait returns `0`, every case
  changes settings `0x5 -> 0x7`, clears settings `+0x30`, and leaves the code
  window unchanged. Data descriptor and data payload windows stay unchanged.
  Output tail length varies across repeats, so these tested counts do not gate
  completion and do not yet explain output/data binding.
- The `target_settings5_no_settings_code_size_matrix` family keeps the completed
  settings-backed shape, keeps opcode `10003`, and varies settings `+0x04`.
  The no-dispatch controls preserve every initialized size. Sizes `0`, `4`,
  `8`, `0xc`, and `0x10` fail with wait `-EIO`, keep settings at `0x5`,
  preserve `settings+0x30`, leave output unchanged, and set native request
  `result_status=0x2`. Size `0x11` is the smallest tested value that reaches
  APUNN settings/output completion. Clean/success-only reruns complete
  `0x11/0x14/0x1c/0x20/0x40/0x48/0x4c/0x1c9` twice. Full range runs show a
  batch-state caveat after the failing prefix: some later valid sizes can return
  wait success and native `result_status=0` while settings/output remain
  unchanged.
- The `target_settings5_no_settings_output_shape_matrix` result keeps the
  completed settings-backed shape and varies `settings+0x08` / output header
  `+0x0c` across `0`, `4`, `0x10`, `0x3c`, `0x40`, `0x44`, and `0x80`, with
  additional output header `+0x10 = 1` cases for `0x40` and `0x44`. Every
  dispatch case returns `0`, every wait returns `0`, every case changes
  settings `0x5 -> 0x7`, and standard data descriptor size `0x0c` still clears
  `settings+0x30`. Small output sizes leave initialized header words intact.
  Larger output sizes show `settings+0x08` is the maximum output-fill bound,
  although repeat-time tail skips still occur.
- The `target_settings5_no_settings_data_desc_matrix` result keeps the completed
  settings-backed shape and varies `settings+0x0c/+0x30`. Every dispatch case
  returns `0`, every wait returns `0`, and output is written. A null descriptor
  pointer remains null. A non-null pointer with `data_desc_size=0` is preserved.
  The standard one-entry size `0x0c` clears `settings+0x30` and leaves the
  descriptor entry unchanged. Larger sizes `0x18` and `0x80` leave
  `settings+0x30` nonzero and clear descriptor word `0` while preserving the
  descriptor's size and IOVA words.
- The `target_settings5_no_settings_data_entry_matrix` result keeps the
  completed settings-backed shape and explicitly fills descriptor entries.
  Single-entry flags `1`, `2`, and `3`, plus a flags-`3` entry with size `0`,
  all complete, clear `settings+0x30`, and leave the descriptor entry
  unchanged. Two-entry descriptors also complete but keep `settings+0x30`
  nonzero. If entry `0` points at the APUNN data-payload window and entry `1`
  points at the plane window, firmware clears both entry flags while preserving
  size/IOVA words; reversing the order leaves both entries unchanged. The data
  and plane payload bytes remain unchanged. This makes the two-entry cleanup
  target/order-sensitive rather than a pure descriptor-size side effect.
- The `target_settings5_no_settings_data_target_matrix` result keeps the
  completed settings-backed shape and points descriptor entries at settings,
  code, output, the descriptor section itself, the APUNN data payload, and the
  native plane payload. Single-entry targets all complete, clear
  `settings+0x30`, and leave their entries unchanged. Two-entry
  payload/settings, settings/payload, payload/code, code/payload,
  payload/output, and output/payload pairs also complete, keep `settings+0x30`
  nonzero, and clear both entry flags while preserving size/IOVA words. The
  target windows remain unchanged outside the normal settings/output completion
  state, and the earlier plane-first exception remains target/order-specific.
- The `target_settings5_no_settings_data_payload_matrix` result keeps the
  standard data descriptor and varies the data payload word base through `0`,
  `0x41505530`, `0x5a5a0000`, and `0x7f000000`. Every dispatch case returns
  `0`, every wait returns `0`, every case changes settings `0x5 -> 0x7`, clears
  `settings+0x30`, and leaves data descriptor and data payload bytes unchanged.
  Output remains completion-style `0xffffffff` data and does not reflect the
  chosen payload pattern, so this tested shape is not source-sensitive.
- The `target_code5_no_settings_word_matrix` result keeps the same request shape
  and varies only descriptor-0 word `0`. No-dispatch controls preserve every
  input. Dispatch plus wait maps ordinary words through `old | 0xb`, while the
  all-ones word maps `0xffffffff -> 0xfffffffd` and returns `-EIO` from wait
  with a VPU scheduler timeout in the kernel log. Settings/output/data remain
  unchanged. The visible descriptor-0 writeback therefore behaves like a
  firmware/worker state word, not an APUNN output copyback.
- The `target_code5_no_settings_size_matrix` result keeps the same request shape
  and first word `0x2713`, but varies native descriptor `width`/`stride`/`length`
  through `0,1,2,3,4,5,6,7,8,9,0xc,0x10,0x20,0x40,0x1c8`. No-dispatch controls
  preserve `0x2713`, and dispatch plus wait returns `0` for every size. The
  first dispatch run missed visible descriptor-0 writeback for `7,9,0xc,0x10`,
  but a repeat run wrote `0x271b` for every tested size, including advertised
  size `0`. Native descriptor payload size is therefore not a hard acceptance or
  bounds gate for the current state-word writeback, and the occasional no-write
  result keeps this signal unsuitable as a normal APUNN completion oracle.
- The `target_code5_no_settings_priority_matrix` result keeps the same request
  shape and first word `0x2713`, but varies request `+0xb68` through
  `0,1,2,3,0xffffffff`. No-dispatch controls preserve the requested value in the
  command-buffer tail. Dispatch plus wait returns `0` for every submitted value;
  the first dispatch missed visible descriptor-0 writeback for `1` and `2`, but a
  repeat run wrote `0x271b` for every tested value. The command-buffer copyback
  clears tail word `40` after dispatch because the default opcode-4 provider
  rewrites `request+0xb68` to slot `0` before execution. This confirms the
  submitted word is not the APUNN completion/output condition; the firmware slot
  input on this path is kernel-selected.
- The `target_code5_no_settings_buffer_count_matrix` result keeps the same
  five-descriptor/no-settings request shape, but varies request `+0x35` through
  `0,1,2,3,4,5,0x20`. No-dispatch controls preserve `0x2713` and APUNN windows.
  Two dispatch runs show `buffer_count=0` returns `-EIO` from wait and leaves
  code word `0` unchanged, while every tested nonzero count writes
  `0x2713 -> 0x271b` and returns `0` from wait. This confirms firmware-visible
  descriptor count is a real liveness gate for descriptor-0 state writeback, but
  not the APUNN completion/output condition once nonzero.
- The `target_code5_no_settings_port_matrix` result keeps the same
  five-descriptor/no-settings request shape and `buffer_count=5`, but varies
  descriptor byte `+0x00` through `0,1,2,3,4,0xff`. No-dispatch controls preserve
  `0x2713` and APUNN windows. Dispatch plus wait returns `0` for every tested
  port; the first dispatch missed visible descriptor-0 writeback for default
  `port_id=1`, but a repeat run wrote `0x2713 -> 0x271b` for every tested value.
  Descriptor port id is therefore not a hard role/acceptance gate for this state
  writeback and is not the APUNN completion/output condition.
- The `target_code5_no_settings_format_matrix` result keeps the same
  five-descriptor/no-settings request shape and `buffer_count=5`, but varies
  descriptor byte `+0x01` through `0,1,2,3,4,0xff`. No-dispatch controls preserve
  `0x2713` and APUNN windows. Two dispatch runs write `0x2713 -> 0x271b` and
  return `0` from wait for every tested value. Descriptor format/direction is
  therefore not a hard role/acceptance gate for this state writeback and is not
  the APUNN completion/output condition.
- The `target_code5_no_settings_plane_count_matrix` result keeps the same
  five-descriptor/no-settings request shape and `buffer_count=5`, but varies
  descriptor byte `+0x02` through `0,1,2,3,4,0xff`. No-dispatch controls preserve
  `0x2713` and APUNN windows. Dispatch plus wait returns `0` for every tested
  value. The first dispatch missed descriptor-0 writeback for `4`, but the repeat
  run wrote `0x2713 -> 0x271b` for every tested value. Descriptor plane count is
  therefore not the liveness gate for this state writeback and is not the APUNN
  completion/output condition.
- The `target_code5_no_settings_height_matrix` result keeps the same
  five-descriptor/no-settings request shape and `buffer_count=5`, but varies
  descriptor word `+0x08` through `0,1,2,3,0x40,0xffffffff`. No-dispatch controls
  preserve `0x2713` and APUNN windows. Dispatch plus wait returns `0` for every
  tested value and never changes APUNN settings/output/data. The first dispatch
  writes `0x2713 -> 0x271b` for every tested height; the repeats miss visible
  state writeback for different subsets. Descriptor height is therefore accepted
  ordinary metadata, not the APUNN completion/output condition.
- The `target_code5_no_settings_operand_id_matrix` result keeps the same
  five-descriptor/no-settings request shape and `buffer_count=5`, but varies the
  single `XTENSA_ANN_VERSION` output operand id through `0,1,2,3,0xffff`.
  No-dispatch controls preserve the requested operand id and APUNN windows.
  Dispatch plus wait returns `0` for every tested value and never changes APUNN
  settings/output/data. The first dispatch writes `0x2713 -> 0x271b` for every
  tested operand id; the repeat misses visible state writeback for `1` and
  `0xffff`. The output operand id is therefore accepted operation metadata, not
  the APUNN completion/output condition.
- The `target_code5_no_settings_op_shape_matrix` result keeps the same
  five-descriptor/no-settings request shape and `buffer_count=5`, but varies the
  `XTENSA_ANN_VERSION` input/output count tuple through `0/0`, `0/1`, `0/2`,
  `1/0`, `1/1`, and `2/1` with matching operand lists. No-dispatch controls
  preserve every requested count and operand-list word. Two dispatch runs return
  `0` from wait, write `0x2713 -> 0x271b` in every case, and never change APUNN
  settings/output/data. The tested count combinations are therefore accepted
  operation metadata, not the APUNN completion/output condition.
- The `target_code5_no_settings_opcode_matrix` result keeps the same
  five-descriptor/no-settings request shape and `buffer_count=5`, but varies the
  first operation opcode through `10001..10009`. This proves the firmware parses
  the opcode word: `10005` returns `-EIO` and times out without a visible state
  write, `10006` and `10007` return `-EIO` and rewrite the first word to
  `10005`, `10004` returns `0` without visible descriptor-0 writeback, and
  `10001`, `10003`, `10008`, and `10009` consistently write `0x271b`.
  `10002` is accepted, with one no-write repeat matching the known unstable
  state-write oracle. No tested opcode changes APUNN settings/output/data.
- The `wrapper_one_data_output44` result follows the host wrapper's dynamic
  output-size formula for one `0x1c8` Xtensa operation: output size
  `0x40 + 4 * 1 = 0x44`, output header flag `1`, `settings_len=0x68`,
  wrapper send-state flags, and one standard APUNN data descriptor. Dispatch
  still returns `0`, settings remain `0x5`, output/data descriptor/data
  payload remain unchanged, and code word `0` changes `0x2713 -> 0x271b`.
  The wrapper dynamic-output size/header combination is therefore not the
  missing APUNN completion condition.
- The `preload_slot` result keeps the same wrapper-one-data request shape but
  sets native VPU `request+0x28 = 0x4`, causing the kernel to enter the
  Preload/slot path directly. Dispatch still returns `0`, settings remain
  `0x5`, output/data descriptor/data payload remain unchanged, and code word
  `0` changes `0x2713 -> 0x271b`. The command-buffer tail gains a slot-like
  copyback word (`request_tail[40] = 1`), so bit `2` changes kernel
  lifetime/slot bookkeeping but is not the missing APUNN completion condition.
- The missing piece has moved: descriptor slot `0` pointing at the DSP
  command/settings buffer is enough for APUNN to mark settings
  `(flags & 0x0a) == 0x02` and write the output section. Remaining work is to
  map opcode/output semantics across this completed shape and to test
  service-wrapper lifetime/slot behavior directly.

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
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_wrapper_data.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_wrapper_data_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_wrapper_data_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_wrapper_data_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_wrapper_data_preload_slot.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_wrapper_data_preload_slot_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_wrapper_data_preload_slot_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_wrapper_data_preload_slot_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_flags_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_flags_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_flags_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_flags_matrix_control_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_operand_offset_matrix.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_operand_offset_matrix_kernel.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_operand_offset_matrix_control.txt`
- `poc-run-results/2026-06-14-batch/13_apusys_run_cmd_vpu_xrp_internal_ann_version_operand_offset_matrix_control_kernel.txt`

## Evidence map

Userland wrapper evidence:

| Binary | Function | Address | Evidence |
|---|---|---:|---|
| `libapu_mdw.so` | `apusysSession_createInstance` | `0x10ed8` | Opens `/dev/apusys`, allocates a `0xe8` `apusysSession`, returns null when open/session construction fails |
| `libapu_mdw.so` | `apusysSession::apusysSession` | `0xe014` | Stores fd at `+0x00`, midware version at `+0x08`, and executor pointer at `+0xe0`; selects executor by version |
| `libapu_mdw.so` | `apusysExecutor_v2::apusysExecutor_v2` | `0x1ce20` | Uses ioctl `0xc0284120` to collect APUSYS metadata and memory-info records for the session |
| `libapu_mdw.so` | `apusysExecutor_v2::sendUserCmd` | `0x1d8ac` | Wraps user-command buffers into ioctl `0xc0204123`, matching the kernel `mdw_usr_ucmd` path |
| `libvpu5.so` | `createInstanceImp` | `0xb070` | Accepts caller `apusysSession*` or creates one, queries device type `3`, and stores the session at stream `+0xc8` |
| `libvpu5.so` | `VpuRequestImp::VpuRequestImp` | `0x722c` | Initializes the `0xb70` native request at object `+0x48` and writes algorithm name into request `+0x04` |
| `libvpu5.so` | `VpuRequestImp::prepareSettBuf` | `0x73c4` | Allocates settings memory and writes request `+0x38/+0x40` through object `+0x80/+0x88` |
| `libvpu5.so` | `VpuRequestImp::addBuffer` | `0x7674` | Populates request `+0x35`, `+0x50`, and per-plane descriptor fields, then calls `mmapMVA()` |
| `libvpu5.so` | `VpuRequestImp::mmapMVA` | `0x78b0` | Imports dmabufs and fills request `+0x68+i*0x40+p*0x10` plane MVA/IOVA slots |
| `libvpu5.so` | `VpuRequestImp::setProperty` | `0x7bb8` | Calls `prepareSettBuf()`, copies caller property bytes into settings memory, and cache-flushes |
| `libvpu5.so` | `VpuStreamImp::runReq` | `0x9310` | Copies native `VpuRequestImp+0x48` into the `0xb70` APUSYS command buffer before dispatch and copies it back after wait/sync |
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
| `libneuron_platform.vpu.so` | `XrpVpuStream::CreateVpuRequest` | `0x16d54` | Calls `vpuStream_acquire`, then `vpuRequest_addBuffer()` five times through `stream+0x88`; leaves the resolved `vpuRequest_setProperty()` slot unused |
| `libneuron_platform.so` | `XrpCommandInfo::PrepareOutputHeader` | `0x129bc` | Writes the output header qword, result size `4`, output size, and output sync/header flag byte |
| `libneuron_platform.so` | `XrpDebugger::PrintXtensaOperations` | `0x13664` | Confirms the debug-visible Xtensa operation fields: stride at code `+0x04`, opcode at entry `+0x00`, operand-list offset at `+0x08`, input count at `+0x0c`, output count at `+0x10`, and operand ids at `entry+0x48+operand_off` |
| `libneuron_platform.so` | `XrpIntrinsic::PrepareXrpCommand` | `0x16ac0` | Standard path: binds input/code, prepares Xtensa command fields, allocates/binds output, and prepares output fields |
| `libneuron_platform.so` | `XrpIntrinsic::FinalizeXrpCommand` | `0x1789c` | Standard path: prepares/finalizes data descriptors, then creates the VPU request |
| `libneuron_platform.so` | `XrpIntrinsic::PrepareInternalCommand` | `0x1728c` | Separate exported path; no direct xref from standard prepare/finalize flow in this library |
| `libapuwarexrp_v2.mtk.so` | `XrpIntrinsicExecutor::XRP_UseInputBuffer` | `0x8ad0` | Converts public 0x30-byte `cXrpBufferInfo` into a 0x38-byte HIDL buffer-info record; uses host-VA/FMQ fallback only when fd is `-1` |
| `libapuwarexrp_v2.mtk.so` | `XrpIntrinsicExecutor::XRP_UseOutputBuffer` | `0x9070` | Same HIDL buffer-info conversion for output binding |
| `libapuwarexrp_v2.mtk.so` | `XrpIntrinsicExecutor::XRP_FinalizeCommand` | `0x93b0` | Serializes the finalize output vector into HIDL buffer-info records and forwards status from the APUWARE service |
| `libapuwarexrp_v2.mtk.so` | `XrpIntrinsicExecutor::XRP_GetPreparedRequests` | `0x9b50` | Requests prepared VPU request info from the APUWARE service after successful finalize |
| `libapuwarexrp_v2.mtk.so` | `.init_array` | `0xe2f8 -> 0xccf0` | Constructs the global `XrpIntrinsicExecutor` before `dlopen()` returns |
| `libapuwarexrp_v2.mtk.so` | `XrpIntrinsicExecutor::XrpIntrinsicExecutor` | `0x7100` | Calls `INeuronXrp::tryGetService("default", false)` during global construction; current timeout run stops in this loader/HIDL boundary |
| `libneuron_platform.so` | `XrpIntrinsicExecutor::PrepareInternalCommandBuffer` | `0x1ff48` | Separate internal path: binds first internal buffer to settings code fields and second internal buffer to settings output fields |
| `libneuron_platform.so` | `XrpIntrinsicExecutor::WritebackCommand` | `0x22660` | Host wrapper requires the same completion-flag predicate and records the first output word as command status |
| `libneuron_platform.so` | `XrpPatternDump::DumpXtensaOperations` | `0x29a0c` | Writes raw code-section bytes to `/data/local/tmp/xrp_xtensa_cmd.bin`; it does not parse fields |
| `libneuron_platform.so` | `XrpVpuStream::DefaultCreateVpuRequest` | `0x2ad64` | Creates libvpu-style descriptors with `port_id=1`, `height=1`, and `stride=size`; default path calls `addBuffer()` five times |

Kernel handoff evidence:

| Source | Function | Evidence |
|---|---|---|
| IDA `vmlinux.bin` | `mdw_cmd_parse_cmd` at `0xffffffc008790938` | Parses the 0x28-byte APUSYS subcommand header, including `cb_info_size` and `ofs_cb_info`, and records the selected code-buffer KVA/size in the subcommand object |
| IDA `vmlinux.bin` | `mdw_cmd_sc_set_hnd` at `0xffffffc00879163c` | Allocates the temporary provider buffer of `cb_info_size` bytes, copies the selected code buffer into it, and passes it to the provider as `provider_arg+0x00/+0x0c` |
| IDA `vmlinux.bin` | `mdw_cmd_sc_clr_hnd` at `0xffffffc008791ab0` | Copies the temporary provider buffer back to the original code-buffer KVA after provider return and frees the temporary allocation |
| `drivers/misc/mediatek/apusys/vpu/4.0/vpu_ioctl.h` | `struct vpu_request` | Defines `algo`, `flags`, `buffer_count`, `sett_length`, `sett_ptr`, `buffers[]`, `status`, and `algo_ret` |
| `drivers/misc/mediatek/apusys/vpu/4.0/vpu_main.c` | `vpu_req_check()` | Requires exact VPU request size, validates flags, and bounds `buffer_count` |
| `drivers/misc/mediatek/apusys/vpu/p1/vpu_hw.c` | `vpu_execute_d2d()` | Copies `req->buffers[]` into the D2D command buffer and writes `XTENSA_INFO12..15`; D2D_EXT also writes preload entry/IRAM registers |
| `drivers/misc/mediatek/apusys/vpu/p1/vpu_hw.h` | register table | Documents `DO_D2D` as INFO12 buffer count, INFO13 buffer-array pointer, INFO14 setting pointer, and INFO15 setting size |
| IDA `vmlinux.bin` | `vpu_execute` at `0xffffffc0087a7974` | Kernel execution entry that reaches the D2D/D2D_EXT request path |
| IDA `vmlinux.bin` | `vpu_execute_d2d_handoff` at `0xffffffc0087a5b74` | Copies `request+0x50` descriptors, writes MMIO `+0x280/+0x284/+0x288/+0x28c`, triggers command id `0x22/0x24`, and waits for completion |
| IDA `vmlinux.bin` | `vpu_init_d2d_command_slot` at `0xffffffc0087a1e90` | Initializes the clamped D2D slot, clears done/status/ret, stores command id, and increments the slot counter |
| IDA `vmlinux.bin` | `vpu_get_preload_entry_for_priority` at `0xffffffc0087a1f40` | Clamps slot to `0..2` and returns the selected Preload object from `core + slot * 0xb0 + 0x3c8` |
| IDA `vmlinux.bin` | `vpu_wait_d2d_completion` at `0xffffffc0087a6374` | Waits on `core + slot * 0xb0 + 0x3d0` without clamping; normal provider paths feed slot `0` or an internal allocator slot |
| IDA `vmlinux.bin` | `vpu_map_d2d_status_to_errno` at `0xffffffc0087a2040` | Reads firmware status from the clamped slot at `+0x3d4` and maps `1/2` to success, `0/8` to `-EIO`, `3/5..7/9..15` to `-EINVAL`, `4` to `-EBUSY`, and `16/0xff` to `-EBADFD` |
| IDA `vmlinux.bin` | `vpu_get_d2d_buffer_array_iova` at `0xffffffc0087a20c8` | Returns the firmware-visible copied descriptor-array IOVA from `core + slot * 0xb0 + 0x400` |
| IDA `vmlinux.bin` | `vpu_copy_req_buffers_to_d2d_inner` at `0xffffffc0087a20f8` | Copies `buffer_count * 0x40` bytes from `request+0x50` into `*(core + slot * 0xb0 + 0x3f8)` |

Runtime evidence now proves `apu_lib_apunn` lookup, normal VPU request
acceptance, exact outer `cb_info_size == 0xb70` enforcement, VPU boot/map
activity, XRP-shaped settings header tolerance, target-side nonzero
code-section tolerance for internal query/status operation shapes, controlled
descriptor-0 status writeback, firmware opcode-class behavior in incomplete
descriptor-target shapes, and APUNN settings/output completion for the
wrapper-shaped `settings5/no-settings` request. In the completed shape, all
five native descriptors point at the DSP command/settings buffer,
`request+0x38/+0x40` are clear, dispatch and wait both return `0`, settings
flags change `0x5 -> 0x7`, settings `+0x30` is cleared, and the code/input
window is unchanged. The completed-shape opcode matrix shows `10001..10009` all
complete. The completed-shape output-operand-id, operand-offset, and
operation-count matrices show operand ids `0/1/2/3/0xffff`, operand-list
offsets `0/0x10/0x40/0x100/0x17e/0x180`, and tested count combinations also
complete. The `0x180` offset places the operand just outside the advertised
operation entry and is still accepted under this completion oracle.
The completed-shape code-size matrices show `settings+0x04` is a code-section
acceptance gate: sizes `0/4/8/0xc/0x10` fail with `-EIO`, `0x11` is the smallest
tested completed size, and clean/success-only reruns complete all selected valid
sizes through `0x1c9`. Full range runs show wait success/native
`result_status=0` is not sufficient after low-size failures; settings/output
mutation is the APUNN completion oracle. Output is filled through offset `0x40`
for every tested opcode except `10004`, which fills through offset `0x3c`; the
operand/count repeats show output-tail length can vary outside the opcode axis,
so the tail is not yet a stable semantic label. The completed-shape
output-size/header matrix shows
`settings+0x08` bounds the maximum output fill. The data-descriptor matrix
shows standard `data_desc_size=0x0c` clears `settings+0x30`, while larger
descriptor sections preserve `settings+0x30` and clear descriptor word `0`.
The explicit data-entry matrix refines that larger-section behavior: single
entries with flags `1/2/3` or size `0` still complete and clear `settings+0x30`;
two-entry descriptors keep `settings+0x30` nonzero, and flags cleanup depends
on the target/order pair. The data-target matrix shows settings/code/output,
data-descriptor, data-payload, and plane-payload targets all complete without
source-sensitive target-window copies. The data-payload pattern matrix shows
tested payload contents are preserved and are not copied into output.

The earlier code-first, output-first, descriptor-size, priority, buffer-count,
port-id, format, plane-count, height, output-operand-id, operation-shape, and
opcode matrices remain useful diagnostics. They show descriptor slot `0` is the
active status/writeback target in incomplete shapes, `buffer_count=0` suppresses
that state write, and code-first `10005..10007` select timeout/error classes
with `10006/10007` normalizing to `10005`. The settings-backed matrices show
those timeout/error classes do not apply once descriptor slot `0` points at the
DSP command/settings buffer. These diagnostics do not establish a
source-sensitive leak. The completed output writeback currently looks like
completion data with some tail variability, not disclosure of unrelated
firmware or kernel memory; data payload windows remained unchanged across
payload patterns `0`, `0x41505530`, `0x5a5a0000`, and `0x7f000000`.

Timeout and teardown experiments map a real command lifetime edge, but UAF is
not demonstrated. Wait consumes the command id, async teardown can leave
residual command cleanup, and timeout/error opcodes can drive `-EIO` paths. The
missing evidence for UAF remains stale command reuse, corrupted object access,
refcount imbalance, or a crash tied to fd close/process teardown/abort timing.
The batch-level devapc warning remains non-attributed because isolated
single-case runs did not reproduce it.
