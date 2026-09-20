---
schema_version: 1
id: 01M2ZZP6THGT2N5Z8QCWZJZW0G
key: GV-6
type: feat
title: "Site scorer: height spread, water fraction, bounded search-and-relocate"
created_by: kevin
created_at: 2026-09-20T18:01:39Z
---

## Scope

`SiteSelector`: pure logic, zero Minecraft imports. Height spread (the 90th-minus-10th-percentile
of sampled ground heights at `OCEAN_FLOOR_WG` across the candidate radius) and water fraction (the
share of sampled columns that are water, via `WORLD_SURFACE_WG`/block-state comparison) sampled
across a candidate village's whole radius before any piece exists; a bounded search
(`site.search_radius`/`search_step`/`search_attempts`) for a qualifying alternative when the first
candidate fails; fallback to vanilla's own unchecked placement when the search is exhausted
(`SITE-REQ-004`); the `SITE-REQ-006` move-retry entry point `domains/pieces.md`'s shrink/move/
vanilla ladder (`DEC-010`) calls into when too few pieces survive rejection.

## Approach

Built independently of "Improved Village Placement" per `decisions/DEC-009-prior-art.md` — no code
reuse, even though that mod's 64-block-radius `WORLD_SURFACE_WG`-variance grid is a real reference
point. `domains/site.md` §3's sampling mechanism and thresholds are the contract; the grid
resolution and exact search algorithm are internal (`contracts/public-surface.md`).

## Acceptance criteria

- [ ] `SITE-REQ-001` through `SITE-REQ-006` implemented
- [ ] unit tests: accept/reject given synthetic heightmap/water-fraction inputs, covering both the
  height-spread and water-fraction thresholds independently
- [ ] the bounded search is genuinely bounded — `SITE-FAIL-002`: an unreasonable
  `search_radius`/`search_attempts` config value is clamped with a logged warning, never allowed to
  make generation unbounded
- [ ] `site.enabled: false` skips site selection entirely and reproduces vanilla behaviour
  (`SITE-REQ-005`)
- [ ] the proposed defaults (`max_height_spread: 12`, `max_water_fraction: 0.05`,
  `search_radius: 48`, `search_step: 16`, `search_attempts: 8`) are confirmed or revised at this
  ticket, per `README.md` "Verifications" row 17's flag that every numeric default is
  Kevin's-to-confirm, not measured yet — **to confirm**

## Constraints and prior findings

Blocked by GV-5 (needs the hook's `ChunkGenerator`/`RandomState` context) and GV-9 (reads config
keys `site.*` from `ConfigModel`). `SITE-FAIL-003`: sampling cost is resolved as bounded — the same
per-call cost vanilla already pays once per piece, 5-9 points per footprint is proportionate; a
whole-site grid is larger but bounded by the same config-driven sample count.
