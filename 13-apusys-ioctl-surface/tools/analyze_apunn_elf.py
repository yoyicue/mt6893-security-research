#!/usr/bin/env python3
"""Summarize the embedded apu_lib_apunn Xtensa ELF.

The V260523 APUNN preload PROG carve starts with a small wrapper and then an
embedded Xtensa ELF. Ghidra can import the ELF, but the Xtensa backend does not
fully understand this MediaTek VPU core's TIE/FLIX extensions. This helper keeps
the non-decompiler facts reproducible: ELF sections, Xtensa property ranges,
string tables, and pointer tables.
"""

from __future__ import annotations

import argparse
import json
import struct
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable


ELF_MAGIC = b"\x7fELF"

XT_PROP_LITERAL = 0x01
XT_PROP_INSN = 0x02
XT_PROP_DATA = 0x04
XT_PROP_UNREACHABLE = 0x08
XT_PROP_LOOP_TARGET = 0x10
XT_PROP_BRANCH_TARGET = 0x20
XT_PROP_NO_DENSITY = 0x40
XT_PROP_NO_REORDER = 0x80
XT_PROP_NO_TRANSFORM = 0x100
XT_PROP_ALIGN = 0x800
XT_PROP_ALIGN2 = 0x2000
XT_PROP_ABS_LITERAL = 0x20000

FLAG_NAMES = (
    (XT_PROP_LITERAL, "literal"),
    (XT_PROP_INSN, "insn"),
    (XT_PROP_DATA, "data"),
    (XT_PROP_UNREACHABLE, "unreachable"),
    (XT_PROP_LOOP_TARGET, "loop_target"),
    (XT_PROP_BRANCH_TARGET, "branch_target"),
    (XT_PROP_NO_DENSITY, "no_density"),
    (XT_PROP_NO_REORDER, "no_reorder"),
    (XT_PROP_NO_TRANSFORM, "no_transform"),
    (XT_PROP_ALIGN, "align"),
    (XT_PROP_ALIGN2, "align2"),
    (XT_PROP_ABS_LITERAL, "abs_literal"),
)


@dataclass
class ElfHeader:
    entry: int
    phoff: int
    phentsize: int
    phnum: int
    shoff: int
    shentsize: int
    shnum: int
    shstrndx: int
    flags: int
    machine: int


@dataclass
class Section:
    index: int
    name: str
    sh_type: int
    flags: int
    addr: int
    offset: int
    size: int
    link: int
    info: int
    align: int
    entsize: int


@dataclass
class XtProp:
    addr: int
    size: int
    flags: int
    flag_names: list[str]


@dataclass
class XtensaInfo:
    fields: dict[str, str]


@dataclass
class StringEntry:
    addr: int
    value: str


@dataclass
class PointerEntry:
    addr: int
    value: int
    target_section: str
    target_string: str | None
    prop_size: int | None
    prop_flags: str | None
    owner_entry: int | None
    owner_delta: int | None


@dataclass
class PointerRun:
    start: int
    end: int
    count: int
    entries: list[PointerEntry]


@dataclass
class PointerRunInvestigation:
    start: int
    end: int
    count: int
    target_owner_counts: list[dict[str, object]]
    raw_slot_ref_count: int
    raw_unaligned_ref_count: int
    l32r_slot_ref_count: int
    table_base_value_ref_count: int
    raw_ref_hits: list[dict[str, object]]
    l32r_ref_hits: list[dict[str, object]]
    assessment: str
    q2_status: str
    next_action: str


@dataclass
class FunctionCandidate:
    addr: int
    prop_size: int
    prop_flags: str
    entry_bytes: str
    next_entry_delta: int | None


@dataclass
class KeyAddress:
    label: str
    addr: int
    section: str | None
    owner_entry: int | None
    owner_delta: int | None
    prop_size: int | None
    prop_flags: str | None


@dataclass
class RodataReference:
    ref_addr: int
    value: int
    string_addr: int
    string_offset: int
    string_value: str
    owner_entry: int | None
    owner_delta: int | None


@dataclass
class CriticalStringHit:
    ref_addr: int
    value: int
    string_offset: int
    owner_entry: int | None
    owner_delta: int | None


@dataclass
class CriticalStringScan:
    pattern: str
    string_addr: int | None
    string_value: str | None
    aligned_hit_count: int
    byte_hit_count: int
    aligned_hits: list[CriticalStringHit]
    byte_hits: list[CriticalStringHit]


@dataclass
class StandardIslandInstruction:
    addr: int
    expected_bytes: str
    actual_bytes: str | None
    verified: bool
    mnemonic: str
    effect: str


@dataclass
class StandardIsland:
    label: str
    start: int
    end: int
    verified: bool
    note: str
    instructions: list[StandardIslandInstruction]


@dataclass
class StandardFieldAccess:
    addr: int
    op: str
    base_reg: int
    value_reg: int
    offset: int
    access_size: int
    is_store: bool
    owner_entry: int | None
    owner_delta: int | None


@dataclass
class FieldAccessCluster:
    owner_entry: int
    base_reg: int
    hit_count: int
    unique_offsets: list[int]
    vpu_buffer_offsets: list[int]
    sample_hits: list[StandardFieldAccess]


@dataclass
class L32RReference:
    addr: int
    bytes: str
    target_reg: int
    imm16: int
    literal_addr: int
    literal_section: str | None
    literal_value: int | None
    value_section: str | None
    literal_string_addr: int | None
    literal_string_offset: int | None
    literal_string_value: str | None
    value_string_addr: int | None
    value_string_offset: int | None
    value_string_value: str | None
    prop_addr: int | None
    prop_size: int | None
    prop_flags: str | None
    owner_entry: int | None
    owner_delta: int | None


@dataclass
class CriticalL32RScan:
    pattern: str
    string_addr: int | None
    string_value: str | None
    hit_count: int
    hits: list[L32RReference]


@dataclass
class LoopTargetCandidate:
    addr: int
    owner_entry: int | None
    owner_delta: int | None
    prop_size: int
    prop_flags: str
    matched_field_bases: list[int]


@dataclass
class FocusedLoopInvestigation:
    label: str
    owner_entry: int
    loop_target: int
    priority: str
    assessment: str
    target_prop_size: int | None
    target_prop_flags: str | None
    prop_runs: list[dict[str, object]]
    visible_field_accesses: list[StandardFieldAccess]
    flix_framing: str
    flix_framing_hits: list[int]
    flix_framing_deltas: list[int]
    standard_loop_opcode_hits: list[int]
    boundary_counts: dict[str, int]
    loop_body_start: int
    loop_body_end: int
    loop_body_boundary_counts: dict[str, int]
    loop_body_core_mem_accesses: list[StandardFieldAccess]
    count_status: str
    stride_0x40_status: str
    next_action: str


@dataclass
class CriticalOwnerCluster:
    owner_entry: int
    pattern_count: int
    hit_count: int
    patterns: list[str]
    sample_refs: list[dict[str, object]]
    assessment: str


@dataclass
class DmaOwnerInvestigation:
    label: str
    owner_entry: int
    analysis_start: int
    analysis_end: int
    cluster_rank: int
    cluster_pattern_count: int
    cluster_hit_count: int
    cluster_patterns: list[str]
    assessment: str
    q1_status: str
    evidence_refs: list[dict[str, object]]
    prop_runs: list[dict[str, object]]
    flix_framing: str
    flix_framing_hit_count: int
    flix_framing_hits: list[int]
    standard_a2_offsets: list[int]
    standard_a2_access_count: int
    next_action: str


@dataclass
class OutputValidationInvestigation:
    label: str
    owner_entry: int
    analysis_start: int
    analysis_end: int
    assessment: str
    q4_status: str
    referenced_patterns: list[str]
    evidence_refs: list[dict[str, object]]
    prop_runs: list[dict[str, object]]
    boundary_counts: dict[str, int]
    next_action: str


@dataclass
class AnnOpTableInvestigation:
    table_start: int
    table_end: int
    table_size: int
    nonzero_entry_count: int
    zero_tail_bytes: int
    all_entries_are_rodata_strings: bool
    raw_ref_hits: list[dict[str, object]]
    l32r_ref_hits: list[dict[str, object]]
    assessment: str
    q2_status: str
    next_action: str


@dataclass
class FlixSweepInstruction:
    addr: int
    length: int
    kind: str
    raw: str
    fmt: str | None
    framing_ok: bool | None
    framing_warn: list[str]
    core_mem_access: StandardFieldAccess | None


@dataclass
class FlixSweep:
    label: str
    start: int
    end: int
    description: str
    start_prop_addr: int | None
    start_prop_size: int | None
    start_prop_flags: str | None
    counts: dict[str, int]
    bad_framing_count: int
    instructions: list[FlixSweepInstruction]
    next_action: str


@dataclass
class FlixLengthRuleValidation:
    rule: str
    total_insn_runs: int
    matched_insn_runs: int
    match_percent: float
    first_nibble_counts: dict[str, int]
    min_e_run_size: int | None
    min_f_run_size: int | None
    candidate_results: list[dict[str, object]]
    assessment: str
    next_action: str


CRITICAL_STRING_PATTERNS = (
    "add idma request fail in %s",
    "ERROR CALLBACK: iDMA in Error",
    "INTERRUPT CALLBACK : processing iDMA interrupt",
    "iDMA error",
    "iDMA schedule error",
    "iDMA wait error",
    "../vp6-ann/libcommon/src/idma_mvpu6/dmaif.c",
    "sDesc > eDesc",
    "eDesc >= TM_DMA_DESC_IDX_MAX",
    "_DMA_STALL",
    "No error",
    "Data buffer does not start in DRAM",
    "Data buffer does not fit in DRAM",
)


KNOWN_STANDARD_ISLANDS = (
    {
        "label": "elf_entry_context_pack",
        "note": (
            "Stable standard Xtensa island inside the 0x70006794 INFO16 entry; "
            "copies preload/context fields into the a10 scratch/context object."
        ),
        "instructions": (
            (0x70006794, "36 41 00", "entry sp, 0x20", "open a 0x20-byte stack frame"),
            (0x700067B1, "98 0c", "l32i.n a9, a12, 0x00", "load dword from a12+0x00"),
            (0x700067B3, "99 1a", "s32i.n a9, a10, 0x04", "store a12+0x00 value to a10+0x04"),
            (0x700067B5, "88 1c", "l32i.n a8, a12, 0x04", "load dword from a12+0x04"),
            (0x700067B7, "89 2a", "s32i.n a8, a10, 0x08", "store a12+0x04 value to a10+0x08"),
            (0x700067B9, "f8 2c", "l32i.n a15, a12, 0x08", "load dword from a12+0x08"),
            (0x700067BB, "f9 3a", "s32i.n a15, a10, 0x0c", "store a12+0x08 value to a10+0x0c"),
            (0x700067BD, "e8 3c", "l32i.n a14, a12, 0x0c", "load dword from a12+0x0c"),
            (0x700067BF, "e9 4a", "s32i.n a14, a10, 0x10", "store a12+0x0c value to a10+0x10"),
            (0x700067C1, "d8 4c", "l32i.n a13, a12, 0x10", "load dword from a12+0x10"),
            (0x700067C3, "d9 5a", "s32i.n a13, a10, 0x14", "store a12+0x10 value to a10+0x14"),
            (0x700067C5, "c8 5c", "l32i.n a12, a12, 0x14", "load dword from a12+0x14"),
            (0x700067C7, "c9 6a", "s32i.n a12, a10, 0x18", "store a12+0x14 value to a10+0x18"),
            (0x700067C9, "d2 22 11", "l32i a13, a2, 0x44", "load dword from a2+0x44"),
            (0x700067CC, "d9 aa", "s32i.n a13, a10, 0x28", "store a2+0x44 value to a10+0x28"),
            (0x700067CE, "b2 22 13", "l32i a11, a2, 0x4c", "load dword from a2+0x4c"),
            (0x700067D1, "b9 7a", "s32i.n a11, a10, 0x1c", "store a2+0x4c value to a10+0x1c"),
            (0x700067D3, "92 22 14", "l32i a9, a2, 0x50", "load dword from a2+0x50"),
            (0x700067D6, "99 8a", "s32i.n a9, a10, 0x20", "store a2+0x50 value to a10+0x20"),
        ),
    },
    {
        "label": "early_helper_dispatch",
        "note": "Small helper that forwards a context-derived pointer to 0x70007440 and returns 0.",
        "instructions": (
            (0x70006590, "36 41 00", "entry sp, 0x20", "open a 0x20-byte stack frame"),
            (0x70006593, "a8 c2", "l32i.n a10, a2, 0x30", "load pointer from a2+0x30"),
            (0x70006595, "a8 6a", "l32i.n a10, a10, 0x18", "load pointer from previous+0x18"),
            (0x70006597, "a5 ea 00", "call8 0x70007440", "call early dynamic dispatch helper"),
            (0x7000659A, "0c 02", "movi.n a2, 0", "set return value to 0"),
            (0x7000659C, "1d f0", "retw.n", "return"),
        ),
    },
    {
        "label": "early_dynamic_dispatch_standard_island",
        "note": (
            "Standard instructions visible inside extension-heavy 0x70007440; "
            "shows ccount timing and repeated indirect calls through *(a12+0)."
        ),
        "instructions": (
            (0x70007440, "36 c1 00", "entry sp, 0x60", "open a 0x60-byte stack frame"),
            (0x7000744F, "90 ea 03", "rsr.ccount a9", "read cycle counter before dispatch"),
            (0x70007452, "82 2c 00", "l32i a8, a12, 0x00", "load callback/function pointer from a12+0x00"),
            (0x70007455, "bc 78", "beqz.n a8, 0x70007490", "skip indirect call if function pointer is null"),
            (0x7000746D, "e0 08 00", "callx8 a8", "call function pointer"),
            (0x700074B7, "e0 08 00", "callx8 a8", "call function pointer again in the timed dispatch path"),
            (0x700074D8, "65 3e ff", "call8 0x700068c0", "call local helper in the return path"),
            (0x700074E6, "a9 0b", "s32i.n a10, a11, 0x00", "store callback/helper result through a11"),
            (0x700074E8, "ad 02", "mov.n a10, a2", "move saved argument/result into a10 before callback"),
            (0x700074F0, "e0 08 00", "callx8 a8", "second function-pointer call site"),
            (0x700074F5, "c0 ea 03", "rsr.ccount a12", "read cycle counter after dispatch"),
            (0x700074F8, "28 51", "l32i.n a2, sp, 0x14", "load return value from stack"),
            (0x700074FA, "1d f0", "retw.n", "return"),
        ),
    },
    {
        "label": "early_dynamic_dispatch_callback_loop",
        "note": (
            "Second standard-instruction island inside the 0x70007440 owner. "
            "It confirms the early dynamic dispatcher has a repeated callback/"
            "polling loop after the first return-shaped island, not just one "
            "function-pointer call."
        ),
        "instructions": (
            (0x7000750C, "58 c8", "l32i.n a5, a8, 0x30", "load callback/state field from a8+0x30"),
            (0x7000750E, "59 00", "s32i.n a5, a0, 0x00", "store callback/state field through a0"),
            (0x70007516, "e0 08 00", "callx8 a8", "call function pointer"),
            (0x7000752F, "e0 08 00", "callx8 a8", "call function pointer"),
            (0x70007550, "e5 36 ff", "call8 0x700068c0", "call local wait/spin helper"),
            (0x7000755E, "38 32", "l32i.n a3, a2, 0x0c", "load field from returned object at a2+0x0c"),
            (0x70007560, "08 02", "l32i.n a0, a2, 0x00", "load field from returned object at a2+0x00"),
            (0x70007573, "b8 ff", "l32i.n a11, a15, 0x3c", "load field from a15+0x3c before callback"),
            (0x70007575, "e0 08 00", "callx8 a8", "call function pointer"),
            (0x7000757E, "a9 14", "s32i.n a10, a4, 0x04", "store callback result to a4+0x04"),
            (0x70007586, "e0 08 00", "callx8 a8", "call function pointer"),
            (0x70007589, "0c 2b", "movi.n a11, 2", "load constant 2"),
            (0x7000758B, "b0 aa 63", "minu a10, a10, a11", "clamp callback result to at most 2"),
            (0x7000758E, "a9 c1", "s32i.n a10, sp, 0x30", "save clamped callback result on stack"),
            (0x70007590, "a9 04", "s32i.n a10, a4, 0x00", "store clamped callback result to a4+0x00"),
            (0x700075A9, "dc 58", "bnez.n a8, 0x700075c2", "branch on callback/state pointer"),
            (0x700075C1, "e0 08 00", "callx8 a8", "call function pointer"),
        ),
    },
    {
        "label": "dispatcher_locateBuffer_trampoline",
        "note": (
            "Byte-verified standard island at the dispatcher-like locateBuffer "
            "candidate. The l32r at 0x700301e3 loads the rodata suffix "
            "`locateBuffer` from 0x70001884, then the island reaches a branch "
            "toward the 0x70030240/0x70030a0c owner and an indirect call."
        ),
        "instructions": (
            (0x700301D8, "36 61 00", "entry sp, 0x30", "open a 0x30-byte stack frame"),
            (0x700301E3, "31 a8 45", "l32r a3, 0x70001884", "load `locateBuffer` string suffix into a3"),
            (0x700301EF, "88 b8", "l32i.n a8, a8, 0x2c", "load dispatch table/context field from a8+0x2c"),
            (0x700301F6, "38 d8", "l32i.n a3, a8, 0x34", "load dispatch/context field from a8+0x34"),
            (0x700301F8, "06 04 02", "j 0x70030a0c", "jump into the larger 0x70030240 owner"),
            (0x70030201, "e0 08 00", "callx8 a8", "call function pointer"),
            (0x70030204, "2d 0a", "mov.n a2, a10", "move callback return value into a2"),
            (0x70030206, "1d f0", "retw.n", "return"),
        ),
    },
    {
        "label": "dispatcher_operand_record_field_decode",
        "note": (
            "Byte-verified standard instructions reached from the locateBuffer "
            "trampoline landing path. They read byte/halfword-shaped fields from "
            "the a2 record at offsets matching the DSP command operation/operand "
            "area, including +0x49/+0x4a near the first operand slot, then test "
            "the extracted 4-bit value against 1, 5, 6, and 9. This ties the "
            "0x700301d8 dispatcher path to command/operand parsing; it is not yet "
            "the native INFO13 vpu_buffer-array parser."
        ),
        "instructions": (
            (0x70030A0C, "66 12 00", "bnei a2, 1, 0x70030a10", "branch on a2 != 1 special case"),
            (0x70030A18, "cc 13", "bnez.n a3, 0x70030a1d", "skip first high-byte load when a3 is nonzero"),
            (0x70030A1A, "a2 02 4a", "l8ui a10, a2, 0x4a", "load byte field from a2+0x4a"),
            (0x70030A1D, "c2 02 49", "l8ui a12, a2, 0x49", "load byte field from a2+0x49"),
            (0x70030A20, "80 aa 11", "slli a10, a10, 8", "shift high byte into a 16-bit field"),
            (0x70030A23, "c0 aa 20", "or a10, a10, a12", "combine bytes from a2+0x49/+0x4a"),
            (0x70030A26, "a0 a2 34", "extui a10, a10, 2, 4", "extract bits 2..5 from combined 16-bit field"),
            (0x70030A3C, "86 58 c0", "j 0x70020ba2", "dispatch based on decoded operand field"),
            (0x70030A46, "06 04 02", "j 0x7003125a", "alternate jump into deeper operand parser"),
            (0x70030A54, "38 d8", "l32i.n a3, a8, 0x34", "load table/context field before alternate parser"),
            (0x70030B09, "b2 02 0f", "l8ui a11, a2, 0x0f", "load byte field from a2+0x0f"),
            (0x70030B0C, "c2 02 0e", "l8ui a12, a2, 0x0e", "load byte field from a2+0x0e"),
            (0x70030B17, "c0 bb 20", "or a11, a11, a12", "combine bytes from a2+0x0e/+0x0f"),
            (0x70030B48, "c2 02 13", "l8ui a12, a2, 0x13", "load byte field from a2+0x13"),
            (0x70030B4B, "d2 02 12", "l8ui a13, a2, 0x12", "load byte field from a2+0x12"),
            (0x70030B56, "d0 cc 20", "or a12, a12, a13", "combine bytes from a2+0x12/+0x13"),
            (0x70030BAB, "b9 0e", "s32i.n a11, a14, 0x00", "store decoded field to output/result slot +0x00"),
            (0x70030BAD, "b9 1e", "s32i.n a11, a14, 0x04", "store decoded field to output/result slot +0x04"),
            (0x70030BAF, "b9 2e", "s32i.n a11, a14, 0x08", "store decoded field to output/result slot +0x08"),
            (0x70030BB1, "b9 3e", "s32i.n a11, a14, 0x0c", "store decoded field to output/result slot +0x0c"),
            (0x70030BB3, "b9 4e", "s32i.n a11, a14, 0x10", "store decoded field to output/result slot +0x10"),
            (0x70030C13, "26 1a 32", "beqi a10, 1, 0x70030c49", "special-case decoded field value 1"),
            (0x70030C16, "26 5a 2f", "beqi a10, 5, 0x70030c49", "special-case decoded field value 5"),
            (0x70030C19, "26 6a 2c", "beqi a10, 6, 0x70030c49", "special-case decoded field value 6"),
            (0x70030C1C, "0c 9f", "movi.n a15, 9", "load special-case decoded field value 9"),
            (0x70030C1E, "f7 1a 27", "beq a10, a15, 0x70030c49", "special-case decoded field value 9"),
            (0x70030CA0, "62 12 00", "l16ui a6, a2, 0x00", "load 16-bit field from a2+0x00"),
            (0x70030CD2, "82 02 2f", "l8ui a8, a2, 0x2f", "load byte field from a2+0x2f"),
            (0x70030CD5, "92 02 2e", "l8ui a9, a2, 0x2e", "load byte field from a2+0x2e"),
            (0x70030CE0, "90 88 20", "or a8, a8, a9", "combine bytes from a2+0x2e/+0x2f"),
            (0x70030CEE, "80 88 11", "slli a8, a8, 8", "shift combined field for later use"),
            (0x70030D71, "92 02 07", "l8ui a9, a2, 0x07", "load byte field from a2+0x07"),
            (0x70030D74, "a2 02 06", "l8ui a10, a2, 0x06", "load byte field from a2+0x06"),
            (0x70030D7F, "a0 99 20", "or a9, a9, a10", "combine bytes from a2+0x06/+0x07"),
            (0x70030D8D, "80 99 11", "slli a9, a9, 8", "shift combined field for later use"),
        ),
    },
    {
        "label": "buffer_record_high_field_validator_candidate",
        "note": (
            "Byte-verified standard-instruction island in the 0x7003ce3c owner. "
            "It performs null/zero checks on an a2-based record using offsets "
            "+0x08, +0x0c, +0x10, +0x1c, +0x20, +0x24, +0x28, +0x34, and +0x38, "
            "plus byte fields at +0x39/+0x3a/+0x3b. These offsets overlap the high "
            "half of a 0x40-byte VPU buffer-shaped record, including plane "
            "length/pointer fields, but this island by itself does not prove "
            "the INFO12/INFO13 array loop or its iteration bound."
        ),
        "instructions": (
            (0x7003CE3C, "36 c1 03", "entry sp, 0x1e0", "open a 0x1e0-byte stack frame"),
            (0x7003CE3F, "82 af c0", "movi a8, -0x40", "prepare 64-byte stack alignment mask"),
            (0x7003CE42, "80 81 10", "and a8, sp, a8", "align stack pointer down to 64 bytes"),
            (0x7003CE45, "10 18 00", "movsp sp, a8", "install aligned stack pointer"),
            (0x7003CE95, "16 e3 5f", "beqz a3, 0x7003d497", "reject null/zero context field"),
            (0x7003CE98, "62 2a 21", "l32i a6, a10, 0x84", "load context field from a10+0x84"),
            (0x7003CE9B, "16 86 5f", "beqz a6, 0x7003d497", "reject null context field"),
            (0x7003CE9E, "16 04 62", "beqz a4, 0x7003d4c2", "reject null record-related pointer"),
            (0x7003CED8, "d8 22", "l32i.n a13, a2, 0x08", "load a2 record field +0x08"),
            (0x7003CEDA, "16 ed 58", "beqz a13, 0x7003d46c", "reject zero field +0x08"),
            (0x7003CEED, "f8 32", "l32i.n a15, a2, 0x0c", "load a2 record field +0x0c"),
            (0x7003CEEF, "16 9f 57", "beqz a15, 0x7003d46c", "reject zero field +0x0c"),
            (0x7003CF02, "48 42", "l32i.n a4, a2, 0x10", "load a2 record field +0x10"),
            (0x7003CF04, "16 44 56", "beqz a4, 0x7003d46c", "reject zero field +0x10"),
            (0x7003CF1D, "d2 02 3a", "l8ui a13, a2, 0x3a", "load high byte field from a2+0x3a"),
            (0x7003CF36, "f2 02 3b", "l8ui a15, a2, 0x3b", "load high byte field from a2+0x3b"),
            (0x7003CF39, "c8 72", "l32i.n a12, a2, 0x1c", "load a2 record field +0x1c"),
            (0x7003CF5E, "c2 22 08", "l32i a12, a2, 0x20", "load a2 record field +0x20"),
            (0x7003CF61, "16 bc 60", "beqz a12, 0x7003d570", "reject zero field +0x20"),
            (0x7003CF64, "a2 22 09", "l32i a10, a2, 0x24", "load a2 record field +0x24"),
            (0x7003CF67, "16 0a 63", "beqz a10, 0x7003d59b", "reject zero field +0x24"),
            (0x7003CF6A, "82 22 0a", "l32i a8, a2, 0x28", "load a2 record field +0x28"),
            (0x7003CF6D, "16 58 65", "beqz a8, 0x7003d5c6", "reject zero field +0x28"),
            (0x7003CF70, "a2 22 0d", "l32i a10, a2, 0x34", "load a2 record field +0x34"),
            (0x7003CF73, "16 aa 67", "beqz a10, 0x7003d5f1", "reject zero field +0x34"),
            (0x7003CF76, "92 02 38", "l8ui a9, a2, 0x38", "load byte field from a2+0x38"),
            (0x7003CF79, "8c e9", "beqz.n a9, 0x7003cf8b", "branch on zero byte field +0x38"),
            (0x7003CF8B, "42 02 39", "l8ui a4, a2, 0x39", "load byte field from a2+0x39"),
            (0x7003CF8E, "8c e4", "beqz.n a4, 0x7003cfa0", "branch on zero byte field +0x39"),
            (0x7003CFB5, "62 12 00", "l16ui a6, a2, 0x00", "reload low 16-bit field from a2+0x00"),
            (0x7003CFEC, "b2 02 38", "l8ui a11, a2, 0x38", "reload byte field from a2+0x38"),
        ),
    },
)


