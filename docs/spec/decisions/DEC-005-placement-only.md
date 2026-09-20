---
title: "grounded_villages DEC-005 — Placement only: site selection and per-piece rejection, no terrain edits"
type: "spec"
category: "grounded_villages"
---

# `DEC-005` — Placement only: site selection and per-piece rejection, no terrain edits

**Status:** decided by Kevin, 2026-09-20; the "what counts as vanilla's own foundation fill" question
closed by Kevin, 2026-09-20.

The whole fix is **where** vanilla's own village pieces are allowed to land, never what the terrain
around them looks like afterward. Two mechanisms, both described in full in `domains/site.md` and
`domains/pieces.md`:

- **Site selection**, before any piece exists: height spread and water fraction sampled across the
  village's whole radius, with a bounded search for a better spot nearby when the first candidate
  fails, and a fallback to vanilla's own unchecked behaviour when the bounded search is exhausted —
  no village is silently deleted for want of a perfect site.
- **Per-piece rejection**, as pieces are placed: a piece whose footprint contains water, or whose
  ground height deviates from the village's own start height by more than a configurable number of
  blocks, is rejected. Streets are treated exactly like buildings — no special case for the piece
  type that most directly causes the "half the village is on the path over water" complaint this mod
  exists to fix.

**No terrain edits beyond vanilla's own foundation fill.** This mod never flattens, fills, or
otherwise sculpts terrain of its own — it only decides which of vanilla's own candidate sites and
pieces are allowed to proceed, using whatever ground vanilla generation already put there. The one
exception is not an exception at all: whatever vanilla's own mechanism already does around a piece it
places runs unchanged, because it is vanilla's, not this mod's.

**What that vanilla mechanism actually is, closed**: `TerrainAdjustment` — vanilla's per-piece enum
(`NONE, BURY, BEARD_THIN, BEARD_BOX, ENCAPSULATE`); `village_plains.json` uses `"beard_thin"`
(`village-jigsaw-placement-1-20-1-to-26-2.md` §A). **Kevin's ruling: `beard_thin` *is* vanilla's own
foundation fill, and it stays exactly as vanilla ships it** — "placement only" means this mod adds no
terrain edits of its own; it does not mean auditing or suppressing what vanilla's own mechanism
already does around a piece it places, whatever that turns out to be in detail. This closes the
question as a design matter.

**What is not closed, and does not need to be**: the research did not trace `beard_thin`'s exact
carving behaviour into bytecode. Since this mod does not touch it either way, that trace is not a
design blocker — it is a **verification item for the first ticket** (`README.md` "Verifications"),
useful for confirming the mechanism genuinely behaves the way "vanilla's own foundation fill" implies
before the first playtest, not for deciding anything this spec still needs to rule on.

**Compatible with other worldgen mods, no scars.** Because nothing here edits terrain, a village
generated on top of Terralith, or any other terrain-generation mod, degrades to exactly the same
site-selection and rejection logic running against whatever terrain that mod produced — no special
integration is needed, and no dedicated terrain-smoothing pass exists to leave a footprint of its
own if it fails partway (`domains/site.md` `SITE-FAIL` table, `02-journeys.md` UC covering a
modpack with Terralith).

Alternative considered: a terrain-shaping pass — flattening a pad under a village or filling a small
lake near one — the way some existing "better villages"-style mods do. Rejected by Kevin outright:
placement-only keeps the mod's failure mode legible (a rejected piece just doesn't generate, rather
than a hole appearing where a piece would have gone) and keeps it trivially compatible with any other
worldgen mod, since it never claims ownership of terrain another mod might also want to shape. Cost
if wrong: a terrain-shaping pass is a genuinely separate, larger feature — not an incremental
addition to this design — so getting this wrong would mean a second mod or a major version, not a
patch.
