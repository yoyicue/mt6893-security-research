# APUNN firmware disassembly — handoff requirement

Date: 2026-06-15

## Why this is the blocker

The APUSYS kernel primitive investigation has reached a point where every
remaining attack path depends on firmware DMA timing or firmware-internal
parsing behavior that cannot be resolved from the kernel side alone.

Current evidence chain:

1. `system_app` can open `/dev/apusys`, dispatch VPU commands, and reach
   `apu_lib_apunn` firmware completion (settings `0x5 → 0x7`).
2. The IOVA lifetime gap is proven: `mem_free` succeeds on an in-flight
   command's shared IOVA.
3. Allocator gap-shaping can produce exact IOVA reuse at ~0.3% rate
   (`exact_target=4/640` best case, 64K `p16/r8`).
4. **Firmware always finishes writing before the Java-layer `mem_free` round-trip
   completes.** Every firmware-coupled gap-reuse probe shows `wait=0` with
   settings already at `0x7`, or `wait=-EIO` with the replacement buffer
   untouched.

The cross-buffer write primitive requires firmware to still be performing DMA
when the replacement page is mapped. Without the firmware binary we cannot:

- determine whether any opcode/operand combination produces a **slower or
  multi-phase DMA write** (the current `XTENSA_ANN_VERSION` completion is
  sub-millisecond)
- identify whether the firmware has an internal **descriptor walk loop** that
  could be extended with crafted input to increase the write window
- confirm whether the output fill, data descriptor rewrite, or settings flag
  updates are **atomic or sequenced** (sequenced writes with a gap between them
  would be raceable)
- discover additional DMA targets beyond the known settings/output/data
  descriptor windows
- find firmware-internal bugs (buffer overflow, unchecked offset, type
  confusion) that are invisible from the kernel-handoff model

## Current kernel/source/live-device alignment

The active IDA kernel image is the latest local OTA kernel image for V260523.
`vpu_init_bin` was checked in the kernel IDB at `0xffffffc0095b0d84` and matches
the reference MTK source in `/tmp/generic_kernel_mediatek_alps`:
`drivers/misc/mediatek/apusys/vpu/4.0/vpu_main.c`. The loader reads
`bin-phy-addr`, `bin-size`, `img-head`, and `pre-bin` from the
`mediatek,vpu_core0` DT node, then maps an external VPU binary blob into the
kernel. The firmware body is not embedded in `vmlinux`.

The live MT6893 target was probed from `uid=1000(system)` /
`u:r:system_app:s0`. That context can read the DT metadata but still cannot
read the block partitions or proc/debug VPU nodes.

| DT property | Value |
|---|---|
| reserved-memory `mblock-18-vpu_binary/reg` | base `0xbe510000`, size `0x018f0000` |
| `vpu_core0@19030000/bin-phy-addr` | `0xbe510000` |
| `vpu_core0@19030000/bin-size` | `0x018f0000` |
| `vpu_core0@19030000/img-head` | `0x00cb1000` |
| `vpu_core0@19030000/pre-bin` | `0x00cbf000` |

So the current device's merged VPU binary, if dumped from live physical memory,
should cover physical range `0xbe510000..0xcee00000`, with the image-header
chain at file offset `0xcb1000`.

`vpu_execute_d2d_handoff` in the kernel IDB at `0xffffffc0087a5b74` also
matches the reference source in
`drivers/misc/mediatek/apusys/vpu/p1/vpu_hw.c:vpu_execute_d2d()`: when the
request has `ALG_PRELOAD`, the driver issues `VPU_CMD_DO_D2D_EXT` (`0x24`),
writes the selected preload entry to `XTENSA_INFO16`, writes IRAM MVA to
`XTENSA_INFO19`, then passes `buffer_count`, the copied `struct vpu_buffer[]`
IOVA, and the optional settings tuple through `XTENSA_INFO12..15`.

## Current OTA artifacts

The full V260523 OTA is available on the local Synology share and has already
been used to extract the VPU partitions:

