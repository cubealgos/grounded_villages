---
title: "grounded_villages spec — architecture: mixins into JigsawPlacement, shared source, six version/loader nodes"
type: "spec"
category: "grounded_villages"
---

# 04 — Architecture

Sheet §3. Both research notes this spec depended on have now landed:
`village-jigsaw-placement-1-20-1-to-26-2.md` (the vanilla hook, read directly from the 26.2 jar via
`javap`, plus a diff of Mojang's own mapping files for 1.20.1/1.21.1) and
`multi-loader-multi-version-mods-2026.md`'s "Grounded Villages" section (build tooling), both added
2026-09-20. No FILL markers remain in this file.

## Shape

```
 vanilla jigsaw pipeline                  one shared src/main/ tree (Stonecutter default shape)
 ┌────────────────────────────┐          ┌──────────────────────────────────────────────┐
 │ JigsawPlacement.addPieces    │  mixin  │ pure logic, zero Minecraft imports:            │
 │  (start piece)                │────────►│  SiteSelector, PieceGate, TierRoller, ConfigModel │
 │ JigsawPlacement$Placer        │  mixin  │ thin mixin adapters (per §ARCH-DEC-002,        │
 │  .tryPlacingChildren          │────────►│  one variant for 1.20.1, one shared by         │
 │  (every child piece)          │         │  1.21.1/26.2) call the pure logic above         │
 └────────────────────────────┘          └──────────────────────────────────────────────┘
                                            six Stonecutter version nodes:
                                            1.20.1-fabric, 1.20.1-forge, 1.21.1-fabric,
                                            1.21.1-neoforge, 26.2-fabric, 26.2-neoforge
```

Both injection points are gated by testing the structure's registry holder against
`#minecraft:village` (`BuiltInRegistries.STRUCTURE.getTag(...)`/`Holder.is(tag)`,
`decisions/DEC-007-village-tag-scope.md`) — vanilla's other jigsaw structures (bastions, ancient
cities, trail ruins, trial chambers, pillager outposts) and any modded jigsaw structure not tagged
`village` are left untouched.

## `ARCH-DEC-001` — mixin into `JigsawPlacement.addPieces` and `JigsawPlacement$Placer.tryPlacingChildren`, all three loaders, all versions

**Confirmed**, `village-jigsaw-placement-1-20-1-to-26-2.md` §A/§B/§C (read directly from the 26.2
jar via `javap -p -c`, cross-checked against Mojang's own mapping diffs for 1.20.1/1.21.1):

- `net.minecraft.world.level.levelgen.structure.structures.JigsawStructure.findGenerationPoint` does
  **no terrain sampling of its own** — it samples only a pure `HeightProvider` for the start piece's
  Y, then hands off to `JigsawPlacement.addPieces`. All terrain-aware placement happens one layer
  down, which is why both mixin points sit inside `JigsawPlacement`, not `JigsawStructure` itself.
- **No loader on any target version exposes a structure- or piece-placement event.** Fabric API has
  no structure-placement module (a 2016-era proposal was abandoned, never merged); NeoForge's
  structure-adjacent events only affect terrain blending for a mod's *own* new pieces, not
  interception of existing ones; Forge 1.20.1 has no official Mixin support story at all — mods
  bootstrap SpongePowered Mixin themselves via ModLauncher, same as every other loader here. **A
  mixin is required on all three loaders and all three Minecraft major versions, not merely
  preferred.**
- **`JigsawPlacement.addPieces`** (the start piece): if the structure's `project_start_to_heightmap`
  is present, its Y comes from one `chunkGenerator.getFirstFreeHeight(...)` call using whatever
  `Heightmap.Types` the structure JSON names — `village_plains.json` names `WORLD_SURFACE_WG`, which
  treats water as "surface" (§C below). This is the mixin's site-level injection point
  (`domains/site.md`).
- **`JigsawPlacement$Placer.tryPlacingChildren`** (every subsequent piece): `TERRAIN_MATCHING`
  pieces (every street element in `village/plains/streets.json`) get their own independent
  `getFirstFreeHeight` query at `WORLD_SURFACE_WG` — hardcoded in bytecode, not JSON-configurable —
  **with no cross-piece smoothing or agreement check against the start piece or any neighbour**;
  `RIGID` pieces (every house element in `village/plains/houses.json`, 36/36) inherit their height
  purely from fixed offset arithmetic relative to their parent piece, no terrain query of their own.
  This is the mixin's per-piece injection point (`domains/pieces.md`).
- **Confirmed: vanilla has no liquid/fluid check anywhere in jigsaw piece placement, on any version.**
  `WORLD_SURFACE_WG` uses the `NOT_AIR` predicate, so a query over a lake returns the water's own top
  surface — this is the direct, load-bearing mechanism behind "half the village in water": not a
  missing edge case, `WORLD_SURFACE_WG` doing exactly what it is told.

**Considered and rejected: a custom `StructureType`.** `StructureType` is a plain, code-registered
interface (`BuiltInRegistries.STRUCTURE_TYPE`) a mod can register its own implementation into —
technically real and traced through bytecode — but it means reimplementing vanilla's whole
jigsaw graph-assembly algorithm (BFS, junction math, pool-alias resolution) to stay behaviourally
identical for every other structure that uses `minecraft:jigsaw` and isn't a village (bastions,
ancient cities, pillager outposts, trail ruins, trial chambers), for a mod that only wants to change
villages. A mixin carries version-drift risk instead (`contracts/platform-matrix.md`'s version-delta
table), but that risk is bounded and precedented: `TelepathicGrunt/StructureLayoutOptimizer` and
`modding-legacy/structure-gel-api` both maintain this exact mixin shape across the 1.20.1→1.21.4+
range today.

