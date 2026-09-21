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
  combinations are new in that wave and which ... remain planned"). GV-21: the 1.0.0 release ships
  every node `NODES` lists together (`docs/spec/contracts/platform-matrix.md`, synced 2026-09-21,
  every row `built`/`ships in 1.0`), so every target's wave statement now reads the same way --
  everything in `NODES` is new, nothing remains planned; `wave_statement`'s own docstring covers
  why this is no longer a per-node `wave`-field comparison. Still not from any actual Modrinth
  publish history, since this repository has not published a version yet.
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
DEFAULT_CHECKSUMS = Path("dist/SHA256SUMS")

# One row per docs/spec/contracts/platform-matrix.md "Target combinations, in build order" /
# "Per-row toolchain" / "Wave 3 nodes" tables. `combination` is the matrix's own "<Loader>, <mc>"
# label; `wave` is its "Ships in" column. `loader`/`loader_version` are REL-REQ-002's "<loader>
# <loader version>" clause -- for Fabric this is Fabric Loader, a coordinate distinct from Fabric
# API (`deps.fabric_loader` in stonecutter.properties.toml, shared across every Fabric node); for
# NeoForge/Forge the loader *is* the modding API, so the one pinned coordinate fills both this and
# `library_version` below. `library`/`library_version` are the "<Fabric API | NeoForge | Forge>
# <version>" clause.
#
# `1.20.1-fabric`'s `wave` was this tool's own judgment call as of GV-20 -- `platform-matrix.md`'s
# enumeration tables did not give Fabric-on-1.20.1 its own row at all, even though it was already a
# real, built twelfth Stonecutter node. GV-21's spec sync closed that gap: the vault's
# `platform-matrix.md` now lists "Fabric, 1.20.1 | built, proven at GV-2 and GV-24; ships with Wave
# 2 | Wave 2" explicitly, confirming Wave 2 was the right guess. `wave` is otherwise no longer used
# for the new-vs-planned split (see `wave_statement` below); it stays as the documented build-order
# record only.
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
    that wave and which structure/version combinations remain planned".

    GV-21 finding: this used to compare each node's own `wave` field against every other node's,
    so a Wave 1 node's own notes would list every later-wave combination as "still planned" even
    once those later waves had actually shipped in the same release -- correct only while releases
    tracked one wave at a time. `docs/spec/contracts/platform-matrix.md` (synced 2026-09-21, GV-21)
    now shows every row `built` and `ships in 1.0`: the 1.0.0 release ships every node in `NODES`
    together, so every node's own wave statement is the same one -- everything `NODES` lists is
    new in this release, nothing remains planned. `wave` stays on each entry only as the historical
    build-order record (Wave 1/2/3, still read by nothing else here); a future release that again
    ships less than everything in `NODES` at once is the case this function would need to branch
    on again, not assumed here since no such release exists yet."""
    all_combinations = [n["combination"] for n in NODES.values()]
    return f"New in this release: {'; '.join(all_combinations)}. Still planned: none."


def load_checksums(path: Path) -> dict[str, str]:
    """GV-21: `dist/SHA256SUMS` (`shasum -a 256 *.jar` over `chiseledBuild`'s own collected
    twelve-jar output directory, `<mod>/<mod version>/`), the release checksum
    `operations/compliance.md` "Supply chain and release integrity" requires in the release notes.
    Maps jar basename -> hex digest; empty (not missing -- a dry run before a real build has run
    yet is a normal state, `files_line` below just omits the section) when the file does not
    exist, same "absent is not fatal" shape `tools/release_notes.py`'s own single-jar checksum
    lookup already uses."""
    if not path.exists():
        return {}
    checksums: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        digest, _, name = line.partition("  ")
        if digest and name:
            checksums[name.strip()] = digest.strip()
    return checksums


def files_line(target: dict, checksums: dict[str, str]) -> str | None:
    """REL-REQ per `operations/compliance.md`: each jar's own checksum, in its own notes file --
    not the whole `SHA256SUMS` list, so a reader of one target's notes sees only the one hash that
    actually verifies that one download."""
    jar = target.get("jar")
    if not jar:
        return None
    jar_name = Path(jar).name
    digest = checksums.get(jar_name)
    if not digest:
        return None
    return f"## Files\n\n`{jar_name}`, SHA-256 `{digest}`."


def notes_for(target: dict, changelog_body: str, checksums: dict[str, str]) -> str:
    node = target.get("node")
    info = NODES.get(node)
    if info is None:
        raise SystemExit(f"target_notes: no platform-matrix entry for node {node!r} -- add one to NODES")
    title = target.get("version_name") or target.get("version_number") or node
    parts = [f"# {title}", ""]
    if changelog_body:
        parts += [changelog_body, ""]
    parts += [tested_line(info), "", wave_statement(info), ""]
    files = files_line(target, checksums)
    if files:
        parts += [files, ""]
    return "\n".join(parts).rstrip() + "\n"


def write_all(targets: list[dict], changelog_body: str, out_dir: Path, checksums: dict[str, str]) -> list[Path]:
    out_dir.mkdir(parents=True, exist_ok=True)
    written = []
    for target in targets:
        version_number = target.get("version_number")
        if not version_number:
            raise SystemExit(f"target_notes: target for node {target.get('node')!r} has no version_number")
        text = notes_for(target, changelog_body, checksums)
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
    parser.add_argument("--checksums", type=Path, default=DEFAULT_CHECKSUMS, help="dist/SHA256SUMS, relative to --root")
    args = parser.parse_args(argv)
    root = args.root.resolve()

    targets = load_targets(root / args.targets)
    changelog_path = root / args.changelog
    if not changelog_path.exists():
        raise SystemExit(f"target_notes: changelog not found: {changelog_path}")
    _, body = changelog_section(changelog_path.read_text(encoding="utf-8"))
    checksums = load_checksums(root / args.checksums)

    written = write_all(targets, body, root / args.out_dir, checksums)
    for path in written:
        print(path.relative_to(root))
    return 0


if __name__ == "__main__":
    sys.exit(main())
