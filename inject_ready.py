#!/usr/bin/env python3
"""
Inject a prebuilt agent jar using a prebuilt Attacher (no Gradle).

Usage:
    python inject_ready.py <attacher> <mod.jar>
    python inject_ready.py                  # search both in the current working directory
    python inject_ready.py --pid 12345
    python inject_ready.py --timeout 25

<attacher> = classpath root containing com/mikasa/copybuilding/attacher/Attacher.class
             (or a path to Attacher.class itself)
<mod.jar>  = agent/mod jar

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

ATTACHER_MAIN = "com.mikasa.copybuilding.attacher.Attacher"
ATTACHER_CLASS_REL = Path("com") / "mikasa" / "copybuilding" / "attacher" / "Attacher.class"


def log(msg: str) -> None:
    print(f"[inject-ready] {msg}", flush=True)


def jps_path() -> Path:
    exe = "jps.exe" if os.name == "nt" else "jps"
    candidate = JAVA_HOME / "bin" / exe
    return candidate if candidate.exists() else Path(exe)


def java_path() -> Path:
    exe = "java.exe" if os.name == "nt" else "java"
    candidate = JAVA_HOME / "bin" / exe
    return candidate if candidate.exists() else Path(exe)


def run(cmd, check=True):
    log(f"$ {' '.join(str(c) for c in cmd)}")
    result = subprocess.run(
        [str(c) for c in cmd],
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


def attacher_cp_from_path(path: Path) -> Path | None:
    """Return classpath root if Attacher.class is found under path."""
    path = path.resolve()
    if path.is_file():
        if path.name != "Attacher.class":
            return None
        # .../com/mikasa/copybuilding/attacher/Attacher.class → classpath root
        try:
            rel = path.parent.relative_to(path.parents[4])  # attacher → copybuilding → mikasa → com → root
            if rel == Path("com") / "mikasa" / "copybuilding" / "attacher":
                return path.parents[4]
        except (IndexError, ValueError):
            pass
        # fallback: walk up while matching package suffix
        cur = path.parent
        expected = list(ATTACHER_CLASS_REL.parent.parts)[::-1]
        for part in expected:
            if cur.name != part:
                return None
            cur = cur.parent
        return cur

    if not path.is_dir():
        return None

    direct = path / ATTACHER_CLASS_REL
    if direct.is_file():
        return path

    # shallow search: common folder names / one level of nesting
    for candidate in [path, *path.iterdir()] if path.is_dir() else []:
        if not candidate.is_dir():
            continue
        hit = candidate / ATTACHER_CLASS_REL
        if hit.is_file():
            return candidate
    return None


def find_attacher_in_cwd(cwd: Path) -> Path:
    # Prefer obvious dirs
    for name in ("attacher-classes", "attacher", "build/attacher-classes"):
        cand = attacher_cp_from_path(cwd / name)
        if cand is not None:
            return cand

    # Any Attacher.class with the expected package layout under cwd
    matches: list[Path] = []
    for class_file in cwd.rglob("Attacher.class"):
        cp = attacher_cp_from_path(class_file)
        if cp is None:
            continue
        cp = cp.resolve()
        try:
            cp.relative_to(cwd.resolve())
            matches.append(cp)
        except ValueError:
            continue

    uniq = sorted({m for m in matches}, key=lambda p: len(str(p)))
    if len(uniq) == 1:
        return uniq[0]
    if len(uniq) > 1:
        listed = "\n  ".join(str(p) for p in uniq)
        raise SystemExit(f"Multiple Attacher classpath roots in {cwd}:\n  {listed}\nPass the path explicitly.")
    raise SystemExit(
        f"Attacher not found in {cwd}.\n"
        f"Expected folder with {ATTACHER_CLASS_REL.as_posix()} or pass path as 1st argument."
    )


def find_mod_jar_in_cwd(cwd: Path) -> Path:
    preferred = [
        p for p in cwd.glob("Mikasa-copy-building*.jar")
        if p.is_file() and "sources" not in p.name and "dev" not in p.name and "javadoc" not in p.name
    ]
    if len(preferred) == 1:
        return preferred[0].resolve()
    if len(preferred) > 1:
        jar = max(preferred, key=lambda p: p.stat().st_mtime)
        log(f"Multiple mod jars; using newest: {jar.name}")
        return jar.resolve()

    any_jars = [
        p for p in cwd.glob("*.jar")
        if p.is_file() and "sources" not in p.name and "javadoc" not in p.name
    ]
    if len(any_jars) == 1:
        log(f"Using jar in cwd: {any_jars[0].name}")
        return any_jars[0].resolve()
    if len(any_jars) > 1:
        listed = "\n  ".join(p.name for p in any_jars)
        raise SystemExit(f"Multiple jars in {cwd}:\n  {listed}\nPass mod jar path as 2nd argument.")
    raise SystemExit(
        f"Mod jar not found in {cwd}.\n"
        f"Expected Mikasa-copy-building*.jar (or a single *.jar) or pass path as 2nd argument."
    )


def resolve_attacher(explicit: Path | None, cwd: Path) -> Path:
    if explicit is not None:
        cp = attacher_cp_from_path(explicit)
        if cp is None:
            raise SystemExit(
                f"Attacher not found at {explicit.resolve()}.\n"
                f"Need a directory containing {ATTACHER_CLASS_REL.as_posix()} "
                f"(or a path to Attacher.class)."
            )
        return cp
    return find_attacher_in_cwd(cwd)


def resolve_mod_jar(explicit: Path | None, cwd: Path) -> Path:
    if explicit is not None:
        jar = explicit.resolve()
        if not jar.is_file() or jar.suffix.lower() != ".jar":
            raise SystemExit(f"Mod jar not found: {jar}")
        return jar
    return find_mod_jar_in_cwd(cwd)


def clear_status_file() -> None:
    try:
        if STATUS_FILE.exists():
            STATUS_FILE.unlink()
    except OSError as e:
        log(f"Could not clear status file {STATUS_FILE}: {e}")


def inject(pid: int, jar: Path, attacher_cp: Path, token: str) -> None:
    log(f"Injecting agent into PID {pid}...")
    log(f"JAR: {jar}")
    log(f"Attacher CP: {attacher_cp}")
    log(f"Handshake file: {STATUS_FILE}")
    cmd = [
        java_path(),
        "--add-modules", "jdk.attach",
        "-cp", str(attacher_cp),
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

    if appdata:
        paths.append(appdata / ".minecraft" / "logs" / "latest.log")

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
        direct = root / "logs" / "latest.log"
        if direct not in paths:
            paths.append(direct)
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
    parser = argparse.ArgumentParser(
        description="Inject prebuilt Copy That Building agent (no Gradle)",
        epilog="Without paths, both attacher and mod jar are searched in the current working directory.",
    )
    parser.add_argument(
        "attacher",
        nargs="?",
        type=Path,
        help="Attacher classpath root (or path to Attacher.class)",
    )
    parser.add_argument(
        "mod_jar",
        nargs="?",
        type=Path,
        help="Agent/mod jar",
    )
    parser.add_argument("--pid", type=int, help="Fabric KnotClient PID (auto via jps if omitted)")
    parser.add_argument(
        "--timeout",
        type=float,
        default=20.0,
        help="Seconds to wait for handshake (default 20)",
    )
    args = parser.parse_args()

    cwd = Path.cwd()
    attacher_cp = resolve_attacher(args.attacher, cwd)
    jar = resolve_mod_jar(args.mod_jar, cwd)
    pid = args.pid if args.pid else find_fabric_pid()

    token = uuid.uuid4().hex
    clear_status_file()
    inject(pid, jar, attacher_cp, token)

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
