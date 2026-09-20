---
title: "grounded_villages spec — testing"
type: "spec"
category: "grounded_villages"
---

# Testing (`TEST`)

Test-strategy shape confirmed against `multi-loader-multi-version-mods-2026.md`'s "Grounded
Villages" §6 (added 2026-09-20 after this spec's first draft), with one correction to that first
draft: vanilla `GameTest` is **not a fit for the headless, N-seed statistics measure** — every source
that note read describes `GameTest` loading a pre-built structure template into a bounded test
region, not generating chunks through a real `ChunkGenerator`/structure-set pipeline. The two layers
below are split accordingly, where the first draft had conflated them into one "game tests" row.

| Layer | What | Where |
|---|---|---|
| Unit | Site-selection accept/reject logic given synthetic heightmap/water-fraction inputs; per-piece rejection given synthetic footprint/height inputs; tier rolling's determinism (same seed + position → same tier, every time) and its weighted distribution over many rolls; the config file's default/clamp/fallback behaviour for every malformed-input case in `domains/config.md` §6 — all pure, no Minecraft imports, checked by the shared-source purity check (`04-architecture.md` `ARCH-DEC-002`) | `src/test`, against the shared mixin-adjacent source, no Minecraft classpath needed |
| **Headless harness — the core acceptance measure** | Generate N seeds' worth of villages and measure height spread and water fraction **before and after** this mod is active, over the same seeds — the direct, numeric proof that `00-context.md`'s "one ground level, dry" claim actually holds, not just that the code runs. Also measures the realised tier distribution against configured weights. **Recommended shape**: model the harness on Minecraft's own **datagen** entry point (`runDatagen` on Fabric, `GatherDataEvent` on NeoForge) — both already boot game registries and run `BootstrapContext`-driven worldgen code with no live server or client; a new Gradle task in the same family builds a `ChunkGenerator`+`RandomState` for the registered dimension and calls structure placement directly for N seeds. **Flagged by the research itself as its weakest-evidenced recommendation** — no second precedent beyond datagen's own shape was found; worth a short spike before committing to it. Fallback, not primary: booting a dedicated server per seed (`runServer`/`runGameTest`) and parsing `/locate` output — works, but N seeds means N full registry/asset boots and a fragile command-output assertion channel. | A standalone Gradle task modeled on datagen; CI and local |
| Game tests | Vanilla `GameTest` exists on all three loader families — Fabric (`fabric-gametest-api-v1`/`FabricGameTest`), NeoForge (`@GameTest`/`@GameTestHolder`/`RegisterGameTestsEvent`), Forge 1.20.1 (its own documented gametest mechanism) — confirmed present everywhere this mod targets, but scoped to a **pre-built structure template loaded into a bounded test region**, not seed-driven generation. Used here for what it *is* a fit for: confirming the hook itself fires correctly against a known, pre-built scenario — a pre-placed piece with water in its footprint gets rejected, a rejected piece's neighbour does not end up with a dangling connector, per-piece rejection coexists with vanilla placement without mutating a piece it accepts. **Not used for N-seed statistics** — that is the headless harness's job. | Per loader's own game-test framework, once each node's module lands |
| Manual / release checklist | Generating a fresh test world per shipped combination and confirming, by eye, several villages each show a consistent ground level, no water-crossing pieces or streets, and at least one visibly different-sized village across a reasonable exploration radius | release checklist |
| Development tool | A debug command or world-generation report that lists, for a given seed and region, every village's rolled tier and which candidate sites/pieces were rejected and why — the closest analogue to the siblings' debug commands, and genuinely useful here since a rejection is otherwise invisible (a piece that never generates leaves no trace to inspect after the fact) | `grounded_villages.debug`, `just client`, once client tooling exists |

**What is genuinely hard here, stated plainly:** the accept/reject and tier-roll logic is fully
unit-testable given synthetic terrain inputs and a fixed seed. What is *not* pure-testable is whether
the confirmed hook into vanilla's jigsaw pipeline (`04-architecture.md` `ARCH-DEC-001`) actually fires
at the right stage, with the right data, on a real server world — only a game test per loader/version
confirms that, and only the headless harness confirms the whole-mod claim (fewer scattered, fewer
water-crossed villages) numerically rather than anecdotally.

`TEST-REQ-001`: every `SITE-REQ`, `PIECE-REQ`, `TIER-REQ`, and `CONFIG-REQ` names its test in the
ticket that implements it.
`TEST-REQ-002`: a deliberate-break proof for the shared-source purity check, once.
`TEST-REQ-003`: the headless harness runs against a fixed, checked-in seed list in CI, so its
before/after height-spread and water-fraction numbers are reproducible and comparable release over
release, not just a one-off manual measurement.
`TEST-REQ-004`: a game test proves this mod's hook coexists with vanilla and with a synthetic
terrain-altering test mod without side effects — the hook only ever prevents or relocates a piece,
never mutates one it accepts.
`TEST-REQ-005`: the release checklist is run against every combination in `contracts/platform-matrix.md`
before that combination's own first release, not carried over from another combination's pass.

## Verification for the first ticket

The datagen-modeled harness stays the proposal, not a confirmed shape — the research's own weakest
evidence in this spec. Kevin's ruling: keep it as the proposal, but record it as something to prove,
not assume.

| # | Item to verify | Fallback if it fails |
|---|---|---|
| V1 | Prove at the first ticket that the datagen-modeled harness (`runDatagen`/`GatherDataEvent`, building a `ChunkGenerator`+`RandomState` and calling structure placement directly for N seeds) actually works, specifically whether a `GenerationContext`'s `StructureTemplateManager` can be built without a live server — the gap the research itself could not close | A Fabric-node-only `runServer` booted per seed, with `level-seed` set in `server.properties` and a mod command that reports height spread and water fraction per village by sampling the columns under each placed piece, writing the result to a file rather than parsing log text (`multi-loader-multi-version-mods-2026.md` "Grounded Villages" §6). Bound the spike to one day; if the in-process context cannot be built by then, ship the fallback directly rather than spending further time on the harder shape. |