## `ARCH-DEC-002` — pure logic with zero Minecraft imports, thin mixins compiled fresh per node, no precompiled `common` subproject

Reconciles a framing difference between the two research notes, both dated 2026-09-20:
`multi-loader-multi-version-mods-2026.md`'s "Grounded Villages" §3 argues against a **precompiled**
`common` Gradle subproject (because each node's own Mixin annotation-processor run produces its own
`refmap.json`, so a precompiled mixin jar would carry the wrong refmap everywhere except the node
that compiled it); `village-jigsaw-placement-1-20-1-to-26-2.md`'s own Recommendation independently
suggests "a common module ... holding the rejection/tier logic as pure functions ... thin per-loader
mixin/entrypoint modules." **These are not actually in conflict once read at the right grain**: the
refmap problem only touches classes that carry `@Mixin` annotations or reference Minecraft/loader
types. `SiteSelector`, `PieceGate`, `TierRoller`, and `ConfigModel` — pure functions over positions,
heights, booleans, and a seed — reference no Minecraft type and carry no Mixin annotation, so they
need no refmap and are safe to keep as one logically separate, zero-import unit. What must **not** be
precompiled is the **mixin classes themselves** (`JigsawPlacement`/`Placer` targets,
`ARCH-DEC-001`) — those are shared `.java` source, compiled fresh per Stonecutter node, each getting
its own correctly-wired refmap, and calling into the pure-logic unit rather than reimplementing its
decisions per node.

**Six Stonecutter version nodes** (`dev.kikugie.stonecutter` `0.9.8`): `match("1.20.1", "fabric",
"forge"); match("1.21.1", "fabric", "neoforge"); match("26.2", "fabric", "neoforge")`, syntax
`versions/<id>-<loader>`, each with its own `build.<loader>.gradle.kts` supplying only the loader
entrypoint class, the mixin-config JSON, and that loader's own mod-metadata file. Preprocessor syntax
(`//? if <condition> {` … `//?}`) handles small per-version signature differences in the shared mixin
source directly — needed at exactly one boundary (`ARCH-DEC-001`'s version-delta table,
`contracts/platform-matrix.md`): `JigsawPlacement$Placer.tryPlacingChildren`'s parameter list gained
`PoolAliasLookup`/`LiquidSettings` params between 1.20.1 and 1.21.1, so the mixin needs one variant
for 1.20.1 and a second shared by 1.21.1 and 26.2.

**Plugin families: fabric-loom for the three Fabric legs; MDG `2.0.147` for the two NeoForge legs;
MDG's own `legacyforge` addon (`net.neoforged.moddev.legacyforge`) for the Forge 1.20.1 leg** —
confirmed by NeoForged's own `LEGACY.md`, covering Forge 1.17–1.20.1 on the same plugin family as the
NeoForge legs, rather than needing separate ForgeGradle tooling. **Cost if wrong**: none identified
for the build-tooling shape — it is confirmed, not proposed. The version-delta risk named above
(`ARCH-DEC-001`) is the one place this decision could still cost real rework, and it is already
priced in as a two-variant mixin, not a rewrite.

## `ARCH-DEC-003` — config load is loader-agnostic, one startup path

`ConfigModel`'s JSON read/write/validate logic is ordinary pure source (`ARCH-DEC-002`), with no
Minecraft or loader import (`domains/config.md`); each node's loader entrypoint calls it once at
startup and hands it the correct per-platform config-directory path. No loader-specific config format
or library is involved (`decisions/DEC-008-config-file.md`), and no datapack-driven config either —
tolerance/tier values are mod behaviour, not world data, and datapack JSON would reopen the unresolved
cross-mod-override-ordering risk `village-jigsaw-placement-1-20-1-to-26-2.md`'s own open questions
flag (`FabricMC/fabric#3543`, open and unresolved as of 2026-09-20) for no reason — this mod's mixin
path does not depend on datapack override precedence at all, sidestepping that risk entirely.

## `ARCH-DEC-004` — feature flags per behaviour, all independently disableable

