---
schema_version: 1
id: 01M2ZZPCKTGACWPT69AJJX74W9
key: GV-12
type: feat
title: "NeoForge entrypoint parity: config load and mixin wiring match the Fabric legs"
created_by: kevin
created_at: 2026-09-20T18:01:45Z
---

## Scope

NeoForge entrypoint parity: the NeoForge mod entrypoint calls `ConfigModel`'s load path at startup
(before any world loads), wired through `FMLPaths.CONFIGDIR.get()`, and registers the shared mixin
source via NeoForge's declarative `[[mixins]]` (official since NeoForge 20.3) — matching the
Fabric legs' behaviour exactly, no divergence in what runs or when.

## Approach

Adapt the already-proven Fabric entrypoint's config-load and mixin-registration shape to NeoForge's
own declarative mechanisms (`[[mixins]]` in `neoforge.mods.toml`, `FMLPaths` for the config
directory) — no new behaviour, only the loader-specific plumbing that makes the shared logic run
identically.

## Acceptance criteria

- [x] NeoForge 1.21.1 and 26.2 nodes load config at startup identically to the Fabric legs
  (same validation, same fallback/clamp behaviour, same file path convention adapted only for the
  loader-specific directory API)
- [x] the mixin config is registered in `neoforge.mods.toml`'s `[[mixins]]`, no stale `refmap` key
  (NeoForge needs none — a stale one only logs a harmless warning, per
  `multi-loader-multi-version-mods-2026.md` §2)
- [x] manual/release-checklist item satisfied: a fresh NeoForge test world shows the same visible
  effect (consistent ground level, no water-crossing pieces) as the Fabric legs on the same seed

## Constraints and prior findings

Blocked by GV-5 (mixin targets) and GV-9 (`ConfigModel` to call into). This ticket is deliberately
narrow — it is parity, not new behaviour; any behavioural difference found here is a bug in GV-5/
GV-9, not a NeoForge-specific feature to build.
