#!/usr/bin/env python3
"""The shared-source purity check (`docs/spec/04-architecture.md` `ARCH-DEC-002`): the four pure
packages -- `grounded_villages.piece`, `grounded_villages.site`, `grounded_villages.tier`,
`grounded_villages.config` -- carry "no Minecraft, Fabric, NeoForge or Forge import anywhere in
this package" (each package's own `package-info.java`, in those exact words). This scans every
`.java` file under those packages, one file per package's own two confirmed adapters excepted
(`grounded_villages.piece.PieceRejectionHook`, `grounded_villages.site.SiteStartHook` -- both
package-info.java files name themselves as "the one place" that package touches a Minecraft type),
for an `import` line naming a banned prefix.

Read-only: reports, changes nothing. Exit 0 when every scanned file is clean, 4 when a banned
import is found (naming the file, the line, and the import). `tools/purity_proof.py` is the
deliberate-break proof this check exists to be proven against (`TEST-REQ-002`).
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

# grounded_villages.piece / .site / .tier / .config -- ARCH-DEC-002's own four named pure units
# (PieceGate, SiteSelector, TierRoller, ConfigModel), one package each.
PURE_PACKAGE_ROOTS = (
    Path("src/main/java/grounded_villages/piece"),
    Path("src/main/java/grounded_villages/site"),
    Path("src/main/java/grounded_villages/tier"),
    Path("src/main/java/grounded_villages/config"),
)

# The two confirmed exceptions, named by their own package-info.java as "the one place" that
# package touches a Minecraft type -- excluded by relative path, not by a broader package carve-out,
# so a *new* file in either package is checked by default rather than silently exempted.
KNOWN_ADAPTERS = frozenset({
    Path("src/main/java/grounded_villages/piece/PieceRejectionHook.java"),
    Path("src/main/java/grounded_villages/site/SiteStartHook.java"),
})

BANNED_IMPORT_PREFIXES = (
    "net.minecraft.",
    "net.fabricmc.",
    "net.neoforged.",
    "net.minecraftforge.",
    "cpw.mods.",
    "org.spongepowered.asm.",
)

IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([\w.]+)")


def scan_file(path: Path) -> list[tuple[int, str]]:
    """Every (line number, import line) in `path` naming a banned prefix."""
    violations: list[tuple[int, str]] = []
    for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        match = IMPORT_RE.match(line)
        if match is None:
            continue
        imported = match.group(1)
        if any(imported.startswith(prefix) for prefix in BANNED_IMPORT_PREFIXES):
            violations.append((lineno, line.strip()))
    return violations


def scanned_files(root: Path) -> list[Path]:
    files: list[Path] = []
    for package_root in PURE_PACKAGE_ROOTS:
        directory = root / package_root
        if not directory.is_dir():
            continue
        for path in sorted(directory.glob("*.java")):
            rel = path.relative_to(root)
            if rel in KNOWN_ADAPTERS:
                continue
            files.append(path)
    return files


def check(root: Path) -> tuple[bool, list[str]]:
    """(clean, messages) -- one message per violation, empty when clean."""
    messages: list[str] = []
    for path in scanned_files(root):
        for lineno, line in scan_file(path):
            messages.append(f"{path.relative_to(root)}:{lineno}: banned import -- {line}")
    return not messages, messages


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="repository root (default: cwd)")
    args = parser.parse_args(argv)
    root = args.root.resolve()

    clean, messages = check(root)
    if clean:
        print(f"purity: clean ({len(scanned_files(root))} files scanned, "
              f"{len(PURE_PACKAGE_ROOTS)} packages, {len(KNOWN_ADAPTERS)} known adapters excepted)")
        return 0

    for message in messages:
        print(f"  FAIL  {message}")
    print(f"purity: {len(messages)} violation(s) found")
    return 4


if __name__ == "__main__":
    sys.exit(main())
