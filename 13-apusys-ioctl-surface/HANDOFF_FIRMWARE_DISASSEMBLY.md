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

Main flat-window hashes:

| Core mask | Carved file | SHA-256 |
|---|---|---|
| `0x61` | `/tmp/ota_V260523_cam_vpu/apunn_carve/apu_lib_apunn_h0_pre0_prog_off3b4.bin` | `531198afb182e868919163fdf701591dcd212e52dc7cfb71fd512d7faa4c63db` |
| `0x62` | `/tmp/ota_V260523_cam_vpu/apunn_carve/apu_lib_apunn_h0_pre4_prog_off32d1e0.bin` | `a9d09d45af5cc67c88c7908b9e5f85c0b2a8d1a6dd0ee781a0851cd1b5ee46e7` |
| `0x64` | `/tmp/ota_V260523_cam_vpu/apunn_carve/apu_lib_apunn_h0_pre8_prog_off65a00c.bin` | `1220a7648829ec4bf5a390c777fe0e47f57b9e8ea119d8f032b03a28bd2c98ee` |

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

`/tmp/apunn_core0_full.elf` SHA-256:
`69658bfe18e8084e44da165ebc326c01ce9a2e672a059ae5a706ce5e397c3c88`.
A live physical reserved-memory dump is still useful to verify the exact
LK-merged layout, but it is no longer a blocker for core-0 static analysis.

The current ELF, IDA IDB, and analyzer outputs are persisted under
`13-apusys-ioctl-surface/firmware/apunn/`:

| Artifact | Path | SHA-256 |
|---|---|---|
| Core-0 full ELF | `13-apusys-ioctl-surface/firmware/apunn/apunn_core0_full.elf` | `69658bfe18e8084e44da165ebc326c01ce9a2e672a059ae5a706ce5e397c3c88` |
| IDA Pro Xtensa IDB | `13-apusys-ioctl-surface/firmware/apunn/apunn_core0_full.elf.i64` | `4da91a806b7adf894f027adddec0bb60ed5b107409b4f4c8b86e4049fd247e09` |
| IDA sidecars | `13-apusys-ioctl-surface/firmware/apunn/apunn_core0_full.elf.{id0,id1,id2,nam,til}` | see `firmware/apunn/README.md` |
| Analyzer JSON | `13-apusys-ioctl-surface/firmware/apunn/apunn_core0_full_analysis_refs.json` | `a53bd6519ccd675e7887bf064a2ced935656c64aab16a2b54a36261a7d1a13a5` |
| Analyzer Markdown | `13-apusys-ioctl-surface/firmware/apunn/apunn_core0_full_analysis_refs.md` | `8adf839c190449351f962c413a3ee3111259279810f056e058ddbc924d28c956` |

Current core-0 ELF facts from
`13-apusys-ioctl-surface/tools/analyze_apunn_elf.py`:

| Section | VA | File offset | Size |
|---|---:|---:|---:|
| `.rodata` | `0x70000000` | `0x3b4` | `0x6500` |
| `.text` | `0x70006500` | `0x68b4` | `0x2645f0` |
| `.data` | `0x7ff04000` | `0x26aea4` | `0xb4` |
| `.dram0.data` | `0x7ff040c0` | `0x26af64` | `0x17c4` |
| `.dram_op.data` | `0x7ff3b000` | `0x26c728` | `0x218` |
| `.xt.prop` | n/a | `0x26c940` | `0xb9aa8` |

The Xtensa property table is the strongest non-decompiler source for code
ranges. It contains `63374` entries, including `54282` instruction ranges,
`14383` branch targets, and `8838` unreachable/align entries. Around the
kernel-provided entry, it marks `0x70006794` as a real instruction range:

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
  consistent with branch-table dispatch. This is structural dispatch evidence,
  not a proven mapping from table index to the 63 ANN op-name entries.
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
- `Data buffer does not fit in DRAM` has no aligned refs but has six all-byte
  suffix-pointer samples at owners `0x700c13b0`, `0x700cc080`, `0x700cda20`,
  `0x7016bc40`, `0x70262690`, and `0x70262a00`. Because these are unaligned
  byte-window hits, treat them as disassembly leads rather than proven literal
  references.

Manual entry-window observation, using radare2 only as an adjunct because this
core has unresolved TIE/FLIX opcodes: the `0x70006794` property range starts
with `entry a1, 0x20`, then a standard-instruction island copies six dwords from
`a12+0x00..0x14` into `a10+0x04..0x18`, and copies `a2+0x44`,
`a2+0x4c`, and `a2+0x50` into `a10+0x28`, `a10+0x1c`, and `a10+0x20`.
Treat this as a preload-context packing clue, not as a complete entry
prototype, until the custom instruction semantics are resolved.

IDA Pro MCP state after reloading the same ELF as **ELF for Xtensa** (not raw
`Binary File`): processor `XTENSA`, 32-bit, sections mapped at the ELF VAs
above, saved IDB
`13-apusys-ioctl-surface/firmware/apunn/apunn_core0_full.elf.i64`. Running
`tools/ida_apply_apunn_xt_prop.py` against
`/tmp/apunn_core0_full_analysis_refs.json` creates/names `.xt.prop`-bounded
entry candidates up to the safe `next_entry_delta <= 0x2000` threshold. Current
IDA count is `663` functions, including:

