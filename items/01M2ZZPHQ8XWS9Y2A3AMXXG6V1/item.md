---
schema_version: 1
id: 01M2ZZPHQ8XWS9Y2A3AMXXG6V1
key: GV-14
type: epic
title: "Wave 2: Forge 1.20.1"
created_by: kevin
created_at: 2026-09-20T18:01:51Z
---

## Scope

Epic. Land the Forge 1.20.1 leg end to end: build (`legacyforge` addon, SRG refmap), the shared
mixin/site/piece/tier/config logic proven on this leg, and game-test coverage — tracked via its
child ticket(s), the first of which is GV-15.

## Approach

Not designed further here; the concrete work is scoped at GV-15 and any further children added as
Wave 2 proceeds, per this fleet's epic convention (grouping only, via `child_of`/`parent_of`).

## Acceptance criteria

- [x] every child ticket of this epic reaches `done`

## Constraints and prior findings

`decisions/DEC-004-versions-and-toolchain.md`: Forge 1.20.1 ships second, after Wave 1's four
Fabric/NeoForge nodes are proven — "the oldest and most widely-installed version in this ladder."
`contracts/platform-matrix.md` Wave 2 row.
