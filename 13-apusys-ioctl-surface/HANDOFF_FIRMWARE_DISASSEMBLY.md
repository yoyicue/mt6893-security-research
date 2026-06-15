# APUNN firmware disassembly — handoff requirement

Date: 2026-06-15

## Basic disassembly stage status

The current OTA APUNN ELF now has a reproducible foundation layer for IDA,
machine-readable refs, and this handoff. This stage is sufficient for the
kernel-primitive decision loop and for later deeper reverse engineering:

- the persistent ELF/IDB artifacts under
  `../13-apusys-ioctl-surface/firmware/apunn/` are the current OTA
  `apunn_core0_full.elf` and saved IDA database
- `analyze_apunn_elf.py` regenerates the JSON/Markdown refs from the ELF and
  `.xt.prop` without relying on decompiler output
- `ida_apply_apunn_xt_prop.py` consumes those refs and annotates the active IDA
  Xtensa ELF IDB
- FLIX instruction width is now validated globally from `.xt.prop`: `0xe` is a
  16-byte FLIX128 bundle, `0xf` is an 8-byte FLIX64 bundle, and that rule tiles
  all `54,282` instruction-property runs exactly
- current Q1/Q2/Q3/Q4 evidence is captured below; full FLIX slot semantics,
  full decompilation, and the final `10001..10009` indexed-dispatch map are
  follow-up work rather than prerequisites for this foundation handoff
- the generated refs now include `elf_verification_1234` and the Markdown
  section "ELF Verification 1-4", which records the completed static result for
  settings/output-fill, descriptor validation/use, iDMA owner sequencing, and
  opcode parser correlation

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
4. **Current Java-layer races do not catch a live completion writeback.** The
   firmware-coupled gap-reuse probes show `wait=0` with settings already at
   `0x7`, or `wait=-EIO` with the replacement buffer untouched. The
   completion-poll probe adds that the shared HardwareBuffer mapping does not
   show settings/output/data-desc changes during a 10 ms post-`run_async`
   busy-poll; the completion bytes become visible after `wait_cmd`.

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

The first source-vs-firmware comparison pass is captured in
`MTK_SOURCE_FIRMWARE_COMPARISON_MATRIX.md`. It accepts the INFO12/INFO13
descriptor contract and INFO16 APUNN entry binding against current firmware
refs, keeps INFO14/INFO15 settings/output-fill as only partially statically
mapped, treats INFO01 `0x24` as a kernel/resident-firmware selector until a
direct APUNN-side read is found, and rejects the ANN op-name table / FLK-ANN
slot signatures as proof of wrapper opcode index mapping. The current ELF refs
materialize the active 1-4 verification pass as:

| ID | Result |
|---|---|
| `EV1_SETTINGS_OUTPUT_FILL` | static output validators mapped; exact runtime `settings+0x08` fill loop not identified in ELF-only refs |
| `EV2_DESCRIPTOR_VALIDATION_USE` | INFO12/INFO13 count and 0x40 layout closed; validation/use pairing remains partial |
| `EV3_IDMA_OWNER_SEQUENCING` | top iDMA schedule/wait owner `0x70044b74` closed as an anchor; timing remains runtime/FLIX-slot evidence |
| `EV4_OPCODE_PARSER_CORRELATION` | locateBuffer/parser path mapped; indexed `10001..10009` opcode table evidence not found |

## Current OTA artifacts

The full V260523 OTA is available on the local Synology share and has already
been used to extract the VPU partitions:

| Artifact | Path | Size |
|---|---|---|
| Full OTA | `/private/tmp/nas_sync_powerctrl_23770/ota_V260523/V260523_FULL.zip` | local Synology copy |
| `cam_vpu1` | `/tmp/ota_V260523_cam_vpu/cam_vpu1.img` | `0x3fa000` |
| `cam_vpu2` | `/tmp/ota_V260523_cam_vpu/cam_vpu2.img` | `0xa05000` |
| `cam_vpu3` | `/tmp/ota_V260523_cam_vpu/cam_vpu3.img` | `0x2000` |

Extraction command:

```sh
mkdir -p /tmp/ota_V260523_cam_vpu
payload-dumper-go -p cam_vpu1,cam_vpu2,cam_vpu3 \
  -o /tmp/ota_V260523_cam_vpu \
  /private/tmp/nas_sync_powerctrl_23770/ota_V260523/V260523_FULL.zip
```

This follows the standard Android A/B OTA `payload.bin` path: the vendor OTA app
feeds `payload_offset` and `payload_size` metadata to
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
| ISA / container | Xtensa ELF embedded at `+0x234` inside each main PROG preload segment; strings show `Xtensa Compiler Version 12.0.10` | V260523 `cam_vpu2.img`; `tools/parse_vpu_image.py` embedded-ELF scan |
| MMIO dispatch registers | `INFO16 = pre->a.mva + pre->a.entry_off` (firmware-facing preload target), `INFO19 = pre->a.iram_mva` (IRAM) | IDA D2D_EXT selector at `0xffffffc0087a1f40` |
| Image packing | `struct vpu_image_header` → `struct vpu_pre_info` array; each entry has `off`, `pAddr`, `mem_sz`, `file_sz`; the main APUNN PROG segment has a wrapper prefix before the ELF header | kernel `vpu_hw.h` layout; `tools/parse_vpu_image.py` implements the parser |
| Segments | PROG (code+data, `pAddr ≠ 0xFFFFFFFF`) and IRAM (`pAddr == 0xFFFFFFFF`) | parser logic and kernel init path |
| Entry offset | `pAddr - (start_addr & 0xffff0000)` for PROG segments; for V260523 APUNN this is `0x6794` on the three main PROG windows and matches the embedded ELF entry low offset | parser output; MTK `vpu_hw.c:vpu_init_dev_algo_preload_entry()` |
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

