---
schema_version: 1
id: 01M30J4AZYCMC0ZKTYKK3ABP3W
key: GV-24
type: bug
title: Jar names from the build do not follow REL-DEC-001; reconcile build.<loader>.gradle.kts, release_notes.py and targets.json
created_by: kevin
created_at: 2026-09-20T23:23:57Z
---

## Scope

Found by GV-19: the build writes `grounded_villages-<loader>-<version>+<mc>.jar` under `build/libs/<mod.version>/`, while `docs/spec/operations/release.md` `REL-DEC-001`, `tools/release_notes.py` and `docs/modrinth/targets.json` expect `grounded_villages-<mc>-<loader>-<version>.jar`. A real six-jar release fails on the first missing jar.

## Approach

Decide the one scheme (the spec's, unless the loader plugins make it awkward: then amend `REL-DEC-001` in the vault first), set it in every `build.<loader>.gradle.kts` (`archivesName`/`archiveFileName`), point `targets.json` and `release_notes.py` at it, and add the check to `tools/doctor.py` (`check_targets` verifies each target's jar path matches what `chiseledBuild` produces, by building or by deriving the name from the same expression).

## Acceptance criteria

- [x] One jar-name scheme, in the spec, the build files, the release-notes tool and the targets file.
- [x] `just publish-dry` finds all jars after `chiseledBuild`; `tools/doctor.py` fails on drift; `just check` green; merged through a Forgejo pull request into `development`.

## Constraints and prior findings

GV-19's report; GV-2 named the jars; `REL-DEC-001` in `docs/spec/operations/release.md`.
