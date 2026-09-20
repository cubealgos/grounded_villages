---
schema_version: 1
id: 01M2ZZP6WE2C0QJN9MCEGJ03MT
key: GV-7
type: feat
title: "Per-piece rejection: water-in-footprint and height deviation, shrink/move/vanilla ladder"
created_by: kevin
created_at: 2026-09-20T18:01:39Z
---

## Scope

`PieceGate`: pure logic — water-in-footprint (a flat any-water rejection, not a tunable fraction,
`PIECE-REQ-002`) and height-deviation-from-start-height (`piece.max_height_deviation`, proposed
default 6 blocks, `PIECE-REQ-003`) for every piece, streets included with no exemption
(`PIECE-REQ-004`). The shrink/move/vanilla ladder (`decisions/DEC-010-shrink-move-vanilla.md`,
`PIECE-REQ-006`/`007`): shrink to `hamlet` if surviving non-street pieces clear
`tier.hamlet_minimum_pieces`, else call GV-6's bounded search for a shifted site, else fall back to
vanilla's own unchecked placement.

## Approach

Both criteria sample the candidate piece's bounding-box corners and centre (5 points minimum) via
`getBaseColumn`/`getFirstFreeHeight` at both `WORLD_SURFACE_WG` (water) and `OCEAN_FLOOR_WG`
(height) — the same mechanism `domains/site.md` uses at village-instance scale, confirmed to
generalise cleanly to per-piece checks (`04-architecture.md`).

## Acceptance criteria

- [ ] `PIECE-REQ-001` through `PIECE-REQ-007` implemented
- [ ] streets and buildings use byte-identical criteria and code path — no special case for
  `TERRAIN_MATCHING` vs `RIGID` beyond how each already computes its own candidate height
- [ ] shrink: surviving pieces >= `tier.hamlet_minimum_pieces` (proposed default 4) keeps the
  village, relabelled `hamlet` regardless of its originally rolled tier
- [ ] move: falls through to GV-6's bounded search, up to `site.search_attempts` shifted
  candidates, before the vanilla fallback
- [ ] vanilla fallback: confirmed no world ever ends up with a missing village
- [ ] unit tests: accept/reject given synthetic footprint/height inputs; a shrink-triggering case;
  a move-triggering case
- [ ] `piece.enabled: false` skips per-piece rejection entirely (`PIECE-REQ-005`)

## Constraints and prior findings

Blocked by GV-6 (the move step reuses its bounded search) and GV-8 (reads
`tier.hamlet_minimum_pieces`). `DEC-010`'s order is deliberate: shrink before move, move before
vanilla — re-ordering later is a logic change in one place, not a redesign. `PIECE-FAIL-002`'s
verification is GV-5's job, not repeated here.