| Core mask | Segment | Raw offset | File size | Load base / target | Embedded ELF |
|---|---|---:|---:|---|---|
| `0x61` | main PROG | `0x3b4` | `0x26aaf0` | base `0x70000000`, target `0x70006794` | `+0x234` / file `0x5e8`, entry `0x70006794`, `e_shoff=0x32cc18` |
| `0x61` | IRAM | `0x32cdf8` | `0x2000` | dynamic `INFO19` | n/a |
| `0x61` | aux PROG | `0x26aea4` | `0x1884` | `0x7ff04000` | n/a |
| `0x61` | aux PROG | `0x26c728` | `0x218` | `0x7ff3b000` | n/a |
| `0x62` | main PROG | `0x32d1e0` | `0x26aaf0` | base `0x74000000`, target `0x74006794` | `+0x234` / file `0x32d414`, entry `0x74006794`, `e_shoff=0x32cc18` |
| `0x62` | IRAM | `0x659c24` | `0x2000` | dynamic `INFO19` | n/a |
| `0x62` | aux PROG | `0x597cd0` | `0x1884` | `0x7ff04000` | n/a |
| `0x62` | aux PROG | `0x599554` | `0x218` | `0x7ff3b000` | n/a |
| `0x64` | main PROG | `0x65a00c` | `0x26aaf0` | base `0x78000000`, target `0x78006794` | `+0x234` / file `0x65a240`, entry `0x78006794`, `e_shoff=0x32cc18` |
| `0x64` | IRAM | `0x986a50` | `0x2000` | dynamic `INFO19` | n/a |
| `0x64` | aux PROG | `0x8c4afc` | `0x1884` | `0x7ff04000` | n/a |
| `0x64` | aux PROG | `0x8c6380` | `0x218` | `0x7ff3b000` | n/a |

Main flat-window carves:

| Core mask | Carved file |
|---|---|
| `0x61` | `/tmp/ota_V260523_cam_vpu/apunn_carve/apu_lib_apunn_h0_pre0_prog_off3b4.bin` |
| `0x62` | `/tmp/ota_V260523_cam_vpu/apunn_carve/apu_lib_apunn_h0_pre4_prog_off32d1e0.bin` |
| `0x64` | `/tmp/ota_V260523_cam_vpu/apunn_carve/apu_lib_apunn_h0_pre8_prog_off65a00c.bin` |

The main PROG carves are wrapper-prefixed flat windows. The actual APUNN code
container starts at the embedded ELF header (`raw_off + 0x234`). Do not truncate
the ELF at the preload `file_sz`: the section header table is at `e_shoff
0x32cc18`, beyond the declared main PROG file size, because the aux PROG/IRAM
payloads and section metadata follow in the packed partition. For analysis,
extract from the embedded ELF header to the end of `cam_vpu2.img` and let the
ELF section table define the core-0 memory map.

Core-0 full ELF extraction:

```sh
dd if=/tmp/ota_V260523_cam_vpu/cam_vpu2.img \
  of=/tmp/apunn_core0_full.elf bs=1 skip=$((0x3b4+0x234)) status=none

file /tmp/apunn_core0_full.elf
# ELF 32-bit LSB executable, Tensilica Xtensa, statically linked, stripped
```

A live physical reserved-memory dump is still useful to verify the exact
LK-merged layout, but it is no longer a blocker for core-0 static analysis.

The current ELF, IDA IDB, and analyzer outputs are persisted under
`../13-apusys-ioctl-surface/firmware/apunn/`:

| Artifact | Path | Notes |
|---|---|---|
| Core-0 full ELF | `../13-apusys-ioctl-surface/firmware/apunn/apunn_core0_full.elf` | persistent analysis baseline |
| IDA Pro Xtensa IDB | `../13-apusys-ioctl-surface/firmware/apunn/apunn_core0_full.elf.i64` | mutable analysis database |
| IDA sidecars | optional | IDA may create `apunn_core0_full.elf.id0`, `.id1`, `.id2`, `.nam`, or `.til` beside the `.i64` database |
| Analyzer JSON | `../13-apusys-ioctl-surface/firmware/apunn/apunn_core0_full_analysis_refs.json` | mutable generated analysis input for IDA apply script |
| Analyzer Markdown | `../13-apusys-ioctl-surface/firmware/apunn/apunn_core0_full_analysis_refs.md` | mutable generated analysis summary |

Current core-0 ELF facts from
`../13-apusys-ioctl-surface/tools/analyze_apunn_elf.py`:

| Section | VA | File offset | Size |
|---|---:|---:|---:|
| `.rodata` | `0x70000000` | `0x3b4` | `0x6500` |
| `.text` | `0x70006500` | `0x68b4` | `0x2645f0` |
| `.data` | `0x7ff04000` | `0x26aea4` | `0xb4` |
| `.dram0.data` | `0x7ff040c0` | `0x26af64` | `0x17c4` |
| `.dram_op.data` | `0x7ff3b000` | `0x26c728` | `0x218` |
| `.xt.prop` | n/a | `0x26c940` | `0xb9aa8` |

The Xtensa property table is the strongest non-decompiler source for code
ranges. It contains `63374` entries, including `54282` instruction property
runs, `14383` branch targets, and `8838` unreachable/align entries. These are
property runs, not guaranteed per-instruction boundaries; a single entry can
cover a FLIX/TIE-heavy block such as the `0x7003c102` `0xaa`-byte loop body.
Around the kernel-provided entry, it marks `0x70006794` as a real instruction
range:

