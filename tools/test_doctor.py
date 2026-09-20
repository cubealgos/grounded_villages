"""tools/doctor.py, exercised as the command a person runs (rule 6 of the standard): a fixture
repository tree in a temporary directory, environment variables standing in for JAVA_HOME,
GRADLE_USER_HOME and GV_VAULT_SPEC, and a fake `java` on PATH -- never the real JDKs or vault this
machine happens to have. One test per PLATFORM-REQ-001 failure mode: a missing JDK, a drifted
dependency coordinate, and a stale spec copy, plus the all-green path."""
import json
import os
import stat
import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

TOOL = Path(__file__).resolve().parent / "doctor.py"
MAP_TOOL = Path(__file__).resolve().parent / "map.py"

WRAPPER_PROPS = "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.5.1-bin.zip\n"

SETTINGS_GRADLE = textwrap.dedent('''
    plugins {
        id("dev.kikugie.stonecutter") version "0.9.8"
    }

    stonecutter {
        create(rootProject) {
            fun match(project: String, vararg loaders: String, version: String = project) {
                for (loader in loaders) version("$project-$loader", version).buildscript("build.$loader.gradle.kts")
            }

            match("1.20.1", "fabric", "forge")
            match("1.21.1", "fabric", "neoforge")
            match("26.2", "fabric", "neoforge")
        }
    }
''')

# The six nodes SETTINGS_GRADLE's own match() calls above declare -- kept as one importable list
# so a test that mutates SETTINGS_GRADLE's node set can build a matching (or deliberately
# mismatched) targets.json from the same source instead of retyping the six strings.
SETTINGS_GRADLE_NODES = [
    "1.20.1-fabric", "1.20.1-forge", "1.21.1-fabric", "1.21.1-neoforge", "26.2-fabric", "26.2-neoforge",
]


def targets_json(nodes: list[str]) -> str:
    return json.dumps({"targets": [
        {"node": n, "jar": f"build/libs/0.1.0/grounded_villages-{n}-0.1.0.jar",
         "game_versions": [n.rsplit("-", 1)[0]], "loaders": [n.rsplit("-", 1)[1]],
         "version_number": f"grounded_villages-{n}"}
        for n in nodes
    ]})

STONECUTTER_PROPS = textwrap.dedent('''
    deps.fabric_loader = "0.19.5"
    loomx.loom_version = "1.17-SNAPSHOT"

    [fabric."1.20.1"]
    deps.fabric_api = "0.92.12+1.20.1"

    [forge."1.20.1"]
    deps.forge = "47.4.23"

    [fabric."1.21.1"]
    deps.fabric_api = "0.116.17+1.21.1"

    [neoforge."1.21.1"]
    deps.neoforge = "21.1.251"

    [fabric."26.2"]
    deps.fabric_api = "0.161.0+26.2"

    [neoforge."26.2"]
    deps.neoforge = "26.2.0.88"

    [fabric."1.21.4"]
    deps.fabric_api = "0.119.4+1.21.4"

    [neoforge."1.21.4"]
    deps.neoforge = "21.4.157"

    [fabric."1.21.5"]
    deps.fabric_api = "0.128.2+1.21.5"

    [neoforge."1.21.5"]
    deps.neoforge = "21.5.98"

    [fabric."1.21.8"]
    deps.fabric_api = "0.136.1+1.21.8"

    [neoforge."1.21.8"]
    deps.neoforge = "21.8.54"
''')

FAKE_JAVA_25 = "#!/bin/sh\necho 'openjdk version \"25.0.1\" 2026-01-01' 1>&2\n"


def write_fake_java(bin_dir: Path, version: str) -> Path:
    bin_dir.mkdir(parents=True, exist_ok=True)
    java = bin_dir / "java"
    java.write_text(f"#!/bin/sh\necho 'openjdk version \"{version}\" 2026-01-01' 1>&2\n")
    java.chmod(java.stat().st_mode | stat.S_IEXEC | stat.S_IXGRP | stat.S_IXOTH)
    return java


