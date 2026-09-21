---
title: "grounded_villages spec — platform matrix: loaders, versions, and the CI grid"
type: "spec"
category: "grounded_villages"
---

# Platform matrix (`PLATFORM`)

## Target combinations, in build order

| Combination | State | Ships in |
|---|---|---|
| Fabric, 1.21.1 | built, CI green; ships in 1.0 (GV-21) | Wave 1 |
| NeoForge, 1.21.1 | built, CI green; ships in 1.0 (GV-21) | Wave 1 |
| Fabric, 26.2 | built, CI green; ships in 1.0 (GV-21) | Wave 1 |
| NeoForge, 26.2 | built, CI green; ships in 1.0 (GV-21) | Wave 1 |
| Fabric, 1.20.1 | built, proven at GV-2 and GV-24; ships with Wave 2 | Wave 2 |
| Forge, 1.20.1 | built, CI green; ships in 1.0 (GV-21) | Wave 2 |
| Fabric, 1.21.4 | built, CI green; ships in 1.0 (GV-21) | Wave 3 |
| NeoForge, 1.21.4 | built, CI green; ships in 1.0 (GV-21) | Wave 3 |
| Fabric, 1.21.5 | built, CI green; ships in 1.0 (GV-21) | Wave 3 |
| NeoForge, 1.21.5 | built, CI green; ships in 1.0 (GV-21) | Wave 3 |
| Fabric, 1.21.8 | built, CI green; ships in 1.0 (GV-21) | Wave 3 |
| NeoForge, 1.21.8 | built, CI green; ships in 1.0 (GV-21) | Wave 3 |

Every row is built and green in CI as of 2026-09-21 (GV-2, GV-15, GV-17); `planned` becomes `shipped` when GV-21 publishes the twelve versions
(`decisions/DEC-004-versions-and-toolchain.md`). Waves 1–2 (six nodes) are confirmed build targets
per `multi-loader-multi-version-mods-2026.md`'s "Grounded Villages" section and
`village-jigsaw-placement-1-20-1-to-26-2.md`, both added 2026-09-20 after this spec's first draft.
**Wave 3 is now enumerated (GV-17, 2026-09-21)**: three 1.21.x point releases -- 1.21.4, 1.21.5,
1.21.8 -- chosen live against Modrinth's own Fabric API release feed and `maven.neoforged.net`'s
own NeoForge release feed, per the decision rule `decisions/DEC-004-versions-and-toolchain.md`
already set (neither research note named a curated list; the jigsaw note's own version-delta table
only bisected an unrelated class-shape change to "between 1.21.2 and 1.21.9, not pinned" -- an
incidental mention, not a release-selection claim). See "Wave 3 nodes (GV-17)" below for the full
live evidence and why the other candidates were not picked: 1.21.3, 1.21.10 and 1.21.11 each have
both a Fabric API and a NeoForge release too, but lost out on spread/adoption grounds; 1.21.2,
1.21.6, 1.21.7 and 1.21.9 have a Fabric API release but no NeoForge release at all, so none of the
four ever qualified.

## Per-row toolchain (confirmed for Waves 1–3)

| Row | Java | Build plugin(s) | Version pins |
|---|---|---|---|
| Fabric, 1.21.1 | 21 | `fabric-loom` `1.17-SNAPSHOT` | Fabric API `0.116.17+1.21.1` |
| NeoForge, 1.21.1 | 21 | MDG `2.0.147` | NeoForge `21.1.251` |
| Fabric, 26.2 | 25 | `fabric-loom` `1.17-SNAPSHOT` | Fabric API `0.161.0+26.2` |
| NeoForge, 26.2 | 25 | MDG `2.0.147` | NeoForge `26.2.0.88` |
| Fabric, 1.20.1 | 17 | `fabric-loom` `1.17-SNAPSHOT` | Fabric API `0.92.12+1.20.1` |
| Forge, 1.20.1 | **17** | MDG `2.0.147` + `legacyforge` addon | Forge `47.4.23` (verified GV-2, see below); compiled against Mojang mappings, runtime jar reobfuscated to SRG via Mixin's refmap step; Fabric API precedent for this MC generation is `0.92.12+1.20.1` (the Fabric leg's own pin, not Forge's) |
| Fabric, 1.21.4 (Wave 3, GV-17) | 21 | `fabric-loom` `1.17-SNAPSHOT` | Fabric API `0.119.4+1.21.4` |
| NeoForge, 1.21.4 (Wave 3, GV-17) | 21 | MDG `2.0.147` | NeoForge `21.4.157` |
| Fabric, 1.21.5 (Wave 3, GV-17) | 21 | `fabric-loom` `1.17-SNAPSHOT` | Fabric API `0.128.2+1.21.5` |
| NeoForge, 1.21.5 (Wave 3, GV-17) | 21 | MDG `2.0.147` | NeoForge `21.5.98` |
| Fabric, 1.21.8 (Wave 3, GV-17) | 21 | `fabric-loom` `1.17-SNAPSHOT` | Fabric API `0.136.1+1.21.8` |
| NeoForge, 1.21.8 (Wave 3, GV-17) | 21 | MDG `2.0.147` | NeoForge `21.8.54` |

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

