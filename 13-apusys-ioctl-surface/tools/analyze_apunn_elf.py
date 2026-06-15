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


@dataclass
class PointerRun:
    start: int
    end: int
    count: int
    entries: list[PointerEntry]


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
            cur.append(
                PointerEntry(
                    addr=source.addr + off,
                    value=value,
                    target_section=target_section.name,
                    target_string=ro_string_at(strings_by_addr, value)
                    or cstring_at_va(data, sections, value, {".rodata"}),
                    prop_size=None if prop is None else prop.size,
                    prop_flags=None if prop is None else flags_text(prop.flags),
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
    lines.append("## Pointer Runs")
    lines.append("")
    for run in payload["pointer_runs"]:
        assert isinstance(run, dict)
        lines.append(f"### `{hx(run['start'])}` count {run['count']}")
        lines.append("")
        lines.append("| slot | value | target | prop | string |")
        lines.append("|---:|---:|---|---|---|")
        for entry in run["entries"]:
            assert isinstance(entry, dict)
            lines.append(
                f"| `{hx(entry['addr'])}` | `{hx(entry['value'])}` | "
                f"`{entry['target_section']}` | `{entry.get('prop_flags') or ''}` | "
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
        min_run=min_run,
    )

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