def write_provisioned_jdk(jdks_root: Path, name: str, major: str, full: str, macos: bool = True) -> None:
    """A Gradle-provisioned JDK's own directory shape: a `release` file plus a `bin/java`, nested
    under `Contents/Home/` on macOS and directly under the version folder on Linux."""
    home = jdks_root / name / f"jdk-{full}" / ("Contents/Home" if macos else "")
    home = Path(str(home).rstrip("/"))
    home.mkdir(parents=True, exist_ok=True)
    (home / "release").write_text(f'JAVA_VERSION="{full}"\nIMPLEMENTOR="Eclipse Adoptium"\n')
    write_fake_java(home / "bin", major)


class DoctorFixture(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name) / "repo"
        (self.root / "gradle" / "wrapper").mkdir(parents=True)
        (self.root / "gradle" / "wrapper" / "gradle-wrapper.properties").write_text(WRAPPER_PROPS)
        (self.root / "settings.gradle.kts").write_text(SETTINGS_GRADLE)
        (self.root / "stonecutter.properties.toml").write_text(STONECUTTER_PROPS)
        (self.root / "docs" / "modrinth").mkdir(parents=True)
        (self.root / "docs" / "modrinth" / "targets.json").write_text(targets_json(SETTINGS_GRADLE_NODES))
        (self.root / "docs" / "spec").mkdir(parents=True)
        (self.root / "docs" / "spec" / "README.md").write_text("spec\n")
        # docs/map.md is generated, never hand-written (docs/spec/README.md is a `.md`, so map.py's
        # own source-file scan ignores it) -- run the real tool once so the fixture starts current,
        # exactly as `just map` would leave it.
        subprocess.run([sys.executable, str(MAP_TOOL), "--root", str(self.root)], capture_output=True, text=True, check=True)

        # An empty gradle-provisioned-JDK root and vault by default: every "gradle jdks" and
        # "spec copy" check that does not set up its own fixture sees nothing there, same as the
        # tool's own honest "skipped"/"not found" reporting.
        self.gradle_home = Path(self.tmp.name) / "gradle-home"
        self.gradle_home.mkdir()
        self.vault = Path(self.tmp.name) / "vault-missing"

        # A PATH with nothing but a fake `just`, `python3`, `kontor`, and (unless a test overrides
        # it) no `java` at all, so java resolution is fully driven by JAVA_HOME/JAVA*_HOME/gradle
        # jdks rather than by whatever is really installed on this machine.
        self.fake_bin = Path(self.tmp.name) / "bin"
        self.fake_bin.mkdir()
        for name, out in (("just", "just 1.58.0"), ("python3", "Python 3.12.0")):
            script = self.fake_bin / name
            script.write_text(f"#!/bin/sh\necho '{out}'\n")
            script.chmod(script.stat().st_mode | stat.S_IEXEC | stat.S_IXGRP | stat.S_IXOTH)
        kontor = self.fake_bin / "kontor"
        kontor.write_text("#!/bin/sh\necho kontor 1.0.0\n")
        kontor.chmod(kontor.stat().st_mode | stat.S_IEXEC | stat.S_IXGRP | stat.S_IXOTH)
        # `diff` and `git` are the real system tools the checks shell out to -- linked in by their
        # known absolute paths rather than opening the whole real PATH, which would let a real
        # `java` leak back in and defeat the "no java anywhere" fixtures below.
        for real in ("/usr/bin/diff", "/usr/bin/git"):
            if Path(real).exists():
                (self.fake_bin / Path(real).name).symlink_to(real)

        self.env = {
            "PATH": str(self.fake_bin),
            "HOME": str(Path(self.tmp.name) / "home"),
            "GRADLE_USER_HOME": str(self.gradle_home),
            "GV_VAULT_SPEC": str(self.vault),
        }

    def tearDown(self):
        self.tmp.cleanup()

    def run_doctor(self, env_overrides: dict | None = None) -> subprocess.CompletedProcess:
        env = dict(self.env)
        env.update(env_overrides or {})
        return subprocess.run([sys.executable, str(TOOL), "--root", str(self.root)],
                               capture_output=True, text=True, env=env)


