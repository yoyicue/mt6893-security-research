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
| ISA | Xtensa (Cadence HiFi-class DSP) | MTK APU/VPU documentation; `XrpIntrinsicExecutor::PrepareXtensaCommandBuffer` in `libneuron_platform.so` |
| MMIO dispatch registers | `INFO16 = pre->a.mva + pre->a.entry_off` (PROG entry), `INFO19 = pre->a.iram_mva` (IRAM) | IDA D2D_EXT selector at `0xffffffc0087a1f40` |
| Image packing | `struct vpu_image_header` → `struct vpu_pre_info` array; each entry has `off`, `pAddr`, `mem_sz`, `file_sz` | kernel `vpu_hw.h` layout; `tools/parse_vpu_image.py` implements the parser |
| Segments | PROG (code+data, `pAddr ≠ 0xFFFFFFFF`) and IRAM (`pAddr == 0xFFFFFFFF`) | parser logic and kernel init path |
| Entry offset | `pAddr - (start_addr & 0xffff0000)` for PROG segments | `tools/parse_vpu_image.py` line 226 |
| Kernel loader | `vpu_init_bin` at `0xffffffc0095b0d84` — reads DT props `bin-phy-addr`, `bin-size`, `img-head`, `pre-bin` | IDA string xrefs |

### Acquisition paths (ranked)

#### Path 1: Partition dump (needs elevated read)

The device exposes VPU partitions:

```
/dev/block/by-name/cam_vpu1_a
/dev/block/by-name/cam_vpu2_a
/dev/block/by-name/cam_vpu3_a
```

Current `uid=1000` shell cannot read these. Options:

- **adb root** on a userdebug/eng build of the same firmware
- **TWRP / custom recovery** — dd the partitions
- **MTK SP Flash Tool** — read back the `cam_vpu*` partitions from the ROM
- **OTA / factory image** — some MTK vendors ship the `cam_vpu*` images in
  scatter-file based ROMs; extract from the update package

Once the partition images are obtained:

```sh
python3 13-apusys-ioctl-surface/tools/parse_vpu_image.py \
  cam_vpu1_a.bin cam_vpu2_a.bin cam_vpu3_a.bin \
  --auto --algo apu_lib_apunn --json apunn_preload.json \
  --carve-dir apunn_carve
```

This produces `apunn_carve/apu_lib_apunn_prog.bin` and optionally
`apunn_carve/apu_lib_apunn_iram.bin`.

#### Path 2: Live memory dump (needs kernel read)

The VPU binary is mapped from reserved memory at boot:

```
/sys/firmware/devicetree/base/reserved-memory/mblock-18-vpu_binary
```

If a kernel read primitive is obtained from another surface (e.g. display DRM
CVEs), the reserved-memory region can be read directly. The DT `bin-phy-addr`
and `bin-size` properties give the physical address and size; apply the same
parser to the dump.

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

Use `parse_vpu_image.py` to extract `apu_lib_apunn` PROG and IRAM segments.

Load into Ghidra or IDA with an Xtensa processor module:
- Ghidra: built-in Xtensa support (Language: `Xtensa:LE:32:default`)
- IDA: requires a third-party Xtensa processor module

Set the base address to the preload `start_addr & 0xffff0000` value from the
parser JSON output. The entry point is `start_addr & 0xffff0000 + entry_off`.

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
| VPU image parser | `13-apusys-ioctl-surface/tools/parse_vpu_image.py` | Ready; tested on synthetic headers |
| Allocator gap profiler | `13-apusys-ioctl-surface/poc/ApusysIoctlProbe.java` | Active; 8+ probe modes |
| Firmware-coupled gap reuse | `--run-cmd-vpu-xrp-mem-free-race-completed-gap-reuse-iova` | Ready to re-run with new shapes |
| Wrapper static analysis | `13-apusys-ioctl-surface/APUNN_SETTINGS_ABI.md` | Complete for current scope |
| Kernel primitive handoff | `13-apusys-ioctl-surface/HANDOFF_KERNEL_PRIMITIVE.md` | Active closure artifact |
| Allocator controllability | `13-apusys-ioctl-surface/ALLOCATOR_CONTROLLABILITY_OPPORTUNITY.md` | Active experiment loop |

## Acceptance criteria

The firmware disassembly task is complete when:

1. `apu_lib_apunn` PROG segment is loaded in a disassembler with correct base
   address and entry point
2. The `D2D_EXT` command handler is identified (reads `INFO01 == 0x24`)
3. Q1 (DMA timing) has a concrete answer: single burst, or sequenced with
   measured/estimated inter-store gap
4. Q3 (descriptor bounds) has a concrete answer: firmware-side `buffer_count`
   cap exists or doesn't
5. Findings are written back to `HANDOFF_KERNEL_PRIMITIVE.md`

Q2/Q4/Q5/Q6 are valuable follow-ups but not blocking for the immediate
allocator-reuse race decision.
