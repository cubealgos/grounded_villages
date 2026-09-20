---
title: "grounded_villages spec — context: why, for whom, and what it will not do"
type: "spec"
category: "grounded_villages"
---

# 00 — Context

## Why this exists

Kevin: "I hate vanilla village generation: sometimes the village is scattered across immense
heights, or some villages spawn with more than half their buildings and paths on water. I want it
to be more realistic, including more size variety in villages." Vanilla's jigsaw structure
generation places each village piece independently, so nothing stops one house from landing on a
cliff edge two dozen blocks above its neighbour, or a street from running straight across a lake.
The result reads as generated rather than settled — no real village would have been built that way,
because no real builder would have built it that way.

## What a realistic village is, for this mod's purposes

- **One ground level, within a tolerance.** Every piece — building or street — sits close to the
  same height the village started at, not scattered across whatever height each individual piece's
  own patch of terrain happened to be (`domains/site.md`, `domains/pieces.md`).
- **Dry.** No piece's footprint sits in water; a village doesn't have paths or house floors running
  through a lake or a river (`domains/pieces.md`).
- **Compact.** A village that fails its site check searches nearby for a better one, and a piece that
  fails its own check is simply not placed, rather than the whole village sprawling to accommodate
  bad terrain (`domains/site.md`, `domains/pieces.md`).
- **Sizes that vary.** Not every village is the same vanilla-sized cluster — a small hamlet, an
  ordinary village, a larger town, and rarely a city, each a genuinely different experience to find
  (`domains/tiers.md`).

## Who it is for

- Players exploring a world, who want a village they find to look like somewhere people would
  actually have settled — not scattered across a mountainside or half-submerged in a lake — and who
  want the size of the next village they find to be a real surprise.
- Server operators who install it and expect villages to generate more sensibly with no
  configuration, with a config file available for anyone who wants to retune the thresholds, tier
  weights, or which structures it applies to (`domains/config.md`).
- Datapack authors whose own village-tagged structures — vanilla-like or entirely custom — get the
  same site-selection and piece-rejection treatment automatically, because scope is driven by the
  `#minecraft:village` structure tag, not a maintained list (`decisions/DEC-007-village-tag-scope.md`).
- Modpack authors who run this mod alongside another worldgen mod (Terralith or similar) and need it
  to behave well on terrain it did not generate itself, without any integration work on either side
  (`02-journeys.md`).
- Another worldgen mod, whose generated terrain this mod reads to decide where villages belong, but
  never edits (`decisions/DEC-005-placement-only.md`).

## Prior art

"Improved Village Placement" (Apollounknowndev, MIT, the same Fabric/Forge/NeoForge 1.20.1→26.2
matrix) already rejects whole village sites by height variance — the site-selection half of this
mod's own scope. Grounded Villages is built independently, with no code reuse, and credited here as
prior art rather than named as competition: the superset this mod adds is height *plus* water *plus*
size tiers, together, in one mod (`decisions/DEC-009-prior-art.md`).

## Business context

No business model, no revenue, no telemetry. Published on Modrinth under MIT, source public on the
cubealgos Forgejo with a GitHub mirror, from the bootstrap (`decisions/DEC-003-licence.md`).

## What it will not do

- **No new buildings.** Every piece this mod places or rejects is a piece vanilla or a datapack
  author already defined; this mod adds no new structure pieces, jigsaw pools, or building designs
  of its own.
- **No terrain edits beyond vanilla's own per-piece foundation fill.** No flattening pass, no lake
  filling, no terraforming of any kind — placement only (`decisions/DEC-005-placement-only.md`).
- **No new villager behaviour.** Villagers themselves — their AI, trades, professions, schedules —
  are entirely untouched; this mod only decides where and how large the structure they live in gets
  to be.
- **No village removal.** A village that cannot find a qualifying site, or loses too many pieces to
  rejection, does not vanish: it shrinks to a hamlet, moves to a shifted site, or falls back to
  vanilla's own unchecked placement — in that order — rather than generating nothing at all
  (`domains/site.md` `SITE-REQ`, `decisions/DEC-010-shrink-move-vanilla.md`).
- **No effect on any structure outside the `#minecraft:village` tag**, unless an operator
  deliberately widens the configured scope (`decisions/DEC-007-village-tag-scope.md`).

## Success

A new world, explored on the widest-supported combination available at the time: the first village
found sits at one consistent ground level, dry, with no piece stranded over water — and the next
several villages found are visibly different sizes from each other, from a small hamlet up to,
rarely, a city — all without any configuration, and all compatible with whatever other worldgen mod
is also installed.
