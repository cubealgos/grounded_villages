---
title: "grounded_villages spec — glossary"
type: "spec"
category: "grounded_villages"
---

# 03 — Glossary

| Term | Means |
|---|---|
| **site** | The candidate location for a whole village instance, evaluated once, before any piece exists — the unit `domains/site.md`'s sampling and threshold checks operate on. |
| **footprint** | The set of blocks a single piece (or the whole village, for site-level sampling) actually occupies in plan, used to test for water presence and, at village level, water fraction. |
| **start height** | The one ground height a village's site is evaluated and accepted at; every piece's own height-deviation check (`domains/pieces.md`) is measured against this single reference, not against each piece's own local terrain. |
| **height spread** | The site-level metric: how much the ground height varies across the whole candidate village radius, sampled before pieces exist (`domains/site.md` `SITE-REQ-001`). A high spread is what causes vanilla's "scattered across immense heights" complaint (`00-context.md`). |
| **water fraction** | The site-level metric: what fraction of the sampled area across the candidate village radius is water, used alongside height spread to accept or reject a whole site (`domains/site.md`). Distinct from a single piece's own water-in-footprint check (`domains/pieces.md`), which is a per-piece pass/fail, not a fraction. |
| **piece** | One jigsaw structure piece — a building or a street segment — as vanilla's own jigsaw placement already defines the term; this mod adds no new pieces, only accepts or rejects the ones vanilla or a datapack already offers (`domains/pieces.md`). |
| **street** | A piece like any other for this mod's purposes: subject to the identical footprint-water and height-deviation checks as a building, with no exemption (`decisions/DEC-005-placement-only.md`, `domains/pieces.md` `PIECE-REQ-004`). |
| **tier** | One of four named village sizes — hamlet, village, town, city — rolled once per village instance, deterministically from the world seed (`domains/tiers.md`). |
| **budget** | The relative piece-count ceiling a rolled tier gives a village instance to place pieces within, before the performance cap applies (`domains/tiers.md`). |
| **jigsaw depth** | Vanilla's own `size` field (decoded as `JigsawStructure.maxDepth`): a pure recursion-depth budget — the maximum piece-to-piece chain length outward from the start piece — not a target piece count or area. `village_plains.json` ships `size: 6`. This mod's size tiers (`domains/tiers.md`) work by feeding a different `maxDepth` (and, for a genuinely different building mix, a different `start_pool`) per rolled tier, confirmed against `village-jigsaw-placement-1-20-1-to-26-2.md` §D. |
| **the village tag** | `#minecraft:village`, the vanilla structure tag this mod's scope is defined against by default — every structure carrying it, vanilla or modded, is covered automatically; configurable (`decisions/DEC-007-village-tag-scope.md`). Vanilla itself lists exactly five members — `village_plains`, `village_desert`, `village_savanna`, `village_snowy`, `village_taiga` — unchanged 1.20.1 through 26.2 (`village-jigsaw-placement-1-20-1-to-26-2.md`, verified facts). |
