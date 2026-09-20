---
schema_version: 1
id: 01M2ZZPQXR56A05DABJY1NQ1R7
key: GV-20
type: docs
title: "Modrinth listing per target: six version entries, game-versions arrays within one Java generation"
created_by: kevin
created_at: 2026-09-20T18:01:57Z
---

## Scope

Six Modrinth version entries (or six body variants, whichever GV-19's tool shape ends up needing),
one per shipped Wave 1-2 combination, each with the correct `Game versions` array and loader tag.

## Approach

One listing/version entry per shipped combination, built from GV-13's positioning copy and
GV-19's per-target publish tool, checked against `contracts/platform-matrix.md`'s CI matrix so
nothing ships that CI has not actually built green.

## Acceptance criteria

- [x] each of the six Wave 1-2 combinations has its own listing/version entry, built from GV-13's
  positioning copy
- [x] `REL-REQ-004`: the first release notes of each wave state plainly which combinations are new
  in that wave and which remain `planned`
- [x] `REL-REQ-002`: each version entry's release notes list the exact Minecraft version, loader,
  and loader version tested for that specific jar
- [x] no `Game versions` array spans a Java-version boundary or a loader (mirrors GV-19's own check)

## Constraints and prior findings

Blocked by GV-13 (the positioning copy this listing is built from). `operations/release.md`'s
Modrinth publish matrix: "Waves 1-2's six confirmed nodes need six separate invocations for a full
release at that point in the ladder."