| Address | Size | Flags |
|---:|---:|---|
| `0x7000677b` | `0x17` | `insn|no_reorder` |
| `0x70006792` | `0x2` | `insn|branch_target|no_reorder` |
| `0x70006794` | `0x0` | `unreachable|align|align2` |
| `0x70006794` | `0xac` | `insn|no_reorder` |

`analyze_apunn_elf.py` now also scans `.text` for Xtensa `entry` prologues only
when they are backed by an `.xt.prop` instruction range. This recovers `1019`
function-entry candidates, versus Ghidra's current `331` auto-functions. Treat
these as candidate entry points, not decompiler-proven C functions; they are
still the better map for TIE/FLIX-heavy code.

Important owner assignments from the entry-candidate map:

| Label | Address | Owner entry | Delta | Property |
|---|---:|---:|---:|---|
| kernel `INFO16` / ELF entry | `0x70006794` | `0x70006794` | `0x0` | `insn|no_reorder:0xac` |
| FLK pointer-target cluster | `0x70017d40` | `0x70015e98` | `0x1ea8` | `insn|data|no_reorder|no_transform:0xb` |
| FLK pointer-table owner | `0x70015e98` | `0x70015e98` | `0x0` | `insn|data|no_reorder|no_transform:0xb09` |
| dispatcher-like `locateBuffer` user | `0x700301d8` | `0x700301d8` | `0x0` | `insn|no_reorder:0x30` |
| ANN pointer-table owner | `0x70081d50` | `0x70081d50` | `0x0` | `insn|no_reorder:0xa3` |
| ANN pointer-target cluster | `0x70081ee7` | `0x70081d50` | `0x196` | `insn|branch_target|no_reorder:0x5` |
| small type helper | `0x70083068` | `0x70083068` | `0x0` | `insn|no_reorder:0x17` |

Recovered pointer/name tables:

- `.rodata` pointer runs at `0x70000180`, `0x700001c0`, `0x70000200`,
  and `0x70000b80` point into `.text` instruction ranges. The first three are
  12-entry clusters around `0x70017cxx..0x70017exx`; the fourth has 31 entries
  around `0x70081ee7..0x70082aac`.
- All three 12-entry FLK pointer runs resolve to owner `0x70015e98`.
- The 31-entry ANN pointer run at `0x70000b80` resolves entirely to owner
  `0x70081d50`; a standard-instruction island in that owner contains `jx a9`,
  consistent with branch-table-shaped control flow. The refreshed reachability
  scan finds L32R literal-slot signatures for selected `0x70000b80` slots, so
  those code pointers are reachable at the slot-signature level, but it does not
  find a table-base value reference that proves opcode-indexed dispatch. This is
  slot-level code-pointer evidence, not a proven mapping from table index to the
  63 ANN op-name entries.
- `.dram_op.data` holds a 63-entry ANN op name table:
  `CONV2D`, `DWCONV2D`, `POOL2D`, `LOGISTIC`, `RELU`, `SOFTMAX`, `RESHAPE`,
  `CONCAT`, `ELEWISE`, `L2NORM`, `RESZBILINR`, `TRANSPOSE`, `DECONV2D`, `PAD`,
  `STRIDESLICE`, `MEAN`, `BATCH2SPACE`, `SPACE2BATCH`, `DEPTHNSPACE`,
  `REQUANT`, `DIDWCONV2D`, `DICONV2D`, `PRELU`, `TANH`, `ARGMINMAX`,
  `GROUPCONV2D`, `SHUFFLE`, `REDUCE`, `PCCONV2D`, `PCDWCONV2D`, `DIPCCONV2D`,
  `DIPCDWCONV2D`, `CAST`, `BOXTRANSFORM`, `SPLIT`, `QUANT16LSTM`,
  `BOXWITHNMS`, `GENPROPOSALS`, `HEATMAXKEY`, `TOPKV2`, `ROIALIGN`,
  `RESZNEAR`, `TILE`, `GATHER`, `SELECT`, `QUANTIZE`, `DEQUANTIZE`,
  `INSTANCENORM`, `LAYERNORM`, `DIV`, `SQRT`, `RSQRT`, `QUANTLSTM`,
  `HARDSWISH`, `FILL`, `TYPECONVERT`, `ELTCOMPARE`, `ROIALIGNV2`,
  `RESZBILINRV2`, `RESZNEARV2`, `ARGMINMAX4D`, `XFL_SQRT_QUANT`,
  `XFL_RSQRT_QUANT`.
- Security-relevant strings include `process_command`, `execute_op`,
  `kernelProcess`, `dma_barrier`, `Invalid input/output buffer size`,
  `Data buffer does not fit in DRAM`, `add idma request fail in %s\n`,
  `ERROR CALLBACK: iDMA in Error`,
  `INTERRUPT CALLBACK : processing iDMA interrupt`, `iDMA schedule error`,
  `iDMA wait error`, `sDesc > eDesc`, and `eDesc >= TM_DMA_DESC_IDX_MAX`.
- `.xtensa.info` identifies the core as `MVPU6F_1214_Prod`, Xtensa
  `LX7.0.10`, release `RG-2018.10/12.0.10`, with
  `USE_ABSOLUTE_LITERALS=0`. That makes PC-relative `L32R` literal-slot
  recovery mandatory; raw `.text` pointer scans are insufficient for string
  owner recovery.
- `.text` aligned 32-bit references into `.rodata` strings now recover `180`
  references after accepting newline-bearing log strings, `45` of them
  matching Invalid/Error/buffer/DMA-related tokens.
- `ProcessTileWise.c` rodata refs resolve to owners `0x700c13b0`,
  `0x7011be20`, and `0x70262690`.
