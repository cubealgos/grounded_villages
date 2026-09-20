"""tools/target_notes.py, exercised both as a library (the pure per-target formatting) and as the
command a person runs (rule 6 of the standard): a fixture repo tree in a temporary directory, a
real `docs/modrinth/targets.json` subset, a real `CHANGELOG.md`, `python3 tools/target_notes.py`
against it, then the written `dist/notes/*.md` files read back."""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from target_notes import NODES, changelog_section, notes_for, tested_line, wave_statement

TOOL = Path(__file__).resolve().parent / "target_notes.py"

UNRELEASED_CHANGELOG = """# Changelog

## Unreleased

- Bootstrap: repository scaffold, licence, docs, CI shape, spec copy (GV-1).
"""

RELEASED_CHANGELOG = """# Changelog

## 0.1.0

- The first alpha (GV-1 through GV-24).

## Unreleased

- Nothing yet.
"""


def make_target(node: str, version_number: str | None = None) -> dict:
    version_number = version_number or f"grounded_villages-{node}"
    return {
        "node": node,
        "jar": f"build/libs/0.1.0/grounded_villages-{node}-0.1.0.jar",
        "game_versions": [node.rsplit("-", 1)[0]],
        "loaders": [node.rsplit("-", 1)[1]],
        "version_number": version_number,
        "version_name": f"Grounded Villages 0.1.0 for {node}",
        "changelog_file": f"dist/notes/{version_number}.md",
    }


class ChangelogSectionTest(unittest.TestCase):
    def test_unreleased_only_changelog_is_read(self):
        title, body = changelog_section(UNRELEASED_CHANGELOG)
        self.assertEqual("Unreleased", title)
        self.assertIn("Bootstrap: repository scaffold", body)

    def test_topmost_section_wins_when_a_real_release_exists(self):
        title, body = changelog_section(RELEASED_CHANGELOG)
        self.assertEqual("0.1.0", title)
        self.assertIn("The first alpha", body)
        self.assertNotIn("Nothing yet", body)

    def test_no_section_at_all_raises(self):
        with self.assertRaises(SystemExit):
            changelog_section("# Changelog\n\nno sections here\n")


class TestedLineTest(unittest.TestCase):
    def test_fabric_names_loader_and_library_separately(self):
        self.assertEqual(
            "Tested on Minecraft 1.21.1, Fabric Loader 0.19.5, Fabric API 0.116.17+1.21.1.",
            tested_line(NODES["1.21.1-fabric"]),
        )

    def test_fabric_1_20_1_names_loader_and_library_separately(self):
        self.assertEqual(
            "Tested on Minecraft 1.20.1, Fabric Loader 0.19.5, Fabric API 0.92.12+1.20.1.",
            tested_line(NODES["1.20.1-fabric"]),
        )

    def test_neoforge_collapses_the_repeated_version(self):
        line = tested_line(NODES["1.21.1-neoforge"])
        self.assertEqual("Tested on Minecraft 1.21.1, NeoForge 21.1.251.", line)
        self.assertEqual(1, line.count("21.1.251"))

    def test_forge_collapses_the_repeated_version(self):
        self.assertEqual("Tested on Minecraft 1.20.1, Forge 47.4.23.", tested_line(NODES["1.20.1-forge"]))

    def test_every_node_has_a_tested_line(self):
        for node, info in NODES.items():
            line = tested_line(info)
            self.assertIn(info["mc"], line, node)
            self.assertTrue(line.startswith("Tested on Minecraft"), node)


class WaveStatementTest(unittest.TestCase):
    def test_wave_1_names_its_own_four_combinations_and_the_rest_as_planned(self):
        statement = wave_statement(NODES["1.21.1-fabric"])
        self.assertIn("New in this release: Fabric, 1.21.1; NeoForge, 1.21.1; Fabric, 26.2; NeoForge, 26.2.", statement)
        self.assertIn("Still planned:", statement)
        self.assertIn("Forge, 1.20.1", statement)
        self.assertIn("Fabric, 1.21.4", statement)

    def test_wave_2_names_both_1_20_1_combinations_and_wave_3_is_still_planned(self):
        statement = wave_statement(NODES["1.20.1-forge"])
        self.assertIn("New in this release:", statement)
        self.assertIn("Forge, 1.20.1", statement)
        self.assertIn("Fabric, 1.20.1", statement)
        self.assertIn("Fabric, 1.21.4", statement)
        self.assertIn("NeoForge, 1.21.8", statement)

    def test_wave_2_is_symmetric_between_its_own_two_combinations(self):
        forge_statement = wave_statement(NODES["1.20.1-forge"])
        fabric_statement = wave_statement(NODES["1.20.1-fabric"])
        self.assertEqual(forge_statement, fabric_statement)

    def test_last_wave_has_nothing_still_planned(self):
        for node in ("1.21.4-fabric", "1.21.4-neoforge", "1.21.5-fabric", "1.21.5-neoforge",
                      "1.21.8-fabric", "1.21.8-neoforge"):
            statement = wave_statement(NODES[node])
            self.assertTrue(statement.endswith("Still planned: none."), f"{node}: {statement}")

    def test_wave_3_new_list_names_all_six_wave_3_combinations(self):
        statement = wave_statement(NODES["1.21.8-neoforge"])
        for combination in ("Fabric, 1.21.4", "NeoForge, 1.21.4", "Fabric, 1.21.5",
                             "NeoForge, 1.21.5", "Fabric, 1.21.8", "NeoForge, 1.21.8"):
            self.assertIn(combination, statement)


