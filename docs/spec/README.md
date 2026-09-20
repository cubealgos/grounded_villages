---
title: "grounded_villages spec — index"
type: "spec"
category: "grounded_villages"
repo: "grounded_villages"
---

# grounded_villages — "Grounded Villages" — specification

Villages generate on one consistent ground level, dry, and in a genuine range of sizes: a
multi-loader, multi-version Minecraft mod (Fabric, NeoForge, and Forge, 1.20.1 through 26.2) — not a
`create_civilization` building block and not a Create add-on. Kevin's idea, verbatim: "I hate vanilla
village generation: sometimes the village is scattered across immense heights, or some villages
spawn with more than half their buildings and paths on water. I want it to be more realistic,
including more size variety in villages." "For this one I truly want to support as many versions as
possible." (Kevin, 2026-09-20). Placement only — no terrain edits of this mod's own, no new
buildings, no new villager behaviour, no village removal (`00-context.md`).

**Status: ready for bootstrap.** This spec went through three passes, all 2026-09-20: a first draft
written ahead of two pending research notes; a second pass folding in both notes once they landed
(`multi-loader-multi-version-mods-2026.md`'s "Grounded Villages" section for build tooling,
`village-jigsaw-placement-1-20-1-to-26-2.md` for the vanilla hook itself); and a third pass folding in
Kevin's rulings on the open questions the first two passes surfaced — prior art (`DEC-009`), the
`city` tier's shape (`DEC-006` amendment), `TerrainAdjustment`/`beard_thin` (`DEC-005`), and the
shrink/move/vanilla fallback for a village that loses too many pieces (`DEC-010`) — plus a set of
proposed defaults and closures Kevin still needs to confirm at the first ticket, each marked
**"proposed by Claude, 2026-09-20, Kevin to confirm at the first ticket."** No FILL marker remains
anywhere in this spec; nothing below invents a class name, a hook, or a version fact.

| Sheet section | File |
|---|---|
| §1 Document control | this file: identifiers, state, decisions |
| §2 Executive summary and business context | `00-context.md` |
| §3 Product architecture and runtime topology | `04-architecture.md` |
| §4 Domain-driven functional specifications | `01-actors.md`, `02-journeys.md`, `03-glossary.md`, `domains/site.md`, `domains/pieces.md`, `domains/tiers.md`, `domains/config.md` |
| §5 Interface contracts and integration | `contracts/platform-matrix.md`, `contracts/public-surface.md`, `contracts/data-contract.md` |
| §6 Compliance, security and governance | `operations/compliance.md` |
| §7 Release engineering, distribution and support | `operations/release.md`, `operations/testing.md` |
| §8 Migration, compatibility and out of scope | `00-context.md` §What it will not do, `contracts/data-contract.md` |
| Appendix: technical blueprints | `04-architecture.md` §Shape |

## Files and state

| File | Domain prefix | State |
|---|---|---|
| `00-context.md` | — | written; credits prior art, cites the shrink/move/vanilla fallback |
| `01-actors.md` | `ACTORS` | written |
| `02-journeys.md` | `UC` | written |
| `03-glossary.md` | — | written, fully confirmed, no FILL |
| `04-architecture.md` | `ARCH` | written, fully confirmed — hook family, module shape, and toolchain all sourced, no FILL |
| `domains/site.md` | `SITE` | written, mechanism confirmed; proposed defaults filled in (§3, marked for Kevin's confirmation); new `SITE-REQ-006` for the "move" fallback |
| `domains/pieces.md` | `PIECE` | written, mechanism confirmed; `PIECE-FAIL-001` resolved (`DEC-010`, new `PIECE-REQ-006`/`007`); `PIECE-FAIL-002` (dangling connector) remains a first-ticket verification item, not a design question |
| `domains/tiers.md` | `TIER` | written; `TIER-FAIL-003` resolved — `city` is one centre at 1.0 (`DEC-006` amendment); spacing question closed; proposed weights/depths/distances filled in |
| `domains/config.md` | `CONFIG` | written, no FILL; full proposed default JSON added |
| `contracts/platform-matrix.md` | `PLATFORM` | written; Waves 1–2 (six nodes) fully confirmed; Wave 3 resolved as a decision rule (chosen at its own ticket), not enumerated — no FILL |
| `contracts/public-surface.md` | `SURFACE` | written |
| `contracts/data-contract.md` | `DATA` | written |
| `operations/compliance.md` | `COMP` | written |
| `operations/release.md` | `REL` | written, CI/artifact-naming confirmed |
| `operations/testing.md` | `TEST` | written; test-strategy confirmed; the datagen-harness question kept as a proposal with a named verification and fallback (§"Verification for the first ticket") |
| `decisions/DEC-001`–`DEC-010` | `DEC` | written; `DEC-009` (prior art) and `DEC-010` (shrink/move/vanilla) added; `DEC-002`, `DEC-004`, `DEC-005`, `DEC-006`, `DEC-008` updated with rulings; no FILL anywhere |

## Identifiers

`<DOMAIN>-<KIND>-<NNN>`: `SITE-REQ-002`, `PIECE-UC-…` (use cases are flat, see below),
`TIER-FAIL-003`, `ARCH-DEC-002`. Use cases themselves are `UC-NNN`, flat across the project.
Permanent; a withdrawn item keeps its number.

## Verifications

Every row below is a claim this sheet makes that depended on one of the two research notes, a live
check, or a ruling. **17 of 19 are confirmed or resolved by ruling** (rows 2 and 7 partially — real
technical uncertainty, not blocked on a decision). Rows 17 and 19 are the only two still awaiting
first-ticket work rather than a decision: Wave 3's actual point-release numbers (deferred by design to
that ticket) and the datagen-harness feasibility spike.

