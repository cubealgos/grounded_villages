---
title: "grounded_villages spec — journeys: the use cases end to end"
type: "spec"
category: "grounded_villages"
---

# 02 — Journeys

Every step names who acts. `UC` ids are flat across the project; domain files reference them.

### `UC-001` — A new world's first village

Actor: player (`ACTORS-001`) · Goal: see the mod visibly working, the first time it matters

| Step | Actor | Action |
|---|---|---|
| 1 | player | Generates a new world and starts exploring. |
| 2 | mod (`ACTORS-006`) | As vanilla structure generation reaches a village start point, samples the candidate site's height spread and water fraction across the whole village radius, before any piece exists (`domains/site.md` `SITE-REQ-001`). |
| 3 | mod | The candidate qualifies on the first try; rolls a size tier deterministically from the world seed (`domains/tiers.md` `TIER-REQ-001`); vanilla's own jigsaw placement proceeds, with each piece and street checked against the per-piece rules as it is placed (`domains/pieces.md`). |
| 4 | player | Finds the village: one consistent ground level, no piece in water, a size that is a genuine roll rather than always the same vanilla footprint. |

### `UC-002` — A village on a hillside that gets moved

Actor: player · Goal: see the search-and-relocate mechanism actually fire

| Step | Actor | Action |
|---|---|---|
| 1 | mod | A village start point lands on a hillside; sampling finds the height spread across the candidate radius exceeds the configured threshold (`domains/site.md` `SITE-REQ-002`). |
| 2 | mod | Rejects the candidate and searches nearby, within a bounded radius and attempt count, for a site that qualifies (`domains/site.md` `SITE-REQ-003`). |
| 3 | mod | A nearby, flatter site qualifies within the search bound; generation proceeds from there instead. |
| 4 | player | Finds the village sitting on the flatter ground the search found, not scattered up and down the original hillside — the complaint `00-context.md` opens with, fixed. |

Fails when: no site within the search bound qualifies → falls back to vanilla's own unchecked
placement at (or near) the original candidate, rather than generating no village at all
(`domains/site.md` `SITE-FAIL-001`).

### `UC-003` — A lakeside village that shrinks away from the water

Actor: player · Goal: see per-piece rejection actually fire, including on streets

| Step | Actor | Action |
|---|---|---|
| 1 | mod | A village's site qualifies overall (the water fraction across the whole radius is within threshold), but several individual pieces and one street segment on the lakeward side would land with water in their footprint. |
| 2 | mod | Each of those pieces, and the street among them, is rejected individually as vanilla's jigsaw placement reaches it — streets get no special exemption (`domains/pieces.md` `PIECE-REQ-004`). |
| 3 | player | Finds a village that simply stops short of the water rather than running paths and house floors into it — visibly smaller on that side than vanilla's own layout would have produced, without any piece appearing to float or clip through the lake. |

### `UC-004` — A hamlet

Actor: player · Goal: encounter the smallest tier

| Step | Actor | Action |
|---|---|---|
| 1 | mod | Rolls the `hamlet` tier for a village instance, from the world seed (`domains/tiers.md`). |
| 2 | mod | Generation proceeds with the hamlet's smaller piece budget — a few houses and one workstation cluster, not a scaled-down copy of a full village. |
| 3 | player | Finds a genuinely small settlement, distinct in character from an ordinary village rather than just a truncated one. |

### `UC-005` — A city

Actor: player · Goal: encounter the rare, largest tier

| Step | Actor | Action |
|---|---|---|
| 1 | mod | Rolls the `city` tier — rare, by its configured weight (`domains/tiers.md` `TIER-REQ-002`). |
| 2 | mod | Generation proceeds with the city's larger piece budget and multiple centres, still bounded by the performance cap regardless of how large the roll would otherwise allow (`domains/tiers.md` `TIER-REQ-005`). |
| 3 | player | Finds a landmark-scale settlement, rare enough to be worth remarking on when it happens. |

### `UC-006` — An operator tuning the config

Actor: server operator (`ACTORS-002`) · Goal: retune the mod without touching Java

| Step | Actor | Action |
|---|---|---|
| 1 | operator | Opens the config file the mod wrote on first run (`domains/config.md` `CONFIG-REQ-001`). |
| 2 | operator | Edits the per-piece height-deviation tolerance, tightens the site water-fraction threshold, and lowers the `city` tier's weight to near zero. |
| 3 | operator | Restarts the server (config is loaded at startup; no live-reload command exists at 1.0, `domains/config.md` `CONFIG-REQ-005`). |
| 4 | mod | From the next newly generated chunk onward, uses the edited values; already-generated villages are unaffected (`ACTORS-002`). |

Fails when: the edited file is malformed JSON, or a value is out of range → the affected key (or the
whole file) falls back to its shipped default, with a logged warning, never a crash
(`domains/config.md` `CONFIG-FAIL-001`, `CONFIG-FAIL-002`).

### `UC-007` — A modpack with Terralith or another terrain mod

Actor: modpack author (`ACTORS-004`) · Goal: confirm compatibility needs no integration work

| Step | Actor | Action |
|---|---|---|
| 1 | modpack author | Bundles this mod alongside a terrain-generation mod that reshapes vanilla's own height and biome distribution. |
| 2 | mod | Reads whatever terrain the other mod produced — heightmap, water — through the same vanilla-level queries it always uses; performs no detection of, or special-casing for, that specific mod (`decisions/DEC-005-placement-only.md`). |
| 3 | mod | Site selection and per-piece rejection run identically to `UC-001`–`UC-003`, against the modded terrain instead of vanilla terrain. |
| 4 | modpack author | Ships the pack with no compatibility patch, no load-order requirement beyond both mods being present, and no terrain scar left behind if a village's site search is exhausted on unusually extreme modded terrain (`domains/site.md` `SITE-FAIL-001`). |
