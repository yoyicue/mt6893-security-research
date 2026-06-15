#!/usr/bin/env python3
from __future__ import annotations

import argparse
import glob
import json
import pathlib
import shutil
import sys
import tempfile
from dataclasses import dataclass
from typing import Any


DEFAULT_TARGETS = (
    "~/.claude/history.jsonl",
    "~/.claude/projects/**/*.jsonl",
)
DROP = object()


@dataclass
class FileStats:
    path: pathlib.Path
    lines_total: int = 0
    rows_removed: int = 0
    nested_items_removed: int = 0
    parent_links_rewritten: int = 0
    parse_errors: int = 0
    changed: bool = False


def parse_args() -> argparse.Namespace:
    ap = argparse.ArgumentParser(
        description=(
            "清理 Claude Code 会话 jsonl 和 ~/.claude/history.jsonl 中的 API Error 相关记录。"
        )
    )
    ap.add_argument(
        "targets",
        nargs="*",
        help=(
            "目标文件、目录或 glob。默认扫描 ~/.claude/history.jsonl 和 "
            "~/.claude/projects/**/*.jsonl"
        ),
    )
    ap.add_argument(
        "--apply",
        action="store_true",
        help="原地写回文件。默认只做 dry-run 预览。",
    )
    ap.add_argument(
        "--backup-ext",
        default=".bak",
        help="写回时的备份后缀，默认: .bak",
    )
    ap.add_argument(
        "--no-backup",
        action="store_true",
        help="写回时不保留备份文件。",
    )
    ap.add_argument(
        "--verbose",
        action="store_true",
        help="输出每个文件的详细变更统计。",
    )
    return ap.parse_args()


def normalize_text(value: str) -> str:
    return " ".join(value.split()).strip().lower()


def looks_like_api_error_text(value: Any) -> bool:
    if not isinstance(value, str):
        return False
    text = normalize_text(value)
    return (
        text.startswith("api error")
        or text.startswith("⎿ api error")
        or text.startswith("please run /login · api error")
        or " · api error:" in text
    )


def text_item_is_api_error(value: Any) -> bool:
    return (
        isinstance(value, dict)
        and value.get("type") == "text"
        and looks_like_api_error_text(value.get("text"))
    )


def message_is_api_error(message: Any) -> bool:
    if not isinstance(message, dict):
        return False
    content = message.get("content")
    if isinstance(content, str):
        return looks_like_api_error_text(content)
    if not isinstance(content, list):
        return False

    text_items = [item for item in content if text_item_is_api_error(item)]
    if text_items and len(text_items) == len(content):
        return True
    return False


def record_is_api_error(record: Any) -> bool:
    if not isinstance(record, dict):
        return False
    if record.get("isApiErrorMessage") is True:
        return True
    if looks_like_api_error_text(record.get("display")):
        return True
    if record.get("type") == "assistant" and message_is_api_error(record.get("message")):
        return True
    return False


def prune_api_error_nodes(value: Any) -> tuple[Any, int]:
    if isinstance(value, dict):
        if value.get("isApiErrorMessage") is True:
            return DROP, 1
        if text_item_is_api_error(value):
            return DROP, 1

        new_value: dict[str, Any] = {}
        removed = 0
        for key, child in value.items():
            cleaned_child, child_removed = prune_api_error_nodes(child)
            removed += child_removed
            if cleaned_child is DROP:
                continue
            new_value[key] = cleaned_child
        return new_value, removed

    if isinstance(value, list):
        new_items: list[Any] = []
        removed = 0
        for item in value:
            cleaned_item, item_removed = prune_api_error_nodes(item)
            removed += item_removed
            if cleaned_item is DROP:
                continue
            new_items.append(cleaned_item)
        return new_items, removed

    return value, 0


def record_is_empty_after_cleanup(record: Any) -> bool:
    if not isinstance(record, dict):
        return False
    message = record.get("message")
    if not isinstance(message, dict):
        return False
    content = message.get("content")
    return content == [] or content == ""


def expand_targets(targets: list[str]) -> list[pathlib.Path]:
    raw_targets = targets or list(DEFAULT_TARGETS)
    found: dict[str, pathlib.Path] = {}

    for raw in raw_targets:
        expanded = pathlib.Path(raw).expanduser()
        if any(ch in raw for ch in "*?[]"):
            for match in glob.glob(str(expanded), recursive=True):
                path = pathlib.Path(match)
                if path.is_file():
                    found[str(path.resolve())] = path.resolve()
            continue

        if expanded.is_dir():
            for path in expanded.rglob("*.jsonl"):
                if path.is_file():
                    found[str(path.resolve())] = path.resolve()
            continue

        if expanded.exists() and expanded.is_file():
            found[str(expanded.resolve())] = expanded.resolve()
            continue

        print(f"warning: target not found: {raw}", file=sys.stderr)

    return [found[key] for key in sorted(found)]


