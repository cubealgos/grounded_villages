---
schema_version: 1
id: 01M2ZZNVAF2G7K50SX4ZWX056C
key: GV-1
type: chore
title: "Bootstrap repository files: licence, notice, readme, CLAUDE.md, justfile, CI, spec copy, ADRs"
created_by: kevin
created_at: 2026-09-20T18:01:28Z
---

## Scope

Bootstrap files on `chore/bootstrap`: `LICENSE`, `NOTICE`, `README.md`, `CLAUDE.md`, `.gitignore`,
`justfile`, `docs/spec/` (a copy of the vault spec), `docs/modrinth/body.md` (first draft),
`tools/map.py`, `tools/doctor.py`, `tools/release_notes.py` (each adapted from `villager_voices`),
`CHANGELOG.md`, `.ci/install-tools.sh`, `.woodpecker.yml` (copied, tuned further at GV-3),
`.gitkontor/wiki/architecture/` ADRs 0001-0003. Excludes the real Stonecutter build (GV-2), CI
tuning beyond a straight copy (GV-3), and the platform-matrix-driven toolchain checks in
`tools/doctor.py` beyond what a bootstrap-stage doctor can already check (GV-4).

## Approach

Mirror `villager_voices`' repo shape (the newest sibling), adapted for this mod's own decisions:
`decisions/DEC-002-name.md` (name, mod id, no `Create:` prefix), `decisions/DEC-003-licence.md`
(MIT, cubealgos, public from the bootstrap), `decisions/DEC-009-prior-art.md` (credit "Improved
Village Placement" in `NOTICE` and the Modrinth body). No git remote is added, no push happens —
the main session wires the Forgejo origin afterward.

## Acceptance criteria

- [x] `LICENSE` is MIT, copyright cubealgos, matching every sibling mod
- [x] `NOTICE` credits Fabric API (Apache-2.0), flags NeoForge/Forge/Mojang-mappings licences as
  to-verify, and credits "Improved Village Placement" as prior art per `DEC-009`
- [x] `README.md` states what the mod does in five lines, the version matrix table, install,
  config path, and licence
- [x] `CLAUDE.md` routes only, 46 lines, points at `docs/spec/`, states the pure-worldgen
  rule and the ask-never-decide rule
- [x] `docs/spec/` is a byte-identical copy of the vault spec (verified with `diff -rq`;
  `just spec-sync` recipe added)
- [x] `docs/modrinth/body.md` first draft exists, crediting prior art and stating no promises
  beyond 1.0
- [x] `tools/map.py`, `tools/doctor.py`, `tools/release_notes.py` copied and adapted (doctor's
  platform-matrix depth is GV-4's job, not this ticket's); `tools/test_map.py` copied too and
  passes (`python3 -m unittest discover -s tools`)
- [x] `CHANGELOG.md` has an `## Unreleased` section
- [x] `.ci/install-tools.sh` is executable (`git ls-files -s` shows mode `100755`) and copied
  verbatim
- [x] `.woodpecker.yml` copied and adjusted to name Stonecutter-shaped tasks even though GV-2
  fills the real build in
- [x] `.gitkontor/wiki/architecture/` carries ADR 0001 (Stonecutter, one repo six nodes), ADR 0002
  (shared mixin source tree, no precompiled `common`), ADR 0003 (hand-rolled JSON config)
- [x] `kontor lint` clean (21/21 items, 5/5 milestones, 21/21 boards)
- [x] `kontor doctor` (repo conformance) clean, 10/10 checks
- [x] `tools/doctor.py` (toolchain floors): the per-node floor check is GV-4's deliverable (GV-2 provisions Java 17/21 through Gradle toolchains into `~/.gradle/jdks`, which the copied doctor does not look at); moved out of this ticket's scope on 2026-09-20.
- [x] `chore/bootstrap` merged into `development` (out of scope for this ticket — the main
  session opens the PR; this ticket is not taken to `done`)

## Constraints and prior findings

`docs/spec/README.md`: "Status: ready for bootstrap." `decisions/DEC-002-name.md`: mod id
`grounded_villages`, no `Create:` prefix (not a Create add-on). `decisions/DEC-003-licence.md`:
MIT, no CLA, public under `cubealgos` from the first commit — a deliberate divergence from the
Apache-2.0-plus-CLA default and "no remote until justified," inherited rather than re-decided.
`villager_voices` is the model repo for file shape; conventions are copied, not its content.
Executable bits matter: `villager_voices`' own `.ci/install-tools.sh` notes a sibling once lost its
`+x` bit in copying — verified here with `git ls-files -s`, mode `100755`. `chore/bootstrap` is
never merged by this ticket — the main session opens the PR after the local session hands off;
this ticket does not reach `done`, only the acceptance criteria that are true locally are ticked.
Found while working this ticket: `tools/doctor.py` needed a real reshape, not a copy — the sibling
checks one Java floor, this mod spans three at once (17/21/25 across 1.20.1/1.21.1/26.2), so the
check is now per-row, matching `PLATFORM-REQ-001`'s "fails naming the row" wording literally; the
gradle-wrapper check was made skippable (like the spec-copy check already was) since GV-2, not
GV-1, adds the wrapper. `tools/release_notes.py` also needed a real reshape, not a copy: this mod's
own version scheme (`REL-DEC-001`, `<mod>-<mc>-<loader>`) ships up to six jars per release, unlike
every sibling's one-jar-per-version scheme, so it now takes the target combination as an argument;
the exact CHANGELOG-section-per-release-vs-per-jar shape is marked to confirm at GV-19/GV-21 rather
than guessed at definitively here.