class NotesForTest(unittest.TestCase):
    def test_unknown_node_raises_naming_it(self):
        target = make_target("1.99.9-fabric")
        with self.assertRaises(SystemExit) as ctx:
            notes_for(target, "body text")
        self.assertIn("1.99.9-fabric", str(ctx.exception))

    def test_notes_carry_title_changelog_tested_line_and_wave_statement(self):
        target = make_target("1.21.1-fabric")
        text = notes_for(target, "- Some change (GV-1).")
        self.assertIn("# Grounded Villages 0.1.0 for 1.21.1-fabric", text)
        self.assertIn("- Some change (GV-1).", text)
        self.assertIn("Tested on Minecraft 1.21.1, Fabric Loader 0.19.5, Fabric API 0.116.17+1.21.1.", text)
        self.assertIn("New in this release:", text)

    def test_empty_changelog_body_is_omitted_cleanly(self):
        target = make_target("26.2-neoforge")
        text = notes_for(target, "")
        self.assertIn("Tested on Minecraft 26.2, NeoForge 26.2.0.88.", text)


class MainCommandTest(unittest.TestCase):
    """The command a person runs: a fixture repo, real files, a subprocess invocation."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name) / "repo"
        (self.root / "docs" / "modrinth").mkdir(parents=True)
        (self.root / "CHANGELOG.md").write_text(UNRELEASED_CHANGELOG)
        self.nodes = list(NODES.keys())
        targets = {"targets": [make_target(n) for n in self.nodes]}
        (self.root / "docs" / "modrinth" / "targets.json").write_text(json.dumps(targets))

    def tearDown(self):
        self.tmp.cleanup()

    def run_tool(self, *extra_args: str) -> subprocess.CompletedProcess:
        return subprocess.run(
            [sys.executable, str(TOOL), "--root", str(self.root), *extra_args],
            capture_output=True, text=True,
        )

    def test_writes_one_notes_file_per_target(self):
        proc = self.run_tool()
        self.assertEqual(0, proc.returncode, proc.stdout + proc.stderr)
        notes_dir = self.root / "dist" / "notes"
        written = sorted(p.name for p in notes_dir.glob("*.md"))
        expected = sorted(f"grounded_villages-{n}.md" for n in self.nodes)
        self.assertEqual(expected, written)

    def test_one_target_s_notes_content_end_to_end(self):
        proc = self.run_tool()
        self.assertEqual(0, proc.returncode, proc.stdout + proc.stderr)
        text = (self.root / "dist" / "notes" / "grounded_villages-1.21.4-fabric.md").read_text()
        self.assertIn("Bootstrap: repository scaffold", text)
        self.assertIn("Tested on Minecraft 1.21.4, Fabric Loader 0.19.5, Fabric API 0.119.4+1.21.4.", text)
        self.assertIn("New in this release: Fabric, 1.21.4; NeoForge, 1.21.4; Fabric, 1.21.5; "
                       "NeoForge, 1.21.5; Fabric, 1.21.8; NeoForge, 1.21.8.", text)
        self.assertIn("Still planned: none.", text)

    def test_missing_changelog_fails(self):
        (self.root / "CHANGELOG.md").unlink()
        proc = self.run_tool()
        self.assertNotEqual(0, proc.returncode)
        self.assertIn("changelog not found", proc.stdout + proc.stderr)

    def test_missing_targets_file_fails(self):
        (self.root / "docs" / "modrinth" / "targets.json").unlink()
        proc = self.run_tool()
        self.assertNotEqual(0, proc.returncode)
        self.assertIn("targets file not found", proc.stdout + proc.stderr)


if __name__ == "__main__":
    unittest.main()
