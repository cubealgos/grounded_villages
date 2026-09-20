---
title: "grounded_villages spec — CONFIG: the JSON schema, defaults, validation, reload"
type: "spec"
category: "grounded_villages"
---

# `CONFIG` — the config file

## 1. Purpose

The single hand-rolled JSON file every other domain reads its tunable values from: thresholds
(`domains/site.md`), the per-piece tolerance (`domains/pieces.md`), tier weights and budgets
(`domains/tiers.md`), the performance cap, and the structure-tag scope
(`decisions/DEC-007-village-tag-scope.md`). What the file's format is and why is
`decisions/DEC-008-config-file.md`'s concern; this file is the schema itself.

## 2. Dimensions

| Dimension | Answer |
|---|---|
| **Actors** | The server operator (`ACTORS-002`) is the only human actor; the mod reads and validates the file at startup and writes it once if absent. No client-side config exists — this mod has no per-player setting, unlike `villager_voices`' display toggles, because nothing here is player-visible (`01-actors.md` `FINDING-1`). |
| **Over time** | File absent → the mod writes a fresh copy carrying every key's shipped default → loaded and validated once, at server startup → an operator edits it by hand → takes effect on the next server start, since no live-reload command exists at 1.0 (`CONFIG-REQ-005`). |
| **Multiplicity** | Exactly one config file per server instance. At **zero** (file missing or deleted): defaults are written back (`CONFIG-REQ-001`). No "many" case exists — this is not per-world or per-dimension config. |
| **Unwanted** | Malformed JSON; a value of the wrong type for its key; a value out of a key's valid range; an unknown or removed key from an older version of the file — none of these may crash server startup (`CONFIG-FAIL-001`–`003`). |
| **Not-you** | An operator without Java or mixin knowledge, editing a plain-text file directly rather than through any in-game screen — the file's key names are written to be self-explanatory on their own, since plain JSON carries no inline comments (`decisions/DEC-008-config-file.md`). |

## 3. Enumerations

### Schema, with proposed defaults

**Every default value below is proposed by Claude, 2026-09-20, Kevin to confirm at the first
ticket, tuned against the headless harness sweep (`operations/testing.md`) — not measured against a
real world yet.** The mechanism each key governs is decided; the number is not.

| Key | Type | Domain | Meaning | Default (proposed) |
|---|---|---|---|---|
| `schema_version` | integer | — | Forward-compatibility marker, incremented on any breaking key change (`contracts/data-contract.md`). | `1` |
| `scope.structure_tags` | list of string | — | Structure tags this mod acts on (`decisions/DEC-007-village-tag-scope.md`). | `["minecraft:village"]` |
| `site.enabled` | boolean | `SITE` | Whether site selection runs at all. | `true` |
| `site.max_height_spread` | integer (blocks) | `SITE` | Site-level height-spread threshold: the 90th-minus-10th-percentile spread of sampled ground heights (`OCEAN_FLOOR_WG`) across the candidate radius. | `12` |
| `site.max_water_fraction` | float (0–1) | `SITE` | Site-level water-fraction threshold: the share of sampled columns that are water. | `0.05` |
| `site.search_radius` | integer (blocks) | `SITE` | Bound on how far the alternate-site search may look from the vanilla start, staying inside the start chunk's own placement cell. | `48` |
| `site.search_step` | integer (blocks) | `SITE` | Spacing between candidate offsets the search samples. | `48` (tuned, GV-6 — was `16`) |
| `site.search_attempts` | integer | `SITE` | Bound on how many alternate candidate offsets the search may sample before giving up. | `4` (tuned, GV-6 — was `8`) |
| `piece.enabled` | boolean | `PIECE` | Whether per-piece rejection runs at all. | `true` |
| `piece.max_height_deviation` | integer (blocks) | `PIECE` | Per-piece tolerance from the village's start height. | `6` |
| `tier.enabled` | boolean | `TIER` | Whether tier rolling runs at all; **where** `false`, every village rolls `village` (vanilla-like), matching pre-mod behaviour for size. | `true` |
| `tier.weights.hamlet` / `.village` / `.town` / `.city` | float | `TIER` | Relative roll weight per tier. | `30` / `45` / `20` / `5` |
| `tier.jigsaw_depth.hamlet` / `.village` / `.town` / `.city` | integer | `TIER` | `maxDepth` fed to vanilla's jigsaw assembly per tier. | `3` / `6` / `8` / `10` |
| `tier.max_distance.hamlet` / `.village` / `.town` / `.city` | integer (blocks) | `TIER` | `max_distance_from_center` per tier; `town`/`city` sit at the 128-block hard cap (`decisions/DEC-006-size-tiers.md`). | `80` / `96` / `128` / `128` |
| `tier.hamlet_minimum_pieces` | integer | `TIER` | Non-street piece floor a shrinking village must clear to stay a `hamlet` (`decisions/DEC-010-shrink-move-vanilla.md`). | `4` |
| `tier.performance_cap` | integer | `TIER` | Absolute ceiling on generated piece count per village, regardless of tier. | `3×` vanilla's own piece count |

