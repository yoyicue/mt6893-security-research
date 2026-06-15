#!/usr/bin/env python3
"""Parse MediaTek APUSYS VPU preload metadata.

This parser follows the local kernel source layout:

  drivers/misc/mediatek/apusys/vpu/p1/vpu_hw.h

It is meant for a readable merged VPU binary dump, such as the live
reserved-memory VPU binary. It can also concatenate split cam_vpu* partition
images when they are passed in LK merge order.
"""

from __future__ import annotations

import argparse
import json
import re
import struct
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable


ALGO_NAMELEN = 32
PRE_INFO_SIZE = 0x40
ALGO_INFO_SIZE = 0x2C
PRELOAD_HEADER_SIZE = 0x64
PRELOAD_IRAM = 0xFFFFFFFF

VPU_SIZE_BINARY_CODE = 0x02A10000
VPU_OFFSET_IMAGE_HEADERS = VPU_SIZE_BINARY_CODE - 0x30000

HEX_INT_KEYS = {
    "off",
    "paddr",
    "mem_sz",
    "file_sz",
    "flag",
    "info",
    "start_addr",
    "entry_off",
    "map_size",
    "carve_size",
    "embedded_elf_entry",
    "embedded_elf_shoff",
    "version",
    "build_date",
    "header_size",
    "image_size",
    "pre_size",
    "seg_size",
    "alg_info",
    "pre_info",
    "size",
}


@dataclass
class Header:
    index: int
    file_offset: int
    version: int
    build_date: int
    header_size: int
    image_size: int
    mem_size: int
    code_segment_count: int
    seg_info: int
    seg_size: int
    pre_info_count: int
    pre_info: int
    pre_size: int
    algo_info_count: int
    alg_info: int


@dataclass
class ImagePart:
    path: str
    offset: int
    size: int


@dataclass
class PreInfo:
    header_index: int
    entry_index: int
    entry_offset: int
    vpu_core: int
    off: int
    paddr: int
    mem_sz: int
    file_sz: int
    flag: int
    info: int
    start_addr: int
    name: str
    kind: str
    map_addr: int
    map_size: int
    entry_off: int | None
    carve_offset: int
    carve_size: int
    embedded_elf_offset: int | None
    embedded_elf_file_offset: int | None
    embedded_elf_entry: int | None
    embedded_elf_shoff: int | None


@dataclass
class AlgoInfo:
    header_index: int
    entry_index: int
    entry_offset: int
    vpu_core: int
    offset: int
    length: int
    name: str


def parse_int(text: str) -> int:
    return int(text, 0)


def align(value: int, size: int) -> int:
    return (value + size - 1) & ~(size - 1)


def cstring(raw: bytes) -> str:
    return raw.split(b"\x00", 1)[0].decode("ascii", "replace")


def sanitize_name(name: str) -> str:
    cleaned = re.sub(r"[^A-Za-z0-9_.-]+", "_", name)
    return cleaned.strip("_") or "unnamed"


def load_images(paths: list[Path]) -> tuple[bytes, list[ImagePart]]:
    data = bytearray()
    parts: list[ImagePart] = []
    for path in paths:
        raw = path.read_bytes()
        parts.append(ImagePart(path=str(path), offset=len(data), size=len(raw)))
        data.extend(raw)
    return bytes(data), parts


def parse_header(data: bytes, file_offset: int, index: int) -> Header:
    if file_offset < 0 or file_offset + PRELOAD_HEADER_SIZE > len(data):
        raise ValueError(f"header {index}: offset 0x{file_offset:x} out of range")

    words = struct.unpack_from("<25I", data, file_offset)
    return Header(
        index=index,
        file_offset=file_offset,
        version=words[0],
        build_date=words[1],
        header_size=words[10],
        image_size=words[11],
        mem_size=words[12],
        code_segment_count=words[13],
        seg_info=words[14],
        seg_size=words[15],
        pre_info_count=words[16],
        pre_info=words[17],
        pre_size=words[18],
        algo_info_count=words[19],
        alg_info=words[20],
    )


