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

- [ ] `LICENSE` is MIT, copyright cubealgos, matching every sibling mod
- [ ] `NOTICE` credits Fabric API (Apache-2.0), flags NeoForge/Forge/Mojang-mappings licences as
  to-verify, and credits "Improved Village Placement" as prior art per `DEC-009`
- [ ] `README.md` states what the mod does in five lines, the version matrix table, install,
  config path, and licence
- [ ] `CLAUDE.md` routes only, under 60 lines, points at `docs/spec/`, states the pure-worldgen
  rule and the ask-never-decide rule
- [ ] `docs/spec/` is a byte-identical copy of the vault spec (`just spec-sync` recipe added)
- [ ] `docs/modrinth/body.md` first draft exists, crediting prior art and stating no promises
  beyond 1.0
- [ ] `tools/map.py`, `tools/doctor.py`, `tools/release_notes.py` copied and adapted (doctor's
  platform-matrix depth is GV-4's job, not this ticket's)
- [ ] `CHANGELOG.md` has an `## Unreleased` section
- [ ] `.ci/install-tools.sh` is executable (`chmod +x`) and copied verbatim
- [ ] `.woodpecker.yml` copied and adjusted to name Stonecutter-shaped tasks even though GV-2
  fills the real build in
- [ ] `.gitkontor/wiki/architecture/` carries ADR 0001 (Stonecutter, one repo six nodes), ADR 0002
  (shared mixin source tree, no precompiled `common`), ADR 0003 (hand-rolled JSON config)
- [ ] `kontor lint` and `kontor doctor` both clean (doctor's toolchain floors and spec-copy check
  pass; the Stonecutter-node checks GV-4 adds do not exist yet, so are not in scope here)
- [ ] `chore/bootstrap` merged into `development` (out of scope for this ticket — the main
  session opens the PR; this ticket is not taken to `done`)

## Constraints and prior findings

`docs/spec/README.md`: "Status: ready for bootstrap." `decisions/DEC-002-name.md`: mod id
`grounded_villages`, no `Create:` prefix (not a Create add-on). `decisions/DEC-003-licence.md`:
MIT, no CLA, public under `cubealgos` from the first commit — a deliberate divergence from the
Apache-2.0-plus-CLA default and "no remote until justified," inherited rather than re-decided.
`villager_voices` is the model repo for file shape; conventions are copied, not its content.
Executable bits matter: `villager_voices`' own `.ci/install-tools.sh` notes a sibling once lost its
`+x` bit in copying. `chore/bootstrap` is never merged by this ticket — the main session opens the
PR after the local session hands off; this ticket does not reach `done`, only the acceptance
criteria that are true locally are ticked.
