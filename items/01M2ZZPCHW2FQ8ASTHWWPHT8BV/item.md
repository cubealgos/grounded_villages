---
schema_version: 1
id: 01M2ZZPCHW2FQ8ASTHWWPHT8BV
key: GV-11
type: test
title: "Game tests per loader: hook fires correctly against pre-built scenarios"
created_by: kevin
created_at: 2026-09-20T18:01:45Z
---

## Scope

Per-loader `GameTest` coverage confirming the hook itself fires correctly against known, pre-built
scenarios — not seed-driven generation (that is GV-10's job): a pre-placed piece with water in its
footprint gets rejected; a rejected piece's neighbour does not end up with a dangling connector;
per-piece rejection coexists with vanilla placement without mutating a piece it accepts. Also
`TEST-REQ-002`: one deliberate-break proof for the shared-source purity check.

## Approach

Vanilla `GameTest` on the loaders this ticket's milestone actually covers (Fabric via
`fabric-gametest-api-v1`/`FabricGameTest`, NeoForge via `@GameTest`/`@GameTestHolder`/
`RegisterGameTestsEvent`) — Forge 1.20.1's own game-test coverage lands with GV-15, once that node
exists.

## Acceptance criteria

- [x] `TEST-REQ-004` satisfied: a game test proves the hook coexists with vanilla and never mutates
  a piece it accepts
- [x] at least one game test per Wave 1 loader family (Fabric, NeoForge) covering: water-footprint
  rejection, dangling-connector absence, accept-path non-mutation
- [x] `TEST-REQ-002`: one deliberate-break proof exists for the shared-source purity check (a
  Minecraft import deliberately added to a pure-logic package fails the check)
- [x] these game tests are wired into GV-3's CI pipeline, not left as a local-only recipe

## Constraints and prior findings

Blocked by GV-5, GV-6, GV-7 (needs the hook and both rejection domains to have something to test).
`GameTest` is confirmed not a fit for N-seed statistics (`operations/testing.md`) — this ticket
never uses it for that; GV-10 owns the statistical measure.