### Default JSON (thresholds still proposed; search_step/search_attempts tuned at GV-6's harness sweep)

```json
{
  "schema_version": 1,
  "scope": { "structure_tags": ["minecraft:village"] },
  "site": {
    "enabled": true,
    "max_height_spread": 12,
    "max_water_fraction": 0.05,
    "search_radius": 48,
    "search_step": 48,
    "search_attempts": 4
  },
  "piece": {
    "enabled": true,
    "max_height_deviation": 6
  },
  "tier": {
    "enabled": true,
    "weights": { "hamlet": 30, "village": 45, "town": 20, "city": 5 },
    "jigsaw_depth": { "hamlet": 3, "village": 6, "town": 8, "city": 10 },
    "max_distance": { "hamlet": 80, "village": 96, "town": 128, "city": 128 },
    "hamlet_minimum_pieces": 4,
    "performance_cap_multiplier": 3.0
  }
}
```

### Validation rules

| Failure | Response |
|---|---|
| Malformed JSON (file does not parse) | The whole file falls back to shipped defaults; a warning is logged naming the file. |
| A key present with the wrong type | That key falls back to its own default; every other valid key is kept as configured. |
| A numeric value out of a sane range (e.g. a negative radius, a fraction outside 0–1) | Clamped to the nearest valid bound; a warning is logged naming the key and the clamp applied. |
| A key not recognised (from an older or hand-edited file) | Ignored, with a warning; never an error that blocks startup. |
| A key missing entirely (from an older file predating it) | Falls back to that key's own default — forward-compatible by construction. |

### Actor per step

The operator acts once, outside the request/response loop entirely (editing the file between server
runs); the mod is the only actor at load time. A single-actor column here is expected, not a finding —
config loading has no handover to model.

### Drawings

None. The schema table above is the complete enumeration; there is no state machine here beyond
"absent → written → loaded," already stated in Dimensions: Over time.

## 4. Use cases

`UC-006` in `02-journeys.md`.

## 5. Requirements

| ID | Requirement | Priority | From |
|---|---|---|---|
| `CONFIG-REQ-001` | **If** the config file does not exist at startup, **then** the system shall write one carrying every key's shipped default. | Must | `UC-006` |
| `CONFIG-REQ-002` | The system shall load and validate the config file once, at server startup. | Must | Dimensions: Over time |
| `CONFIG-REQ-003` | **If** the config file is malformed, **then** the system shall fall back to shipped defaults for the whole file, log a warning, and continue starting rather than crash. | Must | `CONFIG-FAIL-001` |
| `CONFIG-REQ-004` | **If** an individual key is missing, unrecognised, mistyped, or out of range, **then** the system shall fall back to that key's own default (or clamp it) independently of every other key. | Must | `CONFIG-FAIL-002` |
| `CONFIG-REQ-005` | The system shall load config only at startup; no in-game or live-reload command exists at 1.0 — a config edit takes effect on the next server start. | Must | `decisions/DEC-008-config-file.md` |

## 6. Failure modes

| ID | Failure | Required response |
|---|---|---|
| `CONFIG-FAIL-001` | Malformed JSON | Defaults for the whole file, logged warning, server still starts (`CONFIG-REQ-003`). |
| `CONFIG-FAIL-002` | One bad key among otherwise-valid ones | That key alone falls back or clamps; every other key keeps its configured value (`CONFIG-REQ-004`). |
| `CONFIG-FAIL-003` | The config directory is unwritable (read-only filesystem, permissions) | The mod runs on in-memory shipped defaults for the session, logs a clear warning naming the path it could not write, and does not crash server startup. |

## 7. Open questions

| Question | Blocks | Decided by |
|---|---|---|
| Whether every §3 default holds up once measured by the headless harness sweep | Every `REQ` above that reads a default | **Proposed by Claude, 2026-09-20; `site.search_step`/`site.search_attempts` re-tuned by Claude, 2026-09-20 (GV-6), from the same 10-seed sweep — `docs/baseline/README.md`'s before/after table and trade-off writeup is the record.** Every other key (including `site.max_height_spread`/`max_water_fraction`, left at the original proposal per GV-6's own ticket scope) remains Kevin's to confirm; config-overridable regardless of outcome |
| Whether a live-reload command or a config-screen-mod integration (Cloth Config, etc.) should ship later | `CONFIG-REQ-005` | deferred, not cut — a later ticket, same restraint `villager_voices` shows for its own config surface |

~~The specific reasoning for "no config library" across all three loaders~~ — resolved:
`decisions/DEC-008-config-file.md`, confirmed by `multi-loader-multi-version-mods-2026.md`'s
"Grounded Villages" §4 — Fabric has no config API, NeoForge's own is TOML/NeoForge-only, so a
hand-rolled JSON file is the only loader-neutral option across all six version/loader nodes.
