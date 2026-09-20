# Modrinth listing (paste-ready)

## Project settings

| Field | Value |
|---|---|
| Name | Grounded Villages |
| Slug | `grounded-villages` |
| Summary | Villages generate on one consistent ground level, dry, and in a genuine range of sizes — no more houses scattered across a cliff or streets running through a lake. |
| Categories | Worldgen, Game-Mechanics |
| Licence | MIT |
| Client side | Unsupported |
| Server side | Required |
| Loaders | Fabric, NeoForge, Forge |
| Game versions | 1.20.1, 1.21.1, 26.2 |
| Dependencies | Fabric API (required) |
| Icon | not yet made — a separate ticket generates it with the fleet's navy-badge tool; `docs/modrinth/icon.png` does not exist until then |
| Links | Source `https://git.cubealgos.de/cubealgos/grounded_villages` · Issues `https://github.com/cubealgos/grounded_villages/issues` |

## Version settings

| Field | Value |
|---|---|
| Version number | `0.1.0+1.21.1-fabric` |
| Version title | Grounded Villages 0.1.0 for Fabric 1.21.1 |
| Channel | Alpha |
| File | `dist/grounded_villages-1.21.1-fabric-0.1.0.jar` |
| Changelog | `CHANGELOG.md` |

Placeholder for the first of six Wave 1–2 publish invocations (`GV-19`/`GV-20`/`GV-21` fill in the
real per-node version numbers, jars and overrides); not a claim that this version exists yet — see
`CHANGELOG.md`'s own "Unreleased" section.

## Body

Vanilla village generation places each piece independently: nothing stops one house from landing
on a cliff edge two dozen blocks above its neighbour, or a street from running straight across a
lake. Grounded Villages fixes both problems, and adds real size variety on top — placement only,
no terrain edits of its own.

### What it does

- **One ground level, within a tolerance.** Every piece in a village — building or street — sits
  close to the same height the village started at, not scattered across whatever height each
  piece's own patch of terrain happened to be.
- **Dry.** No piece's footprint sits in water: no house floor and no street segment runs through a
  lake or a river.
- **Compact.** A village that fails its site check searches nearby for a better one; a piece that
  fails its own check is simply not placed, rather than the whole village sprawling to accommodate
  bad terrain.
- **Sizes that vary.** Four tiers — hamlet, village, town, and a rare city — rolled once per
  village, deterministically from the world seed, so the next village you find is a real surprise
  in scale as well as location.

### How

- **Site scoring and relocation, before any piece exists.** Every candidate village location is
  sampled across its whole radius for ground-height spread and water fraction. A candidate that
  fails either check triggers a bounded search for a better spot nearby, inside the same
  vanilla-defined placement cell; if the search is exhausted without finding one, the village falls
  back to vanilla's own unchecked placement rather than being deleted.
- **Per-piece rejection, as each piece is placed.** Every individual building or street segment is
  checked for water in its own footprint and for how far its ground height deviates from the
  village's start height. Streets get no exemption — the same two checks vanilla's own
  terrain-matching street pieces already had a terrain query at.
- **Seed-deterministic size tiers.** Before pieces generate, each village rolls one of four tiers
  (hamlet/village/town/city), which sets the piece-depth budget and maximum radius vanilla's own
  jigsaw assembly is allowed to use, bounded by an overall performance cap regardless of tier.
- **A village is never deleted for a bad site.** If per-piece rejection leaves too few pieces, the
  village first shrinks in place (kept, relabelled `hamlet`); if that still falls short, it retries
  at a shifted site; if no shifted site does better either, it falls back to vanilla's own
  unchecked placement — in that order, always ending in *some* village rather than none.

### What it does not do

- **No terrain edits of its own**, beyond vanilla's own per-piece foundation fill — no flattening
  pass, no lake filling, no terraforming.
- **No new buildings.** Every piece this mod places or rejects is a piece vanilla or a datapack
  author already defined.
- **No new villager behaviour.** Villager AI, trades, professions and schedules are entirely
  untouched — this mod only decides where and how large the structure they live in gets to be.
- **No village removal.** A village that cannot find a qualifying site or loses too many pieces to
  rejection shrinks, moves, or falls back to vanilla — never generates nothing at all.

### Measured effect, ten seeds

Measured with a headless seed-sweep harness against the fixed ten-seed list checked into this
repo (`docs/baseline/`), comparing the nearest village to spawn with the mod fully enabled against
the same ten seeds with the mod inert (pure vanilla):

| Metric | Vanilla (mean) | With Grounded Villages (mean) |
|---|---|---|
| Height spread | 14.4 blocks | 6.6 blocks |
| Water fraction | 12.8% | 5.5% |
| Piece count | 117 | 52 |

Outcomes across the ten: 8 villages shrank (pieces rejected, kept as a hamlet), 1 was entirely
unaffected, and 1 fell back to vanilla placement outright — the single worst seed for water
(53.4%), whose bounded site search was already exhausted before per-piece rejection ever ran, an
accepted case for terrain this uniformly difficult rather than a bug.