class MissingJdkTest(DoctorFixture):
    def test_no_java_anywhere_fails_naming_every_row(self):
        proc = self.run_doctor()
        self.assertEqual(5, proc.returncode, proc.stdout + proc.stderr)
        for row, floor in (("1.20.1 (fabric, forge)", "17"), ("1.21.1 (fabric, neoforge)", "21"),
                            ("26.2 (fabric, neoforge)", "25")):
            self.assertIn(f"FAIL  java ({row}): no Java {floor} found", proc.stdout)

    def test_one_missing_row_fails_only_that_row(self):
        """26.2's Java 25 is on PATH; 17 and 21 are absent -- each row is judged on its own."""
        write_fake_java(self.fake_bin, "25.0.1")
        proc = self.run_doctor()
        self.assertEqual(5, proc.returncode, proc.stdout + proc.stderr)
        self.assertIn("ok   java (26.2 (fabric, neoforge)): 25 on PATH", proc.stdout)
        self.assertIn("FAIL  java (1.20.1 (fabric, forge)): no Java 17 found", proc.stdout)
        self.assertIn("FAIL  java (1.21.1 (fabric, neoforge)): no Java 21 found", proc.stdout)


class GradleProvisionedJdkTest(DoctorFixture):
    def test_gradle_provisioned_jdks_satisfy_every_row(self):
        """The exact shape GV-2 leaves behind: no JAVA_HOME, no java on PATH, but Gradle itself
        provisioned 17/21/25 under $GRADLE_USER_HOME/jdks/, each found via its own `release` file."""
        write_provisioned_jdk(self.gradle_home / "jdks", "eclipse_adoptium-17", "17.0.20.1", "17.0.20.1")
        write_provisioned_jdk(self.gradle_home / "jdks", "eclipse_adoptium-21", "21.0.12.1", "21.0.12.1")
        write_provisioned_jdk(self.gradle_home / "jdks", "eclipse_adoptium-25", "25.0.1", "25.0.1", macos=False)
        proc = self.run_doctor()
        self.assertEqual(0, proc.returncode, proc.stdout + proc.stderr)
        self.assertIn("ok   java (1.20.1 (fabric, forge)): 17 on eclipse_adoptium-17 (gradle jdks)", proc.stdout)
        self.assertIn("ok   java (1.21.1 (fabric, neoforge)): 21 on eclipse_adoptium-21 (gradle jdks)", proc.stdout)
        self.assertIn("ok   java (26.2 (fabric, neoforge)): 25 on eclipse_adoptium-25 (gradle jdks)", proc.stdout)
        self.assertIn("toolchain: all floors met", proc.stdout)

    def test_gradle_jdk_of_the_wrong_major_does_not_satisfy_the_row(self):
        write_provisioned_jdk(self.gradle_home / "jdks", "eclipse_adoptium-11", "11.0.1", "11.0.1")
        proc = self.run_doctor()
        self.assertEqual(5, proc.returncode, proc.stdout + proc.stderr)
        self.assertIn("FAIL  java (1.20.1 (fabric, forge)): no Java 17 found", proc.stdout)


