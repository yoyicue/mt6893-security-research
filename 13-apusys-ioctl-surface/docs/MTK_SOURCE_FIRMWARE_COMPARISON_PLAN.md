# MTK Source vs APUNN Firmware Comparison Plan

Date: 2026-06-15

Execution artifact:
`MTK_SOURCE_FIRMWARE_COMPARISON_MATRIX.md`

The first concrete pass has been executed there: source contracts for
INFO12/13, INFO14/15, INFO16/19, and completion/wait were extracted and mapped
to current OTA APUNN firmware evidence.

## Stage Closure

The current APUNN firmware disassembly stage is closed at the reproducible
foundation level:

- the current OTA `apunn_core0_full.elf` is stored under
  `../firmware/apunn/`
- the IDA Xtensa ELF database is saved beside the ELF
- `analyze_apunn_elf.py` regenerates JSON/Markdown refs from the ELF and
  `.xt.prop`
- `ida_apply_apunn_xt_prop.py` reapplies the refs into the live IDA database
- FLIX widths are validated from `.xt.prop`: `0xe` is FLIX128 and `0xf` is
  FLIX64
- INFO12/INFO13 is closed for the current kernel/provider plus record-layout
  model

This does not claim full APUNN firmware decompilation. The next phase should use
the MTK reference source to generate risk hypotheses, then require current OTA
firmware evidence before accepting or rejecting each hypothesis.

## Ground Rule

Reference source is a hypothesis generator. Current OTA firmware is the
authority.

A source finding is useful only after it is mapped to at least one firmware-side
evidence class:

- an APUNN ELF address, owner candidate, or `.xt.prop` run
- a string or L32R/rodata reference in the current ELF
- a buffer-record field access or FLIX-correct sweep item
- a pointer run, branch target, or parser path in `apunn_core0_full_analysis_refs`
- an IDA comment/name written by `ida_apply_apunn_xt_prop.py`
- a live-device probe result from the existing PoC tools

## Source Roots

The `/tmp` path on this host resolves through `/private/tmp`. The current source
root to audit is:

`/private/tmp/generic_kernel_mediatek_alps`

Primary APUSYS/VPU source anchors:

| Source file | Why it matters |
|---|---|
| `drivers/misc/mediatek/apusys/vpu/4.0/vpu_main.c` | ioctl request admission, `buffer_count` cap, preload flag handling, VPU binary loader |
| `drivers/misc/mediatek/apusys/vpu/4.0/vpu_ioctl.h` | user/kernel request ABI, `struct vpu_buffer`, `buffer_count`, preload flag |
| `drivers/misc/mediatek/apusys/vpu/p1/vpu_hw.c` | D2D and D2D_EXT register handoff into firmware |
| `drivers/misc/mediatek/apusys/vpu/p1/vpu_hw.h` | INFO register contract comments for descriptor and settings handoff |
| `drivers/misc/mediatek/apusys/vpu/p1/vpu_reg.h` | firmware command IDs, including D2D_EXT |
| `drivers/misc/mediatek/apusys/vpu/p1/vpu_dump.c` | debug dump interpretation of request buffers |
| `drivers/misc/mediatek/apusys/vpu/4.0/vpu_debug.c` | INFO register dump coverage |
| `drivers/misc/mediatek/apusys/midware/1.1/` | APUSYS midware memory, command, queue, and user dispatch context |

Useful first-pass source search:

```sh
rg -n "vpu_execute_d2d|VPU_CMD_DO_D2D_EXT|XTENSA_INFO1[2-9]|buffer_count|struct vpu_buffer|ALG_PRELOAD|vpu_init_bin|img-head|pre-bin|bin-phy-addr" \
  /private/tmp/generic_kernel_mediatek_alps/drivers/misc/mediatek/apusys \
  -g '*.[ch]'
```

## Comparison Workflow

1. Source contract extraction

   Extract only facts that influence firmware-visible memory, register, or
   command semantics:

   - command ID written to firmware
   - INFO register values and ordering
   - ABI struct layout and field sizes
   - user-controlled lengths, counts, flags, IOVAs, and offsets
   - kernel-side caps, copies, zeroing, and error exits
   - lifetime and ownership transitions around `run`, `wait`, and `mem_free`

2. Risk hypothesis generation

   For each source fact, write a firmware-verifiable question. Examples:

   - Does firmware trust INFO12 as a descriptor count after the kernel cap?
   - Does firmware ever read beyond the INFO13 0x40-byte descriptor records?
   - Does firmware use INFO14/INFO15 when settings are zero or short?
   - Does D2D_EXT route through a parser path distinct from plain D2D?
   - Are output size, plane count, or data type checks performed before DMA?
   - Is completion writeback a single burst or sequenced stores?
   - Can one opcode create a longer DMA window than `XTENSA_ANN_VERSION`?

3. Firmware evidence mapping

   Map each hypothesis to existing APUNN evidence before adding new tooling:

   | Source-side topic | Existing firmware evidence | Next firmware check |
   |---|---|---|
   | INFO12 descriptor count | Q3 closure in `HANDOFF_FIRMWARE_DISASSEMBLY.md`; 0x40-record clusters; `0x7003c102` FLIX-correct sweep | Keep as closed unless source reveals another count path |
   | INFO13 descriptor array | native 0x40-record access clusters and `0x7003ce3c` validator island | Add exact source field names to analyzer output if needed |
   | INFO14/INFO15 settings tuple | wrapper ABI notes and current runtime settings mutation evidence | Search firmware for settings-length validation and output-fill loop owner |
   | INFO16 preload entry | ELF entry `0x70006794`; source D2D_EXT handoff | Confirm source command path against parser owner `0x700301d8 -> 0x70030a0c -> 0x700304f8` |
   | D2D_EXT command | kernel source writes command `0x24`; APUNN side direct read not yet proven | Search for INFO01/command-register reads or command dispatch strings |
   | ANN/FLK code-pointer runs | refs identify FLK/ANN runs and parser-owner slot signatures | Do not claim opcode index mapping until table-base/index evidence exists |
   | output validation | `output_validation_investigations` maps static validators | Connect one validator to the runtime `settings+0x08` output-fill behavior |
   | DMA/iDMA timing | top DMA/iDMA owner `0x70044b74` from string clusters | Use source DMA descriptor rules to pick firmware owners for runtime instrumentation |