- `Invalid allocation alignment` rodata refs resolve to owners `0x70076d50`
  and `0x70131c80`.
- `Error processes function` rodata refs resolve to owners `0x700078a0`,
  `0x70009bc0`, `0x70052650`, `0x70055990`, `0x7008fe80`, `0x700d4130`,
  `0x70106c70`, `0x7011be20`, `0x7015d680`, `0x7015ffd0`, `0x701cf890`,
  `0x7023eb10`, and `0x70249e10`.
- Critical-string direct-reference scanning now checks both aligned 32-bit
  `.text` words and all-byte `.text` windows for suffix pointers into the
  target strings. It finds **zero** direct refs for the add-idma-request-fail
  log string, `ERROR CALLBACK: iDMA in Error`,
  `INTERRUPT CALLBACK : processing iDMA interrupt`, `iDMA error`,
  `iDMA schedule error`, `iDMA wait error`,
  `../vp6-ann/libcommon/src/idma_mvpu6/dmaif.c`, `sDesc > eDesc`,
  `eDesc >= TM_DMA_DESC_IDX_MAX`, `_DMA_STALL`, and `No error`. This makes the
  current iDMA owner gap reproducible: the strings are present in `.rodata`,
  but not reachable through the direct literal-pointer method.
- `.xt.prop`-covered `L32R` literal-slot scanning now recovers owner candidates
  for several of those same strings. High-value samples include
  `iDMA schedule error` at owners `0x70024710`, `0x70035c64`, `0x700414d0`,
  and `0x70044b74`; `iDMA wait error` at owners `0x70024710`, `0x70036110`,
  and `0x70044b74`; and `../vp6-ann/libcommon/src/idma_mvpu6/dmaif.c` at
  owners `0x70036110`, `0x70044850`, and `0x70044b74`. These are
  section-filtered decode leads from property-covered ranges, not complete CFG
  proof yet.
- String-cluster scoring ranks `0x70044b74` as the top DMA/iDMA owner
  candidate because the same owner references `iDMA schedule error`,
  `iDMA wait error`, `../vp6-ann/libcommon/src/idma_mvpu6/dmaif.c`, and
  `sDesc > eDesc` through L32R. The next tier has only two distinct
  DMA/iDMA-related strings per owner.
- The new DMA/iDMA owner investigation promotes that lead to the Q1 firmware
  anchor. In `0x70044b74..0x70045380`, the same owner has ordered L32R refs to
  `iDMA schedule error` (`0x70044e3a`), `iDMA wait error` (`0x70044e53`),
  `dmaif.c` (`0x70044f1f`), `sDesc > eDesc` (`0x70044f45`), and the DRAM data
  buffer validation strings (`0x700452ef`, `0x70045319`). The range has 83
  `06 04 02` byte motifs, now classified as the FLIX128 framing tail rather
  than an independent selector. The owner identification stands, but it still
  does not expose completion-write burst shape or inter-store timing.
- `Data buffer does not fit in DRAM` has no aligned refs but has six all-byte
  suffix-pointer samples at owners `0x700c13b0`, `0x700cc080`, `0x700cda20`,
  `0x7016bc40`, `0x70262690`, and `0x70262a00`. Because these are unaligned
  byte-window hits, treat them as disassembly leads rather than proven literal
  references.

`analyze_apunn_elf.py` now emits byte-verified standard Xtensa islands for the
extension-heavy early path. The `0x70006794` `INFO16`/ELF entry starts with
`entry a1, 0x20`, then copies six dwords from `a12+0x00..0x14` into
`a10+0x04..0x18`, and copies `a2+0x44`, `a2+0x4c`, and `a2+0x50` into
`a10+0x28`, `a10+0x1c`, and `a10+0x20`. The `0x70006590` helper loads
`*(a2+0x30)+0x18` into `a10`, calls `0x70007440`, then returns 0.

The `0x70007440` standard islands now show a richer dynamic-dispatch shape:
the function reads `ccount`, loads a function pointer from `a12+0`,
conditionally calls it, calls the local wait/spin helper `0x700068c0`, reads
`ccount` again near a return-shaped sequence, and then continues into a second
callback/polling island at `0x7000750c..0x700075c4`. That second island has
multiple `callx8 a8` sites, another `call8 0x700068c0`, and clamps a callback
result with `minu a10, a10, 2` before storing it through `a4+0`. Treat these as
verified preload-context packing and early dynamic-dispatch clues, not as
complete prototypes, until the custom instruction semantics are resolved.

The former `0x700301d8` "locateBuffer-related" guess is now a byte-verified
standard island. At `0x700301e3`, `l32r a3, 0x70001884` loads the `locateBuffer`
suffix inside the `xvAllocateBuffer` rodata string; the island then loads fields
from `a8+0x2c/+0x34`, branches toward the larger `0x70030240` owner
(`0x70030a0c` landing point), and contains an indirect `callx8 a8` followed by
`mov.n a2, a10; retw.n`.

The `0x70030a0c` landing path is now the first byte-verified DSP
command/operand parser island. It reads byte fields from `a2+0x49` and
`a2+0x4a`, combines them into a 16-bit value, extracts bits `2..5` into `a10`,
and later special-cases decoded values `1`, `5`, `6`, and `9`. The same island
also reads little-endian byte pairs at `a2+0x0e/+0x0f`,
`a2+0x12/+0x13`, `a2+0x2e/+0x2f`, and `a2+0x06/+0x07`, plus a 16-bit field at
`a2+0x00`. These offsets line up with the wrapper-side Xtensa code/operand area
(`entry+0x48+operand_off`) much better than the native 0x40-byte
`struct vpu_buffer` array, so this connects the `locateBuffer` dispatcher to
the descriptor-backed DSP command buffer parser, but not yet to the firmware's
native `INFO12`/`INFO13` iteration bounds.