def parse_int(text: str) -> int:
    return int(text, 0)


def hx(value: int | None) -> str | None:
    return None if value is None else f"0x{value:x}"


def flag_names(flags: int) -> list[str]:
    return [name for bit, name in FLAG_NAMES if flags & bit]


def flags_text(flags: int) -> str:
    names = flag_names(flags)
    return "|".join(names) if names else "0"


def read_cstr(blob: bytes, offset: int) -> str:
    end = blob.find(b"\x00", offset)
    if end < 0:
        end = len(blob)
    return blob[offset:end].decode("ascii", "replace")


def is_string_byte(byte: int) -> bool:
    return byte in (0x09, 0x0A, 0x0D) or 0x20 <= byte < 0x7F


def display_text(value: str) -> str:
    return (
        value.replace("\\", "\\\\")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
        .replace("|", "\\|")
    )


def parse_elf_header(data: bytes) -> ElfHeader:
    if data[:4] != ELF_MAGIC:
        raise ValueError("input is not an ELF file")
    if data[4] != 1 or data[5] != 1:
        raise ValueError("only 32-bit little-endian ELF is supported")
    fields = struct.unpack_from("<16sHHIIIIIHHHHHH", data, 0)
    return ElfHeader(
        entry=fields[4],
        phoff=fields[5],
        shoff=fields[6],
        flags=fields[7],
        phentsize=fields[9],
        phnum=fields[10],
        shentsize=fields[11],
        shnum=fields[12],
        shstrndx=fields[13],
        machine=fields[2],
    )


def parse_sections(data: bytes, eh: ElfHeader) -> list[Section]:
    raw_sections = []
    for index in range(eh.shnum):
        off = eh.shoff + index * eh.shentsize
        raw_sections.append(struct.unpack_from("<IIIIIIIIII", data, off))

    shstr = raw_sections[eh.shstrndx]
    names = data[shstr[4] : shstr[4] + shstr[5]]

    sections: list[Section] = []
    for index, fields in enumerate(raw_sections):
        name = read_cstr(names, fields[0])
        sections.append(
            Section(
                index=index,
                name=name,
                sh_type=fields[1],
                flags=fields[2],
                addr=fields[3],
                offset=fields[4],
                size=fields[5],
                link=fields[6],
                info=fields[7],
                align=fields[8],
                entsize=fields[9],
            )
        )
    return sections


def section_by_name(sections: Iterable[Section], name: str) -> Section | None:
    for section in sections:
        if section.name == name:
            return section
    return None


def section_for_va(sections: Iterable[Section], addr: int) -> Section | None:
    for section in sections:
        if section.size and section.addr <= addr < section.addr + section.size:
            return section
    return None


def section_bytes(data: bytes, section: Section) -> bytes:
    return data[section.offset : section.offset + section.size]


def va_bytes(data: bytes, sections: Iterable[Section], addr: int, size: int) -> bytes | None:
    section = section_for_va(sections, addr)
    if section is None or addr + size > section.addr + section.size:
        return None
    offset = section.offset + (addr - section.addr)
    return data[offset : offset + size]


def flix_insn_len(first_byte: int) -> int:
    op0 = first_byte & 0x0F
    if op0 <= 0x07:
        return 3
    if op0 <= 0x0D:
        return 2
    if op0 == 0x0F:
        return 8
    return 16


def flix_kind(first_byte: int) -> str:
    op0 = first_byte & 0x0F
    if op0 <= 0x07:
        return "core24"
    if op0 <= 0x0D:
        return "dens16"
    if op0 == 0x0F:
        return "flix64"
    return "flix128"


def candidate_flix_insn_len(first_byte: int, e_len: int, f_len: int) -> int:
    op0 = first_byte & 0x0F
    if op0 <= 0x07:
        return 3
    if op0 <= 0x0D:
        return 2
    if op0 == 0x0F:
        return f_len
    return e_len


def tiles_prop_run(
    data: bytes,
    sections: list[Section],
    prop: XtProp,
    e_len: int,
    f_len: int,
) -> tuple[bool, int | None]:
    addr = prop.addr
    end = prop.addr + prop.size
    while addr < end:
        head = va_bytes(data, sections, addr, 1)
        if not head:
            return False, addr
        length = candidate_flix_insn_len(head[0], e_len, f_len)
        if addr + length > end:
            return False, addr
        addr += length
    return addr == end, None if addr == end else addr


def build_flix_length_rule_validation(
    data: bytes,
    sections: list[Section],
    props: list[XtProp],
) -> FlixLengthRuleValidation:
    insn_props = [
        prop
        for prop in props
        if prop.flags & XT_PROP_INSN and section_for_va(sections, prop.addr) is not None
    ]
    candidates = [
        (16, 8, "adopted_e16_f8"),
        (16, 16, "e16_f16"),
        (8, 8, "e8_f8"),
        (8, 16, "e8_f16"),
    ]
    candidate_results: list[dict[str, object]] = []
    for e_len, f_len, name in candidates:
        matched = 0
        failed_samples: list[dict[str, object]] = []
        for prop in insn_props:
            ok, fail_addr = tiles_prop_run(data, sections, prop, e_len, f_len)
            if ok:
                matched += 1
            elif len(failed_samples) < 8:
                failed_samples.append(
                    {
                        "prop_addr": prop.addr,
                        "prop_size": prop.size,
                        "fail_addr": fail_addr,
                        "first_byte": (
                            None
                            if fail_addr is None
                            else (va_bytes(data, sections, fail_addr, 1) or b"\x00")[0]
                        ),
                    }
                )
        candidate_results.append(
            {
                "name": name,
                "e_len": e_len,
                "f_len": f_len,
                "matched_runs": matched,
                "total_runs": len(insn_props),
                "match_percent": (matched * 100.0 / len(insn_props)) if insn_props else 0.0,
                "failed_samples": failed_samples,
            }
        )
    first_nibble_counts = {f"0x{value:x}": 0 for value in range(16)}
    min_e_run_size: int | None = None
    min_f_run_size: int | None = None
    for prop in insn_props:
        head = va_bytes(data, sections, prop.addr, 1)
        if not head:
            continue
        nibble = head[0] & 0x0F
        if nibble == 0x0E:
            min_e_run_size = prop.size if min_e_run_size is None else min(min_e_run_size, prop.size)
        elif nibble == 0x0F:
            min_f_run_size = prop.size if min_f_run_size is None else min(min_f_run_size, prop.size)
        addr = prop.addr
        end = prop.addr + prop.size
        while addr < end:
            byte = va_bytes(data, sections, addr, 1)
            if not byte:
                break
            first_nibble_counts[f"0x{byte[0] & 0x0F:x}"] += 1
            addr += flix_insn_len(byte[0])
    adopted = next(item for item in candidate_results if item["name"] == "adopted_e16_f8")
    total_runs = int(adopted["total_runs"])
    matched_runs = int(adopted["matched_runs"])
    return FlixLengthRuleValidation(
        rule="op0<=0x7:3, op0<=0xd:2, op0==0xf:8, op0==0xe:16",
        total_insn_runs=total_runs,
        matched_insn_runs=matched_runs,
        match_percent=(matched_runs * 100.0 / total_runs) if total_runs else 0.0,
        first_nibble_counts=first_nibble_counts,
        min_e_run_size=min_e_run_size,
        min_f_run_size=min_f_run_size,
        candidate_results=candidate_results,
        assessment=(
            "The adopted FLIX length rule tiles every .xt.prop INSN run exactly. "
            "This turns the 0xe and 0xf op0 nibbles into 128-bit and 64-bit "
            "bundle widths instead of stock Xtensa 2-byte density items."
        ),
        next_action=(
            "Use this rule as the authoritative boundary layer for FLIX-heavy "
            "owners; slot mnemonics still require MVPU6F TIE/FLIX configuration "
            "or dynamic correlation."
        ),
    )


def flix64_framing_warnings(value: int) -> list[str]:
    warnings: list[str] = []
    if value & 0x1F != 0x0F:
        warnings.append("fmt!=0x0f")
    if (value >> 56) & 0xFF != 0:
        warnings.append("topbyte!=0")
    if (value >> 50) & 1:
        warnings.append("bit50!=0")
    return warnings


def flix128_framing_warnings(value: int) -> list[str]:
    warnings: list[str] = []
    if value & 0x0F != 0x0E:
        warnings.append("fmt!=0xe")
    if (value >> 122) & 0x3F:
        warnings.append("bits122-127!=0")
    return warnings


def flix_sweep_range(
    data: bytes,
    sections: list[Section],
    props: list[XtProp],
    function_candidates: list[FunctionCandidate],
    label: str,
    start: int,
    end: int,
    description: str,
    next_action: str,
) -> FlixSweep:
    addr = start
    instructions: list[FlixSweepInstruction] = []
    counts = {"core24": 0, "dens16": 0, "flix64": 0, "flix128": 0, "truncated": 0}
    bad_framing_count = 0
    while addr < end:
        head = va_bytes(data, sections, addr, 1)
        if not head:
            break
        length = flix_insn_len(head[0])
        raw = va_bytes(data, sections, addr, length)
        if raw is None or len(raw) != length:
            partial = va_bytes(data, sections, addr, max(1, end - addr)) or b""
            counts["truncated"] += 1
            instructions.append(
                FlixSweepInstruction(
                    addr=addr,
                    length=length,
                    kind="truncated",
                    raw=partial.hex(),
                    fmt=None,
                    framing_ok=None,
                    framing_warn=["range_end"],
                    core_mem_access=None,
                )
            )
            break
        kind = flix_kind(head[0])
        counts[kind] = counts.get(kind, 0) + 1
        fmt: str | None = None
        framing_ok: bool | None = None
        framing_warn: list[str] = []
        if length == 8:
            value = int.from_bytes(raw, "little")
            fmt = "0x%02x" % (value & 0x1F)
            framing_warn = flix64_framing_warnings(value)
            framing_ok = not framing_warn
        elif length == 16:
            value = int.from_bytes(raw, "little")
            fmt = "0x%x" % (value & 0x0F)
            framing_warn = flix128_framing_warnings(value)
            framing_ok = not framing_warn
        if framing_warn:
            bad_framing_count += 1
        core_mem_access = None
        if kind == "core24":
            decoded = decode_standard_mem_access(raw, 0)
            if decoded is not None:
                mnemonic, base_reg, value_reg, mem_offset, access_size, is_store = decoded
                owner_entry, owner_delta = owner_for_addr(function_candidates, addr)
                core_mem_access = StandardFieldAccess(
                    addr=addr,
                    op=mnemonic,
                    base_reg=base_reg,
                    value_reg=value_reg,
                    offset=mem_offset,
                    access_size=access_size,
                    is_store=is_store,
                    owner_entry=owner_entry,
                    owner_delta=owner_delta,
                )
        instructions.append(
            FlixSweepInstruction(
                addr=addr,
                length=length,
                kind=kind,
                raw=raw.hex(),
                fmt=fmt,
                framing_ok=framing_ok,
                framing_warn=framing_warn,
                core_mem_access=core_mem_access,
            )
        )
        addr += length

    start_prop = best_prop_for_addr(props, start)
    return FlixSweep(
        label=label,
        start=start,
        end=end,
        description=description,
        start_prop_addr=None if start_prop is None else start_prop.addr,
        start_prop_size=None if start_prop is None else start_prop.size,
        start_prop_flags=None if start_prop is None else flags_text(start_prop.flags),
        counts=counts,
        bad_framing_count=bad_framing_count,
        instructions=instructions,
        next_action=next_action,
    )