| Address | IDA name | Note |
|---:|---|---|
| `0x70006794` | `apunn_elf_entry_INFO16_70006794` | kernel `INFO16` / ELF entry, `.xt.prop` size `0xac` |
| `0x70006590` | `apunn_early_helper_70006590` | loads `*(a2+0x30)+0x18` into `a10`, calls `0x70007440`, returns 0 |
| `0x70007440` | `apunn_early_dynamic_dispatch_70007440` | starts `entry a1, 0x60`, reads `ccount`, calls function pointer from `a12+0` when nonzero |
| `0x70015e98` | `apunn_flk_pointer_table_owner_70015e98` | owner for the three 12-entry FLK pointer runs |
| `0x70081d50` | `apunn_ann_pointer_table_owner_70081d50` | owner for the 31-entry ANN pointer run |
| `0x700301d8` | `apunn_dispatcher_like_locateBuffer_700301d8` | `locateBuffer`-related dispatcher candidate |

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
critical strings, and annotates the known APUNN entry/dispatch addresses. This
keeps the IDB useful without forcing every TIE/FLIX-heavy `.xt.prop` range into
IDA code items.

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

- Q1 is still unresolved statically. The ELF contains `dma_barrier`,
  `iDMA schedule error`, `iDMA wait error`, and
  `../vp6-ann/libcommon/src/idma_mvpu6/dmaif.c`, so the right DMA subsystem is
  present. However, the critical-string direct-reference scan finds no aligned
  or all-byte `.text` pointer to the iDMA/dmaif/descriptor assertion strings,
  and Ghidra does not yet produce a reliable schedule/wait loop decompilation
  on the TIE/FLIX-heavy ranges. Runtime remains the strongest evidence: the
  tested completed shape finishes before the Java-layer `mem_free` round trip
  can replace the IOVA.
- Q2 has a real firmware-side op vocabulary now. The 63-entry
  `.dram_op.data` ANN table is distinct from the wrapper/runtime
  `10001..10009` query/status opcodes already tested, so more work is needed to
  connect host opcodes to ANN kernel dispatch entries.
- Q3 is bounded at the kernel/provider boundary: `buffer_count` is capped below
  `0x21` before firmware, and runtime shows `0` suppresses descriptor-following
  state writeback while nonzero tested counts enter the descriptor path. A
  firmware-internal cap/size check has not been proven yet.
- Q4 has runtime evidence that `settings+0x08` bounds the visible output fill;
  the static output-size enforcement path is not identified yet.

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
| VPU image parser | `13-apusys-ioctl-surface/tools/parse_vpu_image.py` | Parses preload metadata, carves raw segments, and reports embedded ELF offsets; use `--head-offset 0x200 --headers 1` for V260523 `cam_vpu2.img` |
| APUNN ELF analyzer | `13-apusys-ioctl-surface/tools/analyze_apunn_elf.py` | Emits section map, `.xt.prop` instruction ranges, `.xt.prop`-backed function-entry candidates, key address owners, `.text`→`.rodata` suffix refs, DMA/descriptor critical-string direct-ref status, pointer runs, ANN op name table, interesting strings, JSON, and Markdown |
| IDA `.xt.prop` applier | `13-apusys-ioctl-surface/tools/ida_apply_apunn_xt_prop.py` | Applies analyzer JSON to an IDA Xtensa ELF IDB: bounded function creation, key names/comments, pointer-run dwords/xrefs, and critical-string annotations |
| Ghidra export script | `13-apusys-ioctl-surface/tools/GhidraApunnExport.java` | Headless adjunct for function/string/decompiler snapshots from `/tmp/apunn_core0_full.elf`; decompiler output is advisory only |
| Allocator gap profiler | `13-apusys-ioctl-surface/poc/ApusysIoctlProbe.java` | Active; 8+ probe modes |
| Firmware-coupled gap reuse | `--run-cmd-vpu-xrp-mem-free-race-completed-gap-reuse-iova` | Ready to re-run with new shapes |
| Wrapper static analysis | `13-apusys-ioctl-surface/APUNN_SETTINGS_ABI.md` | Complete for current scope |
| Kernel primitive handoff | `13-apusys-ioctl-surface/HANDOFF_KERNEL_PRIMITIVE.md` | Active closure artifact |
| Allocator controllability | `13-apusys-ioctl-surface/ALLOCATOR_CONTROLLABILITY_OPPORTUNITY.md` | Active experiment loop |

## Acceptance criteria

The firmware disassembly task is complete when:

1. The embedded `apu_lib_apunn` ELF is loaded with the correct section map and
   `INFO16`/ELF entry `0x70006794` annotation.
2. The APUNN preload entry and first context/descriptor parser path are
   identified far enough to connect `INFO12..15` and the descriptor-backed DSP
   command buffer to firmware code.
3. Q1 (DMA timing) has a concrete answer: single burst, or sequenced with
   measured/estimated inter-store gap
4. Q3 (descriptor bounds) has a concrete answer: firmware-side `buffer_count`
   cap exists or doesn't
5. Findings are written back to `HANDOFF_KERNEL_PRIMITIVE.md`

Q2/Q4/Q5/Q6 are valuable follow-ups but not blocking for the immediate
allocator-reuse race decision. `INFO01 == 0x24` should be treated as the
kernel/resident-firmware dispatch selector unless a direct APUNN-side read is
later found.