The same `0x700304f8` parser owner now has byte-level L32R literal-slot
signatures to nearby code-pointer runs: two slots from the `0x70000180` FLK run,
one slot from the `0x700001c0` FLK run, and two slots from the `0x70000b80` ANN
run. The FLIX-correct local sweeps place all five ref offsets inside FLIX
bundles, not as boundary-visible core instructions, and the static scan still
finds no table-base value reference for indexed opcode dispatch. The branch
targets from the decoded `a2+0x49/+0x4a` field are now tracked as
`0x70020ba2` (cross-owner target in `0x7001dffc`) and `0x7003125a`/`0x7003126a`
(deeper paths inside `0x700304f8`). This ties the parser to concrete FLK/ANN
code-pointer slots at the FLIX bundle-signature level; it still does not prove a
table-base/index mapping for wrapper opcodes `10001..10009`.

The `0x7003ce3c` owner now has a separate byte-verified buffer-record-shaped
field validator island. Standard Xtensa instructions read and zero-check an
`a2`-based record at `+0x08`, `+0x0c`, `+0x10`, `+0x1c`, `+0x20`, `+0x24`,
`+0x28`, `+0x34`, and `+0x38`, with byte fields at `+0x39/+0x3a/+0x3b` and a
low `l16ui` reload at `+0x00`. These offsets overlap the high half of the
0x40-byte VPU buffer-shaped record layout, including plane length/pointer
fields from the kernel reference `struct vpu_buffer`. This establishes a native
buffer-record validation lead distinct from the wrapper operand parser. Together
with the kernel/provider boundary, this closes the INFO12/INFO13 proposition for
the current primitive model: `INFO12` is the firmware-visible descriptor count
capped by the provider below `0x21`, and `INFO13` is the kernel-copied
0x40-byte descriptor array IOVA consumed through native record-shaped accesses.

`analyze_apunn_elf.py` also emits a reproducible standard field-access cluster
scan. It walks only FLIX-correct instruction boundaries, decodes standard
24-bit `l8ui/l16ui/l32i/s8i/s16i/s32i` instructions, and groups them by
`.xt.prop` owner plus base register. The current
top clusters include `0x7003b468/a2`, `0x70039cfc/a2`, and the byte-verified
`0x7003ce3c/a2`, giving a prioritized list of 0x40-record-shaped leads for the
INFO13 record-layout correlation pass without depending on a decompiler.
The same pass now emits `.xt.prop` loop-target candidates near those owners.
`0x7003c102` inside the `0x7003b468/a2` cluster remains the strongest
record-shaped loop-target lead: it is a clean `0xaa`-byte
`insn|loop_target|no_reorder` run. FLIX-boundary-aware scanning now separates
two facts: the surrounding `0x7003b468` owner reads descriptor-shaped `a2`
fields within the 0x40 record window (`+0x02..+0x3d` samples), while the
`0x7003c102..0x7003c1ac` loop-target body exposes only six boundary-visible
core stores to the stack (`a1+0x250..0x2bc`) plus FLIX128/64 bundles. The
companion LOOP scan found no byte-aligned hardware LOOP to `0x7003c102`, no
exact standard branch back-edge, and no boundary-visible `a2 += 0x40`. For the
current primitive model, this closes INFO12/INFO13 at the ABI plus record-layout
level: count is `INFO12` / `buffer_count`, provider-gated below `0x21`, and
stride is the kernel-copied `INFO13` `struct vpu_buffer[]` layout with 0x40
records. Naming a firmware-local bundle-interior count register remains
FLIX/TIE slot-decoder work, not a blocker for the descriptor-count proposition.
`tools/apunn_loop_scan.py` remains useful as a byte-aligned LOOP negative-control
and regression check; it is not a FLIX slot decoder. `0x7003d423` inside
`0x7003ce3c/a2` is downgraded but no longer opaque: the corrected sweep shows
the target itself is also a core24 item, while the surrounding block remains
short branch targets, unreachable gaps, and `insn|data` mixed runs, making it
more likely a switch/error-tail lead than the descriptor array walk.

IDA Pro MCP state after reloading the same ELF as **ELF for Xtensa** (not raw
`Binary File`): processor `XTENSA`, 32-bit, sections mapped at the ELF VAs
above, saved IDB
`../13-apusys-ioctl-surface/firmware/apunn/apunn_core0_full.elf.i64`. Running
`tools/ida_apply_apunn_xt_prop.py` against
`/tmp/apunn_core0_full_analysis_refs.json` creates/names `.xt.prop`-bounded
entry candidates up to the safe `next_entry_delta <= 0x2000` threshold. Current
IDA count is `663` functions, including:

| Address | IDA name | Note |
|---:|---|---|
| `0x70006794` | `apunn_elf_entry_INFO16_70006794` | kernel `INFO16` / ELF entry, `.xt.prop` size `0xac` |
| `0x70006590` | `apunn_early_helper_70006590` | loads `*(a2+0x30)+0x18` into `a10`, calls `0x70007440`, returns 0 |
| `0x70007440` | `apunn_early_dynamic_dispatch_70007440` | starts `entry a1, 0x60`, reads `ccount`, repeatedly calls function pointer from `a12+0`/`a8`, and calls local helper `0x700068c0` |
| `0x70015e98` | `apunn_flk_pointer_table_owner_70015e98` | owner for the three 12-entry FLK pointer runs |
| `0x70081d50` | `apunn_ann_pointer_table_owner_70081d50` | owner for the 31-entry ANN pointer run |
| `0x700301d8` | `apunn_dispatcher_like_locateBuffer_700301d8` | byte-verified `locateBuffer` trampoline; reaches operand parser at `0x70030a0c` |
| `0x7003ce3c` | `apunn_buffer_record_high_field_validator_candidate_7003ce3c` | byte-verified 0x40-record-shaped high-field/null-check island |

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

