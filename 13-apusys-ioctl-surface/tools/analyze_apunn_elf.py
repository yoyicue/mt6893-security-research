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


def find_strings(data: bytes, section: Section, min_len: int) -> list[StringEntry]:
    blob = section_bytes(data, section)
    out: list[StringEntry] = []
    pos = 0
    while pos < len(blob):
        if 0x20 <= blob[pos] < 0x7F:
            end = pos
            while end < len(blob) and 0x20 <= blob[end] < 0x7F:
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
    if not raw or any(byte < 0x20 or byte >= 0x7F for byte in raw):
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
    ("early_callee", 0x70007440),
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
            f"`{hx(ref['string_addr'])}{suffix}` `{ref['string_value']}` |"
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
                f"{entry.get('target_string') or ''} |"
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
            f"`{entry['name']}` | `{hx(entry['string_addr'])}` |"
        )
    lines.append("")
    lines.append("## Interesting Strings")
    lines.append("")
    for entry in payload["interesting_strings"]:
        assert isinstance(entry, dict)
        lines.append(f"- `{hx(entry['addr'])}` `{entry['value']}`")
    lines.append("")
    path.write_text("\n".join(lines) + "\n")


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
        "rodata_ref_count": len(rodata_refs),
        "rodata_refs": to_jsonable(rodata_refs),
        "interesting_rodata_refs": to_jsonable(
            [ref for ref in rodata_refs if is_interesting_rodata_ref(ref)]
        ),
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
    print(
        "rodata_refs="
        f"{payload['rodata_ref_count']} "
        f"interesting={len(payload['interesting_rodata_refs'])}"
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
