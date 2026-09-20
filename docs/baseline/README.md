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

## GV-8: tier rolling

`docs/baseline/grounded-26.2-fabric-10-seeds-tiers-only.json` is the same sweep, same 10 seeds,
same 26.2-fabric node, with GV-8's tier roll live and SITE/PIECE still inert (GV-6/7 have not
landed on this branch) -- shipped-default config throughout: `tier.enabled: true`, weights
`30/45/20/5`, depths `3/6/8/10`, max distances `80/96/128/128`, `performance_cap_multiplier: 3.0`.
Generated by `just sweep 10` (`./gradlew :26.2-fabric:seedSweep -Pcount=10`), the same command as
the vanilla baseline above.

| seed | structure | start | pieces | height spread | water fraction | tier | time (ms) |
|---|---|---|---|---|---|---|---|
| 1 | `minecraft:village_plains` | (640, 0, 816) | 130 | 8.0 | 12.9% | village | 5501 |
| 2 | `minecraft:village_plains` | (-416, 0, 240) | 46 | 2.0 | 0.0% | hamlet | 2615 |
| 3 | `minecraft:village_plains` | (256, 0, 848) | 34 | 3.0 | 0.0% | hamlet | 4041 |
| 4 | `minecraft:village_snowy` | (-464, 0, 16) | 94 | 9.0 | 20.7% | village | 3978 |
| 5 | `minecraft:village_plains` | (-208, 0, -464) | 142 | 29.6 | 12.4% | village | 4922 |
| 6 | `minecraft:village_snowy` | (-1280, 0, 1216) | 244 | 30.0 | 0.0% | town | 7534 |
| 7 | `minecraft:village_plains` | (928, 0, -224) | 350 | 30.0 | 0.7% | town | 11085 |
| 8 | `minecraft:village_taiga` | (16, 0, -224) | 39 | 8.0 | 5.5% | hamlet | 1994 |
| 9 | `minecraft:village_plains` | (-192, 0, 800) | 178 | 26.0 | 57.6% | village | 5202 |
| 10 | `minecraft:village_snowy` | (192, 0, 256) | 49 | 5.0 | 0.0% | hamlet | 2467 |

10 seeds: **4 hamlet, 4 village, 2 town, 0 city** (plausible at 10 draws against a 5%-weighted
city -- 0.95^10 ≈ 60% chance of zero city rolls in 10 trials, not itself evidence of a
weighting bug; `TierRollerTest`'s own 10,000-draw distribution test is the real check on the
weighting). Mean piece count **130.6** (up from the vanilla baseline's 117.1 -- expected, since
even the `village` tier's own default `max_distance` (96) is wider than every vanilla biome
file's own value, `village_plains.json`'s 80 included), mean height spread 15.1, mean water
fraction 11.0%. Generation time per seed tracks piece count roughly, not depth directly (seed 7,
a depth-8 town, took 11.1s; seed 1, a depth-6 village, took 5.5s) -- the same "search dominates,
not per-piece sampling" shape the vanilla baseline's own runtime section already found.

### The tier game test (`./gradlew :26.2-fabric:tierGameTest`)

Runs the dedicated server twice against the same fixed seed (default: `seeds.txt`'s first seed)
and diffs the resulting tier -- the "game test on 26.2-fabric that a fixed seed yields a fixed
tier" this ticket's acceptance criteria ask for, reusing GV-10's own live-server mechanism rather
than standing up a second, parallel `net.minecraft.gametest.framework` integration this project
does not have yet (see this file's own "verdict" above). Not wired into `check` -- like
`seedSweep`, two live dedicated-server boots per run is too slow for the fast local/CI loop `just
check` promises. Verified live for this ticket: seed 1 rolled `village` on both runs.

### City generation time -- and a real finding about the performance cap

None of the 10 committed baseline seeds rolled `city` (expected at 5% weight over only 10 draws,
above). To get at least one real `city` generation-time sample, this ticket also swept
`docs/baseline/seeds.txt`'s next 10 seeds (11-20, part of the same checked-in fixed list, just
past `-Pcount=10`'s default cut) -- **not committed as baseline data** (the committed baseline
stays exactly the first 10, matching `TEST-REQ-003`), reported here only for this one number:

**Seed 18** (`minecraft:village_taiga`, depth 10, the `city` tier's own default): **718 pieces**,
generation time **28,619 ms** (~28.6s, the slowest single village measured across every sweep this
ticket or GV-10 ran).

718 pieces against a `performance_cap_multiplier` of 3.0 (nominal cap: 117 × 3.0 = 351
expected pieces) is a real finding worth stating plainly, not glossing over: **the depth-based
performance cap (`TierRoller.capDepth`) did not, and structurally cannot, keep this village under
the nominal cap.** `capDepth` clamps the tier's configured *depth* using a linear
pieces-per-depth estimate calibrated off one reference point (vanilla's own mean at depth 6); at
`city`'s default depth of 10 that estimate is well under the cap (~195 of 351 allowed), so no
clamp fired -- correctly, per the ticket's own instruction ("cap the depth"), but that instruction
converts an *expected* piece count into a depth ceiling, not a guarantee on any one village's
*actual* piece count. Real piece count varies hugely by terrain and available pool pieces,
independent of depth (the vanilla baseline already showed 50-230 pieces at one fixed depth), and
this ticket's own 10-seed tiers-enabled sweep shows piece count growing *faster* than linear with
depth (hamlet/village/town means of 42/136/297 pieces at depths 3/6/8 -- pieces-per-depth-unit
rises from ~14 to ~23 to ~37, not flat). `TierRoller`'s own javadoc now documents this rather than
claiming the linear model is conservative (an earlier draft of that comment incorrectly claimed
so, before this data existed).

**This is flagged for Kevin, not silently patched**: a true hard bound on generation cost would
need counting pieces live during jigsaw assembly and aborting mid-generation -- a materially
different and heavier mechanism than "cap the depth" (this ticket's own instruction), and arguably
its own ticket. `tier.performance_cap_multiplier` as shipped is a best-effort dampener on the
tier's own configured depth budget, not a hard ceiling on any single village's real cost, and
`city`'s default depth (10) can in practice produce a village several times past the nominal cap.