def plausible_header(header: Header, data_len: int) -> bool:
    if header.header_size < PRELOAD_HEADER_SIZE:
        return False
    if header.header_size > 0x200000:
        return False
    if header.header_size % 4:
        return False
    if header.file_offset + header.header_size > data_len:
        return False
    if header.pre_info_count > 0x2000 or header.algo_info_count > 0x2000:
        return False
    if header.pre_info_count:
        end = header.pre_info + header.pre_info_count * PRE_INFO_SIZE
        if header.pre_info < PRELOAD_HEADER_SIZE or end > header.header_size:
            return False
    if header.algo_info_count:
        end = header.alg_info + header.algo_info_count * ALGO_INFO_SIZE
        if header.alg_info < PRELOAD_HEADER_SIZE or end > header.header_size:
            return False
    return True


def parse_header_chain(data: bytes, start: int, count: int) -> list[Header]:
    headers: list[Header] = []
    off = start
    for index in range(count):
        header = parse_header(data, off, index)
        if not plausible_header(header, len(data)):
            raise ValueError(f"header {index} at 0x{off:x} is not plausible")
        headers.append(header)
        off += header.header_size
    return headers


def find_header_chains(data: bytes, count: int, limit: int) -> list[list[Header]]:
    chains: list[list[Header]] = []
    for off in range(0, max(0, len(data) - PRELOAD_HEADER_SIZE + 1), 0x1000):
        try:
            chain = parse_header_chain(data, off, count)
        except ValueError:
            continue
        score = sum(h.pre_info_count + h.algo_info_count for h in chain)
        if score > 0:
            chains.append(chain)
            if len(chains) >= limit:
                break
    return chains


def parse_pre_infos(data: bytes, header: Header) -> list[PreInfo]:
    entries: list[PreInfo] = []
    base = header.file_offset + header.pre_info
    for index in range(header.pre_info_count):
        off = base + index * PRE_INFO_SIZE
        fields = struct.unpack_from("<8I", data, off)
        name = cstring(data[off + 0x20 : off + 0x20 + ALGO_NAMELEN])
        vpu_core, bin_off, paddr, mem_sz, file_sz, flag, info, start_addr = fields
        map_addr = start_addr & 0xFFFF0000
        map_size = info if info else align(file_sz, 0x1000)
        kind = "IRAM" if paddr == PRELOAD_IRAM else "PROG"
        entry_off = None if kind == "IRAM" else (paddr - map_addr) & 0xFFFFFFFF
        carve_size = file_sz if file_sz else map_size
        embedded_elf_offset = None
        embedded_elf_file_offset = None
        embedded_elf_entry = None
        embedded_elf_shoff = None
        if kind == "PROG" and bin_off < len(data):
            scan_end = min(len(data), bin_off + min(file_sz or map_size, 0x1000))
            rel = data.find(b"\x7fELF", bin_off, scan_end)
            if rel >= 0 and rel + 0x34 <= len(data):
                try:
                    fields = struct.unpack_from("<16sHHIIIIIHHHHHH", data, rel)
                except struct.error:
                    fields = None
                if fields and fields[0] == b"\x7fELF\x01\x01\x01\x00\x00\x00\x00\x00\x00\x00\x00\x00":
                    embedded_elf_offset = rel - bin_off
                    embedded_elf_file_offset = rel
                    embedded_elf_entry = fields[4]
                    embedded_elf_shoff = fields[6]
        entries.append(
            PreInfo(
                header_index=header.index,
                entry_index=index,
                entry_offset=off,
                vpu_core=vpu_core,
                off=bin_off,
                paddr=paddr,
                mem_sz=mem_sz,
                file_sz=file_sz,
                flag=flag,
                info=info,
                start_addr=start_addr,
                name=name,
                kind=kind,
                map_addr=map_addr,
                map_size=map_size,
                entry_off=entry_off,
                carve_offset=bin_off,
                carve_size=carve_size,
                embedded_elf_offset=embedded_elf_offset,
                embedded_elf_file_offset=embedded_elf_file_offset,
                embedded_elf_entry=embedded_elf_entry,
                embedded_elf_shoff=embedded_elf_shoff,
            )
        )
    return entries


