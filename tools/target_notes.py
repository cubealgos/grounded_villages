#!/usr/bin/env python3
"""GV-20: per-target Modrinth release notes, one `dist/notes/<version_number>.md` per
`docs/modrinth/targets.json` entry (GV-19's twelve Stonecutter-node targets).

Unlike `tools/release_notes.py` (one combination + one release version passed on the command
line, for `just release-notes`'s own single-jar flow), this tool takes no arguments about *which*
jar: it reads every target `docs/modrinth/targets.json` already lists and writes one notes file
per target in one run, the same "twelve targets, one command" shape `just publish-dry` and
`modrinth-publish.py version --targets ...` already use. `targets.json`'s own `changelog_file`
field is expected to point at this tool's own output (`dist/notes/<version_number>.md`), read
back by `modrinth-publish.py`'s `--targets` mode at publish time (confirmed live in
`standards/marketing/modrinth/modrinth-publish.py`'s `_resolve_targets`: `changelog_file` is
already read per target, nothing needed there for this ticket).

Each notes file is built from three pieces:

- **The CHANGELOG's own topmost `## ...` section**, title and body, whatever it is named --
  `## Unreleased` today (no release has been tagged yet), a real `## 0.1.0` once one is. Shared
  verbatim across every target, same as `tools/release_notes.py`'s own per-version section
  already is for its one-jar callers.
- **`REL-REQ-002`'s tested-toolchain line** ("the release notes list the Minecraft version,
  loader, and loader version tested for that specific jar"), one line naming the exact Minecraft
  version, loader, loader version, and (per this ticket's own build instructions) the Fabric
  API/NeoForge/Forge version pinned for that node -- `NODES` below, a literal table mirroring
  `docs/spec/contracts/platform-matrix.md`'s "Per-row toolchain" and "Wave 3 nodes" tables, the
  same way `tools/doctor.py`'s own `JAVA_ROWS`/`COORDINATE_ROWS` mirror that same spec file (the
  spec's tables are prose markdown, not a machine-readable source this tool could parse instead).
- **`REL-REQ-004`'s wave statement** ("each wave's first release notes state plainly which
  combinations are new in that wave and which ... remain planned"), derived from `NODES`' own
  `wave` field (the platform matrix's "Ships in" column) -- not from any actual Modrinth publish
  history, since this repository has not published a version yet.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

DEFAULT_TARGETS = Path("docs/modrinth/targets.json")
DEFAULT_CHANGELOG = Path("CHANGELOG.md")
DEFAULT_OUT_DIR = Path("dist/notes")

# One row per docs/spec/contracts/platform-matrix.md "Target combinations, in build order" /
# "Per-row toolchain" / "Wave 3 nodes" tables. `combination` is the matrix's own "<Loader>, <mc>"
# label; `wave` is its "Ships in" column. `loader`/`loader_version` are REL-REQ-002's "<loader>
# <loader version>" clause -- for Fabric this is Fabric Loader, a coordinate distinct from Fabric
# API (`deps.fabric_loader` in stonecutter.properties.toml, shared across every Fabric node); for
# NeoForge/Forge the loader *is* the modding API, so the one pinned coordinate fills both this and
# `library_version` below. `library`/`library_version` are the "<Fabric API | NeoForge | Forge>
# <version>" clause.
#
# `1.20.1-fabric`'s `wave` is this tool's own judgment call, not a value the spec states outright:
# `docs/spec/contracts/platform-matrix.md`'s "Target combinations" and "Per-row toolchain" tables
# never give Fabric-on-1.20.1 its own row at all (only "Forge, 1.20.1 | planned | Wave 2" and a
# passing "Fabric API precedent for this MC generation is 0.92.12+1.20.1" aside) -- yet it is a
# real, already-built twelfth Stonecutter node (`settings.gradle.kts`'s `match("1.20.1", "fabric",
# "forge")`, one call registering both loaders together; `git log` shows both landed in the same
# GV-2 commit, "six version nodes building real jars"; `tools/doctor.py`'s own `COORDINATE_ROWS`
# already tracks its Fabric API pin). Wave 2 here because it shares that one `match()` call and
# Minecraft rung with Forge 1.20.1 -- the best-supported reading, not a documented fact. This is a
# genuine gap in `platform-matrix.md`'s own enumeration tables (a missing row, not an open design
# question this tool should be settling on its own); flagged for a spec fix rather than decided
# silently and left unrecorded.
NODES: dict[str, dict[str, object]] = {
    "1.20.1-fabric": dict(mc="1.20.1", combination="Fabric, 1.20.1", wave=2,
                           loader="Fabric Loader", loader_version="0.19.5",
                           library="Fabric API", library_version="0.92.12+1.20.1"),
    "1.21.1-fabric": dict(mc="1.21.1", combination="Fabric, 1.21.1", wave=1,
                           loader="Fabric Loader", loader_version="0.19.5",
                           library="Fabric API", library_version="0.116.17+1.21.1"),
    "1.21.1-neoforge": dict(mc="1.21.1", combination="NeoForge, 1.21.1", wave=1,
                             loader="NeoForge", loader_version="21.1.251",
                             library="NeoForge", library_version="21.1.251"),
    "26.2-fabric": dict(mc="26.2", combination="Fabric, 26.2", wave=1,
                         loader="Fabric Loader", loader_version="0.19.5",
                         library="Fabric API", library_version="0.161.0+26.2"),
    "26.2-neoforge": dict(mc="26.2", combination="NeoForge, 26.2", wave=1,
                           loader="NeoForge", loader_version="26.2.0.88",
                           library="NeoForge", library_version="26.2.0.88"),
    "1.20.1-forge": dict(mc="1.20.1", combination="Forge, 1.20.1", wave=2,
                          loader="Forge", loader_version="47.4.23",
                          library="Forge", library_version="47.4.23"),
    "1.21.4-fabric": dict(mc="1.21.4", combination="Fabric, 1.21.4", wave=3,
                           loader="Fabric Loader", loader_version="0.19.5",
                           library="Fabric API", library_version="0.119.4+1.21.4"),
    "1.21.4-neoforge": dict(mc="1.21.4", combination="NeoForge, 1.21.4", wave=3,
                             loader="NeoForge", loader_version="21.4.157",
                             library="NeoForge", library_version="21.4.157"),
    "1.21.5-fabric": dict(mc="1.21.5", combination="Fabric, 1.21.5", wave=3,
                           loader="Fabric Loader", loader_version="0.19.5",
                           library="Fabric API", library_version="0.128.2+1.21.5"),
    "1.21.5-neoforge": dict(mc="1.21.5", combination="NeoForge, 1.21.5", wave=3,
                             loader="NeoForge", loader_version="21.5.98",
                             library="NeoForge", library_version="21.5.98"),
    "1.21.8-fabric": dict(mc="1.21.8", combination="Fabric, 1.21.8", wave=3,
                           loader="Fabric Loader", loader_version="0.19.5",
                           library="Fabric API", library_version="0.136.1+1.21.8"),
    "1.21.8-neoforge": dict(mc="1.21.8", combination="NeoForge, 1.21.8", wave=3,
                             loader="NeoForge", loader_version="21.8.54",
                             library="NeoForge", library_version="21.8.54"),
}


def load_targets(path: Path) -> list[dict]:
    if not path.exists():
        raise SystemExit(f"target_notes: targets file not found: {path}")
    doc = json.loads(path.read_text(encoding="utf-8"))
    targets = doc["targets"] if isinstance(doc, dict) else doc
    if not isinstance(targets, list) or not targets:
        raise SystemExit(f"target_notes: {path} has no non-empty targets list")
    return targets


def changelog_section(text: str) -> tuple[str, str]:
    """The CHANGELOG's own topmost `## ...` section (heading text, body) -- whatever it is named,
    since this project has not tagged a release yet and `## Unreleased` is the only section
    `CHANGELOG.md` carries today. `tools/release_notes.py`'s own `section()` looks up one *named*
    version instead; this generalises to "whichever section is first" so a dry run works before
    any version is cut."""
    match = re.search(r"^##\s+(.+?)\s*$(.*?)(?=^##\s|\Z)", text, re.M | re.S)
    if not match:
        raise SystemExit("target_notes: CHANGELOG.md has no '## ...' section to draw from")
    return match.group(1).strip(), match.group(2).strip()


def tested_line(info: dict) -> str:
    """REL-REQ-002: "the release notes list the Minecraft version, loader, and loader version
    tested for that specific jar" -- extended with the modding-API version per this ticket's own
    build instructions ("Tested on Minecraft <mc>, <loader> <loader version>, <Fabric API |
    NeoForge | Forge> <version>"). When the loader *is* the modding API (NeoForge, Forge), that
    third clause would just repeat the same version a second time, so it collapses instead."""
    mc, loader, loader_version = info["mc"], info["loader"], info["loader_version"]
    library, library_version = info["library"], info["library_version"]
    if library == loader and library_version == loader_version:
        return f"Tested on Minecraft {mc}, {loader} {loader_version}."
    return f"Tested on Minecraft {mc}, {loader} {loader_version}, {library} {library_version}."


def wave_statement(info: dict) -> str:
    """REL-REQ-004: "each wave's first release notes state plainly which combinations are new in
    that wave and which structure/version combinations remain planned" -- derived purely from
    `NODES`' own `wave` field (the platform matrix's "Ships in" column), not from any actual
    Modrinth publish history (none exists yet)."""
    wave = info["wave"]
    new = [n["combination"] for n in NODES.values() if n["wave"] == wave]
    planned = [n["combination"] for n in NODES.values() if n["wave"] > wave]
    planned_text = "; ".join(planned) if planned else "none"
    return f"New in this release: {'; '.join(new)}. Still planned: {planned_text}."


def notes_for(target: dict, changelog_body: str) -> str:
    node = target.get("node")
    info = NODES.get(node)
    if info is None:
        raise SystemExit(f"target_notes: no platform-matrix entry for node {node!r} -- add one to NODES")
    title = target.get("version_name") or target.get("version_number") or node
    parts = [f"# {title}", ""]
    if changelog_body:
        parts += [changelog_body, ""]
    parts += [tested_line(info), "", wave_statement(info), ""]
    return "\n".join(parts).rstrip() + "\n"


def write_all(targets: list[dict], changelog_body: str, out_dir: Path) -> list[Path]:
    out_dir.mkdir(parents=True, exist_ok=True)
    written = []
    for target in targets:
        version_number = target.get("version_number")
        if not version_number:
            raise SystemExit(f"target_notes: target for node {target.get('node')!r} has no version_number")
        text = notes_for(target, changelog_body)
        out_path = out_dir / f"{version_number}.md"
        out_path.write_text(text, encoding="utf-8")
        written.append(out_path)
    return written


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="repository root (default: cwd)")
    parser.add_argument("--targets", type=Path, default=DEFAULT_TARGETS, help="targets file, relative to --root")
    parser.add_argument("--changelog", type=Path, default=DEFAULT_CHANGELOG, help="CHANGELOG.md, relative to --root")
    parser.add_argument("--out-dir", type=Path, default=DEFAULT_OUT_DIR, help="output directory, relative to --root")
    args = parser.parse_args(argv)
    root = args.root.resolve()

    targets = load_targets(root / args.targets)
    changelog_path = root / args.changelog
    if not changelog_path.exists():
        raise SystemExit(f"target_notes: changelog not found: {changelog_path}")
    _, body = changelog_section(changelog_path.read_text(encoding="utf-8"))

    written = write_all(targets, body, root / args.out_dir)
    for path in written:
        print(path.relative_to(root))
    return 0


if __name__ == "__main__":
    sys.exit(main())