class DriftedCoordinateTest(DoctorFixture):
    def setUp(self):
        super().setUp()
        write_provisioned_jdk(self.gradle_home / "jdks", "eclipse_adoptium-17", "17.0.20.1", "17.0.20.1")
        write_provisioned_jdk(self.gradle_home / "jdks", "eclipse_adoptium-21", "21.0.12.1", "21.0.12.1")
        write_provisioned_jdk(self.gradle_home / "jdks", "eclipse_adoptium-25", "25.0.1", "25.0.1", macos=False)

    def test_a_drifted_dependency_pin_fails_naming_its_row(self):
        drifted = STONECUTTER_PROPS.replace('deps.fabric_api = "0.116.17+1.21.1"', 'deps.fabric_api = "0.200.0+1.21.1"')
        (self.root / "stonecutter.properties.toml").write_text(drifted)
        proc = self.run_doctor()
        self.assertEqual(5, proc.returncode, proc.stdout + proc.stderr)
        self.assertIn("FAIL  coordinates (Fabric, 1.21.1): fabric.1.21.1.deps.fabric_api = '0.200.0+1.21.1'; "
                      "matrix pins '0.116.17+1.21.1'", proc.stdout)
        # every other row is unaffected by one row's drift
        self.assertIn("ok   coordinates (Fabric, 1.20.1)", proc.stdout)

    def test_a_missing_coordinate_fails_naming_its_row(self):
        without_neoforge = STONECUTTER_PROPS.replace('deps.neoforge = "26.2.0.88"', "")
        (self.root / "stonecutter.properties.toml").write_text(without_neoforge)
        proc = self.run_doctor()
        self.assertEqual(5, proc.returncode, proc.stdout + proc.stderr)
        self.assertIn("FAIL  coordinates (NeoForge, 26.2): neoforge.26.2.deps.neoforge missing", proc.stdout)

    def test_a_drifted_stonecutter_version_fails(self):
        (self.root / "settings.gradle.kts").write_text(SETTINGS_GRADLE.replace('"0.9.8"', '"0.5.1"'))
        proc = self.run_doctor()
        self.assertEqual(5, proc.returncode, proc.stdout + proc.stderr)
        self.assertIn("FAIL  stonecutter: 0.5.1 pinned in settings.gradle.kts; matrix pins 0.9.8", proc.stdout)


class TargetsTest(DoctorFixture):
    """docs/modrinth/targets.json (GV-19) must list exactly one target per Stonecutter node --
    read from settings.gradle.kts, never a hardcoded count, so a node a later ticket adds (GV-17)
    cannot be forgotten in a release."""

    def setUp(self):
        super().setUp()
        write_provisioned_jdk(self.gradle_home / "jdks", "eclipse_adoptium-17", "17.0.20.1", "17.0.20.1")
        write_provisioned_jdk(self.gradle_home / "jdks", "eclipse_adoptium-21", "21.0.12.1", "21.0.12.1")
        write_provisioned_jdk(self.gradle_home / "jdks", "eclipse_adoptium-25", "25.0.1", "25.0.1", macos=False)

    def test_all_six_nodes_covered_passes(self):
        proc = self.run_doctor()
        self.assertEqual(0, proc.returncode, proc.stdout + proc.stderr)
        self.assertIn("ok   targets: docs/modrinth/targets.json covers all 6 Stonecutter node(s)", proc.stdout)

    def test_a_node_missing_from_targets_json_fails_naming_it(self):
        without_one = [n for n in SETTINGS_GRADLE_NODES if n != "26.2-neoforge"]
        (self.root / "docs" / "modrinth" / "targets.json").write_text(targets_json(without_one))
        proc = self.run_doctor()
        self.assertEqual(5, proc.returncode, proc.stdout + proc.stderr)
        self.assertIn("FAIL  targets: missing from targets.json: ['26.2-neoforge']", proc.stdout)

    def test_a_new_stonecutter_node_not_yet_in_targets_json_fails(self):
        """GV-17 adding a seventh node: settings.gradle.kts moves first, targets.json has not
        caught up yet -- this is exactly the gap the check exists to catch."""
        added = SETTINGS_GRADLE.replace('match("26.2", "fabric", "neoforge")',
                                         'match("26.2", "fabric", "neoforge")\n            match("1.21.9", "fabric", "neoforge")')
        (self.root / "settings.gradle.kts").write_text(added)
        proc = self.run_doctor()
        self.assertEqual(5, proc.returncode, proc.stdout + proc.stderr)
        self.assertIn("FAIL  targets: missing from targets.json: ['1.21.9-fabric', '1.21.9-neoforge']", proc.stdout)

    def test_an_extra_targets_json_entry_not_a_stonecutter_node_fails(self):
        (self.root / "docs" / "modrinth" / "targets.json").write_text(
            targets_json(SETTINGS_GRADLE_NODES + ["1.19.2-fabric"])
        )
        proc = self.run_doctor()
        self.assertEqual(5, proc.returncode, proc.stdout + proc.stderr)
        self.assertIn("FAIL  targets: in targets.json but not a Stonecutter node: ['1.19.2-fabric']", proc.stdout)

    def test_a_duplicated_node_in_targets_json_fails(self):
        (self.root / "docs" / "modrinth" / "targets.json").write_text(
            targets_json(SETTINGS_GRADLE_NODES + ["1.20.1-fabric"])
        )
        proc = self.run_doctor()
        self.assertEqual(5, proc.returncode, proc.stdout + proc.stderr)
        self.assertIn("FAIL  targets: duplicated in targets.json: ['1.20.1-fabric']", proc.stdout)

    def test_a_missing_targets_file_fails(self):
        (self.root / "docs" / "modrinth" / "targets.json").unlink()
        proc = self.run_doctor()
        self.assertEqual(5, proc.returncode, proc.stdout + proc.stderr)
        self.assertIn("FAIL  targets:", proc.stdout)
        self.assertIn("missing", proc.stdout)


