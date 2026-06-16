# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Repo Is

Security research workspace for privilege escalation on a MediaTek MT6893 (Dimensity 1200) / MT8797 Android tablet. Target: `uid=1000(system)` → `uid=0(root)`, starting from a `system_app` shell obtained via CVE-2024-31317.

**Device**: kernel 4.19.191, Mali Valhall r32p1, SPL 2023-06-05, SELinux enforcing, Android 13 user build (`7FPE0824B0801372`).

## Research Status (as of 2026-06-16)

**Current position**: `uid=1000(system)` / `u:r:system_app:s0`, arbitrary `ioctl` from Java/ART syscall primitive.

**Active priorities** (see `PIVOT_ASSESSMENT.md` for full ranking):
1. **CVE-2024-20032 (aee)** — permission bypass confirmed; `system_app` connects to root-running `@android:aee_aed` socket; ANR trace leak from `system_server` works; KASLR not yet proven (coredump gates blocked)
2. **CVE-2024-20118/119 (mms)** — write-what-where primitives in `mms` multimedia scheduler, reached via SurfaceFlinger binder → `/dev/mtk-mdp`; `system_app` cannot open `mtk_mdp_device` directly
3. **APUSYS** — `/dev/apusys` open from `system_app`; VPU ucmd dispatch confirmed; IOVA reuse and descriptor-plane redirect are the live leads

**Dead paths**: all Mali CVEs (patched or blocked by `no_user_free_count`), CVE-2023-4622 AF_UNIX (`SOCK_DEAD` ordering), direct `/dev/ion`, `/dev/ashmem`.

## Building C PoCs (NDK)

Requires Android NDK r25+ with `aarch64-linux-android33-clang`.

```bash
# Build a single PoC
cd <NN>-<topic>/poc
make -f ../../common/Makefile.ndk

# Push binary to device
make -f ../../common/Makefile.ndk push
```

`common/Makefile.ndk` auto-detects NDK via `$ANDROID_NDK_HOME` or `$NDK`. It statically links all binaries for aarch64/API-33.

## Running Java PoCs (system_app shell)

All Java probes go through `run_system_app_probe.py`, which compiles Java → DEX and uploads/runs inside the existing `system_app` bind shell.

**Prerequisites**: `javac` (Java 8 source/target), `d8` (Android build-tools), `adb`, and an active `system_app` bind shell forwarded to a local TCP port.

```bash
# Use existing system_app shell
python3 13-apusys-ioctl-surface/poc/run_system_app_probe.py \
  -s 7FPE0824B0801372 --local-port 48888 --probe aee-session --mode ""

# Rebuild the system_app shell (via CVE-2024-31317) and run a probe
python3 13-apusys-ioctl-surface/poc/run_system_app_probe.py \
  -s 7FPE0824B0801372 --local-port 48888 --rebuild-shell --probe apusys

# Available --probe values:
# apusys, xrp-wrapper, ged-bridge, aee-socket, aee-session,
# aslr-extract, drm-trigger, eemg-proc, eemg-write
```

Results land in `poc-run-results/<date>-batch/`.

## Repository Structure

```
mt6893/
├── 00-*/            # Original CVE-2023-4622 ARM64 workspace (historical)
├── 01-*/..22-*/     # Topic writeups + PoCs (numbered by investigation order)
├── common/          # Shared: Makefile.ndk, mali.h
├── docs/            # Cross-topic: CVE search results, closure notes
├── exploit-gatebench/  # Autonomous experiment scoring lab (CVE-2023-4622 only)
├── poc-run-results/ # Captured runtime evidence (date-batched)
└── scripts/         # Repo maintenance (audit_mtk_files.py)
```

Each `NN-<topic>/` directory contains:
- `README.md` — current status + runtime findings
- `IDA_HANDOFF.md` — IDA Pro reverse engineering context and conclusions
- `RISK_ASSESSMENT.md` — exploitation feasibility assessment (where applicable)
- `poc/` — Java probes (`.java`) and/or C PoCs (`.c`)

## IDA Pro Integration

IDA databases live inside individual topic directories (e.g., `20-cve-2024-20032-aee/aee_aed64.i64`, `07-cve-2023-32836-display-overflow/vmlinux.bin.i64`). The kernel IDB (`vmlinux.bin.i64` in `07-`) is shared across multiple CVE analyses. An MCP server (`mcp__ida-pro-mcp__*` tools) is available for live IDA queries when IDA Pro is open.

## Key Files for Active Work

| File | Purpose |
|---|---|
| `PIVOT_ASSESSMENT.md` | Current priority ranking + dead-end table |
| `20-cve-2024-20032-aee/README.md` | AEE session protocol + confirmed exploit chain |
| `20-cve-2024-20032-aee/IDA_HANDOFF.md` | Wire protocol layout (24-byte AE_IND/REQ/RSP headers) |
| `20-cve-2024-20032-aee/CURRENT_KASLR_GATE_NOTES.md` | Coredump gate state (all disabled on device) |
| `18-cve-2024-20118-mms/IDA_HANDOFF.md` | mms attribution correction + GED bridge map |
| `13-apusys-ioctl-surface/README.md` | APUSYS dispatch chain + VPU ucmd findings |
| `docs/CVE_SEARCH_2024_2026.md` | Full CVE table for MT6893, 2024–2026 |

## exploit-gatebench

Standalone autoresearch lab for CVE-2023-4622 scoring. Operates from `exploit-gatebench/` only.

```bash
cd exploit-gatebench
python3 scripts/run_experiment.py --experiment detect_only --strategy baseline --dry-run
python3 scripts/score_run.py --run runs/<run_id>
python3 scripts/scoreboard.py --profile detect_only --last 20
```

Default mode is `--dry-run`; pass `--execute` explicitly to run real commands against the device.
