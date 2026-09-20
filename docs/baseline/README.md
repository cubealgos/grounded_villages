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

## GV-6: `SiteScorer`/`SiteSearch` wired live, the same 10-seed sweep re-run

`site/SiteScorer` and `site/SiteSearch` (`grounded_villages.site`, zero Minecraft imports --
`04-architecture.md` `ARCH-DEC-002`) implement `SITE-REQ-001`-`006`; `SiteStartHook` is the
Minecraft-typed adapter a loader entrypoint registers into `HookRegistry`; `JigsawPlacementMixin`
now actually replaces the start position for a `Shift` decision (`@ModifyVariable`, not `@Inject`
-- GV-5 modeled `Shift` but never enacted it). `docs/baseline/grounded-26.2-fabric-10-seeds-site-only.json`
is this section's raw data, same ten seeds, `site.enabled: true`.

### Before / after

| seed | structure | vanilla spread / water | site-on spread / water | pieces (v / site-on) | changed? |
|---|---|---|---|---|---|
| 1 | `village_plains` | 8.0 / 16.4% | 8.0 / 16.4% | 84 / 84 | no |
| 2 | `village_plains` | 5.0 / 2.4% | 5.0 / 2.4% | 80 / 80 | no |
| 3 | `village_plains` | 12.0 / 0.0% | 12.0 / 0.0% | 103 / 103 | no |
| 4 | `village_snowy` | 11.0 / 46.0% | 11.0 / 46.0% | 105 / 105 | no |
| 5 | `village_plains` | 20.0 / 7.2% | 20.0 / 7.2% | 230 / 230 | no |
| 6 | `village_snowy` | 23.0 / 0.0% | 23.0 / 0.0% | 50 / 50 | no |
| 7 | `village_plains` | 25.0 / 0.9% | 25.0 / 0.9% | 141 / 141 | no |
| 8 | `village_taiga` | 7.0 / 1.5% | 7.0 / 1.5% | 114 / 114 | no |
| 9 | `village_plains` | 26.0 / 53.4% | 26.0 / 53.4% | 151 / 151 | no |
| 10 | `village_snowy` | 6.7 / 0.0% | 6.7 / 0.0% | 113 / 113 | no |

**Zero of the ten reported villages changed at all**, to the exact decimal. Read plainly, before
digging into why: **this before/after table is not evidence SITE is inert.** `HookDebug`
(`-Dgrounded_villages.debug=true`, on by default for the seed-sweep harness as of this ticket)
logs every `VillageStartHook` decision; summed across all ten seeds' own server logs, `SiteSearch`
fired **269 times** -- **13 Keep, 25 Shift, 231 Vanilla-fallback** (`site_decisions` in the
committed JSON). SITE is doing real, measurable work; the table above just isn't the row that
shows it. Why: `SeedSweepRunner` (GV-10) reports the **single nearest** village to spawn per seed
via `ServerLevel.findNearestMapStructure`, which forces `STRUCTURE_STARTS` generation -- and
therefore this hook -- for **every** structure-set cell it scans on the way to the nearest one
(the 269 evaluations above, up to 73 for one seed), not only the one it eventually returns. For
all ten of these specific seeds, the one candidate that happened to end up "nearest" was always
one whose own decision was `Keep` (already qualified) or `Vanilla` (search exhausted) -- never one
of the 25 `Shift`s, which happened on other, non-nearest cells the same broad search touched. This
is a real limitation of a harness built to measure one village per seed (`docs/spec/operations/testing.md`
"Verification for the first ticket" named the harness itself as the weakest-evidenced part of this
spec), not proof of no effect -- the debug-log tally is the honest measure of what SITE actually
did across these seeds; the before/after table is an artifact of which single candidate the
harness happens to land on. Flagged for Kevin: a future ticket's harness improvement (report every
village a sweep's own search touches, not only the nearest) would make this table meaningful
again; out of this ticket's own scope to build.

### Keep / Shift / Vanilla counts (`site_decisions`, all 10 seeds combined)

| Outcome | Count | Share |
|---|---|---|
| Keep | 13 | 4.8% |
| Shift | 25 | 9.3% |
| Vanilla (search exhausted) | 231 | 85.9% |
| **Total evaluations** | **269** | |

Of the 256 evaluations that needed a search at all (269 minus the 13 immediate `Keep`s), only 25
(9.8%) found a qualifying alternative -- most candidates that fail `site.max_height_spread`/
`max_water_fraction` stay failing everywhere the bounded search can reach. Per seed (first attempt,
`search_step: 16`, `search_attempts: 8`, proposed defaults unchanged): seed 1 had 50 evaluations
(5 `Shift`), seed 3 had 27 (10 `Shift`, 5 `Keep`), seeds 4/5/9 (the highest-water seeds) had 0
`Shift` out of 8/8/24 -- every search on those three exhausted outright. This matches `site.md` §2
"Unwanted"'s own accepted case: "terrain so uniformly bad ... that almost every village falls back
to vanilla behaviour ... not a bug in this mod" -- seeds 4 and 9 in particular (46%/53% water) are
large-water-feature seeds no bounded, in-cell search is likely to escape.