| Artifact | Path | Size / hash |
|---|---|---|
| Full OTA | `/private/tmp/nas_sync_powerctrl_23770/ota_V260523/V260523_FULL.zip` | SHA-256 `d2d1427d3ca0012cf36ece263f8ca450a572b81cf8a0e52e0af5ac195937f432` |
| `cam_vpu1` | `/tmp/ota_V260523_cam_vpu/cam_vpu1.img` | `0x3fa000`; SHA-256 `27d3a41b261211b127e51469d7f936ec8e8e3d000172661ff9ce3ef95bae2990` |
| `cam_vpu2` | `/tmp/ota_V260523_cam_vpu/cam_vpu2.img` | `0xa05000`; SHA-256 `e7a4dd68b953ee3704bf573caa422cac85424c63307851cda85bdc364dbe06a2` |
| `cam_vpu3` | `/tmp/ota_V260523_cam_vpu/cam_vpu3.img` | `0x2000`; SHA-256 `ef5f85aac5d2aeca12601547b1805f9a2b014f196fb9580cc0d5ba7b239a95a5` |

Extraction command:

```sh
mkdir -p /tmp/ota_V260523_cam_vpu
payload-dumper-go -p cam_vpu1,cam_vpu2,cam_vpu3 \
  -o /tmp/ota_V260523_cam_vpu \
  /private/tmp/nas_sync_powerctrl_23770/ota_V260523/V260523_FULL.zip
```

This follows the standard Android A/B OTA `payload.bin` path: the vendor OTA app
feeds `payload_offset`, `payload_size`, and hash metadata to
`UpdateEngine.applyPayload()`. Public Android references:
`https://source.android.com/docs/core/ota/ab` and
`https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/os/UpdateEngine.java`.
The local OTA logs also contain auth tokens and device identifiers; do not copy
raw logs into git.

## What to acquire

### Target: `apu_lib_apunn` Xtensa binary

This is a Cadence Xtensa DSP binary loaded as a VPU preload algorithm. It runs
on the MT6893 APU / VPU core. The kernel does not expose it as a file; it is
packed in the VPU binary image.

### Known facts about the binary

| Property | Value | Source |
|---|---|---|
| Algorithm key | `apu_lib_apunn` | kernel `vpu_alg_get` lookup string |
| Execution path | Preload / `D2D_EXT` (MMIO cmd `0x24`) | IDA `vpu_execute` at `0xffffffc0087a7974`: Normal miss → bit 2 → Preload retry |
| ISA | Xtensa flat binary, not ELF; strings show `Xtensa Compiler Version 12.0.10` | V260523 `cam_vpu2.img` strings |
| MMIO dispatch registers | `INFO16 = pre->a.mva + pre->a.entry_off` (firmware-facing preload target), `INFO19 = pre->a.iram_mva` (IRAM) | IDA D2D_EXT selector at `0xffffffc0087a1f40` |
| Image packing | `struct vpu_image_header` → `struct vpu_pre_info` array; each entry has `off`, `pAddr`, `mem_sz`, `file_sz` | kernel `vpu_hw.h` layout; `tools/parse_vpu_image.py` implements the parser |
| Segments | PROG (code+data, `pAddr ≠ 0xFFFFFFFF`) and IRAM (`pAddr == 0xFFFFFFFF`) | parser logic and kernel init path |
| Entry offset | `pAddr - (start_addr & 0xffff0000)` for PROG segments; for V260523 APUNN this is `0x6794` on the three main PROG windows | parser output; MTK `vpu_hw.c:vpu_init_dev_algo_preload_entry()` |
| Kernel loader | `vpu_init_bin` at `0xffffffc0095b0d84` — reads DT props `bin-phy-addr`, `bin-size`, `img-head`, `pre-bin` | IDA string xrefs |
| Live target VPU blob | PA `0xbe510000`, size `0x018f0000`, header offset `0xcb1000`, preload offset `0xcbf000` | `uid=1000(system)` DT probe |

### Acquisition paths (ranked)

#### Path 1: Local full OTA (current best path)

The Synology V260523 full OTA already contains the raw `cam_vpu*` partition
images. `apu_lib_apunn` is in `cam_vpu2.img`; `cam_vpu1.img` contains other VPU
libraries and `cam_vpu3.img` has no preload entries.

Parse `cam_vpu2.img` with the raw-partition header at `0x200`:

```sh
python3 13-apusys-ioctl-surface/tools/parse_vpu_image.py \
  /tmp/ota_V260523_cam_vpu/cam_vpu2.img \
  --head-offset 0x200 --headers 1 --algo apu_lib_apunn \
  --json /tmp/ota_V260523_cam_vpu/cam_vpu2_apunn_preload.json \
  --carve-dir /tmp/ota_V260523_cam_vpu/apunn_carve --list-all
```