| # | Claim | Section | Status |
|---|---|---|---|
| 1 | The exact vanilla jigsaw-pipeline hook: `JigsawPlacement.addPieces` (start piece) and `JigsawPlacement$Placer.tryPlacingChildren` (every child piece), mixin-required on all three loaders and every targeted version since no loader exposes a structure-placement event | `04-architecture.md` `ARCH-DEC-001`, `contracts/platform-matrix.md` | **confirmed**, `village-jigsaw-placement-1-20-1-to-26-2.md` §A/§B, read via `javap` against the 26.2 jar |
| 2 | Whether a rejected piece's dangling jigsaw connector is already handled gracefully by vanilla | `domains/pieces.md` `PIECE-FAIL-002` | **partially confirmed** — structurally likely (rejection sits inside vanilla's own existing piece-fits check), not bytecode-traced downstream; same note, not fully resolved |
| 3 | Whether vanilla's jigsaw pipeline natively supports the `city` tier's multiple independent start points | `domains/tiers.md` `TIER-FAIL-003` | **confirmed — no, it does not; resolved by amendment.** `size` is a single-start recursion-depth budget. Kevin's ruling: `city` at 1.0 is one centre at the deepest budget and the 128-block hard radius; multiple centres deferred to a later ticket (`decisions/DEC-006-size-tiers.md` amendment) |
| 4 | The sampling mechanism: `ChunkGenerator.getBaseColumn`/`getFirstFreeHeight` at `WORLD_SURFACE_WG` (water detection) and `OCEAN_FLOOR_WG` (true ground), available at the same mixin injection point already in scope | `domains/site.md` §3, `domains/pieces.md` §3 | **confirmed**, same note §A/§C; exact grid resolution remains a config default, not a research gap |
| 5 | Whether sampling is cheap enough at the hook point this mod uses | `domains/site.md` `SITE-FAIL-003` | **confirmed** — same per-call cost vanilla already pays once per piece; proportionate at 5–9 points per footprint |
| 6 | Vanilla's jigsaw-depth (`size`/`maxDepth`) mechanism | `03-glossary.md` "jigsaw depth" | **confirmed** — a pure recursion-depth budget, not a piece count or area; tiers work by feeding a different `maxDepth` |
| 7 | Whether another worldgen mod hooking the same pipeline stage is a real conflict risk | `04-architecture.md` `ARCH-FAIL-005` | **partially confirmed, still a real risk** — a specific close-precedent mod exists (see the prior-art finding below); bytecode-level mixin coexistence between two mods was not resolved either way |
| 8 | Per-rung toolchain for Waves 1–2: Java 17/1.20.1, 21/1.21.1, 25/26.2; `fabric-loom`; MDG `2.0.147`, `legacyforge` addon for Forge 1.20.1; Fabric API `0.116.17+1.21.1`/`0.161.0+26.2`; NeoForge `21.1.251`/`26.2.0.88`; Stonecutter `0.9.8`; root Gradle `9.5.1`; Mojang mappings everywhere | `contracts/platform-matrix.md`, `04-architecture.md`, `decisions/DEC-004-versions-and-toolchain.md` | **confirmed**, `multi-loader-multi-version-mods-2026.md` "Grounded Villages" §1–2, §B |
| 9 | Whether Forge 1.20.1 fits the same build-tooling shape as cleanly as NeoForge/Fabric do | `04-architecture.md` `ARCH-DEC-002` | **confirmed** — yes, via MDG's `legacyforge` addon, same plugin family as the NeoForge legs |
| 10 | The specific reasoning for "no config library" across all three loaders | `decisions/DEC-008-config-file.md` | **confirmed** — Fabric has no config API, NeoForge's is TOML/NeoForge-only; reinforced independently by the jigsaw note's own config recommendation |
| 11 | Which game-test framework each loader uses, and its actual fitness for this mod's headless N-seed measure | `operations/testing.md` | **confirmed, with a correction** — `GameTest` exists on all three but is not a fit for seed-driven generation statistics; a datagen-modeled harness is recommended instead, flagged as the note's own weakest-evidenced claim |
| 12 | Whether a precompiled `common` module (`villager_voices`' own shape) works here | `04-architecture.md` `ARCH-DEC-002` | **confirmed — reconciled between both notes**: pure logic with zero Minecraft imports can stay a separate unit safely; the mixin classes themselves cannot be precompiled, since each Stonecutter node needs its own Mixin refmap |
| 13 | Whether the proposed Modrinth slug `grounded-villages` is free | `decisions/DEC-002-name.md` | **confirmed free** (HTTP 404), checked live 2026-09-20; no matching CurseForge project found either |
| 14 | Whether close prior art already exists for part of this mod's scope | `decisions/DEC-002-name.md`, `decisions/DEC-009-prior-art.md` | **confirmed — yes, and resolved.** "Improved Village Placement" (Apollounknowndev, MIT, identical version/loader matrix) already does whole-site height-variance rejection; no water check, no size tiers. Kevin's ruling: build independently, no code reuse, credited as prior art, positioned as the superset (`DEC-009`) |
| 15 | `#minecraft:village`'s exact vanilla membership | `03-glossary.md` "the village tag" | **confirmed** — exactly `village_plains`/`desert`/`savanna`/`snowy`/`taiga`, unchanged 1.20.1–26.2 |
| 16 | Real vanilla village spacing/budget numbers (`structure_set` spacing, `max_distance_from_center`, the hard 128-block engine ceiling) | `domains/tiers.md` "Spacing" | **confirmed, and used to close the spacing question** — `spacing: 34` chunks, `separation: 8` chunks (128 blocks), `max_distance_from_center: 80` (village_plains.json default), `MAX_TOTAL_STRUCTURE_RANGE = 128` hard cap. Proposed closure: no spacing change at 1.0, every tier capped at the 128-block hard radius, occasional near-neighbour overlap accepted |
| 17 | The exact 1.21.x point releases between 1.20.1 and 26.2 this mod's Wave 3 targets, and their toolchain | `contracts/platform-matrix.md`, `decisions/DEC-004-versions-and-toolchain.md` | **resolved as a decision rule, not a fixed list**: neither research pass named a curated set, so Wave 3 targets whichever 1.21.x points have both a current Fabric API and NeoForge release, chosen at that wave's own ticket |
| 18 | Whether vanilla's `TerrainAdjustment` (`beard_thin` for village pieces) is inert or itself edits terrain | `decisions/DEC-005-placement-only.md` | **design question closed by Kevin**: `beard_thin` *is* vanilla's own foundation fill and stays as vanilla ships it, regardless of its exact behaviour. **Not traced into bytecode** — kept as a first-ticket verification item, not a blocker |
| 19 | Whether the datagen-modeled headless test harness actually works as described | `operations/testing.md` "Verification for the first ticket" | **kept as the proposal**, per Kevin's ruling — a named verification (V1) with a concrete `runServer`-based fallback if the in-process `GenerationContext` cannot be built in a day's spike |

## Divergences from heimathafen standards

| Standard | Divergence | Recorded in |
|---|---|---|
| `standards/legal/dependency-license-policy.md` | MIT, no CLA | `decisions/DEC-003-licence.md` |
| "no remote unless justified later" | Public on Forgejo under `cubealgos` from the bootstrap, mirrored to GitHub with the issue tracker there | `decisions/DEC-003-licence.md` |
| `standards/marketing/naming-theme.md` (German-maritime house style) | No German-maritime candidate sweep was run. Settled as a standing exception: Minecraft mods in this fleet carry plain descriptive English names by Kevin's own choice each time, seven mods running, `create_brass_compass` `DEC-002` the first instance | `decisions/DEC-002-name.md` |
| No dedicated config library | Hand-rolled JSON only, across six loader nodes; reasoning confirmed — no config mechanism is both present and loader-neutral across Fabric/NeoForge/Forge | `decisions/DEC-008-config-file.md` |
| `villager_voices`' own precedent of a precompiled `common` module | This mod uses one shared `src/main/` source tree instead, compiled fresh per Stonecutter node — required by per-node Mixin refmaps, not a style choice | `04-architecture.md` `ARCH-DEC-002` |

## Decisions

| ID | Decision | State |
|---|---|---|
| `DEC-001` | Distributed product, full spec sheet | written |
| `DEC-002` | Name "Grounded Villages", mod id `grounded_villages`; Modrinth slug `grounded-villages` confirmed free; naming-theme divergence settled as a standing exception; prior-art question resolved in `DEC-009` | written |
| `DEC-003` | MIT, no CLA; public under the cubealgos organisation from the first commit | written |
| `DEC-004` | Version ladder: Fabric+NeoForge 1.21.1 & 26.2 first, then Forge 1.20.1, then Wave 3's 1.21.x points (chosen at that wave's own ticket, not enumerated here); one repository, Stonecutter `0.9.8`, Mojang mappings; Waves 1–2 toolchain fully confirmed | written |
| `DEC-005` | Placement only: site selection plus per-piece rejection, no terrain edits of this mod's own; `TerrainAdjustment`/`beard_thin` is vanilla's own foundation fill and stays as vanilla ships it | written |
| `DEC-006` | Four weighted size tiers — hamlet, village, town, city (rare) — rolled per village from the world seed; **amended**: `city` at 1.0 is one centre at the deepest budget and the 128-block hard radius, multiple centres deferred | written |
| `DEC-007` | Scope is every structure in `#minecraft:village`, vanilla and modded, configurable | written |
| `DEC-008` | One hand-rolled JSON config file, defaults written on first run, no config library; reasoning confirmed | written |
| `DEC-009` | Prior art: build independently, no code reuse; "Improved Village Placement" credited, Grounded Villages positioned as the superset | written |
| `DEC-010` | A village that loses too many pieces to rejection: shrink to `hamlet` if it clears the minimum, else move to a shifted site, else fall back to vanilla — no world loses a village | written |

