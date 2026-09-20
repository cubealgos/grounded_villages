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
| Forge, 1.20.1 | **17** | MDG `2.0.147` + `legacyforge` addon | Forge `47.4.23` (verified GV-2, see below); compiled against Mojang mappings, runtime jar reobfuscated to SRG via Mixin's refmap step; Fabric API precedent for this MC generation is `0.92.12+1.20.1` (the Fabric leg's own pin, not Forge's) |
| Fabric, 1.21.x (Wave 3) | 21 (every 1.21.x point shares Java 21, `≥1.20.5 → Java 21`) | `fabric-loom` `1.17-SNAPSHOT` | Chosen at the Wave 3 ticket, against whichever point release has a current Fabric API build |
| NeoForge, 1.21.x (Wave 3) | 21 | MDG `2.0.147` | Chosen at the Wave 3 ticket, against whichever point release has a current NeoForge build |

Root Gradle `9.5.1` across every row; Stonecutter `dev.kikugie.stonecutter` `0.9.8`. Source is
Mojang mappings everywhere (`decisions/DEC-004-versions-and-toolchain.md`).

`PLATFORM-REQ-001`: **if** any row's toolchain version moves, **then** `just doctor` fails naming the
row (mirroring `villager_voices` `PLATFORM-REQ-001`).

## Verified coordinates (GV-2, 2026-09-20)

Every coordinate below was checked with a real HTTP request against its own Maven (or the Gradle
Plugin Portal / distribution service) before being pinned in the Stonecutter skeleton, per GV-2's
own instructions — not carried over from the research note unverified. All confirmed present;
none needed to change from what the spec above already named, except the Forge version itself,
which no prior document had pinned.

