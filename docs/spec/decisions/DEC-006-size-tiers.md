---
title: "grounded_villages DEC-006 — Four weighted size tiers; city at 1.0 is one centre, capped at the 128-block hard radius"
type: "spec"
category: "grounded_villages"
---

# `DEC-006` — Four weighted size tiers; city at 1.0 is one centre, capped at the 128-block hard radius

**Status:** decided by Kevin, 2026-09-20; amended by Kevin, 2026-09-20, closing `TIER-FAIL-003`;
amended again by Kevin, 2026-09-21 (GV-27), dropping city's jigsaw depth from 10 to 9. Default
weights/depths/distances confirmed by Kevin, 2026-09-21 (GV-27).

Kevin: "I want it to be more realistic, including more size variety in villages." Four tiers, each a
named relative piece budget against vanilla's own village size:

| Tier | Relative to vanilla | Shape |
|---|---|---|
| **Hamlet** | Smallest | A few houses and one workstation cluster — not a full village in miniature, a genuinely small settlement. |
| **Village** | ~1× (vanilla-like) | The common case — what vanilla already generates today, unchanged in scale. |
| **Town** | ~2× vanilla's piece budget | Roughly twice the buildings of a vanilla village. |
| **City** | ~3× vanilla's piece budget, rare | **Amended, below** |

Every village instance rolls its tier **deterministically from the world seed** (`domains/tiers.md`
`TIER-REQ-001`) — the same seed regenerates the same tier at the same location, matching vanilla's
own seed-determinism for everything else about structure generation. A **performance cap** bounds
total generated piece count/work regardless of tier, so a city roll can never make a single village
an unbounded cost on the generation thread (`domains/tiers.md` `TIER-REQ-005`).

## Amendment, 2026-09-20: city at 1.0 is one centre, not multiple

`village-jigsaw-placement-1-20-1-to-26-2.md` confirmed `size`/`maxDepth` is a single-start
recursion-depth budget — vanilla has no native mechanism for independent multiple centres within one
structure instance (`domains/tiers.md` `TIER-FAIL-003`). Kevin's ruling closing that tension:
**at 1.0, `city` is one centre at the deepest piece budget and the 128-block hard structure radius**
(`MAX_TOTAL_STRUCTURE_RANGE`, `village-jigsaw-placement-1-20-1-to-26-2.md` §D) — the largest single
settlement this mod can reach without reimplementing vanilla's jigsaw assembly. **Multiple centres
are deferred, not cut**: a later ticket may add a second chained start roughly 60 blocks from the
first, noted as a candidate shape in `domains/tiers.md` §7, not designed further here.

## Default weights, depths, and distances — confirmed

| Tier | Weight | Jigsaw depth (`maxDepth`) | Max distance from centre |
|---|---|---|---|
| `hamlet` | 30 | 3 | 80 blocks |
| `village` | 45 | 6 (vanilla's own default) | 96 blocks |
| `town` | 20 | 8 | 128 blocks (the hard cap) |
| `city` | 5 | 9 | 128 blocks (the hard cap) |

Plus a **hamlet minimum of 4 non-street pieces** (`TIER-REQ-007`, `decisions/DEC-010-shrink-move-vanilla.md`)
and a **performance cap of 3× vanilla's piece count** (`TIER-REQ-005`). All of these are shipped
defaults — reasonable starting numbers derived from vanilla's own `size: 6`/`max_distance_from_center:
80` and the 128-block hard ceiling. **Confirmed by Kevin, 2026-09-21 (GV-27)** — the full schema
lives in `domains/config.md`, not repeated here beyond this summary. `city`'s own jigsaw depth is
`9`, not `10` — see the 2026-09-21 amendment below.

Alternative considered: continuous size variation (a random multiplier per village) instead of named
discrete tiers. Rejected: discrete, named tiers are what a player actually experiences and talks
about ("I found a city!"), they map cleanly to config keys an operator can reason about individually,
and they are what Kevin asked for by name. Cost if wrong: a continuous variant is a strict
generalisation of four discrete tiers and could be added later without removing the tier names
players already understand.

## Amendment, 2026-09-21 (GV-27): city's jigsaw depth drops from 10 to 9

GV-8's own tiers-enabled sweep, extended past the committed ten-seed baseline to reach a real
`city` sample, found seed 18 (`minecraft:village_taiga`, depth 10, the tier's then-default) rolled
**718 pieces** in **28,619 ms** (~28.6s) — the largest and slowest single village measured across
this fleet's entire baseline history, at a depth `TierRoller.capDepth`'s own linear model judged
well within the performance cap (`docs/baseline/README.md` "GV-8: tier rolling", "A real
performance finding about the performance cap"). `TierRoller`'s own javadoc already documents why:
piece count grows *faster* than linearly with depth, and `capDepth` bounds an *expected* piece
count, not any single village's *actual* one — a structural property of the "cap the depth"
mechanism this decision does not undo.

**Kevin's ruling**: drop `city`'s shipped jigsaw depth from `10` to `9` for 1.0 — a direct response
to the 718-piece measurement, trading some of `city`'s own maximum reach for a lower worst-case
cost, without pretending the underlying cap is now a hard bound (it structurally is not, and
remains a best-effort dampener per `TierRoller`'s own javadoc). **A live, hard piece-count bound —
counting pieces during jigsaw assembly and aborting mid-generation, rather than only capping the
depth budget going in — stays deferred to a later ticket**, same restraint this decision already
showed for the multiple-centres deferral above: a materially heavier mechanism than this ticket's
own scope. `city`'s max distance stays at the 128-block hard cap, unchanged by this amendment.
