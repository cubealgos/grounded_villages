---
title: "grounded_villages spec — SITE: whole-village site selection before any piece exists"
type: "spec"
category: "grounded_villages"
---

# `SITE` — site selection

## 1. Purpose

Deciding, before any piece is placed, whether a candidate village location is good ground to build
on at all — and if not, searching nearby for one that is. This is the fix for vanilla's "scattered
across immense heights" and "half the village on water" complaints at the whole-village level; the
per-piece version of the same problem is `domains/pieces.md`'s concern, not this file's.

## 2. Dimensions

| Dimension | Answer |
|---|---|
| **Actors** | The mod (`ACTORS-006`) samples and decides, entirely server-side; the server operator (`ACTORS-002`) tunes the thresholds and search bounds in config; another worldgen mod (`ACTORS-005`) supplies the terrain being sampled, read-only. |
| **Over time** | A structure start candidate is proposed by vanilla's own structure placement → this mod samples height spread and water fraction across the candidate's whole village radius, before any piece exists → accept, or reject and search nearby within a bound → accept the search result, or exhaust the bound and fall back to vanilla's own unchecked placement. Nothing here runs again once a site is accepted — a site is a one-shot decision per village instance. |
| **Multiplicity** | Exactly one site accepted per village instance. At **zero** qualifying candidates within the search bound: fallback to vanilla behaviour, not "no village" (`decisions/DEC-005-placement-only.md`). At an unreasonable number of search attempts: the search is itself bounded by config (`SITE-REQ-003`), so it cannot run unbounded on a server with unusually difficult terrain. |
| **Unwanted** | A candidate whose height spread or water fraction exceeds threshold (rejected, search triggered); a search that exhausts its bound without finding a qualifying site (`SITE-FAIL-001`); terrain so uniformly bad (another worldgen mod's extreme output) that almost every village falls back to vanilla behaviour — an accepted, visible consequence of that terrain mod's own choices, not a bug in this mod. |
| **Not-you** | A modpack author running an extreme terrain mod, who needs wider thresholds than the shipped defaults to see this mod behave any differently from vanilla at all (`domains/config.md`) — the mod degrades to "vanilla behaviour, mostly" rather than failing outright in that case. |

## 3. Enumerations

### Sampling

**Confirmed mechanism** (`village-jigsaw-placement-1-20-1-to-26-2.md` §A/§C): the injection point is
`JigsawPlacement.addPieces`, the start-piece placement call — the same call already in scope at that
mixin site (`04-architecture.md` `ARCH-DEC-001`) has direct access to the `ChunkGenerator` and
`RandomState` this domain's sampling needs, so no new context needs threading in. Sampling uses
`ChunkGenerator`'s own pre-placement column queries — identical signatures on every targeted version:

| Metric | What it measures | Computed from |
|---|---|---|
| Height spread | How much sampled ground height varies across the candidate radius | `getBaseHeight`/`getBaseColumn` at `Heightmap.Types.OCEAN_FLOOR_WG` (true ground, sees through water) across the sampling grid |
| Water fraction | What fraction of the sampled area is water | `getBaseColumn`'s returned block states (direct water detection) compared against `getBaseHeight` at `WORLD_SURFACE_WG` (which treats water as "surface," so a mismatch between the two at a point identifies water there) across the same grid |

Precedent for the grid shape: "Improved Village Placement" (MIT, same version/loader matrix,
credited as prior art, `decisions/DEC-009-prior-art.md`) already samples a 64-block-radius grid
against `WORLD_SURFACE_WG` variance for its own whole-site rejection — a real reference point this
mod's own scoring is nonetheless built independently from (`DEC-009`: no code reuse). This domain's
own metric is height *spread*, not raw variance: the 90th-minus-10th-percentile of sampled ground
heights at `OCEAN_FLOOR_WG` (true ground, not water-topped) across the candidate radius.

### Thresholds and search (config keys, `domains/config.md`) — proposed defaults, tune at the harness sweep

**Proposed by Claude, 2026-09-20, Kevin to confirm at the first ticket.** All from
`domains/config.md`'s own schema, restated here for this domain's reading:

| Config key | Governs | Default (proposed) |
|---|---|---|
| `site.enabled` | Whether site selection runs at all (`ARCH-DEC-004`) | `true` |
| `site.max_height_spread` | 90th-minus-10th-percentile spread of sampled ground heights (`OCEAN_FLOOR_WG`) a candidate must be within to qualify | `12` blocks |
| `site.max_water_fraction` | Share of sampled columns that may be water before a candidate is rejected | `0.05` (5%) |
| `site.search_radius` | How far from the vanilla start the bounded search may look, staying inside the start chunk's own placement cell | `48` blocks |
| `site.search_step` | Spacing between candidate offsets the search samples | `48` blocks (tuned, GV-6 — was `16`; step == radius puts every offset at the full safe bound, in the 4 cardinal directions only, `SITE-FAIL-002`'s geometry) |
| `site.search_attempts` | How many alternate candidate offsets the search may sample before giving up | `4` (tuned, GV-6 — was `8`; matches what `search_step == search_radius` actually makes reachable) |

### Actor per step

Every step in this domain's use cases is taken by the mod itself; the operator and another worldgen
mod act only outside the sampling/decision loop (config before it runs, terrain generation before
this mod reads it) — see `01-actors.md` `FINDING-2` for why that is a deliberate, not accidental,
one-actor column.

### Drawings

None. A site's lifecycle is short enough (proposed → sampled → accept or search → accept or
fallback) that the transition table above is complete on its own; a human reader gets more from
`02-journeys.md` `UC-001`/`UC-002`'s worked sequences than a separate diagram would add.

## 4. Use cases

`UC-001`, `UC-002`, `UC-007` in `02-journeys.md`.

## 5. Requirements

| ID | Requirement | Priority | From |
|---|---|---|---|
| `SITE-REQ-001` | The system shall sample height spread and water fraction across a candidate village's whole radius before any piece of that village is placed. | Must | `UC-001` |
| `SITE-REQ-002` | **If** a candidate's height spread exceeds `site.max_height_spread`, or its water fraction exceeds `site.max_water_fraction`, **then** the system shall reject that candidate. | Must | `UC-002` |
| `SITE-REQ-003` | **When** a candidate is rejected, the system shall search for a qualifying alternative within `site.search_radius` and `site.search_attempts`, both configurable and each bounded to a finite value. | Must | `UC-002` |
| `SITE-REQ-004` | **If** the bounded search exhausts its attempts without finding a qualifying candidate, **then** the system shall fall back to vanilla's own unchecked placement rather than generating no village. | Must | `SITE-FAIL-001` |
| `SITE-REQ-005` | **Where** `site.enabled` is `false`, the system shall skip site selection entirely and behave exactly as vanilla does. | Could | `ARCH-DEC-004` |
| `SITE-REQ-006` | **When** a village's surviving pieces fall below `tier.hamlet_minimum_pieces` after per-piece rejection, the system shall re-run this domain's own bounded search (`SITE-REQ-003`'s mechanism) to find a shifted site for a full retry, before falling back to vanilla placement. | Must | `decisions/DEC-010-shrink-move-vanilla.md` step 2 ("move"), `domains/pieces.md` `PIECE-REQ-007` |

## 6. Failure modes

| ID | Failure | Required response |
|---|---|---|
| `SITE-FAIL-001` | No candidate within the bounded search qualifies | Falls back to vanilla's own unchecked placement at (or near) the original candidate; the village still generates (`SITE-REQ-004`). |
| `SITE-FAIL-002` | `site.search_radius` or `site.search_attempts` is configured to an unreasonably large value | Clamped to a sane maximum with a logged warning, so a misconfigured server cannot turn site selection into an unbounded generation-thread cost. |
| `SITE-FAIL-003` | Sampling itself proves expensive on the world-generation thread at scale | **Resolved, bounded**: each column query walks a noise column top-down until its stop predicate matches — one full noise-aquifer sample per distinct `(x,z)`, not free, but the same per-call cost vanilla itself already pays once per jigsaw piece placed. Querying 5–9 points across a candidate footprint (the per-piece precedent, `domains/pieces.md`) is proportionate; a whole-site grid is larger but bounded by the same config-driven sample count (`village-jigsaw-placement-1-20-1-to-26-2.md` §A/C). |

## 7. Open questions

| Question | Blocks | Decided by |
|---|---|---|
| Whether §3's proposed defaults hold up once measured by the headless harness sweep | `SITE-REQ-001`–`003` | **Proposed by Claude, 2026-09-20; `search_step`/`search_attempts` re-tuned by Claude, 2026-09-20 (GV-6) to `48`-block radius in `48`-block steps / `4` attempts (4 cardinal offsets at the full safe distance), from the same 10-seed sweep — `docs/baseline/README.md` has the before/after table and the measured trade-off.** `max_height_spread`/`max_water_fraction` (`12` blocks / `5%`) left as originally proposed, per GV-6's own ticket scope — still Kevin's to confirm at this ticket, config-overridable regardless of outcome |
| Whether to read "Improved Village Placement"'s own source for design ideas (`decisions/DEC-009-prior-art.md` already rules out code reuse; this is only about reading, not reusing) | `SITE-REQ-001` | first ticket, low priority |