| Coordinate | Verified as | How |
|---|---|---|
| `dev.kikugie.stonecutter` | `0.9.8` | Gradle Plugin Portal, HTTP 200 |
| `dev.kikugie.loom-back-compat` | `0.4.2` | present in `maven.kikugie.dev/releases`' own `maven-metadata.xml` (Plugin Portal marker itself 404s off Central; the plugin is real, just not mirrored there — `pluginManagement.repositories` must include `maven.kikugie.dev`) |
| `org.gradle.toolchains.foojay-resolver-convention` | `1.0.0` | Gradle Plugin Portal, HTTP 200 |
| `net.neoforged.moddev` / `net.neoforged.moddev.legacyforge` | `2.0.147` | Gradle Plugin Portal, HTTP 200 for both plugin markers |
| `net.fabricmc:fabric-loom` | `1.17-SNAPSHOT` | `maven.fabricmc.net` snapshot metadata, build 20, last updated 2026-09-15 -- still resolves; not the newest available (`1.18.2` is latest release) but the spec's own deliberate pin |
| Fabric API | `0.116.17+1.21.1`, `0.161.0+26.2`, `0.92.12+1.20.1` | exact versions listed in `maven.fabricmc.net`'s own `fabric-api` metadata |
| `net.fabricmc:fabric-loader` | `0.19.5` | latest release per `maven.fabricmc.net` metadata (spec did not pin an exact loader version; this ticket supplies and verifies it) |
| NeoForge | `21.1.251`, `26.2.0.88` | exact versions listed in `maven.neoforged.net/releases`' own metadata |
| **Forge 1.20.1** | **`47.4.23`** | latest of 132 versions listed in `maven.minecraftforge.net`'s own metadata for `1.20.1-*`; `-userdev.jar` classifier confirmed present (HTTP 200) -- no prior document in this spec had pinned an exact Forge version, this ticket is the first to supply one |
| Root Gradle wrapper | `9.5.1` | `services.gradle.org/distributions/gradle-9.5.1-bin.zip` downloads clean (140MB, HTTP 200 after redirect); confirmed still the deliberate pin from `multi-loader-multi-version-mods-2026.md` §B (NeoForge 26.1's own floor), not merely the newest available (`9.7.1` is latest) |
| Mixin annotation processor (Forge leg, when wired) | `org.spongepowered:mixin:0.8.5:processor` | resolves via `maven.minecraftforge.net`, HTTP 200 -- not yet added to the build (see correction below) |
| NeoForge licence | LGPL-2.1 | GitHub API `license` field on `neoforged/NeoForge`, matching Forge's own (`MinecraftForge/MinecraftForge` `LICENSE.txt`, both compile-time platform dependencies only, `decisions/DEC-003-licence.md`) |

**Correction to the cited research** (`multi-loader-multi-version-mods-2026.md` "Grounded
Villages" §5's `stonecutter registerChiseled tasks.register(name, stonecutter.chiseled) {
ofTask(...) }` snippet, and this contract's own "CI matrix" section below as originally written):
that API does not exist in the actual `0.9.8` jar -- verified by decompiling
`~/.gradle/caches/modules-2/files-2.1/dev.kikugie/stonecutter/0.9.8/**/stonecutter-0.9.8.jar` and
finding zero occurrences of the string `"chiseled"` anywhere in it. The research's own cited
source (`Trainguy9512/locomotion`) in fact pins Stonecutter `0.5.1` in its `settings.gradle.kts`
(read live 2026-09-20), an old release where `chiseled`/`registerChiseled` still existed, not
`0.9.8`. Current `0.9.8`/v2 docs (`codeberg.org/stonecutter/docs`,
`docs/wiki/v2/reference/gradle-api/project-controller.md` "Task Hooks" > "Aggregation") document
the replacement used in this repository's actual `stonecutter.gradle.kts`:
`stonecutter.tasks.named("<task>")` returns a lazy collection of that task across every registered
node, used as a `dependsOn` on a plain `tasks.register("chiseledBuild") { ... }`. Same two task
names (`chiseledBuild`, `chiseledCheck`), same effect (verified live: `./gradlew chiseledBuild
chiseledCheck` produces six distinct jars and passes `check` on all six nodes), real `0.9.8` API.
Those same v2 docs ("Multi-loader Mods with Stonecutter" > "Flat Approaches" > "Split
Buildscript") independently name this repository's own shape -- one shared `src/main/` tree plus
a dedicated `build.<loader>.gradle.kts` per loader -- as **"the preferred multi-loader setup with
Stonecutter,"** citing `rotgruengelb/stonecutter-mod-template` as a worked example, which this
ticket's build scripts were cross-checked against for the Forge leg.

**Second correction, live-verified**: wiring MDG legacyforge's `mixin { add(sourceSets.main.get(),
"grounded_villages.refmap.json"); config("grounded_villages.mixins.json") }` block plus the
`org.spongepowered:mixin:0.8.5:processor` annotationProcessor with **zero** `@Mixin`-annotated
classes present makes `reobfJar` fail: `FileNotFoundException` on
`build/mixin/grounded_villages.refmap.json.mappings.tsrg`, because the AP only writes that file
when there is a `@Mixin` class to process. GV-2 ships no mixin classes (skeleton only), so
`build.forge.gradle.kts` does not wire that block yet -- the mixin-config JSON stub is still
referenced via the jar manifest's `MixinConfigs` attribute (SpongePowered Mixin self-bootstraps via
ModLauncher regardless, "Mixin support story" above, and loads a zero-mixin config as a no-op).
The `mixin {}` block and its AP dependency return once a real `@Mixin` class lands.

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

Woodpecker: `./gradlew chiseledBuild` — a plain aggregate task this repository's root
`stonecutter.gradle.kts` registers itself (`tasks.register("chiseledBuild") {
dependsOn(stonecutter.tasks.named("buildAndCollect")) }`), invoking each node's own
`buildAndCollect`-shaped task. Confirmed this is not created automatically by applying the
Stonecutter plugin: the root build script must register it itself. See "Verified coordinates
(GV-2)" above for the correction to the exact registration API (the research's own
`registerChiseled`/`stonecutter.chiseled` snippet does not exist in the real `0.9.8` jar; live-
verified 2026-09-20). Each wave adds jobs for its own new nodes only; per-node compilation
(`04-architecture.md` `ARCH-DEC-002`) means a Forge 1.20.1 job failing never blocks a
Fabric/NeoForge release and vice versa.

`PLATFORM-REQ-004`: **if** a shipped combination's CI job is red, **then** that combination's
Modrinth version is not published; the other combinations are unaffected.
