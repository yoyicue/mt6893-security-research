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
    ("large_auto_function", 0x7003B424),
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
    function_candidates = find_function_candidates(data, sections, props)
    prop_counts: dict[str, int] = {
        "total": len(props),
        "insn": sum(1 for prop in props if prop.flags & XT_PROP_INSN),
        "data": sum(1 for prop in props if prop.flags & XT_PROP_DATA),
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
        "property_counts": prop_counts,
        "entry_properties": entry_props,
        "function_candidate_count": len(function_candidates),
        "function_candidates": to_jsonable(function_candidates),
        "key_addresses": to_jsonable(
            build_key_addresses(sections, props, function_candidates)
        ),
        "standard_islands": to_jsonable(standard_islands),
        "rodata_ref_count": len(rodata_refs),
        "rodata_refs": to_jsonable(rodata_refs),
        "interesting_rodata_refs": to_jsonable(
            [ref for ref in rodata_refs if is_interesting_rodata_ref(ref)]
        ),
        "critical_string_refs": to_jsonable(critical_string_refs),
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
    for section in payload["sections"]:
        assert isinstance(section, dict)
        print(
            f"section {section['name'] or '<null>':18s} "
            f"addr={hx(section['addr'])} off={hx(section['offset'])} "
            f"size={hx(section['size'])} flags={hx(section['flags'])}"
        )
    print("property_counts", payload["property_counts"])
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
    print(f"pointer_runs={len(payload['pointer_runs'])}")
    for run in payload["pointer_runs"]:
        assert isinstance(run, dict)
        print(f"  run {hx(run['start'])}-{hx(run['end'])} count={run['count']}")
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
