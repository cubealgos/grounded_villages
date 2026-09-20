---
title: "grounded_villages DEC-006 — Four weighted size tiers; city at 1.0 is one centre, capped at the 128-block hard radius"
type: "spec"
category: "grounded_villages"
---

# `DEC-006` — Four weighted size tiers; city at 1.0 is one centre, capped at the 128-block hard radius

**Status:** decided by Kevin, 2026-09-20; amended by Kevin, 2026-09-20, closing `TIER-FAIL-003`.
Default weights/depths/distances are proposed by Claude, 2026-09-20, Kevin to confirm at the first
ticket.

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

## Default weights, depths, and distances — proposed, tune at the harness sweep

| Tier | Weight | Jigsaw depth (`maxDepth`) | Max distance from centre |
|---|---|---|---|
| `hamlet` | 30 | 3 | 80 blocks |
| `village` | 45 | 6 (vanilla's own default) | 96 blocks |
| `town` | 20 | 8 | 128 blocks (the hard cap) |
| `city` | 5 | 10 | 128 blocks (the hard cap) |

Plus a **hamlet minimum of 4 non-street pieces** (`TIER-REQ-007`, `decisions/DEC-010-shrink-move-vanilla.md`)
and a **performance cap of 3× vanilla's piece count** (`TIER-REQ-005`). All of these are proposed
defaults — reasonable starting numbers derived from vanilla's own `size: 6`/`max_distance_from_center:
80` and the 128-block hard ceiling, not measured against a real world yet. **Proposed by Claude,
2026-09-20, Kevin to confirm at the first ticket** — the full schema lives in `domains/config.md`,
not repeated here beyond this summary.

Alternative considered: continuous size variation (a random multiplier per village) instead of named
discrete tiers. Rejected: discrete, named tiers are what a player actually experiences and talks
about ("I found a city!"), they map cleanly to config keys an operator can reason about individually,
and they are what Kevin asked for by name. Cost if wrong: a continuous variant is a strict
generalisation of four discrete tiers and could be added later without removing the tier names
players already understand.
