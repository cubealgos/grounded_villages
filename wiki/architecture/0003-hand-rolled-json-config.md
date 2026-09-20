# ADR 0003 — One hand-rolled JSON config file, no config library

**Status:** accepted (as of 2026-09-20) · carried over from `grounded_villages` spec `decisions/DEC-008-config-file.md`

## Context

Six version/loader nodes each have their own idea of "the" config mechanism: Fabric ships no config
API of its own at all (Cloth Config and AutoConfig are both third-party, one more dependency to pin
per node); NeoForge's `ModConfigSpec` is TOML-formatted and NeoForge-only; Forge's own
`ForgeConfigSpec` is a separate, differently-shaped TOML mechanism again — so even the two
Forge-family legs could not share a config format with each other, let alone with the three Fabric
legs. No config mechanism is both present everywhere and loader-neutral across this matrix.

## Decision

**One hand-rolled JSON config file** (`config/grounded_villages.json`), read/write/validate written
directly against a small hand-rolled parser, not a dedicated config framework. A record whose
compact constructor clamps out-of-range values to shipped defaults rather than throwing — the same
shape `villager_voices`' own dependency-free `Config`/`MiniJson` pair already uses. Defaults are
written to disk the first time the file is missing. The only loader-specific line is the config
directory lookup (`FabricLoader.getInstance().getConfigDir()` on Fabric,
`FMLPaths.CONFIGDIR.get()` on NeoForge/Forge) — everything else lives in the loader-free
`ConfigModel` class (ADR 0002).

## Consequences

Adds no dependency-version bookkeeping to the six-node matrix, and reads identically on every
target. No in-game reload or config-screen integration (Cloth Config, ModMenu) exists at 1.0 — a
restart is required for a config edit to take effect — deferred, not rejected: a config-screen
integration would be additive UI on top of the same underlying JSON file, and can land once the
placement mechanics (GV-5 through GV-9) are proven. Adopting a config library later, if ever, would
be a migration of the loading code, not a change to the schema an operator already edits by hand.
