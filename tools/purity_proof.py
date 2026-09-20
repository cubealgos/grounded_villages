#!/usr/bin/env python3
"""`TEST-REQ-002` (`docs/spec/operations/testing.md`): a deliberate-break proof for
`tools/purity_check.py`, once. Confirms the check is not a no-op by temporarily adding a real
loader import to a real pure-source file, confirming `purity_check.py` then fails and names the
break, restoring the file (even on an unexpected error, via `try`/`finally`), and confirming the
check passes again clean.

Run explicitly (`just purity-proof`) -- not part of `just check`: it mutates a tracked source file
mid-run, restored before this script exits, but not a step that belongs in the routine loop.
Exit 0 when every stage behaves as expected (clean before, fails naming the break, clean again
after restore); non-zero and a named reason otherwise. The full output of the run this proof was
recorded from lives in `docs/spec/operations/testing.md`'s own verification table.
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent
ROOT = TOOL_DIR.parent
PURITY_CHECK = TOOL_DIR / "purity_check.py"

# A real pure-source file (grounded_villages.piece, one of ARCH-DEC-002's own four named units) --
# not a fixture, the genuine article this check is meant to protect.
TARGET = ROOT / "src/main/java/grounded_villages/piece/PieceFootprint.java"
BREAK_IMPORT = "import net.minecraft.core.BlockPos;\n"


def run_purity_check() -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(PURITY_CHECK), "--root", str(ROOT)],
        capture_output=True, text=True, check=False,
    )


def main() -> int:
    original = TARGET.read_text(encoding="utf-8")
    stages: list[str] = []
    ok = True

    before = run_purity_check()
    stages.append(f"before the break -- exit {before.returncode}\n{before.stdout}{before.stderr}")
    if before.returncode != 0:
        ok = False
        stages.append("FAIL: purity_check.py was not clean before the deliberate break was even "
                       "introduced -- fix that first, this proof cannot trust its own result otherwise.")

    try:
        lines = original.splitlines(keepends=True)
        package_line_index = next(i for i, line in enumerate(lines) if line.startswith("package "))
        lines.insert(package_line_index + 1, "\n" + BREAK_IMPORT)
        TARGET.write_text("".join(lines), encoding="utf-8")

        during = run_purity_check()
        stages.append(f"with the break -- exit {during.returncode}\n{during.stdout}{during.stderr}")
        if during.returncode == 0:
            ok = False
            stages.append("FAIL: purity_check.py stayed clean (exit 0) with a deliberate "
                           "net.minecraft.core.BlockPos import added to PieceFootprint.java -- the "
                           "check is not actually checking anything.")
        elif "PieceFootprint.java" not in during.stdout or "net.minecraft.core.BlockPos" not in during.stdout:
            ok = False
            stages.append("FAIL: purity_check.py failed, but did not name the file/import it broke "
                           "on -- TEST-REQ-002 asks for the break to be named, not just detected.")
    finally:
        TARGET.write_text(original, encoding="utf-8")

    after = run_purity_check()
    stages.append(f"after restore -- exit {after.returncode}\n{after.stdout}{after.stderr}")
    if after.returncode != 0 or TARGET.read_text(encoding="utf-8") != original:
        ok = False
        stages.append("FAIL: the target file was not cleanly restored, or purity_check.py was not "
                       "clean again afterward.")

    print("\n".join(stages))
    print()
    print("purity proof: PASS" if ok else "purity proof: FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