4. Firmware-first acceptance

   A row is accepted only when the firmware evidence is specific enough to
   reproduce:

   - exact source file and symbol
   - exact firmware address or owner
   - exact analyzer JSON key or Markdown section
   - exact IDA comment target if the result is applied to the IDB
   - exact live probe mode if runtime evidence is required

## Risk Work Items

### R1: D2D_EXT register contract

Source premise:

- `vpu_execute_d2d()` writes INFO12 as `buffer_count`
- INFO13 is the copied descriptor array IOVA
- INFO14/INFO15 are the settings pointer and size
- INFO16 is the selected preload entry
- INFO19 is IRAM MVA
- command ID is D2D_EXT for preload requests

Firmware checks:

- confirm APUNN entry and parser path consume the expected ABI shape
- search for command-register reads that can bind D2D_EXT to APUNN code
- annotate any firmware owner that reads settings length before using settings

Risk focus:

- settings length trust
- stale or reused descriptor IOVA
- command path mismatch between kernel source and APUNN firmware

### R2: Descriptor record layout

Source premise:

- `struct vpu_buffer` is copied as an array capped by `buffer_count`
- current primitive model uses 0x40-byte descriptor records

Firmware checks:

- keep INFO12/INFO13 closed at the ABI plus record-layout level
- map source field names onto the firmware access clusters
- prioritize owners with 0x40-window loads/stores and output diagnostics

Risk focus:

- plane or size validation gaps
- descriptor fields consumed after partial validation
- fields rewritten by firmware before completion

### R3: Settings and output fill

Source premise:

- kernel passes settings pointer and length separately from descriptors
- runtime evidence shows `settings+0x08` influences visible output fill bounds

Firmware checks:

- search APUNN refs for settings-length validation owners
- connect `output_validation_investigations` to the runtime output-fill loop
- identify whether output-fill uses descriptor size, settings size, or both

Risk focus:

- short settings buffer
- mismatched settings length vs descriptor output size
- output fill continuing after shared IOVA replacement

### R4: Opcode dispatch and slow DMA shapes

Source premise:

- wrapper opcodes `10001..10009` reach firmware through the APUNN command buffer
- the source does not by itself prove firmware opcode mapping

Firmware checks:

- keep `0x700301d8 -> 0x70030a0c -> 0x700304f8` as the current static parser path
- treat FLK/ANN pointer-run refs as slot signatures, not indexed mapping
- use runtime probes to correlate opcode families with DMA timing

Risk focus:

- opcodes that perform multi-buffer writes
- opcodes that create a longer write window
- opcodes that sequence settings, output, and descriptor writeback separately

### R5: DMA/iDMA owner behavior

Source premise:

- APUSYS/VPU source names DMA descriptor and scheduling concepts
- APUNN firmware contains iDMA schedule/wait/error string clusters

Firmware checks:

- use source naming to rank firmware iDMA owners
- keep `0x70044b74` as the current top static owner
- look for descriptor range checks and completion paths near that owner

Risk focus:

- delayed DMA completion after kernel-visible wait state
- descriptor range validation that differs from kernel caps
- undocumented DMA target or side-channel buffers

## Deliverables For The Next Phase

| Artifact | Required content |
|---|---|
| Source-risk matrix | source symbol, source path, user-controlled fields, kernel cap, firmware question |
| Firmware evidence matrix | firmware owner/address, refs JSON key, IDA comment target, confidence |
| Analyzer updates | source-derived field names or risk tags only when tied to firmware evidence |
| Handoff update | accepted findings and rejected hypotheses with evidence |
| Runtime probe update | only if a source-derived hypothesis requires device timing evidence |

## First Concrete Pass

1. Read these source ranges:

   - `vpu_main.c`: request validation, `buffer_count`, preload flag, binary load
   - `vpu_ioctl.h`: `struct vpu_buffer` and request ABI
   - `vpu_hw.c`: `vpu_execute_d2d()`, INFO register writes, wait/completion
   - `vpu_hw.h`: INFO register contract comments
   - `vpu_reg.h`: command constants

2. Produce a small source-risk matrix for:

   - INFO12/INFO13 descriptor handoff
   - INFO14/INFO15 settings handoff
   - INFO16/INFO19 preload handoff
   - completion/wait registers

3. For each row, add one of:

   - accepted by current APUNN firmware refs
   - contradicted by current APUNN firmware refs
   - not yet verifiable statically
   - requires runtime probe

4. Only after that, modify analyzer/IDA refs for rows that have firmware-side
   addresses or owners.

## Non-Goals

- Do not re-open manual decoding of `0x7003c102` as a primary task.
- Do not treat Ghidra decompiler output as authoritative on FLIX-heavy ranges.
- Do not claim source behavior is present in the current OTA firmware without an
  APUNN-side address, owner, string, ref, or live-device observation.
- Do not add hashes to Markdown artifacts.
