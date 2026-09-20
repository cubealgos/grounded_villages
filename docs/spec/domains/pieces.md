---
title: "grounded_villages spec — PIECE: per-piece rejection, streets included"
type: "spec"
category: "grounded_villages"
---

# `PIECE` — per-piece rejection

## 1. Purpose

Deciding, piece by piece as vanilla's own jigsaw placement reaches each one, whether that specific
building or street may actually be placed — a piece whose footprint has water in it, or whose ground
height deviates too far from the village's own start height, is rejected. This is the fix for
vanilla's "more than half its buildings and paths on water" complaint at the individual-piece level;
whether the whole site was worth building on at all is `domains/site.md`'s concern, not this file's.

## 2. Dimensions

| Dimension | Answer |
|---|---|
| **Actors** | The mod (`ACTORS-006`) evaluates and accepts/rejects each piece as vanilla's own jigsaw assembly proposes it; the operator (`ACTORS-002`) tunes the height-deviation tolerance and the enable toggle; a datapack author (`ACTORS-003`) who adds new jigsaw pieces gets identical treatment with no code of their own. |
| **Over time** | Vanilla's jigsaw assembly proposes a piece at a specific position → this mod checks its footprint for water and its ground height against the village's start height → accept (piece is placed, assembly continues) or reject (piece is not placed). Each piece is evaluated exactly once, at proposal time; nothing is re-evaluated after placement. |
| **Multiplicity** | One piece evaluated at a time, arbitrarily many per village depending on the tier's budget (`domains/tiers.md`). At **zero or few** pieces surviving rejection: the shrink/move/vanilla ladder applies (`decisions/DEC-010-shrink-move-vanilla.md`, `PIECE-FAIL-001`). At the top end: a `city` tier's larger budget means more pieces evaluated, bounded by the performance cap (`domains/tiers.md` `TIER-REQ-005`), not by this domain. |
| **Unwanted** | A piece rejected for water; a piece rejected for height deviation; a rejected piece whose jigsaw connection point leaves a neighbouring piece with a dangling, unconnected stub (`PIECE-FAIL-002`); a village that loses enough pieces to rejection to fall below the hamlet minimum — handled by shrink, then move, then vanilla (`DEC-010`), never left as a visibly broken result. |
| **Not-you** | A datapack author who defines a new jigsaw piece pool with no awareness this mod exists still gets every piece in that pool checked the same way — the rule is transparent to piece authorship (`ACTORS-003`). |

## 3. Enumerations

### Rejection criteria

**Confirmed detection mechanism** (`village-jigsaw-placement-1-20-1-to-26-2.md` §C): sample the
candidate piece's bounding-box corners and centre (5 points minimum, more for a larger footprint) via
`ChunkGenerator.getBaseColumn`/`getFirstFreeHeight`, at both `Heightmap.Types.WORLD_SURFACE_WG` (a
direct block-state read — the confirmed way to detect water itself, since `WORLD_SURFACE_WG`'s
`NOT_AIR` predicate returns a lake's water surface rather than its bed) and `OCEAN_FLOOR_WG` (true
ground height, unaffected by water). Both mixin injection points already have the `ChunkGenerator`
and `RandomState` this needs in scope (`04-architecture.md` `ARCH-DEC-001`), so no new context needs
threading in — confirming this mod's original site-level design generalises cleanly to per-piece
checks too.

| Criterion | Applies to | Rule | Configurable? |
|---|---|---|---|
| Water in footprint | Every piece, including streets | Reject if any sampled point's block state is water | The check can be turned off entirely (`piece.enabled`); the rule itself (any water = reject) is not a tunable fraction — Kevin's ruling states it as a flat condition, not a threshold, unlike site-level water fraction (`domains/site.md`) |
| Height deviation | Every piece, including streets | Reject if the piece's own ground height (via `OCEAN_FLOOR_WG`) deviates from the village's start height (`03-glossary.md`) by more than `piece.max_height_deviation` blocks | Yes — `piece.max_height_deviation` is a config value, proposed default `6` blocks (`domains/config.md`, **proposed by Claude, 2026-09-20, Kevin to confirm at the first ticket**) |

Vanilla's own two piece kinds behave differently going into this check, confirmed by direct bytecode
read: **`TERRAIN_MATCHING` pieces** (every street element in `village/plains/streets.json`) already
get their own independent terrain query as vanilla places them — this mod's check sits at exactly
that same query, replacing/augmenting vanilla's own `WORLD_SURFACE_WG`-only lookup with the water and
deviation checks above. **`RIGID` pieces** (every house element in `village/plains/houses.json`,
36/36) inherit their height purely from fixed offset arithmetic relative to their parent piece, with
no terrain query of their own — this mod's check for a rigid piece therefore evaluates the position
vanilla has already computed for it (parent height plus offset), not a fresh terrain sample.

### Streets are pieces

