#!/usr/bin/env python3
"""Toolchain floors and the spec copy, per docs/spec/contracts/platform-matrix.md.

Unlike a single-floor sibling, this project spans three Java majors at once (17 for 1.20.1, 21 for
1.21.1, 25 for 26.2) — every row is checked and reported by name, not collapsed into one floor
(PLATFORM-REQ-001: "if any row's toolchain version moves, then just doctor fails naming the row").

Read-only: reports, changes nothing. Exit 0 when every check passes, 5 when a toolchain is absent
or below its floor, 6 when docs/spec/ differs from the vault copy. Each failure names the floor and
what was found, and looks where the tool actually lives rather than only on PATH.
"""
from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# One row per contracts/platform-matrix.md "Per-row toolchain" table. 1.20.1 needs Java 17, not
# 21 -- the one rung below fabric-loom's own >=1.20.5 -> Java 21 boundary.
JAVA_ROWS = {
    "1.20.1 (fabric, forge)": 17,
    "1.21.1 (fabric, neoforge)": 21,
    "26.2 (fabric, neoforge)": 25,
}
GRADLE_FLOOR = (9, 5, 1)
OTHER_FLOORS = {"just": (1, 58), "python": (3, 12)}

# Candidate env vars per major, checked before falling back to PATH's own `java -version`. Neither
# sdkman nor jenv naming is assumed -- an explicit JAVA<major>_HOME is the portable answer across
# machines, documented here rather than guessed at silently.
JAVA_HOME_CANDIDATES = {17: ["JAVA17_HOME", "JAVA_HOME_17"], 21: ["JAVA21_HOME", "JAVA_HOME_21"],
                         25: ["JAVA25_HOME", "JAVA_HOME_25"]}


def run(*cmd: str) -> str:
    try:
        out = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
    except (OSError, subprocess.TimeoutExpired) as exc:
        return f"<{exc}>"
    return (out.stdout + out.stderr).strip()


def parse_version(text: str) -> tuple[int, ...] | None:
    match = re.search(r"(\d+)(?:\.(\d+))?(?:\.(\d+))?", text)
    if not match:
        return None
    return tuple(int(part) for part in match.groups() if part is not None)


def find_java(major: int) -> tuple[str, str] | None:
    """(java binary path, where it was found) for a specific major, or None."""
    for var in JAVA_HOME_CANDIDATES.get(major, []):
        home = os.environ.get(var)
        if home and (Path(home) / "bin" / "java").exists():
            return str(Path(home) / "bin" / "java"), var
    java_home = os.environ.get("JAVA_HOME")
    if java_home and (Path(java_home) / "bin" / "java").exists():
        candidate = str(Path(java_home) / "bin" / "java")
        text = run(candidate, "-version")
        found = re.search(r'version "(\d+)', text)
        if found and int(found.group(1)) == major:
            return candidate, "JAVA_HOME"
    path_java = shutil.which("java")
    if path_java:
        text = run(path_java, "-version")
        found = re.search(r'version "(\d+)', text)
        if found and int(found.group(1)) == major:
            return path_java, "PATH"
    return None


def check_java_row(label: str, floor: int) -> tuple[bool, str]:
    found = find_java(floor)
    if not found:
        return False, f"java ({label}): no Java {floor} found via JAVA{floor}_HOME or PATH (floor {floor})"
    java, where = found
    return True, f"java ({label}): {floor} on {where} ({java}); floor {floor}"


def check_wrapper() -> tuple[bool | None, str]:
    props = ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties"
    if not props.exists():
        return None, "gradle wrapper: not present yet; skipped (GV-2 adds the Stonecutter build)"
    match = re.search(r"gradle-(\d+\.\d+(?:\.\d+)?)-", props.read_text())
    if not match:
        return False, "gradle wrapper: distributionUrl has no recognisable version"
    found = parse_version(match.group(1)) or ()
    ok = found >= GRADLE_FLOOR
    return ok, f"gradle wrapper: {match.group(1)} pinned; floor {'.'.join(map(str, GRADLE_FLOOR))}"


def check_tool(name: str, floor_key: str | None, *args: str) -> tuple[bool, str]:
    path = shutil.which(name)
    if not path:
        floor = ".".join(map(str, OTHER_FLOORS[floor_key])) if floor_key else "any"
        return False, f"{name}: not found on PATH (floor {floor})"
    if not floor_key:
        return True, f"{name}: present ({path})"
    found = parse_version(run(path, *args)) or ()
    floor = OTHER_FLOORS[floor_key]
    return found >= floor, f"{name}: {'.'.join(map(str, found))} ({path}); floor {'.'.join(map(str, floor))}"


def main_checkout() -> Path:
    """The repository's main checkout, even when run from a worktree under .worktrees/."""
    common = run("git", "-C", str(ROOT), "rev-parse", "--path-format=absolute", "--git-common-dir")
    return Path(common).parent if common and not common.startswith("<") else ROOT


def check_spec_copy() -> tuple[bool | None, str]:
    default = main_checkout().parent / "heimathafen" / "vault" / "projects" / "grounded_villages" / "spec"
    vault = Path(os.environ.get("GV_VAULT_SPEC", default))
    if not vault.exists():
        return None, f"spec copy: vault not present at {vault}; skipped (set GV_VAULT_SPEC to check)"
    diff = run("diff", "-rq", str(ROOT / "docs" / "spec"), str(vault.resolve()))
    if not diff:
        return True, "spec copy: docs/spec/ is identical to the vault"
    return False, "spec copy: docs/spec/ differs from the vault:\n  " + diff.replace("\n", "\n  ")


def check_map() -> tuple[bool, str]:
    """The map is generated from the source and a stale one fails: run the command, not the function."""
    try:
        proc = subprocess.run([sys.executable, str(ROOT / "tools" / "map.py"), "--root", str(ROOT), "--check"],
                              capture_output=True, text=True, timeout=120)
    except (OSError, subprocess.TimeoutExpired) as exc:
        return False, f"map: could not run tools/map.py ({exc})"
    if proc.returncode == 0:
        return True, "map: docs/map.md and docs/map/ match the source"
    return False, "map: stale; run `just map`:\n  " + (proc.stderr.strip() or proc.stdout.strip()).replace("\n", "\n  ")


def report(ok: bool | None, message: str) -> str:
    return ("  skip  " if ok is None else "   ok   " if ok else "  FAIL  ") + message


def main() -> int:
    results: list[tuple[bool, str]] = [check_java_row(label, floor) for label, floor in JAVA_ROWS.items()]
    results += [
        check_tool("just", "just", "--version"),
        check_tool("python3", "python", "--version"),
        check_tool("kontor", None),
        check_map(),
    ]
    code = 0
    for ok, message in results:
        print(report(ok, message))
        if not ok:
            code = 5

    wrapper_ok, wrapper_message = check_wrapper()
    print(report(wrapper_ok, wrapper_message))
    if wrapper_ok is False:
        code = 5

    spec_ok, spec_message = check_spec_copy()
    print(report(spec_ok, spec_message))
    if spec_ok is False and code == 0:
        code = 6
    print("toolchain: all floors met" if code == 0 else f"toolchain: problems found (exit {code})")
    return code


if __name__ == "__main__":
    sys.exit(main())