Site selection, per-piece rejection, and tier rolling are each governed by their own config toggle
(`domains/config.md`): an operator can run per-piece rejection with site selection off, or tiers with
placement checks off, in any combination. No behaviour is hard-wired to another.

## Loader adapters, condensed

| Loader | Build plugin | Mappings | Mixin shape |
|---|---|---|---|
| Fabric (1.20.1, 1.21.1, 26.2) | `fabric-loom` | Mojang mappings via `loom.officialMojangMappings()` | Same two targets (`ARCH-DEC-001`), version-gated body for the 1.20.1 parameter-list difference |
| NeoForge (1.21.1, 26.2) | MDG `2.0.147` | Mojang mappings by default, reobfuscated to SRG only for the production jar | Same two targets, no version split needed within this range |
| Forge (1.20.1 only) | MDG `2.0.147` + `legacyforge` addon | Compiled against Mojang mappings; runtime jar reobfuscated to SRG transparently via Mixin's refmap step | Same two targets, the 1.20.1 parameter-list variant |

## Java, Gradle, and loader/version toolchain (source: `multi-loader-multi-version-mods-2026.md` §B and its "Grounded Villages" section)

| MC version | Java | Loader plugin(s) | Notes |
|---|---|---|---|
| 1.20.1 | **17** | fabric-loom (Fabric); MDG `2.0.147` + `legacyforge` (Forge) | `fabric-loom`'s own `requiredJava` table: `≥1.18 → Java 17`. Not 21 — the one rung the earlier general toolchain table did not cover. |
| 1.21.1 | 21 | fabric-loom (Fabric); MDG `2.0.147` (NeoForge) | Fabric API `0.116.17+1.21.1`; NeoForge `21.1.251` |
| 26.2 | 25 | fabric-loom (Fabric); MDG `2.0.147` (NeoForge) | Fabric API `0.161.0+26.2`; NeoForge `26.2.0.88` |

Root Gradle pinned to `9.5.1` (the higher pin across every node). Stonecutter `0.9.8`
(`dev.kikugie.stonecutter`, primary repo now Codeberg). Source reads Mojang mappings on every node;
Forge 1.20.1 is the one leg where the compiled name and the runtime name differ, handled
transparently by Mixin's refmap step, invisible to this mod's own code
(`decisions/DEC-004-versions-and-toolchain.md`).

## Runtime topology (sheet §3.1)

Server-side only for every actual decision: site sampling, piece rejection, and tier rolling all run
during world generation on the server (or the integrated-server side of a singleplayer client), the
same "server decides" shape `villager_voices` `ACTORS-002` states for its own reaction system, applied
here to generation rather than gameplay. No client-side code makes a placement decision of any kind;
a client only ever sees the generated result, exactly like any other player exploring a vanilla
world.

## Failure modes with no single owner (sheet §3.6)

| ID | Failure | Response |
|---|---|---|
| `ARCH-FAIL-001` | ~~The hook family does not exist on a given rung~~ — resolved: the same two mixin targets exist on every targeted version, confirmed by direct bytecode/mapping read (`ARCH-DEC-001`) | No longer a live risk in the form first drafted; the residual risk is the version-delta parameter-list difference, already priced into the mixin's two-variant design. |
| `ARCH-FAIL-002` | ~~Forge 1.20.1 does not fit the shared build-tooling shape~~ — resolved: MDG's `legacyforge` addon puts Forge 1.20.1 on the same plugin family as the NeoForge legs (`ARCH-DEC-002`) | No longer a live risk. |
| `ARCH-FAIL-003` | A mixin target's exact parameter list or method shape shifts at a Minecraft version boundary not yet catalogued (`contracts/platform-matrix.md`'s version-delta table names the known ones) | Build fails at compile time for a direct reference, or mod load fails with a named error for a looser target — never a silent no-op, the same discipline `villager_voices` `REACTION-FAIL-001` and `create_synthetic_diamonds`' platform requirements both state. |
| `ARCH-FAIL-004` | A `city`-tier village's larger piece budget (deeper `maxDepth`, `domains/tiers.md`) costs more generation-thread time than expected | Bounded by the performance cap (`domains/tiers.md` `TIER-REQ-005`) regardless of what the tier roll and weights would otherwise allow; each additional sample point costs one noise-column walk, the same per-call cost vanilla itself already pays once per piece. |
| `ARCH-FAIL-005` | Another mod also mixins into `JigsawPlacement`/`Placer` | **Partially resolved, still a real risk**: `village-jigsaw-placement-1-20-1-to-26-2.md` names a specific, close-precedent mod — "Improved Village Placement" (MIT, same version/loader matrix) — that already does whole-site height-variance rejection via (presumably) a similar injection point, not independently bytecode-confirmed for its own mixin target. Two mods mixin-injecting into the same vanilla methods is a real bytecode-level coexistence question this research did not resolve either way. Flagged in `README.md` "Open questions gathered" **for Kevin** alongside the broader prior-art question (`decisions/DEC-002-name.md`). |
