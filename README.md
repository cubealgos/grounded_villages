# Grounded Villages

Villages generate on one consistent ground level, dry, and in a genuine range of sizes: a
multi-loader, multi-version Minecraft mod (Fabric, NeoForge, and Forge, 1.20.1 through 26.2).
Placement only — no terrain edits of this mod's own, no new buildings, no new villager behaviour,
no village removal.

Mod id `grounded_villages`. Independent of "Improved Village Placement" (credited as prior art,
`NOTICE`); Grounded Villages adds height *plus* water *plus* size tiers together, in one mod.

## Version matrix

| Combination | Wave | State |
|---|---|---|
| Fabric, 1.21.1 | 1 | planned |
| NeoForge, 1.21.1 | 1 | planned |
| Fabric, 26.2 | 1 | planned |
| NeoForge, 26.2 | 1 | planned |
| Forge, 1.20.1 | 2 | planned |
| Fabric, the 1.21.x point releases with a current Fabric API and NeoForge release | 3 | planned |
| NeoForge, the 1.21.x point releases with a current Fabric API and NeoForge release | 3 | planned |

No code exists yet — this repository is at its bootstrap ticket (GV-1). See
`docs/spec/contracts/platform-matrix.md` for the full toolchain per row.

## Install

Not yet published. Once released: drop the jar matching your Minecraft version and loader into
`mods/`, alongside Fabric API (Fabric legs) or nothing extra (NeoForge/Forge legs).

## Config

`config/grounded_villages.json`, written with shipped defaults on first launch. No in-game
reload — a config edit takes effect on the next server start. See
`docs/spec/domains/config.md` for the full schema.

## Licence

MIT (`LICENSE`); credits in `NOTICE`. Source public under the `cubealgos` organisation.

Development: `just --list`. The specification is `docs/spec/`.