## Open questions gathered

**Resolved by Kevin's direct ruling, 2026-09-20** — no longer open, listed for traceability:

- Prior art (`decisions/DEC-009-prior-art.md`): build independently, no code reuse, credit "Improved
  Village Placement," position as the superset.
- The `city` tier's shape (`decisions/DEC-006-size-tiers.md` amendment): one centre at 1.0, capped at
  the 128-block hard radius; multiple centres deferred to a later ticket.
- `TerrainAdjustment`/`beard_thin` (`decisions/DEC-005-placement-only.md`): it *is* vanilla's own
  foundation fill and stays exactly as vanilla ships it; its exact carving behaviour is a first-ticket
  verification item, not a design question.
- What happens to a village that loses too many pieces to rejection
  (`decisions/DEC-010-shrink-move-vanilla.md`): shrink to `hamlet` if it clears the minimum, else
  move to a shifted site, else fall back to vanilla.

**Proposed by Claude, 2026-09-20 — genuinely still open, Kevin to confirm at the first ticket:**

- **Every numeric config default** — `domains/config.md`'s full proposed JSON: site height-spread
  (`12` blocks) and water-fraction (`5%`) thresholds, the search radius/step/attempts (`48`/`16`/`8`),
  the per-piece height tolerance (`6` blocks), tier weights (`30`/`45`/`20`/`5`), jigsaw depths
  (`3`/`6`/`8`/`10`), max distances (`80`/`96`/`128`/`128`), the hamlet minimum (`4` pieces), and the
  performance cap (`3×` vanilla) — reasonable starting numbers derived from vanilla's own defaults and
  the 128-block hard ceiling, not yet measured against a real world. **This is the single largest
  remaining confirmation Kevin owes this spec.**
