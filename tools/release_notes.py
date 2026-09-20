#!/usr/bin/env python3
"""Print the release notes for one shipped combination's jar.

Unlike a single-jar sibling, one release here ships up to six jars (REL-DEC-001:
`<mod>-<mc>-<loader>`, both axes encoded) -- so this takes the combination explicitly rather than
assuming there is only one. `just release-notes <mc>-<loader> <version>` matches one call to one
jar, the same shape `docs/spec/operations/release.md`'s Modrinth publish matrix requires (one
`cmd_version` call per jar, six calls for a full Wave 1-2 release).

CHANGELOG.md sections are expected keyed by the plain release version (e.g. `## 1.0.0`), covering
every combination shipped in that release together -- REL-REQ-002 (per-jar MC version/loader/
loader-version) and REL-REQ-003 (defaults in force) are the release notes' own job to state inside
that one shared section, not one section per jar. If that shared-section shape turns out wrong once
a real release is cut, that is a GV-19/GV-21 finding, not a guess to lock in here.
"""
import re
import sys
from pathlib import Path

from jar_naming import jar_file_name


def section(changelog: str, version: str) -> str:
    match = re.search(rf"^## \[?{re.escape(version)}\]?.*?$(.*?)(?=^## |\Z)", changelog, re.M | re.S)
    if not match:
        raise SystemExit(f"release_notes: no CHANGELOG.md section for {version}")
    return match.group(1).strip()


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: release_notes.py <mc>-<loader> <version>")
    target, version = sys.argv[1], sys.argv[2]
    jar_name = jar_file_name(target, version)
    body = section(Path("CHANGELOG.md").read_text(), version)
    checksum_path = Path(f"dist/{jar_name}.sha256")
    checksum = checksum_path.read_text().split()[0] if checksum_path.exists() else "<not built yet>"
    print(f"# Grounded Villages {version} for {target}\n")
    print(body)
    print(f"\n## Files\n\n`{jar_name}`, SHA-256 `{checksum}`.")
    print(f"\nSee docs/spec/contracts/platform-matrix.md for the exact toolchain {target} was built and tested against. MIT.")


if __name__ == "__main__":
    main()
