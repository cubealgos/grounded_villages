---
title: "grounded_villages spec — public surface: the config file, the tag scope, no API at 1.0"
type: "spec"
category: "grounded_villages"
---

# Public surface (`SURFACE`)

| Surface | Stable from | What it is |
|---|---|---|
| Mod id `grounded_villages` | 1.0 | Fabric/NeoForge/Forge mod id, every loader (`decisions/DEC-002-name.md`) |
| Config file schema (`domains/config.md`) | 1.0 | Every key in `domains/config.md` §3's schema table; a server operator or a modpack author pre-seeding a config in their pack may rely on the key names and types staying stable within a major version |
| `#minecraft:village` as the default scope tag | 1.0 | A datapack author's own structure, once tagged, automatically receives site selection and per-piece rejection with no code of their own (`decisions/DEC-007-village-tag-scope.md`) — the tag itself is this mod's extension point |
| `scope.structure_tags` config key | 1.0 | The one way to widen or narrow which tags this mod acts on (`decisions/DEC-007-village-tag-scope.md`) |

**No Java API at 1.0.** This mod exposes no public class, interface, event bus, or capability for
another mod to call into — unlike `villager_voices`' EMF animation-variable surface, there is no
external integration point beyond the config file and the structure tag. A mod that wants
programmatic control over site selection or piece rejection has none at 1.0; this is a deliberate
scope boundary (`00-context.md`), not an oversight — revisit only if a real integration need
surfaces.

Not public: the `SiteSelector`/`PieceGate`/`TierRoller` internal class shapes
(`04-architecture.md`), the exact sampling grid or search algorithm beyond the pass/fail contract
`domains/site.md` states, and the exact hook mechanism into vanilla's jigsaw pipeline (an
implementation detail of *how* a piece is observed, not a contract any external pack depends on).
Versioned by SemVer over the surface above (`operations/release.md`).

`SURFACE-REQ-001`: a change to a stable surface (a config key's name, type, or removal; the default
scope tag) is a major version.
`SURFACE-REQ-002`: **if** an operator's config file names a key this mod no longer recognises,
**then** the system shall ignore it with a logged warning, never fail startup
(`domains/config.md` `CONFIG-REQ-004`).
`SURFACE-REQ-003`: a datapack author cannot bypass this mod's checks for a tagged structure except
through the server operator's own config-level exclusion (`01-actors.md` `ACTORS-003`) — there is no
per-structure opt-out available to the datapack author directly, only to the operator.
