#!/usr/bin/env python3
"""Toolchain floors and the spec copy, per docs/spec/contracts/platform-matrix.md.

Unlike a single-floor sibling, this project spans three Java majors at once (17 for 1.20.1, 21 for
1.21.1, 25 for 26.2) -- every row is checked and reported by name, not collapsed into one floor
(PLATFORM-REQ-001: "if any row's toolchain version moves, then just doctor fails naming the row").

Each Java row is satisfiable either by an installed JDK (`JAVA<major>_HOME`/`JAVA_HOME_<major>`,
`JAVA_HOME`, or `PATH`) or by a JDK Gradle itself provisioned under
`$GRADLE_USER_HOME/jdks/` (`~/.gradle/jdks/` by default) via the `foojay-resolver-convention`
plugin -- discovered by reading each provisioned JDK directory's own `release` file for
`JAVA_VERSION`, not by guessing a path shape (macOS nests it under `Contents/Home/`, Linux does
not). This repository also checks that `settings.gradle.kts`'s Stonecutter plugin version and
`stonecutter.properties.toml`'s per-row dependency coordinates (Fabric API / NeoForge / Forge /
Fabric Loader / Fabric Loom) still match the pins `contracts/platform-matrix.md` verified,
reporting drift as a failure naming the row -- the same "fails naming the row" contract as the Java
floors, extended to every pinned coordinate, not only the JDK majors.

Read-only: reports, changes nothing. Exit 0 when every check passes, 5 when a toolchain or
coordinate is absent or below/unequal to its pin, 6 when docs/spec/ differs from the vault copy
(and nothing else failed). Each failure names the floor/pin and what was found, and looks where the
tool actually lives rather than only on PATH.
"""
from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import tomllib
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent

# One row per contracts/platform-matrix.md "Per-row toolchain" table. 1.20.1 needs Java 17, not
# 21 -- the one rung below fabric-loom's own >=1.20.5 -> Java 21 boundary.
JAVA_ROWS = {
    "1.20.1 (fabric, forge)": 17,
    "1.21.1 (fabric, neoforge)": 21,
    "26.2 (fabric, neoforge)": 25,
}
GRADLE_FLOOR = (9, 5, 1)
OTHER_FLOORS = {"just": (1, 58), "python": (3, 12)}

# "Root Gradle 9.5.1 across every row; Stonecutter dev.kikugie.stonecutter 0.9.8"
# (contracts/platform-matrix.md "Per-row toolchain").
STONECUTTER_VERSION = "0.9.8"

# Candidate env vars per major, checked before falling back to PATH's own `java -version`. Neither
# sdkman nor jenv naming is assumed -- an explicit JAVA<major>_HOME is the portable answer across
# machines, documented here rather than guessed at silently.
JAVA_HOME_CANDIDATES = {17: ["JAVA17_HOME", "JAVA_HOME_17"], 21: ["JAVA21_HOME", "JAVA_HOME_21"],
                         25: ["JAVA25_HOME", "JAVA_HOME_25"]}

# One row per contracts/platform-matrix.md "Per-row toolchain" / "Verified coordinates (GV-2)":
# (row label, dotted path into stonecutter.properties.toml, the pin the matrix verified).
COORDINATE_ROWS = [
    ("Fabric, 1.20.1", ("fabric", "1.20.1", "deps", "fabric_api"), "0.92.12+1.20.1"),
    ("Forge, 1.20.1", ("forge", "1.20.1", "deps", "forge"), "47.4.23"),
    ("Fabric, 1.21.1", ("fabric", "1.21.1", "deps", "fabric_api"), "0.116.17+1.21.1"),
    ("NeoForge, 1.21.1", ("neoforge", "1.21.1", "deps", "neoforge"), "21.1.251"),
    ("Fabric, 26.2", ("fabric", "26.2", "deps", "fabric_api"), "0.161.0+26.2"),
    ("NeoForge, 26.2", ("neoforge", "26.2", "deps", "neoforge"), "26.2.0.88"),
    ("shared: fabric loader", ("deps", "fabric_loader"), "0.19.5"),
    ("shared: fabric-loom", ("loomx", "loom_version"), "1.17-SNAPSHOT"),
]


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


def gradle_user_home() -> Path:
    return Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))


def find_provisioned_jdk(major: int) -> tuple[str, str] | None:
    """(java binary path, where) for a Gradle-provisioned JDK under
    `$GRADLE_USER_HOME/jdks/<toolchain>/...`, found by reading each candidate's own `release` file
    for `JAVA_VERSION` rather than assuming a path shape (macOS nests the JDK under
    `Contents/Home/`; Linux does not)."""
    jdks_root = gradle_user_home() / "jdks"
    if not jdks_root.is_dir():
        return None
    for entry in sorted(jdks_root.iterdir()):
        if not entry.is_dir():
            continue
        for release in sorted(entry.rglob("release")):
            text = release.read_text(errors="replace")
            found = re.search(r'JAVA_VERSION="(\d+)', text)
            if found and int(found.group(1)) == major:
                java = release.parent / "bin" / "java"
                if java.exists():
                    return str(java), f"{entry.name} (gradle jdks)"
    return None


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
    return find_provisioned_jdk(major)


def check_java_row(label: str, floor: int) -> tuple[bool, str]:
    found = find_java(floor)
    if not found:
        return False, (f"java ({label}): no Java {floor} found via JAVA{floor}_HOME, JAVA_HOME, PATH, "
                        f"or a Gradle-provisioned JDK under {gradle_user_home() / 'jdks'} (floor {floor})")
    java, where = found
    return True, f"java ({label}): {floor} on {where} ({java}); floor {floor}"