def build_flix_sweeps(
    data: bytes,
    sections: list[Section],
    props: list[XtProp],
    function_candidates: list[FunctionCandidate],
) -> list[FlixSweep]:
    specs = (
        (
            "info13_record_lead_corrected_boundaries",
            0x7003C0EE,
            0x7003C14C,
            (
                "Length-correct sweep across the 0x7003c102 loop-target "
                "neighborhood. Core LSAI-shaped accesses are interleaved with "
                "FLIX128/64 bundles instead of being swallowed by 2-byte "
                "base-Xtensa sizing."
            ),
            (
                "Use these boundaries before inspecting descriptor-field core "
                "ops. The 06 04 02 bytes are FLIX128 framing tails, not an "
                "independent selector."
            ),
        ),
        (
            "downgraded_error_tail_corrected_boundaries",
            0x7003D3E2,
            0x7003D460,
            (
                "Length-correct sweep around the downgraded 0x7003d423 "
                "loop-target property. The target itself is a core24 item, "
                "but the surrounding block remains switch/error-tail shaped."
            ),
            (
                "Keep as secondary local-control-flow evidence; corrected "
                "boundaries do not promote it back into the INFO13 mainline."
            ),
        ),
        (
            "dmaif_owner_entry_prefix_corrected_boundaries",
            0x70044B74,
            0x70044C50,
            (
                "Length-correct sweep at the top iDMA schedule/wait owner. "
                "The owner starts with a standard entry core op followed by "
                "dense FLIX128/64 bundles and sparse core/density items."
            ),
            (
                "Use this as the Q1 owner boundary map before any FLIX/iDMA "
                "instrumentation; string ownership alone still does not prove "
                "completion-store timing."
            ),
        ),
        (
            "dmaif_dram_validation_tail_corrected_boundaries",
            0x700452C4,
            0x70045330,
            (
                "Length-correct sweep around the DRAM data-buffer validation "
                "string owner tail inside the same iDMA cluster."
            ),
            (
                "Correlate these core24 checks with the DRAM validation "
                "strings before treating the FLIX bundles as DMA movement."
            ),
        ),
    )
    return [
        flix_sweep_range(
            data=data,
            sections=sections,
            props=props,
            function_candidates=function_candidates,
            label=label,
            start=start,
            end=end,
            description=description,
            next_action=next_action,
        )
        for label, start, end, description, next_action in specs
    ]


def find_strings(data: bytes, section: Section, min_len: int) -> list[StringEntry]:
    blob = section_bytes(data, section)
    out: list[StringEntry] = []
    pos = 0
    while pos < len(blob):
        if is_string_byte(blob[pos]):
            end = pos
            while end < len(blob) and is_string_byte(blob[end]):
                end += 1
            if end - pos >= min_len and end < len(blob) and blob[end] == 0:
                out.append(
                    StringEntry(
                        addr=section.addr + pos,
                        value=blob[pos:end].decode("ascii", "replace"),
                    )
                )
            pos = max(pos + 1, end + 1)
        else:
            pos += 1
    return out


def parse_xt_props(data: bytes, section: Section | None) -> list[XtProp]:
    if section is None:
        return []
    blob = section_bytes(data, section)
    out: list[XtProp] = []
    for off in range(0, len(blob) - 11, 12):
        addr, size, flags = struct.unpack_from("<III", blob, off)
        if addr == 0 and size == 0 and flags == 0:
            continue
        out.append(XtProp(addr=addr, size=size, flags=flags, flag_names=flag_names(flags)))
    return out


def parse_xtensa_info(data: bytes, section: Section | None) -> XtensaInfo | None:
    if section is None:
        return None
    blob = section_bytes(data, section)
    text = blob.decode("ascii", "ignore")
    start = text.find("HW_CONFIGID0=")
    if start < 0:
        start = text.find("Xtensa_Info")
    if start < 0:
        return XtensaInfo(fields={})
    fields: dict[str, str] = {}
    for line in text[start:].replace("\x00", "\n").splitlines():
        line = line.strip()
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"')
        if key and all(ch.isalnum() or ch == "_" for ch in key):
            fields[key] = value
    return XtensaInfo(fields=fields)


def best_prop_for_addr(props: list[XtProp], addr: int) -> XtProp | None:
    exact = [prop for prop in props if prop.addr == addr and prop.flags & XT_PROP_INSN]
    if exact:
        return max(exact, key=lambda prop: prop.size)
    containing = [
        prop
        for prop in props
        if prop.flags & XT_PROP_INSN and prop.addr <= addr < prop.addr + max(prop.size, 1)
    ]
    if containing:
        return min(containing, key=lambda prop: (addr - prop.addr, -prop.size))
    return None


def find_function_candidates(
    data: bytes, sections: list[Section], props: list[XtProp]
) -> list[FunctionCandidate]:
    text = section_by_name(sections, ".text")
    if text is None:
        return []
    blob = section_bytes(data, text)
    starts: list[tuple[int, XtProp, bytes]] = []
    seen: set[int] = set()
    for prop in props:
        if not prop.flags & XT_PROP_INSN:
            continue
        if prop.addr in seen or not (text.addr <= prop.addr < text.addr + text.size):
            continue
        off = prop.addr - text.addr
        if off + 3 > len(blob):
            continue
        entry = blob[off : off + 3]
        if entry[0] != 0x36:
            continue
        starts.append((prop.addr, prop, entry))
        seen.add(prop.addr)

    starts.sort(key=lambda item: item[0])
    out: list[FunctionCandidate] = []
    for index, (addr, prop, entry) in enumerate(starts):
        next_delta = None
        if index + 1 < len(starts):
            next_delta = starts[index + 1][0] - addr
        out.append(
            FunctionCandidate(
                addr=addr,
                prop_size=prop.size,
                prop_flags=flags_text(prop.flags),
                entry_bytes=entry.hex(),
                next_entry_delta=next_delta,
            )
        )
    return out


def owner_for_addr(
    function_candidates: list[FunctionCandidate], addr: int
) -> tuple[int | None, int | None]:
    target = addr & ~1
    lo = 0
    hi = len(function_candidates)
    while lo < hi:
        mid = (lo + hi) // 2
        if function_candidates[mid].addr <= target:
            lo = mid + 1
        else:
            hi = mid
    index = lo - 1
    if index < 0:
        return None, None
    owner = function_candidates[index].addr
    return owner, target - owner


def read_u32_va(data: bytes, sections: Iterable[Section], addr: int) -> int | None:
    raw = va_bytes(data, sections, addr, 4)
    if raw is None:
        return None
    return struct.unpack_from("<I", raw, 0)[0]


def ro_string_at(strings_by_addr: dict[int, str], addr: int) -> str | None:
    return strings_by_addr.get(addr)


def cstring_at_va(
    data: bytes,
    sections: Iterable[Section],
    addr: int,
    allowed_sections: set[str] | None = None,
) -> str | None:
    section = section_for_va(sections, addr)
    if section is None:
        return None
    if allowed_sections is not None and section.name not in allowed_sections:
        return None
    offset = section.offset + (addr - section.addr)
    if offset < 0 or offset >= len(data):
        return None
    end = data.find(b"\x00", offset)
    if end < 0:
        return None
    raw = data[offset:end]
    if not raw or any(not is_string_byte(byte) for byte in raw):
        return None
    return raw.decode("ascii", "replace")


def find_pointer_runs(
    data: bytes,
    sections: list[Section],
    source: Section,
    strings_by_addr: dict[int, str],
    props: list[XtProp],
    function_candidates: list[FunctionCandidate],
    min_run: int,
) -> list[PointerRun]:
    blob = section_bytes(data, source)
    runs: list[PointerRun] = []
    cur: list[PointerEntry] = []

    def finish() -> None:
        nonlocal cur
        if len(cur) >= min_run:
            runs.append(
                PointerRun(
                    start=cur[0].addr,
                    end=cur[-1].addr,
                    count=len(cur),
                    entries=cur,
                )
            )
        cur = []

    for off in range(0, len(blob) - 3, 4):
        value = struct.unpack_from("<I", blob, off)[0]
        target = value & ~1
        target_section = section_for_va(sections, target)
        if target_section and target_section.name in {
            ".text",
            ".rodata",
            ".data",
            ".dram0.data",
            ".dram_op.data",
        }:
            prop = best_prop_for_addr(props, value)
            owner_entry, owner_delta = owner_for_addr(function_candidates, value)
            cur.append(
                PointerEntry(
                    addr=source.addr + off,
                    value=value,
                    target_section=target_section.name,
                    target_string=ro_string_at(strings_by_addr, value)
                    or cstring_at_va(data, sections, value, {".rodata"}),
                    prop_size=None if prop is None else prop.size,
                    prop_flags=None if prop is None else flags_text(prop.flags),
                    owner_entry=owner_entry,
                    owner_delta=owner_delta,
                )
            )
        else:
            finish()
    finish()
    return runs


def op_name_table(
    data: bytes,
    sections: list[Section],
    section: Section | None,
    strings_by_addr: dict[int, str],
) -> list[dict[str, object]]:
    if section is None:
        return []
    blob = section_bytes(data, section)
    out: list[dict[str, object]] = []
    for off in range(0, len(blob) - 3, 4):
        value = struct.unpack_from("<I", blob, off)[0]
        name = strings_by_addr.get(value) or cstring_at_va(data, sections, value, {".rodata"})
        if name is None:
            continue
        out.append(
            {
                "index": off // 4,
                "entry_addr": section.addr + off,
                "string_addr": value,
                "name": name,
            }
        )
    return out


def interesting_strings(strings: list[StringEntry]) -> list[StringEntry]:
    tokens = (
        "execute_op",
        "process_command",
        "xrp",
        "XRP",
        "apunn",
        "d2d",
        "kernelProcess",
        "dma",
        "idma",
        "DMA",
        "Desc",
        "buffer",
        "VPU",
        "version",
    )
    return [entry for entry in strings if any(token in entry.value for token in tokens)]


KEY_ADDRESS_LABELS = (
    ("elf_entry_INFO16", 0x70006794),
    ("early_helper", 0x70006590),
    ("early_dynamic_dispatch", 0x70007440),
    ("flk_pointer_target_cluster", 0x70017D40),
    ("flk_pointer_table_owner", 0x70015E98),
    ("dispatcher_like_locateBuffer", 0x700301D8),
    ("flix_assisted_INFO13_record_lead", 0x7003B468),
    ("flix_assisted_INFO13_record_loop_target", 0x7003C102),
    ("buffer_record_high_field_validator_candidate", 0x7003CE3C),
    ("large_auto_function", 0x7003B424),
    ("top_dmaif_l32r_owner_cluster", 0x70044B74),
    ("ann_pointer_table_owner", 0x70081D50),
    ("ann_pointer_target_cluster", 0x70081EE7),
    ("ann_type_helper", 0x70083068),
)


def build_key_addresses(
    sections: list[Section],
    props: list[XtProp],
    function_candidates: list[FunctionCandidate],
) -> list[KeyAddress]:
    out: list[KeyAddress] = []
    for label, addr in KEY_ADDRESS_LABELS:
        target = addr & ~1
        section = section_for_va(sections, target)
        prop = best_prop_for_addr(props, target)
        owner_entry, owner_delta = owner_for_addr(function_candidates, target)
        out.append(
            KeyAddress(
                label=label,
                addr=addr,
                section=None if section is None else section.name,
                owner_entry=owner_entry,
                owner_delta=owner_delta,
                prop_size=None if prop is None else prop.size,
                prop_flags=None if prop is None else flags_text(prop.flags),
            )
        )
    return out


def string_containing_addr(strings: list[StringEntry], addr: int) -> StringEntry | None:
    for entry in strings:
        if entry.addr <= addr < entry.addr + len(entry.value):
            return entry
    return None


def xt_prop_insn_coverage(text: Section, props: list[XtProp]) -> bytearray:
    coverage = bytearray(text.size)
    for prop in props:
        if not prop.flags & XT_PROP_INSN:
            continue
        start = max(prop.addr, text.addr) - text.addr
        end = min(prop.addr + max(prop.size, 1), text.addr + text.size) - text.addr
        if 0 <= start < end <= len(coverage):
            coverage[start:end] = b"\x01" * (end - start)
    return coverage


def decode_l32r(
    blob: bytes,
    offset: int,
    addr: int,
    use_absolute_literals: bool,
) -> tuple[int, int, int] | None:
    if offset + 3 > len(blob):
        return None
    b0, b1, b2 = blob[offset], blob[offset + 1], blob[offset + 2]
    if b0 & 0x0F != 0x01:
        return None
    target_reg = b0 >> 4
    imm16 = b1 | (b2 << 8)
    if use_absolute_literals:
        literal_addr = imm16 << 2
    else:
        literal_addr = ((addr + 3) & ~3) + (imm16 << 2) - 0x40000
    return target_reg, imm16, literal_addr


def l32r_reference_from_addr(
    data: bytes,
    sections: list[Section],
    strings: list[StringEntry],
    function_candidates: list[FunctionCandidate],
    text: Section,
    text_blob: bytes,
    text_offset: int,
    prop: XtProp | None,
    use_absolute_literals: bool,
) -> L32RReference | None:
    addr = text.addr + text_offset
    decoded = decode_l32r(text_blob, text_offset, addr, use_absolute_literals)
    if decoded is None:
        return None
    target_reg, imm16, literal_addr = decoded
    literal_section = section_for_va(sections, literal_addr)
    if literal_section is None:
        return None
    literal_value = read_u32_va(data, sections, literal_addr)
    value_section = section_for_va(sections, literal_value & ~1) if literal_value is not None else None
    literal_string = string_containing_addr(strings, literal_addr)
    value_string = (
        string_containing_addr(strings, literal_value)
        if literal_value is not None
        else None
    )
    owner_entry, owner_delta = owner_for_addr(function_candidates, addr)
    return L32RReference(
        addr=addr,
        bytes=text_blob[text_offset : text_offset + 3].hex(" "),
        target_reg=target_reg,
        imm16=imm16,
        literal_addr=literal_addr,
        literal_section=literal_section.name,
        literal_value=literal_value,
        value_section=None if value_section is None else value_section.name,
        literal_string_addr=None if literal_string is None else literal_string.addr,
        literal_string_offset=None if literal_string is None else literal_addr - literal_string.addr,
        literal_string_value=None if literal_string is None else literal_string.value,
        value_string_addr=None if value_string is None else value_string.addr,
        value_string_offset=None
        if value_string is None or literal_value is None
        else literal_value - value_string.addr,
        value_string_value=None if value_string is None else value_string.value,
        prop_addr=None if prop is None else prop.addr,
        prop_size=None if prop is None else prop.size,
        prop_flags=None if prop is None else flags_text(prop.flags),
        owner_entry=owner_entry,
        owner_delta=owner_delta,
    )


def is_interesting_l32r_ref(ref: L32RReference) -> bool:
    if ref.literal_string_value or ref.value_string_value:
        return True
    if ref.literal_section in {".dram_op.data", ".rodata"}:
        return True
    if ref.value_section in {".dram_op.data", ".rodata", ".text"}:
        return True
    return False


def l32r_ref_text(ref: L32RReference) -> str:
    return " ".join(
        value
        for value in (ref.literal_string_value, ref.value_string_value)
        if value
    )


def l32r_interest_priority(ref: L32RReference) -> tuple[int, int]:
    text = l32r_ref_text(ref)
    if "locateBuffer" in text:
        return (0, ref.addr)
    if any(pattern in text for pattern in CRITICAL_STRING_PATTERNS):
        return (1, ref.addr)
    if any(token in text for token in ("iDMA", "idma", "dmaif", "DMA", "Data buffer")):
        return (2, ref.addr)
    if ref.literal_section == ".dram_op.data" or ref.value_section == ".dram_op.data":
        return (3, ref.addr)
    if text:
        return (4, ref.addr)
    if ref.literal_section == ".rodata" or ref.value_section == ".rodata":
        return (5, ref.addr)
    return (6, ref.addr)


def select_interesting_l32r_refs(
    l32r_refs: list[L32RReference], limit: int
) -> list[L32RReference]:
    refs = [ref for ref in l32r_refs if is_interesting_l32r_ref(ref)]
    refs.sort(key=l32r_interest_priority)
    return refs[:limit]


def find_l32r_references(
    data: bytes,
    sections: list[Section],
    strings: list[StringEntry],
    props: list[XtProp],
    function_candidates: list[FunctionCandidate],
    xtensa_info: XtensaInfo | None,
    interesting_limit: int = 256,
) -> tuple[int, list[L32RReference], list[L32RReference]]:
    text = section_by_name(sections, ".text")
    if text is None:
        return 0, [], []
    text_blob = section_bytes(data, text)
    coverage = xt_prop_insn_coverage(text, props)
    use_absolute_literals = False
    if xtensa_info is not None:
        use_absolute_literals = xtensa_info.fields.get("USE_ABSOLUTE_LITERALS") == "1"

    count = 0
    all_refs_for_scans: list[L32RReference] = []
    visited = bytearray(len(text_blob))
    for prop in props:
        if not prop.flags & XT_PROP_INSN:
            continue
        start = max(prop.addr, text.addr) - text.addr
        end = min(prop.addr + max(prop.size, 1), text.addr + text.size) - text.addr
        if not (0 <= start < end <= len(text_blob)):
            continue
        for off in range(start, max(start, end - 2)):
            if visited[off]:
                continue
            visited[off] = 1
            if text_blob[off] & 0x0F != 0x01:
                continue
            if not coverage[off]:
                continue
            ref = l32r_reference_from_addr(
                data,
                sections,
                strings,
                function_candidates,
                text,
                text_blob,
                off,
                prop,
                use_absolute_literals,
            )
            if ref is None:
                continue
            count += 1
            all_refs_for_scans.append(ref)
    interesting = select_interesting_l32r_refs(all_refs_for_scans, interesting_limit)
    return count, interesting, all_refs_for_scans


def find_critical_l32r_refs(
    strings: list[StringEntry],
    l32r_refs: list[L32RReference],
    sample_limit: int = 16,
) -> list[CriticalL32RScan]:
    scans: list[CriticalL32RScan] = []
    for pattern in CRITICAL_STRING_PATTERNS:
        matches = [entry for entry in strings if pattern in entry.value]
        if not matches:
            scans.append(
                CriticalL32RScan(
                    pattern=pattern,
                    string_addr=None,
                    string_value=None,
                    hit_count=0,
                    hits=[],
                )
            )
            continue
        for entry in matches:
            hits = [
                ref
                for ref in l32r_refs
                if ref.literal_string_addr == entry.addr or ref.value_string_addr == entry.addr
            ]
            scans.append(
                CriticalL32RScan(
                    pattern=pattern,
                    string_addr=entry.addr,
                    string_value=entry.value,
                    hit_count=len(hits),
                    hits=hits[:sample_limit],
                )
            )
    return scans


def find_dram_op_l32r_refs(
    sections: list[Section],
    l32r_refs: list[L32RReference],
    sample_limit: int = 64,
) -> list[L32RReference]:
    dram_op = section_by_name(sections, ".dram_op.data")
    if dram_op is None:
        return []
    out: list[L32RReference] = []
    for ref in l32r_refs:
        literal_hit = dram_op.addr <= ref.literal_addr < dram_op.addr + dram_op.size
        value_hit = (
            ref.literal_value is not None
            and dram_op.addr <= (ref.literal_value & ~1) < dram_op.addr + dram_op.size
        )
        if literal_hit or value_hit:
            out.append(ref)
            if len(out) >= sample_limit:
                break
    return out