Current parse result:

| Core mask | Segment | Raw offset | File size | Load base / target |
|---|---|---:|---:|---|
| `0x61` | main PROG | `0x3b4` | `0x26aaf0` | base `0x70000000`, target `0x70006794` |
| `0x61` | IRAM | `0x32cdf8` | `0x2000` | dynamic `INFO19` |
| `0x61` | aux PROG | `0x26aea4` | `0x1884` | `0x7ff04000` |
| `0x61` | aux PROG | `0x26c728` | `0x218` | `0x7ff3b000` |
| `0x62` | main PROG | `0x32d1e0` | `0x26aaf0` | base `0x74000000`, target `0x74006794` |
| `0x62` | IRAM | `0x659c24` | `0x2000` | dynamic `INFO19` |
| `0x62` | aux PROG | `0x597cd0` | `0x1884` | `0x7ff04000` |
| `0x62` | aux PROG | `0x599554` | `0x218` | `0x7ff3b000` |
| `0x64` | main PROG | `0x65a00c` | `0x26aaf0` | base `0x78000000`, target `0x78006794` |
| `0x64` | IRAM | `0x986a50` | `0x2000` | dynamic `INFO19` |
| `0x64` | aux PROG | `0x8c4afc` | `0x1884` | `0x7ff04000` |
| `0x64` | aux PROG | `0x8c6380` | `0x218` | `0x7ff3b000` |

Main flat-window hashes:

| Core mask | Carved file | SHA-256 |
|---|---|---|
| `0x61` | `/tmp/ota_V260523_cam_vpu/apunn_carve/apu_lib_apunn_h0_pre0_prog_off3b4.bin` | `531198afb182e868919163fdf701591dcd212e52dc7cfb71fd512d7faa4c63db` |
| `0x62` | `/tmp/ota_V260523_cam_vpu/apunn_carve/apu_lib_apunn_h0_pre4_prog_off32d1e0.bin` | `a9d09d45af5cc67c88c7908b9e5f85c0b2a8d1a6dd0ee781a0851cd1b5ee46e7` |
| `0x64` | `/tmp/ota_V260523_cam_vpu/apunn_carve/apu_lib_apunn_h0_pre8_prog_off65a00c.bin` | `1220a7648829ec4bf5a390c777fe0e47f57b9e8ea119d8f032b03a28bd2c98ee` |

The carved outputs are raw flat windows; `file(1)` reports `data`, and no local
`readelf`/`llvm-readelf` binary recognizes them because there is no ELF header.
Use the metadata above for base addresses and treat the `INFO16` value as the
firmware-facing preload target until disassembly proves whether it is a direct
code entry, a dispatcher table, or an algorithm descriptor. A live physical
reserved-memory dump is still useful to verify the exact LK-merged layout.

#### Path 2: Live memory dump (needs kernel read)

The VPU binary is mapped from reserved memory at boot:

```
/sys/firmware/devicetree/base/reserved-memory/mblock-18-vpu_binary
```

If a kernel read primitive is obtained from another surface (e.g. display DRM
CVEs), the reserved-memory region can be read directly. The DT `bin-phy-addr`
and `bin-size` properties give the physical address and size; apply the same
parser to the dump.

For the current target:

```sh
# Dump this physical range with a real kernel/physical-memory read primitive.
# uid=1000 can read these DT values but cannot read the bytes directly.
phys_start=0xbe510000
phys_size=0x018f0000
phys_end=0xcee00000

python3 13-apusys-ioctl-surface/tools/parse_vpu_image.py \
  vpu_binary_be510000_018f0000.bin \
  --head-offset 0xcb1000 --algo apu_lib_apunn \
  --json apunn_preload.json --carve-dir apunn_carve
```

Current `uid=1000(system)` checks that still fail without a stronger primitive:

```sh
dd if=/dev/block/by-name/cam_vpu1_a of=/dev/null bs=4096 count=1
cat /proc/vpu/vpu_memory
cat /proc/vpu/vpu0/mesg
cat /proc/iomem
cat /proc/kallsyms
```