- **The naming-theme standing exception** for Minecraft mods in this fleet (`decisions/DEC-002-name.md`)
  — a policy reading, recorded as settled, but still a proposal rather than Kevin's own words.
  Low-stakes: consistent with seven mods' worth of existing precedent.
- **Spacing** (`domains/tiers.md` "Spacing"): no change to vanilla's own structure spacing at 1.0,
  every tier capped at the 128-block hard radius, occasional near-neighbour overlap for `town`/`city`
  accepted. Low-stakes, but a real design call.
- **Wave 3's decision rule** (`contracts/platform-matrix.md`, `decisions/DEC-004-versions-and-toolchain.md`):
  target whichever 1.21.x points have a current Fabric API and NeoForge release, chosen at that
  wave's own ticket, rather than a fixed list picked now. Low-stakes.
- **The datagen-modeled test harness** kept as the proposal (`operations/testing.md` "Verification
  for the first ticket"), with a named `runServer`-based fallback bounded to a one-day spike.

**Genuinely open, technical, not blocked on Kevin — first-ticket verification items:**

- Whether a rejected piece's dangling jigsaw connector needs explicit handling, or vanilla's own
  existing piece-fits path already covers it (`domains/pieces.md` `PIECE-FAIL-002`).
- Bytecode-level coexistence between this mod's mixin and another mod's — including "Improved Village
  Placement," which likely mixins a similar injection point (`04-architecture.md` `ARCH-FAIL-005`).
- Whether the deferred multiple-centres shape (a second chained jigsaw start ~60 blocks from the
  first, `domains/tiers.md` §7) is the right mechanism once that later ticket is scoped — not designed
  yet, not blocking 1.0.
- Whether to read (not reuse) "Improved Village Placement"'s own source for design ideas
  (`domains/site.md` §7) — minor, low priority.