def find_rodata_refs(
    data: bytes,
    sections: list[Section],
    strings: list[StringEntry],
    function_candidates: list[FunctionCandidate],
) -> list[RodataReference]:
    text = section_by_name(sections, ".text")
    rodata = section_by_name(sections, ".rodata")
    if text is None or rodata is None:
        return []
    blob = section_bytes(data, text)
    out: list[RodataReference] = []
    for off in range(0, len(blob) - 3, 4):
        value = struct.unpack_from("<I", blob, off)[0]
        if not (rodata.addr <= value < rodata.addr + rodata.size):
            continue
        entry = string_containing_addr(strings, value)
        if entry is None:
            continue
        ref_addr = text.addr + off
        owner_entry, owner_delta = owner_for_addr(function_candidates, ref_addr)
        out.append(
            RodataReference(
                ref_addr=ref_addr,
                value=value,
                string_addr=entry.addr,
                string_offset=value - entry.addr,
                string_value=entry.value,
                owner_entry=owner_entry,
                owner_delta=owner_delta,
            )
        )
    return out


def is_interesting_rodata_ref(ref: RodataReference) -> bool:
    tokens = (
        "Invalid",
        "Inconsistent",
        "Error",
        "buffer",
        "Buffer",
        "DMA",
        "iDMA",
        "ProcessTileWise",
        "tileManager",
        "cnnarena",
        "cnnrt",
        "dmaif",
        "operations/",
    )
    return any(token in ref.string_value for token in tokens)


def find_critical_string_refs(
    data: bytes,
    sections: list[Section],
    strings: list[StringEntry],
    function_candidates: list[FunctionCandidate],
    sample_limit: int = 16,
) -> list[CriticalStringScan]:
    text = section_by_name(sections, ".text")
    if text is None:
        return []

    scans: list[CriticalStringScan] = []
    for pattern in CRITICAL_STRING_PATTERNS:
        matches = [entry for entry in strings if pattern in entry.value]
        if not matches:
            scans.append(
                CriticalStringScan(
                    pattern=pattern,
                    string_addr=None,
                    string_value=None,
                    aligned_hit_count=0,
                    byte_hit_count=0,
                    aligned_hits=[],
                    byte_hits=[],
                )
            )
            continue
        for entry in matches:
            scans.append(
                CriticalStringScan(
                    pattern=pattern,
                    string_addr=entry.addr,
                    string_value=entry.value,
                    aligned_hit_count=0,
                    byte_hit_count=0,
                    aligned_hits=[],
                    byte_hits=[],
                )
            )

    suffix_targets: dict[int, list[tuple[int, int]]] = {}
    for index, scan in enumerate(scans):
        if scan.string_addr is None or scan.string_value is None:
            continue
        for string_offset in range(len(scan.string_value)):
            suffix_targets.setdefault(scan.string_addr + string_offset, []).append(
                (index, string_offset)
            )

    blob = section_bytes(data, text)
    for off in range(0, len(blob) - 3):
        value = struct.unpack_from("<I", blob, off)[0]
        targets = suffix_targets.get(value)
        if not targets:
            continue
        ref_addr = text.addr + off
        owner_entry, owner_delta = owner_for_addr(function_candidates, ref_addr)
        aligned = off % 4 == 0
        for index, string_offset in targets:
            scan = scans[index]
            hit = CriticalStringHit(
                ref_addr=ref_addr,
                value=value,
                string_offset=string_offset,
                owner_entry=owner_entry,
                owner_delta=owner_delta,
            )
            scan.byte_hit_count += 1
            if len(scan.byte_hits) < sample_limit:
                scan.byte_hits.append(hit)
            if aligned:
                scan.aligned_hit_count += 1
                if len(scan.aligned_hits) < sample_limit:
                    scan.aligned_hits.append(hit)

    return scans


def build_standard_islands(data: bytes, sections: list[Section]) -> list[StandardIsland]:
    islands: list[StandardIsland] = []
    for spec in KNOWN_STANDARD_ISLANDS:
        instructions: list[StandardIslandInstruction] = []
        verified = True
        for addr, expected_text, mnemonic, effect in spec["instructions"]:
            expected = bytes.fromhex(expected_text)
            actual = va_bytes(data, sections, addr, len(expected))
            item_verified = actual == expected
            if not item_verified:
                verified = False
            instructions.append(
                StandardIslandInstruction(
                    addr=addr,
                    expected_bytes=expected.hex(" "),
                    actual_bytes=None if actual is None else actual.hex(" "),
                    verified=item_verified,
                    mnemonic=mnemonic,
                    effect=effect,
                )
            )
        start = min(item.addr for item in instructions)
        end = max(item.addr + len(bytes.fromhex(item.expected_bytes)) for item in instructions)
        islands.append(
            StandardIsland(
                label=str(spec["label"]),
                start=start,
                end=end,
                verified=verified,
                note=str(spec["note"]),
                instructions=instructions,
            )
        )
    return islands


STANDARD_MEM_OPS = {
    0x0: ("l8ui", 1, False),
    0x1: ("l16ui", 2, False),
    0x2: ("l32i", 4, False),
    0x4: ("s8i", 1, True),
    0x5: ("s16i", 2, True),
    0x6: ("s32i", 4, True),
}


STANDARD_LOOP_KINDS = {
    0x8: "loop",
    0x9: "loopnez",
    0xA: "loopgtz",
}


VPU_BUFFER_OFFSETS = {
    0x00,
    0x01,
    0x02,
    0x04,
    0x08,
    0x0C,
    0x10,
    0x14,
    0x18,
    0x1C,
    0x20,
    0x24,
    0x28,
    0x2C,
    0x30,
    0x34,
    0x38,
    0x39,
    0x3A,
    0x3B,
}


def decode_standard_mem_access(blob: bytes, offset: int) -> tuple[str, int, int, int, int, bool] | None:
    if offset + 3 > len(blob):
        return None
    b0, b1, b2 = blob[offset], blob[offset + 1], blob[offset + 2]
    if b0 & 0x0F != 0x02:
        return None
    op_kind = b1 >> 4
    op = STANDARD_MEM_OPS.get(op_kind)
    if op is None:
        return None
    mnemonic, access_size, is_store = op
    value_reg = b0 >> 4
    base_reg = b1 & 0x0F
    mem_offset = b2 * access_size
    return mnemonic, base_reg, value_reg, mem_offset, access_size, is_store


def find_standard_field_access_clusters(
    data: bytes,
    sections: list[Section],
    props: list[XtProp],
    function_candidates: list[FunctionCandidate],
    max_offset: int = 0x80,
    min_vpu_offsets: int = 6,
    sample_limit: int = 8,
    cluster_limit: int = 24,
) -> list[FieldAccessCluster]:
    text = section_by_name(sections, ".text")
    if text is None:
        return []
    raw_clusters: dict[tuple[int, int], list[StandardFieldAccess]] = {}
    for prop in props:
        if not prop.flags & XT_PROP_INSN:
            continue
        if not (text.addr <= prop.addr < text.addr + text.size):
            continue
        addr = prop.addr
        end = min(prop.addr + prop.size, text.addr + text.size)
        while addr < end:
            head = va_bytes(data, sections, addr, 1)
            if not head:
                break
            length = flix_insn_len(head[0])
            raw = va_bytes(data, sections, addr, length)
            if raw is None or len(raw) != length:
                break
            if flix_kind(head[0]) != "core24":
                addr += length
                continue
            decoded = decode_standard_mem_access(raw, 0)
            if decoded is None:
                addr += length
                continue
            mnemonic, base_reg, value_reg, mem_offset, access_size, is_store = decoded
            if mem_offset > max_offset:
                addr += length
                continue
            if base_reg in {0, 1}:
                addr += length
                continue
            owner_entry, owner_delta = owner_for_addr(function_candidates, addr)
            if owner_entry is None:
                addr += length
                continue
            hit = StandardFieldAccess(
                addr=addr,
                op=mnemonic,
                base_reg=base_reg,
                value_reg=value_reg,
                offset=mem_offset,
                access_size=access_size,
                is_store=is_store,
                owner_entry=owner_entry,
                owner_delta=owner_delta,
            )
            raw_clusters.setdefault((owner_entry, base_reg), []).append(hit)
            addr += length

    clusters: list[FieldAccessCluster] = []
    for (owner_entry, base_reg), hits in raw_clusters.items():
        unique_offsets = sorted({hit.offset for hit in hits})
        vpu_offsets = sorted({hit.offset for hit in hits if hit.offset in VPU_BUFFER_OFFSETS})
        if len(vpu_offsets) < min_vpu_offsets:
            continue
        hits.sort(key=lambda hit: hit.addr)
        clusters.append(
            FieldAccessCluster(
                owner_entry=owner_entry,
                base_reg=base_reg,
                hit_count=len(hits),
                unique_offsets=unique_offsets,
                vpu_buffer_offsets=vpu_offsets,
                sample_hits=hits[:sample_limit],
            )
        )

    clusters.sort(
        key=lambda cluster: (
            len(cluster.vpu_buffer_offsets),
            len(cluster.unique_offsets),
            cluster.hit_count,
            -cluster.owner_entry,
        ),
        reverse=True,
    )
    return clusters[:cluster_limit]


def find_loop_target_candidates(
    sections: list[Section],
    props: list[XtProp],
    function_candidates: list[FunctionCandidate],
    field_clusters: list[FieldAccessCluster],
    sample_limit: int = 128,
) -> tuple[int, list[LoopTargetCandidate]]:
    text = section_by_name(sections, ".text")
    if text is None:
        return 0, []
    field_bases_by_owner: dict[int, set[int]] = {}
    for cluster in field_clusters:
        field_bases_by_owner.setdefault(cluster.owner_entry, set()).add(cluster.base_reg)

    count = 0
    candidates: list[LoopTargetCandidate] = []
    for prop in props:
        if not prop.flags & XT_PROP_LOOP_TARGET:
            continue
        if not (text.addr <= prop.addr < text.addr + text.size):
            continue
        count += 1
        owner_entry, owner_delta = owner_for_addr(function_candidates, prop.addr)
        bases = sorted(field_bases_by_owner.get(owner_entry or -1, set()))
        if not bases:
            continue
        candidates.append(
            LoopTargetCandidate(
                addr=prop.addr,
                owner_entry=owner_entry,
                owner_delta=owner_delta,
                prop_size=prop.size,
                prop_flags=flags_text(prop.flags),
                matched_field_bases=bases,
            )
        )
        if len(candidates) >= sample_limit:
            break
    return count, candidates


def prop_run_samples(
    data: bytes,
    sections: list[Section],
    props: list[XtProp],
    start: int,
    end: int,
    sample_limit: int = 64,
) -> list[dict[str, object]]:
    out: list[dict[str, object]] = []
    for prop in props:
        if not (start <= prop.addr < end):
            continue
        raw = va_bytes(data, sections, prop.addr, min(prop.size, 16)) if prop.size else b""
        out.append(
            {
                "addr": prop.addr,
                "size": prop.size,
                "flags": flags_text(prop.flags),
                "bytes": raw.hex(" ") if raw else "",
            }
        )
        if len(out) >= sample_limit:
            break
    return out


def visible_base_accesses_in_range(
    data: bytes,
    sections: list[Section],
    owner_entry: int,
    start: int,
    end: int,
    base_reg: int,
    max_offset: int = 0x100,
    sample_limit: int = 64,
) -> list[StandardFieldAccess]:
    return boundary_core_mem_accesses_in_range(
        data=data,
        sections=sections,
        owner_entry=owner_entry,
        start=start,
        end=end,
        base_reg=base_reg,
        max_offset=max_offset,
        sample_limit=sample_limit,
    )


def boundary_kind_counts(
    data: bytes,
    sections: list[Section],
    start: int,
    end: int,
) -> dict[str, int]:
    counts = {"core24": 0, "dens16": 0, "flix64": 0, "flix128": 0, "truncated": 0}
    addr = start
    while addr < end:
        head = va_bytes(data, sections, addr, 1)
        if not head:
            break
        length = flix_insn_len(head[0])
        raw = va_bytes(data, sections, addr, length)
        if raw is None or len(raw) != length:
            counts["truncated"] += 1
            break
        counts[flix_kind(head[0])] += 1
        addr += length
    return counts


def boundary_core_mem_accesses_in_range(
    data: bytes,
    sections: list[Section],
    owner_entry: int,
    start: int,
    end: int,
    base_reg: int | None = None,
    max_offset: int | None = None,
    sample_limit: int = 64,
) -> list[StandardFieldAccess]:
    text = section_by_name(sections, ".text")
    if text is None:
        return []
    out: list[StandardFieldAccess] = []
    addr = start
    while addr < end:
        head = va_bytes(data, sections, addr, 1)
        if not head:
            break
        length = flix_insn_len(head[0])
        raw = va_bytes(data, sections, addr, length)
        if raw is None or len(raw) != length:
            break
        if flix_kind(head[0]) != "core24":
            addr += length
            continue
        decoded = decode_standard_mem_access(raw, 0)
        if decoded is None:
            addr += length
            continue
        mnemonic, found_base, value_reg, mem_offset, access_size, is_store = decoded
        if base_reg is not None and found_base != base_reg:
            addr += length
            continue
        if max_offset is not None and mem_offset > max_offset:
            addr += length
            continue
        out.append(
            StandardFieldAccess(
                addr=addr,
                op=mnemonic,
                base_reg=found_base,
                value_reg=value_reg,
                offset=mem_offset,
                access_size=access_size,
                is_store=is_store,
                owner_entry=owner_entry,
                owner_delta=addr - owner_entry,
            )
        )
        if len(out) >= sample_limit:
            break
        addr += length
    return out


def byte_pattern_hits(data: bytes, sections: list[Section], start: int, end: int, pattern: bytes) -> list[int]:
    raw = va_bytes(data, sections, start, end - start)
    if raw is None:
        return []
    hits: list[int] = []
    for off in range(0, len(raw) - len(pattern) + 1):
        if raw[off : off + len(pattern)] == pattern:
            hits.append(start + off)
    return hits


def possible_standard_loop_hits_to_target(
    data: bytes,
    sections: list[Section],
    target: int,
    search_start: int,
    search_end: int,
) -> list[int]:
    text = section_by_name(sections, ".text")
    if text is None:
        return []
    blob = section_bytes(data, text)
    hits: list[int] = []
    for addr in range(search_start, search_end):
        off = addr - text.addr
        if off < 0 or off + 3 > len(blob):
            continue
        b0, b1, b2 = blob[off], blob[off + 1], blob[off + 2]
        if b0 == 0x76 and b1 >> 4 in STANDARD_LOOP_KINDS and addr + 4 + b2 == target:
            hits.append(addr)
    return hits


def build_focused_loop_investigations(
    data: bytes,
    sections: list[Section],
    props: list[XtProp],
) -> list[FocusedLoopInvestigation]:
    specs = (
        {
            "label": "flix_assisted_INFO13_record_lead",
            "owner": 0x7003B468,
            "target": 0x7003C102,
            "priority": "independent_flix_branch",
            "assessment": (
                "Clean 0xaa-byte loop_target property run inside the strongest "
                "0x40-record-shaped field cluster. FLIX-correct boundaries show "
                "the owner reads descriptor-shaped a2 fields within 0x02..0x3d; "
                "the 0x7003c102 loop body itself contains stack spills plus FLIX "
                "bundles. The length-only FLIX overlay therefore closes the "
                "record-layout evidence but cannot name a bundle-interior count "
                "register."
            ),
            "range_start": 0x7003B468,
            "range_end": 0x7003C1C0,
            "base": 2,
            "next_action": (
                "Keep INFO12/INFO13 closed at the ABI plus record-layout level. "
                "Decode FLIX/TIE slots only if firmware-local count-register "
                "naming becomes necessary."
            ),
        },
        {
            "label": "downgraded_error_tail_loop_target",
            "owner": 0x7003CE3C,
            "target": 0x7003D423,
            "priority": "downgraded",
            "assessment": (
                "Loop-target property is real but surrounding props contain short "
                "branch targets, unreachable gaps, and insn|data mixed runs. Treat "
                "as switch/error-tail lead, not a descriptor-array walk."
            ),
            "range_start": 0x7003D3A0,
            "range_end": 0x7003D470,
            "base": 2,
            "next_action": (
                "Keep as secondary local-control-flow evidence only; do not use "
                "this downgraded target to drive INFO12/INFO13 closure."
            ),
        },
    )
    investigations: list[FocusedLoopInvestigation] = []
    for spec in specs:
        target = int(spec["target"])
        target_prop = best_prop_for_addr(props, target)
        range_start = int(spec["range_start"])
        range_end = int(spec["range_end"])
        loop_body_start = target
        loop_body_end = target + (0 if target_prop is None else target_prop.size)
        framing_hits = byte_pattern_hits(data, sections, range_start, range_end, b"\x06\x04\x02")
        visible_field_accesses = visible_base_accesses_in_range(
            data,
            sections,
            int(spec["owner"]),
            range_start,
            range_end,
            int(spec["base"]),
        )
        visible_field_offsets = sorted({hit.offset for hit in visible_field_accesses})
        loop_body_core_mem_accesses = boundary_core_mem_accesses_in_range(
            data=data,
            sections=sections,
            owner_entry=int(spec["owner"]),
            start=loop_body_start,
            end=loop_body_end,
            sample_limit=32,
        )
        if target == 0x7003C102:
            count_status = (
                "INFO12 count is closed at the ABI/kernel/provider boundary: "
                "firmware receives buffer_count, and the provider gates it below "
                "0x21. After FLIX correction there is no byte-aligned "
                "LOOP/LOOPNEZ/LOOPGTZ to 0x7003c102; the loop_target body exposes "
                "stack-spill core ops plus FLIX bundles. A firmware-local count "
                "register name would require FLIX/TIE slot semantics, but the "
                "primitive model no longer depends on that name."
            )
            stride_status = (
                "INFO13 stride is closed at the record-layout level: "
                "boundary-visible a2 descriptor loads cover offsets "
                f"{', '.join(hx(value) or '' for value in visible_field_offsets)} "
                "within a 0x40-byte record, and the kernel/provider copies "
                "INFO13 as struct vpu_buffer[INFO12] with 0x40 stride. The "
                "length-only FLIX overlay does not expose a boundary-visible "
                "a2 += 0x40 instruction in this owner."
            )
        else:
            count_status = (
                "byte-aligned hardware LOOP is present for this downgraded local "
                "control-flow target, but it is not the INFO13 descriptor walk."
            )
            stride_status = (
                "downgraded local-control-flow lead; do not use it as the "
                "INFO13 descriptor stride proof."
            )
        investigations.append(
            FocusedLoopInvestigation(
                label=str(spec["label"]),
                owner_entry=int(spec["owner"]),
                loop_target=target,
                priority=str(spec["priority"]),
                assessment=str(spec["assessment"]),
                target_prop_size=None if target_prop is None else target_prop.size,
                target_prop_flags=None if target_prop is None else flags_text(target_prop.flags),
                prop_runs=prop_run_samples(data, sections, props, range_start, range_end),
                visible_field_accesses=visible_field_accesses,
                flix_framing="FLIX128 framing tail 06 04 02",
                flix_framing_hits=framing_hits[:32],
                flix_framing_deltas=[
                    framing_hits[index + 1] - framing_hits[index]
                    for index in range(len(framing_hits) - 1)
                ][:31],
                standard_loop_opcode_hits=possible_standard_loop_hits_to_target(
                    data, sections, target, range_start, target
                ),
                boundary_counts=boundary_kind_counts(data, sections, range_start, range_end),
                loop_body_start=loop_body_start,
                loop_body_end=loop_body_end,
                loop_body_boundary_counts=boundary_kind_counts(
                    data, sections, loop_body_start, loop_body_end
                ),
                loop_body_core_mem_accesses=loop_body_core_mem_accesses,
                count_status=count_status,
                stride_0x40_status=stride_status,
                next_action=str(spec["next_action"]),
            )
        )
    return investigations