### Step 1: Extract and load the full embedded ELF

Use `parse_vpu_image.py` first to verify the APUNN preload metadata and embedded
ELF offset. Then extract the core-0 ELF from `cam_vpu2.img` at file offset
`0x5e8`:

```sh
13-apusys-ioctl-surface/tools/parse_vpu_image.py \
  /tmp/ota_V260523_cam_vpu/cam_vpu2.img \
  --head-offset 0x200 --headers 1 --algo apu_lib_apunn \
  --json /tmp/ota_V260523_cam_vpu/cam_vpu2_apunn_preload.json \
  --list-all

dd if=/tmp/ota_V260523_cam_vpu/cam_vpu2.img \
  of=/tmp/apunn_core0_full.elf bs=1 skip=$((0x3b4+0x234)) status=none
```

Recommended Ghidra load:

```sh
rm -rf /tmp/ghidra_apunn_full_elf /tmp/apunn_ghidra_full_elf_export
mkdir -p /tmp/ghidra_apunn_full_elf
/opt/homebrew/opt/ghidra/libexec/support/analyzeHeadless \
  /tmp/ghidra_apunn_full_elf ApunnFullElf \
  -import /tmp/apunn_core0_full.elf \
  -analysisTimeoutPerFile 300 \
  -scriptPath 13-apusys-ioctl-surface/tools \
  -postScript GhidraApunnExport.java /tmp/apunn_ghidra_full_elf_export
```

Import as `Xtensa:LE:32:default`. The ELF section table already maps `.rodata`
at `0x70000000`, `.text` at `0x70006500`, `.data/.dram0.data` at `0x7ff04000`,
and `.dram_op.data` at `0x7ff3b000`, so separate raw block import is not the
primary workflow anymore.

Ghidra 12.0.4 identifies hundreds of functions, but the decompiler is only a
hint on this MediaTek Xtensa target. Many APUNN ranges use Xtensa TIE/FLIX
constructors that Ghidra does not resolve, so the `.xt.prop` table and section
layout are the authority for instruction boundaries. In particular,
`0x70006794` is both the kernel `INFO16` target and the ELF entry; `.xt.prop`
marks it as a `0xac`-byte instruction range even if the decompiler stops at
`flix()`.

Recommended IDA Pro load:

1. Open `/tmp/apunn_core0_full.elf` as **ELF for Xtensa**. Do not use raw
   `Binary File` unless the ELF loader fails.
2. Verify `ida_ida.inf_get_procname()` returns `XTENSA`; `0x70006794` should
   decode as `entry sp, 0x20`.
3. Generate the reproducible metadata:

```sh
13-apusys-ioctl-surface/tools/analyze_apunn_elf.py \
  /tmp/apunn_core0_full.elf \
  --json /tmp/apunn_core0_full_analysis_refs.json \
  --markdown /tmp/apunn_core0_full_analysis_refs.md
```

4. In IDA, run:

```python
exec(open(
  "13-apusys-ioctl-surface/tools/ida_apply_apunn_xt_prop.py"
).read())
```

The script creates/names only `.xt.prop`-backed `entry` candidates with a
bounded `next_entry_delta <= 0x2000` by default, defines the pointer runs, names
critical strings, annotates selected `L32R` literal refs and loop-target
candidates, annotates the known APUNN entry/dispatch addresses, and adds the
global FLIX length-rule validation to the `.text` start. This keeps the IDB
useful without forcing every TIE/FLIX-heavy `.xt.prop` range into IDA code
items.

### Step 2: Map the MMIO dispatch interface

The kernel writes these MMIO registers before signaling the DSP:

| Register | Content | Firmware use |
|---|---|---|
| `XTENSA_INFO01` | `0x24` (`DO_D2D_EXT`) | Kernel/resident-firmware command selector |
| `XTENSA_INFO11` | Slot index (0–2) | Preload set selector |
| `XTENSA_INFO12` | `buffer_count` from `request+0x35` (max `0x20`) | Descriptor array length |
| `XTENSA_INFO13` | Kernel-copied descriptor array IOVA | Pointer to `struct vpu_buffer[]` copy |
| `XTENSA_INFO14` | `request+0x40` (settings IOVA when libvpu mode) | Optional settings pointer |
| `XTENSA_INFO15` | `request+0x38` (settings length when libvpu mode) | Optional settings length |
| `XTENSA_INFO16` | `pre->a.mva + pre->a.entry_off` | APUNN preload ELF entry address |
| `XTENSA_INFO19` | `pre->a.iram_mva` | IRAM load address |

The `INFO01 == 0x24` selector is probably consumed by the resident VPU command
firmware before control reaches the selected preload entry. The APUNN ELF itself
therefore should not be expected to contain a direct `INFO01` comparison. For
APUNN static work, the first boundary to prove is: `INFO16` enters
`0x70006794`, the entry builds or receives the preload context, and later code
consumes `INFO12..15` / the descriptor-backed DSP command buffer.

### Step 3: Answer the security questions

These are the specific questions the firmware binary needs to resolve, in
priority order:

Current partial answers from the ELF pass:

