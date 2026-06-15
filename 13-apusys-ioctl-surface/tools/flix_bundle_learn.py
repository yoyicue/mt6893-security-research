#!/usr/bin/env python3
"""Learn APUNN FLIX bundle structure from dispatch-table anchors.

This is deliberately not a disassembler.  It treats FLIX64/FLIX128 decoding as
an inference problem and exports reproducible evidence:

* dispatch-table targets and their .xt.prop metadata
* per-table target stride patterns
* byte stability around table targets
* 8/16-byte phase scores inside dense FLIX regions
* standard Xtensa control-flow candidates as boundary constraints
* repeated raw chunks that can become future format/NOP/slot templates

The first useful question is whether dispatch targets are actual bundle starts
or landing pads inside a larger FLIX stream.  The reports produced here are
designed to answer that before we assign any opcode semantics.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import math
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import analyze_apunn_elf as A  # noqa: E402


LEARNER_PROFILES: dict[str, dict[str, int]] = {
    "baseline": {},
    "phase8_strict": {"phase8_bonus": 4, "phase16_bonus": 1},
    "phase16_boost": {"phase8_bonus": 1, "phase16_bonus": 5},
    "control_heavy": {"control_exact_bonus": 6, "control_near_bonus": 4},
    "pcrel_landing": {"pcrel_exact_landing_bonus": 4, "pcrel_near_landing_bonus": 3},
    "pcrel_control_heavy": {
        "control_exact_bonus": 6,
        "control_near_bonus": 4,
        "pcrel_exact_landing_bonus": 5,
        "pcrel_near_landing_bonus": 4,
    },
    "pcrel_internal_strict": {
        "pcrel_exact_landing_bonus": 4,
        "pcrel_near_landing_bonus": 3,
        "internal_pcrel_min_score": 29,
        "internal_pcrel_dense_insn": 0,
    },
    "pcrel_internal_broad": {
        "pcrel_exact_landing_bonus": 4,
        "pcrel_near_landing_bonus": 3,
        "internal_pcrel_min_score": 20,
        "internal_pcrel_dense_insn": 1,
    },
    "slot_grammar_strict": {
        "pcrel_exact_landing_bonus": 4,
        "pcrel_near_landing_bonus": 3,
        "internal_pcrel_min_score": 29,
        "internal_pcrel_dense_insn": 0,
        "slot_template_min_usable": 4,
        "slot_template_min_internal": 4,
    },
    "slot_grammar_broad": {
        "pcrel_exact_landing_bonus": 4,
        "pcrel_near_landing_bonus": 3,
        "internal_pcrel_min_score": 20,
        "internal_pcrel_dense_insn": 1,
        "slot_template_min_usable": 1,
        "slot_template_min_internal": 1,
    },
    "operand_model_strict": {
        "pcrel_exact_landing_bonus": 4,
        "pcrel_near_landing_bonus": 3,
        "internal_pcrel_min_score": 29,
        "internal_pcrel_dense_insn": 0,
        "slot_template_min_usable": 4,
        "slot_template_min_internal": 4,
        "operand_model_min_usable": 64,
        "operand_model_min_internal": 64,
        "operand_model_max_reject_bps": 3000,
        "operand_model_min_negative": 8,
    },
    "operand_model_broad": {
        "pcrel_exact_landing_bonus": 4,
        "pcrel_near_landing_bonus": 3,
        "internal_pcrel_min_score": 20,
        "internal_pcrel_dense_insn": 1,
        "slot_template_min_usable": 1,
        "slot_template_min_internal": 1,
        "operand_model_min_usable": 16,
        "operand_model_min_internal": 16,
        "operand_model_max_reject_bps": 4000,
        "operand_model_min_negative": 8,
    },
    "cfg_edges_strict": {
        "pcrel_exact_landing_bonus": 4,
        "pcrel_near_landing_bonus": 3,
        "internal_pcrel_min_score": 29,
        "internal_pcrel_dense_insn": 0,
        "slot_template_min_usable": 4,
        "slot_template_min_internal": 4,
        "operand_model_min_usable": 64,
        "operand_model_min_internal": 64,
        "operand_model_max_reject_bps": 3000,
        "operand_model_min_negative": 8,
        "cfg_edge_min_score": 29,
        "cfg_edge_allow_dense_insn": 0,
        "cfg_edge_allow_anchor_landing": 0,
        "cfg_cluster_min_nodes": 2,
    },
    "cfg_edges_broad": {
        "pcrel_exact_landing_bonus": 4,
        "pcrel_near_landing_bonus": 3,
        "internal_pcrel_min_score": 20,
        "internal_pcrel_dense_insn": 1,
        "slot_template_min_usable": 1,
        "slot_template_min_internal": 1,
        "operand_model_min_usable": 16,
        "operand_model_min_internal": 16,
        "operand_model_max_reject_bps": 4000,
        "operand_model_min_negative": 8,
        "cfg_edge_min_score": 20,
        "cfg_edge_allow_dense_insn": 1,
        "cfg_edge_allow_anchor_landing": 1,
        "cfg_cluster_min_nodes": 1,
    },
    "block_extent_strict": {
        "pcrel_exact_landing_bonus": 4,
        "pcrel_near_landing_bonus": 3,
        "internal_pcrel_min_score": 29,
        "internal_pcrel_dense_insn": 0,
        "slot_template_min_usable": 4,
        "slot_template_min_internal": 4,
        "operand_model_min_usable": 64,
        "operand_model_min_internal": 64,
        "operand_model_max_reject_bps": 3000,
        "operand_model_min_negative": 8,
        "cfg_edge_min_score": 29,
        "cfg_edge_allow_dense_insn": 0,
        "cfg_edge_allow_anchor_landing": 0,
        "cfg_cluster_min_nodes": 2,
        "block_extent_enable": 1,
        "block_include_entry_only": 0,
    },
    "block_extent_broad": {
        "pcrel_exact_landing_bonus": 4,
        "pcrel_near_landing_bonus": 3,
        "internal_pcrel_min_score": 20,
        "internal_pcrel_dense_insn": 1,
        "slot_template_min_usable": 1,
        "slot_template_min_internal": 1,
        "operand_model_min_usable": 16,
        "operand_model_min_internal": 16,
        "operand_model_max_reject_bps": 4000,
        "operand_model_min_negative": 8,
        "cfg_edge_min_score": 20,
        "cfg_edge_allow_dense_insn": 1,
        "cfg_edge_allow_anchor_landing": 1,
        "cfg_cluster_min_nodes": 1,
        "block_extent_enable": 1,
        "block_include_entry_only": 1,
    },
    "block_components_strict": {
        "pcrel_exact_landing_bonus": 4,
        "pcrel_near_landing_bonus": 3,
        "internal_pcrel_min_score": 29,
        "internal_pcrel_dense_insn": 0,
        "slot_template_min_usable": 4,
        "slot_template_min_internal": 4,
        "operand_model_min_usable": 64,
        "operand_model_min_internal": 64,
        "operand_model_max_reject_bps": 3000,
        "operand_model_min_negative": 8,
        "cfg_edge_min_score": 29,
        "cfg_edge_allow_dense_insn": 0,
        "cfg_edge_allow_anchor_landing": 0,
        "cfg_cluster_min_nodes": 2,
        "block_extent_enable": 1,
        "block_include_entry_only": 0,
        "component_enable": 1,
    },
    "block_components_broad": {
        "pcrel_exact_landing_bonus": 4,
        "pcrel_near_landing_bonus": 3,
        "internal_pcrel_min_score": 20,
        "internal_pcrel_dense_insn": 1,
        "slot_template_min_usable": 1,
        "slot_template_min_internal": 1,
        "operand_model_min_usable": 16,
        "operand_model_min_internal": 16,
        "operand_model_max_reject_bps": 4000,
        "operand_model_min_negative": 8,
        "cfg_edge_min_score": 20,
        "cfg_edge_allow_dense_insn": 1,
        "cfg_edge_allow_anchor_landing": 1,
        "cfg_cluster_min_nodes": 1,
        "block_extent_enable": 1,
        "block_include_entry_only": 1,
        "component_enable": 1,
    },
    "template_strict": {"template_threshold": 7, "repeat_bonus": 1},
    "template_aggressive": {"template_threshold": 3, "repeat_bonus": 3},
    "landing_strict": {"landing_margin": 4, "abc_stride_bonus": 6},
    "c1_strong": {"c1_bonus": 6, "phase16_bonus": 3},
}


DISPATCH_TABLES = (
    ("A", 0x70000180, 12),
    ("B", 0x700001C0, 12),
    ("C", 0x70000200, 12),
    ("D", 0x70000B80, 31),
)

DENSE_REGIONS = (
    ("flk_dense", 0x700169A2, 0x70018190),
    ("ann_dense", 0x70081EC5, 0x70082B2C),
)

MANUAL_ENTRIES = (
    ("flk_region_start", 0x700169A2),
    ("flk_entry_A", 0x70016E2D),
    ("flk_entry_B", 0x70017B73),
    ("flk_entry_C", 0x70017CD1),
    ("ann_owner", 0x70081D50),
)


@dataclass(frozen=True)
class Anchor:
    group: str
    index: int | None
    addr: int
    source: str


@dataclass
class PropHit:
    addr: int
    size: int
    flags: str


@dataclass
class ControlCandidate:
    source: int
    kind: str
    raw: bytes
    target: int | None
    confidence: str
    note: str


def hx(value: int | None) -> str:
    if value is None:
        return ""
    if value < 0:
        return "-0x%x" % (-value)
    return "0x%x" % value


def bytes_hex(blob: bytes) -> str:
    return blob.hex()


def parse_hex(text: object) -> int | None:
    if text is None:
        return None
    value = str(text)
    if not value:
        return None
    return int(value, 0)


def byte_mask(samples: list[bytes]) -> tuple[str, list[int]]:
    if not samples:
        return "", []
    width = min(len(s) for s in samples)
    fixed_positions: list[int] = []
    tokens: list[str] = []
    for i in range(width):
        vals = {s[i] for s in samples}
        if len(vals) == 1:
            fixed_positions.append(i)
            tokens.append("%02x" % samples[0][i])
        else:
            tokens.append("??")
    return " ".join(tokens), fixed_positions


def shannon(values: Iterable[int]) -> float:
    vals = list(values)
    if not vals:
        return 0.0
    total = len(vals)
    counts = Counter(vals)
    return -sum((n / total) * math.log2(n / total) for n in counts.values())


def read_va(data: bytes, sections: list[A.Section], addr: int, size: int) -> bytes:
    sec = A.section_for_va(sections, addr)
    if sec is None:
        return b""
    off = sec.offset + (addr - sec.addr)
    if off < 0 or off >= len(data):
        return b""
    return data[off: min(off + size, sec.offset + sec.size, len(data))]


def read_u32(data: bytes, sections: list[A.Section], addr: int) -> int:
    value = A.read_u32_va(data, sections, addr)
    if value is None:
        raise ValueError("cannot read u32 at %#x" % addr)
    return value


def count_chunk_in_region(data: bytes, sections: list[A.Section], addr: int, width: int) -> int:
    region = region_for_addr(addr)
    if not region:
        return 0
    for name, start, end in DENSE_REGIONS:
        if name != region:
            continue
        chunk = read_va(data, sections, addr, width)
        blob = read_va(data, sections, start, end - start)
        if len(chunk) != width or not blob:
            return 0
        count = 0
        for off in range(0, len(blob) - width + 1):
            if blob[off:off + width] == chunk:
                count += 1
        return count
    return 0


def prop_for_addr(props: list[A.XtProp], addr: int) -> PropHit | None:
    # The property list is small enough for a simple scan, and this keeps the
    # result faithful to the analyzer's current interpretation.
    for prop in props:
        end = prop.addr + max(prop.size, 1)
        if prop.addr <= addr < end:
            return PropHit(prop.addr, prop.size, "|".join(prop.flag_names))
    return None


def best_prop_for_addr(props: list[A.XtProp], addr: int) -> PropHit | None:
    best = A.best_prop_for_addr(props, addr)
    if best is None:
        return None
    return PropHit(best.addr, best.size, A.flags_text(best.flags))


def region_for_addr(addr: int) -> str:
    for name, start, end in DENSE_REGIONS:
        if start <= addr < end:
            return name
    return ""


def text_contains(sections: list[A.Section], addr: int | None) -> bool:
    if addr is None:
        return False
    sec = A.section_for_va(sections, addr)
    return sec is not None and sec.name == ".text"


def sign_extend(value: int, bits: int) -> int:
    sign = 1 << (bits - 1)
    return (value ^ sign) - sign


def decode_pcrel_operand_raw(raw: str) -> dict[str, int]:
    blob = bytes.fromhex(raw)
    if len(blob) != 3:
        raise ValueError("PC-relative operand raw must be 3 bytes: %s" % raw)
    opcode_low = blob[0] & 0x0F
    opcode_high = blob[0] >> 4
    imm18 = (opcode_high << 16) | blob[1] | (blob[2] << 8)
    signed_words = sign_extend(imm18, 18)
    return {
        "opcode_low": opcode_low,
        "opcode_high": opcode_high,
        "imm18": imm18,
        "signed_words": signed_words,
        "decoded_delta": signed_words << 2,
    }


def decode_standard_control_candidate(blob: bytes, addr: int, off: int) -> ControlCandidate | None:
    if off >= len(blob):
        return None
    b0 = blob[off]

    if off + 3 <= len(blob) and blob[off:off + 3] == b"\xe0\x08\x00":
        return ControlCandidate(addr, "callx8", blob[off:off + 3], None, "high", "exact callx8 a8 byte pattern")

    if off + 3 <= len(blob) and b0 == 0x76:
        b1 = blob[off + 1]
        kind = b1 >> 4
        if kind in A.STANDARD_LOOP_KINDS:
            target = addr + 4 + blob[off + 2]
            return ControlCandidate(addr, A.STANDARD_LOOP_KINDS[kind], blob[off:off + 3], target, "high", "standard LOOP-family byte pattern")

    if off + 3 <= len(blob) and b0 in (0x16, 0x56):
        imm12 = (blob[off + 2] << 4) | (blob[off + 1] >> 4)
        target = addr + 4 + sign_extend(imm12, 12)
        return ControlCandidate(
            addr,
            "beqz" if b0 == 0x16 else "bnez",
            blob[off:off + 3],
            target,
            "medium",
            "standard long zero-branch candidate",
        )

    # Xtensa J encodes an 18-bit word offset in a 3-byte core24 instruction.
    # Restrict to high nibbles seen in known J encodings; this deliberately
    # excludes 0x16/0x56 zero branches above.
    if off + 3 <= len(blob) and (b0 & 0x0F) == 0x06 and (b0 >> 4) in (0x0, 0x4, 0x8, 0xC):
        imm18 = (b0 >> 4) << 16 | blob[off + 1] | (blob[off + 2] << 8)
        target = ((addr + 4) & ~3) + (sign_extend(imm18, 18) << 2)
        return ControlCandidate(addr, "j", blob[off:off + 3], target, "medium", "standard J candidate")

    return None


def anchor_distance(target: int | None, anchor_addrs: list[int]) -> int | None:
    if target is None or not anchor_addrs:
        return None
    return min(abs(target - addr) for addr in anchor_addrs)


def control_candidate_score(
    candidate: ControlCandidate,
    sections: list[A.Section],
    props: list[A.XtProp],
    anchor_addrs: list[int],
) -> tuple[int, list[str]]:
    score = 0
    reasons: list[str] = []
    if candidate.kind == "callx8":
        return 10, ["indirect_call_no_static_target"]
    if candidate.target is None:
        return 0, []
    if text_contains(sections, candidate.target):
        score += 8
        reasons.append("target_in_text")
    region = region_for_addr(candidate.target)
    if region:
        score += 6
        reasons.append("target_in_%s" % region)
    prop = best_prop_for_addr(props, candidate.target)
    if prop:
        score += 4
        reasons.append("target_has_prop:%s:%s" % (hx(prop.size), prop.flags))
        if "branch_target" in prop.flags or "loop_target" in prop.flags:
            score += 8
            reasons.append("target_prop_control")
        if "insn" in prop.flags:
            score += 3
            reasons.append("target_prop_insn")
    dist = anchor_distance(candidate.target, anchor_addrs)
    if dist is not None and dist <= 1:
        score += 10
        reasons.append("target_at_or_adjacent_anchor:%d" % dist)
    elif dist is not None and dist <= 8:
        score += 4
        reasons.append("target_near_anchor:%d" % dist)
    if candidate.confidence == "high":
        score += 3
    return score, reasons


def collect_anchors(data: bytes, sections: list[A.Section]) -> list[Anchor]:
    anchors: list[Anchor] = []
    for name, base, count in DISPATCH_TABLES:
        for i in range(count):
            anchors.append(Anchor(name, i, read_u32(data, sections, base + i * 4), "dispatch"))
    for i, (name, addr) in enumerate(MANUAL_ENTRIES):
        anchors.append(Anchor(name, i, addr, "manual"))
    return anchors


def table_targets(data: bytes, sections: list[A.Section], table: tuple[str, int, int]) -> list[int]:
    _name, base, count = table
    return [read_u32(data, sections, base + i * 4) for i in range(count)]


def write_csv(path: Path, rows: list[dict[str, object]], fieldnames: list[str]) -> None:
    with path.open("w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def build_anchor_rows(
    data: bytes,
    sections: list[A.Section],
    props: list[A.XtProp],
    funcs: list[A.FunctionCandidate],
    anchors: list[Anchor],
) -> list[dict[str, object]]:
    by_addr = defaultdict(list)
    for anchor in anchors:
        by_addr[anchor.addr].append(anchor)
    sorted_addrs = sorted(by_addr)
    next_by_addr = {
        addr: (sorted_addrs[i + 1] - addr if i + 1 < len(sorted_addrs) else None)
        for i, addr in enumerate(sorted_addrs)
    }
    rows: list[dict[str, object]] = []
    for anchor in sorted(anchors, key=lambda a: (a.addr, a.group, a.index or -1)):
        prop = prop_for_addr(props, anchor.addr)
        owner, delta = A.owner_for_addr(funcs, anchor.addr)
        rows.append(
            {
                "group": anchor.group,
                "index": "" if anchor.index is None else anchor.index,
                "addr": hx(anchor.addr),
                "source": anchor.source,
                "region": region_for_addr(anchor.addr),
                "owner": hx(owner),
                "owner_delta": hx(delta),
                "prop_addr": hx(prop.addr if prop else None),
                "prop_size": hx(prop.size if prop else None),
                "prop_flags": prop.flags if prop else "",
                "next_anchor_delta": hx(next_by_addr[anchor.addr]),
                "bytes16": bytes_hex(read_va(data, sections, anchor.addr, 16)),
                "bytes32": bytes_hex(read_va(data, sections, anchor.addr, 32)),
            }
        )
    return rows


def build_stride_rows(data: bytes, sections: list[A.Section]) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    for table in DISPATCH_TABLES:
        name, base, _count = table
        targets = table_targets(data, sections, table)
        for i, addr in enumerate(targets):
            prev_delta = None if i == 0 else addr - targets[i - 1]
            next_delta = None if i + 1 == len(targets) else targets[i + 1] - addr
            rows.append(
                {
                    "table": name,
                    "index": i,
                    "slot_addr": hx(base + i * 4),
                    "target": hx(addr),
                    "target_region": region_for_addr(addr),
                    "prev_delta": hx(prev_delta),
                    "next_delta": hx(next_delta),
                    "mod8": addr % 8,
                    "mod16": addr % 16,
                }
            )
    return rows


def build_entropy_rows(data: bytes, sections: list[A.Section]) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    for table in DISPATCH_TABLES:
        name, _base, _count = table
        targets = table_targets(data, sections, table)
        samples = [read_va(data, sections, addr, 32) for addr in targets]
        samples = [s for s in samples if len(s) == 32]
        mask, fixed_positions = byte_mask(samples)
        for off in range(32):
            vals = [s[off] for s in samples]
            counts = Counter(vals)
            rows.append(
                {
                    "table": name,
                    "offset": off,
                    "unique_values": len(counts),
                    "entropy": "%.4f" % shannon(vals),
                    "most_common": " ".join("%02x:%d" % (v, n) for v, n in counts.most_common(5)),
                    "fixed": 1 if off in fixed_positions else 0,
                }
            )
        rows.append(
            {
                "table": name,
                "offset": "mask32",
                "unique_values": "",
                "entropy": "",
                "most_common": mask,
                "fixed": len(fixed_positions),
            }
        )
    return rows


def build_phase_rows(data: bytes, sections: list[A.Section], anchors: list[Anchor]) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    anchors_by_region: dict[str, list[int]] = defaultdict(list)
    for anchor in anchors:
        r = region_for_addr(anchor.addr)
        if r:
            anchors_by_region[r].append(anchor.addr)
    for region_name, start, end in DENSE_REGIONS:
        blob = read_va(data, sections, start, end - start)
        if not blob:
            continue
        region_anchors = anchors_by_region.get(region_name, [])
        for width in (8, 16):
            for phase in range(width):
                base = start + phase
                if base >= end:
                    continue
                chunks = []
                o = phase
                while o + width <= len(blob):
                    chunks.append(blob[o:o + width])
                    o += width
                if not chunks:
                    continue
                counts = Counter(chunks)
                anchors_on = sum(1 for a in region_anchors if (a - base) % width == 0)
                anchors_near = sum(1 for a in region_anchors if min((a - base) % width, width - ((a - base) % width)) <= 1)
                duplicate_chunks = sum(1 for _chunk, n in counts.items() if n > 1)
                duplicate_instances = sum(n for _chunk, n in counts.items() if n > 1)
                zeroish = sum(1 for c in chunks if c.count(0) >= width - 1)
                score = (
                    anchors_on * 10
                    + anchors_near * 2
                    + duplicate_instances
                    + duplicate_chunks * 3
                    - zeroish
                )
                rows.append(
                    {
                        "region": region_name,
                        "region_start": hx(start),
                        "width": width,
                        "phase": phase,
                        "base": hx(base),
                        "chunks": len(chunks),
                        "unique_chunks": len(counts),
                        "duplicate_chunks": duplicate_chunks,
                        "duplicate_instances": duplicate_instances,
                        "zeroish_chunks": zeroish,
                        "anchors_total": len(region_anchors),
                        "anchors_on_boundary": anchors_on,
                        "anchors_near_boundary": anchors_near,
                        "score": score,
                    }
                )
    return rows


def build_repeat_rows(data: bytes, sections: list[A.Section]) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    for region_name, start, end in DENSE_REGIONS:
        blob = read_va(data, sections, start, end - start)
        for width in (8, 16):
            counts: Counter[bytes] = Counter()
            addrs: dict[bytes, list[int]] = defaultdict(list)
            for off in range(0, max(0, len(blob) - width + 1)):
                chunk = blob[off:off + width]
                counts[chunk] += 1
                if len(addrs[chunk]) < 12:
                    addrs[chunk].append(start + off)
            for chunk, count in counts.most_common(80):
                if count < 3:
                    break
                rows.append(
                    {
                        "region": region_name,
                        "width": width,
                        "count": count,
                        "sha1_8": hashlib.sha1(chunk).hexdigest()[:8],
                        "chunk": bytes_hex(chunk),
                        "sample_addrs": " ".join(hx(a) for a in addrs[chunk]),
                    }
                )
    return rows


def build_control_flow_rows(
    data: bytes,
    sections: list[A.Section],
    props: list[A.XtProp],
    anchors: list[Anchor],
) -> tuple[list[dict[str, object]], list[dict[str, object]]]:
    rows: list[dict[str, object]] = []
    votes: dict[int, dict[str, object]] = {}
    anchor_addrs = sorted({anchor.addr for anchor in anchors})

    for region_name, start, end in DENSE_REGIONS:
        blob = read_va(data, sections, start, end - start)
        for off in range(0, max(0, len(blob) - 1)):
            addr = start + off
            candidate = decode_standard_control_candidate(blob, addr, off)
            if candidate is None:
                continue
            score, reasons = control_candidate_score(candidate, sections, props, anchor_addrs)
            target_prop = best_prop_for_addr(props, candidate.target) if candidate.target is not None else None
            target_region = region_for_addr(candidate.target) if candidate.target is not None else ""
            dist = anchor_distance(candidate.target, anchor_addrs)
            rows.append(
                {
                    "source_region": region_name,
                    "source": hx(candidate.source),
                    "kind": candidate.kind,
                    "raw": candidate.raw.hex(),
                    "target": hx(candidate.target),
                    "target_region": target_region,
                    "target_prop_addr": hx(target_prop.addr if target_prop else None),
                    "target_prop_size": hx(target_prop.size if target_prop else None),
                    "target_prop_flags": target_prop.flags if target_prop else "",
                    "nearest_anchor_distance": "" if dist is None else dist,
                    "score": score,
                    "confidence": candidate.confidence,
                    "reasons": ";".join(reasons),
                    "note": candidate.note,
                }
            )
            if candidate.target is None:
                continue
            vote = votes.setdefault(
                candidate.target,
                {
                    "target": hx(candidate.target),
                    "target_region": target_region,
                    "target_prop_addr": hx(target_prop.addr if target_prop else None),
                    "target_prop_size": hx(target_prop.size if target_prop else None),
                    "target_prop_flags": target_prop.flags if target_prop else "",
                    "nearest_anchor_distance": "" if dist is None else dist,
                    "actionable": 1 if target_region or (dist is not None and dist <= 8) else 0,
                    "vote_count": 0,
                    "score_sum": 0,
                    "kinds": Counter(),
                    "source_samples": [],
                },
            )
            vote["vote_count"] = int(vote["vote_count"]) + 1
            vote["score_sum"] = int(vote["score_sum"]) + score
            vote["kinds"][candidate.kind] += 1
            samples = vote["source_samples"]
            if isinstance(samples, list) and len(samples) < 12:
                samples.append("%s:%s:%s" % (hx(candidate.source), candidate.kind, candidate.raw.hex()))

    rows.sort(key=lambda row: (int(row["score"]), row["source"]), reverse=True)
    vote_rows: list[dict[str, object]] = []
    for vote in votes.values():
        kinds = vote["kinds"]
        samples = vote["source_samples"]
        vote_rows.append(
            {
                "target": vote["target"],
                "target_region": vote["target_region"],
                "target_prop_addr": vote["target_prop_addr"],
                "target_prop_size": vote["target_prop_size"],
                "target_prop_flags": vote["target_prop_flags"],
                "nearest_anchor_distance": vote["nearest_anchor_distance"],
                "actionable": vote["actionable"],
                "vote_count": vote["vote_count"],
                "score_sum": vote["score_sum"],
                "kinds": " ".join("%s:%d" % (k, n) for k, n in kinds.most_common()),
                "source_samples": " ".join(samples),
            }
        )
    vote_rows.sort(key=lambda row: (int(row["score_sum"]), int(row["vote_count"])), reverse=True)
    return rows, vote_rows


PCREL_USABLE_CLASSES = {
    "strong_dense_control_target",
    "anchor_adjacent_landing",
    "near_anchor_landing",
    "dense_insn_target",
}


def classify_pcrel_slot_candidate(row: dict[str, object], raw_count: int) -> tuple[str, str]:
    flags = str(row["target_prop_flags"])
    target_region = str(row["target_region"])
    nearest = obj_int(row, "nearest_anchor_distance", 10**9)
    score = obj_int(row, "score")
    raw = str(row["raw"])
    if target_region and ("branch_target" in flags or "loop_target" in flags) and score >= 29:
        return "strong_dense_control_target", "dense target has explicit control .xt.prop"
    if target_region and nearest <= 1:
        return "anchor_adjacent_landing", "target lands at or next to a dispatch/manual anchor"
    if target_region and nearest <= 8:
        return "near_anchor_landing", "target lands near a dispatch/manual anchor"
    if target_region and "insn" in flags and score >= 20:
        return "dense_insn_target", "target remains inside dense FLIX with insn .xt.prop"
    if raw_count >= 8 and not target_region:
        return "reject_repeated_out_of_region", "same PC-relative-looking bytes repeatedly target outside dense FLIX"
    if raw == "060402" and target_region:
        return "repeated_060402_dense_target", "common FLIX byte pattern that sometimes lands on dense targets"
    return "weak_or_unusable", "insufficient target/anchor/property evidence"


def build_pcrel_slot_candidate_rows(control_rows: list[dict[str, object]]) -> list[dict[str, object]]:
    j_rows = [row for row in control_rows if row["kind"] == "j"]
    raw_counts = Counter(str(row["raw"]) for row in j_rows)
    rows: list[dict[str, object]] = []
    for row in j_rows:
        klass, note = classify_pcrel_slot_candidate(row, raw_counts[str(row["raw"])])
        operand = decode_pcrel_operand_raw(str(row["raw"]))
        source = parse_hex(row["source"])
        target = parse_hex(row["target"])
        normalized_delta = None
        if source is not None and target is not None:
            normalized_delta = target - ((source + 4) & ~3)
        rows.append(
            {
                "source_region": row["source_region"],
                "source": row["source"],
                "raw": row["raw"],
                "raw_count": raw_counts[str(row["raw"])],
                "opcode_low": hx(operand["opcode_low"]),
                "opcode_high": hx(operand["opcode_high"]),
                "imm18": hx(operand["imm18"]),
                "signed_words": operand["signed_words"],
                "decoded_delta": hx(operand["decoded_delta"]),
                "normalized_delta": hx(normalized_delta),
                "delta_match": int(normalized_delta == operand["decoded_delta"]),
                "target": row["target"],
                "target_region": row["target_region"],
                "target_prop_size": row["target_prop_size"],
                "target_prop_flags": row["target_prop_flags"],
                "nearest_anchor_distance": row["nearest_anchor_distance"],
                "score": row["score"],
                "pcrel_class": klass,
                "note": note,
                "reasons": row["reasons"],
            }
        )
    rows.sort(
        key=lambda row: (
            {
                "strong_dense_control_target": 0,
                "anchor_adjacent_landing": 1,
                "near_anchor_landing": 2,
                "dense_insn_target": 3,
                "repeated_060402_dense_target": 4,
                "reject_repeated_out_of_region": 5,
                "weak_or_unusable": 6,
            }.get(str(row["pcrel_class"]), 9),
            -obj_int(row, "score"),
            str(row["source"]),
        )
    )
    return rows


def build_internal_control_boundary_rows(
    pcrel_rows: list[dict[str, object]],
    profile: dict[str, int] | None = None,
) -> list[dict[str, object]]:
    profile = profile or {}
    min_score = profile.get("internal_pcrel_min_score")
    if min_score is None:
        return []
    allow_dense_insn = bool(profile.get("internal_pcrel_dense_insn", 0))
    grouped: dict[str, dict[str, object]] = {}
    for row in pcrel_rows:
        pcrel_class = str(row["pcrel_class"])
        if pcrel_class == "strong_dense_control_target":
            confidence = "strong"
        elif pcrel_class == "dense_insn_target" and allow_dense_insn:
            confidence = "medium"
        else:
            continue
        if obj_int(row, "score") < min_score:
            continue
        target = str(row["target"])
        item = grouped.setdefault(
            target,
            {
                "target": target,
                "target_region": row["target_region"],
                "target_prop_size": row["target_prop_size"],
                "target_prop_flags": row["target_prop_flags"],
                "pcrel_class": pcrel_class,
                "confidence": confidence,
                "vote_count": 0,
                "score_sum": 0,
                "raw_counts": Counter(),
                "source_samples": [],
                "source_min": row["source"],
                "source_max": row["source"],
            },
        )
        item["vote_count"] = int(item["vote_count"]) + 1
        item["score_sum"] = int(item["score_sum"]) + obj_int(row, "score")
        item["raw_counts"][str(row["raw"])] += 1
        samples = item["source_samples"]
        if isinstance(samples, list) and len(samples) < 12:
            samples.append("%s:%s" % (row["source"], row["raw"]))
        source = parse_hex(row["source"])
        source_min = parse_hex(item["source_min"])
        source_max = parse_hex(item["source_max"])
        if source is not None and source_min is not None and source < source_min:
            item["source_min"] = row["source"]
        if source is not None and source_max is not None and source > source_max:
            item["source_max"] = row["source"]

    rows: list[dict[str, object]] = []
    for item in grouped.values():
        raw_counts = item["raw_counts"]
        samples = item["source_samples"]
        rows.append(
            {
                "target": item["target"],
                "target_region": item["target_region"],
                "target_prop_size": item["target_prop_size"],
                "target_prop_flags": item["target_prop_flags"],
                "pcrel_class": item["pcrel_class"],
                "confidence": item["confidence"],
                "vote_count": item["vote_count"],
                "score_sum": item["score_sum"],
                "source_min": item["source_min"],
                "source_max": item["source_max"],
                "raw_counts": " ".join("%s:%d" % (k, v) for k, v in raw_counts.most_common()),
                "source_samples": " ".join(samples),
            }
        )
    rows.sort(
        key=lambda row: (
            {"strong": 0, "medium": 1}.get(str(row["confidence"]), 9),
            -obj_int(row, "score_sum"),
            row["target"],
        )
    )
    return rows


def build_pcrel_slot_template_rows(
    pcrel_rows: list[dict[str, object]],
    internal_boundary_rows: list[dict[str, object]],
    profile: dict[str, int] | None = None,
) -> list[dict[str, object]]:
    profile = profile or {}
    min_usable = profile.get("slot_template_min_usable")
    min_internal = profile.get("slot_template_min_internal", min_usable or 0)
    internal_targets = {row["target"] for row in internal_boundary_rows}
    by_raw: dict[str, list[dict[str, object]]] = defaultdict(list)
    for row in pcrel_rows:
        by_raw[str(row["raw"])].append(row)

    rows: list[dict[str, object]] = []
    for raw, items in by_raw.items():
        normalized_deltas = Counter()
        source_mod8 = Counter()
        target_mod8 = Counter()
        classes = Counter()
        regions = Counter()
        prop_groups = Counter()
        source_samples = []
        usable_count = 0
        internal_count = 0
        reject_count = 0
        for item in items:
            source = parse_hex(item["source"])
            target = parse_hex(item["target"])
            if source is not None and target is not None:
                normalized_deltas[target - ((source + 4) & ~3)] += 1
                source_mod8[source % 8] += 1
                target_mod8[target % 8] += 1
            klass = str(item["pcrel_class"])
            classes[klass] += 1
            regions[str(item["target_region"]) or "none"] += 1
            prop_groups["%s:%s" % (item["target_prop_size"], item["target_prop_flags"])] += 1
            if klass in PCREL_USABLE_CLASSES:
                usable_count += 1
            if item["target"] in internal_targets:
                internal_count += 1
            if klass == "reject_repeated_out_of_region":
                reject_count += 1
            if len(source_samples) < 12:
                source_samples.append("%s->%s" % (item["source"], item["target"]))

        normalized_unique = len(normalized_deltas)
        dominant_delta, dominant_delta_count = normalized_deltas.most_common(1)[0] if normalized_deltas else (None, 0)
        accepted = 0
        status = "observed_only"
        evidence: list[str] = []
        if normalized_unique == 1:
            evidence.append("single_normalized_delta")
        else:
            evidence.append("mixed_normalized_delta")
        if reject_count:
            evidence.append("reject_count_%d" % reject_count)
        if min_usable is not None:
            if normalized_unique == 1 and usable_count >= min_usable and internal_count >= min_internal:
                accepted = 1
                status = "accepted_slot_template"
                evidence.append("passes_profile_thresholds")
            elif normalized_unique != 1:
                status = "rejected_mixed_delta"
            elif usable_count < min_usable:
                status = "rejected_low_usable"
            else:
                status = "rejected_low_internal"

        rows.append(
            {
                "raw": raw,
                "row_count": len(items),
                "usable_count": usable_count,
                "internal_count": internal_count,
                "reject_count": reject_count,
                "normalized_delta_count": normalized_unique,
                "dominant_normalized_delta": hx(dominant_delta),
                "dominant_normalized_delta_count": dominant_delta_count,
                "source_mod8": " ".join("%d:%d" % (k, v) for k, v in sorted(source_mod8.items())),
                "target_mod8": " ".join("%d:%d" % (k, v) for k, v in sorted(target_mod8.items())),
                "pcrel_classes": " ".join("%s:%d" % (k, v) for k, v in classes.most_common()),
                "target_regions": " ".join("%s:%d" % (k, v) for k, v in regions.most_common()),
                "target_prop_groups": " ".join("%s:%d" % (k, v) for k, v in prop_groups.most_common(8)),
                "accepted": accepted,
                "status": status,
                "evidence": ";".join(evidence),
                "source_samples": " ".join(source_samples),
            }
        )
    rows.sort(
        key=lambda row: (
            -obj_int(row, "accepted"),
            -obj_int(row, "internal_count"),
            -obj_int(row, "usable_count"),
            -obj_int(row, "row_count"),
            row["raw"],
        )
    )
    return rows


def build_pcrel_operand_model_rows(
    pcrel_rows: list[dict[str, object]],
    internal_boundary_rows: list[dict[str, object]],
    profile: dict[str, int] | None = None,
) -> list[dict[str, object]]:
    profile = profile or {}
    min_usable = profile.get("operand_model_min_usable")
    min_internal = profile.get("operand_model_min_internal", min_usable or 0)
    max_reject_bps = profile.get("operand_model_max_reject_bps", 10000)
    min_negative = profile.get("operand_model_min_negative", 0)
    internal_targets = {row["target"] for row in internal_boundary_rows}
    grouped: dict[tuple[str, str], list[dict[str, object]]] = defaultdict(list)
    for row in pcrel_rows:
        grouped[(str(row["opcode_low"]), str(row["opcode_high"]))].append(row)

    rows: list[dict[str, object]] = []
    for (opcode_low, opcode_high), items in grouped.items():
        classes = Counter(str(item["pcrel_class"]) for item in items)
        regions = Counter(str(item["target_region"]) or "none" for item in items)
        raws = Counter(str(item["raw"]) for item in items)
        usable_count = sum(1 for item in items if item["pcrel_class"] in PCREL_USABLE_CLASSES)
        internal_count = sum(1 for item in items if item["target"] in internal_targets)
        reject_count = classes.get("reject_repeated_out_of_region", 0)
        weak_count = classes.get("weak_or_unusable", 0)
        delta_match_count = sum(obj_int(item, "delta_match") for item in items)
        delta_mismatch_count = len(items) - delta_match_count
        signed_negative_count = sum(1 for item in items if obj_int(item, "signed_words") < 0)
        reject_bps = (10000 * reject_count) // max(1, len(items))

        accepted = 0
        status = "observed_only"
        evidence: list[str] = []
        if delta_mismatch_count == 0:
            evidence.append("all_model_delta_match")
        else:
            evidence.append("delta_mismatch_%d" % delta_mismatch_count)
        if reject_count:
            evidence.append("reject_count_%d" % reject_count)
        if min_usable is not None:
            if delta_mismatch_count:
                status = "rejected_delta_mismatch"
            elif min_negative and reject_count >= min_negative and usable_count == 0:
                status = "rejected_negative_operand_family"
                evidence.append("negative_guard")
            elif usable_count < min_usable:
                status = "rejected_low_usable"
            elif internal_count < min_internal:
                status = "rejected_low_internal"
            elif reject_bps > max_reject_bps:
                status = "rejected_high_reject_ratio"
            else:
                accepted = 1
                status = "accepted_operand_model"
                evidence.append("passes_profile_thresholds")

        samples = [
            "%s:%s->%s:%s" % (
                item["source"],
                item["raw"],
                item["target"],
                item["decoded_delta"],
            )
            for item in items[:12]
        ]
        rows.append(
            {
                "opcode_low": opcode_low,
                "opcode_high": opcode_high,
                "row_count": len(items),
                "raw_variants": len(raws),
                "usable_count": usable_count,
                "internal_count": internal_count,
                "reject_count": reject_count,
                "weak_count": weak_count,
                "reject_ratio_bps": reject_bps,
                "delta_match_count": delta_match_count,
                "delta_mismatch_count": delta_mismatch_count,
                "signed_negative_count": signed_negative_count,
                "target_regions": " ".join("%s:%d" % (k, v) for k, v in regions.most_common()),
                "pcrel_classes": " ".join("%s:%d" % (k, v) for k, v in classes.most_common()),
                "dominant_raws": " ".join("%s:%d" % (k, v) for k, v in raws.most_common(8)),
                "accepted": accepted,
                "status": status,
                "evidence": ";".join(evidence),
                "source_samples": " ".join(samples),
            }
        )
    rows.sort(
        key=lambda row: (
            -obj_int(row, "accepted"),
            -obj_int(row, "internal_count"),
            -obj_int(row, "usable_count"),
            obj_int(row, "reject_ratio_bps"),
            row["opcode_low"],
            row["opcode_high"],
        )
    )
    return rows


def build_pcrel_cfg_edge_rows(
    pcrel_rows: list[dict[str, object]],
    operand_model_rows: list[dict[str, object]],
    props: list[A.XtProp],
    profile: dict[str, int] | None = None,
) -> list[dict[str, object]]:
    profile = profile or {}
    min_score = profile.get("cfg_edge_min_score")
    if min_score is None:
        return []
    allow_dense_insn = bool(profile.get("cfg_edge_allow_dense_insn", 0))
    allow_anchor_landing = bool(profile.get("cfg_edge_allow_anchor_landing", 0))
    accepted_models = {
        (str(row["opcode_low"]), str(row["opcode_high"]))
        for row in operand_model_rows
        if obj_int(row, "accepted")
    }
    rows: list[dict[str, object]] = []
    for row in pcrel_rows:
        model_key = (str(row["opcode_low"]), str(row["opcode_high"]))
        if model_key not in accepted_models:
            continue
        if not str(row["target_region"]):
            continue
        pcrel_class = str(row["pcrel_class"])
        if pcrel_class == "strong_dense_control_target":
            edge_confidence = "strong"
        elif pcrel_class == "dense_insn_target" and allow_dense_insn:
            edge_confidence = "medium"
        elif pcrel_class in {"anchor_adjacent_landing", "near_anchor_landing"} and allow_anchor_landing:
            edge_confidence = "landing"
        else:
            continue
        if obj_int(row, "score") < min_score:
            continue
        source = parse_hex(row["source"])
        target = parse_hex(row["target"])
        if source is None or target is None:
            continue
        source_prop = best_prop_for_addr(props, source)
        rows.append(
            {
                "source": row["source"],
                "target": row["target"],
                "source_region": row["source_region"],
                "target_region": row["target_region"],
                "raw": row["raw"],
                "opcode_low": row["opcode_low"],
                "opcode_high": row["opcode_high"],
                "decoded_delta": row["decoded_delta"],
                "normalized_delta": row["normalized_delta"],
                "delta_match": row["delta_match"],
                "direction": "forward" if target > source else ("backward" if target < source else "self"),
                "span": hx(target - source),
                "source_mod8": source % 8,
                "target_mod8": target % 8,
                "pcrel_class": pcrel_class,
                "edge_confidence": edge_confidence,
                "score": row["score"],
                "source_prop_addr": hx(source_prop.addr if source_prop else None),
                "source_prop_size": hx(source_prop.size if source_prop else None),
                "source_prop_flags": source_prop.flags if source_prop else "",
                "target_prop_size": row["target_prop_size"],
                "target_prop_flags": row["target_prop_flags"],
                "nearest_anchor_distance": row["nearest_anchor_distance"],
            }
        )
    rows.sort(key=lambda item: (item["source_region"], parse_hex(item["source"]) or 0, parse_hex(item["target"]) or 0))
    return rows


def build_pcrel_cfg_node_rows(
    cfg_edge_rows: list[dict[str, object]],
    anchors: list[Anchor],
) -> list[dict[str, object]]:
    anchor_addrs = sorted({anchor.addr for anchor in anchors})
    grouped: dict[str, dict[str, object]] = {}
    for edge in cfg_edge_rows:
        target = str(edge["target"])
        item = grouped.setdefault(
            target,
            {
                "target": target,
                "target_region": edge["target_region"],
                "target_prop_size": edge["target_prop_size"],
                "target_prop_flags": edge["target_prop_flags"],
                "incoming_count": 0,
                "score_sum": 0,
                "classes": Counter(),
                "confidences": Counter(),
                "raws": Counter(),
                "source_min": edge["source"],
                "source_max": edge["source"],
                "source_samples": [],
            },
        )
        item["incoming_count"] = int(item["incoming_count"]) + 1
        item["score_sum"] = int(item["score_sum"]) + obj_int(edge, "score")
        item["classes"][str(edge["pcrel_class"])] += 1
        item["confidences"][str(edge["edge_confidence"])] += 1
        item["raws"][str(edge["raw"])] += 1
        samples = item["source_samples"]
        if isinstance(samples, list) and len(samples) < 12:
            samples.append("%s:%s:%s" % (edge["source"], edge["raw"], edge["edge_confidence"]))
        source = parse_hex(edge["source"])
        source_min = parse_hex(item["source_min"])
        source_max = parse_hex(item["source_max"])
        if source is not None and source_min is not None and source < source_min:
            item["source_min"] = edge["source"]
        if source is not None and source_max is not None and source > source_max:
            item["source_max"] = edge["source"]

    rows: list[dict[str, object]] = []
    for item in grouped.values():
        target_addr = parse_hex(item["target"])
        nearest = anchor_distance(target_addr, anchor_addrs)
        confidences = item["confidences"]
        if confidences.get("strong", 0):
            node_confidence = "strong"
        elif confidences.get("medium", 0):
            node_confidence = "medium"
        elif confidences.get("landing", 0):
            node_confidence = "landing"
        else:
            node_confidence = "weak"
        rows.append(
            {
                "target": item["target"],
                "target_region": item["target_region"],
                "target_prop_size": item["target_prop_size"],
                "target_prop_flags": item["target_prop_flags"],
                "node_confidence": node_confidence,
                "incoming_count": item["incoming_count"],
                "score_sum": item["score_sum"],
                "nearest_anchor_distance": "" if nearest is None else nearest,
                "source_min": item["source_min"],
                "source_max": item["source_max"],
                "pcrel_classes": " ".join("%s:%d" % (k, v) for k, v in item["classes"].most_common()),
                "edge_confidences": " ".join("%s:%d" % (k, v) for k, v in confidences.most_common()),
                "raw_counts": " ".join("%s:%d" % (k, v) for k, v in item["raws"].most_common()),
                "source_samples": " ".join(item["source_samples"]),
            }
        )
    rows.sort(
        key=lambda row: (
            {"strong": 0, "medium": 1, "landing": 2}.get(str(row["node_confidence"]), 9),
            -obj_int(row, "incoming_count"),
            row["target"],
        )
    )
    return rows


def build_pcrel_cfg_cluster_rows(
    cfg_node_rows: list[dict[str, object]],
    cfg_edge_rows: list[dict[str, object]],
    profile: dict[str, int] | None = None,
) -> list[dict[str, object]]:
    profile = profile or {}
    min_nodes = profile.get("cfg_cluster_min_nodes")
    if min_nodes is None:
        return []
    edges_by_target = Counter(str(edge["target"]) for edge in cfg_edge_rows)
    grouped: dict[tuple[str, str, str], dict[str, object]] = {}
    for node in cfg_node_rows:
        key = (
            str(node["target_region"]),
            str(node["target_prop_size"]),
            str(node["target_prop_flags"]),
        )
        item = grouped.setdefault(
            key,
            {
                "target_region": key[0],
                "target_prop_size": key[1],
                "target_prop_flags": key[2],
                "node_count": 0,
                "edge_count": 0,
                "score_sum": 0,
                "confidence_counts": Counter(),
                "raw_counts": Counter(),
                "target_min": node["target"],
                "target_max": node["target"],
            },
        )
        item["node_count"] = int(item["node_count"]) + 1
        item["edge_count"] = int(item["edge_count"]) + edges_by_target[str(node["target"])]
        item["score_sum"] = int(item["score_sum"]) + obj_int(node, "score_sum")
        item["confidence_counts"][str(node["node_confidence"])] += 1
        for part in str(node["raw_counts"]).split():
            raw, _, count = part.partition(":")
            if raw and count:
                item["raw_counts"][raw] += int(count)
        target = parse_hex(node["target"])
        target_min = parse_hex(item["target_min"])
        target_max = parse_hex(item["target_max"])
        if target is not None and target_min is not None and target < target_min:
            item["target_min"] = node["target"]
        if target is not None and target_max is not None and target > target_max:
            item["target_max"] = node["target"]

    rows: list[dict[str, object]] = []
    for item in grouped.values():
        accepted = int(int(item["node_count"]) >= min_nodes)
        rows.append(
            {
                "target_region": item["target_region"],
                "target_prop_size": item["target_prop_size"],
                "target_prop_flags": item["target_prop_flags"],
                "node_count": item["node_count"],
                "edge_count": item["edge_count"],
                "score_sum": item["score_sum"],
                "target_min": item["target_min"],
                "target_max": item["target_max"],
                "accepted": accepted,
                "node_confidences": " ".join("%s:%d" % (k, v) for k, v in item["confidence_counts"].most_common()),
                "raw_counts": " ".join("%s:%d" % (k, v) for k, v in item["raw_counts"].most_common(8)),
            }
        )
    rows.sort(
        key=lambda row: (
            -obj_int(row, "accepted"),
            -obj_int(row, "node_count"),
            -obj_int(row, "edge_count"),
            row["target_region"],
            row["target_min"],
        )
    )
    return rows


def block_start_candidates(
    props: list[A.XtProp],
    anchors: list[Anchor],
    cfg_node_rows: list[dict[str, object]],
) -> dict[str, list[int]]:
    by_region: dict[str, set[int]] = defaultdict(set)
    for region_name, start, _end in DENSE_REGIONS:
        by_region[region_name].add(start)
    for anchor in anchors:
        region = region_for_addr(anchor.addr)
        if region:
            by_region[region].add(anchor.addr)
    for prop in props:
        region = region_for_addr(prop.addr)
        if region and prop.size > 0 and "insn" in A.flags_text(prop.flags):
            by_region[region].add(prop.addr)
    for node in cfg_node_rows:
        target = parse_hex(node["target"])
        region = str(node["target_region"])
        if target is not None and region:
            by_region[region].add(target)
    return {region: sorted(addrs) for region, addrs in by_region.items()}


def nearest_start_at_or_before(starts: list[int], addr: int) -> int | None:
    best = None
    for start in starts:
        if start <= addr:
            best = start
        else:
            break
    return best


def next_start_after(starts: list[int], addr: int) -> int | None:
    for start in starts:
        if start > addr:
            return start
    return None


def build_pcrel_basic_block_rows(
    cfg_edge_rows: list[dict[str, object]],
    cfg_node_rows: list[dict[str, object]],
    props: list[A.XtProp],
    anchors: list[Anchor],
    profile: dict[str, int] | None = None,
) -> tuple[list[dict[str, object]], list[dict[str, object]]]:
    profile = profile or {}
    if not profile.get("block_extent_enable"):
        return [], []
    include_entry_only = bool(profile.get("block_include_entry_only", 0))
    starts_by_region = block_start_candidates(props, anchors, cfg_node_rows)
    blocks: dict[tuple[str, int], dict[str, object]] = {}

    for edge in cfg_edge_rows:
        source = parse_hex(edge["source"])
        target = parse_hex(edge["target"])
        region = str(edge["source_region"])
        if source is None or target is None or not region:
            continue
        starts = starts_by_region.get(region, [])
        start = nearest_start_at_or_before(starts, source)
        if start is None:
            continue
        item = blocks.setdefault(
            (region, start),
            {
                "region": region,
                "block_start": start,
                "terminators": defaultdict(list),
                "edge_confidences": Counter(),
                "pcrel_classes": Counter(),
                "raws": Counter(),
                "edge_count": 0,
                "score_sum": 0,
            },
        )
        item["terminators"][source].append(edge)
        item["edge_confidences"][str(edge["edge_confidence"])] += 1
        item["pcrel_classes"][str(edge["pcrel_class"])] += 1
        item["raws"][str(edge["raw"])] += 1
        item["edge_count"] = int(item["edge_count"]) + 1
        item["score_sum"] = int(item["score_sum"]) + obj_int(edge, "score")

    if include_entry_only:
        for node in cfg_node_rows:
            target = parse_hex(node["target"])
            region = str(node["target_region"])
            if target is not None and region:
                blocks.setdefault(
                    (region, target),
                    {
                        "region": region,
                        "block_start": target,
                        "terminators": defaultdict(list),
                        "edge_confidences": Counter(),
                        "pcrel_classes": Counter(),
                        "raws": Counter(),
                        "edge_count": 0,
                        "score_sum": 0,
                    },
                )

    block_rows: list[dict[str, object]] = []
    edge_rows: list[dict[str, object]] = []
    for (region, start), item in blocks.items():
        starts = starts_by_region.get(region, [])
        region_end = next((end for name, _start, end in DENSE_REGIONS if name == region), None)
        next_start = next_start_after(starts, start)
        prop = best_prop_for_addr(props, start)
        prop_end = prop.addr + prop.size if prop and prop.size > 0 else None
        terminators: dict[int, list[dict[str, object]]] = item["terminators"]
        terminator_source = min(terminators) if terminators else None
        successors: list[str] = []
        edge_kinds: list[str] = []
        if terminator_source is not None:
            block_end = terminator_source + 3
            extent_status = "terminated_by_pcrel_source"
            for edge in terminators[terminator_source]:
                successors.append(str(edge["target"]))
                edge_kinds.append("pcrel:%s:%s" % (edge["raw"], edge["edge_confidence"]))
        elif next_start is not None:
            block_end = next_start
            extent_status = "fallthrough_to_next_start"
            successors.append(hx(next_start))
            edge_kinds.append("fallthrough")
        elif prop_end is not None:
            block_end = prop_end
            extent_status = "prop_run_end_only"
        elif region_end is not None:
            block_end = region_end
            extent_status = "region_end_open"
        else:
            block_end = start
            extent_status = "open"

        if next_start is not None and block_end > next_start:
            block_end = next_start
            extent_status += "|capped_at_next_start"
        if prop_end is not None and block_end > prop_end:
            block_end = prop_end
            extent_status += "|capped_at_prop_end"

        confidences: Counter = item["edge_confidences"]
        if confidences.get("strong", 0):
            block_confidence = "strong"
        elif confidences.get("medium", 0):
            block_confidence = "medium"
        elif confidences.get("landing", 0):
            block_confidence = "landing"
        elif extent_status == "fallthrough_to_next_start":
            block_confidence = "entry"
        else:
            block_confidence = "open"

        unique_successors = sorted(set(successors), key=lambda value: parse_hex(value) or 0)
        block_size = max(0, block_end - start)
        block_rows.append(
            {
                "region": region,
                "block_start": hx(start),
                "block_end": hx(block_end),
                "block_size": hx(block_size),
                "extent_status": extent_status,
                "block_confidence": block_confidence,
                "terminator_source": hx(terminator_source),
                "next_start": hx(next_start),
                "prop_addr": hx(prop.addr if prop else None),
                "prop_size": hx(prop.size if prop else None),
                "prop_end": hx(prop_end),
                "prop_flags": prop.flags if prop else "",
                "edge_count": item["edge_count"],
                "score_sum": item["score_sum"],
                "successor_count": len(unique_successors),
                "successors": " ".join(unique_successors),
                "edge_kinds": " ".join(edge_kinds),
                "edge_confidences": " ".join("%s:%d" % (k, v) for k, v in confidences.most_common()),
                "pcrel_classes": " ".join("%s:%d" % (k, v) for k, v in item["pcrel_classes"].most_common()),
                "raw_counts": " ".join("%s:%d" % (k, v) for k, v in item["raws"].most_common()),
            }
        )
        for successor in unique_successors:
            successor_addr = parse_hex(successor)
            successor_region = region_for_addr(successor_addr) if successor_addr is not None else ""
            edge_rows.append(
                {
                    "block_start": hx(start),
                    "block_end": hx(block_end),
                    "successor": successor,
                    "successor_region": successor_region,
                    "edge_kind": "fallthrough" if successor == hx(next_start) and terminator_source is None else "pcrel",
                    "terminator_source": hx(terminator_source),
                    "block_confidence": block_confidence,
                }
            )

    block_rows.sort(key=lambda row: (row["region"], parse_hex(row["block_start"]) or 0))
    edge_rows.sort(key=lambda row: (parse_hex(row["block_start"]) or 0, parse_hex(row["successor"]) or 0))
    return block_rows, edge_rows


def find_component(nodes: dict[str, str], node: str) -> str:
    parent = nodes.setdefault(node, node)
    if parent != node:
        nodes[node] = find_component(nodes, parent)
    return nodes[node]


def union_component(nodes: dict[str, str], a: str, b: str) -> None:
    root_a = find_component(nodes, a)
    root_b = find_component(nodes, b)
    if root_a != root_b:
        if (parse_hex(root_b) or 0) < (parse_hex(root_a) or 0):
            root_a, root_b = root_b, root_a
        nodes[root_b] = root_a


def build_block_component_rows(
    basic_block_rows: list[dict[str, object]],
    block_edge_rows: list[dict[str, object]],
    anchors: list[Anchor],
    profile: dict[str, int] | None = None,
) -> tuple[list[dict[str, object]], list[dict[str, object]]]:
    profile = profile or {}
    if not profile.get("component_enable"):
        return [], []

    start_to_block = {str(row["block_start"]): row for row in basic_block_rows}
    parent: dict[str, str] = {}
    for start in start_to_block:
        find_component(parent, start)
    for edge in block_edge_rows:
        source = str(edge["block_start"])
        successor = str(edge["successor"])
        find_component(parent, source)
        find_component(parent, successor)
        union_component(parent, source, successor)

    by_root: dict[str, set[str]] = defaultdict(set)
    for node in list(parent):
        by_root[find_component(parent, node)].add(node)

    edges_by_root: dict[str, list[dict[str, object]]] = defaultdict(list)
    outdeg = Counter(str(edge["block_start"]) for edge in block_edge_rows)
    indeg = Counter(str(edge["successor"]) for edge in block_edge_rows)
    for edge in block_edge_rows:
        edges_by_root[find_component(parent, str(edge["block_start"]))].append(edge)

    root_ids = {
        root: "comp_%03d" % i
        for i, root in enumerate(sorted(by_root, key=lambda value: parse_hex(value) or 0))
    }
    node_to_component = {
        node: root_ids[root]
        for root, nodes in by_root.items()
        for node in nodes
    }

    rows: list[dict[str, object]] = []
    for root, nodes in by_root.items():
        known = [node for node in nodes if node in start_to_block]
        unresolved = sorted(nodes - set(known), key=lambda value: parse_hex(value) or 0)
        blocks = [start_to_block[node] for node in known]
        starts = [parse_hex(node) for node in nodes if parse_hex(node) is not None]
        component_edges = edges_by_root[root]
        edge_kind_counts = Counter(str(edge["edge_kind"]) for edge in component_edges)
        region_counts = Counter(str(block["region"]) for block in blocks)
        confidence_counts = Counter(str(block["block_confidence"]) for block in blocks)
        status_counts = Counter(str(block["extent_status"]) for block in blocks)
        prop_counts = Counter(
            "%s:%s" % (block["prop_size"], block["prop_flags"])
            for block in blocks
        )
        entry_blocks = sorted(
            [node for node in known if indeg[node] == 0],
            key=lambda value: parse_hex(value) or 0,
        )
        terminal_blocks = sorted(
            [node for node in known if outdeg[node] == 0],
            key=lambda value: parse_hex(value) or 0,
        )
        all_degrees_linear = all(indeg[node] <= 1 and outdeg[node] <= 1 for node in nodes)
        component_kind = "linear_chain" if all_degrees_linear else "branch_or_join"
        rows.append(
            {
                "component_id": root_ids[root],
                "component_kind": component_kind,
                "region_counts": " ".join("%s:%d" % (k, v) for k, v in region_counts.most_common()),
                "node_count": len(nodes),
                "known_block_count": len(known),
                "unresolved_node_count": len(unresolved),
                "edge_count": len(component_edges),
                "pcrel_edge_count": edge_kind_counts.get("pcrel", 0),
                "fallthrough_edge_count": edge_kind_counts.get("fallthrough", 0),
                "entry_block_count": len(entry_blocks),
                "terminal_block_count": len(terminal_blocks),
                "addr_min": hx(min(starts) if starts else None),
                "addr_max": hx(max(starts) if starts else None),
                "entry_blocks": " ".join(entry_blocks[:16]),
                "terminal_blocks": " ".join(terminal_blocks[:16]),
                "unresolved_nodes": " ".join(unresolved[:16]),
                "block_confidences": " ".join("%s:%d" % (k, v) for k, v in confidence_counts.most_common()),
                "extent_statuses": " ".join("%s:%d" % (k, v) for k, v in status_counts.most_common()),
                "top_prop_groups": " ".join("%s:%d" % (k, v) for k, v in prop_counts.most_common(8)),
            }
        )
    rows.sort(key=lambda row: (parse_hex(row["addr_min"]) or 0, row["component_id"]))

    dispatch_rows: list[dict[str, object]] = []
    blocks_sorted = sorted(
        basic_block_rows,
        key=lambda row: parse_hex(row["block_start"]) or 0,
    )
    for anchor in anchors:
        if anchor.source != "dispatch" or anchor.index is None:
            continue
        addr = anchor.addr
        region = region_for_addr(addr)
        exact = start_to_block.get(hx(addr))
        inside = None
        if exact is None:
            for block in blocks_sorted:
                if block["region"] != region:
                    continue
                start = parse_hex(block["block_start"])
                end = parse_hex(block["block_end"])
                if start is not None and end is not None and start <= addr < end:
                    inside = block
                    break
        successor_component = node_to_component.get(hx(addr))
        nearest = None
        nearest_distance = None
        if exact is None and inside is None and successor_component is None and region:
            same_region = [block for block in blocks_sorted if block["region"] == region]
            for block in same_region:
                start = parse_hex(block["block_start"])
                if start is None:
                    continue
                dist = abs(addr - start)
                if nearest is None or nearest_distance is None or dist < nearest_distance:
                    nearest = block
                    nearest_distance = dist
        if exact is not None:
            relation = "exact_block_start"
            block = exact
            component_id = node_to_component.get(str(block["block_start"]), "")
            distance = 0
        elif inside is not None:
            relation = "inside_block"
            block = inside
            component_id = node_to_component.get(str(block["block_start"]), "")
            start = parse_hex(block["block_start"])
            distance = "" if start is None else addr - start
        elif successor_component is not None:
            relation = "successor_node"
            block = None
            component_id = successor_component
            distance = 0
        elif nearest is not None:
            relation = "nearest_block_start"
            block = nearest
            component_id = node_to_component.get(str(block["block_start"]), "")
            distance = nearest_distance
        else:
            relation = "unmapped"
            block = None
            component_id = ""
            distance = ""
        dispatch_rows.append(
            {
                "table": anchor.group,
                "index": anchor.index,
                "target": hx(addr),
                "target_region": region,
                "relation": relation,
                "component_id": component_id,
                "distance": distance,
                "block_start": "" if block is None else block["block_start"],
                "block_end": "" if block is None else block["block_end"],
                "block_confidence": "" if block is None else block["block_confidence"],
                "extent_status": "" if block is None else block["extent_status"],
            }
        )
    dispatch_rows.sort(key=lambda row: (row["table"], obj_int(row, "index")))
    return rows, dispatch_rows


def best_phase_alignment(
    addr: int,
    region: str,
    width: int,
    phase_rows: list[dict[str, object]],
) -> tuple[int | None, int | None, int | None]:
    rows = [r for r in phase_rows if r["region"] == region and int(r["width"]) == width]
    rows.sort(key=lambda r: int(r["score"]), reverse=True)
    best_rank = None
    best_score = None
    best_phase = None
    for rank, row in enumerate(rows, 1):
        base = parse_hex(row["base"])
        if base is None:
            continue
        if (addr - base) % width == 0:
            best_rank = rank
            best_score = int(row["score"])
            best_phase = int(row["phase"])
            break
    return best_rank, best_score, best_phase


def build_boundary_hypothesis_rows(
    data: bytes,
    sections: list[A.Section],
    props: list[A.XtProp],
    anchors: list[Anchor],
    stride_rows: list[dict[str, object]],
    phase_rows: list[dict[str, object]],
    control_vote_rows: list[dict[str, object]],
    pcrel_rows: list[dict[str, object]],
    profile: dict[str, int] | None = None,
) -> list[dict[str, object]]:
    profile = profile or {}
    phase8_bonus = profile.get("phase8_bonus", 2)
    phase16_bonus = profile.get("phase16_bonus", 2)
    abc_stride_bonus = profile.get("abc_stride_bonus", 4)
    d_stride_bonus = profile.get("d_stride_bonus", 4)
    repeat_bonus = profile.get("repeat_bonus", 2)
    control_exact_bonus = profile.get("control_exact_bonus", 3)
    control_near_bonus = profile.get("control_near_bonus", 2)
    c1_bonus = profile.get("c1_bonus", 2)
    pcrel_exact_landing_bonus = profile.get("pcrel_exact_landing_bonus", 0)
    pcrel_near_landing_bonus = profile.get("pcrel_near_landing_bonus", 0)
    landing_margin = profile.get("landing_margin", 2)
    bundle_margin = profile.get("bundle_margin", 2)
    template_threshold = profile.get("template_threshold", 4)
    stride_by_key = {
        (str(row["table"]), int(row["index"])): row
        for row in stride_rows
    }
    vote_by_target = {
        parse_hex(row["target"]): row
        for row in control_vote_rows
        if parse_hex(row["target"]) is not None
    }
    pcrel_by_target: dict[int, dict[str, object]] = {}
    for row in pcrel_rows:
        target = parse_hex(row["target"])
        if target is None:
            continue
        if row["pcrel_class"] not in PCREL_USABLE_CLASSES:
            continue
        current = pcrel_by_target.get(target)
        if current is None or obj_int(row, "score") > obj_int(current, "score"):
            pcrel_by_target[target] = row

    rows: list[dict[str, object]] = []
    for anchor in anchors:
        if anchor.source != "dispatch" or anchor.index is None:
            continue
        addr = anchor.addr
        prop = prop_for_addr(props, addr)
        best_prop = best_prop_for_addr(props, addr)
        stride = stride_by_key.get((anchor.group, anchor.index), {})
        vote = vote_by_target.get(addr)
        near_vote = None
        for delta in (-1, 1, -2, 2, -4, 4, -8, 8):
            near_vote = vote_by_target.get(addr + delta)
            if near_vote is not None:
                break
        pcrel_exact = pcrel_by_target.get(addr)
        pcrel_near = None
        for delta in (-1, 1, -2, 2, -4, 4, -8, 8):
            pcrel_near = pcrel_by_target.get(addr + delta)
            if pcrel_near is not None:
                break
        region = region_for_addr(addr)
        rank8, score8, phase8 = best_phase_alignment(addr, region, 8, phase_rows) if region else (None, None, None)
        rank16, score16, phase16 = best_phase_alignment(addr, region, 16, phase_rows) if region else (None, None, None)
        repeat8 = count_chunk_in_region(data, sections, addr, 8)
        repeat16 = count_chunk_in_region(data, sections, addr, 16)

        prop_flags = prop.flags if prop else ""
        prop_size = prop.size if prop else None
        prev_delta = parse_hex(stride.get("prev_delta"))
        next_delta = parse_hex(stride.get("next_delta"))
        bundle_score = 0
        landing_score = 0
        template_score = 0
        evidence: list[str] = []

        if prop_flags:
            evidence.append("prop=%s:%s" % (hx(prop_size), prop_flags))
        if best_prop and (not prop or best_prop.addr != prop.addr or best_prop.size != prop.size):
            evidence.append("best_prop=%s:%s:%s" % (hx(best_prop.addr), hx(best_prop.size), best_prop.flags))

        if "insn" in prop_flags and prop_size is not None and prop_size >= 8:
            bundle_score += 3
            evidence.append("explicit_insn_prop_ge_8")
        if prop_size == 0 and "unreachable" in prop_flags:
            landing_score += 3
            evidence.append("zero_size_unreachable_marker")
        if anchor.group in {"A", "B", "C"} and (prev_delta == -0xB or next_delta == -0xB):
            landing_score += abc_stride_bonus
            evidence.append("abc_minus_0xb_stride")
        if anchor.group == "D" and (prev_delta in {-0x68, -0x75, -0x76, -0x77} or next_delta in {-0x68, -0x75, -0x76, -0x77}):
            template_score += d_stride_bonus
            evidence.append("d_generated_stride_family")
        if rank8 is not None and rank8 <= 4:
            bundle_score += phase8_bonus
            evidence.append("aligns_top8_phase_rank_%d" % rank8)
        if rank16 is not None and rank16 <= 4:
            bundle_score += phase16_bonus
            evidence.append("aligns_top16_phase_rank_%d" % rank16)
        if repeat8 >= 3:
            template_score += repeat_bonus
            evidence.append("repeat8_count_%d" % repeat8)
        if repeat16 >= 3:
            template_score += repeat_bonus
            evidence.append("repeat16_count_%d" % repeat16)
        if vote is not None and int(vote["actionable"]):
            landing_score += control_exact_bonus
            evidence.append("control_vote_exact:%s:%s" % (vote["vote_count"], vote["kinds"]))
        if near_vote is not None and int(near_vote["actionable"]):
            landing_score += control_near_bonus
            evidence.append("control_vote_near:%s:%s" % (near_vote["target"], near_vote["kinds"]))
        if pcrel_exact is not None and pcrel_exact_landing_bonus:
            if anchor.group in {"A", "B", "C"} or (prop_size == 0 and "unreachable" in prop_flags):
                landing_score += pcrel_exact_landing_bonus
            elif anchor.group == "D":
                template_score += max(1, pcrel_exact_landing_bonus // 2)
            evidence.append("pcrel_slot_exact:%s:%s" % (pcrel_exact["source"], pcrel_exact["pcrel_class"]))
        if pcrel_near is not None and pcrel_near_landing_bonus:
            if anchor.group in {"A", "B", "C"} or (prop_size == 0 and "unreachable" in prop_flags):
                landing_score += pcrel_near_landing_bonus
            elif anchor.group == "D":
                template_score += max(1, pcrel_near_landing_bonus // 2)
            evidence.append("pcrel_slot_near:%s:%s:%s" % (
                pcrel_near["target"],
                pcrel_near["source"],
                pcrel_near["pcrel_class"],
            ))
        if anchor.group == "C" and anchor.index == 1:
            bundle_score += c1_bonus
            evidence.append("special_c1_control_anchor")

        if anchor.group == "D" and template_score >= template_threshold:
            hypothesis = "generated_template_entry"
        elif landing_score >= bundle_score + landing_margin:
            hypothesis = "likely_landing_pad_or_slot_target"
        elif bundle_score >= landing_score + bundle_margin:
            hypothesis = "candidate_bundle_start"
        else:
            hypothesis = "ambiguous_boundary"

        rows.append(
            {
                "table": anchor.group,
                "index": anchor.index,
                "addr": hx(addr),
                "region": region,
                "hypothesis": hypothesis,
                "bundle_score": bundle_score,
                "landing_score": landing_score,
                "template_score": template_score,
                "prev_delta": hx(prev_delta),
                "next_delta": hx(next_delta),
                "mod8": addr % 8,
                "mod16": addr % 16,
                "prop_addr": hx(prop.addr if prop else None),
                "prop_size": hx(prop_size),
                "prop_flags": prop_flags,
                "phase8_rank": "" if rank8 is None else rank8,
                "phase8_score": "" if score8 is None else score8,
                "phase8": "" if phase8 is None else phase8,
                "phase16_rank": "" if rank16 is None else rank16,
                "phase16_score": "" if score16 is None else score16,
                "phase16": "" if phase16 is None else phase16,
                "repeat8": repeat8,
                "repeat16": repeat16,
                "control_vote_exact": "" if vote is None else "%s:%s" % (vote["vote_count"], vote["kinds"]),
                "control_vote_near": "" if near_vote is None else "%s:%s:%s" % (near_vote["target"], near_vote["vote_count"], near_vote["kinds"]),
                "pcrel_slot_exact": "" if pcrel_exact is None or not pcrel_exact_landing_bonus else "%s:%s" % (pcrel_exact["source"], pcrel_exact["pcrel_class"]),
                "pcrel_slot_near": "" if pcrel_near is None or not pcrel_near_landing_bonus else "%s:%s:%s" % (
                    pcrel_near["target"],
                    pcrel_near["source"],
                    pcrel_near["pcrel_class"],
                ),
                "evidence": ";".join(evidence),
            }
        )
    rows.sort(key=lambda row: (row["table"], int(row["index"])))
    return rows


def family_name_for_stride(table: str, prev_delta: int | None, next_delta: int | None, addr: int) -> str:
    if table in {"A", "B", "C"} and (prev_delta == -0xB or next_delta == -0xB):
        return "%s_minus_0xb_landing_series" % table
    if table == "C" and addr == 0x700169A4:
        return "C_special_0x700169a4"
    if table == "D":
        for delta in (next_delta, prev_delta):
            if delta in {-0x68, -0x75, -0x76, -0x77}:
                return "D_stride_%s" % hx(delta).replace("-", "minus_").replace("0x", "0x")
        if addr == 0x70081EC5:
            return "D_shared_0x70081ec5"
    return "%s_misc" % table


def build_template_family_rows(
    data: bytes,
    sections: list[A.Section],
    stride_rows: list[dict[str, object]],
) -> list[dict[str, object]]:
    families: dict[str, list[int]] = defaultdict(list)
    for row in stride_rows:
        addr = parse_hex(row["target"])
        if addr is None:
            continue
        prev_delta = parse_hex(row["prev_delta"])
        next_delta = parse_hex(row["next_delta"])
        family = family_name_for_stride(str(row["table"]), prev_delta, next_delta, addr)
        families[family].append(addr)

    rows: list[dict[str, object]] = []
    for family, addrs in sorted(families.items()):
        samples32 = [read_va(data, sections, addr, 32) for addr in addrs]
        samples32 = [s for s in samples32 if len(s) == 32]
        mask32, fixed32 = byte_mask(samples32)
        first8 = Counter(s[:8].hex() for s in samples32)
        first16 = Counter(s[:16].hex() for s in samples32)
        rows.append(
            {
                "family": family,
                "count": len(addrs),
                "addr_min": hx(min(addrs)),
                "addr_max": hx(max(addrs)),
                "mod8_residues": " ".join("%d:%d" % (k, v) for k, v in sorted(Counter(a % 8 for a in addrs).items())),
                "mod16_residues": " ".join("%d:%d" % (k, v) for k, v in sorted(Counter(a % 16 for a in addrs).items())),
                "fixed32_count": len(fixed32),
                "mask32": mask32,
                "top_first8": " ".join("%s:%d" % (k, v) for k, v in first8.most_common(5)),
                "top_first16": " ".join("%s:%d" % (k, v) for k, v in first16.most_common(5)),
            }
        )
    return rows


def obj_int(row: dict[str, object], key: str, default: int = 0) -> int:
    value = row.get(key, default)
    if value in ("", None):
        return default
    return int(value)


def metric_rows_from_summary(metrics: dict[str, object]) -> list[dict[str, object]]:
    return [
        {"metric": key, "value": value}
        for key, value in metrics.items()
    ]


def calculate_flix_metrics(
    boundary_rows: list[dict[str, object]],
    template_family_rows: list[dict[str, object]],
    control_rows: list[dict[str, object]],
    control_vote_rows: list[dict[str, object]],
    pcrel_rows: list[dict[str, object]],
    internal_boundary_rows: list[dict[str, object]],
    slot_template_rows: list[dict[str, object]],
    operand_model_rows: list[dict[str, object]],
    cfg_edge_rows: list[dict[str, object]],
    cfg_node_rows: list[dict[str, object]],
    cfg_cluster_rows: list[dict[str, object]],
    basic_block_rows: list[dict[str, object]],
    block_edge_rows: list[dict[str, object]],
    component_rows: list[dict[str, object]],
    dispatch_component_rows: list[dict[str, object]],
) -> dict[str, object]:
    """Fixed evaluator for autoresearch-style iterations.

    The evaluator is intentionally independent from the active learner profile.
    It rewards stable facts we already trust: C[1] should remain the FLK control
    anchor, A/B/C -0xb runs should not be promoted to bundle starts, and D stride
    families should collapse into generated template entries.
    """
    by_key = {
        (str(row["table"]), obj_int(row, "index")): row
        for row in boundary_rows
    }
    counts = Counter(str(row["hypothesis"]) for row in boundary_rows)
    abc_minus_0xb = [
        row for row in boundary_rows
        if row["table"] in {"A", "B", "C"}
        and ("abc_minus_0xb_stride" in str(row["evidence"]))
    ]
    abc_minus_0xb_landing = [
        row for row in abc_minus_0xb
        if row["hypothesis"] == "likely_landing_pad_or_slot_target"
    ]
    d_generated = [
        row for row in boundary_rows
        if row["table"] == "D" and "d_generated_stride_family" in str(row["evidence"])
    ]
    d_template = [
        row for row in d_generated
        if row["hypothesis"] == "generated_template_entry"
    ]
    actionable_votes = [row for row in control_vote_rows if obj_int(row, "actionable")]
    high_score_controls = [row for row in control_rows if obj_int(row, "score") >= 20]
    exact_or_near_votes = [
        row for row in boundary_rows
        if row["control_vote_exact"] or row["control_vote_near"]
    ]
    pcrel_supported_boundaries = [
        row for row in boundary_rows
        if row.get("pcrel_slot_exact") or row.get("pcrel_slot_near")
    ]
    usable_pcrel = [
        row for row in pcrel_rows
        if row["pcrel_class"] in PCREL_USABLE_CLASSES
    ]
    rejected_pcrel = [
        row for row in pcrel_rows
        if row["pcrel_class"] == "reject_repeated_out_of_region"
    ]
    strong_internal = [
        row for row in internal_boundary_rows
        if row["confidence"] == "strong"
    ]
    medium_internal = [
        row for row in internal_boundary_rows
        if row["confidence"] == "medium"
    ]
    internal_prop_groups = {
        (row["target_prop_size"], row["target_prop_flags"])
        for row in internal_boundary_rows
    }
    accepted_slot_templates = [
        row for row in slot_template_rows
        if obj_int(row, "accepted")
    ]
    high_volume_slot_templates = [
        row for row in accepted_slot_templates
        if obj_int(row, "internal_count") >= 16
    ]
    slot_template_internal_edges = sum(obj_int(row, "internal_count") for row in accepted_slot_templates)
    accepted_operand_models = [
        row for row in operand_model_rows
        if obj_int(row, "accepted")
    ]
    negative_operand_models = [
        row for row in operand_model_rows
        if row["status"] == "rejected_negative_operand_family"
    ]
    operand_model_internal_edges = sum(obj_int(row, "internal_count") for row in accepted_operand_models)
    operand_model_delta_match = sum(obj_int(row, "delta_match_count") for row in operand_model_rows)
    operand_model_total = sum(obj_int(row, "row_count") for row in operand_model_rows)
    strong_cfg_edges = [row for row in cfg_edge_rows if row["edge_confidence"] == "strong"]
    medium_cfg_edges = [row for row in cfg_edge_rows if row["edge_confidence"] == "medium"]
    landing_cfg_edges = [row for row in cfg_edge_rows if row["edge_confidence"] == "landing"]
    strong_cfg_nodes = [row for row in cfg_node_rows if row["node_confidence"] == "strong"]
    medium_cfg_nodes = [row for row in cfg_node_rows if row["node_confidence"] == "medium"]
    accepted_cfg_clusters = [row for row in cfg_cluster_rows if obj_int(row, "accepted")]
    terminated_blocks = [
        row for row in basic_block_rows
        if "terminated_by_pcrel_source" in str(row["extent_status"])
    ]
    fallthrough_blocks = [
        row for row in basic_block_rows
        if "fallthrough_to_next_start" in str(row["extent_status"])
    ]
    strong_blocks = [row for row in basic_block_rows if row["block_confidence"] == "strong"]
    medium_blocks = [row for row in basic_block_rows if row["block_confidence"] == "medium"]
    linear_components = [
        row for row in component_rows
        if row["component_kind"] == "linear_chain"
    ]
    branch_components = [
        row for row in component_rows
        if row["component_kind"] == "branch_or_join"
    ]
    mapped_dispatch = [
        row for row in dispatch_component_rows
        if row["relation"] != "unmapped"
    ]
    exact_dispatch = [
        row for row in dispatch_component_rows
        if row["relation"] == "exact_block_start"
    ]
    inside_dispatch = [
        row for row in dispatch_component_rows
        if row["relation"] == "inside_block"
    ]
    successor_dispatch = [
        row for row in dispatch_component_rows
        if row["relation"] == "successor_node"
    ]
    strong_template_families = [
        row for row in template_family_rows
        if obj_int(row, "count") >= 3 and obj_int(row, "fixed32_count") >= 16
    ]
    d_fixed29 = [
        row for row in template_family_rows
        if str(row["family"]).startswith("D_stride_") and obj_int(row, "fixed32_count") >= 29
    ]

    c1 = by_key.get(("C", 1))
    c1_ok = bool(
        c1
        and c1["hypothesis"] == "candidate_bundle_start"
        and parse_hex(c1["addr"]) == 0x700169A4
    )
    ambiguous = counts.get("ambiguous_boundary", 0)
    false_bundle_abc = [
        row for row in abc_minus_0xb
        if row["hypothesis"] == "candidate_bundle_start"
    ]
    false_template_non_d = [
        row for row in boundary_rows
        if row["table"] != "D" and row["hypothesis"] == "generated_template_entry"
    ]
    d_missed = [
        row for row in d_generated
        if row["hypothesis"] != "generated_template_entry"
    ]

    flix_score = 0
    flix_score += 120 if c1_ok else -120
    flix_score += 4 * len(abc_minus_0xb_landing)
    flix_score += 5 * len(d_template)
    flix_score += 2 * len(exact_or_near_votes)
    flix_score += min(80, len(actionable_votes) // 2)
    flix_score += min(80, len(high_score_controls) // 3)
    flix_score += min(80, len(usable_pcrel) // 2)
    flix_score += 4 * len(pcrel_supported_boundaries)
    flix_score += min(220, 2 * len(strong_internal) + len(medium_internal))
    flix_score += min(80, 8 * len(internal_prop_groups))
    flix_score += min(160, 30 * len(accepted_slot_templates) + min(80, slot_template_internal_edges))
    flix_score += 20 * len(high_volume_slot_templates)
    flix_score += min(120, 40 * len(accepted_operand_models) + min(80, operand_model_internal_edges // 2))
    flix_score += 20 * len(negative_operand_models)
    flix_score += min(160, len(strong_cfg_edges) + (len(medium_cfg_edges) // 2) + (len(landing_cfg_edges) // 3))
    flix_score += min(120, len(strong_cfg_nodes) + (len(medium_cfg_nodes) // 2))
    flix_score += min(80, 8 * len(accepted_cfg_clusters))
    flix_score += min(180, len(terminated_blocks) + (len(fallthrough_blocks) // 3))
    flix_score += min(120, len(strong_blocks) + (len(medium_blocks) // 2))
    flix_score += min(80, len(block_edge_rows))
    flix_score += min(120, 8 * len(component_rows) + 4 * len(linear_components))
    flix_score += min(120, 2 * len(mapped_dispatch) + 3 * len(exact_dispatch) + len(inside_dispatch) + len(successor_dispatch))
    flix_score += 20 * len(strong_template_families)
    flix_score += 25 * len(d_fixed29)
    flix_score -= 30 * ambiguous
    flix_score -= 45 * len(false_bundle_abc)
    flix_score -= 35 * len(false_template_non_d)
    flix_score -= 15 * len(d_missed)

    coverage_den = max(1, len(boundary_rows))
    resolved = len(boundary_rows) - ambiguous
    abc_den = max(1, len(abc_minus_0xb))
    d_den = max(1, len(d_generated))
    return {
        "score_version": "v8_components",
        "flix_score": flix_score,
        "boundary_rows": len(boundary_rows),
        "resolved_boundary_ratio": "%.4f" % (resolved / coverage_den),
        "ambiguous_count": ambiguous,
        "c1_candidate_bundle_start": int(c1_ok),
        "abc_minus_0xb_landing": len(abc_minus_0xb_landing),
        "abc_minus_0xb_total": len(abc_minus_0xb),
        "abc_landing_ratio": "%.4f" % (len(abc_minus_0xb_landing) / abc_den),
        "d_generated_template": len(d_template),
        "d_generated_total": len(d_generated),
        "d_template_ratio": "%.4f" % (len(d_template) / d_den),
        "strong_template_families": len(strong_template_families),
        "d_fixed29_template_families": len(d_fixed29),
        "control_vote_boundaries": len(exact_or_near_votes),
        "actionable_control_votes": len(actionable_votes),
        "high_score_control_candidates": len(high_score_controls),
        "pcrel_usable_candidates": len(usable_pcrel),
        "pcrel_rejected_candidates": len(rejected_pcrel),
        "pcrel_supported_boundaries": len(pcrel_supported_boundaries),
        "internal_control_boundaries": len(internal_boundary_rows),
        "internal_strong_boundaries": len(strong_internal),
        "internal_medium_boundaries": len(medium_internal),
        "internal_prop_groups": len(internal_prop_groups),
        "accepted_slot_templates": len(accepted_slot_templates),
        "high_volume_slot_templates": len(high_volume_slot_templates),
        "slot_template_internal_edges": slot_template_internal_edges,
        "operand_model_rows": len(operand_model_rows),
        "accepted_operand_models": len(accepted_operand_models),
        "negative_operand_models": len(negative_operand_models),
        "operand_model_internal_edges": operand_model_internal_edges,
        "operand_model_delta_match_ratio": "%.4f" % (operand_model_delta_match / max(1, operand_model_total)),
        "cfg_edges": len(cfg_edge_rows),
        "cfg_strong_edges": len(strong_cfg_edges),
        "cfg_medium_edges": len(medium_cfg_edges),
        "cfg_landing_edges": len(landing_cfg_edges),
        "cfg_nodes": len(cfg_node_rows),
        "cfg_strong_nodes": len(strong_cfg_nodes),
        "cfg_medium_nodes": len(medium_cfg_nodes),
        "cfg_clusters": len(cfg_cluster_rows),
        "accepted_cfg_clusters": len(accepted_cfg_clusters),
        "basic_blocks": len(basic_block_rows),
        "terminated_blocks": len(terminated_blocks),
        "fallthrough_blocks": len(fallthrough_blocks),
        "strong_blocks": len(strong_blocks),
        "medium_blocks": len(medium_blocks),
        "block_edges": len(block_edge_rows),
        "block_components": len(component_rows),
        "linear_components": len(linear_components),
        "branch_components": len(branch_components),
        "mapped_dispatch_anchors": len(mapped_dispatch),
        "exact_dispatch_blocks": len(exact_dispatch),
        "inside_dispatch_blocks": len(inside_dispatch),
        "successor_dispatch_nodes": len(successor_dispatch),
        "false_bundle_abc": len(false_bundle_abc),
        "false_template_non_d": len(false_template_non_d),
        "d_missed_templates": len(d_missed),
    }


def summarize_markdown(
    out: Path,
    anchors: list[Anchor],
    stride_rows: list[dict[str, object]],
    entropy_rows: list[dict[str, object]],
    phase_rows: list[dict[str, object]],
    repeat_rows: list[dict[str, object]],
    control_rows: list[dict[str, object]],
    control_vote_rows: list[dict[str, object]],
    boundary_rows: list[dict[str, object]],
    template_family_rows: list[dict[str, object]],
    pcrel_slot_rows: list[dict[str, object]],
    internal_boundary_rows: list[dict[str, object]],
    slot_template_rows: list[dict[str, object]],
    operand_model_rows: list[dict[str, object]],
    cfg_edge_rows: list[dict[str, object]],
    cfg_node_rows: list[dict[str, object]],
    cfg_cluster_rows: list[dict[str, object]],
    basic_block_rows: list[dict[str, object]],
    block_edge_rows: list[dict[str, object]],
    component_rows: list[dict[str, object]],
    dispatch_component_rows: list[dict[str, object]],
    metrics: dict[str, object],
    profile_name: str,
) -> None:
    lines: list[str] = []
    lines.append("# FLIX Bundle Learning Baseline")
    lines.append("")
    lines.append("This report is evidence, not a disassembly. It is meant to decide")
    lines.append("whether table targets are bundle starts, landing pads, or another")
    lines.append("kind of FLIX/TIE entry before assigning opcode semantics.")
    lines.append("")
    lines.append("## Anchor Corpus")
    lines.append("")
    lines.append("* Dispatch anchors: %d" % sum(1 for a in anchors if a.source == "dispatch"))
    lines.append("* Manual anchors: %d" % sum(1 for a in anchors if a.source == "manual"))
    lines.append("* Dense regions: `%s`, `%s`" % (
        "0x700169a2..0x70018190",
        "0x70081ec5..0x70082b2c",
    ))
    lines.append("* Learner profile: `%s`" % profile_name)
    lines.append("* Score version: `%s`" % metrics["score_version"])
    lines.append("* Fixed `flix_score`: `%s`" % metrics["flix_score"])
    lines.append("")
    lines.append("## Fixed Scorecard")
    lines.append("")
    for key in (
        "resolved_boundary_ratio",
        "ambiguous_count",
        "c1_candidate_bundle_start",
        "abc_landing_ratio",
        "d_template_ratio",
        "strong_template_families",
        "d_fixed29_template_families",
        "control_vote_boundaries",
        "pcrel_usable_candidates",
        "pcrel_rejected_candidates",
        "pcrel_supported_boundaries",
        "internal_control_boundaries",
        "internal_strong_boundaries",
        "internal_medium_boundaries",
        "internal_prop_groups",
        "accepted_slot_templates",
        "high_volume_slot_templates",
        "slot_template_internal_edges",
        "operand_model_rows",
        "accepted_operand_models",
        "negative_operand_models",
        "operand_model_internal_edges",
        "operand_model_delta_match_ratio",
        "cfg_edges",
        "cfg_strong_edges",
        "cfg_medium_edges",
        "cfg_landing_edges",
        "cfg_nodes",
        "cfg_strong_nodes",
        "cfg_medium_nodes",
        "accepted_cfg_clusters",
        "basic_blocks",
        "terminated_blocks",
        "fallthrough_blocks",
        "strong_blocks",
        "medium_blocks",
        "block_edges",
        "block_components",
        "linear_components",
        "branch_components",
        "mapped_dispatch_anchors",
        "exact_dispatch_blocks",
        "inside_dispatch_blocks",
        "successor_dispatch_nodes",
        "false_bundle_abc",
        "false_template_non_d",
        "d_missed_templates",
    ):
        lines.append("* `%s`: %s" % (key, metrics[key]))
    lines.append("")
    lines.append("## Table Stride Highlights")
    lines.append("")
    for table_name, _base, _count in DISPATCH_TABLES:
        rows = [r for r in stride_rows if r["table"] == table_name]
        deltas = Counter(r["next_delta"] for r in rows if r["next_delta"])
        mod8 = Counter(str(r["mod8"]) for r in rows)
        mod16 = Counter(str(r["mod16"]) for r in rows)
        prop_rows = [r for r in rows]
        targets = [r["target"] for r in prop_rows]
        lines.append("* Table `%s`: targets `%s`..`%s`, next deltas %s" % (
            table_name,
            min(targets),
            max(targets),
            ", ".join("%s:%d" % (k, v) for k, v in deltas.most_common(6)),
        ))
        lines.append("  mod8 residues: %s" % ", ".join("%s:%d" % (k, v) for k, v in sorted(mod8.items())))
        lines.append("  mod16 residues: %s" % ", ".join("%s:%d" % (k, v) for k, v in sorted(mod16.items())))
    lines.append("")
    lines.append("## Stable Byte Masks")
    lines.append("")
    for table_name, _base, _count in DISPATCH_TABLES:
        mask = next((r for r in entropy_rows if r["table"] == table_name and r["offset"] == "mask32"), None)
        if mask:
            lines.append("* Table `%s`: fixed positions=%s" % (table_name, mask["fixed"]))
            lines.append("  `%s`" % mask["most_common"])
    lines.append("")
    lines.append("## Best 8/16-byte Phase Scores")
    lines.append("")
    for region_name, _start, _end in DENSE_REGIONS:
        for width in (8, 16):
            rows = [r for r in phase_rows if r["region"] == region_name and r["width"] == width]
            rows.sort(key=lambda r: int(r["score"]), reverse=True)
            top = rows[:4]
            lines.append("* `%s` width=%d:" % (region_name, width))
            for row in top:
                lines.append(
                    "  phase=%s base=%s score=%s anchors_on=%s/%s duplicates=%s instances=%s"
                    % (
                        row["phase"],
                        row["base"],
                        row["score"],
                        row["anchors_on_boundary"],
                        row["anchors_total"],
                        row["duplicate_chunks"],
                        row["duplicate_instances"],
                    )
                )
    lines.append("")
    lines.append("## Current Boundary Read")
    lines.append("")
    lines.append("* Tables `A`, `B`, and most of `C` have a dominant `-0xb` target stride and cycle through many mod8/mod16 residues. That is weak evidence for 8/16-byte bundle-start targets and stronger evidence for short landing pads or slot-level branch targets.")
    lines.append("* Table `C[1]` at `0x700169a4` is special: it has an explicit `.xt.prop` `insn|data|no_reorder|no_transform:0x8` run, while the later dense `0x70017cxx` targets are mostly `.xt.prop` zero-size `data|unreachable|no_transform` markers.")
    lines.append("* Table `D` has repeated prologue bytes across many entries and dominant `-0x68` / `-0x75` / `-0x77` spacing. This looks like a family of generated ANN stubs/templates rather than independent arbitrary function starts.")
    lines.append("* The ANN dense region has much stronger repeated 8/16-byte chunks than the FLK dense region; it should be the easier first target for raw-template learning, while `0x700169a4` remains the best FLK control anchor.")
    lines.append("")
    lines.append("## Control-Flow Constraint Candidates")
    lines.append("")
    kind_counts = Counter(str(row["kind"]) for row in control_rows)
    high_score = [row for row in control_rows if int(row["score"]) >= 20]
    actionable_votes = [row for row in control_vote_rows if int(row["actionable"])]
    actionable_hits = [
        row for row in control_rows
        if row["target_region"] or (str(row["nearest_anchor_distance"]).isdigit() and int(row["nearest_anchor_distance"]) <= 8)
    ]
    lines.append("* Candidate byte-pattern hits: %d total; high-score hits: %d" % (len(control_rows), len(high_score)))
    lines.append("* Actionable hits for FLIX boundary learning: %d; actionable target votes: %d" % (len(actionable_hits), len(actionable_votes)))
    lines.append("* Kind counts: %s" % ", ".join("%s:%d" % (k, v) for k, v in kind_counts.most_common()))
    lines.append("* Top actionable target votes:")
    for row in actionable_votes[:10]:
        lines.append(
            "  target=%s votes=%s score=%s kinds=%s prop=%s:%s nearest_anchor=%s"
            % (
                row["target"],
                row["vote_count"],
                row["score_sum"],
                row["kinds"],
                row["target_prop_size"],
                row["target_prop_flags"],
                row["nearest_anchor_distance"],
            )
        )
    lines.append("")
    lines.append("Control-flow rows are constraints, not decoded instructions. For bundle learning, `actionable` means the target lands in a dense FLIX region or within 8 bytes of a dispatch/manual anchor; other `.text` targets stay in the CSV but are not used as boundary evidence yet.")
    lines.append("")
    lines.append("## PC-relative Slot Candidate Read")
    lines.append("")
    pcrel_counts = Counter(str(row["pcrel_class"]) for row in pcrel_slot_rows)
    usable_pcrel = [
        row for row in pcrel_slot_rows
        if row["pcrel_class"] in PCREL_USABLE_CLASSES
    ]
    lines.append("* PC-relative-looking slot candidates: %d; usable as constraints: %d" % (len(pcrel_slot_rows), len(usable_pcrel)))
    lines.append("* Candidate classes: %s" % ", ".join("%s:%d" % (k, v) for k, v in pcrel_counts.most_common()))
    lines.append("* Strongest usable PC-relative slot constraints:")
    for row in usable_pcrel[:12]:
        lines.append(
            "  source=%s raw=%s target=%s class=%s score=%s prop=%s:%s nearest_anchor=%s"
            % (
                row["source"],
                row["raw"],
                row["target"],
                row["pcrel_class"],
                row["score"],
                row["target_prop_size"],
                row["target_prop_flags"],
                row["nearest_anchor_distance"],
            )
        )
    lines.append("")
    lines.append("The repeated `060402` pattern is useful only as a constrained PC-relative slot signal. Treat it as a real standard jump only when the target also lands on dense-region `.xt.prop` control metadata or a dispatch-adjacent landing pad.")
    lines.append("")
    lines.append("## PC-relative Operand Models")
    lines.append("")
    accepted_operand_models = [row for row in operand_model_rows if obj_int(row, "accepted")]
    negative_operand_models = [row for row in operand_model_rows if row["status"] == "rejected_negative_operand_family"]
    lines.append("* Accepted operand models: %d" % len(accepted_operand_models))
    lines.append("* Negative operand models: %d" % len(negative_operand_models))
    lines.append("* Top operand model families:")
    for row in operand_model_rows[:8]:
        lines.append(
            "  low=%s high=%s status=%s rows=%s raws=%s usable=%s internal=%s rejects=%s reject_bps=%s delta_match=%s/%s classes=%s"
            % (
                row["opcode_low"],
                row["opcode_high"],
                row["status"],
                row["row_count"],
                row["raw_variants"],
                row["usable_count"],
                row["internal_count"],
                row["reject_count"],
                row["reject_ratio_bps"],
                row["delta_match_count"],
                row["row_count"],
                row["pcrel_classes"],
            )
        )
    lines.append("")
    lines.append("## PC-relative CFG Edges")
    lines.append("")
    lines.append("* CFG edges accepted from operand models: %d" % len(cfg_edge_rows))
    lines.append("* CFG nodes: %d" % len(cfg_node_rows))
    lines.append("* Accepted CFG clusters: %d" % sum(1 for row in cfg_cluster_rows if obj_int(row, "accepted")))
    lines.append("* Top CFG nodes:")
    for row in cfg_node_rows[:12]:
        lines.append(
            "  target=%s confidence=%s incoming=%s score=%s prop=%s:%s sources=%s..%s raws=%s"
            % (
                row["target"],
                row["node_confidence"],
                row["incoming_count"],
                row["score_sum"],
                row["target_prop_size"],
                row["target_prop_flags"],
                row["source_min"],
                row["source_max"],
                row["raw_counts"],
            )
        )
    lines.append("* Top CFG clusters:")
    for row in cfg_cluster_rows[:8]:
        lines.append(
            "  region=%s prop=%s:%s nodes=%s edges=%s targets=%s..%s accepted=%s conf=%s"
            % (
                row["target_region"],
                row["target_prop_size"],
                row["target_prop_flags"],
                row["node_count"],
                row["edge_count"],
                row["target_min"],
                row["target_max"],
                row["accepted"],
                row["node_confidences"],
            )
        )
    lines.append("")
    lines.append("## PC-relative Basic Block Extents")
    lines.append("")
    lines.append("* Basic block hypotheses: %d" % len(basic_block_rows))
    lines.append("* Block successor edges: %d" % len(block_edge_rows))
    lines.append("* Top block extents:")
    for row in basic_block_rows[:14]:
        lines.append(
            "  %s %s..%s size=%s status=%s conf=%s term=%s succ=%s prop=%s:%s"
            % (
                row["region"],
                row["block_start"],
                row["block_end"],
                row["block_size"],
                row["extent_status"],
                row["block_confidence"],
                row["terminator_source"],
                row["successors"],
                row["prop_size"],
                row["prop_flags"],
            )
        )
    lines.append("")
    lines.append("## Block Components And Dispatch Map")
    lines.append("")
    lines.append("* Block components: %d" % len(component_rows))
    lines.append("* Dispatch anchors mapped to components: %d/%d" % (
        sum(1 for row in dispatch_component_rows if row["relation"] != "unmapped"),
        len(dispatch_component_rows),
    ))
    lines.append("* Top components:")
    for row in component_rows[:12]:
        lines.append(
            "  %s kind=%s nodes=%s known=%s unresolved=%s edges=%s addr=%s..%s entries=%s terminals=%s conf=%s"
            % (
                row["component_id"],
                row["component_kind"],
                row["node_count"],
                row["known_block_count"],
                row["unresolved_node_count"],
                row["edge_count"],
                row["addr_min"],
                row["addr_max"],
                row["entry_block_count"],
                row["terminal_block_count"],
                row["block_confidences"],
            )
        )
    lines.append("* Dispatch component map samples:")
    for row in dispatch_component_rows[:16]:
        lines.append(
            "  %s[%s] target=%s relation=%s component=%s block=%s..%s conf=%s"
            % (
                row["table"],
                row["index"],
                row["target"],
                row["relation"],
                row["component_id"],
                row["block_start"],
                row["block_end"],
                row["block_confidence"],
            )
        )
    lines.append("")
    lines.append("## Internal Control Boundary Candidates")
    lines.append("")
    confidence_counts = Counter(str(row["confidence"]) for row in internal_boundary_rows)
    prop_groups = Counter("%s:%s" % (row["target_prop_size"], row["target_prop_flags"]) for row in internal_boundary_rows)
    lines.append("* Accepted internal boundaries: %d" % len(internal_boundary_rows))
    lines.append("* Confidence counts: %s" % (
        ", ".join("%s:%d" % (k, v) for k, v in confidence_counts.most_common()) or "none"
    ))
    lines.append("* Top property groups:")
    for prop, count in prop_groups.most_common(8):
        lines.append("  %s count=%d" % (prop, count))
    lines.append("* Top internal targets:")
    for row in internal_boundary_rows[:12]:
        lines.append(
            "  target=%s confidence=%s votes=%s score=%s prop=%s:%s sources=%s..%s"
            % (
                row["target"],
                row["confidence"],
                row["vote_count"],
                row["score_sum"],
                row["target_prop_size"],
                row["target_prop_flags"],
                row["source_min"],
                row["source_max"],
            )
        )
    lines.append("")
    lines.append("## PC-relative Slot Grammar Templates")
    lines.append("")
    accepted_templates = [row for row in slot_template_rows if obj_int(row, "accepted")]
    lines.append("* Accepted slot templates: %d" % len(accepted_templates))
    lines.append("* Top slot templates:")
    for row in slot_template_rows[:12]:
        lines.append(
            "  raw=%s status=%s rows=%s usable=%s internal=%s rejects=%s delta=%s classes=%s"
            % (
                row["raw"],
                row["status"],
                row["row_count"],
                row["usable_count"],
                row["internal_count"],
                row["reject_count"],
                row["dominant_normalized_delta"],
                row["pcrel_classes"],
            )
        )
    lines.append("")
    lines.append("## Boundary Hypotheses")
    lines.append("")
    hypothesis_counts = Counter(str(row["hypothesis"]) for row in boundary_rows)
    lines.append("* Hypothesis counts: %s" % ", ".join("%s:%d" % (k, v) for k, v in hypothesis_counts.most_common()))
    lines.append("* Strongest landing/template candidates:")
    ranked = sorted(
        boundary_rows,
        key=lambda row: (int(row["landing_score"]) + int(row["template_score"]), int(row["bundle_score"])),
        reverse=True,
    )
    for row in ranked[:12]:
        lines.append(
            "  %s[%s] %s -> %s bundle=%s landing=%s template=%s evidence=%s"
            % (
                row["table"],
                row["index"],
                row["addr"],
                row["hypothesis"],
                row["bundle_score"],
                row["landing_score"],
                row["template_score"],
                row["evidence"],
            )
        )
    bundle_ranked = sorted(
        boundary_rows,
        key=lambda row: (int(row["bundle_score"]), -int(row["landing_score"]), int(row["template_score"])),
        reverse=True,
    )
    lines.append("* Candidate bundle/control starts:")
    for row in bundle_ranked[:8]:
        lines.append(
            "  %s[%s] %s -> %s bundle=%s landing=%s template=%s evidence=%s"
            % (
                row["table"],
                row["index"],
                row["addr"],
                row["hypothesis"],
                row["bundle_score"],
                row["landing_score"],
                row["template_score"],
                row["evidence"],
            )
        )
    lines.append("")
    lines.append("## Template Families")
    lines.append("")
    for row in sorted(template_family_rows, key=lambda r: int(r["count"]), reverse=True):
        lines.append(
            "* `%s`: count=%s addr=%s..%s fixed32=%s mod8=%s"
            % (
                row["family"],
                row["count"],
                row["addr_min"],
                row["addr_max"],
                row["fixed32_count"],
                row["mod8_residues"],
            )
        )
        lines.append("  mask32=`%s`" % row["mask32"])
    lines.append("")
    lines.append("## Top Repeated Raw Chunks")
    lines.append("")
    for region_name, _start, _end in DENSE_REGIONS:
        for width in (8, 16):
            rows = [r for r in repeat_rows if r["region"] == region_name and r["width"] == width]
            lines.append("* `%s` width=%d:" % (region_name, width))
            for row in rows[:6]:
                lines.append(
                    "  count=%s sha1=%s chunk=`%s` samples=%s"
                    % (row["count"], row["sha1_8"], row["chunk"], row["sample_addrs"])
                )
    lines.append("")
    lines.append("## Interpretation Rules")
    lines.append("")
    lines.append("* A high phase score means raw chunk repetition and anchor alignment agree; it does not prove semantic decoding.")
    lines.append("* Low anchor-on-boundary counts with strong target stride suggest dispatch targets may be landing pads inside bundles.")
    lines.append("* Stable byte masks identify candidate format/register/immediate fields for a later minimal decoder.")
    out.write_text("\n".join(lines) + "\n")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("elf", help="APUNN core ELF")
    ap.add_argument("--out-dir", default="firmware/apunn/flix_bundle_learning",
                    help="directory for CSV and Markdown output")
    ap.add_argument("--profile", choices=sorted(LEARNER_PROFILES), default="baseline",
                    help="boundary-hypothesis learner profile; flix_score evaluator remains fixed")
    args = ap.parse_args()

    elf = Path(args.elf)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    data = elf.read_bytes()
    eh = A.parse_elf_header(data)
    sections = A.parse_sections(data, eh)
    props = A.parse_xt_props(data, A.section_by_name(sections, ".xt.prop"))
    funcs = A.find_function_candidates(data, sections, props)

    anchors = collect_anchors(data, sections)
    anchor_rows = build_anchor_rows(data, sections, props, funcs, anchors)
    stride_rows = build_stride_rows(data, sections)
    entropy_rows = build_entropy_rows(data, sections)
    phase_rows = build_phase_rows(data, sections, anchors)
    repeat_rows = build_repeat_rows(data, sections)
    control_rows, control_vote_rows = build_control_flow_rows(data, sections, props, anchors)
    pcrel_slot_rows = build_pcrel_slot_candidate_rows(control_rows)
    internal_boundary_rows = build_internal_control_boundary_rows(
        pcrel_slot_rows,
        LEARNER_PROFILES[args.profile],
    )
    slot_template_rows = build_pcrel_slot_template_rows(
        pcrel_slot_rows,
        internal_boundary_rows,
        LEARNER_PROFILES[args.profile],
    )
    operand_model_rows = build_pcrel_operand_model_rows(
        pcrel_slot_rows,
        internal_boundary_rows,
        LEARNER_PROFILES[args.profile],
    )
    cfg_edge_rows = build_pcrel_cfg_edge_rows(
        pcrel_slot_rows,
        operand_model_rows,
        props,
        LEARNER_PROFILES[args.profile],
    )
    cfg_node_rows = build_pcrel_cfg_node_rows(cfg_edge_rows, anchors)
    cfg_cluster_rows = build_pcrel_cfg_cluster_rows(
        cfg_node_rows,
        cfg_edge_rows,
        LEARNER_PROFILES[args.profile],
    )
    basic_block_rows, block_edge_rows = build_pcrel_basic_block_rows(
        cfg_edge_rows,
        cfg_node_rows,
        props,
        anchors,
        LEARNER_PROFILES[args.profile],
    )
    boundary_rows = build_boundary_hypothesis_rows(
        data,
        sections,
        props,
        anchors,
        stride_rows,
        phase_rows,
        control_vote_rows,
        pcrel_slot_rows,
        LEARNER_PROFILES[args.profile],
    )
    template_family_rows = build_template_family_rows(data, sections, stride_rows)
    metrics = calculate_flix_metrics(
        boundary_rows,
        template_family_rows,
        control_rows,
        control_vote_rows,
        pcrel_slot_rows,
        internal_boundary_rows,
        slot_template_rows,
        operand_model_rows,
        cfg_edge_rows,
        cfg_node_rows,
        cfg_cluster_rows,
        basic_block_rows,
        block_edge_rows,
    )

    write_csv(
        out_dir / "anchors.csv",
        anchor_rows,
        [
            "group", "index", "addr", "source", "region", "owner", "owner_delta",
            "prop_addr", "prop_size", "prop_flags", "next_anchor_delta",
            "bytes16", "bytes32",
        ],
    )
    write_csv(
        out_dir / "table_strides.csv",
        stride_rows,
        ["table", "index", "slot_addr", "target", "target_region", "prev_delta", "next_delta", "mod8", "mod16"],
    )
    write_csv(
        out_dir / "table_byte_entropy.csv",
        entropy_rows,
        ["table", "offset", "unique_values", "entropy", "most_common", "fixed"],
    )
    write_csv(
        out_dir / "region_phase_scores.csv",
        phase_rows,
        [
            "region", "region_start", "width", "phase", "base", "chunks",
            "unique_chunks", "duplicate_chunks", "duplicate_instances",
            "zeroish_chunks", "anchors_total", "anchors_on_boundary",
            "anchors_near_boundary", "score",
        ],
    )
    write_csv(
        out_dir / "repeated_chunks.csv",
        repeat_rows,
        ["region", "width", "count", "sha1_8", "chunk", "sample_addrs"],
    )
    write_csv(
        out_dir / "control_flow_candidates.csv",
        control_rows,
        [
            "source_region", "source", "kind", "raw", "target", "target_region",
            "target_prop_addr", "target_prop_size", "target_prop_flags",
            "nearest_anchor_distance", "score", "confidence", "reasons", "note",
        ],
    )
    write_csv(
        out_dir / "control_flow_target_votes.csv",
        control_vote_rows,
        [
            "target", "target_region", "target_prop_addr", "target_prop_size",
            "target_prop_flags", "nearest_anchor_distance", "actionable", "vote_count",
            "score_sum", "kinds", "source_samples",
        ],
    )
    write_csv(
        out_dir / "pcrel_slot_candidates.csv",
        pcrel_slot_rows,
        [
            "source_region", "source", "raw", "raw_count",
            "opcode_low", "opcode_high", "imm18", "signed_words",
            "decoded_delta", "normalized_delta", "delta_match",
            "target", "target_region",
            "target_prop_size", "target_prop_flags", "nearest_anchor_distance",
            "score", "pcrel_class", "note", "reasons",
        ],
    )
    write_csv(
        out_dir / "j_opportunities.csv",
        pcrel_slot_rows,
        [
            "source_region", "source", "raw", "raw_count",
            "opcode_low", "opcode_high", "imm18", "signed_words",
            "decoded_delta", "normalized_delta", "delta_match",
            "target", "target_region",
            "target_prop_size", "target_prop_flags", "nearest_anchor_distance",
            "score", "pcrel_class", "note", "reasons",
        ],
    )
    write_csv(
        out_dir / "internal_control_boundaries.csv",
        internal_boundary_rows,
        [
            "target", "target_region", "target_prop_size", "target_prop_flags",
            "pcrel_class", "confidence", "vote_count", "score_sum",
            "source_min", "source_max", "raw_counts", "source_samples",
        ],
    )
    write_csv(
        out_dir / "pcrel_slot_templates.csv",
        slot_template_rows,
        [
            "raw", "row_count", "usable_count", "internal_count", "reject_count",
            "normalized_delta_count", "dominant_normalized_delta",
            "dominant_normalized_delta_count", "source_mod8", "target_mod8",
            "pcrel_classes", "target_regions", "target_prop_groups",
            "accepted", "status", "evidence", "source_samples",
        ],
    )
    write_csv(
        out_dir / "pcrel_operand_models.csv",
        operand_model_rows,
        [
            "opcode_low", "opcode_high", "row_count", "raw_variants",
            "usable_count", "internal_count", "reject_count", "weak_count",
            "reject_ratio_bps", "delta_match_count", "delta_mismatch_count",
            "signed_negative_count", "target_regions", "pcrel_classes",
            "dominant_raws", "accepted", "status", "evidence",
            "source_samples",
        ],
    )
    write_csv(
        out_dir / "pcrel_cfg_edges.csv",
        cfg_edge_rows,
        [
            "source", "target", "source_region", "target_region", "raw",
            "opcode_low", "opcode_high", "decoded_delta", "normalized_delta",
            "delta_match", "direction", "span", "source_mod8", "target_mod8",
            "pcrel_class", "edge_confidence", "score", "source_prop_addr",
            "source_prop_size", "source_prop_flags", "target_prop_size",
            "target_prop_flags", "nearest_anchor_distance",
        ],
    )
    write_csv(
        out_dir / "pcrel_cfg_nodes.csv",
        cfg_node_rows,
        [
            "target", "target_region", "target_prop_size", "target_prop_flags",
            "node_confidence", "incoming_count", "score_sum",
            "nearest_anchor_distance", "source_min", "source_max",
            "pcrel_classes", "edge_confidences", "raw_counts",
            "source_samples",
        ],
    )
    write_csv(
        out_dir / "pcrel_cfg_clusters.csv",
        cfg_cluster_rows,
        [
            "target_region", "target_prop_size", "target_prop_flags",
            "node_count", "edge_count", "score_sum", "target_min",
            "target_max", "accepted", "node_confidences", "raw_counts",
        ],
    )
    write_csv(
        out_dir / "pcrel_basic_blocks.csv",
        basic_block_rows,
        [
            "region", "block_start", "block_end", "block_size",
            "extent_status", "block_confidence", "terminator_source",
            "next_start", "prop_addr", "prop_size", "prop_end",
            "prop_flags", "edge_count", "score_sum", "successor_count",
            "successors", "edge_kinds", "edge_confidences",
            "pcrel_classes", "raw_counts",
        ],
    )
    write_csv(
        out_dir / "pcrel_block_edges.csv",
        block_edge_rows,
        [
            "block_start", "block_end", "successor", "successor_region",
            "edge_kind", "terminator_source", "block_confidence",
        ],
    )
    write_csv(
        out_dir / "boundary_hypotheses.csv",
        boundary_rows,
        [
            "table", "index", "addr", "region", "hypothesis", "bundle_score",
            "landing_score", "template_score", "prev_delta", "next_delta",
            "mod8", "mod16", "prop_addr", "prop_size", "prop_flags",
            "phase8_rank", "phase8_score", "phase8", "phase16_rank",
            "phase16_score", "phase16", "repeat8", "repeat16",
            "control_vote_exact", "control_vote_near", "pcrel_slot_exact",
            "pcrel_slot_near", "evidence",
        ],
    )
    write_csv(
        out_dir / "template_families.csv",
        template_family_rows,
        [
            "family", "count", "addr_min", "addr_max", "mod8_residues",
            "mod16_residues", "fixed32_count", "mask32", "top_first8",
            "top_first16",
        ],
    )
    write_csv(
        out_dir / "score_metrics.csv",
        metric_rows_from_summary(metrics),
        ["metric", "value"],
    )
    summarize_markdown(
        out_dir / "report.md",
        anchors,
        stride_rows,
        entropy_rows,
        phase_rows,
        repeat_rows,
        control_rows,
        control_vote_rows,
        boundary_rows,
        template_family_rows,
        pcrel_slot_rows,
        internal_boundary_rows,
        slot_template_rows,
        operand_model_rows,
        cfg_edge_rows,
        cfg_node_rows,
        cfg_cluster_rows,
        basic_block_rows,
        block_edge_rows,
        metrics,
        args.profile,
    )

    print("wrote %s" % out_dir)
    print("profile=%s" % args.profile)
    print("flix_score=%s" % metrics["flix_score"])
    print(
        "anchors=%d phase_rows=%d repeat_rows=%d control_rows=%d control_votes=%d boundary_rows=%d template_families=%d"
        % (
            len(anchor_rows),
            len(phase_rows),
            len(repeat_rows),
            len(control_rows),
            len(control_vote_rows),
            len(boundary_rows),
            len(template_family_rows),
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