IDMA_CLUSTER_PATTERNS = {
    "add idma request fail in %s",
    "ERROR CALLBACK: iDMA in Error",
    "INTERRUPT CALLBACK : processing iDMA interrupt",
    "iDMA error",
    "iDMA schedule error",
    "iDMA wait error",
    "../vp6-ann/libcommon/src/idma_mvpu6/dmaif.c",
    "sDesc > eDesc",
    "eDesc >= TM_DMA_DESC_IDX_MAX",
    "_DMA_STALL",
    "No error",
}


def build_critical_owner_clusters(
    critical_l32r_refs: list[CriticalL32RScan],
    sample_limit: int = 8,
) -> list[CriticalOwnerCluster]:
    raw: dict[int, dict[str, object]] = {}
    for scan in critical_l32r_refs:
        if scan.pattern not in IDMA_CLUSTER_PATTERNS:
            continue
        for hit in scan.hits:
            if hit.owner_entry is None:
                continue
            item = raw.setdefault(
                hit.owner_entry,
                {"patterns": set(), "hits": 0, "sample_refs": []},
            )
            patterns = item["patterns"]
            assert isinstance(patterns, set)
            patterns.add(scan.pattern)
            item["hits"] = int(item["hits"]) + 1
            sample_refs = item["sample_refs"]
            assert isinstance(sample_refs, list)
            if len(sample_refs) < sample_limit:
                sample_refs.append(
                    {
                        "pattern": scan.pattern,
                        "ref_addr": hit.addr,
                        "literal_addr": hit.literal_addr,
                        "literal_string_offset": hit.literal_string_offset,
                    }
                )
    out: list[CriticalOwnerCluster] = []
    for owner, item in raw.items():
        patterns = sorted(item["patterns"])
        assert isinstance(patterns, list)
        hit_count = int(item["hits"])
        if len(patterns) >= 4:
            assessment = "top DMA/iDMA owner candidate"
        elif len(patterns) >= 2:
            assessment = "secondary DMA/iDMA owner candidate"
        else:
            assessment = "single-string lead"
        out.append(
            CriticalOwnerCluster(
                owner_entry=owner,
                pattern_count=len(patterns),
                hit_count=hit_count,
                patterns=patterns,
                sample_refs=item["sample_refs"],
                assessment=assessment,
            )
        )
    out.sort(key=lambda item: (item.pattern_count, item.hit_count, -item.owner_entry), reverse=True)
    return out


DMA_OWNER_INVESTIGATION_PATTERNS = (
    "iDMA schedule error",
    "iDMA wait error",
    "../vp6-ann/libcommon/src/idma_mvpu6/dmaif.c",
    "sDesc > eDesc",
    "Data buffer does not start in DRAM",
    "Data buffer does not fit in DRAM",
)


def standard_a2_access_summary(
    data: bytes,
    sections: list[Section],
    start: int,
    end: int,
    max_offset: int = 0x80,
) -> tuple[list[int], int]:
    accesses = boundary_core_mem_accesses_in_range(
        data=data,
        sections=sections,
        owner_entry=start,
        start=start,
        end=end,
        base_reg=2,
        max_offset=max_offset,
        sample_limit=1000000,
    )
    return sorted({hit.offset for hit in accesses}), len(accesses)


def build_dma_owner_investigations(
    data: bytes,
    sections: list[Section],
    props: list[XtProp],
    l32r_refs: list[L32RReference],
    critical_owner_clusters: list[CriticalOwnerCluster],
) -> list[DmaOwnerInvestigation]:
    if critical_owner_clusters:
        cluster_rank, top_cluster = max(
            enumerate(critical_owner_clusters, start=1),
            key=lambda item: (
                item[1].pattern_count,
                item[1].hit_count,
                -item[1].owner_entry,
            ),
        )
        owner = top_cluster.owner_entry
        cluster_patterns = top_cluster.patterns
        cluster_pattern_count = top_cluster.pattern_count
        cluster_hit_count = top_cluster.hit_count
    else:
        cluster_rank = 0
        owner = 0x70044B74
        cluster_patterns = []
        cluster_pattern_count = 0
        cluster_hit_count = 0
    start = owner
    end = owner + 0x80C
    evidence_refs: list[dict[str, object]] = []
    for pattern in DMA_OWNER_INVESTIGATION_PATTERNS:
        candidates = [
            ref
            for ref in l32r_refs
            if ref.owner_entry == owner
            and (
                (ref.literal_string_value is not None and pattern in ref.literal_string_value)
                or (ref.value_string_value is not None and pattern in ref.value_string_value)
            )
        ]
        candidates.sort(key=lambda ref: ref.addr)
        if not candidates:
            continue
        ref = candidates[0]
        prop = best_prop_for_addr(props, ref.addr)
        evidence_refs.append(
            {
                "pattern": pattern,
                "ref_addr": ref.addr,
                "owner_delta": ref.owner_delta,
                "target_reg": ref.target_reg,
                "literal_addr": ref.literal_addr,
                "literal_string_offset": ref.literal_string_offset,
                "prop_addr": None if prop is None else prop.addr,
                "prop_size": None if prop is None else prop.size,
                "prop_flags": None if prop is None else flags_text(prop.flags),
            }
        )

    framing_hits = byte_pattern_hits(data, sections, start, end, b"\x06\x04\x02")
    a2_offsets, a2_count = standard_a2_access_summary(data, sections, start, end)
    return [
        DmaOwnerInvestigation(
            label="top_dmaif_schedule_wait_owner",
            owner_entry=owner,
            analysis_start=start,
            analysis_end=end,
            cluster_rank=cluster_rank,
            cluster_pattern_count=cluster_pattern_count,
            cluster_hit_count=cluster_hit_count,
            cluster_patterns=cluster_patterns,
            assessment=(
                "Top iDMA schedule/wait wrapper candidate selected by L32R "
                "string-cluster ranking. The same .xt.prop owner contains refs "
                "to schedule/wait errors, dmaif.c, descriptor range validation, "
                "and data-buffer DRAM validation strings."
            ),
            q1_status=(
                "owner_closed_for_static_schedule_wait_anchor; FLIX-correct "
                "boundaries separate core/density items from bundles in the owner. "
                "Completion-write burst timing remains a runtime/slot-semantics "
                "question."
            ),
            evidence_refs=evidence_refs,
            prop_runs=prop_run_samples(data, sections, props, start, end),
            flix_framing="FLIX128 framing tail 06 04 02",
            flix_framing_hit_count=len(framing_hits),
            flix_framing_hits=framing_hits[:32],
            standard_a2_offsets=a2_offsets,
            standard_a2_access_count=a2_count,
            next_action=(
                "Use this owner as the Q1 firmware anchor for DMA schedule/wait. "
                "Close timing with runtime instrumentation or deeper FLIX/TIE "
                "slot semantics; do not infer inter-store timing from string "
                "ownership alone."
            ),
        )
    ]


OUTPUT_VALIDATION_PATTERNS = (
    "Inconsistent output size",
    "Invalid output size",
    "Inconsistent output height",
    "Invalid input/output buffer size",
    "Invalid input/output dimensions",
    "Invalid output data type",
    "Invalid output score shift",
    "Invalid output args",
    "Invalid output tile",
    "Invalid output batch",
    "Invalid output zero point",
    "Invalid output tile dimensions",
    "Inconsistent output batch",
    "Inconsistent output depth",
    "Output alignment requirements",
    "output == NULL",
    "output failed",
)


def output_validation_patterns(text: str | None) -> list[str]:
    if not text or "output" not in text.lower():
        return []
    return [pattern for pattern in OUTPUT_VALIDATION_PATTERNS if pattern in text]


def build_output_validation_investigations(
    data: bytes,
    sections: list[Section],
    props: list[XtProp],
    rodata_refs: list[RodataReference],
    l32r_refs: list[L32RReference],
    owner_limit: int = 8,
    evidence_limit: int = 8,
) -> list[OutputValidationInvestigation]:
    grouped: dict[int, dict[str, object]] = {}

    def add_evidence(owner: int | None, evidence: dict[str, object], patterns: list[str]) -> None:
        if owner is None or not patterns:
            return
        item = grouped.setdefault(owner, {"patterns": set(), "evidence_refs": []})
        pattern_set = item["patterns"]
        refs = item["evidence_refs"]
        assert isinstance(pattern_set, set)
        assert isinstance(refs, list)
        pattern_set.update(patterns)
        if len(refs) < evidence_limit:
            refs.append(evidence)

    for ref in rodata_refs:
        patterns = output_validation_patterns(ref.string_value)
        if not patterns:
            continue
        prop = best_prop_for_addr(props, ref.ref_addr)
        add_evidence(
            ref.owner_entry,
            {
                "kind": "rodata32",
                "pattern": patterns[0],
                "ref_addr": ref.ref_addr,
                "owner_delta": ref.owner_delta,
                "string_addr": ref.string_addr,
                "string_offset": ref.string_offset,
                "string_value": ref.string_value,
                "prop_addr": None if prop is None else prop.addr,
                "prop_size": None if prop is None else prop.size,
                "prop_flags": None if prop is None else flags_text(prop.flags),
            },
            patterns,
        )

    for ref in l32r_refs:
        texts = [
            value
            for value in (ref.literal_string_value, ref.value_string_value)
            if value
        ]
        if not texts:
            continue
        patterns = sorted(
            {pattern for text in texts for pattern in output_validation_patterns(text)}
        )
        if not patterns:
            continue
        prop = best_prop_for_addr(props, ref.addr)
        add_evidence(
            ref.owner_entry,
            {
                "kind": "l32r",
                "pattern": patterns[0],
                "ref_addr": ref.addr,
                "owner_delta": ref.owner_delta,
                "target_reg": ref.target_reg,
                "literal_addr": ref.literal_addr,
                "literal_string_addr": ref.literal_string_addr,
                "literal_string_offset": ref.literal_string_offset,
                "literal_string_value": ref.literal_string_value,
                "value_string_addr": ref.value_string_addr,
                "value_string_offset": ref.value_string_offset,
                "value_string_value": ref.value_string_value,
                "prop_addr": None if prop is None else prop.addr,
                "prop_size": None if prop is None else prop.size,
                "prop_flags": None if prop is None else flags_text(prop.flags),
            },
            patterns,
        )

    def owner_score(owner_item: tuple[int, dict[str, object]]) -> tuple[int, int, int, int, int]:
        owner, item = owner_item
        patterns = item["patterns"]
        refs = item["evidence_refs"]
        assert isinstance(patterns, set)
        assert isinstance(refs, list)
        has_size = int("Inconsistent output size" in patterns or "Invalid output size" in patterns)
        has_height = int("Inconsistent output height" in patterns)
        return (has_size, has_height, len(patterns), len(refs), -owner)

    investigations: list[OutputValidationInvestigation] = []
    for owner, item in sorted(grouped.items(), key=owner_score, reverse=True)[:owner_limit]:
        patterns = sorted(item["patterns"])
        refs = item["evidence_refs"]
        assert isinstance(patterns, list)
        assert isinstance(refs, list)
        max_ref = max(int(ref["ref_addr"]) for ref in refs)
        end = max(owner + 0x80, min(owner + 0x1000, max_ref + 0x80))
        if "Inconsistent output size" in patterns or "Invalid output size" in patterns:
            label = "output_size_validation_owner"
            assessment = (
                "Static output-size validation owner selected by direct rodata32 "
                "or L32R-backed output-size diagnostics."
            )
        elif "Inconsistent output height" in patterns:
            label = "output_height_validation_owner"
            assessment = (
                "Static output-height validation owner; adjacent output-shape "
                "evidence for Q4, separate from the runtime fill loop."
            )
        else:
            label = "output_validation_owner"
            assessment = (
                "Output-related validation owner selected by output diagnostic "
                "string references."
            )
        investigations.append(
            OutputValidationInvestigation(
                label=label,
                owner_entry=owner,
                analysis_start=owner,
                analysis_end=end,
                assessment=assessment,
                q4_status=(
                    "static_output_validation_owner_identified; this maps "
                    "firmware output-shape validation owners. It does not yet "
                    "identify the runtime settings+0x08 output-fill loop observed "
                    "in completed wrapper replay."
                ),
                referenced_patterns=patterns,
                evidence_refs=refs,
                prop_runs=prop_run_samples(data, sections, props, owner, end),
                boundary_counts=boundary_kind_counts(data, sections, owner, end),
                next_action=(
                    "Use these owner anchors for targeted FLIX/TIE or runtime "
                    "tracing if Q4 needs the exact settings+0x08 fill loop."
                ),
            )
        )
    return investigations


def raw_u32_refs_to_range(
    data: bytes,
    sections: list[Section],
    start: int,
    end: int,
    source_names: set[str] | None = None,
    function_candidates: list[FunctionCandidate] | None = None,
    ignore_ref_ranges: list[tuple[int, int]] | None = None,
    sample_limit: int = 32,
    scan_step: int = 4,
) -> list[dict[str, object]]:
    hits: list[dict[str, object]] = []
    if scan_step <= 0:
        raise ValueError("scan_step must be positive")
    for section in sections:
        if source_names is not None and section.name not in source_names:
            continue
        blob = section_bytes(data, section)
        for off in range(0, max(0, len(blob) - 3), scan_step):
            ref_addr = section.addr + off
            if ignore_ref_ranges and any(lo <= ref_addr < hi for lo, hi in ignore_ref_ranges):
                continue
            value = struct.unpack_from("<I", blob, off)[0]
            if start <= value < end:
                hit: dict[str, object] = {
                    "ref_addr": ref_addr,
                    "source_section": section.name,
                    "value": value,
                    "delta": value - start,
                    "slot_aligned": ((value - start) % 4) == 0,
                    "alignment": ref_addr & 3,
                }
                if function_candidates is not None:
                    owner_entry, owner_delta = owner_for_addr(function_candidates, ref_addr)
                    hit["owner_entry"] = owner_entry
                    hit["owner_delta"] = owner_delta
                hits.append(hit)
                if len(hits) >= sample_limit:
                    return hits
    return hits


def l32r_refs_to_range(
    refs: list[L32RReference],
    start: int,
    end: int,
    sample_limit: int = 32,
) -> list[dict[str, object]]:
    hits: list[dict[str, object]] = []
    for ref in refs:
        literal_hit = start <= ref.literal_addr < end
        value_hit = ref.literal_value is not None and start <= (ref.literal_value & ~1) < end
        if not literal_hit and not value_hit:
            continue
        literal_delta = ref.literal_addr - start if literal_hit else None
        value_delta = (ref.literal_value & ~1) - start if value_hit else None
        hits.append(
            {
                "ref_addr": ref.addr,
                "owner_entry": ref.owner_entry,
                "owner_delta": ref.owner_delta,
                "target_reg": ref.target_reg,
                "literal_addr": ref.literal_addr,
                "literal_value": ref.literal_value,
                "literal_hit": literal_hit,
                "value_hit": value_hit,
                "literal_delta": literal_delta,
                "value_delta": value_delta,
                "literal_slot_aligned": literal_delta is not None and literal_delta % 4 == 0,
                "value_slot_aligned": value_delta is not None and value_delta % 4 == 0,
                "prop_addr": ref.prop_addr,
                "prop_size": ref.prop_size,
                "prop_flags": ref.prop_flags,
            }
        )
        if len(hits) >= sample_limit:
            return hits
    return hits


def pointer_run_owner_counts(run: PointerRun) -> list[dict[str, object]]:
    by_owner: dict[int | None, list[PointerEntry]] = {}
    for entry in run.entries:
        by_owner.setdefault(entry.owner_entry, []).append(entry)
    out: list[dict[str, object]] = []
    for owner, entries in sorted(
        by_owner.items(),
        key=lambda item: (item[0] is None, 0 if item[0] is None else item[0]),
    ):
        out.append(
            {
                "owner_entry": owner,
                "count": len(entries),
                "min_target": min(entry.value & ~1 for entry in entries),
                "max_target": max(entry.value & ~1 for entry in entries),
                "first_slot": min(entry.addr for entry in entries),
                "last_slot": max(entry.addr for entry in entries),
            }
        )
    return out


def build_pointer_run_investigations(
    data: bytes,
    sections: list[Section],
    pointer_runs: list[PointerRun],
    function_candidates: list[FunctionCandidate],
    l32r_refs: list[L32RReference],
) -> list[PointerRunInvestigation]:
    investigations: list[PointerRunInvestigation] = []
    source_names = {".text", ".rodata", ".data", ".dram0.data"}
    for run in pointer_runs:
        run_start = run.start
        run_end = run.end + 4
        raw_hits = raw_u32_refs_to_range(
            data=data,
            sections=sections,
            start=run_start,
            end=run_end,
            source_names=source_names,
            function_candidates=function_candidates,
            ignore_ref_ranges=[(run_start, run_end)],
            sample_limit=64,
            scan_step=1,
        )
        l32r_hits = l32r_refs_to_range(l32r_refs, run_start, run_end, sample_limit=64)
        raw_hits.sort(
            key=lambda hit: (
                not bool(hit.get("slot_aligned")),
                int(hit["ref_addr"]),
                int(hit["value"]),
            )
        )
        l32r_hits.sort(
            key=lambda hit: (
                not (bool(hit.get("literal_slot_aligned")) or bool(hit.get("value_slot_aligned"))),
                int(hit["ref_addr"]),
            )
        )
        owner_counts = pointer_run_owner_counts(run)
        only_owner = owner_counts[0]["owner_entry"] if len(owner_counts) == 1 else None
        raw_slot_hits = [hit for hit in raw_hits if bool(hit.get("slot_aligned"))]
        l32r_slot_hits = [
            hit
            for hit in l32r_hits
            if bool(hit.get("literal_slot_aligned")) or bool(hit.get("value_slot_aligned"))
        ]
        table_base_value_hits = [
            hit for hit in raw_hits if int(hit["value"]) == run_start
        ] + [
            hit
            for hit in l32r_hits
            if hit.get("literal_value") is not None and (int(hit["literal_value"]) & ~1) == run_start
        ]
        if run.start == 0x70000B80 and run.count == 31 and l32r_slot_hits and not table_base_value_hits:
            q2_status = (
                "ann_code_pointer_run_has_direct_slot_refs_no_indexed_base; L32R "
                "refs load individual 0x70000b80 slots, but no table-base value "
                "ref proving indexed opcode dispatch was found."
            )
            assessment = (
                "The 31-entry ANN pointer run targets the 0x70081d50 owner cluster. "
                "Current refs show direct L32R loads from selected slots, which "
                "makes those code pointers reachable, but not an opcode-indexed "
                "dispatch table base."
            )
            next_action = (
                "Use the referenced slots as concrete ANN owner leads. For the "
                "wrapper opcode map, keep prioritizing 0x700301d8/0x70030a0c "
                "parser evidence or runtime tracing that observes a live table "
                "base/index."
            )
        elif raw_slot_hits or l32r_slot_hits:
            q2_status = (
                "pointer_run_has_direct_slot_refs; validate source owners and "
                "local instructions before assigning index semantics."
            )
            assessment = (
                "This pointer run has static refs to one or more 4-byte-aligned "
                "slot addresses. That makes it a concrete code-table reachability "
                "lead, while direct slot loads still differ from table-base indexed "
                "dispatch."
            )
            next_action = (
                "Inspect the ref owners and nearby loads/comparisons to determine "
                "whether the values are table bases, terminal slots, or bounds."
            )
        elif raw_hits or l32r_hits:
            q2_status = (
                "pointer_run_has_only_unaligned_raw_refs; treat as weak until local "
                "instruction validation confirms a real table reference."
            )
            assessment = (
                "The broad byte-wise scan found in-range values, but none land on "
                "4-byte-aligned table slots. These are weaker than direct slot refs."
            )
            next_action = (
                "Deprioritize unless a decoder or disassembly check shows the "
                "unaligned values are intentional literal data."
            )
        else:
            q2_status = (
                "pointer_run_without_static_refs; structural code-pointer cluster "
                "only in this static scan."
            )
            assessment = (
                "This pointer run targets code, but the current static scan found "
                "no raw-u32 or L32R ref to its table range."
            )
            next_action = (
                "Keep it as low-priority structure unless a runtime trace or "
                "additional decoder pass observes a table-base load."
            )
        if only_owner is not None and "0x" not in assessment:
            assessment += f" All entries resolve to owner {hx(int(only_owner))}."
        investigations.append(
            PointerRunInvestigation(
                start=run.start,
                end=run_end,
                count=run.count,
                target_owner_counts=owner_counts,
                raw_slot_ref_count=len(raw_slot_hits),
                raw_unaligned_ref_count=len(raw_hits) - len(raw_slot_hits),
                l32r_slot_ref_count=len(l32r_slot_hits),
                table_base_value_ref_count=len(table_base_value_hits),
                raw_ref_hits=raw_hits,
                l32r_ref_hits=l32r_hits,
                assessment=assessment,
                q2_status=q2_status,
                next_action=next_action,
            )
        )
    return investigations