- Q1 is closed at the current Java/HardwareBuffer visibility layer and still
  open only below that layer. `0x70044b74` is now the top
  DMA/iDMA schedule/wait owner: it contains the schedule error, wait error,
  `dmaif.c`, descriptor range, and DRAM data-buffer validation string refs in
  one FLIX-heavy owner. This identifies the firmware schedule/wait layer to
  instrument. Runtime evidence now says the tested completed shapes expose no
  post-`run_async`, pre-`wait_cmd` field sequencing to Java: a 10 ms busy-poll
  saw no settings/output/data-desc mutation, and `wait_cmd` returned success
  before the normal completion bytes were visible in the shared mapping.
- Q2 has a real firmware-side op vocabulary now, but the 63-entry
  `.dram_op.data` ANN op-name table is not static dispatch proof. It is a table
  of `.rodata` strings followed by zero tail bytes; the analyzer finds no
  reproducible raw-u32 or L32R refs from code/data to `.dram_op.data`. The
  31-entry ANN code-pointer run at `0x70000b80` is stronger code evidence: it
  resolves to owner `0x70081d50` and has literal-slot signatures, but still no
  table-base value ref proving indexed opcode dispatch. The
  byte-verified `0x700301d8` `locateBuffer` trampoline and `0x70030a0c`
  operand-record decode island now connect the parser owner `0x700304f8` to
  FLIX-bundle-interior FLK/ANN pointer-run slot signatures. That is the
  strongest current Q2 static path, but it still lacks a table-base/index
  mapping for wrapper/runtime
  `10001..10009` opcodes.
- Q3 is closed for the current INFO12/INFO13 proposition at the
  kernel/provider boundary plus record-layout level: `buffer_count` is capped
  below `0x21` before firmware, runtime shows `0` suppresses
  descriptor-following state writeback while nonzero tested counts enter the
  descriptor path, and the ELF now has native 0x40-record-shaped descriptor
  validation evidence. FLIX-correct sweeps now show the `0x7003c102` loop body
  as stack-spill core ops plus FLIX bundles, not direct descriptor-field loads.
  The firmware-local count-register name remains behind TIE/FLIX slot semantics;
  the count/stride proposition for the exposed interface is closed by
  `INFO12=buffer_count` and the 0x40-stride `INFO13` descriptor array.
- Q4 has runtime evidence that `settings+0x08` bounds the visible output fill.
  Static output-shape validation owners are now identified from output diagnostic
  strings, including `0x700184b4`, `0x70015e98`, `0x700a7be0`, and
  `0x7009b5f0`; the exact runtime `settings+0x08` output-fill loop is still not
  identified.

#### Q1: DMA write timing and sequencing

For the current wrapper-shaped `XTENSA_ANN_VERSION` trigger, the Java-visible
answer is: no useful post-`run_async`, pre-`wait_cmd` completion-store window
was observed. The new completion-poll probe tested `0x40` and `0x1000` output
sizes and sampled settings flags, `settings+0x30`, output words, and data-desc
word 0 for 10 ms after `run_async`; every field stayed at its pre-async value.
After `wait_cmd`, normal completion was visible (`settings=0x7`, bounded output
fill, descriptor cleanup).

The lower-level firmware question remains separate: whether the internal DMA
engine performs one burst or sequenced stores before host wait/cache
synchronization. That is now a FLIX/iDMA instrumentation question, not a blocker
for the Java-layer allocator race ranking.

Look for: loops that iterate over descriptor entries, conditional writes gated
on intermediate results, explicit DSP wait/sleep/barrier instructions between
DMA stores.

Current static anchor: `0x70044b74` is the best DMA/iDMA schedule/wait owner
candidate. The corrected sweep confirms it starts on a standard entry core op
and then mixes FLIX128/64 bundles with sparse core/density items. Use it for
runtime tracing or deeper FLIX/TIE work; do not infer completion write timing
from string ownership alone.

#### Q2: Opcode dispatch table

The wrapper generates opcodes `10001..10009` in the Xtensa code section. The
firmware presumably has a switch or table dispatch, but `.dram_op.data` is not
that proof: it currently resolves as a 63-entry ANN op-name vocabulary table,
with no raw-u32 or L32R code reference to the table. The strongest Q2 static
path is now `0x700301d8 -> 0x70030a0c -> 0x700304f8`: it decodes
`a2+0x49/+0x4a` bits `2..5`, branches to `0x70020ba2` and
`0x7003125a/0x7003126a`, and the same owner has FLIX-bundle-interior L32R
literal-slot signatures to FLK and ANN pointer-run slots. Current pointer-run
reachability still separates slot signatures from table-base/indexed dispatch:
neither the FLK nor ANN runs currently have a table-base value ref that proves
wrapper-opcode index mapping. Open
runtime questions:
- which `10001..10009` opcodes produce the most DMA traffic?
- are there opcodes that write to multiple descriptor-backed buffers in
  sequence?
- are there opcodes that perform iterative computation with repeated output
  writes?
- are there opcodes that read from one descriptor and write to another
  (copy-style operations)?

Any of these would increase the exploitable DMA window.

#### Q3: Descriptor bounds checking

`INFO12` (buffer_count) + `INFO13` (descriptor array IOVA) define the
firmware's view of the descriptor array. The current answer for the exposed
kernel interface is:
- `buffer_count` is provider-gated below `0x21` before MMIO, so userland cannot
  enlarge the firmware-visible descriptor count through this path.
- `INFO13` is the kernel-copied 0x40-byte descriptor array, not the original
  user buffer.
- Native firmware code has verified 0x40-record-shaped validation/consumption
  islands. The `0x7003c102` loop body does not itself expose descriptor loads;
  it exposes stack stores plus FLIX bundles. Count is sourced from INFO12 at the
  ABI/kernel/provider boundary, and stride is the 0x40 INFO13 descriptor layout.
  FLIX/TIE slot semantics may still name a local count register, but that is
  separate from the exposed descriptor-count primitive.

