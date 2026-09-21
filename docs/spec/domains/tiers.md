---
title: "grounded_villages spec — TIER: hamlet, village, town, city, rolled from the world seed"
type: "spec"
category: "grounded_villages"
---

# `TIER` — size tiers

## 1. Purpose

Deciding how large a given village instance gets to be — one of four named tiers, rolled once per
village, deterministically from the world seed. This is the fix for "I want ... more size variety in
villages" (`00-context.md`); which sites and pieces are acceptable at whatever size gets rolled is
`domains/site.md` and `domains/pieces.md`'s concern, not this file's.

## 2. Dimensions

| Dimension | Answer |
|---|---|
| **Actors** | The mod (`ACTORS-006`) rolls the tier and applies its budget, entirely server-side; the operator (`ACTORS-002`) configures weights, depths, distances, and the performance cap. |
| **Over time** | A village start point is chosen by vanilla's own structure placement → before pieces generate, this mod rolls a tier deterministically from the world seed and the village's position → the tier's `maxDepth`/`max_distance_from_center` governs how many pieces `domains/site.md`/`domains/pieces.md`'s checks are allowed to accept → generation proceeds up to that budget, the performance cap, or a shrink/move/vanilla fallback if too few pieces survive (`decisions/DEC-010-shrink-move-vanilla.md`), whichever binds first. The roll happens exactly once per village instance and is never re-rolled. |
| **Multiplicity** | Exactly one tier per village instance — every village gets one, there is no "no tier" state. At the top end: `city` is deliberately rare by weight, capped at the 128-block hard structure radius (`decisions/DEC-006-size-tiers.md`), and additionally bounded by the performance cap. |
| **Unwanted** | A tier roll whose budget the performance cap then clamps down (`TIER-FAIL-001`); a weight configuration that sums to zero across every tier (`TIER-FAIL-002`); a `town`/`city` village generating close enough to a neighbour to sit near it — accepted, not designed around (§ "Spacing" below, closed). |
| **Not-you** | A modpack author who wants zero chance of ever seeing a `city` sets that tier's weight to zero in config and gets exactly that, without needing to understand how the roll itself works. |

## 3. Enumerations

### The four tiers