def build_ann_op_table_investigation(
    data: bytes,
    sections: list[Section],
    strings_by_addr: dict[int, str],
    l32r_refs: list[L32RReference],
) -> AnnOpTableInvestigation | None:
    table = section_by_name(sections, ".dram_op.data")
    if table is None:
        return None
    blob = section_bytes(data, table)
    nonzero = 0
    all_rodata_strings = True
    for off in range(0, len(blob) - 3, 4):
        value = struct.unpack_from("<I", blob, off)[0]
        if value == 0:
            break
        nonzero += 1
        if (ro_string_at(strings_by_addr, value) or cstring_at_va(data, sections, value, {".rodata"})) is None:
            all_rodata_strings = False
    zero_tail_bytes = max(0, table.size - nonzero * 4)
    raw_hits = raw_u32_refs_to_range(
        data=data,
        sections=sections,
        start=table.addr,
        end=table.addr + table.size,
        source_names={".text", ".rodata", ".data", ".dram0.data"},
    )
    l32r_hits: list[dict[str, object]] = []
    for ref in l32r_refs:
        literal_hit = table.addr <= ref.literal_addr < table.addr + table.size
        value_hit = (
            ref.literal_value is not None
            and table.addr <= (ref.literal_value & ~1) < table.addr + table.size
        )
        if literal_hit or value_hit:
            l32r_hits.append(
                {
                    "ref_addr": ref.addr,
                    "owner_entry": ref.owner_entry,
                    "owner_delta": ref.owner_delta,
                    "target_reg": ref.target_reg,
                    "literal_addr": ref.literal_addr,
                    "literal_value": ref.literal_value,
                    "prop_addr": ref.prop_addr,
                    "prop_size": ref.prop_size,
                    "prop_flags": ref.prop_flags,
                }
            )
    if nonzero == 63 and all_rodata_strings and not raw_hits and not l32r_hits:
        q2_status = (
            "ann_op_name_table_is_vocabulary_not_dispatch_proof; no reproducible "
            ".text/.rodata/.data/.dram0.data raw u32 refs or L32R refs to "
            ".dram_op.data were found."
        )
        assessment = (
            "The .dram_op.data section is a 63-entry table of rodata operation "
            "name pointers followed by zero tail bytes. Current static evidence "
            "does not show it being indexed as the host opcode dispatch table."
        )
    else:
        q2_status = (
            "ann_op_name_table_has_references_or_nonstandard_shape; inspect "
            "raw_ref_hits and l32r_ref_hits before using it as dispatch evidence."
        )
        assessment = (
            "The .dram_op.data section needs manual review before classifying it "
            "as vocabulary-only or dispatch-related."
        )
    return AnnOpTableInvestigation(
        table_start=table.addr,
        table_end=table.addr + table.size,
        table_size=table.size,
        nonzero_entry_count=nonzero,
        zero_tail_bytes=zero_tail_bytes,
        all_entries_are_rodata_strings=all_rodata_strings,
        raw_ref_hits=raw_hits,
        l32r_ref_hits=l32r_hits,
        assessment=assessment,
        q2_status=q2_status,
        next_action=(
            "Keep Q2 focused on the 0x700301d8/0x70030a0c command parser and "
            "nearby pointer/code tables; do not treat ANN op-name indices as "
            "the wrapper 10001..10009 dispatch mapping without a code reference."
        ),
    )


def prop_near(props: list[XtProp], addr: int, window: int = 0x20) -> list[XtProp]:
    return [
        prop
        for prop in props
        if prop.addr <= addr < prop.addr + max(prop.size, 1) or abs(prop.addr - addr) <= window
    ]


def to_jsonable(obj: object) -> object:
    if isinstance(obj, list):
        return [to_jsonable(item) for item in obj]
    if isinstance(obj, dict):
        return {key: to_jsonable(value) for key, value in obj.items()}
    if hasattr(obj, "__dataclass_fields__"):
        raw = asdict(obj)
        return {key: to_jsonable(value) for key, value in raw.items()}
    return obj


