---
schema_version: 1
id: 01M2ZZP709CKEX2G18ERERE03E
key: GV-9
type: feat
title: "Config file: JSON schema, defaults, validation, clamp-and-warn"
created_by: kevin
created_at: 2026-09-20T18:01:40Z
---

## Scope

`ConfigModel`: one hand-rolled JSON config file, read/write/validate against a small hand-rolled
parser — no config library. A record whose compact constructor clamps out-of-range values to
shipped defaults. Defaults written to disk on first run if the file is absent. Every key in
`domains/config.md` §3's schema table, with that section's proposed default JSON.

## Approach

The `villager_voices` `Config`/`MiniJson` shape (`decisions/DEC-008-config-file.md`), because no
config mechanism is both present and loader-neutral across this mod's six nodes: Fabric has no
config API, NeoForge's own is TOML/NeoForge-only. The only loader-specific line is the config
directory lookup (`FabricLoader.getInstance().getConfigDir()`, `FMLPaths.CONFIGDIR.get()` on
Forge/NeoForge) — that line lives in each loader's own entrypoint, not in this pure-logic class.

## Acceptance criteria

- [ ] `CONFIG-REQ-001` through `CONFIG-REQ-005` implemented
- [ ] `CONFIG-FAIL-001` (malformed JSON): whole-file fallback to defaults, logged warning, no crash
- [ ] `CONFIG-FAIL-002` (one bad key): that key alone falls back or clamps, every other valid key
  is kept
- [ ] `CONFIG-FAIL-003` (unwritable config directory): runs on in-memory defaults for the session,
  logs the path it could not write, does not crash startup
- [ ] the shipped default JSON matches `domains/config.md` §3's proposed schema exactly, including
  `schema_version: 1`
- [ ] `ConfigModel` carries zero Minecraft or loader imports (checked by the shared-source purity
  check that `ARCH-DEC-002` names, `TEST-REQ-002`'s deliberate-break proof lands with GV-11)
- [ ] no live-reload command exists at 1.0 — a restart is required for a config edit to take effect
  (`CONFIG-REQ-005`)

## Constraints and prior findings

Blocked by GV-2 (needs a source tree to live in). `DATA-REQ-002`: exactly one config file written,
no other. This ticket does not add a config-screen-mod integration (Cloth Config, ModMenu) —
deferred, not cut, per `decisions/DEC-008-config-file.md`.