Remaining descriptor questions are size/plane validation quality and DMA owner
timing, not whether the current interface exposes an unchecked descriptor-count
primitive.

#### Q4: Output write bounds

`settings+0x08` is known to bound output fill from the kernel side. Does the
firmware enforce this bound internally, or does it rely on the caller? If the
firmware derives its own write size from the code section (e.g., from operand
metadata), a mismatch between the declared `output_size` and the firmware's
computed size could produce an overflow.

Static owner map status: `analyze_apunn_elf.py` now emits
`output_validation_investigations` from rodata32 and L32R-backed output
diagnostics. The strongest current anchors are:

| owner | evidence | interpretation |
|---:|---|---|
| `0x700184b4` | `Inconsistent output size`, invalid output args/data type/batch diagnostics | broad output-shape validator |
| `0x70015e98` | L32R ref to `Inconsistent output size` | output-size validation owner near the ANN/FLK path |
| `0x700a7be0` | direct rodata32 ref to `Inconsistent output size` at `0x700a7ce8` | compact output-size validator |
| `0x7009b5f0` | direct rodata32 refs to `Inconsistent output height` at `0x7009c0dc` and `0x7009c33c` | output-height validator |

This identifies firmware output-shape validation owners, not the completed
wrapper replay's output-fill loop. The remaining Q4 static task is to connect
one of these validators, or another nearby owner, to the runtime
`settings+0x08` bound and the actual write/fill path.

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
| VPU image parser | `../13-apusys-ioctl-surface/tools/parse_vpu_image.py` | Parses preload metadata, carves raw segments, and reports embedded ELF offsets; use `--head-offset 0x200 --headers 1` for V260523 `cam_vpu2.img` |
| APUNN ELF analyzer | `../13-apusys-ioctl-surface/tools/analyze_apunn_elf.py` | Emits section map, `.xtensa.info`, `.xt.prop` property runs, `.xt.prop`-backed function-entry candidates, key address owners, global FLIX length-rule validation, byte-verified standard Xtensa islands, FLIX-correct boundary sweeps, `.text`→`.rodata` suffix refs, PC-relative `L32R` literal refs, focused loop investigations, L32R string-owner clusters, output-validation owner investigations, DMA/descriptor critical-string status, pointer runs plus reachability, `elf_verification_1234`, ANN op name table, interesting strings, JSON, and Markdown |
| Byte-aligned hardware-loop scanner | `../13-apusys-ioctl-surface/tools/apunn_loop_scan.py` | Regression/negative-control scanner for `LOOP/LOOPNEZ/LOOPGTZ`; confirms no byte-aligned LOOP to `0x7003c102` and the downgraded `0x7003d3ea -> 0x7003d423` positive control |
| IDA `.xt.prop` applier | `../13-apusys-ioctl-surface/tools/ida_apply_apunn_xt_prop.py` | Applies analyzer JSON to an IDA Xtensa ELF IDB: bounded function creation, key names/comments, pointer-run dwords/xrefs plus reachability comments, critical-string annotations, selected `L32R` refs, loop-target candidates, focused loop notes, global FLIX length-rule and FLIX-correct sweep comments, L32R string-owner clusters, output-validation comments, `elf_verification_1234` comments, and byte-verified standard-island comments |
| Ghidra export script | `../13-apusys-ioctl-surface/tools/GhidraApunnExport.java` | Headless adjunct for function/string/decompiler snapshots from `/tmp/apunn_core0_full.elf`; decompiler output is advisory only |
| Allocator gap profiler | `../13-apusys-ioctl-surface/poc/ApusysIoctlProbe.java` | Active; 8+ probe modes |
| Firmware-coupled gap reuse | `--run-cmd-vpu-xrp-mem-free-race-completed-gap-reuse-iova` | Ready to re-run with new shapes |
| Completion write poll | `--run-cmd-vpu-xrp-completion-poll-iova` | Runtime negative for Java-visible pre-wait field sequencing |
| Wrapper static analysis | `../13-apusys-ioctl-surface/APUNN_SETTINGS_ABI.md` | Complete for current scope |
| Kernel primitive handoff | `../13-apusys-ioctl-surface/HANDOFF_KERNEL_PRIMITIVE.md` | Active closure artifact |
| Allocator controllability | `../13-apusys-ioctl-surface/ALLOCATOR_CONTROLLABILITY_OPPORTUNITY.md` | Active experiment loop |

## Acceptance criteria

The current foundation disassembly stage is complete when:

1. The embedded `apu_lib_apunn` ELF is loaded with the correct section map and
   `INFO16`/ELF entry `0x70006794` annotation.
2. The APUNN preload entry and first context/descriptor parser path are
   identified far enough to connect `INFO12..15` and the descriptor-backed DSP
   command buffer to firmware code.
3. The refs contain `elf_verification_1234` with four entries covering
   settings/output-fill, descriptor validation/use, iDMA owner sequencing, and
   opcode parser correlation.
4. Q3 (descriptor bounds) has a concrete answer for the current interface:
   kernel/provider `buffer_count` cap plus native 0x40-record layout evidence
   close INFO12/INFO13, with FLIX-hidden loop internals tracked separately
5. The IDA applier consumes the same refs and writes the 1-4 verification
   summary to the active Xtensa ELF IDB.

Full Q1 timing, exact Q4 runtime output-fill owner attribution, and final
`10001..10009` indexed-dispatch mapping are deeper firmware follow-ups. They are
not prerequisites for this foundation refs/handoff stage. `INFO01 == 0x24`
should be treated as the kernel/resident-firmware dispatch selector unless a
direct APUNN-side read is later found.