def emit_markdown(payload: dict[str, object], path: Path) -> None:
    lines: list[str] = []
    eh = payload["elf_header"]
    assert isinstance(eh, dict)
    lines.append("# APUNN Xtensa ELF summary")
    lines.append("")
    lines.append("## ELF")
    lines.append("")
    lines.append(f"- entry: `{hx(eh['entry'])}`")
    lines.append(f"- machine: `{hx(eh['machine'])}`")
    lines.append(f"- flags: `{hx(eh['flags'])}`")
    lines.append("")
    xtensa_info = payload.get("xtensa_info")
    if isinstance(xtensa_info, dict):
        fields = xtensa_info.get("fields")
        if isinstance(fields, dict) and fields:
            lines.append("## Xtensa Core Info")
            lines.append("")
            for key in (
                "CORE_NAME",
                "HW_VERSION",
                "RELEASE_NAME",
                "RELEASE_VERSION",
                "ABI",
                "USE_ABSOLUTE_LITERALS",
                "SW_FLOATING_POINT_ABI",
            ):
                if key in fields:
                    lines.append(f"- `{key}`: `{display_text(str(fields[key]))}`")
            lines.append("")
    lines.append("## Sections")
    lines.append("")
    lines.append("| name | addr | file offset | size | flags |")
    lines.append("|---|---:|---:|---:|---:|")
    for section in payload["sections"]:
        assert isinstance(section, dict)
        lines.append(
            f"| `{section['name']}` | `{hx(section['addr'])}` | "
            f"`{hx(section['offset'])}` | `{hx(section['size'])}` | "
            f"`{hx(section['flags'])}` |"
        )
    lines.append("")
    lines.append("## Xtensa Property Counts")
    lines.append("")
    for name, count in payload["property_counts"].items():
        lines.append(f"- `{name}`: {count}")
    lines.append("")
    flix_rule = payload.get("flix_length_rule_validation")
    if isinstance(flix_rule, dict):
        lines.append("## FLIX Length Rule Validation")
        lines.append("")
        lines.append(f"- rule: `{display_text(str(flix_rule['rule']))}`")
        lines.append(
            f"- adopted match: {flix_rule['matched_insn_runs']}/"
            f"{flix_rule['total_insn_runs']} "
            f"({float(flix_rule['match_percent']):.2f}%)"
        )
        lines.append(f"- min `0xe` run size: `{hx(flix_rule.get('min_e_run_size'))}`")
        lines.append(f"- min `0xf` run size: `{hx(flix_rule.get('min_f_run_size'))}`")
        lines.append(f"- assessment: {display_text(str(flix_rule['assessment']))}")
        lines.append(f"- next action: {display_text(str(flix_rule['next_action']))}")
        lines.append("")
        lines.append("| candidate | 0xe len | 0xf len | matched runs | match | first failures |")
        lines.append("|---|---:|---:|---:|---:|---|")
        candidate_results = flix_rule["candidate_results"]
        assert isinstance(candidate_results, list)
        for item in candidate_results:
            assert isinstance(item, dict)
            failures: list[str] = []
            failed_samples = item.get("failed_samples", [])
            assert isinstance(failed_samples, list)
            for sample in failed_samples[:3]:
                assert isinstance(sample, dict)
                failures.append(
                    f"{hx(sample.get('prop_addr'))}+{hx(sample.get('prop_size'))} "
                    f"fail={hx(sample.get('fail_addr'))} b0={hx(sample.get('first_byte'))}"
                )
            lines.append(
                f"| `{item['name']}` | {item['e_len']} | {item['f_len']} | "
                f"{item['matched_runs']}/{item['total_runs']} | "
                f"{float(item['match_percent']):.2f}% | "
                f"{display_text('; '.join(failures) if failures else 'none')} |"
            )
        lines.append("")
        nibble_counts = flix_rule.get("first_nibble_counts", {})
        if isinstance(nibble_counts, dict):
            lines.append("| op0 nibble | decoded items |")
            lines.append("|---:|---:|")
            for index in range(16):
                key = f"0x{index:x}"
                lines.append(f"| `{key}` | {nibble_counts.get(key, 0)} |")
            lines.append("")
    lines.append("## Entry Property Neighborhood")
    lines.append("")
    lines.append("| addr | size | flags |")
    lines.append("|---:|---:|---|")
    for prop in payload["entry_properties"]:
        assert isinstance(prop, dict)
        lines.append(f"| `{hx(prop['addr'])}` | `{hx(prop['size'])}` | `{prop['flags_text']}` |")
    lines.append("")
    lines.append("## Function Entry Candidates")
    lines.append("")
    lines.append(
        f"- `.xt.prop` constrained `entry` prologues: {payload['function_candidate_count']}"
    )
    lines.append("")
    lines.append("### First Candidates")
    lines.append("")
    lines.append("| addr | prop size | next entry delta | entry bytes | flags |")
    lines.append("|---:|---:|---:|---|---|")
    function_candidates = payload["function_candidates"]
    assert isinstance(function_candidates, list)
    for fn in function_candidates[:32]:
        assert isinstance(fn, dict)
        lines.append(
            f"| `{hx(fn['addr'])}` | `{hx(fn['prop_size'])}` | "
            f"`{hx(fn.get('next_entry_delta'))}` | `{fn['entry_bytes']}` | "
            f"`{fn['prop_flags']}` |"
        )
    lines.append("")
    lines.append("### Largest Next-Entry Gaps")
    lines.append("")
    lines.append("| addr | next entry delta | prop size | entry bytes | flags |")
    lines.append("|---:|---:|---:|---|---|")
    by_gap = sorted(
        (
            fn
            for fn in function_candidates
            if isinstance(fn, dict) and fn.get("next_entry_delta") is not None
        ),
        key=lambda fn: int(fn["next_entry_delta"]),
        reverse=True,
    )
    for fn in by_gap[:20]:
        assert isinstance(fn, dict)
        lines.append(
            f"| `{hx(fn['addr'])}` | `{hx(fn['next_entry_delta'])}` | "
            f"`{hx(fn['prop_size'])}` | `{fn['entry_bytes']}` | "
            f"`{fn['prop_flags']}` |"
        )
    lines.append("")
    lines.append("## Key Address Owners")
    lines.append("")
    lines.append("| label | addr | section | owner entry | owner delta | prop |")
    lines.append("|---|---:|---|---:|---:|---|")
    for item in payload["key_addresses"]:
        assert isinstance(item, dict)
        lines.append(
            f"| `{item['label']}` | `{hx(item['addr'])}` | "
            f"`{item.get('section') or ''}` | `{hx(item.get('owner_entry'))}` | "
            f"`{hx(item.get('owner_delta'))}` | "
            f"`{item.get('prop_flags') or ''}:{hx(item.get('prop_size'))}` |"
        )
    lines.append("")
    lines.append("## Verified Standard Xtensa Islands")
    lines.append("")
    lines.append(
        "These are narrow byte-verified standard-instruction islands inside "
        "extension-heavy ranges; they are not complete function decompilations."
    )
    lines.append("")
    standard_islands = payload["standard_islands"]
    assert isinstance(standard_islands, list)
    for island in standard_islands:
        assert isinstance(island, dict)
        lines.append(
            f"### `{island['label']}` `{hx(island['start'])}`-`{hx(island['end'])}` "
            f"verified={island['verified']}"
        )
        lines.append("")
        lines.append(display_text(str(island["note"])))
        lines.append("")
        lines.append("| addr | bytes | mnemonic | effect |")
        lines.append("|---:|---|---|---|")
        instructions = island["instructions"]
        assert isinstance(instructions, list)
        for insn in instructions:
            assert isinstance(insn, dict)
            prefix = "" if insn["verified"] else "MISMATCH "
            lines.append(
                f"| `{hx(insn['addr'])}` | `{prefix}{insn.get('actual_bytes')}` | "
                f"`{display_text(str(insn['mnemonic']))}` | "
                f"{display_text(str(insn['effect']))} |"
            )
        lines.append("")
    lines.append("## Standard Field-Access Clusters")
    lines.append("")
    lines.append(
        "These clusters are produced by a lightweight standard Xtensa 24-bit "
        "load/store decoder (`l8ui/l16ui/l32i/s8i/s16i/s32i`) and grouped by "
        "function owner plus base register. They are leads for record-shape "
        "analysis, not decompiler output."
    )
    lines.append("")
    lines.append("| owner | base | hits | unique offsets | VPU-buffer-shaped offsets | samples |")
    lines.append("|---:|---:|---:|---:|---|---|")
    for cluster in payload["standard_field_access_clusters"]:
        assert isinstance(cluster, dict)
        samples: list[str] = []
        sample_hits = cluster["sample_hits"]
        assert isinstance(sample_hits, list)
        for hit in sample_hits[:8]:
            assert isinstance(hit, dict)
            samples.append(
                f"{hx(hit['addr'])}:{hit['op']} a{hit['value_reg']},"
                f"a{hit['base_reg']}+{hx(hit['offset'])}"
            )
        offsets = ", ".join(hx(value) or "" for value in cluster["vpu_buffer_offsets"])
        lines.append(
            f"| `{hx(cluster['owner_entry'])}` | `a{cluster['base_reg']}` | "
            f"{cluster['hit_count']} | {len(cluster['unique_offsets'])} | "
            f"{offsets} | {display_text('; '.join(samples))} |"
        )
    lines.append("")
    lines.append("## Loop Targets Near Field Clusters")
    lines.append("")
    lines.append(
        f"- `.xt.prop` loop-target properties in `.text`: {payload['loop_target_count']}"
    )
    lines.append(
        "- table is limited to loop targets whose owner also has a VPU-buffer-shaped "
        "field-access cluster."
    )
    lines.append("")
    lines.append("| addr | owner | owner delta | field bases | prop |")
    lines.append("|---:|---:|---:|---|---|")
    for item in payload["loop_target_candidates"]:
        assert isinstance(item, dict)
        bases = ", ".join(f"a{value}" for value in item["matched_field_bases"])
        lines.append(
            f"| `{hx(item['addr'])}` | `{hx(item.get('owner_entry'))}` | "
            f"`{hx(item.get('owner_delta'))}` | `{bases}` | "
            f"`{item['prop_flags']}:{hx(item['prop_size'])}` |"
        )
    lines.append("")
    lines.append("### Focused Loop Investigations")
    lines.append("")
    lines.append(
        "`0x7003c102` remains the strongest record-shaped loop-target lead. "
        "After FLIX correction, the target body exposes stack-spill core ops and "
        "FLIX bundles; the INFO12 count is closed at the ABI/kernel/provider "
        "boundary, while the INFO13 stride is closed by the 0x40 descriptor "
        "layout plus `0x7003b468/a2` field coverage. Naming a firmware-local "
        "bundle-interior count register still requires FLIX/TIE slot semantics. "
        "`0x7003d423` is kept as a downgraded local-control-flow lead."
    )
    lines.append("")
    for item in payload["focused_loop_investigations"]:
        assert isinstance(item, dict)
        lines.append(
            f"#### `{item['label']}` `{hx(item['owner_entry'])}` target `{hx(item['loop_target'])}`"
        )
        lines.append("")
        lines.append(f"- priority: `{item['priority']}`")
        lines.append(f"- assessment: {display_text(str(item['assessment']))}")
        lines.append(
            f"- target prop: `{item.get('target_prop_flags') or ''}:"
            f"{hx(item.get('target_prop_size'))}`"
        )
        lines.append(
            f"- FLIX framing motif `{item['flix_framing']}` hits: "
            f"{', '.join(hx(value) or '' for value in item['flix_framing_hits'][:16])}"
        )
        lines.append(
            f"- FLIX framing motif deltas: "
            f"{', '.join(hx(value) or '' for value in item['flix_framing_deltas'][:16])}"
        )
        lines.append(
            f"- standard loop opcode hits to target: "
            f"{', '.join(hx(value) or '' for value in item['standard_loop_opcode_hits']) or 'none'}"
        )
        boundary_counts = item["boundary_counts"]
        assert isinstance(boundary_counts, dict)
        lines.append(
            "- owner boundary counts: "
            f"core24={boundary_counts.get('core24', 0)}, "
            f"dens16={boundary_counts.get('dens16', 0)}, "
            f"flix64={boundary_counts.get('flix64', 0)}, "
            f"flix128={boundary_counts.get('flix128', 0)}, "
            f"truncated={boundary_counts.get('truncated', 0)}"
        )
        loop_body_counts = item["loop_body_boundary_counts"]
        assert isinstance(loop_body_counts, dict)
        lines.append(
            f"- loop body: `{hx(item['loop_body_start'])}`..`{hx(item['loop_body_end'])}`; "
            f"core24={loop_body_counts.get('core24', 0)}, "
            f"dens16={loop_body_counts.get('dens16', 0)}, "
            f"flix64={loop_body_counts.get('flix64', 0)}, "
            f"flix128={loop_body_counts.get('flix128', 0)}, "
            f"truncated={loop_body_counts.get('truncated', 0)}"
        )
        lines.append(f"- count status: {display_text(str(item['count_status']))}")
        lines.append(f"- stride status: {display_text(str(item['stride_0x40_status']))}")
        lines.append(f"- next action: {display_text(str(item['next_action']))}")
        lines.append("")
        lines.append("| visible a2 field access | op | offset |")
        lines.append("|---:|---|---:|")
        accesses = item["visible_field_accesses"]
        assert isinstance(accesses, list)
        for hit in accesses[:24]:
            assert isinstance(hit, dict)
            lines.append(
                f"| `{hx(hit['addr'])}` | `{hit['op']} a{hit['value_reg']},"
                f"a{hit['base_reg']}+{hx(hit['offset'])}` | `{hx(hit['offset'])}` |"
            )
        lines.append("")
        lines.append("| loop-body boundary core mem | op | offset |")
        lines.append("|---:|---|---:|")
        loop_body_accesses = item["loop_body_core_mem_accesses"]
        assert isinstance(loop_body_accesses, list)
        for hit in loop_body_accesses[:24]:
            assert isinstance(hit, dict)
            lines.append(
                f"| `{hx(hit['addr'])}` | `{hit['op']} a{hit['value_reg']},"
                f"a{hit['base_reg']}+{hx(hit['offset'])}` | `{hx(hit['offset'])}` |"
            )
        lines.append("")
        lines.append("| prop | size | flags | first bytes |")
        lines.append("|---:|---:|---|---|")
        prop_runs = item["prop_runs"]
        assert isinstance(prop_runs, list)
        for prop in prop_runs[:24]:
            assert isinstance(prop, dict)
            lines.append(
                f"| `{hx(prop['addr'])}` | `{hx(prop['size'])}` | "
                f"`{prop['flags']}` | `{prop['bytes']}` |"
            )
        lines.append("")
    lines.append("### FLIX-Correct Boundary Sweeps")
    lines.append("")
    lines.append(
        "These ranges use the `.xt.prop`-validated hybrid length rule: "
        "`0x0..0x7 -> 3`, `0x8..0xd -> 2`, `0xe -> 16`, `0xf -> 8`. "
        "The `06 04 02` motif is treated as FLIX128 framing, not as an "
        "independent selector."
    )
    lines.append("")
    for item in payload["flix_sweeps"]:
        assert isinstance(item, dict)
        counts = item["counts"]
        assert isinstance(counts, dict)
        lines.append(
            f"#### `{item['label']}` `{hx(item['start'])}`..`{hx(item['end'])}`"
        )
        lines.append("")
        lines.append(display_text(str(item["description"])))
        lines.append("")
        lines.append(
            f"- start prop: `{item.get('start_prop_flags') or ''}:"
            f"{hx(item.get('start_prop_size'))}` at `{hx(item.get('start_prop_addr'))}`"
        )
        lines.append(
            "- counts: "
            f"core24={counts.get('core24', 0)}, "
            f"dens16={counts.get('dens16', 0)}, "
            f"flix64={counts.get('flix64', 0)}, "
            f"flix128={counts.get('flix128', 0)}, "
            f"truncated={counts.get('truncated', 0)}"
        )
        lines.append(f"- bad framing: {item['bad_framing_count']}")
        lines.append(f"- next action: {display_text(str(item['next_action']))}")
        lines.append("")
        lines.append("| addr | len | kind | raw | fmt | framing | decoded core mem |")
        lines.append("|---:|---:|---|---|---|---|---|")
        instructions = item["instructions"]
        assert isinstance(instructions, list)
        for insn in instructions[:32]:
            assert isinstance(insn, dict)
            framing = ""
            if insn.get("framing_ok") is not None:
                framing = "ok" if insn["framing_ok"] else ",".join(insn["framing_warn"])
            mem_text = ""
            mem = insn.get("core_mem_access")
            if isinstance(mem, dict):
                mem_text = (
                    f"{mem['op']} a{mem['value_reg']},"
                    f"a{mem['base_reg']}+{hx(mem['offset'])}"
                )
            lines.append(
                f"| `{hx(insn['addr'])}` | {insn['length']} | `{insn['kind']}` | "
                f"`{insn['raw']}` | `{insn.get('fmt') or ''}` | `{framing}` | "
                f"`{mem_text}` |"
            )
        lines.append("")
    lines.append("## L32R Literal References")
    lines.append("")
    lines.append(
        "These references are decoded only inside `.xt.prop` instruction-covered "
        "`.text` ranges. `literal` is the PC-relative L32R literal address; "
        "`value` is the 32-bit word stored there when it is readable."
    )
    lines.append(
        "Because a property range can contain extension bundles, these are "
        "section-filtered leads; high-value owners still need local byte or IDA "
        "validation before treating them as control-flow facts."
    )
    lines.append("")
    lines.append(f"- valid L32R refs with in-ELF literal address: {payload['l32r_ref_count']}")
    lines.append(
        f"- interesting L32R refs emitted: {len(payload['interesting_l32r_refs'])}"
    )
    lines.append("")
    lines.append("| addr | reg | literal | value | owner | literal string | value string |")
    lines.append("|---:|---:|---:|---:|---:|---|---|")
    for ref in payload["interesting_l32r_refs"][:80]:
        assert isinstance(ref, dict)
        literal_string = ""
        if ref.get("literal_string_value"):
            suffix = "" if int(ref["literal_string_offset"]) == 0 else f"+0x{int(ref['literal_string_offset']):x}"
            literal_string = (
                f"`{hx(ref['literal_string_addr'])}{suffix}` "
                f"`{display_text(str(ref['literal_string_value']))}`"
            )
        value_string = ""
        if ref.get("value_string_value"):
            suffix = "" if int(ref["value_string_offset"]) == 0 else f"+0x{int(ref['value_string_offset']):x}"
            value_string = (
                f"`{hx(ref['value_string_addr'])}{suffix}` "
                f"`{display_text(str(ref['value_string_value']))}`"
            )
        lines.append(
            f"| `{hx(ref['addr'])}` | `a{ref['target_reg']}` | "
            f"`{hx(ref['literal_addr'])}` `{ref.get('literal_section') or ''}` | "
            f"`{hx(ref.get('literal_value'))}` `{ref.get('value_section') or ''}` | "
            f"`{hx(ref.get('owner_entry'))}` | {literal_string} | {value_string} |"
        )
    lines.append("")
    lines.append("### Critical String L32R References")
    lines.append("")
    lines.append("| pattern | string | hits | samples |")
    lines.append("|---|---:|---:|---|")
    for item in payload["critical_l32r_refs"]:
        assert isinstance(item, dict)
        samples: list[str] = []
        hits = item["hits"]
        assert isinstance(hits, list)
        for hit in hits[:4]:
            assert isinstance(hit, dict)
            literal_suffix = ""
            if hit.get("literal_string_offset") is not None:
                literal_suffix = "+0x%x" % int(hit["literal_string_offset"])
            samples.append(
                f"{hx(hit['addr'])}:a{hit['target_reg']} "
                f"lit={hx(hit['literal_addr'])}{literal_suffix} "
                f"owner={hx(hit.get('owner_entry'))}"
            )
        lines.append(
            f"| `{display_text(str(item['pattern']))}` | `{hx(item.get('string_addr'))}` | "
            f"{item['hit_count']} | {display_text('; '.join(samples))} |"
        )
    lines.append("")
    lines.append("### Critical L32R Owner Clusters")
    lines.append("")
    lines.append(
        "Owners are ranked by how many distinct DMA/iDMA-related strings are "
        "referenced through L32R. This is a string-cluster lead, not full control "
        "flow."
    )
    lines.append("")
    lines.append("| owner | patterns | hits | assessment | samples |")
    lines.append("|---:|---:|---:|---|---|")
    for item in payload["critical_l32r_owner_clusters"]:
        assert isinstance(item, dict)
        samples: list[str] = []
        sample_refs = item["sample_refs"]
        assert isinstance(sample_refs, list)
        for ref in sample_refs[:6]:
            assert isinstance(ref, dict)
            samples.append(f"{ref['pattern']}@{hx(ref['ref_addr'])}")
        lines.append(
            f"| `{hx(item['owner_entry'])}` | {item['pattern_count']} | "
            f"{item['hit_count']} | {display_text(str(item['assessment']))} | "
            f"{display_text('; '.join(samples))} |"
        )
    lines.append("")
    lines.append("### DMA/iDMA Owner Investigations")
    lines.append("")
    lines.append(
        "These records promote the string-cluster leads into explicit Q1 owner "
        "investigations. They identify schedule/wait ownership, not completion "
        "write timing."
    )
    lines.append("")
    for item in payload["dma_owner_investigations"]:
        assert isinstance(item, dict)
        lines.append(f"#### `{item['label']}` `{hx(item['owner_entry'])}`")
        lines.append("")
        lines.append(f"- range: `{hx(item['analysis_start'])}`..`{hx(item['analysis_end'])}`")
        lines.append(
            f"- string-cluster rank: {item['cluster_rank']}; "
            f"patterns={item['cluster_pattern_count']}; hits={item['cluster_hit_count']}"
        )
        lines.append(
            f"- string-cluster patterns: "
            f"{display_text(', '.join(str(value) for value in item['cluster_patterns']))}"
        )
        lines.append(f"- assessment: {display_text(str(item['assessment']))}")
        lines.append(f"- Q1 status: {display_text(str(item['q1_status']))}")
        lines.append(
            f"- FLIX framing motif `{item['flix_framing']}` hits: "
            f"{item['flix_framing_hit_count']} "
            f"(first {', '.join(hx(value) or '' for value in item['flix_framing_hits'][:12])})"
        )
        lines.append(
            f"- standard a2 access signals: {item['standard_a2_access_count']} hits, "
            f"offsets {', '.join(hx(value) or '' for value in item['standard_a2_offsets'][:32])}"
        )
        lines.append(f"- next action: {display_text(str(item['next_action']))}")
        lines.append("")
        lines.append("| evidence | ref | delta | literal | prop |")
        lines.append("|---|---:|---:|---:|---|")
        evidence_refs = item["evidence_refs"]
        assert isinstance(evidence_refs, list)
        for ref in evidence_refs:
            assert isinstance(ref, dict)
            lines.append(
                f"| {display_text(str(ref['pattern']))} | `{hx(ref['ref_addr'])}` | "
                f"`{hx(ref.get('owner_delta'))}` | "
                f"`a{ref['target_reg']} -> {hx(ref['literal_addr'])}` | "
                f"`{ref.get('prop_flags') or ''}:{hx(ref.get('prop_size'))}` |"
            )
        lines.append("")
        lines.append("| prop | size | flags | first bytes |")
        lines.append("|---:|---:|---|---|")
        prop_runs = item["prop_runs"]
        assert isinstance(prop_runs, list)
        for prop in prop_runs[:24]:
            assert isinstance(prop, dict)
            lines.append(
                f"| `{hx(prop['addr'])}` | `{hx(prop['size'])}` | "
                f"`{prop['flags']}` | `{prop['bytes']}` |"
            )
        lines.append("")
    lines.append("### Output Validation Investigations")
    lines.append("")
    lines.append(
        "These records map static output-shape validation owners from rodata32 "
        "and L32R-backed output diagnostics. They do not identify the runtime "
        "`settings+0x08` output-fill loop."
    )
    lines.append("")
    for item in payload["output_validation_investigations"]:
        assert isinstance(item, dict)
        lines.append(f"#### `{item['label']}` `{hx(item['owner_entry'])}`")
        lines.append("")
        lines.append(f"- range: `{hx(item['analysis_start'])}`..`{hx(item['analysis_end'])}`")
        lines.append(
            f"- referenced patterns: "
            f"{display_text(', '.join(str(value) for value in item['referenced_patterns']))}"
        )
        lines.append(f"- assessment: {display_text(str(item['assessment']))}")
        lines.append(f"- Q4 status: {display_text(str(item['q4_status']))}")
        counts = item["boundary_counts"]
        assert isinstance(counts, dict)
        lines.append(
            "- boundary counts: "
            f"core24={counts.get('core24', 0)}, "
            f"dens16={counts.get('dens16', 0)}, "
            f"flix64={counts.get('flix64', 0)}, "
            f"flix128={counts.get('flix128', 0)}, "
            f"truncated={counts.get('truncated', 0)}"
        )
        lines.append(f"- next action: {display_text(str(item['next_action']))}")
        lines.append("")
        lines.append("| kind | pattern | ref | delta | string/literal | prop |")
        lines.append("|---|---|---:|---:|---|---|")
        evidence_refs = item["evidence_refs"]
        assert isinstance(evidence_refs, list)
        for ref in evidence_refs:
            assert isinstance(ref, dict)
            if ref["kind"] == "rodata32":
                string_suffix = (
                    ""
                    if int(ref["string_offset"]) == 0
                    else f"+0x{int(ref['string_offset']):x}"
                )
                target = (
                    f"`{hx(ref['string_addr'])}{string_suffix}` "
                    f"`{display_text(str(ref['string_value']))}`"
                )
            else:
                parts = [f"`a{ref['target_reg']} -> {hx(ref['literal_addr'])}`"]
                if ref.get("literal_string_value"):
                    parts.append(f"`{display_text(str(ref['literal_string_value']))}`")
                if ref.get("value_string_value"):
                    parts.append(f"`{display_text(str(ref['value_string_value']))}`")
                target = " ".join(parts)
            lines.append(
                f"| `{ref['kind']}` | {display_text(str(ref['pattern']))} | "
                f"`{hx(ref['ref_addr'])}` | `{hx(ref.get('owner_delta'))}` | "
                f"{target} | `{ref.get('prop_flags') or ''}:{hx(ref.get('prop_size'))}` |"
            )
        lines.append("")
        lines.append("| prop | size | flags | first bytes |")
        lines.append("|---:|---:|---|---|")
        prop_runs = item["prop_runs"]
        assert isinstance(prop_runs, list)
        for prop in prop_runs[:12]:
            assert isinstance(prop, dict)
            lines.append(
                f"| `{hx(prop['addr'])}` | `{hx(prop['size'])}` | "
                f"`{prop['flags']}` | `{prop['bytes']}` |"
            )
        lines.append("")
    lines.append("### `.dram_op.data` L32R References")
    lines.append("")
    lines.append("| addr | reg | literal | value | owner |")
    lines.append("|---:|---:|---:|---:|---:|")
    for ref in payload["dram_op_l32r_refs"]:
        assert isinstance(ref, dict)
        lines.append(
            f"| `{hx(ref['addr'])}` | `a{ref['target_reg']}` | "
            f"`{hx(ref['literal_addr'])}` `{ref.get('literal_section') or ''}` | "
            f"`{hx(ref.get('literal_value'))}` `{ref.get('value_section') or ''}` | "
            f"`{hx(ref.get('owner_entry'))}` |"
        )
    lines.append("")
    lines.append("## Rodata String References")
    lines.append("")
    lines.append(f"- `.text` 32-bit references into `.rodata` strings: {payload['rodata_ref_count']}")
    lines.append(
        f"- interesting refs: {len(payload['interesting_rodata_refs'])} "
        "(table below is capped at 80)"
    )
    lines.append("")
    lines.append("| ref | value | owner | string |")
    lines.append("|---:|---:|---:|---|")
    interesting_refs = payload["interesting_rodata_refs"]
    assert isinstance(interesting_refs, list)
    for ref in interesting_refs[:80]:
        assert isinstance(ref, dict)
        offset = int(ref["string_offset"])
        suffix = "" if offset == 0 else f"+0x{offset:x}"
        lines.append(
            f"| `{hx(ref['ref_addr'])}` | `{hx(ref['value'])}` | "
            f"`{hx(ref.get('owner_entry'))}` | "
            f"`{hx(ref['string_addr'])}{suffix}` `{display_text(str(ref['string_value']))}` |"
        )
    lines.append("")
    lines.append("## Critical String Direct References")
    lines.append("")
    lines.append(
        "- all-byte refs include unaligned matches and are false-positive prone; "
        "absence is useful, presence needs disassembly validation."
    )
    lines.append("")
    lines.append("| pattern | string | aligned refs | all-byte refs | sample refs |")
    lines.append("|---|---:|---:|---:|---|")
    critical_refs = payload["critical_string_refs"]
    assert isinstance(critical_refs, list)
    for item in critical_refs:
        assert isinstance(item, dict)
        hits = item["aligned_hits"] if item["aligned_hits"] else item["byte_hits"]
        assert isinstance(hits, list)
        samples: list[str] = []
        for hit in hits[:4]:
            assert isinstance(hit, dict)
            owner = hx(hit.get("owner_entry"))
            suffix = "" if int(hit["string_offset"]) == 0 else f"+0x{int(hit['string_offset']):x}"
            samples.append(f"{hx(hit['ref_addr'])}->{owner}{suffix}")
        lines.append(
            f"| `{display_text(str(item['pattern']))}` | `{hx(item.get('string_addr'))}` | "
            f"{item['aligned_hit_count']} | {item['byte_hit_count']} | "
            f"{', '.join(samples)} |"
        )
    lines.append("")
    lines.append("## Pointer Runs")
    lines.append("")
    for run in payload["pointer_runs"]:
        assert isinstance(run, dict)
        lines.append(f"### `{hx(run['start'])}` count {run['count']}")
        lines.append("")
        lines.append("| slot | value | target | owner | prop | string |")
        lines.append("|---:|---:|---|---:|---|---|")
        for entry in run["entries"]:
            assert isinstance(entry, dict)
            lines.append(
                f"| `{hx(entry['addr'])}` | `{hx(entry['value'])}` | "
                f"`{entry['target_section']}` | `{hx(entry.get('owner_entry'))}` | "
                f"`{entry.get('prop_flags') or ''}` | "
                f"{display_text(str(entry.get('target_string') or ''))} |"
            )
        lines.append("")
    pointer_run_investigations = payload.get("pointer_run_investigations")
    if isinstance(pointer_run_investigations, list):
        lines.append("## Pointer Run Reachability")
        lines.append("")
        lines.append(
            "- raw-u32 refs are scanned byte-wise outside the table itself; L32R refs "
            "cover literal-slot and literal-value hits."
        )
        lines.append("")
        lines.append(
            "| run | count | target owners | raw slot/total | L32R slot/total | "
            "table-base refs | sample refs | Q2 status |"
        )
        lines.append("|---:|---:|---|---:|---:|---:|---|---|")
        for item in pointer_run_investigations:
            assert isinstance(item, dict)
            owner_counts = item["target_owner_counts"]
            assert isinstance(owner_counts, list)
            owner_text: list[str] = []
            for owner in owner_counts:
                assert isinstance(owner, dict)
                owner_text.append(
                    f"`{hx(owner.get('owner_entry'))}`:{owner['count']} "
                    f"`{hx(owner['min_target'])}`..`{hx(owner['max_target'])}`"
                )
            samples: list[str] = []
            raw_hits = item["raw_ref_hits"]
            assert isinstance(raw_hits, list)
            for hit in raw_hits[:4]:
                assert isinstance(hit, dict)
                samples.append(
                    "raw "
                    f"`{hx(hit['ref_addr'])}`->`{hx(hit['value'])}` "
                    f"owner=`{hx(hit.get('owner_entry'))}` "
                    f"slot={hit.get('slot_aligned')} align={hit.get('alignment')}"
                )
            l32r_hits = item["l32r_ref_hits"]
            assert isinstance(l32r_hits, list)
            for hit in l32r_hits[:2]:
                assert isinstance(hit, dict)
                samples.append(
                    "l32r "
                    f"`{hx(hit['ref_addr'])}` literal=`{hx(hit.get('literal_addr'))}` "
                    f"value=`{hx(hit.get('literal_value'))}` "
                    f"owner=`{hx(hit.get('owner_entry'))}`"
                )
            lines.append(
                f"| `{hx(item['start'])}`..`{hx(item['end'])}` | {item['count']} | "
                f"{'<br>'.join(owner_text)} | "
                f"{item.get('raw_slot_ref_count', 0)}/{len(raw_hits)} | "
                f"{item.get('l32r_slot_ref_count', 0)}/{len(l32r_hits)} | "
                f"{item.get('table_base_value_ref_count', 0)} | "
                f"{'<br>'.join(samples) if samples else 'none'} | "
                f"{display_text(str(item['q2_status']))} |"
            )
        lines.append("")
    ann_table = payload.get("ann_op_table_investigation")
    if isinstance(ann_table, dict):
        lines.append("## ANN Op Name Table Reachability")
        lines.append("")
        lines.append(f"- table: `{hx(ann_table['table_start'])}`..`{hx(ann_table['table_end'])}`")
        lines.append(f"- nonzero entries: {ann_table['nonzero_entry_count']}")
        lines.append(f"- zero tail bytes: `{hx(ann_table['zero_tail_bytes'])}`")
        lines.append(
            f"- all nonzero entries are rodata strings: "
            f"{ann_table['all_entries_are_rodata_strings']}"
        )
        lines.append(f"- assessment: {display_text(str(ann_table['assessment']))}")
        lines.append(f"- Q2 status: {display_text(str(ann_table['q2_status']))}")
        lines.append(f"- next action: {display_text(str(ann_table['next_action']))}")
        lines.append("")
        lines.append("| ref type | ref | section/owner | value | delta/prop |")
        lines.append("|---|---:|---|---:|---|")
        raw_hits = ann_table["raw_ref_hits"]
        assert isinstance(raw_hits, list)
        for hit in raw_hits:
            assert isinstance(hit, dict)
            lines.append(
                f"| raw-u32 | `{hx(hit['ref_addr'])}` | `{hit['source_section']}` | "
                f"`{hx(hit['value'])}` | `{hx(hit['delta'])}` |"
            )
        l32r_hits = ann_table["l32r_ref_hits"]
        assert isinstance(l32r_hits, list)
        for hit in l32r_hits:
            assert isinstance(hit, dict)
            lines.append(
                f"| l32r | `{hx(hit['ref_addr'])}` | `{hx(hit.get('owner_entry'))}` | "
                f"`{hx(hit.get('literal_value'))}` | "
                f"`{hit.get('prop_flags') or ''}:{hx(hit.get('prop_size'))}` |"
            )
        if not raw_hits and not l32r_hits:
            lines.append("| none |  |  |  | no raw-u32 or L32R refs to `.dram_op.data` |")
        lines.append("")
    lines.append("## ANN Op Name Table")
    lines.append("")
    lines.append("| index | entry | name | string |")
    lines.append("|---:|---:|---|---:|")
    for entry in payload["ann_op_name_table"]:
        assert isinstance(entry, dict)
        lines.append(
            f"| {entry['index']} | `{hx(entry['entry_addr'])}` | "
            f"`{display_text(str(entry['name']))}` | `{hx(entry['string_addr'])}` |"
        )
    lines.append("")
    lines.append("## Interesting Strings")
    lines.append("")
    for entry in payload["interesting_strings"]:
        assert isinstance(entry, dict)
        lines.append(f"- `{hx(entry['addr'])}` `{display_text(str(entry['value']))}`")
    lines.append("")
    path.write_text("\n".join(lines).rstrip() + "\n")