**Caveat**: these numbers come from ten seeds on one version/loader combination (Fabric, 26.2) and
should be read as a directional result, not a statistical guarantee for every world.

### Configuration

Written to `config/grounded_villages.json` with every key below on first launch. Plain JSON, no
in-game reload at this release — an edit takes effect on the next server start. Every value is
retunable without touching Java.

| Key | Default | What it does |
|---|---|---|
| `scope.structure_tags` | `["minecraft:village"]` | Which structure tags this mod acts on. |
| `site.enabled` | `true` | Whether whole-site selection runs at all. |
| `site.max_height_spread` | `12` blocks | Site-level ground-height-spread threshold a candidate must clear. |
| `site.max_water_fraction` | `0.05` (5%) | Site-level water-fraction threshold a candidate must clear. |
| `site.search_radius` | `48` blocks | How far from the original candidate the relocation search may look. |
| `site.search_step` | `48` blocks | Spacing between the offsets the relocation search samples. |
| `site.search_attempts` | `4` | How many alternate offsets the relocation search may try before giving up. |
| `piece.enabled` | `true` | Whether per-piece rejection runs at all. |
| `piece.max_height_deviation` | `6` blocks | How far a single piece's ground height may deviate from the village's start height. |
| `tier.enabled` | `true` | Whether tier rolling runs at all; when `false`, every village rolls `village` (vanilla-like size). |
| `tier.weights.hamlet` / `.village` / `.town` / `.city` | `30` / `45` / `20` / `5` | Relative roll weight per tier. |
| `tier.jigsaw_depth.hamlet` / `.village` / `.town` / `.city` | `3` / `6` / `8` / `10` | Piece-depth budget fed to vanilla's own jigsaw assembly per tier. |
| `tier.max_distance.hamlet` / `.village` / `.town` / `.city` | `80` / `96` / `128` / `128` blocks | Maximum radius from the village centre per tier. |
| `tier.hamlet_minimum_pieces` | `4` | Non-street piece floor a shrinking village must clear to stay labelled `hamlet`. |
| `tier.performance_cap_multiplier` | `3.0` | Ceiling on generated piece count, as a multiplier of vanilla's own piece count for that structure. |

### Version matrix

| Combination | State |
|---|---|
| Fabric, 1.21.1 | new in this release (Wave 1) |
| NeoForge, 1.21.1 | new in this release (Wave 1) |
| Fabric, 26.2 | new in this release (Wave 1) |
| NeoForge, 26.2 | new in this release (Wave 1) |
| Forge, 1.20.1 | planned (Wave 2) |
| Fabric, current 1.21.x point releases | planned (Wave 3, exact point releases chosen when that wave ships) |
| NeoForge, current 1.21.x point releases | planned (Wave 3, exact point releases chosen when that wave ships) |

If your version and loader aren't listed as shipping yet, this mod isn't out for that combination
yet — it's coming, not abandoned.

### Prior art

["Improved Village Placement"](https://modrinth.com/mod/improved-village-placement)
(Apollounknowndev, MIT, the same Fabric/Forge/NeoForge version matrix) already rejects whole
village sites by height variance — the site-selection half of this mod's own scope. Grounded
Villages is built independently, with no code reuse, and credited here as prior art rather than
named as competition: the superset this mod adds is height *plus* water *plus* size tiers,
together, in one mod, where the prior-art mod covers only the height half and has no water check
and no size-tier system.

### Compatibility

- **Any other worldgen mod.** Placement only, no terrain edits of its own, and no hard dependency
  on or detection of any specific other worldgen mod — this mod works on top of whatever terrain
  another mod (Terralith or similar) generates, with no dedicated compatibility patch and no
  integration code aimed at any specific mod by name.
- **Structure scope.** This mod acts on every structure carrying the `#minecraft:village` tag —
  every vanilla village variant, and any modded or datapack-added structure registered into that
  same tag — automatically, with no per-structure integration work on either side. Nothing outside
  that tag is touched. The tag scope itself is config-overridable, for a modpack that wants the
  same treatment applied somewhere the vanilla tag doesn't reach, or wants to exclude one specific
  structure from it.
- **Modded villages.** Covered automatically the moment they carry the `#minecraft:village` tag —
  no code change needed on this mod's side or the structure's own mod for that coverage to apply.

### Known limitations

- A village whose surviving pieces still fall short of the hamlet minimum after a shifted-site
  retry falls back to vanilla's own unchecked placement rather than disappearing — on genuinely
  difficult terrain (a seed dominated by a large lake or extreme elevation swings), a village can
  still generate scattered or partly wet, exactly as vanilla would have placed it.
- A village that loses pieces to rejection but still clears the hamlet minimum is kept and
  relabelled `hamlet`, regardless of which tier it originally rolled — a shrunk village always
  presents as the smallest tier, not as a partially built larger one.
- No in-game or live config reload at this release; a config edit takes effect on the next server
  start.

### Privacy

No telemetry, no update checks, no network calls of any kind.

### Support

Issues and questions go through the tracker only. No SLA. MIT licensed; source public on the
cubealgos Forgejo, mirrored to GitHub.