def check_wrapper(root: Path) -> tuple[bool, str]:
    props = root / "gradle" / "wrapper" / "gradle-wrapper.properties"
    if not props.exists():
        return False, "gradle wrapper: gradle/wrapper/gradle-wrapper.properties missing"
    match = re.search(r"gradle-(\d+\.\d+(?:\.\d+)?)-", props.read_text())
    if not match:
        return False, "gradle wrapper: distributionUrl has no recognisable version"
    found = parse_version(match.group(1)) or ()
    ok = found >= GRADLE_FLOOR
    return ok, f"gradle wrapper: {match.group(1)} pinned; floor {'.'.join(map(str, GRADLE_FLOOR))}"


def check_stonecutter_version(root: Path) -> tuple[bool, str]:
    settings = root / "settings.gradle.kts"
    if not settings.exists():
        return False, "stonecutter: settings.gradle.kts missing"
    match = re.search(r'id\("dev\.kikugie\.stonecutter"\)\s+version\s+"([^"]+)"', settings.read_text())
    if not match:
        return False, "stonecutter: no dev.kikugie.stonecutter version pin found in settings.gradle.kts"
    found = match.group(1)
    ok = found == STONECUTTER_VERSION
    return ok, f"stonecutter: {found} pinned in settings.gradle.kts; matrix pins {STONECUTTER_VERSION}"


def dig(doc: dict, path: tuple[str, ...]):
    node = doc
    for key in path:
        if not isinstance(node, dict) or key not in node:
            return None
        node = node[key]
    return node


def check_coordinates(root: Path) -> list[tuple[bool, str]]:
    """Per contracts/platform-matrix.md "Verified coordinates (GV-2)": every dependency pin
    `stonecutter.properties.toml` declares for a row must still match what the matrix verified --
    drift here is exactly PLATFORM-REQ-001's "fails naming the row", extended past the JDK majors
    to the dependency coordinates themselves."""
    props = root / "stonecutter.properties.toml"
    if not props.exists():
        return [(False, "coordinates: stonecutter.properties.toml missing")]
    try:
        doc = tomllib.loads(props.read_text())
    except tomllib.TOMLDecodeError as exc:
        return [(False, f"coordinates: stonecutter.properties.toml did not parse ({exc})")]
    results = []
    for label, path, expected in COORDINATE_ROWS:
        found = dig(doc, path)
        where = ".".join(path)
        if found is None:
            results.append((False, f"coordinates ({label}): {where} missing from stonecutter.properties.toml; matrix pins {expected!r}"))
        else:
            ok = found == expected
            results.append((ok, f"coordinates ({label}): {where} = {found!r}; matrix pins {expected!r}"))
    return results


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


def main_checkout(root: Path) -> Path:
    """The repository's main checkout, even when run from a worktree under .worktrees/."""
    common = run("git", "-C", str(root), "rev-parse", "--path-format=absolute", "--git-common-dir")
    return Path(common).parent if common.startswith("/") else root


def check_spec_copy(root: Path) -> tuple[bool | None, str]:
    default = main_checkout(root).parent / "heimathafen" / "vault" / "projects" / "grounded_villages" / "spec"
    vault = Path(os.environ.get("GV_VAULT_SPEC", default))
    if not vault.exists():
        return None, f"spec copy: vault not present at {vault}; skipped (set GV_VAULT_SPEC to check)"
    diff = run("diff", "-rq", str(root / "docs" / "spec"), str(vault.resolve()))
    if not diff:
        return True, "spec copy: docs/spec/ is identical to the vault"
    return False, "spec copy: docs/spec/ differs from the vault:\n  " + diff.replace("\n", "\n  ")


def check_map(root: Path) -> tuple[bool, str]:
    """The map is generated from the source and a stale one fails: run the command, not the function."""
    try:
        proc = subprocess.run([sys.executable, str(TOOL_DIR / "map.py"), "--root", str(root), "--check"],
                              capture_output=True, text=True, timeout=120)
    except (OSError, subprocess.TimeoutExpired) as exc:
        return False, f"map: could not run tools/map.py ({exc})"
    if proc.returncode == 0:
        return True, "map: docs/map.md and docs/map/ match the source"
    return False, "map: stale; run `just map`:\n  " + (proc.stderr.strip() or proc.stdout.strip()).replace("\n", "\n  ")


def report(ok: bool | None, message: str) -> str:
    return ("  skip  " if ok is None else "   ok   " if ok else "  FAIL  ") + message


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="repository root (default: cwd)")
    args = parser.parse_args(argv)
    root = args.root.resolve()

    results: list[tuple[bool | None, str]] = [check_java_row(label, floor) for label, floor in JAVA_ROWS.items()]
    results += [
        check_tool("just", "just", "--version"),
        check_tool("python3", "python", "--version"),
        check_tool("kontor", None),
        check_map(root),
        check_wrapper(root),
        check_stonecutter_version(root),
    ]
    results += check_coordinates(root)

    code = 0
    for ok, message in results:
        print(report(ok, message))
        if not ok:
            code = 5

    spec_ok, spec_message = check_spec_copy(root)
    print(report(spec_ok, spec_message))
    if spec_ok is False and code == 0:
        code = 6
    print("toolchain: all floors met" if code == 0 else f"toolchain: problems found (exit {code})")
    return code


if __name__ == "__main__":
    sys.exit(main())
