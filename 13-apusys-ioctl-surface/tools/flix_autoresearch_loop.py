#!/usr/bin/env python3
"""Run autoresearch-style FLIX learner iterations until score stalls."""

from __future__ import annotations

import argparse
import csv
import shutil
import subprocess
import sys
import time
from pathlib import Path


DEFAULT_PROFILES = (
    "phase8_strict",
    "phase16_boost",
    "control_heavy",
    "pcrel_landing",
    "pcrel_control_heavy",
    "pcrel_internal_strict",
    "pcrel_internal_broad",
    "slot_grammar_strict",
    "slot_grammar_broad",
    "operand_model_strict",
    "operand_model_broad",
    "cfg_edges_strict",
    "cfg_edges_broad",
    "block_extent_strict",
    "block_extent_broad",
    "template_strict",
    "template_aggressive",
    "landing_strict",
    "c1_strong",
)

PROFILE_DESCRIPTIONS = {
    "baseline": "fixed floor before loop iteration",
    "phase8_strict": "raise 8-byte phase alignment weight",
    "phase16_boost": "raise 16-byte phase alignment weight",
    "control_heavy": "raise exact/near control-flow vote weight",
    "pcrel_landing": "use constrained PC-relative slot candidates as landing evidence",
    "pcrel_control_heavy": "combine control-heavy scoring with PC-relative slot landing evidence",
    "pcrel_internal_strict": "accept only strong dense PC-relative control targets as internal boundaries",
    "pcrel_internal_broad": "also accept dense insn PC-relative targets as internal boundary candidates",
    "slot_grammar_strict": "learn stable PC-relative slot templates with strong internal targets",
    "slot_grammar_broad": "also accept small stable PC-relative slot templates",
    "operand_model_strict": "learn PC-relative operand family models with a strict reject guard",
    "operand_model_broad": "accept broader PC-relative operand family models with a reject guard",
    "cfg_edges_strict": "materialize strong PC-relative CFG edges from accepted operand models",
    "cfg_edges_broad": "materialize broader PC-relative CFG edges and clusters from accepted operand models",
    "block_extent_strict": "infer strict basic-block extents from PC-relative source slots",
    "block_extent_broad": "infer broader basic-block extents and successor edges from PC-relative source slots",
    "template_strict": "require stronger D template evidence",
    "template_aggressive": "accept D template evidence earlier",
    "landing_strict": "make A/B/C -0xb landing classification stricter",
    "c1_strong": "favor C[1] 0x700169a4 as FLK control anchor",
}


def read_metrics(out_dir: Path) -> dict[str, str]:
    with (out_dir / "score_metrics.csv").open(newline="") as f:
        return {row["metric"]: row["value"] for row in csv.DictReader(f)}


def append_tsv(path: Path, row: list[object]) -> None:
    with path.open("a", newline="") as f:
        writer = csv.writer(f, delimiter="\t", lineterminator="\n")
        writer.writerow(row)