**Third correction, live-verified (GV-15, 2026-09-21): the Forge 1.20.1 leg's SRG refmap proof.**
GV-5/6/7 landed the three real `@Mixin` classes this leg needed; this ticket built the leg alone
(`./gradlew :1.20.1-forge:build`), inspected the jar, and proved the mixin fires against a real
Forge 1.20.1 server, not just against a dev-environment deobfuscated runtime -- the distinction the
ticket itself was scoped to close.

- **Jar inspection**: `grounded_villages-forge-0.1.0+1.20.1.jar`'s `META-INF/MANIFEST.MF` carries
  `MixinConfigs: grounded_villages.mixins.json`; the jar contains `grounded_villages.refmap.json`
  (4.1KB); its `mappings`/`data.searge` tables carry real SRG (`m_...`) targets for every
  Minecraft-side member the three mixins touch -- `JigsawStructure.findGenerationPoint`
  (`m_214086_`), `JigsawPlacement.addPieces` (`m_227238_`), `JigsawPlacement$Placer
  .tryPlacingChildren` (`m_227264_`), and `PoolElementStructurePiece.addJunction` (`m_209916_`).
  JDK/`java.util` redirect targets (`Deque.addLast`, `List.add`) correctly stay unmapped -- they
  are not Minecraft members, so SRG has nothing to rename. `legacyForge`'s own `parchment {}` block
  is absent by design (this mod uses no Parchment parameter-name layer); the compiled-against
  mappings are plain Mojang-over-official, matching the platform matrix's own row above.
