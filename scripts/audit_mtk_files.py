#!/usr/bin/env python3
from __future__ import annotations

import pathlib
import subprocess
from dataclasses import dataclass


ROOT = pathlib.Path(__file__).resolve().parents[1]
REPORT = ROOT / "MTK_FILE_AUDIT.md"
IGNORED_PARTS = {".git", ".claude"}


@dataclass(frozen=True)
class Entry:
    path: pathlib.Path
    size: int
    git_state: str
    file_desc: str
    value: str
    action: str
    reason: str


def human_size(size: int) -> str:
    units = ["B", "KB", "MB", "GB"]
    value = float(size)
    for unit in units:
        if value < 1024 or unit == units[-1]:
            if unit == "B":
                return f"{int(value)}{unit}"
            return f"{value:.1f}{unit}"
        value /= 1024.0
    return f"{size}B"


def run_git_pathset(args: list[str]) -> set[pathlib.Path]:
    cmd = ["git", *args, "-z"]
    out = subprocess.check_output(cmd, cwd=ROOT)
    return {
        pathlib.Path(item.decode("utf-8"))
        for item in out.split(b"\0")
        if item
    }


def gather_git_states() -> tuple[set[pathlib.Path], set[pathlib.Path], set[pathlib.Path]]:
    tracked = run_git_pathset(["ls-files"])
    untracked = run_git_pathset(["ls-files", "--others", "--exclude-standard"])
    modified = run_git_pathset(["diff", "--name-only"]) | run_git_pathset(
        ["diff", "--cached", "--name-only"]
    )
    return tracked, untracked, modified


def git_state_for(
    rel_path: pathlib.Path,
    tracked: set[pathlib.Path],
    untracked: set[pathlib.Path],
    modified: set[pathlib.Path],
) -> str:
    if rel_path in untracked:
        return "untracked"
    if rel_path in modified:
        return "modified"
    if rel_path in tracked:
        return "tracked"
    return "untracked"


def describe_file(path: pathlib.Path) -> str:
    try:
        return subprocess.check_output(
            ["file", "-b", str(path)],
            text=True,
        ).strip()
    except Exception:
        return "unknown"


def has_source_pair(path: pathlib.Path) -> bool:
    candidates = (
        path.with_suffix(".c"),
        path.with_suffix(".cpp"),
        path.with_suffix(".cc"),
        path.with_suffix(".py"),
        path.with_suffix(".sh"),
    )
    return any(candidate.exists() for candidate in candidates)


def classify(rel_path: pathlib.Path, file_desc: str) -> tuple[str, str, str]:
    path_str = rel_path.as_posix()
    suffix = rel_path.suffix.lower()
    name = rel_path.name
    top = rel_path.parts[0]
    is_elf = "ELF " in file_desc

    if name == ".DS_Store":
        return (
            "Low",
            "Delete",
            "Finder metadata, not part of the research or build chain.",
        )

    if name == ".gitignore":
        return (
            "High",
            "Keep",
            "Repository hygiene rule set; keeps low-value noise out of version control.",
        )

    if "__pycache__" in rel_path.parts or suffix == ".pyc":
        return (
            "Low",
            "Delete",
            "Python cache artifact; reproducible and should stay out of the workspace.",
        )

    if top in {"_adb_crash_collect_20260415", "_adb_crash_collect_20260415_refresh"}:
        return (
            "High",
            "Archive",
            "Crash forensics and bugreport evidence referenced by exploit analysis.",
        )

    if path_str.startswith("cve-2023-4622-arm64/forensics/"):
        return (
            "High",
            "Archive",
            "Crash forensics and bugreport evidence referenced by exploit analysis.",
        )

    if path_str.startswith("cve-2023-4622-arm64/phase4_exploit/vmlinux_rebuilt"):
        return (
            "High",
            "Keep",
            "Kernel image / IDA database; large, generated-looking, but usually the highest-value reverse-engineering state.",
        )

    if "ion_traces/" in path_str:
        return (
            "High",
            "Archive",
            "Runtime trace evidence supporting phase4 conclusions.",
        )

    if path_str == "exploit-gatebench/runs/score_history.jsonl":
        return (
            "Medium",
            "Archive",
            "Generated experiment history; useful evidence, but not core source.",
        )

    if suffix in {".md", ".yaml", ".toml"} or name == "Makefile":
        return (
            "High",
            "Keep",
            "Handwritten project knowledge, configuration, or build logic.",
        )

    if suffix in {".c", ".h", ".py", ".sh"}:
        return (
            "High",
            "Keep",
            "Primary source code or scripting logic.",
        )

    if suffix in {".txt", ".zip"}:
        return (
            "High",
            "Archive",
            "Evidence or captured output worth preserving, but better grouped under a dedicated evidence area.",
        )

    if suffix in {".json", ".jsonl"}:
        return (
            "Medium",
            "Keep",
            "Structured project state or tooling metadata; keep, but separate generated state from source where possible.",
        )

    if is_elf and has_source_pair(ROOT / rel_path):
        return (
            "Medium",
            "Move/Ignore",
            "Compiled binary with an obvious source pair; operationally useful, but not repository-grade content.",
        )

    if is_elf:
        return (
            "Medium",
            "Review",
            "Binary artifact without an obvious local source pair; keep for now until provenance is clarified.",
        )

    return (
        "Medium",
        "Review",
        f"Unclassified file type ({file_desc}); needs case-by-case judgement.",
    )