def parse_algo_infos(data: bytes, header: Header) -> list[AlgoInfo]:
    entries: list[AlgoInfo] = []
    base = header.file_offset + header.alg_info
    for index in range(header.algo_info_count):
        off = base + index * ALGO_INFO_SIZE
        vpu_core, algo_off, length = struct.unpack_from("<3I", data, off)
        name = cstring(data[off + 0x0C : off + 0x0C + ALGO_NAMELEN])
        entries.append(
            AlgoInfo(
                header_index=header.index,
                entry_index=index,
                entry_offset=off,
                vpu_core=vpu_core,
                offset=algo_off,
                length=length,
                name=name,
            )
        )
    return entries


def choose_headers(data: bytes, args: argparse.Namespace) -> list[Header]:
    if args.head_offset is not None:
        return parse_header_chain(data, args.head_offset, args.headers)

    candidates = []
    if len(data) >= VPU_OFFSET_IMAGE_HEADERS + PRELOAD_HEADER_SIZE:
        candidates.append(VPU_OFFSET_IMAGE_HEADERS)
    candidates.append(0)

    errors: list[str] = []
    for off in candidates:
        try:
            return parse_header_chain(data, off, args.headers)
        except ValueError as exc:
            errors.append(str(exc))

    if not args.auto:
        joined = "; ".join(errors)
        raise ValueError(f"no plausible header at default offsets: {joined}")

    chains = find_header_chains(data, args.headers, args.scan_limit)
    if not chains:
        raise ValueError("auto-scan found no plausible preload image-header chain")
    if len(chains) > 1:
        print(
            f"[!] auto-scan found {len(chains)} candidate header chains; using "
            f"0x{chains[0][0].file_offset:x}",
            file=sys.stderr,
        )
    return chains[0]


def match_name(name: str, pattern: str) -> bool:
    return name == pattern or pattern in name


def hex_dict(obj: object) -> dict[str, object]:
    out = asdict(obj)
    for key, value in list(out.items()):
        if isinstance(value, int) and (
            key.endswith("offset")
            or key.endswith("addr")
            or key in HEX_INT_KEYS
        ):
            out[f"{key}_hex"] = f"0x{value:x}"
    return out


def print_headers(headers: Iterable[Header]) -> None:
    for h in headers:
        print(
            f"header[{h.index}] file_off=0x{h.file_offset:x} "
            f"version=0x{h.version:x} build_date=0x{h.build_date:x} "
            f"header_size=0x{h.header_size:x} image_size=0x{h.image_size:x} "
            f"pre_info=0x{h.pre_info:x} pre_count={h.pre_info_count} "
            f"alg_info=0x{h.alg_info:x} alg_count={h.algo_info_count}"
        )


def print_pre(entry: PreInfo) -> None:
    entry_off = "n/a" if entry.entry_off is None else f"0x{entry.entry_off:x}"
    elf = ""
    if entry.embedded_elf_offset is not None:
        elf = (
            f" embedded_elf=+0x{entry.embedded_elf_offset:x}"
            f"/file0x{entry.embedded_elf_file_offset:x}"
            f" elf_entry=0x{entry.embedded_elf_entry:x}"
            f" elf_shoff=0x{entry.embedded_elf_shoff:x}"
        )
    print(
        f"pre[{entry.header_index}:{entry.entry_index}] {entry.name!r} "
        f"{entry.kind} vpu_core=0x{entry.vpu_core:x} off=0x{entry.off:x} "
        f"file_sz=0x{entry.file_sz:x} map_size=0x{entry.map_size:x} "
        f"paddr=0x{entry.paddr:x} start=0x{entry.start_addr:x} "
        f"entry_off={entry_off} flag=0x{entry.flag:x} info=0x{entry.info:x}"
        f"{elf}"
    )