- **A real, load-bearing bug found and fixed**: `grounded_villages.mixins.json` (the shared config
  under `src/main/resources`, referenced by all three loaders) shipped with no `"refmap"` key.
  Compile-time this is invisible -- MDG's `mixin { add(...) }` DSL passes the AP its output path
  directly (`-AoutRefMapFile=...`), independent of that JSON key, so `:1.20.1-forge:build` and
  `chiseledCheck` were green either way. At **runtime** it is fatal: SpongePowered Mixin's config
  loader falls back to no refmap at all when `"refmap"` is unset, so on a real SRG-obfuscated
  server it tried to match the literal source name (`findGenerationPoint`) against SRG-renamed
  bytecode and failed outright --
  `InvalidInjectionException: ... could not find any targets matching 'findGenerationPoint' in
  ...JigsawStructure. No refMap loaded.` -- crashing the dedicated server at mixin-apply time, every
  time, on the production jar. Fixed by adding `"refmap": "grounded_villages.refmap.json"` (and a
  `"minVersion": "0.8"`, missing for the same reason and separately warned about) to
  `grounded_villages.mixins.json`. Confirmed harmless on the other two loaders per this note's own
  refmap table above: NeoForge only logs "Reference map could not be read" for a stale/absent
  refmap key (Create#8742, Iris#2782 precedent already cited there), and Fabric's Loom ≥1.5 ignores
  the AP-based refmap mechanism entirely (Tiny Remapper rewrites the jar directly). This is why a
  dev-environment `runServer` boot on this leg is not sufficient proof by itself for this specific
  bug class: MDG legacyforge's dev runtime is Mojang-named (deobfuscated), so the un-remapped
  literal name matches directly and the missing refmap key never manifests there -- only a real,
  installer-built, SRG-named production server exercises the actual failure mode. Both are recorded
  below since both were run live.
- **Dev-environment proof** (`./gradlew :1.20.1-forge:runServer`, `-Dmixin.debug.verbose=true`):
  boots and (as expected for MDG legacyforge's Mojang-named dev runtime) never exercised the
  missing-refmap bug, since names already match without remapping.
- **Production proof** (the actual SRG-runtime proof this ticket asks for): a plain Forge 1.20.1
  server built from the official installer (`forge-1.20.1-47.4.23-installer.jar`, 8.6MB, fetched
  from `maven.minecraftforge.net` in under 2 seconds; `--installServer` completed in 27.7s and
  pulled ~160MB of libraries, including the real SRG-named `server-1.20.1-...-srg.jar`), our built
  jar dropped into its `mods/`, booted under Java 17 with `eula=true`. Before the refmap fix: fatal
  `MixinApplyError` at world load, server never reaches "Done". After the fix: clean boot,
  `mixin.env.refMapRemappingEnv : <searge>` printed in the verbose Mixin banner (direct
  confirmation this runtime is the SRG one), and all three `Mixing village.<X>Mixin ... into
  net.minecraft...` apply lines present:
  ```
  [mixin/]: Mixing village.JigsawStructureMixin ... into ...structures.JigsawStructure
  [mixin/]: Mixing village.JigsawPlacementMixin ... into ...pools.JigsawPlacement
  [mixin/]: Mixing village.PlacerMixin ... into ...pools.JigsawPlacement$Placer
  ```
  With `-Dgrounded_villages.debug=true`, the full hook chain fires on this production server for
  real villages found near spawn and via `/locate structure minecraft:village_plains` +
  `/forceload`: `JigsawStructureMixin#findGenerationPoint fired: village-tagged`, `TierRoller
  fired: TierAssignment[tier=..., jigsawDepth=..., maxDistance=...]`, `VillageStartHook fired:
  Vanilla[]`, `PieceGate fired: accept/reject building|street (WATER|HEIGHT_DEVIATION)`, `PieceLadder
  fired: shrink/unaffected/relabelled ...` -- the same site/piece/tier decision chain GV-5/6/7/8
  proved on Fabric, now proven against real SRG bytecode. Seed 1's located village (`[640, ~, 816]`,
  matching the Fabric baseline's own start position exactly) rolled `VILLAGE`, then the ladder
  shrank it (`shrink (27 non-street, 6 rejected)`) and relabelled it `hamlet` -- the same final tier
  and the same non-street-rejected count (6 = 3 water + 3 height) as
  `docs/baseline/grounded-26.2-fabric-10-seeds-all.json`'s own seed-1 row. Seed 2's located village
  (`[-416, ~, 240]`, again matching Fabric's own start position) came back `unaffected` (0
  rejected), consistent with Fabric's seed-2 row also showing 0/0 rejected. See
  `docs/baseline/README.md` "Forge proof" for the full session log excerpts and timings.
- **The `Cannot remap addPieces(...)` warning GV-8-era context flagged**: not reproduced. Tried,
  live: a single clean `:1.20.1-forge:build`; a full clean `chiseledBuild chiseledCheck --continue`
  across all six nodes (the genuine `--parallel` case); and two literally concurrent
  `:1.20.1-forge:build --rerun-tasks --no-daemon` processes racing on the same worktree. All three
  came back clean, zero `Cannot remap` lines, and the Stonecutter-generated per-node source
  (`versions/1.20.1-forge/build/generated/stonecutter/main/java/.../JigsawPlacementMixin.java`)
  correctly comments out both inactive-version `@ModifyVariable`/`@Redirect` branches, leaving only
  the `<1.21` descriptors active for this node -- so there is no stray, wrongly-active annotation
  with a mismatched descriptor to trigger it either. The most likely remaining explanation is a
  cross-worktree race on the shared, content-addressed `~/.gradle/caches/neoformruntime` cache (the
  only place this leg's `officialToSrg`/merged `.tsrg` mappings live outside the project's own
  `build/`) between two *separate* worktrees both building `:1.20.1-forge` at once -- plausible
  given this ticket's own siblings (GV-11/12/13) run in parallel worktrees of the same repo, though
  NFRT's own per-artifact `.lock` files are designed to serialize exactly that case. Recorded here
  rather than silently patched, per the ticket's own instruction to find the cause: no fix is
  applied because the cause could not be pinned down to anything actually wrong in this leg's
  build configuration or mixin descriptors, and inventing one against a bug that would not
  reproduce risks masking whatever the real, narrower trigger is.
- **NOTICE re-verified, not assumed**: `raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.20.x/LICENSE.txt`
  read live 2026-09-21, confirms LGPL-2.1 in the file text itself ("licensed under the terms of the
  LGPL 2.1"). Note for future verifiers: GitHub's own API `license` field (`GET
  /repos/MinecraftForge/MinecraftForge`) currently returns `{"key": "other", "spdx_id":
  "NOASSERTION"}` for this repository -- GitHub's licence auto-detector does not classify it, so the
  API field GV-2 used successfully for `neoforged/NeoForge` is not reliable for this one repo; the
  file text is authoritative and was read directly instead.

## Wave 3 nodes (GV-17, chosen and verified live 2026-09-21)

**The two live feeds, read directly, not from either research note** (neither named a curated
list -- `decisions/DEC-004-versions-and-toolchain.md`'s own decision rule required this ticket to
check live):

- **Fabric API**: `GET https://api.modrinth.com/v2/project/fabric-api/version` (1202 entries), the
  `game_versions` field on each entry. Every 1.21.x point release from 1.21.1 through 1.21.11 has
  at least one Fabric API release -- no gaps on the Fabric side at all in this range.
- **NeoForge**: `GET https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge`
  (1202 versions). NeoForge's own version scheme embeds the Minecraft minor directly
  (`21.<mc-minor>.<build>`, e.g. `21.4.157` for 1.21.4): grouping the 422 `21.*` releases by that
  middle segment shows real NeoForge builds only for minors 0, 1, 3, 4, 5, 8, 10 and 11 -- **1.21.2,
  1.21.6, 1.21.7 and 1.21.9 never got a NeoForge release at all**, confirmed by the complete absence
  of any `21.2.*`/`21.6.*`/`21.7.*`/`21.9.*` version string in the live feed, not merely an
  unlucky sample.

**Candidates with both, per Minecraft's own release-date feed** (`GET
https://api.modrinth.com/v2/tag/game_version`, `date` field per version) grouped into "drops" (a
feature release followed immediately by its own point-release patches): 1.21.2+1.21.3 (final point
with NeoForge: **1.21.3**, 2024-10-23), 1.21.4 solo (2024-12-03), 1.21.5 solo (2025-03-25),
1.21.6+1.21.7 (no NeoForge release for either -- the whole drop is disqualified), 1.21.8 solo
(2025-07-17), 1.21.9+1.21.10 (final point with NeoForge: **1.21.10**, 2025-10-07), 1.21.11 solo
(2025-12-09, closing out the 1.21.x line before 26.1). Six qualifying final points in total:
1.21.3, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11.

**Picking three of six, on adoption and spread**: Modrinth's own faceted search
(`GET /v2/search?facets=[["versions:<v>"]]`, `total_hits`) as a live adoption proxy --

| Version | Modrinth projects supporting it |
|---|---|
| 1.21.3 | 45,049 |
| 1.21.4 | **53,123** |
| 1.21.5 | 48,757 |
| 1.21.8 | 50,178 |
| 1.21.10 | 46,509 |
| 1.21.11 | 52,467 |

1.21.4 and 1.21.5 are unambiguous: highest adoption of the early/mid candidates and each its own
solo drop (no lower-adoption sibling point release competing for the same slot), a real four-month
spread against Wave 1's own 1.21.1. For the third slot, 1.21.8 was picked over 1.21.10 and 1.21.11:
1.21.8 outdrew 1.21.10 on adoption (50,178 vs 46,509 projects) and stayed the current point for 2.5
months before 1.21.9 (2025-07-17 to 2025-09-30) against 1.21.10's own two months before 1.21.11
(2025-10-07 to 2025-12-09) -- both "how long-lived" signals the ticket's own instructions named
point the same way, to 1.21.8. 1.21.11 was passed over even though its adoption count is close
behind 1.21.4's: it sits only ~3.5 months before 26.1 (2025-12-09 to 2026-03-24), the least
independent coverage of the six candidates, and 1.21.8 already sits at a well-spread middle point
between 1.21.5 and 26.2 that 1.21.10/1.21.11 would only crowd.

**Coordinates, verified by direct HTTP request against their own Maven, per GV-2's own discipline**
(not carried over from the feed JSON unverified):

| Coordinate | Verified as | How |
|---|---|---|
| Fabric API, 1.21.4 | `0.119.4+1.21.4` | `maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml` lists it; `fabric-api-0.119.4+1.21.4.jar` HTTP 200 |
| Fabric API, 1.21.5 | `0.128.2+1.21.5` | same metadata file; jar HTTP 200 |
| Fabric API, 1.21.8 | `0.136.1+1.21.8` | same metadata file; jar HTTP 200 |
| NeoForge, 1.21.4 | `21.4.157` | `maven.neoforged.net/releases/net/neoforged/neoforge/21.4.157/neoforge-21.4.157-universal.jar` and `.pom` both HTTP 200 |
| NeoForge, 1.21.5 | `21.5.98` | same, `21.5.98` universal jar + pom HTTP 200 |
| NeoForge, 1.21.8 | `21.8.54` | same, `21.8.54` universal jar + pom HTTP 200 |

**Nodes added the way GV-2 added Wave 1's** (`settings.gradle.kts`'s own `match()` helper,
`stonecutter.properties.toml`'s per-version/per-loader sections) -- `./gradlew projects` confirms
all twelve nodes register: `1.21.4-fabric`, `1.21.4-neoforge`, `1.21.5-fabric`, `1.21.5-neoforge`,
`1.21.8-fabric`, `1.21.8-neoforge` alongside the six from Waves 1–2.

**The hook re-verification this ticket's own acceptance criteria require, per-version, not
assumed**: `javap -p` against the real Loom-merged jar for each of the three new versions (already
cached locally from prior sibling-ticket builds in this fleet's shared `~/.gradle`, so no fresh
Minecraft download was needed to inspect them) --

- `JigsawPlacement.addPieces`'s public overload, `JigsawStructure.maxDistanceFromCenter`'s field
  type, `Placer.tryPlacingChildren`'s signature, and `PoolElementStructurePiece`'s constructor are
  **byte-for-byte identical in shape across 1.21.4, 1.21.5 and 1.21.8** to 1.21.1's own (the
  existing `elif <26.1` mixin branch): plain `int maxDistanceFromCenter` (no `MaxDistance` record
  on any of the three -- the record's real introduction point is therefore later than 1.21.8, not
  merely "between 1.21.2 and 1.21.9" as the jigsaw research note guessed), the same
  `PoolAliasLookup`/`DimensionPadding`/`LiquidSettings`-bearing `addPieces` overload, the same
  `SequencedPriorityIterator`-based placing queue. No new mixin branch needed for any of these.
- **One real, previously-unrecorded delta found**: `RegistryAccess.registryOrThrow` -- the call
  `JigsawStructureMixin#gv$gateOnVillageTag` makes to resolve `Registries.STRUCTURE` -- is already
  gone on 1.21.4 (confirmed missing by `javap -p net.minecraft.core.RegistryAccess`, only the
  `lookupOrThrow` overloads present), and gone the same way on 1.21.5 and 1.21.8. GV-5 had only
  1.20.1/1.21.1/26.2 jars to check and reasonably (but wrongly) assumed this rename landed at the
  same 26.1 boundary `JigsawPlacement`'s own parameter-list changes do; it did not -- first
  confirmed live build failure was `:1.21.4-fabric:compileJava`, `error: cannot find symbol
  registryOrThrow(ResourceKey<Registry<Structure>>)`. Fixed by moving
  `JigsawStructureMixin`'s own Stonecutter condition from `//? if <26.1` to `//? if <1.21.2` (the
  exact removal point between 1.21.2 and 1.21.3 is not pinned -- this ladder targets neither -- but
  it is no later than 1.21.4, confirmed). This is the one mixin source change Wave 3 needed.

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

