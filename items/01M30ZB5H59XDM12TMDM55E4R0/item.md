---
schema_version: 1
id: 01M30ZB5H59XDM12TMDM55E4R0
key: GV-28
type: docs
title: "Modrinth version numbers carry the mod version: 1.0.0+<mc>-<loader>"
created_by: kevin
created_at: 2026-09-21T03:14:52Z
---

## Scope

At the 1.0.0 publish (2026-09-21) the targets file's version numbers were `grounded_villages-<mc>-<loader>` per `REL-DEC-001` as written: no mod version in them, so a 1.0.1 could never be uploaded (Modrinth version numbers are unique per project and permanent). The twelve versions were published as `1.0.0+<mc>-<loader>` instead. This ticket makes the repo match: `targets.json`, the notes file names, `REL-DEC-001` amended in the vault and synced.

## Approach

`docs/modrinth/targets.json` version numbers `<mod.version>+<mc>-<loader>` derived from `stonecutter.properties.toml`'s `mod.version` (so a bump regenerates them; extend `tools/doctor.py`'s targets check to derive and compare), `REL-DEC-001` amended, spec synced.

## Acceptance criteria

- [x] Version numbers `1.0.0+<mc>-<loader>` in `targets.json`, matching the twelve published versions; the doctor derives and checks them.
- [x] `REL-DEC-001` amended in the vault and `docs/spec`; `just check` green; merged through a Forgejo pull request into `development`.

## Constraints and prior findings

The published version ids are recorded in the GV-21 ticket text; never re-upload.