No separate rule exists for streets: `PIECE-REQ-004` states plainly that a street segment is
evaluated by the identical two criteria above, with no exemption — the direct fix for vanilla's
"paths ... on water" half of the original complaint (`00-context.md`), and confirmed as the exact
mechanism producing that complaint in the first place: every street element already uses
`TERRAIN_MATCHING`, each independently querying `WORLD_SURFACE_WG` (which returns a lake's surface,
not its bed) with **no cross-piece smoothing or agreement check against the start piece or any
neighbour** — this is the direct code path for a street that snakes into a lake.

### Actor per step

Identical single-actor shape to `domains/site.md` — the mod evaluates every piece; the operator and
datapack author act only outside the evaluation loop (config before generation runs, piece-pool
authorship independent of this mod's existence).

### Drawings

None. The per-piece decision is a single accept/reject branch, fully captured by the criteria table
above; `02-journeys.md` `UC-003`'s worked sequence shows the shape in context.

## 4. Use cases

`UC-002`, `UC-003` in `02-journeys.md`.

## 5. Requirements

| ID | Requirement | Priority | From |
|---|---|---|---|
| `PIECE-REQ-001` | The system shall evaluate every proposed piece's footprint, including streets, for the presence of water before that piece is placed. | Must | `UC-003` |
| `PIECE-REQ-002` | **If** a piece's footprint contains any water, **then** the system shall reject that piece. | Must | `UC-003` |
| `PIECE-REQ-003` | **If** a piece's ground height deviates from the village's start height by more than `piece.max_height_deviation` blocks, **then** the system shall reject that piece. | Must | `UC-002` |
| `PIECE-REQ-004` | A street segment shall be evaluated by `PIECE-REQ-001`–`003` identically to a building piece, with no exemption. | Must | `00-context.md`, Dimensions: Unwanted |
| `PIECE-REQ-005` | **Where** `piece.enabled` is `false`, the system shall skip per-piece rejection entirely and behave exactly as vanilla does. | Could | `ARCH-DEC-004` |
| `PIECE-REQ-006` | **When** per-piece rejection leaves a village's surviving non-street piece count at or above `tier.hamlet_minimum_pieces`, the system shall keep the village as generated, relabelled `hamlet` regardless of its originally rolled tier. | Must | `decisions/DEC-010-shrink-move-vanilla.md` step 1 ("shrink") |
| `PIECE-REQ-007` | **If** the surviving piece count falls below `tier.hamlet_minimum_pieces`, **then** the system shall retry the whole village at a shifted site, up to `site.search_attempts` times, before falling back to vanilla's own unchecked placement. | Must | `DEC-010` steps 2–3 ("move", "vanilla") |

## 6. Failure modes

| ID | Failure | Required response |
|---|---|---|
| `PIECE-FAIL-001` | A village loses enough pieces to rejection that little of its rolled tier's budget survives | **Resolved: shrink, then move, then vanilla** (`decisions/DEC-010-shrink-move-vanilla.md`). If the surviving pieces still clear `tier.hamlet_minimum_pieces`, keep the village, relabelled `hamlet` (`PIECE-REQ-006`). Otherwise retry at a shifted site up to `site.search_attempts` times, then fall back to vanilla's own unchecked placement (`PIECE-REQ-007`) — no world ever loses a village outright. |
| `PIECE-FAIL-002` | A rejected piece leaves a neighbouring piece's jigsaw connector with nothing to attach to | **Partially resolved, not fully confirmed.** This mod's rejection sits inside `JigsawPlacement$Placer.tryPlacingChildren`, the same method vanilla's own collision/bounding-box rejection already uses to decide a candidate piece doesn't fit — a strong structural reason to expect a rejected piece follows vanilla's own existing "try the next pool entry, or leave that branch unconnected" path rather than a new failure shape this mod invents. **Not independently bytecode-confirmed**: `village-jigsaw-placement-1-20-1-to-26-2.md` traced the height/water query bytecode but did not trace what happens downstream of a `false` return from the piece-fits check. |
| `PIECE-FAIL-003` | Per-piece evaluation itself costs meaningful generation-thread time on a large (`town`/`city`) village | Bounded indirectly by the tier performance cap (`domains/tiers.md` `TIER-REQ-005`), which limits total piece count regardless of how much per-piece checking costs. |

## 7. Open questions

| Question | Blocks | Decided by |
|---|---|---|
| Whether water-in-footprint should ever become a configurable fraction rather than a flat any-water rule | `PIECE-REQ-002` | first ticket, if the flat rule proves too aggressive in practice |
| Whether a rejected piece's dangling jigsaw connector needs explicit handling, or vanilla's existing piece-rejection path already covers it (structurally likely, not bytecode-confirmed downstream of the fits-check) — kept as a verification item, not a design question | `PIECE-FAIL-002` | first ticket — a targeted trace of `tryPlacingChildren`'s failure path, or an empirical game-test check |

## 8. Decisions

- `PIECE-FAIL-001`'s minimum-viable-size question is `decisions/DEC-010-shrink-move-vanilla.md`:
  shrink to `hamlet` if the surviving pieces clear the minimum, else move to a shifted site, else
  fall back to vanilla — no world loses a village.