def run_learner(
    learner: Path,
    elf: Path,
    out_dir: Path,
    profile: str,
    timeout_seconds: int,
) -> tuple[str, dict[str, str], float]:
    out_dir.mkdir(parents=True, exist_ok=True)
    cmd = [
        sys.executable,
        str(learner),
        str(elf),
        "--out-dir",
        str(out_dir),
        "--profile",
        profile,
    ]
    start = time.monotonic()
    proc = subprocess.run(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        timeout=timeout_seconds,
        check=False,
    )
    elapsed = time.monotonic() - start
    (out_dir / "run.log").write_text(proc.stdout)
    if proc.returncode != 0:
        raise RuntimeError("learner failed for %s; see %s" % (profile, out_dir / "run.log"))
    return proc.stdout, read_metrics(out_dir), elapsed


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("elf", help="APUNN core ELF")
    ap.add_argument("--out-dir", default="firmware/apunn/flix_bundle_learning",
                    help="best-profile output directory")
    ap.add_argument("--run-dir", default="",
                    help="per-iteration output directory; defaults to OUT_DIR/autoresearch_runs")
    ap.add_argument("--patience", type=int, default=5,
                    help="stop after this many consecutive non-improving iterations")
    ap.add_argument("--timeout-seconds", type=int, default=600)
    ap.add_argument("--max-iterations", type=int, default=64)
    ap.add_argument("--profiles", nargs="*", default=list(DEFAULT_PROFILES),
                    help="profiles to cycle after baseline")
    args = ap.parse_args()

    root = Path(__file__).resolve().parents[1]
    learner = root / "tools" / "flix_bundle_learn.py"
    elf = Path(args.elf)
    out_dir = Path(args.out_dir)
    run_dir = Path(args.run_dir) if args.run_dir else out_dir / "autoresearch_runs"
    run_dir.mkdir(parents=True, exist_ok=True)
    for child in run_dir.iterdir():
        if child.is_dir() and child.name[:3].isdigit():
            shutil.rmtree(child)
    results_path = run_dir / "results.tsv"
    results_path.write_text(
        "iteration\tprofile\tscore_version\tflix_score\tresolved_boundary_ratio\t"
        "ambiguous_count\tc1_candidate_bundle_start\td_template_ratio\t"
        "pcrel_supported_boundaries\tinternal_control_boundaries\t"
        "internal_strong_boundaries\tinternal_medium_boundaries\t"
        "accepted_slot_templates\thigh_volume_slot_templates\t"
        "accepted_operand_models\toperand_model_internal_edges\t"
        "negative_operand_models\tcfg_edges\tcfg_nodes\taccepted_cfg_clusters\t"
        "basic_blocks\tterminated_blocks\tblock_edges\t"
        "status\tdescription\tseconds\n"
    )

    best_profile = "baseline"
    best_score = -10**9
    no_improve = 0

    schedule = ["baseline"]
    while len(schedule) < args.max_iterations + 1:
        schedule.extend(args.profiles)

    for iteration, profile in enumerate(schedule[: args.max_iterations + 1]):
        iter_out = run_dir / ("%03d_%s" % (iteration, profile))
        try:
            _stdout, metrics, elapsed = run_learner(
                learner, elf, iter_out, profile, args.timeout_seconds
            )
            score = int(metrics["flix_score"])
            if iteration == 0:
                status = "floor"
                best_score = score
                best_profile = profile
            elif score > best_score:
                status = "keep"
                best_score = score
                best_profile = profile
                no_improve = 0
            else:
                status = "discard"
                no_improve += 1
            append_tsv(
                results_path,
                [
                    iteration,
                    profile,
                    metrics.get("score_version", ""),
                    score,
                    metrics["resolved_boundary_ratio"],
                    metrics["ambiguous_count"],
                    metrics["c1_candidate_bundle_start"],
                    metrics["d_template_ratio"],
                    metrics.get("pcrel_supported_boundaries", ""),
                    metrics.get("internal_control_boundaries", ""),
                    metrics.get("internal_strong_boundaries", ""),
                    metrics.get("internal_medium_boundaries", ""),
                    metrics.get("accepted_slot_templates", ""),
                    metrics.get("high_volume_slot_templates", ""),
                    metrics.get("accepted_operand_models", ""),
                    metrics.get("operand_model_internal_edges", ""),
                    metrics.get("negative_operand_models", ""),
                    metrics.get("cfg_edges", ""),
                    metrics.get("cfg_nodes", ""),
                    metrics.get("accepted_cfg_clusters", ""),
                    metrics.get("basic_blocks", ""),
                    metrics.get("terminated_blocks", ""),
                    metrics.get("block_edges", ""),
                    status,
                    PROFILE_DESCRIPTIONS.get(profile, profile),
                    "%.3f" % elapsed,
                ],
            )
            print(
                "iteration=%d profile=%s score=%d status=%s best=%d:%s no_improve=%d"
                % (iteration, profile, score, status, best_score, best_profile, no_improve)
            )
            if iteration > 0 and no_improve >= args.patience:
                break
        except Exception as exc:
            if iteration == 0:
                raise
            no_improve += 1
            append_tsv(
                results_path,
                [
                    iteration,
                    profile,
                    "",
                    0,
                    "0.0000",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "crash",
                    "%s: %s" % (PROFILE_DESCRIPTIONS.get(profile, profile), exc),
                    "0.000",
                ],
            )
            print(
                "iteration=%d profile=%s score=0 status=crash best=%d:%s no_improve=%d"
                % (iteration, profile, best_score, best_profile, no_improve)
            )
            if no_improve >= args.patience:
                break

    _stdout, final_metrics, _elapsed = run_learner(
        learner, elf, out_dir, best_profile, args.timeout_seconds
    )
    print("best_profile=%s" % best_profile)
    print("best_flix_score=%s" % final_metrics["flix_score"])
    print("results=%s" % results_path)
    print("final_out=%s" % out_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