### Version deltas relevant to the mixin (source: same note, "Version deltas" table; 1.21.4/1.21.5/1.21.8 columns added by GV-17's own live `javap`)

| Item | 1.20.1 | 1.21.1 | 1.21.4 / 1.21.5 / 1.21.8 | 26.2 |
|---|---|---|---|---|
| `JigsawStructure.maxDistanceFromCenter` | plain `int` | plain `int` | plain `int` (all three, confirmed) | `MaxDistance(horizontal, vertical)` record — real switch point is after 1.21.8, not pinned tighter than that (GV-17 narrowed "between 1.21.2 and 1.21.9" down to "later than 1.21.8" by direct `javap`; neither this ladder's own nodes nor either research note pin it exactly) |
| `JigsawPlacement.addPieces` params | no `PoolAliasLookup`/`DimensionPadding`/`LiquidSettings` | adds all three | same as 1.21.1 (confirmed) | same as 1.21.1 |
| `RegistryAccess.registryOrThrow` (used by `JigsawStructureMixin`) | present | present | **gone** (GV-17: confirmed missing by `javap` on all three; the earlier assumption that this rename landed at 26.1 was wrong) | gone |
| Mixin support story | No official support on Forge; mods self-bootstrap SpongePowered Mixin via ModLauncher | Official declarative `[[mixins]]` since NeoForge 20.3 | same as 1.21.1 | same as 1.21.1 |
| `ResourceLocation` class name | `ResourceLocation` | `ResourceLocation` | `ResourceLocation` (confirmed, all three) | renamed `Identifier` at 1.21.11 (26.x ships unobfuscated) |

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
