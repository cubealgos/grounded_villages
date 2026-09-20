---
schema_version: 1
id: 01M2ZZP6RJP688RPN4PBBXK3GH
key: GV-5
type: feat
title: "Mixin hook family: JigsawPlacement.addPieces and Placer.tryPlacingChildren, gated on #minecraft:village"
created_by: kevin
created_at: 2026-09-20T18:01:39Z
---

## Scope

The mixin hook family: `JigsawPlacement.addPieces` (start-piece placement) and
`JigsawPlacement$Placer.tryPlacingChildren` (every child piece), gated on the structure's registry
holder testing against `#minecraft:village`
(`BuiltInRegistries.STRUCTURE.getTag(...)`/`Holder.is(tag)`). One mixin body variant for 1.20.1 (no
`PoolAliasLookup`/`LiquidSettings` params) and a second shared by 1.21.1 and 26.2 (both add those
params identically).

## Approach

Per `04-architecture.md` `ARCH-DEC-001` and `village-jigsaw-placement-1-20-1-to-26-2.md` §B/§C:
mixin, not a custom `StructureType` — reimplementing vanilla's jigsaw graph-assembly algorithm was
rejected as disproportionate for a mod that only wants to change villages. Both injection points
already have `ChunkGenerator`/`RandomState` in scope, so `domains/site.md` and `domains/pieces.md`'s
sampling reuses that context rather than threading in anything new.

## Acceptance criteria

- [ ] mixin compiles and fires on all six nodes (GV-15/GV-17 extend this to the Forge and Wave 3
  legs once those exist; this ticket covers the Wave 1 four nodes plus proves the shape for the
  rest)
- [ ] the `#minecraft:village` tag gate is confirmed live: a non-village jigsaw structure (a
  pillager outpost, say) is provably untouched
- [ ] `PIECE-FAIL-002` (a rejected piece's dangling jigsaw connector) is verified, not just assumed
  — either a targeted trace of `tryPlacingChildren`'s failure path, or an empirical game-test check
  confirming vanilla's own "try the next pool entry, or leave the branch unconnected" path holds
- [ ] `DEC-005`/`beard_thin` verification: confirm `TerrainAdjustment.BEARD_THIN` is left running
  exactly as vanilla ships it, and this mod adds no terrain edit of its own around a placed piece
- [ ] the hook reads `ChunkGenerator`/`RandomState` already in scope at the injection point — no
  new context is threaded in

## Constraints and prior findings

`ARCH-DEC-001`: no loader on any target version exposes a structure- or piece-placement event, so
a mixin is required, not merely preferred, on all three loaders. `contracts/platform-matrix.md`'s
version-delta table is the authoritative list of what differs between 1.20.1 and 1.21.1+. Blocked
by GV-2 (needs real Stonecutter nodes to attach a mixin to). This ticket is also where `README.md`
verification rows 1 (hook targets, confirmed), 2 (dangling connector, partially confirmed), and 18
(`beard_thin`, design-closed but not bytecode-traced) get their first-ticket verification work.
