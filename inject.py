#!/usr/bin/env python3
"""
Build Copy That Building and inject the agent into a running Fabric KnotClient.

Confirmation uses a temp handshake file written by the agent (reliable),
with latest.log tailing only as a secondary signal.

Usage:
    python inject.py
    python inject.py --no-build
    python inject.py --pid 12345
    python inject.py --jar path/to.jar
    python inject.py --timeout 25

Author: Mikasa
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import time
import uuid
from pathlib import Path

JAVA_HOME = Path(os.environ.get("JAVA_HOME", r"C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"))
STATUS_FILE = Path(os.environ.get("TEMP") or os.environ.get("TMP") or "/tmp") / "mikasa-copy-building-inject.status"

ROOT = Path(__file__).resolve().parent
BUILD_LIBS = ROOT / "build" / "libs"
ATTACHER_CLASSES = ROOT / "build" / "attacher-classes"
ATTACHER_MAIN = "com.mikasa.copybuilding.attacher.Attacher"
GRADLE_PROPS = ROOT / "gradle.properties"


def log(msg: str) -> None:
    print(f"[inject] {msg}", flush=True)


def jps_path() -> Path:
    exe = "jps.exe" if os.name == "nt" else "jps"
    candidate = JAVA_HOME / "bin" / exe
    return candidate if candidate.exists() else Path(exe)


def java_path() -> Path:
    exe = "java.exe" if os.name == "nt" else "java"
    candidate = JAVA_HOME / "bin" / exe
    return candidate if candidate.exists() else Path(exe)


def gradlew() -> str:
    return "gradlew.bat" if os.name == "nt" else "./gradlew"


def read_mod_version() -> str | None:
    if not GRADLE_PROPS.exists():
        return None
    for line in GRADLE_PROPS.read_text(encoding="utf-8", errors="replace").splitlines():
        line = line.strip()
        if line.startswith("version="):
            return line.split("=", 1)[1].strip()
    return None


def expected_jar_name() -> str:
    ver = read_mod_version()
    base = "Mikasa-copy-building"
    if ver:
        return f"{base}-{ver}.jar"
    return f"{base}.jar"


def resolve_jar(explicit: Path | None, allow_build_fallback: bool) -> Path:
    if explicit is not None:
        return explicit.resolve()
    preferred = BUILD_LIBS / expected_jar_name()
    if preferred.exists():
        return preferred
    candidates = [
        p for p in BUILD_LIBS.glob("Mikasa-copy-building-*.jar")
        if "sources" not in p.name and "dev" not in p.name and "javadoc" not in p.name
    ]
    if not candidates:
        if allow_build_fallback:
            raise SystemExit(f"JAR not found in {BUILD_LIBS}")
        raise SystemExit(f"JAR not found: {preferred}")
    jar = max(candidates, key=lambda p: p.stat().st_mtime)
    log(f"Using newest jar: {jar.name}")
    return jar


def run(cmd, cwd=None, check=True):
    log(f"$ {' '.join(str(c) for c in cmd)}")
    result = subprocess.run(
        [str(c) for c in cmd],
        cwd=cwd or ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if result.stdout:
        print(result.stdout, end="")
    if result.stderr:
        print(result.stderr, end="", file=sys.stderr)
    if check and result.returncode != 0:
        raise SystemExit(f"Command failed with code {result.returncode}")
    return result


def build() -> Path:
    log("Building mod/agent jar...")
    run([gradlew(), "build"], check=True)
    jar = resolve_jar(None, allow_build_fallback=True)
    log(f"JAR: {jar}")
    return jar


def find_fabric_pid() -> int:
    log("Looking for Fabric KnotClient via jps...")
    result = run([jps_path(), "-l"], check=True)
    matches: list[tuple[int, str]] = []
    for line in result.stdout.splitlines():
        line = line.strip()
        if not line or line.startswith("Jps"):
            continue
        m = re.match(r"^(\d+)\s+(.+)$", line)
        if not m:
            continue
        pid, cls = m.group(1), m.group(2)
        if "KnotClient" in cls:
            matches.append((int(pid), cls))
    if not matches:
        raise SystemExit("Fabric KnotClient not found. Start Minecraft with Fabric first.")
    if len(matches) > 1:
        log(f"Multiple KnotClient processes: {matches}. Using first.")
    pid, cls = matches[0]
    log(f"Found Fabric client: PID={pid} ({cls})")
    return pid


def build_attacher() -> None:
    log("Building Attacher...")
    run([gradlew(), "buildAttacher"], check=True)
    if not ATTACHER_CLASSES.exists():
        raise SystemExit(f"Missing {ATTACHER_CLASSES}")


def clear_status_file() -> None:
    try:
        if STATUS_FILE.exists():
            STATUS_FILE.unlink()
    except OSError as e:
        log(f"Could not clear status file {STATUS_FILE}: {e}")


def inject(pid: int, jar: Path, token: str) -> None:
    log(f"Injecting agent into PID {pid}...")
    log(f"Handshake file: {STATUS_FILE}")
    cmd = [
        java_path(),
        "--add-modules", "jdk.attach",
        "-cp", str(ATTACHER_CLASSES),
        ATTACHER_MAIN,
        str(pid),
        str(jar.resolve()),
        token,
    ]
    run(cmd, check=True)


def parse_status(text: str) -> dict[str, str]:
    data: dict[str, str] = {}
    for line in text.splitlines():
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        data[key.strip().lower()] = value.strip()
    return data


def wait_handshake(token: str, timeout: float) -> dict[str, str] | None:
    """Poll temp status file written by the agent (primary confirmation)."""
    deadline = time.time() + timeout
    last_err = None
    while time.time() < deadline:
        try:
            if STATUS_FILE.is_file():
                raw = STATUS_FILE.read_text(encoding="utf-8", errors="replace")
                data = parse_status(raw)
                file_token = data.get("token", "")
                status = data.get("status", "")
                if file_token and file_token != token:
                    # Stale file from a previous run; keep waiting.
                    last_err = f"stale token in status file ({file_token[:8]}…)"
                elif status:
                    log(f"Handshake: status={status} detail={data.get('detail', '')}")
                    return data
        except OSError as e:
            last_err = str(e)
        time.sleep(0.15)
    if last_err:
        log(f"Handshake wait ended ({last_err})")
    return None


def candidate_log_paths() -> list[Path]:
    paths: list[Path] = []
    appdata = Path(os.environ.get("APPDATA", ""))
    home = Path.home()

    # Official launcher
    if appdata:
        paths.append(appdata / ".minecraft" / "logs" / "latest.log")

    # Common custom / multi-instance layouts (best-effort)
    search_roots = []
    if appdata:
        search_roots.extend([
            appdata / ".minecraft",
            appdata / "PrismLauncher" / "instances",
            appdata / "PolyMC" / "instances",
            appdata / "MultiMC" / "instances",
        ])
    search_roots.append(home / ".minecraft")

    for root in search_roots:
        if not root.exists():
            continue
        # Direct latest.log
        direct = root / "logs" / "latest.log"
        if direct not in paths:
            paths.append(direct)
        # Instance folders
        try:
            for inst in root.iterdir():
                if not inst.is_dir():
                    continue
                for rel in (
                    Path("minecraft") / "logs" / "latest.log",
                    Path(".minecraft") / "logs" / "latest.log",
                    Path("logs") / "latest.log",
                ):
                    p = inst / rel
                    if p not in paths:
                        paths.append(p)
        except OSError:
            pass

    # Deduplicate while preserving order; keep existing files first in wait loop
    seen: set[Path] = set()
    ordered: list[Path] = []
    for p in paths:
        rp = p.resolve() if p.exists() else p
        if rp in seen:
            continue
        seen.add(rp)
        ordered.append(p)
    return ordered


def scan_logs_for_marker(marker: str, since_sizes: dict[Path, int]) -> list[str]:
    collected: list[str] = []
    for path, start_size in since_sizes.items():
        try:
            if not path.is_file():
                continue
            with path.open("r", encoding="utf-8", errors="replace") as f:
                f.seek(min(start_size, path.stat().st_size))
                for line in f:
                    if marker in line:
                        collected.append(line.rstrip())
        except OSError:
            continue
    return collected


def wait_logs_secondary(timeout: float) -> list[str]:
    """Secondary: scan known latest.log paths (often miss System.out)."""
    paths = [p for p in candidate_log_paths() if p.is_file()]
    if not paths:
        log("No latest.log found for secondary check.")
        return []

    log(f"Secondary log scan ({len(paths)} path(s)), first: {paths[0]}")
    since = {p: p.stat().st_size for p in paths}
    deadline = time.time() + timeout
    collected: list[str] = []
    while time.time() < deadline:
        for line in scan_logs_for_marker("[CopyBuildingAgent]", since):
            if line not in collected:
                collected.append(line)
                print(f"  {line}")
        if any(
            "agent runtime started" in l
            or "already loaded via mods" in l
            or "already injected" in l
            for l in collected
        ):
            break
        time.sleep(0.4)
    return collected


def classify_result(status: str | None, log_lines: list[str]) -> tuple[str, int]:
    """Returns (message, exit_code)."""
    if status == "started":
        return "SUCCESS: agent runtime started (Ctrl+M+I toggles menu).", 0
    if status == "mods_loaded":
        return "SKIP: mod already loaded from mods/. Remove it to use agent mode.", 0
    if status == "already_injected":
        return "SKIP: agent already injected.", 0
    if status == "no_knot":
        return "FAIL: Minecraft ClassLoader not found (is Fabric client fully started?).", 1
    if status == "failed":
        return "FAIL: agent reported failure — see game console.", 1

    if any("agent runtime started" in l for l in log_lines):
        return "SUCCESS (via log): agent runtime started.", 0
    if any("already loaded via mods" in l for l in log_lines):
        return "SKIP (via log): mod already loaded from mods/.", 0
    if any("already injected" in l for l in log_lines):
        return "SKIP (via log): agent already injected.", 0
    if log_lines:
        return "PARTIAL: agent log lines seen, but final status unknown.", 1
    return (
        "Could not confirm injection. Attach may still have worked — "
        f"check game console or {STATUS_FILE}",
        1,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Inject Copy That Building agent into Fabric KnotClient")
    parser.add_argument("--no-build", action="store_true")
    parser.add_argument("--pid", type=int)
    parser.add_argument("--jar", type=Path)
    parser.add_argument("--timeout", type=float, default=20.0,
                        help="Seconds to wait for handshake (default 20)")
    args = parser.parse_args()

    if args.jar:
        jar = args.jar.resolve()
    elif args.no_build:
        jar = resolve_jar(None, allow_build_fallback=False)
    else:
        jar = build()

    if not jar.exists():
        raise SystemExit(f"JAR not found: {jar}")

    pid = args.pid if args.pid else find_fabric_pid()
    build_attacher()

    token = uuid.uuid4().hex
    clear_status_file()
    inject(pid, jar, token)

    # loadAgent blocks until agentmain returns, so handshake is usually instant.
    log("Waiting for agent handshake file...")
    data = wait_handshake(token, timeout=max(2.0, args.timeout))
    status = data.get("status") if data else None

    log_lines: list[str] = []
    if status is None:
        log("No handshake yet — trying latest.log as fallback...")
        log_lines = wait_logs_secondary(timeout=min(8.0, max(3.0, args.timeout / 2)))

    msg, code = classify_result(status, log_lines)
    log(msg)
    if code != 0 and STATUS_FILE.exists():
        try:
            log(f"Status file contents:\n{STATUS_FILE.read_text(encoding='utf-8', errors='replace')}")
        except OSError:
            pass
    return code


if __name__ == "__main__":
    sys.exit(main())
