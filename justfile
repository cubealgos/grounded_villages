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
# "CI matrix"). `--continue` on this and every other aggregate invocation below is
# `PLATFORM-REQ-004`'s structural half: the six nodes already have no dependency on each other in
# the build graph (per-node compilation, `04-architecture.md` `ARCH-DEC-002`), so `--continue`
# makes Gradle actually run every node to completion and report each one's own result, instead of
# its own fail-fast default stopping at the first red node and leaving the rest unreported.
build:
    ./gradlew chiseledBuild --continue

# Static analysis and the project's own rules, without the tests.
# `-x runGameTest` is dropped for now: no game-test task exists anywhere in the project yet
# (GV-2 ships zero mixin/rejection code to test; the harness lands with docs/spec/operations
# /testing.md "Verification for the first ticket"), and Gradle's `-x` fails hard on a task path
# that resolves on no project at all. Restore the exclusion once that task exists.
lint:
    ./gradlew chiseledCheck -x test --continue

# Unit tests across every node, then the repository tools as commands.
test: test-java test-tools

test-java:
    ./gradlew chiseledCheck --continue

test-tools:
    python3 -m unittest discover -s tools -p 'test_*.py'

# Server-side game tests on a headless dedicated server, per loader.
gametest:
    ./gradlew chiseledCheck --continue

# GV-10: sweep N seeds for the first village near spawn (height spread, water fraction, piece
# count), 26.2-fabric only. Fixed, checked-in seed list (docs/baseline/seeds.txt, TEST-REQ-003);
# pass a literal seed list instead with `./gradlew :26.2-fabric:seedSweep -Pseeds=1,2,3`.
sweep count="10":
    ./gradlew :26.2-fabric:seedSweep -Pcount={{count}}

# GV-12: the same sweep on 26.2-neoforge -- docs/loaders.md's cross-loader determinism check.
sweep-neoforge count="10":
    ./gradlew :26.2-neoforge:seedSweep -Pcount={{count}}

# GV-8: proves a fixed seed rolls a fixed tier (runs the dedicated server twice, diffs the
# result). 26.2-fabric only, not wired into `check` (see build.fabric.gradle.kts's own comment on
# `tierGameTest` for why).
tiergametest seed="":
    ./gradlew :26.2-fabric:tierGameTest {{ if seed == "" { "" } else { "-Pseed=" + seed } }}

# One version node's client, e.g. `just client 1.21.1-fabric`.
client node:
    ./gradlew :{{node}}:runClient

# One version node's dedicated server, e.g. `just server 26.2-neoforge`.
server node:
    ./gradlew :{{node}}:runServer

# Refresh docs/spec/ from the vault; the vault is authoritative.
spec-sync:
    rsync -a --delete "{{vault_spec}}/" docs/spec/

# Render the Modrinth icon (GV-23): vanilla's own flat village bell item sprite, on the cubealgos
# navy badge, the way create_villager_customers composes the vanilla emerald. Reads it from a
# local Minecraft client jar found by globbing the Gradle cache; pass --jar PATH, or --pick to
# write one of the other rendered candidates instead, via tools/icon.py directly.
icon:
    python3 tools/icon.py

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

# Everything a merge must survive. `build` runs last: `chiseledBuild`'s own `buildAndCollect` /
# `reobfJar` step is a real, independent failure mode `chiseledCheck` does not exercise (GV-2
# found it live on the Forge leg — an empty `@Mixin` refmap breaks `reobfJar` even though `check`
# is green, `contracts/platform-matrix.md` "Second correction"), so packaging is proven here too,
# not assumed from a green test run.
check: lint map-check test gametest build

# Release notes for one version, e.g. `just release-notes grounded_villages-1.21.1-fabric-1.0.0`.
release-notes version:
    python3 tools/release_notes.py {{version}}

# Every shipped jar, built clean, from a tag (REL-REQ-001). GV-19/GV-21 fill in the six-invocation
# publish loop; this recipe stays the build half.
release:
    ./gradlew chiseledBuild
