# GV-10: the seed-sweep harness

`docs/spec/operations/testing.md` "Verification for the first ticket" (V1) asked one question:
can the headless harness be built in-process, modeled on datagen (`runDatagen`/`GatherDataEvent`),
with no live server at all? This ticket spiked that question, bounded to one day, and shipped the
named fallback instead. This file is the "written verdict" the ticket's acceptance criteria ask
for, plus the actual command, what it measures, how, and the runtime it takes.

## The verdict: datagen-shaped, rejected

**No.** Not "unclear" or "needs more time" -- confirmed by direct disassembly of the real 26.2
`minecraft-merged-deobf` jar (`net.minecraft.server.MinecraftServer`, `net.minecraft.server.Main`,
`net.minecraft.server.WorldLoader`, `net.minecraft.data.Main`), not just by the absence of a found
precedent (the research's own evidence).

`Structure.GenerationContext`'s `StructureTemplateManager` field requires a
`LevelStorageSource.LevelStorageAccess` and a `CloseableResourceManager`. Tracing every call site
that ever constructs a `StructureTemplateManager` in the 26.2 jar turns up exactly one:
`MinecraftServer`'s own constructor, fed by a `WorldStem` that only `WorldLoader.load(...)`
produces. `WorldLoader.load` is not a small utility -- it is the same `CompletableFuture`-chained
bootstrap `net.minecraft.server.Main` (the dedicated server's own entrypoint) uses before it ever
opens a network port: `DedicatedServerSettings`/`DedicatedServerProperties` (full `server.properties`
parsing), `Services` (session/auth plumbing), `PackRepository` and datapack resolution, and a
world-save upgrade path (`forceUpgrade`, `LevelStorageSource.LevelStorageAccess`). `net.minecraft.server.Main.class`
alone disassembles to ~30KB of bytecode across that one bootstrap sequence.

Datagen's own entrypoint (`net.minecraft.data.Main`) never touches any of this -- disassembling it
turns up zero references to `StructureTemplateManager`, `ResourceManager`, or `ChunkGenerator`
anywhere. It only ever produces a `HolderLookup.Provider` for JSON-dumping data providers. This is
exactly the gap `docs/spec/operations/testing.md` named ("a `GenerationContext` also needs a
`StructureTemplateManager` over a real `ResourceManager` for the `.nbt` pieces, the part datagen
never touches") -- now confirmed precisely, rather than inferred from an absent precedent.
Reimplementing `WorldLoader.load`'s bootstrap by hand, correctly, for a "plain `main` on the dev
classpath" was judged well outside this ticket's one-day bound. The fallback was shipped instead.

## The fallback: `runServer` per seed

A Fabric-node-only (26.2-fabric) dedicated server, booted once per seed with that seed's own
`level-seed` in a throwaway `server.properties`/`eula.txt`, and a dev-only hook
(`grounded_villages.harness.SeedSweepCommand`, registered only when
`FabricLoader.getInstance().isDevelopmentEnvironment()` is true) that runs once the server has
fully started, finds the first village near spawn, samples it, writes one JSON file, and halts the
server. No stdin, no log-text parsing -- the harness writes and reads a file
(`docs/spec/operations/testing.md`'s own fallback wording: "Assert on the file, not on log text").

### What is measured, and how

Exactly `docs/spec/domains/site.md` §3's definitions, not a re-derivation of them:

- **Height spread**: the 90th-minus-10th-percentile of sampled ground heights at
  `Heightmap.Types.OCEAN_FLOOR_WG` (true ground, sees through water), via
  `ChunkGenerator.getBaseHeight` -- the same pre-placement column query `JigsawPlacement` itself
  uses, not a read of blocks the game has already placed.
- **Water fraction**: the share of sampled columns where `WORLD_SURFACE_WG`'s height disagrees with
  `OCEAN_FLOOR_WG`'s, confirmed against the actual block state via `ChunkGenerator.getBaseColumn`
  (`liquid()`) -- `site.md`'s "direct water detection" leg, so a snow layer or similar non-water,
  non-motion-blocking block doesn't get miscounted as water.
- **Sampling grid**: every structure piece's bounding box (`StructureStart.getPieces()`), sampled on
  a 4-block grid across its X/Z footprint.
- **Village lookup**: `ServerLevel.findNearestMapStructure(StructureTags.VILLAGE, spawnPos, 100,
  false)` (vanilla's own `/locate` default radius, in chunks) to find the start chunk, then the
  start chunk is forced to generate to `ChunkStatus.FULL` (the whole jigsaw graph resolves
  synchronously within that one chunk's own `STRUCTURE_STARTS` step --
  `village-jigsaw-placement-1-20-1-to-26-2.md` §A/§C -- so every piece's real, terrain-adjusted
  bounding box exists after that one chunk generates, even pieces whose own chunk hasn't). The
  actual `StructureStart` is then read via a tag-filtered, chunk-scoped
  `StructureManager.startsForStructure` lookup -- not a 3D point match against the position
  `findNearestMapStructure` returns, whose own Y is `village_plains.json`'s inert
  `start_height: {"absolute": 0}` codec field, not a real height.
- **Tier**: always `null` today -- `docs/spec/domains/tiers.md`'s tier system is GV-8's, not yet
  implemented. The field exists in the JSON and the table so GV-8 only has to fill it in, not add
  it.

### The exact command

```
just sweep <N>                                          # first N seeds of seeds.txt (default 10)
./gradlew :26.2-fabric:seedSweep -Pcount=20              # same thing, explicit
./gradlew :26.2-fabric:seedSweep -Pseeds=1,2,3           # ad hoc seeds, not the checked-in list
```

`seedSweep` loops seeds sequentially: for each one it re-invokes `./gradlew :26.2-fabric:runServer`
as its own subprocess (a fresh run directory under `build/seedsweep/run-<seed>/`, a freshly
allocated free TCP port so concurrent sibling-worktree `runServer` sessions on the same machine
never collide with this one), waits for that seed's JSON to land in the node's own
`build/seedsweep/seed-<seed>.json`, then hands every seed's file to `tools/seed_sweep_report.py`,
which prints the table below and writes the combined JSON (`build/seedsweep/report.json`).

`docs/baseline/seeds.txt` is the fixed, checked-in seed list (`TEST-REQ-003`): plain small integers
1-20, so every `-Pcount=N` run (N &le; 20) is the same N seeds release over release.
`-Pseeds=...` is for ad hoc, non-reproducible exploration outside that list.

### Runtime

10 seeds took **5m 27s** end to end (~33s/seed average; per-seed cost ranged 20-45s, dominated by
JVM/Mixin cold-start and one fresh world's spawn-area chunk generation each time, not by the
sampling itself -- the harness's own measured sampling+lookup time per village, logged in each
result's `runtimeMillis`, was 5-27 seconds. runtimeMillis was disproportionately larger on seeds
whose nearest village was further from spawn (more chunks to force through `STRUCTURE_STARTS`
before the start chunk itself was reached; seed 7 also skewed high, see below) -- the search itself,
not the per-piece sampling, dominates that number).

### Unit tests

`SeedSweepStats` (percentiles, height spread, water fraction) is pure -- no Minecraft imports --
and unit-tested on synthetic samples by a hand-rolled runner (`SeedSweepStatsTest`, no JUnit
dependency added for this ticket): **20/20 passed**, wired into `check`
(`./gradlew :26.2-fabric:harnessStatsTest`, part of `chiseledCheck`).

## Baseline: 10 vanilla seeds

`docs/baseline/vanilla-26.2-fabric-10-seeds.json` is this table's raw data, committed so GV-6/7/8
(site selection, per-piece rejection, tiers) diff their own before/after numbers against it rather
than re-measuring vanilla from scratch. Grounded Villages was installed but inert for this run --
none of SITE/PIECE/TIER exist yet, so this is vanilla behaviour end to end.

| seed | structure | start | pieces | height spread | water fraction | tier |
|---|---|---|---|---|---|---|
| 1 | `minecraft:village_plains` | (640, 0, 816) | 84 | 8.0 | 16.4% | - |
| 2 | `minecraft:village_plains` | (-416, 0, 240) | 80 | 5.0 | 2.4% | - |
| 3 | `minecraft:village_plains` | (256, 0, 848) | 103 | 12.0 | 0.0% | - |
| 4 | `minecraft:village_snowy` | (-464, 0, 16) | 105 | 11.0 | 46.0% | - |
| 5 | `minecraft:village_plains` | (-208, 0, -464) | 230 | 20.0 | 7.2% | - |
| 6 | `minecraft:village_snowy` | (-1280, 0, 1216) | 50 | 23.0 | 0.0% | - |
| 7 | `minecraft:village_plains` | (928, 0, -224) | 141 | 25.0 | 0.9% | - |
| 8 | `minecraft:village_taiga` | (16, 0, -224) | 114 | 7.0 | 1.5% | - |
| 9 | `minecraft:village_plains` | (-192, 0, 800) | 151 | 26.0 | 53.4% | - |
| 10 | `minecraft:village_snowy` | (192, 0, 256) | 113 | 6.7 | 0.0% | - |

10 seeds: mean height spread **14.4** (max 26.0), mean water fraction **12.8%** (max 53.4%,
seed 9), mean piece count **117.1**.

Read against `docs/spec/domains/site.md` §3's proposed defaults (`site.max_height_spread: 12`,
`site.max_water_fraction: 0.05`) -- Kevin's own open question at "confirm at the first ticket": on
this 10-seed sample, height spread already exceeds 12 in 5 of 10 villages and water fraction
already exceeds 5% in 3 of 10, so the proposed defaults would reject roughly half of vanilla's own
first-village-near-spawn placements outright. That is exactly the shape SITE's bounded search
(`site.search_radius`/`site.search_attempts`) exists to absorb, but it is worth GV-6 re-reading
`site.md` §7's open question against these numbers before locking the defaults in, rather than
this ticket deciding it inline.
