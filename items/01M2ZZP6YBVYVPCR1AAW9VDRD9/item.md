---
schema_version: 1
id: 01M2ZZP6YBVYVPCR1AAW9VDRD9
key: GV-8
type: feat
title: "Tier roll and budgets: hamlet/village/town/city, seed-deterministic, performance cap"
created_by: kevin
created_at: 2026-09-20T18:01:40Z
---

## Scope

`TierRoller`: rolls exactly one tier per village instance — `hamlet`/`village`/`town`/`city` —
deterministically from `Structure.GenerationContext.random()` (already seeded via
`setLargeFeatureSeed(worldSeed, chunkPos.x, chunkPos.z)`), weighted by `tier.weights.*`, feeding a
different `maxDepth` (`tier.jigsaw_depth.*`) and `max_distance_from_center` (`tier.max_distance.*`)
per tier. A performance cap (`tier.performance_cap`, proposed 3x vanilla's piece count) bounds
generation regardless of tier. At 1.0, `city` is one centre at the deepest budget and the
128-block hard radius (`TIER-REQ-004`, `decisions/DEC-006-size-tiers.md` amendment) — no multiple
centres.

## Approach

A pure recursion-depth budget, not a piece-count or area target (`03-glossary.md` "jigsaw depth"):
this ticket only ever changes which `maxDepth`/`max_distance_from_center` vanilla's own assembly
receives, never reimplements the assembly itself.

## Acceptance criteria

- [ ] `TIER-REQ-001` through `TIER-REQ-007` implemented
- [ ] determinism test: same seed + position rolls the same tier every time, across regenerations
- [ ] weighted-distribution test over many rolls, matching configured `tier.weights.*` within a
  reasonable tolerance
- [ ] `TIER-FAIL-001`: a roll whose budget would exceed `tier.performance_cap` is clamped, not
  denied — the village stays labelled and shaped as its rolled tier, just capped
- [ ] `TIER-FAIL-002`: `tier.weights.*` summing to zero falls back to a safe `village`-weighted
  distribution with a logged warning, never a crash or an unrollable tier
- [ ] `city` generates as one centre only at 1.0 — no multi-centre code path exists yet
  (`TIER-REQ-004`)
- [ ] the proposed defaults (weights `30`/`45`/`20`/`5`, depths `3`/`6`/`8`/`10`, max distances
  `80`/`96`/`128`/`128`, hamlet minimum `4`, performance cap `3x`) are confirmed or revised at this
  ticket — **to confirm**, tuned against GV-10's harness once it lands

## Constraints and prior findings

Blocked by GV-5 (feeds `maxDepth` at the hook's injection point) and GV-9 (reads `tier.*` config
keys). `TIER-FAIL-003` is already resolved by Kevin's ruling (`DEC-006` amendment) — this ticket
implements that ruling, it does not reopen the multiple-centres question. The deferred multi-centre
shape (a second chained jigsaw start ~60 blocks from the first) is explicitly out of scope here,
per `domains/tiers.md` §7.
