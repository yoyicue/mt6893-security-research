# MTK Source vs APUNN Firmware Comparison Matrix

Date: 2026-06-15

This executes the first concrete pass from
`MTK_SOURCE_FIRMWARE_COMPARISON_PLAN.md`. The reference source is used only to
generate interface and risk hypotheses; current OTA APUNN firmware refs decide
whether a row is accepted, partially accepted, not statically verifiable, or
requires runtime evidence.

## Inputs

| Input | Path |
|---|---|
| MTK reference source root | `/private/tmp/generic_kernel_mediatek_alps` |
| Current APUNN ELF refs | `../13-apusys-ioctl-surface/firmware/apunn/apunn_core0_full_analysis_refs.json` |
| Firmware handoff | `../13-apusys-ioctl-surface/HANDOFF_FIRMWARE_DISASSEMBLY.md` |
| Kernel primitive handoff | `../13-apusys-ioctl-surface/HANDOFF_KERNEL_PRIMITIVE.md` |
| Settings/runtime ABI notes | `../13-apusys-ioctl-surface/APUNN_SETTINGS_ABI.md` |

## Source Contract Facts

| Source fact | Source evidence | Firmware-visible contract |
|---|---|---|
| APUSYS execute path rejects invalid request size/flags and caps `buffer_count` at `VPU_MAX_NUM_PORTS` | `vpu/4.0/vpu_main.c:91..118`; `vpu/4.0/vpu_ioctl.h:9` defines `VPU_MAX_NUM_PORTS = 32` | Firmware-visible descriptor count is capped before D2D_EXT |
| APUSYS execute path forces `ALG_PRELOAD` before `vpu_preempt()` | `vpu/4.0/vpu_main.c:152..158` and `:171..174` | Normal APUSYS execute reaches the preload/D2D_EXT class on this path |
| `struct vpu_buffer` is the native descriptor record | `vpu/4.0/vpu_ioctl.h:302..309` | Descriptor records copied to firmware are 0x40 bytes |
| `struct vpu_request` carries `buffer_count`, `sett_length`, `sett_ptr`, and `buffers[]` | `vpu/4.0/vpu_ioctl.h:335..345` | Kernel exposes descriptors through INFO12/INFO13 and optional settings through INFO14/INFO15 |
| D2D_EXT writes preload entry and IRAM before the common descriptor/settings tuple | `vpu/p1/vpu_hw.c:713..732` | INFO16 is APUNN entry; INFO19 is IRAM MVA; INFO12..15 are common D2D inputs |
| Command ID `0x24` is D2D_EXT | `vpu/p1/vpu_reg.h:15..18` | INFO01 command selector is resident-firmware-facing D2D_EXT |
| D2D command buffer is a per-priority kernel copy, then synced for device | `vpu/4.0/vpu_cmd.c:313..343`; `vpu/p1/vpu_hw.c:704..705` | INFO13 points at the copied `struct vpu_buffer[]`, not the original user request |
| Wait completion is IRQ-driven through INFO17, INFO00, and INFO02 | `vpu/p1/vpu_hw.c:570..623`, `:640..678`; `vpu/4.0/vpu_cmd.c:182..191`, `:265..293` | Host wait completion does not by itself prove all APUNN data writes are sequenced before replacement reuse |
| `wait_command()` wakes on `cmd[prio].done` or times out | `vpu/p1/vpu_hw.c:230..275` | Runtime timing still determines whether a post-submit, pre-wait writeback window exists |

## Descriptor Layout From Source

Derived from `vpu/4.0/vpu_ioctl.h:287..309` under the LP64 kernel ABI:

| Offset | Field | Size |
|---:|---|---:|
| `+0x00` | `port_id` | 1 |
| `+0x01` | `format` | 1 |
| `+0x02` | `plane_count` | 1 |
| `+0x04` | `width` | 4 |
| `+0x08` | `height` | 4 |
| `+0x10` | `planes[0].stride` | 4 |
| `+0x14` | `planes[0].length` | 4 |
| `+0x18` | `planes[0].ptr` | 8 |
| `+0x20` | `planes[1].stride` | 4 |
| `+0x24` | `planes[1].length` | 4 |
| `+0x28` | `planes[1].ptr` | 8 |
| `+0x30` | `planes[2].stride` | 4 |
| `+0x34` | `planes[2].length` | 4 |
| `+0x38` | `planes[2].ptr` | 8 |

Record size is `0x40`. This source layout matches the firmware-side
0x40-record evidence used for the INFO12/INFO13 closure; field-name assignment
should prefer the source offsets above when annotating firmware clusters.

## Source-Risk Matrix

