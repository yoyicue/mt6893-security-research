#!/usr/bin/env python3
"""Apply APUNN .xt.prop-derived analysis facts to an IDA database.

Run after importing /tmp/apunn_core0_full.elf as an ELF for Xtensa.

Usage from IDA:
  File -> Script file -> ida_apply_apunn_xt_prop.py

Usage from command line/headless IDA:
  ida64 -A -S"ida_apply_apunn_xt_prop.py /tmp/apunn_core0_full_analysis_refs.json" \
    /tmp/apunn_core0_full.elf

The JSON is produced by analyze_apunn_elf.py. This script intentionally applies
only high-confidence facts: .xt.prop-backed entry candidates, known key owners,
pointer tables, strings, L32R literal refs, and critical-string scan status. It
does not force every .xt.prop instruction range into code because this APUNN core
uses Xtensa TIE/FLIX encodings that IDA may not decode cleanly.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

import ida_auto
import ida_bytes
import ida_funcs
import ida_name
import ida_segment
import ida_xref
import idc


DEFAULT_JSON = Path("/tmp/apunn_core0_full_analysis_refs.json")
MAX_BOUNDED_FUNCTION_DELTA = 0x2000
OBSOLETE_COMMENT_MARKERS = (
    "primary_INFO13_record_loop_candidate",
    "primary_INFO13_record_loop_target",
    "primary_INFO13_loop_candidate",
    "priority=primary target=0x7003c102",
    "stride_status=not_closed",
    "Count/stride are not closed",
    "do not spend primary effort here until 0x7003c102 is exhausted",
    "not the primary INFO13 array walk",
    "standard-ISA scans do not expose a byte-aligned LOOP",
    "because the owner is FLIX/TIE-heavy",
    "firmware-local count register is not boundary-visible after FLIX correction",
    "record stride is closed at the data-layout level",
    "count update is in FLIX/TIE slot semantics rather than a boundary-visible core LOOP",
)


def get_json_path() -> Path:
    candidates: list[str] = []
    candidates.extend(str(arg) for arg in getattr(idc, "ARGV", []) if arg)
    candidates.extend(sys.argv[1:])
    for candidate in candidates:
        if candidate.endswith(".json"):
            return Path(candidate)
    return DEFAULT_JSON


def has_segment(ea: int) -> bool:
    return ida_segment.getseg(ea) is not None


def safe_name(text: str, fallback: str) -> str:
    cleaned = re.sub(r"[^0-9A-Za-z_]+", "_", text).strip("_")
    if not cleaned:
        cleaned = fallback
    if cleaned[0].isdigit():
        cleaned = "_" + cleaned
    return cleaned[:180]


def append_comment(ea: int, text: str, repeatable: bool = False) -> bool:
    if not has_segment(ea):
        return False
    old_raw = idc.get_cmt(ea, repeatable) or ""
    kept: list[str] = []
    seen: set[str] = set()
    for line in old_raw.splitlines():
        if any(marker in line for marker in OBSOLETE_COMMENT_MARKERS):
            continue
        if line in seen:
            continue
        kept.append(line)
        seen.add(line)
    old = "\n".join(kept)
    if text in old:
        if old != old_raw:
            return bool(idc.set_cmt(ea, old, repeatable))
        return False
    new = text if not old else old + "\n" + text
    return bool(idc.set_cmt(ea, new, repeatable))


def try_set_name(ea: int, name: str) -> bool:
    if not has_segment(ea):
        return False
    return bool(idc.set_name(ea, name, ida_name.SN_CHECK))


def try_create_insn(ea: int) -> bool:
    if not has_segment(ea):
        return False
    try:
        return bool(idc.create_insn(ea))
    except Exception:
        return False


def force_create_verified_insn(ea: int, size: int) -> bool:
    if not has_segment(ea):
        return False
    if try_create_insn(ea):
        return True
    try:
        ida_bytes.del_items(ea, ida_bytes.DELIT_SIMPLE, size)
    except Exception:
        return False
    return try_create_insn(ea)


def try_create_string(ea: int) -> bool:
    if not has_segment(ea):
        return False
    try:
        return bool(idc.create_strlit(ea, idc.BADADDR))
    except Exception:
        return False


def function_end_for_candidate(fn: dict[str, object]) -> int | None:
    delta = fn.get("next_entry_delta")
    if delta is None:
        return None
    delta = int(delta)
    if delta <= 0 or delta > MAX_BOUNDED_FUNCTION_DELTA:
        return None
    return int(fn["addr"]) + delta


def apply_function_candidates(payload: dict[str, object]) -> tuple[int, int, int, int]:
    created = 0
    bounded = 0
    named = 0
    inside_existing = 0
    for fn in payload.get("function_candidates", []):
        ea = int(fn["addr"])
        if not has_segment(ea):
            continue
        try_create_insn(ea)
        existing_start = idc.get_func_attr(ea, idc.FUNCATTR_START)
        if existing_start == idc.BADADDR:
            end = function_end_for_candidate(fn)
            if end is not None and idc.add_func(ea, end):
                created += 1
                bounded += 1
            elif idc.add_func(ea):
                created += 1
        elif existing_start != ea:
            inside_existing += 1
        if try_set_name(ea, "apunn_fn_%08x" % ea):
            named += 1
        append_comment(
            ea,
            "APUNN .xt.prop-backed entry candidate; prop_size=0x%x flags=%s next_entry_delta=%s"
            % (
                int(fn["prop_size"]),
                fn.get("prop_flags") or "",
                "None"
                if fn.get("next_entry_delta") is None
                else "0x%x" % int(fn["next_entry_delta"]),
            ),
        )
    return created, bounded, named, inside_existing


def apply_key_addresses(payload: dict[str, object]) -> int:
    count = 0
    for item in payload.get("key_addresses", []):
        ea = int(item["addr"])
        label = safe_name(str(item["label"]), "key")
        if try_set_name(ea, "apunn_%s_%08x" % (label, ea)):
            count += 1
        append_comment(
            ea,
            "APUNN key address %s; section=%s owner=%s+%s prop=%s:%s"
            % (
                item.get("label"),
                item.get("section"),
                fmt_hex(item.get("owner_entry")),
                fmt_hex(item.get("owner_delta")),
                item.get("prop_flags"),
                fmt_hex(item.get("prop_size")),
            ),
        )
    return count


def apply_pointer_runs(payload: dict[str, object]) -> tuple[int, int]:
    data_items = 0
    refs = 0
    for index, run in enumerate(payload.get("pointer_runs", [])):
        start = int(run["start"])
        try_set_name(start, "apunn_ptr_run_%08x" % start)
        append_comment(start, "APUNN pointer run %d; count=%d" % (index, int(run["count"])))
        for entry in run.get("entries", []):
            ea = int(entry["addr"])
            target = int(entry["value"]) & ~1
            if not has_segment(ea):
                continue
            if idc.create_dword(ea):
                data_items += 1
            try:
                idc.op_plain_offset(ea, 0, 0)
            except Exception:
                try:
                    idc.op_offset(ea, 0, 0)
                except Exception:
                    pass
            if has_segment(target) and ida_xref.add_dref(ea, target, ida_xref.dr_O):
                refs += 1
            append_comment(
                ea,
                "APUNN pointer-table slot; target=%s owner=%s prop=%s"
                % (
                    fmt_hex(entry.get("value")),
                    fmt_hex(entry.get("owner_entry")),
                    entry.get("prop_flags"),
                ),
            )
    return data_items, refs


def apply_pointer_run_investigations(payload: dict[str, object]) -> int:
    comments = 0
    for item in payload.get("pointer_run_investigations", []):
        start = int(item["start"])
        if append_comment(
            start,
            "APUNN pointer-run reachability; range=%s..%s count=%s raw_slot_refs=%s/%s "
            "l32r_slot_refs=%s/%s table_base_refs=%s q2_status=%s"
            % (
                fmt_hex(item.get("start")),
                fmt_hex(item.get("end")),
                item.get("count"),
                item.get("raw_slot_ref_count", 0),
                len(item.get("raw_ref_hits", [])),
                item.get("l32r_slot_ref_count", 0),
                len(item.get("l32r_ref_hits", [])),
                item.get("table_base_value_ref_count", 0),
                item.get("q2_status"),
            ),
        ):
            comments += 1
        for hit in item.get("raw_ref_hits", [])[:8]:
            ref = int(hit["ref_addr"])
            value = int(hit["value"])
            if not has_segment(ref):
                continue
            if has_segment(value):
                ida_xref.add_dref(ref, value, ida_xref.dr_O)
            if append_comment(
                ref,
                "APUNN raw-u32 ref to pointer-run slot %s; run=%s owner=%s align=%s"
                % (
                    fmt_hex(value),
                    fmt_hex(item.get("start")),
                    fmt_hex(hit.get("owner_entry")),
                    "%s slot=%s" % (hit.get("alignment"), hit.get("slot_aligned")),
                ),
            ):
                comments += 1
        for hit in item.get("l32r_ref_hits", [])[:8]:
            ref = int(hit["ref_addr"])
            literal_value = hit.get("literal_value")
            if not has_segment(ref):
                continue
            if literal_value is not None and has_segment(int(literal_value) & ~1):
                ida_xref.add_dref(ref, int(literal_value) & ~1, ida_xref.dr_O)
            if append_comment(
                ref,
                "APUNN L32R ref to pointer-run range %s; literal=%s value=%s owner=%s"
                % (
                    fmt_hex(item.get("start")),
                    fmt_hex(hit.get("literal_addr")),
                    fmt_hex(literal_value),
                    fmt_hex(hit.get("owner_entry")),
                ),
            ):
                comments += 1
    return comments


def apply_strings(payload: dict[str, object]) -> int:
    count = 0
    for entry in payload.get("interesting_strings", []):
        ea = int(entry["addr"])
        value = str(entry["value"])
        if try_create_string(ea):
            count += 1
        name = "apunn_str_%s_%08x" % (safe_name(value[:48], "str"), ea)
        try_set_name(ea, name)
    for scan in payload.get("critical_string_refs", []):
        ea = scan.get("string_addr")
        if ea is None:
            continue
        ea = int(ea)
        value = scan.get("string_value") or scan.get("pattern")
        if try_create_string(ea):
            count += 1
        try_set_name(ea, "apunn_critical_%s_%08x" % (safe_name(str(scan["pattern"]), "crit"), ea))
        append_comment(
            ea,
            "APUNN critical string; aligned_refs=%d all_byte_refs=%d"
            % (int(scan["aligned_hit_count"]), int(scan["byte_hit_count"])),
        )
        for hit in scan.get("aligned_hits", []):
            ref = int(hit["ref_addr"])
            target = int(hit["value"])
            if has_segment(ref):
                ida_xref.add_dref(ref, target, ida_xref.dr_O)
                append_comment(
                    ref,
                    "APUNN aligned critical-string ref to %s+0x%x"
                    % (scan["pattern"], int(hit["string_offset"])),
                )
        for hit in scan.get("byte_hits", [])[:8]:
            ref = int(hit["ref_addr"])
            if has_segment(ref):
                append_comment(
                    ref,
                    "APUNN all-byte critical-string sample to %s+0x%x; validate disassembly"
                    % (scan["pattern"], int(hit["string_offset"])),
                )
    return count


def apply_standard_islands(payload: dict[str, object]) -> int:
    comments = 0
    for island in payload.get("standard_islands", []):
        label = str(island["label"])
        start = int(island["start"])
        if append_comment(
            start,
            "APUNN verified standard island %s; range=%s-%s verified=%s"
            % (
                label,
                fmt_hex(island.get("start")),
                fmt_hex(island.get("end")),
                island.get("verified"),
            ),
        ):
            comments += 1
        if append_comment(start, str(island.get("note") or "")):
            comments += 1
        for insn in island.get("instructions", []):
            ea = int(insn["addr"])
            expected = str(insn.get("expected_bytes") or "")
            expected_size = len(bytes.fromhex(expected)) if expected else 1
            if insn.get("verified"):
                force_create_verified_insn(ea, expected_size)
            else:
                try_create_insn(ea)
            if append_comment(
                ea,
                "APUNN standard island %s: %s ; %s"
                % (label, insn.get("mnemonic"), insn.get("effect")),
            ):
                comments += 1
    return comments


def collect_l32r_refs(payload: dict[str, object]) -> list[dict[str, object]]:
    refs: list[dict[str, object]] = []
    seen: set[int] = set()

    def add(ref: dict[str, object]) -> None:
        ea = int(ref["addr"])
        if ea in seen:
            return
        seen.add(ea)
        refs.append(ref)

    for ref in payload.get("interesting_l32r_refs", [])[:256]:
        add(ref)
    for ref in payload.get("dram_op_l32r_refs", []):
        add(ref)
    for scan in payload.get("critical_l32r_refs", []):
        for ref in scan.get("hits", []):
            add(ref)
    return refs


def apply_l32r_refs(payload: dict[str, object]) -> tuple[int, int]:
    comments = 0
    refs = 0
    for ref in collect_l32r_refs(payload):
        ea = int(ref["addr"])
        literal = int(ref["literal_addr"])
        if not has_segment(ea):
            continue
        try_create_insn(ea)
        if has_segment(literal) and ida_xref.add_dref(ea, literal, ida_xref.dr_R):
            refs += 1
        value_text = fmt_hex(ref.get("literal_value"))
        string_value = ref.get("literal_string_value") or ref.get("value_string_value")
        if string_value:
            text = (
                "APUNN L32R ref; a%d literal=%s[%s] value=%s[%s] string=%s"
                % (
                    int(ref["target_reg"]),
                    fmt_hex(ref.get("literal_addr")),
                    ref.get("literal_section"),
                    value_text,
                    ref.get("value_section"),
                    str(string_value)[:96],
                )
            )
        else:
            text = (
                "APUNN L32R ref; a%d literal=%s[%s] value=%s[%s]"
                % (
                    int(ref["target_reg"]),
                    fmt_hex(ref.get("literal_addr")),
                    ref.get("literal_section"),
                    value_text,
                    ref.get("value_section"),
                )
            )
        if append_comment(ea, text):
            comments += 1
    return comments, refs


def apply_loop_targets(payload: dict[str, object]) -> int:
    comments = 0
    for item in payload.get("loop_target_candidates", []):
        ea = int(item["addr"])
        if not has_segment(ea):
            continue
        try_create_insn(ea)
        comment_ea = idc.get_item_head(ea)
        if comment_ea == idc.BADADDR or not has_segment(comment_ea):
            comment_ea = ea
        bases = ",".join("a%d" % int(value) for value in item.get("matched_field_bases", []))
        if append_comment(
            comment_ea,
            "APUNN loop-target candidate at %s near field-access cluster; owner=%s+%s bases=%s prop=%s:%s"
            % (
                fmt_hex(ea),
                fmt_hex(item.get("owner_entry")),
                fmt_hex(item.get("owner_delta")),
                bases,
                item.get("prop_flags"),
                fmt_hex(item.get("prop_size")),
            ),
        ):
            comments += 1
    return comments


def apply_focused_loop_investigations(payload: dict[str, object]) -> int:
    comments = 0
    for item in payload.get("focused_loop_investigations", []):
        owner = int(item["owner_entry"])
        target = int(item["loop_target"])
        text = (
            "APUNN focused loop investigation %s; priority=%s target=%s "
            "target_prop=%s:%s loop_body=%s..%s count_status=%s stride_status=%s"
            % (
                item.get("label"),
                item.get("priority"),
                fmt_hex(target),
                item.get("target_prop_flags"),
                fmt_hex(item.get("target_prop_size")),
                fmt_hex(item.get("loop_body_start")),
                fmt_hex(item.get("loop_body_end")),
                item.get("count_status"),
                item.get("stride_0x40_status"),
            )
        )
        if append_comment(owner, text):
            comments += 1
        if has_segment(target):
            head = idc.get_item_head(target)
            if head == idc.BADADDR or not has_segment(head):
                head = target
            if append_comment(
                head,
                "APUNN focused loop target %s; owner=%s priority=%s assessment=%s count_status=%s"
                % (
                    fmt_hex(target),
                    fmt_hex(owner),
                    item.get("priority"),
                    item.get("assessment"),
                    item.get("count_status"),
                ),
            ):
                comments += 1
    return comments


def apply_flix_sweeps(payload: dict[str, object]) -> int:
    comments = 0
    for item in payload.get("flix_sweeps", []):
        start = int(item["start"])
        end = int(item["end"])
        counts = item.get("counts", {})
        if not isinstance(counts, dict):
            counts = {}
        summary = (
            "APUNN FLIX-correct sweep %s; range=%s..%s core24=%s dens16=%s "
            "flix64=%s flix128=%s bad_framing=%s; rule 0xe=16B 0xf=8B; "
            "06 04 02 is FLIX128 framing, not selector"
            % (
                item.get("label"),
                fmt_hex(start),
                fmt_hex(end),
                counts.get("core24", 0),
                counts.get("dens16", 0),
                counts.get("flix64", 0),
                counts.get("flix128", 0),
                item.get("bad_framing_count"),
            )
        )
        if append_comment(start, summary):
            comments += 1
        for insn in item.get("instructions", [])[:24]:
            ea = int(insn["addr"])
            if not has_segment(ea):
                continue
            text = "APUNN FLIX sweep item %s len=%s kind=%s raw=%s" % (
                item.get("label"),
                insn.get("length"),
                insn.get("kind"),
                insn.get("raw"),
            )
            if insn.get("fmt"):
                text += " fmt=%s framing=%s" % (
                    insn.get("fmt"),
                    "ok" if insn.get("framing_ok") else insn.get("framing_warn"),
                )
            mem = insn.get("core_mem_access")
            if isinstance(mem, dict):
                text += " decoded=%s a%s,a%s+%s" % (
                    mem.get("op"),
                    mem.get("value_reg"),
                    mem.get("base_reg"),
                    fmt_hex(mem.get("offset")),
                )
            head = idc.get_item_head(ea)
            if head == idc.BADADDR or not has_segment(head):
                head = ea
            if append_comment(head, text):
                comments += 1
    return comments


def apply_critical_owner_clusters(payload: dict[str, object]) -> int:
    comments = 0
    for item in payload.get("critical_l32r_owner_clusters", [])[:8]:
        owner = int(item["owner_entry"])
        if not has_segment(owner):
            continue
        patterns = ", ".join(str(pattern) for pattern in item.get("patterns", []))
        if append_comment(
            owner,
            "APUNN L32R string-cluster owner; patterns=%d hits=%d assessment=%s strings=%s"
            % (
                int(item["pattern_count"]),
                int(item["hit_count"]),
                item.get("assessment"),
                patterns[:220],
            ),
        ):
            comments += 1
    return comments


def apply_dma_owner_investigations(payload: dict[str, object]) -> int:
    comments = 0
    for item in payload.get("dma_owner_investigations", []):
        owner = int(item["owner_entry"])
        evidence = item.get("evidence_refs", [])
        evidence_text = ", ".join(
            "%s@%s" % (ref.get("pattern"), fmt_hex(ref.get("ref_addr")))
            for ref in evidence[:8]
        )
        if append_comment(
            owner,
            "APUNN DMA/iDMA owner investigation %s; range=%s..%s "
            "cluster_rank=%s patterns=%s hits=%s q1_status=%s evidence=%s"
            % (
                item.get("label"),
                fmt_hex(item.get("analysis_start")),
                fmt_hex(item.get("analysis_end")),
                item.get("cluster_rank"),
                item.get("cluster_pattern_count"),
                item.get("cluster_hit_count"),
                item.get("q1_status"),
                evidence_text,
            ),
        ):
            comments += 1
        for ref in evidence:
            ea = int(ref["ref_addr"])
            comment_ea = idc.get_item_head(ea)
            if comment_ea == idc.BADADDR or not has_segment(comment_ea):
                comment_ea = ea
            if append_comment(
                comment_ea,
                "APUNN DMA/iDMA evidence ref for owner %s; pattern=%s literal=%s prop=%s:%s"
                % (
                    fmt_hex(owner),
                    ref.get("pattern"),
                    fmt_hex(ref.get("literal_addr")),
                    ref.get("prop_flags"),
                    fmt_hex(ref.get("prop_size")),
                ),
            ):
                comments += 1
    return comments


def apply_output_validation_investigations(payload: dict[str, object]) -> int:
    comments = 0
    for item in payload.get("output_validation_investigations", []):
        owner = int(item["owner_entry"])
        evidence = item.get("evidence_refs", [])
        patterns = ", ".join(str(pattern) for pattern in item.get("referenced_patterns", []))
        evidence_text = ", ".join(
            "%s@%s" % (ref.get("pattern"), fmt_hex(ref.get("ref_addr")))
            for ref in evidence[:8]
        )
        if append_comment(
            owner,
            "APUNN output-validation investigation %s; range=%s..%s "
            "patterns=%s q4_status=%s evidence=%s"
            % (
                item.get("label"),
                fmt_hex(item.get("analysis_start")),
                fmt_hex(item.get("analysis_end")),
                patterns[:220],
                item.get("q4_status"),
                evidence_text,
            ),
        ):
            comments += 1
        for ref in evidence:
            ea = int(ref["ref_addr"])
            comment_ea = idc.get_item_head(ea)
            if comment_ea == idc.BADADDR or not has_segment(comment_ea):
                comment_ea = ea
            literal = ref.get("literal_addr", ref.get("string_addr"))
            if append_comment(
                comment_ea,
                "APUNN output-validation evidence ref for owner %s; kind=%s "
                "pattern=%s literal=%s prop=%s:%s"
                % (
                    fmt_hex(owner),
                    ref.get("kind"),
                    ref.get("pattern"),
                    fmt_hex(literal),
                    ref.get("prop_flags"),
                    fmt_hex(ref.get("prop_size")),
                ),
            ):
                comments += 1
    return comments


def apply_ann_op_table_investigation(payload: dict[str, object]) -> int:
    item = payload.get("ann_op_table_investigation")
    if not isinstance(item, dict):
        return 0
    start = int(item["table_start"])
    if not has_segment(start):
        return 0
    return int(
        append_comment(
            start,
            "APUNN ANN op-name table reachability; range=%s..%s entries=%s "
            "raw_refs=%s l32r_refs=%s q2_status=%s"
            % (
                fmt_hex(item.get("table_start")),
                fmt_hex(item.get("table_end")),
                item.get("nonzero_entry_count"),
                len(item.get("raw_ref_hits", [])),
                len(item.get("l32r_ref_hits", [])),
                item.get("q2_status"),
            ),
        )
    )


def fmt_hex(value: object) -> str:
    if value is None:
        return "None"
    return "0x%x" % int(value)


def main() -> None:
    json_path = get_json_path()
    if not json_path.exists():
        raise RuntimeError(
            "missing %s; run analyze_apunn_elf.py --json first" % json_path
        )
    payload = json.loads(json_path.read_text())
    print("[APUNN] applying analysis from %s" % json_path)

    ida_auto.auto_wait()
    fn_created, fn_bounded, fn_named, inside_existing = apply_function_candidates(payload)
    key_named = apply_key_addresses(payload)
    data_items, data_refs = apply_pointer_runs(payload)
    pointer_run_comments = apply_pointer_run_investigations(payload)
    strings = apply_strings(payload)
    island_comments = apply_standard_islands(payload)
    l32r_comments, l32r_refs = apply_l32r_refs(payload)
    loop_comments = apply_loop_targets(payload)
    focused_loop_comments = apply_focused_loop_investigations(payload)
    flix_sweep_comments = apply_flix_sweeps(payload)
    cluster_comments = apply_critical_owner_clusters(payload)
    dma_owner_comments = apply_dma_owner_investigations(payload)
    output_validation_comments = apply_output_validation_investigations(payload)
    ann_op_table_comments = apply_ann_op_table_investigation(payload)
    ida_auto.auto_wait()

    print(
        "[APUNN] functions_created=%d bounded_functions=%d function_names=%d inside_existing=%d "
        "key_names=%d pointer_dwords=%d pointer_refs=%d strings=%d island_comments=%d "
        "l32r_comments=%d l32r_refs=%d loop_comments=%d focused_loop_comments=%d "
        "flix_sweep_comments=%d cluster_comments=%d dma_owner_comments=%d "
        "output_validation_comments=%d pointer_run_comments=%d ann_op_table_comments=%d"
        % (
            fn_created,
            fn_bounded,
            fn_named,
            inside_existing,
            key_named,
            data_items,
            data_refs,
            strings,
            island_comments,
            l32r_comments,
            l32r_refs,
            loop_comments,
            focused_loop_comments,
            flix_sweep_comments,
            cluster_comments,
            dma_owner_comments,
            output_validation_comments,
            pointer_run_comments,
            ann_op_table_comments,
        )
    )


if __name__ == "__main__":
    main()