def relink_parent_uuid(record: Any, parent_map: dict[str, str | None]) -> int:
    if not isinstance(record, dict):
        return 0
    parent = record.get("parentUuid")
    if not isinstance(parent, str):
        return 0

    new_parent = parent
    visited: set[str] = set()
    while isinstance(new_parent, str) and new_parent in parent_map and new_parent not in visited:
        visited.add(new_parent)
        new_parent = parent_map[new_parent]

    if new_parent != parent:
        record["parentUuid"] = new_parent
        return 1
    return 0


def process_file(path: pathlib.Path) -> tuple[list[str], FileStats]:
    stats = FileStats(path=path)
    output_lines: list[str] = []
    removed_parent_map: dict[str, str | None] = {}

    with path.open("r", encoding="utf-8") as fh:
        for raw_line in fh:
            stats.lines_total += 1
            line = raw_line.rstrip("\n")
            if not line.strip():
                output_lines.append(raw_line if raw_line.endswith("\n") else raw_line + "\n")
                continue

            try:
                record = json.loads(line)
            except json.JSONDecodeError:
                stats.parse_errors += 1
                output_lines.append(raw_line if raw_line.endswith("\n") else raw_line + "\n")
                continue

            if record_is_api_error(record):
                stats.rows_removed += 1
                record_uuid = record.get("uuid")
                if isinstance(record_uuid, str):
                    removed_parent_map[record_uuid] = record.get("parentUuid")
                continue

            cleaned_record, removed = prune_api_error_nodes(record)
            stats.nested_items_removed += removed

            if cleaned_record is DROP or record_is_empty_after_cleanup(cleaned_record):
                stats.rows_removed += 1
                record_uuid = record.get("uuid")
                if isinstance(record_uuid, str):
                    removed_parent_map[record_uuid] = record.get("parentUuid")
                continue

            stats.parent_links_rewritten += relink_parent_uuid(cleaned_record, removed_parent_map)
            output_lines.append(json.dumps(cleaned_record, ensure_ascii=False) + "\n")

    stats.changed = (
        stats.rows_removed > 0
        or stats.nested_items_removed > 0
        or stats.parent_links_rewritten > 0
    )
    return output_lines, stats


def write_back(path: pathlib.Path, lines: list[str], backup_ext: str | None) -> None:
    with tempfile.NamedTemporaryFile(
        "w",
        encoding="utf-8",
        dir=path.parent,
        delete=False,
    ) as tmp:
        tmp.writelines(lines)
        tmp_path = pathlib.Path(tmp.name)

    try:
        if backup_ext:
            backup_path = path.with_name(path.name + backup_ext)
            shutil.copy2(path, backup_path)
        tmp_path.replace(path)
    except Exception:
        tmp_path.unlink(missing_ok=True)
        raise


def main() -> int:
    args = parse_args()
    targets = expand_targets(args.targets)
    if not targets:
        print("没有找到可处理的 jsonl 文件。", file=sys.stderr)
        return 1

    changed_files = 0
    total_rows_removed = 0
    total_nested_removed = 0
    total_parent_rewrites = 0
    total_parse_errors = 0

    for path in targets:
        cleaned_lines, stats = process_file(path)
        total_rows_removed += stats.rows_removed
        total_nested_removed += stats.nested_items_removed
        total_parent_rewrites += stats.parent_links_rewritten
        total_parse_errors += stats.parse_errors

        if stats.changed:
            changed_files += 1
            if args.apply:
                backup_ext = None if args.no_backup else args.backup_ext
                write_back(path, cleaned_lines, backup_ext)

        if args.verbose or stats.changed:
            mode = "updated" if args.apply and stats.changed else "would update" if stats.changed else "unchanged"
            print(
                f"{mode}: {path} | removed_rows={stats.rows_removed} "
                f"removed_nested={stats.nested_items_removed} "
                f"relinked_parentUuid={stats.parent_links_rewritten} "
                f"parse_errors={stats.parse_errors}"
            )

    mode_label = "已写回" if args.apply else "dry-run"
    print(
        f"{mode_label}: scanned={len(targets)} changed={changed_files} "
        f"removed_rows={total_rows_removed} removed_nested={total_nested_removed} "
        f"relinked_parentUuid={total_parent_rewrites} parse_errors={total_parse_errors}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