| ID | Source symbol / path | User-controlled fields | Kernel cap or transform | Firmware question |
|---|---|---|---|---|
| SR1 | `vpu_req_check()` in `vpu_main.c` | request size, flags, `buffer_count` | request size must equal `sizeof(struct vpu_request)`; unknown flags rejected; `buffer_count <= 32` | Does APUNN treat INFO12 as the only descriptor count? |
| SR2 | `vpu_cmd_buf_set()` and `vpu_execute_d2d()` | `buffers[]` copied from request | copy length is `sizeof(struct vpu_buffer) * buffer_count`; destination is per-priority command buffer; `VPU_CMD_SIZE` bound applies | Does firmware parse INFO13 as 0x40-byte records and stay within INFO12 count? |
| SR3 | `struct vpu_buffer` in `vpu_ioctl.h` | `port_id`, `format`, `plane_count`, dimensions, plane stride/length/ptr | no source-side per-field semantic validation in the first-pass path | Which descriptor fields are validated before output/DMA use? |
| SR4 | `vpu_execute_d2d()` INFO14/INFO15 writes | `sett_ptr`, `sett_length` | first-pass source path writes raw values after request admission; no settings-specific cap found here | Does firmware honor settings length before reading or writing settings-derived output? |
| SR5 | `vpu_execute_d2d()` INFO16/INFO19 writes | user selects algorithm/preload indirectly through APUSYS command path | kernel chooses preload entry and IRAM MVA from loaded metadata | Does APUNN entry consume the expected descriptor/settings ABI after resident D2D_EXT dispatch? |
| SR6 | `vpu_reg.h` D2D_EXT command and `vpu_cmd()` | command class selected by forced preload path | INFO01 receives `0x24`; interrupt is raised after write barrier | Can current APUNN ELF prove a direct read of the D2D_EXT command selector? |
| SR7 | `vpu_isr_check_cmd()`, `vpu_cmd_done()`, `wait_command()` | none directly after submit, except command timing through payload shape | host completion is based on IRQ state and INFO00/INFO02 result storage | Are APUNN settings/output/descriptor writes single-burst or sequenced after host-visible state changes? |
| SR8 | APUSYS wrapper opcodes outside this kernel source | wrapper command buffer fields | not proven by kernel source alone | Which firmware parser path maps wrapper opcodes `10001..10009` to work functions? |

## Firmware Evidence Matrix

| ID | Firmware evidence | Status | Confidence | Next action |
|---|---|---|---|---|
| SR1 | `focused_loop_investigations.flix_assisted_INFO13_record_lead` states INFO12 count is closed at the ABI/kernel/provider boundary; handoff Q3 records the same closure | Accepted for current primitive model | High | Reopen only if source reveals another firmware count path |
| SR2 | `standard_field_access_clusters` includes 0x40-window owners `0x7003b468/a2`, `0x70039cfc/a2`, and `0x7003ce3c/a2`; `0x7003c102` FLIX-correct sweep and `0x7003ce3c` validator support record-shaped parsing | Accepted for descriptor layout | High | Add source field names to analyzer output only if needed for a later exploit argument |
| SR3 | `elf_verification_1234.EV2_DESCRIPTOR_VALIDATION_USE` ties the `0x7003b468` record walk, `0x7003c102` target, and `0x7003ce3c` high-field validator to the source 0x40 descriptor layout | Partially accepted | Medium | Treat INFO12/INFO13 and layout as closed; reopen validation/use pairing only for a specific plane-field exploit argument |
| SR4 | `elf_verification_1234.EV1_SETTINGS_OUTPUT_FILL` maps static output-shape validators at `0x700184b4`, `0x70015e98`, `0x70040430`, `0x700a7be0`, `0x700414d0`, and `0x700405bc`, while explicitly recording that the runtime fill loop is not identified in ELF-only refs | Not fully statically verified | Medium | Keep runtime settings/output probes as authority for `settings+0x08` bounds until the fill loop is traced |
| SR5 | key address `elf_entry_INFO16` is `0x70006794`; handoff records it as both INFO16 target and ELF entry; parser path begins at `0x700301d8 -> 0x70030a0c -> 0x700304f8` | Accepted for APUNN entry and first parser path | High | Keep command-selector binding separate from APUNN entry binding |
| SR6 | Source proves INFO01 `0x24`; current APUNN refs do not prove a direct APUNN-side read of INFO01. Handoff already treats INFO01 as kernel/resident-firmware selector unless a direct APUNN read is later found | Not statically verifiable in APUNN yet | Medium | Search for command-register reads only if a new resident/APUNN boundary lead appears |
| SR7 | `elf_verification_1234.EV3_IDMA_OWNER_SEQUENCING` closes `0x70044b74` as the top iDMA schedule/wait owner anchor; Q1 runtime completion-poll is negative for Java-visible pre-wait sequencing; firmware refs do not expose burst timing | Requires runtime or FLIX/TIE slot evidence | Medium | Use `0x70044b74` as instrumentation anchor if slow-opcode evidence appears |
| SR8 | `elf_verification_1234.EV4_OPCODE_PARSER_CORRELATION` maps `0x700301d8 -> 0x70030a0c -> 0x700304f8`; branch targets are `0x70020ba2`, `0x7003125a`, and `0x7003126a`; FLK/ANN pointer-run refs are FLIX-bundle-interior literal-slot signatures with no indexed evidence | Partially accepted parser path, rejected indexed mapping | High for parser path, low for opcode index mapping | Do not claim `10001..10009` mapping until table-base/index evidence or runtime correlation exists |

