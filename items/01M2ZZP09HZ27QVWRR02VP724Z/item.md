---
schema_version: 1
id: 01M2ZZP09HZ27QVWRR02VP724Z
key: GV-4
type: chore
title: "just doctor: per-node toolchain floors from the platform matrix"
created_by: kevin
created_at: 2026-09-20T18:01:33Z
---

## Scope

`tools/doctor.py`, adapted from `villager_voices`: reads `contracts/platform-matrix.md`'s per-row
toolchain table (Java 17/21/25 across the three MC generations, Gradle wrapper >= `9.5.1`, `just`,
`python3`, `kontor`), plus the generic spec-copy-diff and map-staleness checks every sibling doctor
already has.

## Approach

Extend the single-floor `FLOORS` dict shape (`villager_voices/tools/doctor.py`) to a per-row table,
since this mod (unlike `villager_voices`) spans three Java majors at once, not one floor. Report
every row's own Java check by name, mirroring `PLATFORM-REQ-001`'s "fails naming the row" wording
literally rather than a single pass/fail.

## Acceptance criteria

- [x] doctor checks Java 17, 21, and 25 are each discoverable (via `JAVA_HOME`-style env vars or
  PATH), reporting per Minecraft generation, not collapsed into one floor
- [x] doctor fails naming the specific row when a toolchain is absent or below its floor
  (`PLATFORM-REQ-001`)
- [x] doctor checks the Gradle wrapper pin is >= `9.5.1`
- [x] doctor checks `docs/spec/` against the vault copy (unchanged from the sibling shape)
- [x] doctor checks `docs/map.md`/`docs/map/` staleness via `tools/map.py --check`
- [x] `kontor doctor` (the repo-conformance half) and `tools/doctor.py` (the toolchain half) both
  clean, matching `just doctor: doctor-repo doctor-toolchain`'s split

## Constraints and prior findings

Blocked by GV-1 (needs the bootstrapped `tools/doctor.py` to extend). This ticket does not require
GV-2's real Stonecutter nodes to exist — the toolchain floors are about locally-installed JDKs and
Gradle, not about the build graph itself, so it can land independently once the platform matrix is
final.
