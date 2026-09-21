---
schema_version: 1
id: 01M2ZZPQSRXS1AZHWFXGFTQG4K
key: GV-18
type: epic
title: "Release: six-jar Modrinth publish and changelog"
created_by: kevin
created_at: 2026-09-20T18:01:57Z
---

## Scope

Epic. Ship 1.0 across every wave that has landed by the time this epic is worked — the six-jar
Modrinth publish, changelog, and release notes — tracked via its child tickets GV-19 (publish-tool
extension), GV-20 (listing per target), and GV-21 (the release itself).

## Approach

Not designed further here; the concrete work is scoped at GV-19, GV-20, and GV-21.

## Acceptance criteria

- [x] every child ticket of this epic reaches `done`

## Constraints and prior findings

`operations/release.md`: `REL-DEC-001` — version scheme `<mod>-<mc>-<loader>`, Kevin's own scheme,
encoding both the Minecraft version and the loader. One `cmd_version` call uploads exactly one jar,
so a full release needs one invocation per shipped combination (six, for Waves 1-2).