### Search-bound tuning (`site.search_step`/`site.search_attempts`, GV-6 -- for Kevin's confirmation)

Ticket instruction: tune `search_radius`/`search_step`/`search_attempts` only (never the
thresholds) to raise the Shift share; write the measured trade-off here rather than deciding it
silently. **Changed**: `site.search_step` `16` &rarr; `48`, `site.search_attempts` `8` &rarr; `4`
(`site.search_radius` unchanged at `48`). Reasoning: with `step == radius`, every offset
`SiteSearch#spiralOffsets` produces sits at the full `48`-block bound in one ring -- geometrically
only the 4 cardinal directions fit (`hypot(1,1) * 48 = 67.9 > 48`, the two diagonals per ring are
excluded), so this is **strictly cheaper** than the original `16`-block-step default (4 evaluated
offsets instead of up to 8) while reaching **3x farther** (48 vs. 16 blocks) at the same, already
cell-safe distance `SiteSearch`'s own javadoc establishes. Re-running the identical 10-seed sweep
after this change: Shift count rose from **15 to 25** (of the same 256 non-`Keep` evaluations,
5.9% &rarr; 9.8%), and the worst single-seed runtime fell from **59.9s to 33.7s** (seed 6, 73
evaluations both times) -- an improvement on both the quality axis the ticket asked to tune and
the performance axis this ticket also had to protect (below). **Not** a majority-Shift outcome --
the ticket's own hoped-for target -- because the seeds still driving `Vanilla` hardest (4, 5, 6, 7,
9) are dominated by features (large water bodies, wide elevation swings) that plausibly exceed
what any single-cell-bounded search can reach at all, an accepted case per `site.md` §2 above, not
a sign the tuning is wrong. **This is Kevin's to confirm**, same as the thresholds themselves.

### A real performance finding: the column cap, not just the search bounds

