---
title: "grounded_villages spec — actors: who touches village generation and what each may do"
type: "spec"
category: "grounded_villages"
---

# 01 — Actors

| ID | Actor | May | May not |
|---|---|---|---|
| `ACTORS-001` | **Player** | Explore generated worlds and encounter villages placed and sized by this mod; observe the result — a consistent ground level, a dry footprint, a size that varies village to village | Influence generation directly: nothing about this mod is player-facing input, no command, no item, no screen at 1.0 (`contracts/public-surface.md`) |
| `ACTORS-002` | **Server operator** | Install the mod, edit its config file to retune thresholds, tier weights/budgets, the performance cap, and the structure-tag scope (`domains/config.md`); remove it without a save-format consequence for chunks already generated (`contracts/data-contract.md`) | Change already-generated chunks: a config edit only affects villages generated from that point forward, never retroactively reshapes terrain already written to the save |
| `ACTORS-003` | **Datapack author** | Add a new structure to the `#minecraft:village` tag and have it automatically receive site selection, piece rejection, and tier rolling with no code of their own (`decisions/DEC-007-village-tag-scope.md`); add or edit jigsaw piece pools the way vanilla already allows | Bypass this mod's checks for a tagged structure without going through the server operator's own config-level exclusion (`domains/config.md`) |
| `ACTORS-004` | **Modpack author** | Bundle this mod with any other worldgen mod (Terralith or similar) and ship it with pre-set config defaults for their pack's own terrain; rely on placement-only behaviour meaning no dedicated compatibility patch is ever required against a terrain-generation mod (`decisions/DEC-005-placement-only.md`) | Expect this mod to adapt its thresholds automatically to an unusually extreme terrain mod: an operator whose terrain mod produces almost no flat, dry land may need to widen the config thresholds themselves |
| `ACTORS-005` | **Another worldgen mod** (e.g. a terrain-generation mod, installed alongside) | Generate terrain this mod reads — heightmap, biome, water — before site selection and piece rejection run against it | Be modified, hooked into, or specially detected: this mod never edits another mod's terrain and has no integration code aimed at any specific terrain mod by name (`decisions/DEC-005-placement-only.md`) |
| `ACTORS-006` | **The mod itself, at generation time** | Sample candidate sites, reject pieces, roll a village's size tier — all deterministically from the world seed and the structure's own generation context, entirely server-side, before or during vanilla's own jigsaw piece placement (`04-architecture.md`) | Trust any client input for any of it: exactly like `villager_voices`' `ACTORS-002`, world generation is entirely server-authoritative, and this mod adds no client-side decision of its own |

## Findings from writing this

- **`FINDING-1`** Unlike `villager_voices` (a player-visible, player-audible mod) or `create_firearms`
  (a player-handled item), this mod has **no player-facing surface at all** at 1.0 — `ACTORS-001`'s
  "may" column is the shortest of any sibling spec, because the entire effect is something a player
  only ever sees indirectly, as a village that looks right rather than as anything to interact with.
- **`FINDING-2`** This is the first sibling spec where **another mod, not a player or an operator, is
  a first-class actor** (`ACTORS-005`) with real weight in the design: `decisions/DEC-005-placement-only.md`'s
  entire justification is keeping that actor's "may not be modified" column true regardless of which
  terrain mod it happens to be.
- **`FINDING-3`** `ACTORS-002`'s "may not" column — a config change never reshapes an already-generated
  chunk — is the same one-way-door property `villager_voices`' `DATA-REQ-003` states for its own
  in-memory state, applied here to something that actually gets written to the save: once a chunk is
  generated, it is generated, and no runtime config edit can undo that.
