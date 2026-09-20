---
title: "grounded_villages DEC-008 — One hand-rolled JSON config file, no config library"
type: "spec"
category: "grounded_villages"
---

# `DEC-008` — One hand-rolled JSON config file, no config library

**Status:** decided by Kevin, 2026-09-20; the reasoning is now confirmed by
`multi-loader-multi-version-mods-2026.md`'s "Grounded Villages" §4 (added 2026-09-20 after this
spec's first draft).

One config file, plain JSON, hand-rolled — read/write/validate written directly against a small
hand-rolled parser, not a dedicated config framework (Cloth Config, NeoForge's `ModConfigSpec`,
Forge's config system). Defaults are written to disk the first time the file is missing, so an
operator who never touches the file still gets a real, readable copy of what the mod is doing
(`domains/config.md`).

**Why, confirmed rather than assumed**: across this mod's six version/loader nodes, **no config
mechanism is both present everywhere and loader-neutral**. Fabric ships no config API at all —
Cloth Config or AutoConfig would be one more third-party dependency to pin per Stonecutter node.
NeoForge's own `ModConfigSpec` is TOML-formatted and NeoForge-only, unreadable by the other five
targets (two Fabric legs and the Forge leg). A hand-rolled JSON file is loader-neutral by
construction, adds no dependency-version bookkeeping to the six-node matrix, and reads identically
everywhere — the same shape `villager_voices`' own dependency-free `Config`/`MiniJson` pair already
uses (`villager_voices/common/src/main/java/villager_voices/{config/Config.java,catalogue/MiniJson.java}`),
here as a record whose compact constructor clamps out-of-range values to shipped defaults rather than
throwing (`domains/config.md` §6).

Alternative considered: a per-loader config-screen-mod integration (Cloth Config API, ModMenu),
matching how many other Fabric/NeoForge mods present config today. Deferred, not rejected: a
config-screen integration is additive UI on top of the same underlying JSON file and can land once
the placement mechanics are proven, the same "no in-game reload/config-screen at 1.0" restraint
`villager_voices` shows for its own config (`villager_voices/spec/domains/display.md` §7). Cost if
wrong: none identified for the file-format choice itself; a config-library adoption later is a
migration of the loading code, not a change to the schema an operator already edits by hand.