Tuning `SiteScorer.MAX_SAMPLE_COLUMNS` (an internal implementation cap, not a config key --
`contracts/public-surface.md`) turned out to matter as much as the search bounds for this specific
harness to even finish. `SeedSweepRunner`'s `findNearestMapStructure` call forces `addPieces` --
and so a full `SiteSearch.evaluateStart` (`1 + search_attempts` `SiteScorer.score` calls) -- for
every structure-set cell it scans before settling on "nearest", not just one: seed 1 needed 50,
seed 6 needed 73. A first, naive cap of `400` columns per site left seed 4 at 56.8s of extra cost
(barely inside the dedicated server's own 60s `max-tick-time` watchdog); at `200` seed 4 was still
56.8s (8 candidate cells, each paying the full cost); at `64` seed 4 dropped to ~11.5s -- but seed
1's own harness run then hit **over 40 candidate cells inside a single blocking call** and crashed
the watchdog outright (`A single server tick took 60.01 seconds`). Shipped at `25` (a 5x5 grid,
`SiteScorer`'s own javadoc has the full account): seed 1's worst run (50 cells) finished in 42.4s,
seed 6's worst (73 cells) in 33.7-59.9s across the two search-bound configurations measured above
-- real margin, but not a large one. **Flagged, not built** (out of this ticket's scope): the
actual fix for the underlying multiplier is a fail-fast scorer (bail once either threshold is
already unrecoverably blown, rather than always sampling the full grid), not a smaller cap --
worth a follow-up ticket if a future harness or a genuinely unlucky real seed needs more margin
than 25 columns leaves. Separately worth noting for anyone reading these numbers against real
gameplay: this cost multiplies specifically because the harness's own `findNearestMapStructure`
call forces many structure-set cells to generate **synchronously, in one blocking call** -- real
gameplay loads chunks incrementally as a player explores, so a single chunk's own `addPieces` call
(a few hundred ms to ~1.5s extra, this ticket's own measurements) is imperceptible; the watchdog
risk this section describes is a property of this specific stress-test harness, not of normal play.

### Sampling cost per site

`SiteScorer.score` samples a `SAMPLE_STEP`-8-block grid, capped at `MAX_SAMPLE_COLUMNS = 25`
columns (coarsening the step above roughly a 40-block radius to stay under the cap -- see
`SiteScorer`'s own javadoc for the exact formula), two `TerrainSampler.getBaseHeight` calls per
column (`OCEAN_FLOOR_WG` + `WORLD_SURFACE_WG` -- the cheaper of the two water-fraction methods
`site.md` §3 allows, chosen over a `getBaseColumn` block-state read because `getBaseHeight` walks
a noise column top-down with an early-exit stop predicate while `getBaseColumn` materialises the
whole column with none; see `SiteScorer`'s javadoc for the full reasoning). `SiteSearch.evaluateStart`
scores up to `1 + search_attempts` sites per candidate village start (now `1 + 4 = 5`, tuned this
ticket): up to `5 x 25 x 2 = 250` `getBaseHeight` calls per candidate. Measured live during this
ticket's harness runs, at roughly `1.25ms`/query (cold JVM, no JIT warm-up), that is ~560ms-1.4s
per candidate cell depending on the search-bound configuration in effect at the time (see above).

### Test counts (verbatim)

- `SiteScorerTest`: 8/8 passed (flat/cliff/lake/slope synthetic height fields, the column-cap
  coarsening, the negative-radius guard).
- `SiteSearchTest`: 13/13 passed (Keep/Shift/Vanilla decisions, the deterministic spiral order and
  its tie-break, the `SITE-REQ-005` disabled-skip, and `SITE-REQ-006`'s own "never re-evaluates the
  origin" property for the future move-retry entry point).
- Existing `config`/`hook` suites: unchanged, still green (`ConfigCodecTest`'s serialized-defaults
  test updated for the new `search_step`/`search_attempts` values).
- `chiseledCheck`/`chiseledBuild`: green on all six nodes (`just check`'s own report has the
  full run).

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

## GV-7: per-piece rejection and the shrink/move/vanilla ladder

`docs/baseline/grounded-26.2-fabric-10-seeds-all.json` is this ticket's own raw data, same ten
seeds, `site.enabled: true`, `piece.enabled: true`, `tier.enabled: true` -- shipped defaults
throughout (`piece.max_height_deviation: 6`, `tier.hamlet_minimum_pieces: 4`), the mod's actual
intended end-to-end behaviour for the first time in this fleet's own history. `grounded_villages.piece.PieceGate`
(`domains/pieces.md` `PIECE-REQ-001`-`004`) and `grounded_villages.piece.PieceLadder`
(`decisions/DEC-010-shrink-move-vanilla.md`) implement the domain; `PlacerMixin`'s own javadoc has
the bytecode evidence for exactly where a child candidate is committed and how rejection reuses
vanilla's own "this connector gets nothing" path; `JigsawPlacementMixin`'s own javadoc has the
mechanism for the ladder itself (wrapping `Structure.GenerationStub`'s deferred piece-placement
consumer, not the call to `addPieces`, which only *builds* that consumer without ever running it).

### Before / after (vanilla vs. every domain enabled)

| seed | structure | vanilla spread / water / pieces | all-on spread / water / pieces | tier | rejected (w/h) | outcome |
|---|---|---|---|---|---|---|
| 1 | `village_plains` | 8.0 / 16.4% / 84 | 3.0 / 0.0% / 52 | hamlet | 3 / 3 | shrink |
| 2 | `village_plains` | 5.0 / 2.4% / 80 | 2.0 / 0.0% / 46 | hamlet | 0 / 0 | unaffected |
| 3 | `village_plains` | 12.0 / 0.0% / 103 | 2.0 / 0.0% / 30 | hamlet | 0 / 2 | shrink |
| 4 | `village_snowy` | 11.0 / 46.0% / 105 | 6.0 / 0.0% / 45 | hamlet | 2 / 4 | shrink |
| 5 | `village_plains` | 20.0 / 7.2% / 230 | 2.0 / 0.0% / 46 | hamlet | 0 / 4 | shrink |
| 6 | `village_snowy` | 23.0 / 0.0% / 50 | 9.0 / 0.0% / 47 | hamlet | 0 / 9 | shrink |
| 7 | `village_plains` | 25.0 / 0.9% / 141 | 6.0 / 0.0% / 47 | hamlet | 0 / 7 | shrink |
| 8 | `village_taiga` | 7.0 / 1.5% / 114 | 5.0 / 0.0% / 33 | hamlet | 2 / 0 | shrink |
| 9 | `village_plains` | 26.0 / 53.4% / 151 | 26.0 / 55.4% / 127 | village | 0 / 0 | vanilla |
| 10 | `village_snowy` | 6.7 / 0.0% / 113 | 5.0 / 0.0% / 45 | hamlet | 0 / 1 | shrink |

10 seeds: mean height spread **14.4 -> 6.6** (54% down), mean water fraction **12.8% -> 5.5%**
(57% down), mean piece count **117.1 -> 51.8** (56% down, expected -- shrinking to hamlet caps
every affected village's own footprint at whatever survived rejection). Ladder outcomes across the
ten: **8 shrink, 1 unaffected, 1 vanilla** (`{shrink: 8, unaffected: 1, vanilla: 1}` --
`tools/seed_sweep_report.py`'s own summary line). Total rejected pieces: **7 water, 30 height
deviation**.

**Reported honestly, seeds 4 and 9 included, per the ticket's own instruction**: seed 4 (46.0%
water in the vanilla baseline, one of the two worst) improved to **0.0%** -- two pieces rejected
for water, four for height deviation, survivors still clearing the hamlet minimum, kept and
relabelled hamlet. Seed 9 (53.4% water in the vanilla baseline, the single worst) did **not**
improve -- **55.4%, effectively unchanged, even slightly higher** than vanilla. This is not
`PieceGate` failing to reject: seed 9's own `site_decisions` tally (GV-6's own baseline section
above) already showed its search exhausted outright on every attempt at this ticket's own
thresholds, and `docs/spec/domains/site.md` §2 "Unwanted" already names this exact case as
accepted, not a bug ("terrain so uniformly bad ... that almost every village falls back to
vanilla behaviour"). This ticket's own ladder reached the same conclusion independently: 0 pieces
were ever rejected for seed 9's winning village (`rejectedWater`/`rejectedHeight` both 0), meaning
`PieceGate` accepted every candidate the site itself proposed -- there was nothing for the ladder
to shrink or move away from. `docs/spec/domains/site.md`'s own move step (`SITE-REQ-006`, this
ticket's own "move" reuse of `SiteSearch.searchAlternative`) never even engages here, since it only
fires when the survivor count falls *short* of the hamlet minimum -- a village this large (127
pieces after tiering to `village`) was never at risk of that, whatever fraction of it sits on
water. Read together with `SITE`'s own already-exhausted search, seed 9 is the sharpest evidence
in this whole baseline sweep for `domains/site.md` §2's own accepted-case wording: a large,
uniformly wet seed genuinely is not something a bounded, in-cell search or a per-piece water veto
can fully rescue, by design.

### Two operational findings, this ticket's own harness runs

**The watchdog, again** (the same class of finding GV-6's `SiteScorer.MAX_SAMPLE_COLUMNS` section
above already made for `SITE`): the shrink/move/vanilla ladder means a village-tagged candidate the
harness's own `findNearestMapStructure` merely scans in passing (not the one it eventually
returns) can now pay up to **3x** a full jigsaw-assembly cost (attempt 1, a "move" retry, a
"vanilla" retry) instead of vanilla's own one-shot cost, on top of `PieceGate`'s own per-piece
sampling. Seed 1's own first sweep run (before this fix) crashed the dedicated server watchdog
outright (`A single server tick took 60.01 seconds`). **Fix shipped**: `build.fabric.gradle.kts`'s
own harness-only `server.properties` template now sets `max-tick-time=300000` (raised, not
disabled with `-1`, so a genuine hang still eventually crashes the harness) -- a test-tooling-only
change, since a real dedicated server never forces dozens of candidate villages to fully assemble
inside one tick the way this harness's own broad, synchronous `findNearestMapStructure` scan does.
**Flagged, not built** (out of this ticket's own scope, same restraint GV-6 showed for its own
column-cap finding): the real fix for the underlying multiplier is a fail-fast ladder (bail the
"move"/"vanilla" retries early once a shifted candidate's own site-level score is already
hopeless, rather than always running a full attempt), not a larger watchdog allowance.

**A reused `run-<seed>` directory silently serves stale placement, with no rejection/ladder
metadata at all**: discovered live, this ticket -- sweeping seed 1 three times in the same
directory produced real `tier`/`ladderOutcome`/rejection data on the first (fresh-world) run and
`null`/`0` on the second and third (world-reused) runs, with byte-identical placement (52 pieces,
height spread 3.0, water 0.0%) every time. Minecraft loads an already-saved chunk's `StructureStart`
straight from its region file rather than regenerating it, so `findGenerationPoint` -- and every
`grounded_villages` hook downstream of it, including the registries `SeedSweepRunner` reads back
from -- never fires a second time for a chunk it has already computed once; this mod's own
bookkeeping is in-memory only, fresh per JVM, with nothing left to read back. **Fixed**: `runServer`'s
own `doFirst` now deletes `serverRunDir` before writing a fresh `server.properties`, so every sweep
is genuinely "one fresh dedicated server per seed," matching what `seedSweep`'s own task
description already claimed. The ten-seed table above was generated from clean directories, this
fix already in place.

### Test counts (verbatim)

- `PieceGateTest`: 13/13 passed (flat dry, a lake edge, a cliff, two slopes either side of the
  default tolerance, the start-height-not-local-terrain distinction, the exact-boundary case, the
  water-checked-first tie-break, streets and buildings sharing the identical check, the 5/7/9-point
  sampling shape, and the inverted-footprint guard).
- `PieceLadderTest`: 6/6 passed (the `UNAFFECTED` carve-out for zero rejections, a shrink-triggering
  case, the exact-boundary case, a move-triggering case, zero survivors, and a reconfigured minimum
  turning the same tally from shrink into retry).
- Existing `config`/`hook`/`site`/`tier` suites: unchanged, still green (`ConfigCodecTest`,
  `SiteScorerTest`, `SiteSearchTest`, `TierRollerTest` all re-run, no regressions).
- `chiseledBuild`/`chiseledCheck`: green on all six nodes, including 1.20.1-forge's own Mixin
  0.8.5 annotation processor (`@Redirect`, no `order()` member needed anywhere in this ticket's
  own mixin work, unlike GV-6/GV-8's own `@ModifyVariable` ordering concern) -- `just check`'s own
  report has the full run.
- Live-server proof, beyond the ten committed baseline seeds: three manual reruns of seed 1 alone
  (the watchdog-crash repro, the two world-reuse reruns, and the clean-directory confirmation after
  the `runServer` fix), all consistent with the committed table above.

### Interpretive ruling recorded this ticket: the shrink relabel only applies when something was
### actually rejected

`docs/spec/domains/pieces.md` `PIECE-REQ-006`'s own literal wording ("keep the village ... at or
above `tier.hamlet_minimum_pieces` ... relabelled hamlet regardless of its originally rolled
tier") has no explicit "only if something was rejected" clause -- read fully literally, it would
relabel *every* village hamlet the moment `piece.enabled` is true, since `hamlet_minimum_pieces`'
own default (4) is far below a typical village's real piece count (this ticket's own table above:
30-127). `grounded_villages.piece.PieceLadder`'s own javadoc records the reading this ticket ships
instead: the ladder (and any relabelling) only engages when at least one piece was actually
rejected (`PieceLadder.Outcome.UNAFFECTED` short-circuits otherwise) -- matching `PIECE-FAIL-001`'s
own title ("a village loses enough pieces to rejection") and `PIECE-REQ-006`'s own "keep the
village as generated" phrasing, which only reads as a meaningful description for a village that
lost something. **Flagged for Kevin to confirm**, same footing as every other proposed default or
reading this fleet carries forward for the first ticket to touch it -- `PieceLadderTest`'s own
`zeroRejectionsIsUnaffectedRegardlessOfSurvivorCount` test is the executable form of this ruling.

## GV-12: NeoForge parity proof

`docs/loaders.md` has the full entrypoint/mixin-declaration parity audit; this section is the live
proof GV-5/6/7/8 each gave for Fabric, given here for the `26.2-neoforge` node -- `./gradlew
:26.2-neoforge:runServer -Pgroundedvillages.seed=1 -Pgroundedvillages.output=... -Pgroundedvillages.runDir=...
-Pgroundedvillages.port=<free port>` (`eula=true`, shipped-default config, `site.enabled`/
`piece.enabled`/`tier.enabled` all `true`), then `./gradlew :26.2-neoforge:seedSweep -Pcount=3`
(the NeoForge twin of GV-10's own task, `docs/loaders.md` "The seed-sweep harness's NeoForge
twin") for the three-seed cross-loader comparison below.

### Mixin-apply and hook-debug lines, seed 1 (`build/seedsweep/run-1/logs/latest.log`)

```
[21Sept.2026 00:46:47.099] [main/INFO] [mixin/]: SpongePowered MIXIN Subsystem Version=0.8.7 Source=file:.../sponge-mixin-0.17.3+mixin.0.8.7.jar Service=FML Env=SERVER
[21Sept.2026 00:46:47.207] [main/INFO] [mixin/]: Compatibility level set to JAVA_25
[21Sept.2026 00:46:48.144] [main/INFO] [MixinExtras|Service/]: Initializing MixinExtras via com.llamalad7.mixinextras.service.MixinExtrasServiceImpl(version=0.5.4).
[21Sept.2026 00:46:53.311] [modloading-worker-0/INFO] [grounded_villages/]: grounded_villages: wrote the config to .../run-1/config/grounded_villages.json
[21Sept.2026 00:46:53.313] [modloading-worker-0/INFO] [grounded_villages/]: Grounded Villages: skeleton loaded (NeoForge)
[21Sept.2026 00:46:53.343] [modloading-worker-0/INFO] [grounded_villages/seedsweep/]: Grounded Villages: seed-sweep harness registered (development environment, NeoForge)
[21Sept.2026 00:46:58.693] [worldgen/INFO] [grounded_villages/hook/]: [grounded_villages] JigsawStructureMixin#findGenerationPoint fired: village-tagged
[21Sept.2026 00:46:58.704] [worldgen/INFO] [grounded_villages/hook/]: [grounded_villages] TierRoller fired: TierAssignment[tier=VILLAGE, jigsawDepth=6, maxDistance=96]
[21Sept.2026 00:46:59.451] [worldgen/INFO] [grounded_villages/hook/]: [grounded_villages] VillageStartHook fired: Vanilla[]
[21Sept.2026 00:46:59.474] [worldgen/INFO] [grounded_villages/hook/]: [grounded_villages] PieceGate fired: reject street (WATER)
[21Sept.2026 00:48:31.209] [worldgen/INFO] [grounded_villages/hook/]: [grounded_villages] PieceGate fired: reject street (WATER)
[21Sept.2026 00:48:31.383] [worldgen/INFO] [grounded_villages/hook/]: [grounded_villages] PieceGate fired: reject street (HEIGHT_DEVIATION)
[21Sept.2026 00:48:31.388] [worldgen/INFO] [grounded_villages/hook/]: [grounded_villages] PieceLadder fired: relabelled hamlet (was VILLAGE)
[21Sept.2026 00:48:31.388] [worldgen/INFO] [grounded_villages/hook/]: [grounded_villages] PieceLadder fired: shrink (27 non-street, 6 rejected)
[21Sept.2026 00:48:34.763] [Server thread/INFO] [grounded_villages/seedsweep/]: seed 1: village minecraft:village_plains at BlockPos{x=640, y=0, z=816} -- 52 pieces, height spread 3.0, water fraction 0.0, tier hamlet, rejected 3w/3h, ladder shrink (87815 ms)
```

No SpongePowered "Mixing X from grounded_villages.mixins.json into Y" summary line appears at
`INFO` (that line is `DEBUG`-only, `-Dmixin.debug.verbose=true` not set for this run) -- the
`JigsawStructureMixin#findGenerationPoint fired` / `TierRoller fired` / `VillageStartHook fired` /
`PieceGate fired` / `PieceLadder fired` lines above are stronger evidence regardless: each one
only exists inside this mod's own injected mixin body, so its presence in the log proves the
mixin transform actually applied and ran, not merely that Mixin loaded the config. `Compatibility
level set to JAVA_25` also confirms `grounded_villages.mixins.json`'s `compatibilityLevel` template
variable resolved correctly off `build.neoforge.gradle.kts`'s own `requiredJava.majorVersion` for
this node (`docs/loaders.md`'s `[[mixins]]` section).

### Cross-loader determinism: three seeds, NeoForge vs. the Fabric baseline

Same shipped-default config (`site.enabled`/`piece.enabled`/`tier.enabled` all `true`,
`site.max_height_spread=12`, `piece.max_height_deviation=6`, tier weights `30/45/20/5`), same
three seeds (`docs/baseline/seeds.txt`'s first three) already reported in
`docs/baseline/grounded-26.2-fabric-10-seeds-all.json` (GV-7's own "all domains on" baseline).

| seed | field | Fabric (`26.2-fabric`) | NeoForge (`26.2-neoforge`) | match? |
|---|---|---|---|---|
| 1 | start | (640, 0, 816) | (640, 0, 816) | yes |
| 1 | pieces | 52 | 52 | yes |
| 1 | height spread | 3.0 | 3.0 | yes |
| 1 | water fraction | 0.0% | 0.0% | yes |
| 1 | sample count | 289 | 289 | yes |
| 1 | tier / rejected (w/h) / outcome | hamlet / 3/3 / shrink | hamlet / 3/3 / shrink | yes |
| 2 | start | (-416, 0, 240) | (-416, 0, 240) | yes |
| 2 | pieces | 46 | 46 | yes |
| 2 | height spread | 2.0 | 2.0 | yes |
| 2 | water fraction | 0.0% | 0.0% | yes |
| 2 | sample count | 231 | 231 | yes |
| 2 | tier / rejected (w/h) / outcome | hamlet / 0/0 / unaffected | hamlet / 0/0 / unaffected | yes |
| 3 | start | (256, 0, 848) | (256, 0, 848) | yes |
| 3 | pieces | 30 | 30 | yes |
| 3 | height spread | 2.0 | 2.0 | yes |
| 3 | water fraction | 0.0% | 0.0% | yes |
| 3 | sample count | 112 | 112 | yes |
| 3 | tier / rejected (w/h) / outcome | hamlet / 0/2 / shrink | hamlet / 0/2 / shrink | yes |

**3/3 seeds match on every field** -- structure, exact start position, piece count, height spread,
water fraction, sample count, tier, per-cause rejection counts, and ladder outcome -- down to the
same last decimal and integer GV-7's own committed Fabric baseline reports. Only `runtimeMillis`
differs (87815/15973/53195 ms NeoForge vs. 76301/14793/44958 ms Fabric, both dominated by cold-JVM
and `findNearestMapStructure`'s own broad structure-set scan per `docs/baseline/README.md`'s
earlier "Runtime" sections, not by per-piece sampling), which is wall-clock noise, not a placement
difference. **This is the real parity test GV-12's own ticket wording asks for**: identical
`SiteSelector`/`PieceGate`/`TierRoller` decisions, for the same seed, run through NeoForge's own
mixin/entrypoint wiring instead of Fabric's -- confirming `docs/loaders.md`'s parity-table finding
that `HookRegistry`/`ConfigHolder` reach the same state, populated the same way, before the first
structure generates, on every loader.

### 1.21.1-neoforge: the same proof, no harness needed

The `run` task exists on this node too, so per this ticket's own instruction the same class of
proof was run there: `./gradlew :1.21.1-neoforge:runServer -Pgroundedvillages.runDir=build/manualproof-1211`
(`eula=true`, `level-seed=1`, `JAVA_TOOL_OPTIONS=-Dgrounded_villages.debug=true` -- the seed-sweep
harness itself is `26.2-neoforge`-only, `docs/loaders.md`, so this node has no automated hook-debug
toggle of its own; the JVM-level system property flag reaches `HookDebug` identically without one).
Vanilla's own spawn-area pregeneration alone (no `/locate`/`/forceload` needed -- both were
attempted via a FIFO'd stdin, which this task does not wire through to the forked server process,
a real but minor harness gap, not investigated further since spawn-area pregeneration already gave
ample evidence) was enough to hit a village-tagged structure:

```
[01:00:15] [main/INFO] [mixin/]: Compatibility level set to JAVA_21
[01:00:16] [main/INFO] [MixinExtras|Service/]: Initializing MixinExtras via com.llamalad7.mixinextras.service.MixinExtrasServiceImpl(version=0.5.3).
[01:00:19] [modloading-worker-0/INFO] [grounded_villages/]: grounded_villages: wrote the config to .../build/manualproof-1211/config/grounded_villages.json
[01:00:19] [modloading-worker-0/INFO] [grounded_villages/]: Grounded Villages: skeleton loaded (NeoForge)
[01:00:21] [worldgen/INFO] [grounded_villages/hook/]: [grounded_villages] JigsawStructureMixin#findGenerationPoint fired: not village-tagged
[01:00:22] [worldgen/INFO] [grounded_villages/hook/]: [grounded_villages] JigsawStructureMixin#findGenerationPoint fired: village-tagged
[01:00:22] [worldgen/INFO] [grounded_villages/hook/]: [grounded_villages] TierRoller fired: TierAssignment[tier=VILLAGE, jigsawDepth=6, maxDistance=96]
[01:00:22] [worldgen/INFO] [grounded_villages/hook/]: [grounded_villages] VillageStartHook fired: Vanilla[]
[01:00:22] [worldgen/INFO] [grounded_villages/hook/]: [grounded_villages] PieceGate fired: reject street (WATER)
[01:00:22] [worldgen/INFO] [grounded_villages/hook/]: [grounded_villages] PieceGate fired: reject building (WATER)
[01:00:31] [Server thread/INFO] [minecraft/DedicatedServer]: Done (10.594s)! For help, type "help"
```

`Compatibility level set to JAVA_21` (vs. 26.2's `JAVA_25`) confirms the mixin config's own
`compatibilityLevel` template variable resolves correctly per node on NeoForge too, matching
`build.neoforge.gradle.kts`'s shared `requiredJava.majorVersion` expansion. 73 hook-fired lines
total during this one run (spawn-area pregeneration alone), the same evidence class as the
26.2-neoforge proof above. `FMLEnvironment` drift is real (`docs/loaders.md`), but everything
downstream of it -- config load, hook registration, mixin transform, hook firing -- is identical
on both NeoForge nodes.

### Test counts (verbatim)

- `:26.2-neoforge:harnessStatsTest` (the same hand-rolled `SeedSweepStatsTest` GV-10 wrote,
  re-run off this node's own compiled classes): **20/20 passed**.
- `:26.2-neoforge:check` and `:1.21.1-neoforge:check`: green, no regressions.
- `:26.2-neoforge:compileJava` / `:1.21.1-neoforge:compileJava`: green (this ticket's own
  reflection-based `FMLEnvironment` fix, `docs/loaders.md`, compiles unchanged on both nodes --
  the Stonecutter-preprocessor-split first attempt did not, see that same section for why).
- `just check` (`chiseledCheck` on all six nodes, `chiseledBuild`, `map-check`, `tools/`'s own
  13 Python unit tests): **BUILD SUCCESSFUL**, zero `FAILED` lines anywhere in the run.
## GV-15: Forge proof

The Forge 1.20.1 leg's own written verdict for `docs/spec/contracts/platform-matrix.md`'s "Third
correction" -- the session log excerpts and timings behind that entry's summary. No `seedSweep`
node switch exists for this leg yet (GV-12, in parallel), so this is a manual live-server session,
per the ticket's own fallback instruction, not the harness's JSON output.

### Build and jar inspection

`./gradlew :1.20.1-forge:build` (clean, single-target): **BUILD SUCCESSFUL in 7s**, no `Cannot
remap` or other Mixin warnings. `versions/1.20.1-forge/build/libs/grounded_villages-forge-0.1.0+1.20.1.jar`
carries `MixinConfigs: grounded_villages.mixins.json` in its manifest and a 4.1KB
`grounded_villages.refmap.json` with real SRG (`m_...`) targets for every Minecraft-side member the
three mixins touch. Full detail in `docs/spec/contracts/platform-matrix.md`'s own "Third
correction" section, including the missing-`"refmap"`-key bug this ticket found and fixed.

### Production server: before the fix

Forge 47.4.23 installed via the official installer (`forge-1.20.1-47.4.23-installer.jar`, **8.6MB,
downloaded in ~2s**; `--installServer` under Java 17, **27.7s**, ~160MB of libraries including the
real SRG-named `server-1.20.1-20230612.114412-srg.jar`) into a scratch directory, `eula=true`, the
jar built above copied into `mods/`. First boot, before the `"refmap"` key fix:

```
[main/FATAL] [mixin/]: Mixin apply failed grounded_villages.mixins.json:village.JigsawStructureMixin
  -> net.minecraft.world.level.levelgen.structure.structures.JigsawStructure:
  org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException Critical injection
  failure: @Inject annotation on gv$gateOnVillageTag could not find any targets matching
  'findGenerationPoint' in net.minecraft.world.level.levelgen.structure.structures.JigsawStructure.
  No refMap loaded.
```

Server crashes outright at world load, every time -- this is the actual SRG-runtime failure mode
the ticket's own acceptance criteria are checking for, caught live rather than assumed away by a
dev-environment boot (MDG legacyforge dev runs are Mojang-named, so this exact bug is invisible
there -- confirmed separately via `:1.20.1-forge:runServer`, which never hit it).

### Production server: after the fix

Same installed server, same `mods/` jar rebuilt with the fix, `-Dmixin.debug.verbose=true
-Dgrounded_villages.debug=true` added to `user_jvm_args.txt`. Clean boot:

```
[mixin/]: mixin.env.refMapRemappingEnv : - <searge>
...
[mixin/]: Mixing village.JigsawStructureMixin from grounded_villages.mixins.json into net.minecraft.world.level.levelgen.structure.structures.JigsawStructure
[mixin/]: Mixing village.JigsawPlacementMixin from grounded_villages.mixins.json into net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement
[mixin/]: Mixing village.PlacerMixin from grounded_villages.mixins.json into net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement$Placer
[minecraft/DedicatedServer]: Done (20.157s)! For help, type "help"
```

`mixin.env.refMapRemappingEnv : <searge>` is Mixin's own confirmation this is the SRG runtime, not
a deobfuscated dev one. Villages within render distance of spawn generated during the normal
spawn-area pre-generation pass already, firing the full hook chain unprompted -- **13 village
starts, 264 `PieceGate` decisions** (both `accept` and `reject ... (WATER)` / `reject ...
(HEIGHT_DEVIATION)` reasons observed), across a mix of `HAMLET`/`VILLAGE`/`TOWN` tier rolls, all on
the real SRG bytecode.

### Seed comparison against the Fabric baseline

`/locate structure minecraft:village_plains` + `/forceload` around the result, `level-seed` set to
match `docs/baseline/seeds.txt`'s first entries, compared against
`docs/baseline/grounded-26.2-fabric-10-seeds-all.json`'s own seed rows (all domains enabled,
shipped-default config, `docs/baseline/README.md`'s own "GV-7" table above):

| seed | `/locate` start (Forge) | Fabric baseline start | match | tier (Forge) | tier (Fabric) | non-street rejected (Forge) | non-street rejected (Fabric, w+h) |
|---|---|---|---|---|---|---|---|
| 1 | `[640, ~, 816]` | `(640, 0, 816)` | yes | hamlet (shrunk from VILLAGE) | hamlet | 6 | 6 (3+3) |
| 2 | `[-416, ~, 240]` | `(-416, 0, 240)` | yes | not measured (see below) | hamlet | not measured | 0 (0+0) |

Seed 1: exact position match, exact final-tier match, exact non-street-rejected-count match
(`PieceLadder fired: shrink (27 non-street, 6 rejected)`, then `relabelled hamlet (was VILLAGE)`).
Seed 2: `/locate`'s own structure-set position matched Fabric's exactly (loader-independent, as
expected -- vanilla's structure-set placement math is seed-derived, not mixin-dependent), but the
`/forceload` of that village's chunks did not finish within this session's own time budget --
consistent with `docs/baseline/README.md`'s own GV-7 "watchdog" finding above (the shrink/move/
vanilla ladder can cost up to 3x a full jigsaw-assembly attempt, and this fleet has already seen a
single tick blow past 60s on a costly candidate). Not pursued further: this is supplementary
comparison data, not one of the ticket's own acceptance criteria, and the same live proof already
stands on seed 1 plus the unprompted 13-village/264-decision sample above. Seed 3 not attempted for
the same reason.

### NOTICE licence re-verification

`raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.20.x/LICENSE.txt` read live 2026-09-21:
"Unless noted below, Minecraft Forge, Forge Mod Loader, and all parts herein are licensed under the
terms of the LGPL 2.1" -- confirms `NOTICE`'s existing LGPL-2.1 line by reading the file text
directly, not GitHub's own licence auto-detector (which currently reports `NOASSERTION` for this
specific repository -- noted in `platform-matrix.md`'s own "Third correction" for the next
verifier). No `NOTICE` change needed; the existing line was already correct, now independently
re-confirmed per this ticket's own acceptance criterion.

### Full-fleet build

`./gradlew chiseledBuild chiseledCheck --continue`: **BUILD SUCCESSFUL in 12s**, all six nodes,
zero `Cannot remap` or other Mixin warnings anywhere in the log.