## Accepted Findings

1. The source confirms the already-used D2D_EXT register contract: INFO12 is
   `buffer_count`, INFO13 is the kernel-copied descriptor-array IOVA, INFO14 and
   INFO15 are the optional settings tuple, INFO16 is the preload entry, and
   INFO19 is IRAM MVA.
2. The source confirms the 0x40 descriptor record layout. This directly supports
   the current APUNN INFO12/INFO13 closure because the firmware refs contain
   0x40-record-shaped access clusters and validator islands.
3. The source confirms APUSYS execute forces the preload path, so treating the
   observed runtime path as D2D_EXT is source-consistent.
4. The source confirms host wait completion is IRQ/result-state based and does
   not, by itself, prove a raceable APUNN writeback gap. The existing runtime
   completion-poll result remains the stronger evidence for Q1 timing.
5. The current OTA ELF refs now materialize the active 1-4 verification result
   in `elf_verification_1234`, so handoff documents can cite one reproducible
   JSON/Markdown source for settings/output-fill, descriptor validation/use,
   iDMA owner sequencing, and opcode parser correlation.

## Rejected Or Not-Yet-Proven Hypotheses

| Hypothesis | Result | Reason |
|---|---|---|
| The 63-entry `.dram_op.data` ANN op-name table is the wrapper opcode dispatch table | Rejected for current static evidence | No raw-u32 or L32R code reference to `.dram_op.data`; APUNN refs classify it as vocabulary, not dispatch proof |
| APUNN firmware directly reads D2D_EXT command `0x24` from INFO01 | Not proven | Source proves the kernel/resident-firmware command selector, but current APUNN refs do not bind a direct APUNN-side INFO01 read |
| INFO14/INFO15 settings tuple is mandatory for normal wrapper completion | Rejected for current runtime shape | Existing handoff and settings ABI show the stable wrapper replay completes with the required APUNN command/settings buffer carried by descriptors, while INFO14/INFO15 can be clear |
| Static output validators identify the exact `settings+0x08` output-fill loop | Not proven | Validators are mapped, but the runtime fill loop remains separate |
| FLK/ANN pointer-run slot signatures prove wrapper opcode index mapping | Rejected | Current refs classify all five parser-owner slot refs as FLIX-bundle-interior signatures and `indexed_dispatch_evidence=not_found` |

## Analyzer And IDA Impact

This pass adds one analyzer/IDA summary layer: `elf_verification_1234`. It does
not replace the lower-level evidence; it packages the active 1-4 source-risk
verification results so the refs, Markdown, handoff, and IDB comments agree.
The rows with firmware-side evidence remain grounded in:

- `key_addresses`
- `standard_field_access_clusters`
- `focused_loop_investigations`
- `dispatcher_parser_investigation`
- `output_validation_investigations`
- `dma_owner_investigations`
- `elf_verification_1234`

Future analyzer updates should be limited to source-derived field labels for
0x40 descriptor offsets or exact fill-loop/runtime-owner attribution, and only
if that improves a concrete firmware owner or exploit argument.

## Runtime Follow-Up

No new runtime probe is required by this source pass alone. Runtime work should
resume only for rows that static firmware evidence cannot answer:

- Q1 DMA/iDMA write sequencing around owner `0x70044b74`
- Q4 exact output-fill owner for the `settings+0x08` bound
- Q2 wrapper opcode to parser/path correlation for `10001..10009`

## Completion Against The Plan

The first concrete pass in `MTK_SOURCE_FIRMWARE_COMPARISON_PLAN.md` is complete:

- the requested source ranges were read
- INFO12/INFO13, INFO14/INFO15, INFO16/INFO19, and completion/wait rows were
  extracted into a source-risk matrix
- each row has a firmware-evidence status
- analyzer/IDA changes are explicitly deferred because the current refs already
  carry the accepted firmware evidence