def print_algo(entry: AlgoInfo) -> None:
    print(
        f"normal[{entry.header_index}:{entry.entry_index}] {entry.name!r} "
        f"vpu_core=0x{entry.vpu_core:x} off=0x{entry.offset:x} "
        f"length=0x{entry.length:x}"
    )


def carve_entries(data: bytes, entries: list[PreInfo], out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    for entry in entries:
        if entry.carve_offset >= len(data):
            print(
                f"[!] skip carve {entry.name}: offset 0x{entry.carve_offset:x} "
                f"outside image",
                file=sys.stderr,
            )
            continue
        end = min(len(data), entry.carve_offset + entry.carve_size)
        suffix = "iram" if entry.kind == "IRAM" else "prog"
        name = (
            f"{sanitize_name(entry.name)}_h{entry.header_index}_"
            f"pre{entry.entry_index}_{suffix}_off{entry.carve_offset:x}.bin"
        )
        path = out_dir / name
        path.write_bytes(data[entry.carve_offset:end])
        print(f"[+] carved {path} size=0x{end - entry.carve_offset:x}")


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Parse MTK APUSYS VPU preload image metadata"
    )
    parser.add_argument(
        "images",
        nargs="+",
        type=Path,
        help="merged VPU binary, or split cam_vpu images in LK merge order",
    )
    parser.add_argument(
        "--head-offset",
        type=parse_int,
        help="image-header offset, e.g. 0x29e0000; defaults to MT687x value then 0",
    )
    parser.add_argument(
        "--auto",
        action="store_true",
        help="scan 4 KiB-aligned offsets if default header offsets fail",
    )
    parser.add_argument("--headers", type=int, default=3, help="header count")
    parser.add_argument("--scan-limit", type=int, default=8, help="auto-scan max hits")
    parser.add_argument("--algo", default="apu_lib_apunn", help="preload algo name")
    parser.add_argument("--list-all", action="store_true", help="list every entry")
    parser.add_argument("--list-normal", action="store_true", help="list normal algos")
    parser.add_argument("--json", type=Path, help="write parsed metadata JSON")
    parser.add_argument("--carve-dir", type=Path, help="carve matching preload segments")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_arg_parser().parse_args(argv)
    data, image_parts = load_images(args.images)
    if len(image_parts) > 1:
        for part in image_parts:
            print(
                f"image-part {part.path} offset=0x{part.offset:x} "
                f"size=0x{part.size:x}"
            )
    headers = choose_headers(data, args)
    pre_entries = [e for h in headers for e in parse_pre_infos(data, h)]
    normal_entries = [e for h in headers for e in parse_algo_infos(data, h)]

    matches = [e for e in pre_entries if match_name(e.name, args.algo)]

    print_headers(headers)
    if args.list_all:
        for entry in pre_entries:
            print_pre(entry)
    else:
        for entry in matches:
            print_pre(entry)
    if args.list_normal:
        for entry in normal_entries:
            print_algo(entry)

    if not matches:
        print(f"[!] no preload entry matched {args.algo!r}", file=sys.stderr)

    if args.carve_dir:
        carve_entries(data, matches, args.carve_dir)

    if args.json:
        payload = {
            "images": [hex_dict(part) for part in image_parts],
            "headers": [hex_dict(h) for h in headers],
            "preload_entries": [hex_dict(e) for e in pre_entries],
            "normal_entries": [hex_dict(e) for e in normal_entries],
            "matches": [hex_dict(e) for e in matches],
        }
        args.json.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n")

    return 0 if matches else 1


if __name__ == "__main__":
    raise SystemExit(main())
