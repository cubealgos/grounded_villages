---
title: "grounded_villages spec — platform matrix: loaders, versions, and the CI grid"
type: "spec"
category: "grounded_villages"
---

# Platform matrix (`PLATFORM`)

## Target combinations, in build order

| Combination | State | Ships in |
|---|---|---|
| Fabric, 1.21.1 | planned | Wave 1 |
| NeoForge, 1.21.1 | planned | Wave 1 |
| Fabric, 26.2 | planned | Wave 1 |
| NeoForge, 26.2 | planned | Wave 1 |
| Forge, 1.20.1 | planned | Wave 2 |
| Fabric, the 1.21.x point releases with a Fabric API and NeoForge release | planned | Wave 3 |
| NeoForge, the 1.21.x point releases with a Fabric API and NeoForge release | planned | Wave 3 |

Every row is `planned` — no code and no repository exist yet
(`decisions/DEC-004-versions-and-toolchain.md`). Waves 1–2 (six nodes) are confirmed build targets
per `multi-loader-multi-version-mods-2026.md`'s "Grounded Villages" section and
`village-jigsaw-placement-1-20-1-to-26-2.md`, both added 2026-09-20 after this spec's first draft.
**Wave 3 is intentionally unenumerated**: neither research note names a curated list of long-lived
1.21.x point releases (the jigsaw note's own version-delta table only bisects a class-shape change to
"between 1.21.2 and 1.21.9, not pinned" — an incidental mention, not a release-selection claim). Per
the coordinator's ruling, Wave 3's exact members are **chosen at that wave's own ticket**, against
whichever 1.21.x point releases have both a Fabric API and a NeoForge release at that time — not
invented here. This is a decision rule, not a placeholder: it closes the question rather than leaving
it open.

## Per-row toolchain (confirmed for Waves 1–2; Wave 3 chosen at its own ticket)

| Row | Java | Build plugin(s) | Version pins |
|---|---|---|---|
| Fabric, 1.21.1 | 21 | `fabric-loom` `1.17-SNAPSHOT` | Fabric API `0.116.17+1.21.1` |
| NeoForge, 1.21.1 | 21 | MDG `2.0.147` | NeoForge `21.1.251` |
| Fabric, 26.2 | 25 | `fabric-loom` `1.17-SNAPSHOT` | Fabric API `0.161.0+26.2` |
| NeoForge, 26.2 | 25 | MDG `2.0.147` | NeoForge `26.2.0.88` |
| Forge, 1.20.1 | **17** | MDG `2.0.147` + `legacyforge` addon | Compiled against Mojang mappings, runtime jar reobfuscated to SRG via Mixin's refmap step; Fabric API precedent for this MC generation is `0.92.12+1.20.1` (the Fabric leg's own pin, not Forge's) |
| Fabric, 1.21.x (Wave 3) | 21 (every 1.21.x point shares Java 21, `≥1.20.5 → Java 21`) | `fabric-loom` `1.17-SNAPSHOT` | Chosen at the Wave 3 ticket, against whichever point release has a current Fabric API build |
| NeoForge, 1.21.x (Wave 3) | 21 | MDG `2.0.147` | Chosen at the Wave 3 ticket, against whichever point release has a current NeoForge build |

Root Gradle `9.5.1` across every row; Stonecutter `dev.kikugie.stonecutter` `0.9.8`. Source is
Mojang mappings everywhere (`decisions/DEC-004-versions-and-toolchain.md`).

`PLATFORM-REQ-001`: **if** any row's toolchain version moves, **then** `just doctor` fails naming the
row (mirroring `villager_voices` `PLATFORM-REQ-001`).

## Hook targets, per loader

**Confirmed for every row** (`village-jigsaw-placement-1-20-1-to-26-2.md`, read directly against the
26.2 jar via `javap`, cross-checked against Mojang's own mapping diffs for 1.20.1/1.21.1):
`net.minecraft.world.level.levelgen.structure.structures.JigsawPlacement.addPieces` (start-piece
placement) and `JigsawPlacement$Placer.tryPlacingChildren` (every subsequent piece) — the same two
targets on every version and every loader, since no loader on any target version exposes a
structure- or piece-placement event of its own (confirmed by direct repo/code-search read of Fabric
API, NeoForge, and Forge 1.20.1). One real per-version difference: `tryPlacingChildren`'s parameter
list gains `PoolAliasLookup`/`LiquidSettings` between 1.20.1 and 1.21.1 (`04-architecture.md`
`ARCH-DEC-002`), so the shared mixin source needs one variant for 1.20.1 and a second shared by
1.21.1 and 26.2 — not a per-loader difference, a per-Minecraft-version one. Both targets are written
once as shared mixin source, compiled fresh per Stonecutter node (`ARCH-DEC-002`), not duplicated by
hand per loader.

### Version deltas relevant to the mixin (source: same note, "Version deltas" table)

| Item | 1.20.1 | 1.21.1 | 26.2 |
|---|---|---|---|
| `JigsawStructure.maxDistanceFromCenter` | plain `int` | plain `int` | `MaxDistance(horizontal, vertical)` record — exact switch version between 1.21.2 and 1.21.9, not pinned |
| `JigsawPlacement.addPieces` params | no `PoolAliasLookup`/`DimensionPadding`/`LiquidSettings` | adds all three | same as 1.21.1 |
| Mixin support story | No official support on Forge; mods self-bootstrap SpongePowered Mixin via ModLauncher | Official declarative `[[mixins]]` since NeoForge 20.3 | same as 1.21.1 |
| `ResourceLocation` class name | `ResourceLocation` | `ResourceLocation` | renamed `Identifier` at 1.21.11 (26.x ships unobfuscated) |

`PLATFORM-REQ-002`: **if** a hook target is renamed or removed by a Minecraft update, **then** the
build fails at compile time for a direct reference, or mod load fails with a named error for a looser
target — never a silent no-op (`04-architecture.md` `ARCH-FAIL-003`).

## Client and server

Site selection, per-piece rejection, and tier rolling are all server-side (or the integrated-server
side of a singleplayer client), as world generation always is — no client-only code makes a placement
decision (`04-architecture.md` "Runtime topology"). Config is loaded server-side only; there is no
client-side config surface, since nothing here is player-visible (`01-actors.md` `FINDING-1`).

## Structure-mod compatibility

No hard dependency on, or detection of, any specific other worldgen mod
(`decisions/DEC-005-placement-only.md`). `PLATFORM-REQ-003`: the mod shall make no version or
presence check against any other specific mod's jar; compatibility is a property of placement-only
design, not a maintained compatibility list.

## CI matrix, one job per shipped combination

Woodpecker: `./gradlew chiseledBuild` — Stonecutter's aggregate task (type `stonecutter.chiseled`),
invoking each node's own `buildAndCollect`-shaped task. Confirmed this is not created automatically
by applying the Stonecutter plugin: the root build script must register it itself
(`stonecutter registerChiseled tasks.register("chiseledBuild", stonecutter.chiseled) { ofTask("buildAndCollect") }`,
`multi-loader-multi-version-mods-2026.md` "Grounded Villages" §5, read verbatim from a real
Stonecutter mod's own build script). Each wave adds jobs for its own new nodes only; per-node
compilation (`04-architecture.md` `ARCH-DEC-002`) means a Forge 1.20.1 job failing never blocks a
Fabric/NeoForge release and vice versa.

`PLATFORM-REQ-004`: **if** a shipped combination's CI job is red, **then** that combination's
Modrinth version is not published; the other combinations are unaffected.
