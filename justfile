# The task surface: the fleet's standard recipe names, pointing at the Stonecutter tasks
# `multi-loader-multi-version-mods-2026.md` "Grounded Villages" names. GV-2 fills the actual
# Stonecutter build in.

main_checkout := parent_directory(`git rev-parse --path-format=absolute --git-common-dir`)
vault_spec := env("GV_VAULT_SPEC", main_checkout / ".." / "heimathafen" / "vault" / "projects" / "grounded_villages" / "spec")

default:
    @just --list

# Resolve every dependency and prove the toolchain.
bootstrap:
    ./gradlew --version

# Every version node's own jar, via Stonecutter's aggregate task (registered explicitly in the
# root build script, not automatic from applying the plugin — `contracts/platform-matrix.md`
# "CI matrix").
build:
    ./gradlew chiseledBuild

# Static analysis and the project's own rules, without the tests.
# `-x runGameTest` is dropped for now: no game-test task exists anywhere in the project yet
# (GV-2 ships zero mixin/rejection code to test; the harness lands with docs/spec/operations
# /testing.md "Verification for the first ticket"), and Gradle's `-x` fails hard on a task path
# that resolves on no project at all. Restore the exclusion once that task exists.
lint:
    ./gradlew chiseledCheck -x test

# Unit tests across every node, then the repository tools as commands.
test: test-java test-tools

test-java:
    ./gradlew chiseledCheck

test-tools:
    python3 -m unittest discover -s tools -p 'test_*.py'

# Server-side game tests on a headless dedicated server, per loader.
gametest:
    ./gradlew chiseledCheck

# GV-10: sweep N seeds for the first village near spawn (height spread, water fraction, piece
# count), 26.2-fabric only. Fixed, checked-in seed list (docs/baseline/seeds.txt, TEST-REQ-003);
# pass a literal seed list instead with `./gradlew :26.2-fabric:seedSweep -Pseeds=1,2,3`.
sweep count="10":
    ./gradlew :26.2-fabric:seedSweep -Pcount={{count}}

# One version node's client, e.g. `just client 1.21.1-fabric`.
client node:
    ./gradlew :{{node}}:runClient

# One version node's dedicated server, e.g. `just server 26.2-neoforge`.
server node:
    ./gradlew :{{node}}:runServer

# Refresh docs/spec/ from the vault; the vault is authoritative.
spec-sync:
    rsync -a --delete "{{vault_spec}}/" docs/spec/

# Regenerate docs/map.md and docs/map/ from the source.
map:
    python3 tools/map.py

map-check:
    python3 tools/map.py --check

# Repository conformance, read-only.
doctor: doctor-repo doctor-toolchain

doctor-repo:
    kontor doctor

doctor-toolchain:
    python3 tools/doctor.py

# Everything a merge must survive.
check: lint map-check test gametest

# Release notes for one version, e.g. `just release-notes grounded_villages-1.21.1-fabric-1.0.0`.
release-notes version:
    python3 tools/release_notes.py {{version}}

# Every shipped jar, built clean, from a tag (REL-REQ-001). GV-19/GV-21 fill in the six-invocation
# publish loop; this recipe stays the build half.
release:
    ./gradlew chiseledBuild