All return `Permission denied` in `u:r:system_app:s0`.

#### Path 3: Vendor library extraction (partial)

`libneuron_platform.vpu.so` and `libneuron_platform.so` contain wrapper-side
Xtensa command preparation but **not** the firmware body itself. The wrapper
builds the settings/code/output/data descriptor buffers; the firmware consumes
them. Static analysis of these libraries is useful context but does not replace
the firmware binary.

Already pulled: both libraries are on-device at `/vendor/lib64/` and have been
used for `APUNN_SETTINGS_ABI.md` wrapper analysis.

## What to do with the binary

### Step 1: Carve and load

Use `parse_vpu_image.py` to extract the `apu_lib_apunn` PROG and IRAM flat
windows from `cam_vpu2.img`.

Load into Ghidra or IDA with an Xtensa processor module:
- Ghidra: built-in Xtensa support (Language: `Xtensa:LE:32:default`)
- IDA: requires a third-party Xtensa processor module

Recommended first load:

| Core mask | File | Load base | Mapped size | `INFO16` target |
|---|---|---:|---:|---:|
| `0x61` | `/tmp/ota_V260523_cam_vpu/apunn_carve/apu_lib_apunn_h0_pre0_prog_off3b4.bin` | `0x70000000` | `0x270000` | `0x70006794` |

Then add the `0x61` IRAM and aux PROG windows as separate raw memory blocks:

| Segment | File | Suggested base |
|---|---|---:|
| IRAM | `/tmp/ota_V260523_cam_vpu/apunn_carve/apu_lib_apunn_h0_pre1_iram_off32cdf8.bin` | annotate as dynamic `INFO19` data |
| aux PROG | `/tmp/ota_V260523_cam_vpu/apunn_carve/apu_lib_apunn_h0_pre2_prog_off26aea4.bin` | `0x7ff04000` |
| aux PROG | `/tmp/ota_V260523_cam_vpu/apunn_carve/apu_lib_apunn_h0_pre3_prog_off26c728.bin` | `0x7ff3b000` |

Do not expect an ELF header or symbol table. Start with strings and tables:
`execute_op`, `flk_*`, `XTENSA_ANN_VERSION`, `../algo/apu_lib_apunn/src/main_proc.c`,
`../algo/apu_lib_apunn/src/d2d_flo.c`,
`../vp6-ann/xrp-dsp/xrp_dsp.c`, and
`../vp6-ann/libcommon/src/idma_mvpu6/dmaif.c`. The `INFO16` target at
`0x70006794` currently lands in a literal/string-heavy area of the raw window,
so annotate it as the kernel-provided preload target first and let code xrefs
prove the real dispatcher/handler entry.

### Step 2: Map the MMIO dispatch interface

The kernel writes these MMIO registers before signaling the DSP:

| Register | Content | Firmware use |
|---|---|---|
| `XTENSA_INFO01` | `0x24` (`DO_D2D_EXT`) | Command selector |
| `XTENSA_INFO11` | Slot index (0–2) | Preload set selector |
| `XTENSA_INFO12` | `buffer_count` from `request+0x35` (max `0x20`) | Descriptor array length |
| `XTENSA_INFO13` | Kernel-copied descriptor array IOVA | Pointer to `struct vpu_buffer[]` copy |
| `XTENSA_INFO14` | `request+0x40` (settings IOVA when libvpu mode) | Optional settings pointer |
| `XTENSA_INFO15` | `request+0x38` (settings length when libvpu mode) | Optional settings length |
| `XTENSA_INFO16` | `pre->a.mva + pre->a.entry_off` | PROG load/entry address |
| `XTENSA_INFO19` | `pre->a.iram_mva` | IRAM load address |

The firmware entry likely reads these via Xtensa special registers or
memory-mapped I/O at fixed offsets. Locate the dispatch handler that reads
`INFO01` and switches on the command type.

### Step 3: Answer the security questions

These are the specific questions the firmware binary needs to resolve, in
priority order:

#### Q1: DMA write timing and sequencing

Is the completion write (settings `0x5 → 0x7`, output fill, `settings+0x30`
clear) a single burst or multiple sequenced stores? If sequenced, what is the
inter-store gap? A gap > 1 µs between the first and last store would reopen the
allocator-reuse race window.

