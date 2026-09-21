# Changelog

## [1.0.0] - 2026-09-21

The first release: villages generate on one consistent ground level, dry, with a genuine range of
sizes, across twelve Minecraft version/loader combinations. Placement only — no terrain edits of
this mod's own.

### Added

- **Site scoring and relocation, before any piece exists** (GV-6). Every candidate village location
  is sampled across its whole radius for ground-height spread and water fraction; a candidate that
  fails either threshold triggers a bounded search for a better spot nearby, inside the same
  vanilla-defined placement cell.
- **Per-piece water and height rejection, streets included** (GV-7). Every individual building or
  street segment is checked for water in its own footprint and for how far its ground height
  deviates from the village's start height — streets get no exemption from either check.
- **The shrink → move → vanilla fallback ladder** (GV-7, `DEC-010`). No village is ever deleted for
  a bad site: pieces lost to rejection first shrink the village in place (relabelled `hamlet` if it
  still clears the hamlet minimum); if that falls short, the whole village retries at a shifted
  site; if no shifted site does better either, it falls back to vanilla's own unchecked placement.
- **Seed-deterministic size tiers: hamlet, village, town, city** (GV-8, `DEC-006`). Each village
  rolls one of four tiers deterministically from the world seed, setting the piece-depth budget and
  maximum radius vanilla's own jigsaw assembly is allowed to use, bounded by an overall performance
  cap regardless of tier.
- **The config file and every default** (GV-9), written to `config/grounded_villages.json` on first
  launch, validated with clamp-and-warn behaviour rather than crashing on a malformed value; no
  in-game or live reload at this release — a config edit takes effect on the next server start.
  Every default below is confirmed by Kevin (GV-27); the `city` tier's jigsaw depth is `9`, dropped
  from the originally proposed `10` after a seed-18 harness measurement rolled a depth-10 city at
  718 pieces in 28.6s:

  | Key | Default |
  |---|---|
  | `scope.structure_tags` | `["minecraft:village"]` |
  | `site.enabled` | `true` |
  | `site.max_height_spread` | `12` blocks |
  | `site.max_water_fraction` | `0.05` (5%) |
  | `site.search_radius` | `48` blocks |
  | `site.search_step` | `48` blocks |
  | `site.search_attempts` | `4` |
  | `piece.enabled` | `true` |
  | `piece.max_height_deviation` | `6` blocks |
  | `tier.enabled` | `true` |
  | `tier.weights.hamlet` / `.village` / `.town` / `.city` | `30` / `45` / `20` / `5` |
  | `tier.jigsaw_depth.hamlet` / `.village` / `.town` / `.city` | `3` / `6` / `8` / `9` |
  | `tier.max_distance.hamlet` / `.village` / `.town` / `.city` | `80` / `96` / `128` / `128` blocks |
  | `tier.hamlet_minimum_pieces` | `4` |
  | `tier.performance_cap_multiplier` | `3.0` (of vanilla's own piece count for that structure) |

- **Twelve supported combinations, all new in this release, none remaining `planned`**
  (`docs/spec/contracts/platform-matrix.md`): Fabric 1.20.1, Forge 1.20.1, Fabric 1.21.1, NeoForge
  1.21.1, Fabric 1.21.4, NeoForge 1.21.4, Fabric 1.21.5, NeoForge 1.21.5, Fabric 1.21.8, NeoForge
  1.21.8, Fabric 26.2, NeoForge 26.2 (GV-2, GV-15, GV-17, GV-24).
- The Modrinth listing body, project settings and icon (GV-13, GV-23).
- Unit tests for site scoring, per-piece rejection, tier rolling and config validation; the
  headless seed-sweep harness with a fixed, checked-in ten-seed list; per-loader game tests
  confirming the mixin hook coexists with vanilla placement without mutating a piece it accepts
  (GV-10, GV-11).

### Measured effect, ten seeds

Headless seed-sweep harness against the fixed ten-seed list checked into this repo
(`docs/baseline/`), comparing the nearest village to spawn with the mod fully enabled against the
same ten seeds with the mod inert (Fabric, 26.2):

| Metric | Vanilla (mean) | With Grounded Villages (mean) |
|---|---|---|
| Height spread | 14.4 blocks | 6.6 blocks |
| Water fraction | 12.8% | 5.5% |
| Piece count | 117 | 52 |

Outcomes across the ten: 8 villages shrank (pieces rejected, kept as a hamlet), 1 was entirely
unaffected, and 1 fell back to vanilla placement outright (the single worst seed for water, whose
bounded site search was already exhausted before per-piece rejection ever ran). These numbers come
from ten seeds on one version/loader combination and should be read as a directional result, not a
statistical guarantee for every world.

### Known limitations

- The `city` tier's jigsaw depth (`9`, dropped from `10` for 1.0 — GV-27, after a seed-18 harness
  run rolled a depth-10 city at 718 pieces in 28.6s) and the 128-block max distance are a
  piece-*budget* estimate fed to vanilla's own jigsaw assembly, not a hard bound on the final piece
  count or footprint — the realised size still depends on what vanilla's assembly and per-piece
  rejection actually produce for a given site. A live, hard piece-count bound is deferred to a
  later ticket.
- A village whose surviving pieces still fall short of the hamlet minimum after a shifted-site
  retry falls back to vanilla's own unchecked placement rather than disappearing — on genuinely
  difficult terrain it can still generate scattered or partly wet, exactly as vanilla would have
  placed it.
- NeoForge 26.2 has no game-test coverage at this release: NeoForge's `RegisterGameTestsEvent` was
  rewritten around 26.2's new registry-driven `GameTestInstance` model with no bridge shipped yet
  (`docs/spec/operations/testing.md` "V2"); NeoForge game-test coverage runs on 1.21.1-neoforge
  instead, a live node on this mod's own platform matrix. Flagged for whichever ticket next touches
  NeoForge's game-test wiring, not silently dropped.

### Prior art

["Improved Village Placement"](https://modrinth.com/mod/improved-village-placement)
(Apollounknowndev, MIT, the same Fabric/Forge/NeoForge version matrix) already rejects whole
village sites by height variance — the site-selection half of this mod's own scope. Grounded
Villages is built independently, with no code reuse, and credited here as prior art rather than
named as competition: the superset this mod adds is height *plus* water *plus* size tiers,
together, in one mod.
