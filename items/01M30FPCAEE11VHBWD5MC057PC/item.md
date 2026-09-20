---
schema_version: 1
id: 01M30FPCAEE11VHBWD5MC057PC
key: GV-23
type: docs
title: "Modrinth icon: the village bell on the navy badge, rendered from the game asset"
created_by: kevin
created_at: 2026-09-20T22:41:22Z
---

## Scope

The Modrinth icon for Grounded Villages in the fleet's convention (`standards/marketing/modrinth-collection-icon.md`, `modrinth-publishing.md`): the real in-game asset rendered on the cubealgos navy badge, as `create_brass_compass` (compass), `create_metered_motor` (recoloured creative motor), `create_villager_customers` (emerald), `villager_voices` (speech bubble) did. Subject: the village bell (`minecraft:bell`, the block model rendered through `standards/marketing/modrinth/block-model-render.py` from the 26.2 client jar's own model and textures, never copied into the repo: rendered at build time by `tools/icon.py`), tilted like the siblings' renders, on the navy background, exported to `docs/modrinth/icon.png` at 512×512 with the navy badge from `standards/marketing/modrinth/navy-badge.py`. A second candidate with a small house piece (the plains small house from the jigsaw pool is too big; a bell over a dry ground slab is the fallback) if the bell alone reads poorly at 64 px.

## Approach

`tools/icon.py` as the siblings: locate the client jar under `~/.gradle/caches/fabric-loom/minecraftMaven/`, read `assets/minecraft/models/block/bell*.json` and textures, render with `block-model-render.py`, badge with `navy-badge.py`, write `docs/modrinth/icon.png`; a candidates sheet to the scratchpad for Kevin.

## Acceptance criteria

- [ ] `tools/icon.py` renders the icon deterministically from the client jar's assets (no Mojang pixels committed except the rendered composite, which the siblings also commit as `icon.png`; note the convention in `NOTICE`).
- [ ] `docs/modrinth/icon.png` 512×512, reads at 64 px; a sheet with the candidates for Kevin.
- [ ] `modrinth-publish.py check --repo .` no longer flags the icon; `just check` green; merged through a Forgejo pull request into `development`.

## Constraints and prior findings

The fleet convention: the real game asset on the navy badge (Kevin, 2026-09-20: "i think we should use that navy background for all our mod icons", "for the brass compass we used the actual ingame asset why not for the other two aswell"). Mojang assets are rendered, never copied as files.