Look for: loops that iterate over descriptor entries, conditional writes gated
on intermediate results, explicit DSP wait/sleep/barrier instructions between
DMA stores.

#### Q2: Opcode dispatch table

The wrapper generates opcodes `10001..10009` in the Xtensa code section. The
firmware presumably has a switch or table dispatch. Which opcodes produce the
most DMA traffic? Are there opcodes that:
- write to multiple descriptor-backed buffers in sequence
- perform iterative computation with repeated output writes
- read from one descriptor and write to another (copy-style operations)

Any of these would increase the exploitable DMA window.

#### Q3: Descriptor bounds checking

`INFO12` (buffer_count) + `INFO13` (descriptor array IOVA) define the
firmware's view of the descriptor array. Does the firmware:
- trust `buffer_count` as the iteration limit, or does it have an internal cap?
- validate descriptor plane MVA/IOVA before DMA?
- check plane sizes before writing?

An unchecked `buffer_count` or missing size validation would be a direct
firmware-side primitive.

#### Q4: Output write bounds

`settings+0x08` is known to bound output fill from the kernel side. Does the
firmware enforce this bound internally, or does it rely on the caller? If the
firmware derives its own write size from the code section (e.g., from operand
metadata), a mismatch between the declared `output_size` and the firmware's
computed size could produce an overflow.

#### Q5: Data descriptor consumption

Standard `data_desc_size=0x0c` is consumed and clears `settings+0x30`. Does the
firmware follow data descriptor pointers and perform DMA to/from them? The
current runtime matrices show data payloads are preserved (not consumed), but
this could be opcode-dependent.

#### Q6: Hidden input channels

Are there firmware-readable regions beyond the declared settings/code/output/data
windows? For example:
- does the firmware read from fixed memory-mapped addresses that could overlap
  with user-importable IOVA ranges?
- does it use `INFO14/INFO15` (settings tuple) even when the wrapper clears
  them?
- are there undocumented MMIO registers the firmware reads?

### Step 4: Feed results back

Update `HANDOFF_KERNEL_PRIMITIVE.md` section "Firmware re-entry criteria" with:
- confirmed DMA timing model (single burst vs sequenced)
- any opcodes that produce extended write windows
- any bounds-check gaps in descriptor/output handling
- any new DMA targets discovered

If Q1 shows sequenced writes with a meaningful gap, immediately re-run the
allocator gap-reuse firmware probe
(`--run-cmd-vpu-xrp-mem-free-race-completed-gap-reuse-iova`) with the
slow-opcode shape.

## Existing tooling

| Tool | Path | Status |
|---|---|---|
| VPU image parser | `13-apusys-ioctl-surface/tools/parse_vpu_image.py` | Ready for metadata and raw flat-window carve; use `--head-offset 0x200 --headers 1` for V260523 `cam_vpu2.img` |
| Allocator gap profiler | `13-apusys-ioctl-surface/poc/ApusysIoctlProbe.java` | Active; 8+ probe modes |
| Firmware-coupled gap reuse | `--run-cmd-vpu-xrp-mem-free-race-completed-gap-reuse-iova` | Ready to re-run with new shapes |
| Wrapper static analysis | `13-apusys-ioctl-surface/APUNN_SETTINGS_ABI.md` | Complete for current scope |
| Kernel primitive handoff | `13-apusys-ioctl-surface/HANDOFF_KERNEL_PRIMITIVE.md` | Active closure artifact |
| Allocator controllability | `13-apusys-ioctl-surface/ALLOCATOR_CONTROLLABILITY_OPPORTUNITY.md` | Active experiment loop |

## Acceptance criteria

The firmware disassembly task is complete when:

1. `apu_lib_apunn` PROG flat window is loaded in a disassembler with correct
   base address, mapped size, and `INFO16` preload target annotation
2. The `D2D_EXT` command handler is identified (reads `INFO01 == 0x24`)
3. Q1 (DMA timing) has a concrete answer: single burst, or sequenced with
   measured/estimated inter-store gap
4. Q3 (descriptor bounds) has a concrete answer: firmware-side `buffer_count`
   cap exists or doesn't
5. Findings are written back to `HANDOFF_KERNEL_PRIMITIVE.md`

Q2/Q4/Q5/Q6 are valuable follow-ups but not blocking for the immediate
allocator-reuse race decision.
