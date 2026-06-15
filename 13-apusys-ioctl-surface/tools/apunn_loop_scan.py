#!/usr/bin/env python3
"""Enumerate Xtensa zero-overhead hardware loops in the APUNN core ELF.

The native MVPU6F (Vision-P6 / LX7) build uses TIE/FLIX bundles that IDA's
generic Xtensa module cannot follow, so neither a stock objdump nor capstone 5
can decode it, and no matching Cadence overlay is installed on this host. This
helper sidesteps that for the one thing we need to bind INFO12/INFO13: the
LOOP/LOOPNEZ/LOOPGTZ family.

Encoding is taken verbatim from Ghidra 12's Xtensa SLEIGH
(Processors/Xtensa/data/languages/xtensaInstructions.sinc, "Loop Option"):

    loop    as, off : op0=6  at=0b0111  ar=0b1000
    loopnez as, off : op0=6  at=0b0111  ar=0b1001
    loopgtz as, off : op0=6  at=0b0111  ar=0b1010
    LoopOffset8 = inst_start + ri8_i8 + 4          # ri8_i8 = imm8 = byte[2]

In little-endian byte terms (word = b0 | b1<<8 | b2<<16):
    b0 == 0x76                 (op0=6, at=7)
    b1>>4 in {8,9,10}          (ar selects loop / loopnez / loopgtz)
    b1&0xF == as               (loop count register)
    b2    == imm8              (loop_end = addr + imm8 + 4)

Validated against the byte-verified island at owner 0x7003ce3c: the only
byte-aligned hardware loop in that function is at 0x7003d3ea with
loop_end == 0x7003d423, which matches the .xt.prop loop_target marker exactly.

NOTE: byte-aligned scanning will MISS any LOOP packed inside a FLIX bundle
(e.g. owner 0x7003b468 / loop_target 0x7003c102 has no byte-aligned LOOP, so
its loop is either FLIX-embedded or a plain conditional back-edge). This tool
only claims byte-aligned hardware loops; FLIX bundle-internal count/branch
semantics require the separate FLIX/TIE slot work.
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import asdict, dataclass
from pathlib import Path

import analyze_apunn_elf as A

LOOP_KIND = {8: "loop", 9: "loopnez", 10: "loopgtz"}


@dataclass
class HwLoop:
    addr: int
    kind: str
    count_reg: int
    imm8: int
    loop_end: int
    body_start: int
    body_size: int
    in_insn_prop: bool
    owner_entry: int | None
    owner_delta: int | None
    body_mem_ops: list[str]


def build_insn_coverage(blob: bytes, props, text) -> bytearray:
    cov = bytearray(len(blob))
    for p in props:
        if not p.flags & A.XT_PROP_INSN:
            continue
        start = max(p.addr, text.addr) - text.addr
        end = min(p.addr + max(p.size, 1), text.addr + text.size) - text.addr
        if 0 <= start < end <= len(cov):
            cov[start:end] = b"\x01" * (end - start)
    return cov


def scan_body_mem_ops(blob: bytes, text, lo: int, hi: int) -> list[str]:
    out: list[str] = []
    o = lo - text.addr
    end = hi - text.addr
    while o < end:
        d = A.decode_standard_mem_access(blob, o)
        if d:
            mn, base, val, moff, sz, st = d
            out.append("%#x %s a%d,a%d,%#x" % (text.addr + o, mn, val, base, moff))
        o += 1
    return out


def find_hw_loops(data: bytes, sections, props, funcs) -> list[HwLoop]:
    text = A.section_by_name(sections, ".text")
    blob = A.section_bytes(data, text)
    cov = build_insn_coverage(blob, props, text)
    loops: list[HwLoop] = []
    for off in range(0, len(blob) - 2):
        if blob[off] != 0x76:
            continue
        ar = blob[off + 1] >> 4
        if ar not in LOOP_KIND:
            continue
        addr = text.addr + off
        imm8 = blob[off + 2]
        end = addr + imm8 + 4
        body_start = addr + 3
        owner, delta = A.owner_for_addr(funcs, addr)
        loops.append(
            HwLoop(
                addr=addr,
                kind=LOOP_KIND[ar],
                count_reg=blob[off + 1] & 0xF,
                imm8=imm8,
                loop_end=end,
                body_start=body_start,
                body_size=end - body_start,
                in_insn_prop=bool(cov[off]),
                owner_entry=owner,
                owner_delta=delta,
                body_mem_ops=scan_body_mem_ops(blob, text, body_start, end),
            )
        )
    return loops


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("elf")
    ap.add_argument("--json")
    ap.add_argument("--insn-only", action="store_true",
                    help="only report loops whose LOOP byte falls in an .xt.prop insn run")
    ap.add_argument("--owner", help="hex owner entry to filter on, e.g. 0x7003ce3c")
    args = ap.parse_args()

    data = Path(args.elf).read_bytes()
    eh = A.parse_elf_header(data)
    sections = A.parse_sections(data, eh)
    props = A.parse_xt_props(data, A.section_by_name(sections, ".xt.prop"))
    funcs = A.find_function_candidates(data, sections, props)

    loops = find_hw_loops(data, sections, props, funcs)
    if args.insn_only:
        loops = [l for l in loops if l.in_insn_prop]
    if args.owner:
        want = int(args.owner, 16)
        loops = [l for l in loops if l.owner_entry == want]

    in_insn = sum(1 for l in loops if l.in_insn_prop)
    print("hardware loops: %d total, %d in insn props" % (len(loops), in_insn))
    loops.sort(key=lambda l: (l.owner_entry or 0, l.addr))
    for l in loops:
        flag = "" if l.in_insn_prop else "  [NOT-in-insn-prop]"
        owner = "%#x+%#x" % (l.owner_entry, l.owner_delta) if l.owner_entry else "?"
        print("  %#x %-8s count=a%-2d end=%#x body=%#x..%#x owner=%s%s"
              % (l.addr, l.kind, l.count_reg, l.loop_end, l.body_start,
                 l.loop_end, owner, flag))
        for m in l.body_mem_ops:
            print("        %s" % m)

    if args.json:
        Path(args.json).write_text(json.dumps([asdict(l) for l in loops], indent=2))
        print("wrote %s" % args.json)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except BrokenPipeError:
        # tolerate `| head` etc.
        try:
            sys.stdout.close()
        except Exception:
            pass