def collect_entries() -> list[Entry]:
    tracked, untracked, modified = gather_git_states()
    entries: list[Entry] = []
    for path in sorted(ROOT.rglob("*")):
        if not path.is_file():
            continue
        if any(part in IGNORED_PARTS for part in path.parts):
            continue
        rel_path = path.relative_to(ROOT)
        if path == REPORT:
            continue
        git_state = git_state_for(rel_path, tracked, untracked, modified)
        file_desc = describe_file(path)
        value, action, reason = classify(rel_path, file_desc)
        entries.append(
            Entry(
                path=rel_path,
                size=path.stat().st_size,
                git_state=git_state,
                file_desc=file_desc,
                value=value,
                action=action,
                reason=reason,
            )
        )
    return entries


def summary_block(entries: list[Entry]) -> str:
    by_value: dict[str, int] = {}
    by_action: dict[str, int] = {}
    by_top: dict[str, int] = {}
    for entry in entries:
        by_value[entry.value] = by_value.get(entry.value, 0) + 1
        by_action[entry.action] = by_action.get(entry.action, 0) + 1
        top = entry.path.parts[0] if len(entry.path.parts) > 1 else "<root>"
        by_top[top] = by_top.get(top, 0) + 1

    lines = [
        "## Summary",
        "",
        f"- Files audited: `{len(entries)}`",
        "- Value split: "
        + ", ".join(f"`{key}={by_value[key]}`" for key in sorted(by_value)),
        "- Action split: "
        + ", ".join(f"`{key}={by_action[key]}`" for key in sorted(by_action)),
        "- Top-level split: "
        + ", ".join(f"`{key}={by_top[key]}`" for key in sorted(by_top)),
        "",
        "## Directory Verdicts",
        "",
        "- `cve-2023-4622-arm64`: highest-value workspace; source, exploit experiments, evidence, and reverse DB are mixed together and should be separated logically, not pruned aggressively.",
        "- `exploit-gatebench`: already well-structured; keep intact and avoid mixing generated run state with source.",
        "- `cve-2021-3444-bpf` and `cve-2022-38181-mali`: useful historical references; keep source, but treat compiled ELF files as rebuildable outputs unless provenance is unique.",
        "- `cve-2023-4622-arm64/forensics`: high-value archived crash evidence; keep it grouped by date and experiment instead of leaving loose root-level dumps.",
        "- `scripts`: keep scripts; delete caches only.",
        "",
        "## Immediate Safe Cleanup",
        "",
        "- Delete `.DS_Store` and `__pycache__/` artifacts.",
        "- Ignore future `.DS_Store` / `*.pyc` / `__pycache__/` noise at repo root.",
        "- Do not delete `vmlinux_rebuilt*`, panic logs, bugreport zips, or handwritten markdown analyses.",
        "",
    ]
    return "\n".join(lines)


def table_for_group(group: str, entries: list[Entry]) -> str:
    lines = [
        f"## {group}",
        "",
        "| Path | Size | Git | Value | Action | Reason |",
        "|---|---:|---|---|---|---|",
    ]
    for entry in entries:
        lines.append(
            "| "
            + f"`{entry.path.as_posix()}` | {human_size(entry.size)} | `{entry.git_state}` | "
            + f"`{entry.value}` | `{entry.action}` | {entry.reason} |"
        )
    lines.append("")
    return "\n".join(lines)


def build_report(entries: list[Entry]) -> str:
    groups: dict[str, list[Entry]] = {}
    for entry in entries:
        group = entry.path.parts[0] if len(entry.path.parts) > 1 else "<root>"
        groups.setdefault(group, []).append(entry)

    sections = [
        "# MTK File Audit",
        "",
        "This report is generated by `scripts/audit_mtk_files.py`.",
        "",
        summary_block(entries),
    ]
    for group in sorted(groups):
        sections.append(table_for_group(group, groups[group]))
    return "\n".join(sections)


def main() -> int:
    entries = collect_entries()
    REPORT.write_text(build_report(entries), encoding="utf-8")
    print(f"Wrote {REPORT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
