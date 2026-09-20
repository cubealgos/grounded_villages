---
title: "grounded_villages spec — data contract: the config file's schema version, nothing else persisted"
type: "spec"
category: "grounded_villages"
---

# Data contract (`DATA`)

## What this mod persists

**One config file. No world-save state of its own.** This mod's entire effect on a save is
downstream and indirect: it changes which vanilla structure pieces get written into newly generated
chunks, using only vanilla's own existing structure/piece NBT — it adds no memory module, no data
component, no capability, no NBT tag of its own to any entity, block entity, chunk, or item stack.
Unlike `villager_voices` (a config file plus transient in-memory state) this mod persists even less:
there is no transient runtime state to reset on restart either, since a tier roll and a site/piece
decision are recomputed once, at generation time, and never touched again.

## Generation decisions are baked into the world, not re-derivable from config alone

Once a chunk is generated, the village pieces it contains — including which tier was rolled and
which candidate pieces were rejected — are permanent, exactly like any other vanilla structure. A
later config change (different thresholds, different tier weights) affects only chunks generated
from that point forward (`01-actors.md` `ACTORS-002`); there is no mechanism, and none is planned, to
retroactively re-evaluate or regenerate an already-written village. This is not a gap to close — it
is the same one-way property vanilla structure generation itself already has for any mod.

## Rules

| ID | Rule |
|---|---|
| `DATA-REQ-001` | The system shall write no memory module, data component, capability, or NBT tag of its own to any entity, block entity, chunk, or item stack. |
| `DATA-REQ-002` | The system shall write exactly one config file to the server's config folder; no other file. |
| `DATA-REQ-003` | No runtime state of this mod's own is held in memory across a generation event — a tier roll and a site/piece decision are each computed once and then only exist as their effect on the generated chunk. |
| `DATA-REQ-004` | A malformed config file shall degrade to shipped defaults, per key where possible, never a crash or a corrupted save (`domains/config.md` `CONFIG-REQ-003`–`004`). |

## Versioning

The config file carries a `schema_version` field from 1.0 (`domains/config.md` §3): a future key
addition is forward-compatible (a missing key defaults), and a removed or renamed key is a
documented migration note in release notes, not a silent behaviour change
(`SURFACE-REQ-001`). There is no world-save schema to version at all — the config file's own
`schema_version` is the entirety of this mod's versioning surface, mirroring how little
`create_synthetic_diamonds`' `DATA` contract has to cover for a mod with no persisted state.

## Out of scope (sheet §8)

No record of past generation decisions anywhere — no log of which villages were relocated, resized,
or had pieces rejected, beyond what a server operator can already see in the generated world itself
or in startup-time config warnings. No export or import of generation history; nothing here survives
independently of the chunks it shaped.