def build_payload(path: Path, min_string: int, min_run: int) -> dict[str, object]:
    data = path.read_bytes()
    eh = parse_elf_header(data)
    sections = parse_sections(data, eh)
    rodata = section_by_name(sections, ".rodata")
    if rodata is None:
        raise ValueError("ELF has no .rodata section")

    strings = find_strings(data, rodata, min_string)
    strings_by_addr = {entry.addr: entry.value for entry in strings}
    props = parse_xt_props(data, section_by_name(sections, ".xt.prop"))
    xtensa_info = parse_xtensa_info(data, section_by_name(sections, ".xtensa.info"))
    function_candidates = find_function_candidates(data, sections, props)
    prop_counts: dict[str, int] = {
        "total": len(props),
        "insn": sum(1 for prop in props if prop.flags & XT_PROP_INSN),
        "data": sum(1 for prop in props if prop.flags & XT_PROP_DATA),
        "loop_target": sum(1 for prop in props if prop.flags & XT_PROP_LOOP_TARGET),
        "branch_target": sum(1 for prop in props if prop.flags & XT_PROP_BRANCH_TARGET),
        "unreachable": sum(1 for prop in props if prop.flags & XT_PROP_UNREACHABLE),
    }

    pointer_runs = find_pointer_runs(
        data=data,
        sections=sections,
        source=rodata,
        strings_by_addr=strings_by_addr,
        props=props,
        function_candidates=function_candidates,
        min_run=min_run,
    )
    rodata_refs = find_rodata_refs(data, sections, strings, function_candidates)
    critical_string_refs = find_critical_string_refs(
        data, sections, strings, function_candidates
    )
    standard_islands = build_standard_islands(data, sections)
    standard_field_access_clusters = find_standard_field_access_clusters(
        data, sections, props, function_candidates
    )
    flix_length_rule_validation = build_flix_length_rule_validation(data, sections, props)
    l32r_ref_count, interesting_l32r_refs, l32r_refs_for_scans = find_l32r_references(
        data, sections, strings, props, function_candidates, xtensa_info
    )
    pointer_run_investigations = build_pointer_run_investigations(
        data, sections, pointer_runs, function_candidates, l32r_refs_for_scans
    )
    critical_l32r_refs = find_critical_l32r_refs(strings, l32r_refs_for_scans)
    critical_owner_clusters = build_critical_owner_clusters(critical_l32r_refs)
    dma_owner_investigations = build_dma_owner_investigations(
        data, sections, props, l32r_refs_for_scans, critical_owner_clusters
    )
    output_validation_investigations = build_output_validation_investigations(
        data, sections, props, rodata_refs, l32r_refs_for_scans
    )
    ann_op_table_investigation = build_ann_op_table_investigation(
        data, sections, strings_by_addr, l32r_refs_for_scans
    )
    dram_op_l32r_refs = find_dram_op_l32r_refs(sections, l32r_refs_for_scans)
    loop_target_count, loop_target_candidates = find_loop_target_candidates(
        sections, props, function_candidates, standard_field_access_clusters
    )
    focused_loop_investigations = build_focused_loop_investigations(
        data, sections, props
    )
    flix_sweeps = build_flix_sweeps(data, sections, props, function_candidates)

    entry_props = []
    for prop in prop_near(props, eh.entry):
        entry_props.append(
            {
                "addr": prop.addr,
                "size": prop.size,
                "flags": prop.flags,
                "flags_text": flags_text(prop.flags),
            }
        )

    return {
        "input": str(path),
        "elf_header": to_jsonable(eh),
        "sections": to_jsonable(sections),
        "xtensa_info": to_jsonable(xtensa_info) if xtensa_info is not None else None,
        "property_counts": prop_counts,
        "entry_properties": entry_props,
        "function_candidate_count": len(function_candidates),
        "function_candidates": to_jsonable(function_candidates),
        "key_addresses": to_jsonable(
            build_key_addresses(sections, props, function_candidates)
        ),
        "standard_islands": to_jsonable(standard_islands),
        "standard_field_access_clusters": to_jsonable(standard_field_access_clusters),
        "flix_length_rule_validation": to_jsonable(flix_length_rule_validation),
        "rodata_ref_count": len(rodata_refs),
        "rodata_refs": to_jsonable(rodata_refs),
        "interesting_rodata_refs": to_jsonable(
            [ref for ref in rodata_refs if is_interesting_rodata_ref(ref)]
        ),
        "critical_string_refs": to_jsonable(critical_string_refs),
        "l32r_ref_count": l32r_ref_count,
        "interesting_l32r_refs": to_jsonable(interesting_l32r_refs),
        "critical_l32r_refs": to_jsonable(critical_l32r_refs),
        "critical_l32r_owner_clusters": to_jsonable(critical_owner_clusters),
        "pointer_run_investigations": to_jsonable(pointer_run_investigations),
        "dma_owner_investigations": to_jsonable(dma_owner_investigations),
        "output_validation_investigations": to_jsonable(output_validation_investigations),
        "ann_op_table_investigation": to_jsonable(ann_op_table_investigation),
        "dram_op_l32r_refs": to_jsonable(dram_op_l32r_refs),
        "loop_target_count": loop_target_count,
        "loop_target_candidates": to_jsonable(loop_target_candidates),
        "focused_loop_investigations": to_jsonable(focused_loop_investigations),
        "flix_sweeps": to_jsonable(flix_sweeps),
        "pointer_runs": to_jsonable(pointer_runs),
        "ann_op_name_table": op_name_table(
            data, sections, section_by_name(sections, ".dram_op.data"), strings_by_addr
        ),
        "interesting_strings": to_jsonable(interesting_strings(strings)),
    }


def write_json(payload: dict[str, object], path: Path) -> None:
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n")


def print_summary(payload: dict[str, object]) -> None:
    eh = payload["elf_header"]
    assert isinstance(eh, dict)
    print(
        "elf "
        f"entry={hx(eh['entry'])} "
        f"machine={hx(eh['machine'])} flags={hx(eh['flags'])}"
    )
    xtensa_info = payload.get("xtensa_info")
    if isinstance(xtensa_info, dict):
        fields = xtensa_info.get("fields")
        if isinstance(fields, dict) and fields:
            print(
                "xtensa_info "
                f"core={fields.get('CORE_NAME')} "
                f"hw={fields.get('HW_VERSION')} "
                f"release={fields.get('RELEASE_NAME')}/{fields.get('RELEASE_VERSION')} "
                f"use_absolute_literals={fields.get('USE_ABSOLUTE_LITERALS')}"
            )
    for section in payload["sections"]:
        assert isinstance(section, dict)
        print(
            f"section {section['name'] or '<null>':18s} "
            f"addr={hx(section['addr'])} off={hx(section['offset'])} "
            f"size={hx(section['size'])} flags={hx(section['flags'])}"
        )
    print("property_counts", payload["property_counts"])
    flix_rule = payload.get("flix_length_rule_validation")
    if isinstance(flix_rule, dict):
        print(
            "flix_length_rule "
            f"matched={flix_rule['matched_insn_runs']}/"
            f"{flix_rule['total_insn_runs']} "
            f"percent={float(flix_rule['match_percent']):.2f} "
            f"rule={flix_rule['rule']}"
        )
        for item in flix_rule["candidate_results"]:
            assert isinstance(item, dict)
            print(
                "  flix_candidate "
                f"{item['name']} e_len={item['e_len']} f_len={item['f_len']} "
                f"matched={item['matched_runs']}/{item['total_runs']} "
                f"percent={float(item['match_percent']):.2f}"
            )
    print(f"function_candidates={payload['function_candidate_count']}")
    for item in payload["key_addresses"]:
        assert isinstance(item, dict)
        print(
            "  key "
            f"{item['label']} addr={hx(item['addr'])} "
            f"owner={hx(item.get('owner_entry'))}+{hx(item.get('owner_delta'))} "
            f"prop={item.get('prop_flags') or ''}:{hx(item.get('prop_size'))}"
        )
    for island in payload["standard_islands"]:
        assert isinstance(island, dict)
        print(
            "  island "
            f"{island['label']} range={hx(island['start'])}-{hx(island['end'])} "
            f"verified={island['verified']}"
        )
    clusters = payload["standard_field_access_clusters"]
    assert isinstance(clusters, list)
    print(f"standard_field_access_clusters={len(clusters)}")
    for cluster in clusters[:10]:
        assert isinstance(cluster, dict)
        offsets = ",".join(hx(value) or "" for value in cluster["vpu_buffer_offsets"])
        print(
            "  field_cluster "
            f"owner={hx(cluster['owner_entry'])} base=a{cluster['base_reg']} "
            f"hits={cluster['hit_count']} vpu_offsets={offsets}"
        )
    loop_targets = payload["loop_target_candidates"]
    assert isinstance(loop_targets, list)
    print(
        f"loop_targets={payload['loop_target_count']} "
        f"near_field_clusters={len(loop_targets)}"
    )
    for item in loop_targets[:10]:
        assert isinstance(item, dict)
        bases = ",".join(f"a{value}" for value in item["matched_field_bases"])
        print(
            "  loop_target "
            f"addr={hx(item['addr'])} owner={hx(item.get('owner_entry'))}+"
            f"{hx(item.get('owner_delta'))} bases={bases}"
        )
    focused_loops = payload["focused_loop_investigations"]
    assert isinstance(focused_loops, list)
    print(f"focused_loop_investigations={len(focused_loops)}")
    for item in focused_loops:
        assert isinstance(item, dict)
        print(
            "  focused_loop "
            f"{item['label']} priority={item['priority']} "
            f"owner={hx(item['owner_entry'])} target={hx(item['loop_target'])} "
            f"target_prop={item.get('target_prop_flags')}:{hx(item.get('target_prop_size'))} "
            f"flix_framing_hits={len(item['flix_framing_hits'])} "
            f"standard_loop_hits={len(item['standard_loop_opcode_hits'])}"
        )
    flix_sweeps = payload["flix_sweeps"]
    assert isinstance(flix_sweeps, list)
    print(f"flix_sweeps={len(flix_sweeps)}")
    for item in flix_sweeps:
        assert isinstance(item, dict)
        counts = item["counts"]
        assert isinstance(counts, dict)
        print(
            "  flix_sweep "
            f"{item['label']} range={hx(item['start'])}..{hx(item['end'])} "
            f"core24={counts.get('core24', 0)} dens16={counts.get('dens16', 0)} "
            f"flix64={counts.get('flix64', 0)} flix128={counts.get('flix128', 0)} "
            f"bad_framing={item['bad_framing_count']}"
        )
    interesting_l32r = payload["interesting_l32r_refs"]
    assert isinstance(interesting_l32r, list)
    print(
        f"l32r_refs={payload['l32r_ref_count']} "
        f"interesting={len(interesting_l32r)}"
    )
    for ref in interesting_l32r[:10]:
        assert isinstance(ref, dict)
        lit_string = ref.get("literal_string_value") or ""
        val_string = ref.get("value_string_value") or ""
        print(
            "  l32r "
            f"addr={hx(ref['addr'])} a{ref['target_reg']} "
            f"literal={hx(ref['literal_addr'])}:{ref.get('literal_section')} "
            f"value={hx(ref.get('literal_value'))}:{ref.get('value_section')} "
            f"owner={hx(ref.get('owner_entry'))} "
            f"literal_string={display_text(str(lit_string))[:48]} "
            f"value_string={display_text(str(val_string))[:48]}"
        )
    dram_op_l32r = payload["dram_op_l32r_refs"]
    assert isinstance(dram_op_l32r, list)
    print(f"dram_op_l32r_refs={len(dram_op_l32r)}")
    print(
        "rodata_refs="
        f"{payload['rodata_ref_count']} "
        f"interesting={len(payload['interesting_rodata_refs'])}"
    )
    for item in payload["critical_string_refs"]:
        assert isinstance(item, dict)
        print(
            "  critical "
            f"{item['pattern']} addr={hx(item.get('string_addr'))} "
            f"aligned={item['aligned_hit_count']} byte={item['byte_hit_count']}"
        )
    for item in payload["critical_l32r_refs"]:
        assert isinstance(item, dict)
        print(
            "  critical_l32r "
            f"{item['pattern']} addr={hx(item.get('string_addr'))} "
            f"hits={item['hit_count']}"
        )
    clusters = payload["critical_l32r_owner_clusters"]
    assert isinstance(clusters, list)
    print(f"critical_l32r_owner_clusters={len(clusters)}")
    for cluster in clusters[:8]:
        assert isinstance(cluster, dict)
        print(
            "  critical_owner_cluster "
            f"owner={hx(cluster['owner_entry'])} patterns={cluster['pattern_count']} "
            f"hits={cluster['hit_count']} assessment={cluster['assessment']}"
        )
    investigations = payload["dma_owner_investigations"]
    assert isinstance(investigations, list)
    print(f"dma_owner_investigations={len(investigations)}")
    for item in investigations:
        assert isinstance(item, dict)
        print(
            "  dma_owner "
            f"{item['label']} owner={hx(item['owner_entry'])} "
            f"evidence={len(item['evidence_refs'])} "
            f"flix_framing_hits={item['flix_framing_hit_count']} "
            f"status={item['q1_status']}"
        )
    output_validators = payload["output_validation_investigations"]
    assert isinstance(output_validators, list)
    print(f"output_validation_investigations={len(output_validators)}")
    for item in output_validators[:6]:
        assert isinstance(item, dict)
        print(
            "  output_validation "
            f"{item['label']} owner={hx(item['owner_entry'])} "
            f"patterns={len(item['referenced_patterns'])} "
            f"evidence={len(item['evidence_refs'])} "
            f"status={item['q4_status']}"
        )
    print(f"pointer_runs={len(payload['pointer_runs'])}")
    for run in payload["pointer_runs"]:
        assert isinstance(run, dict)
        print(f"  run {hx(run['start'])}-{hx(run['end'])} count={run['count']}")
    pointer_reachability = payload.get("pointer_run_investigations")
    if isinstance(pointer_reachability, list):
        print(f"pointer_run_investigations={len(pointer_reachability)}")
        for item in pointer_reachability:
            assert isinstance(item, dict)
            print(
                "  pointer_run_reachability "
                f"{hx(item['start'])}..{hx(item['end'])} "
                f"raw_slot_refs={item.get('raw_slot_ref_count', 0)}/"
                f"{len(item['raw_ref_hits'])} "
                f"l32r_slot_refs={item.get('l32r_slot_ref_count', 0)}/"
                f"{len(item['l32r_ref_hits'])} "
                f"table_base_refs={item.get('table_base_value_ref_count', 0)} "
                f"status={item['q2_status']}"
            )
    ann_investigation = payload.get("ann_op_table_investigation")
    if isinstance(ann_investigation, dict):
        print(
            "ann_op_table "
            f"range={hx(ann_investigation['table_start'])}.."
            f"{hx(ann_investigation['table_end'])} "
            f"entries={ann_investigation['nonzero_entry_count']} "
            f"raw_refs={len(ann_investigation['raw_ref_hits'])} "
            f"l32r_refs={len(ann_investigation['l32r_ref_hits'])} "
            f"status={ann_investigation['q2_status']}"
        )
    print(f"ann_op_names={len(payload['ann_op_name_table'])}")


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Analyze apu_lib_apunn Xtensa ELF metadata")
    parser.add_argument("elf", type=Path)
    parser.add_argument("--min-string", type=int, default=4)
    parser.add_argument("--min-run", type=int, default=2)
    parser.add_argument("--json", type=Path)
    parser.add_argument("--markdown", type=Path)
    return parser


def main() -> int:
    args = build_arg_parser().parse_args()
    payload = build_payload(args.elf, args.min_string, args.min_run)
    print_summary(payload)
    if args.json:
        write_json(payload, args.json)
    if args.markdown:
        emit_markdown(payload, args.markdown)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