| Tier | Weight | Jigsaw depth | Max distance | Shape |
|---|---|---|---|---|
| `hamlet` | 30 | 3 | 80 blocks | A few houses and one workstation cluster — a genuinely small settlement, not a truncated village. Minimum 4 non-street pieces to remain labelled `hamlet` rather than falling further (`TIER-REQ-007`). |
| `village` | 45 | 6 (vanilla's own default) | 96 blocks | Vanilla's own current scale, unchanged in spirit — the baseline, most common case. |
| `town` | 20 | 8 | 128 blocks (the hard cap) | Roughly twice a vanilla village's piece budget, single centre. |
| `city` | 5 | 9 | 128 blocks (the hard cap) | **At 1.0: one centre**, the deepest budget and widest reach this mod allows — resolved below, `TIER-REQ-004`. |

**Every number above is confirmed by Kevin, 2026-09-21 (GV-27)** — the full config schema lives in
`domains/config.md`; this table restates it for this domain's own reading. A **performance cap of
3× vanilla's piece count** applies regardless of tier (`TIER-REQ-005`).

### `city` at 1.0: one centre, not multiple — resolved

**Amended by Kevin, 2026-09-20** (`decisions/DEC-006-size-tiers.md`), closing the tension
`village-jigsaw-placement-1-20-1-to-26-2.md` confirmed: `size`/`maxDepth` is a single-start
recursion-depth budget with no native vanilla mechanism for independent multiple centres. At 1.0,
`city` is **one centre at the deepest piece budget and the 128-block `MAX_TOTAL_STRUCTURE_RANGE`
hard cap** — the largest single settlement reachable without reimplementing vanilla's jigsaw
assembly (`04-architecture.md` `ARCH-DEC-001`'s rejection of a custom `StructureType`).
**Multiple centres are deferred to a later ticket**, not cut: a candidate shape is a second chained
jigsaw start roughly 60 blocks from the first, close enough to read as one settlement, far enough to
feel like two hubs — not designed further here, since it may need its own site/piece-rejection pass
run twice and is a genuinely separate feature from this tier's 1.0 scope.

### Config keys

| Config key | Governs | Default |
|---|---|---|
| `tier.weights.hamlet` / `.village` / `.town` / `.city` | Relative roll weight per tier | `30`/`45`/`20`/`5` |
| `tier.jigsaw_depth.hamlet` / `.village` / `.town` / `.city` | `maxDepth` fed to vanilla's jigsaw assembly per tier | `3`/`6`/`8`/`9` |
| `tier.max_distance.hamlet` / `.village` / `.town` / `.city` | `max_distance_from_center` per tier, blocks | `80`/`96`/`128`/`128` |
| `tier.hamlet_minimum_pieces` | Non-street piece count a shrinking village must clear to stay a `hamlet` rather than triggering a retry | `4` |
| `tier.performance_cap` | Absolute ceiling on total piece count (or equivalent generation cost) per village, regardless of tier | `3×` vanilla's own piece count |

### Actor per step

Single-actor, like `domains/site.md` and `domains/pieces.md`: the mod rolls and applies the tier; the
operator acts only outside the roll, in config.

### Drawings

None. Four named tiers with a budget-ratio column is a complete enumeration on its own; a state
machine would show only "roll once, apply, done," which the table above already states in full.

## 4. Use cases

`UC-004`, `UC-005` in `02-journeys.md`.

## 5. Requirements

| ID | Requirement | Priority | From |
|---|---|---|---|
| `TIER-REQ-001` | The system shall roll exactly one tier per village instance, deterministically from the world seed and the village's position, so regenerating the same seed produces the same tier at the same location. | Must | `00-context.md` "sizes that vary" |
| `TIER-REQ-002` | The tier roll shall be weighted per `tier.weights.*`, each independently configurable. | Must | `decisions/DEC-006-size-tiers.md` |
| `TIER-REQ-003` | The `hamlet` tier shall use vanilla's or a datapack's own existing building pieces at a smaller piece budget than `village`, adding no new piece of its own. | Must | `UC-004`, `00-context.md` "no new buildings" |
| `TIER-REQ-004` | At 1.0, the `city` tier shall generate as one centre at `tier.jigsaw_depth.city` and `tier.max_distance.city` (the 128-block hard cap); multiple centres are deferred to a later ticket. | Must | `UC-005`, `decisions/DEC-006-size-tiers.md` amendment |
| `TIER-REQ-005` | **If** a rolled tier's piece budget would exceed `tier.performance_cap`, **then** the system shall clamp generation to the cap rather than the tier's own budget. | Must | `TIER-FAIL-001` |
| `TIER-REQ-006` | **Where** a tier's weight is configured to zero, the system shall never roll that tier. | Should | `01-actors.md` `ACTORS-004` |
| `TIER-REQ-007` | The system shall treat `tier.hamlet_minimum_pieces` as the floor a shrinking village must clear to remain generated and labelled `hamlet`, per the shrink step of `decisions/DEC-010-shrink-move-vanilla.md`. | Must | `domains/pieces.md` `PIECE-REQ-006` |

## 6. Failure modes

| ID | Failure | Required response |
|---|---|---|
| `TIER-FAIL-001` | A `city` roll's piece budget exceeds `tier.performance_cap` | Clamped to the cap transparently; the village is still labelled and shaped as a `city`, just smaller than its own depth/distance would otherwise produce (`TIER-REQ-005`). |
| `TIER-FAIL-002` | `tier.weights.*` sums to zero across every tier | Falls back to a safe default distribution (weighted toward `village`) with a logged warning, rather than a crash or an unrollable tier. |
| `TIER-FAIL-003` | ~~The `city` tier's multiple-centre shape does not fit vanilla's single-start model~~ — **resolved**: city is one centre at 1.0, multiple centres deferred (`TIER-REQ-004`, `decisions/DEC-006-size-tiers.md` amendment) | No longer a live risk for 1.0; the deferred multi-centre feature reopens this question at its own ticket. |

## 7. Open questions

| Question | Blocks | Decided by |
|---|---|---|
| Whether to build the deferred multiple-centres shape as a second chained jigsaw start ~60 blocks from the first, or some other mechanism | Future ticket, not 1.0 | Not yet scoped — a candidate shape only, `decisions/DEC-006-size-tiers.md` |
| Every default in §3's tables (weights, depths, distances, hamlet minimum, performance cap) | `TIER-REQ-001`–`007` | **Confirmed by Kevin, 2026-09-21 (GV-27)**, tuned against the headless harness sweep (`operations/testing.md`); city's jigsaw depth dropped from `10` to `9` for 1.0 — `decisions/DEC-006-size-tiers.md`'s 2026-09-21 amendment |

### Spacing — closed

**Confirmed by Kevin, 2026-09-21 (GV-27); closes the spacing question this domain previously left
open.** No change to vanilla's own structure spacing at 1.0
(`structure_set/villages.json`: `random_spread`, `spacing: 34` chunks, `separation: 8` chunks,
unchanged 1.20.1–26.2). Every tier's `max_distance_from_center` is capped at the 128-block hard
engine ceiling (`MAX_TOTAL_STRUCTURE_RANGE`) regardless of tier, so `town` and `city` may occasionally
sit near a neighbouring village at minimum separation — **accepted**, not designed around, since the
alternative (widening vanilla's own spacing) is a larger, riskier change this ruling declines to make
for a cosmetic edge case.