class StaleSpecCopyTest(DoctorFixture):
    def setUp(self):
        super().setUp()
        write_provisioned_jdk(self.gradle_home / "jdks", "eclipse_adoptium-17", "17.0.20.1", "17.0.20.1")
        write_provisioned_jdk(self.gradle_home / "jdks", "eclipse_adoptium-21", "21.0.12.1", "21.0.12.1")
        write_provisioned_jdk(self.gradle_home / "jdks", "eclipse_adoptium-25", "25.0.1", "25.0.1", macos=False)
        self.vault = Path(self.tmp.name) / "vault"
        self.vault.mkdir()
        self.env["GV_VAULT_SPEC"] = str(self.vault)

    def test_identical_vault_copy_passes(self):
        (self.vault / "README.md").write_text("spec\n")
        proc = self.run_doctor()
        self.assertEqual(0, proc.returncode, proc.stdout + proc.stderr)
        self.assertIn("ok   spec copy: docs/spec/ is identical to the vault", proc.stdout)

    def test_a_differing_vault_copy_fails_and_exits_6_alone(self):
        (self.vault / "README.md").write_text("a different spec entirely\n")
        proc = self.run_doctor()
        self.assertEqual(6, proc.returncode, proc.stdout + proc.stderr)
        self.assertIn("FAIL  spec copy: docs/spec/ differs from the vault", proc.stdout)

    def test_a_missing_vault_is_skipped_not_failed(self):
        env = dict(self.env)
        env["GV_VAULT_SPEC"] = str(Path(self.tmp.name) / "does-not-exist")
        proc = self.run_doctor(env)
        self.assertEqual(0, proc.returncode, proc.stdout + proc.stderr)
        self.assertIn("skip  spec copy: vault not present", proc.stdout)


class AllGreenTest(DoctorFixture):
    def test_every_check_passing_exits_zero(self):
        write_provisioned_jdk(self.gradle_home / "jdks", "eclipse_adoptium-17", "17.0.20.1", "17.0.20.1")
        write_provisioned_jdk(self.gradle_home / "jdks", "eclipse_adoptium-21", "21.0.12.1", "21.0.12.1")
        write_provisioned_jdk(self.gradle_home / "jdks", "eclipse_adoptium-25", "25.0.1", "25.0.1", macos=False)
        proc = self.run_doctor()
        self.assertEqual(0, proc.returncode, proc.stdout + proc.stderr)
        self.assertNotIn("FAIL", proc.stdout)
        self.assertIn("toolchain: all floors met", proc.stdout)


if __name__ == "__main__":
    unittest.main()
