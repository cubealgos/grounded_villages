---
schema_version: 1
id: 01M2ZZPQVRPN2FXJS7SPDYEZHA
key: GV-19
type: chore
title: "Publish-tool extension: per-target version numbers, jar/game-versions/loaders overrides for six invocations"
created_by: kevin
created_at: 2026-09-20T18:01:57Z
---

## Scope

Extend `modrinth-publish.py`'s `cmd_version` for this mod's own needs: per-target overrides
(`--jar`/`--game-versions`/`--loaders`/`--version-number`), since today it takes only
`--repo`/`--yes`/`--dry-run` and reads every field from one static `docs/modrinth/body.md` table —
fine for a one-jar release, not for six.

## Approach

Extend the upstream `modrinth-publish.py` (`standards/marketing/modrinth/modrinth-publish.py`)
rather than fork it — add the override flags `cmd_version`'s `build_parser` is missing today,
keeping every other sibling mod's own use of the same script unaffected.

## Acceptance criteria

- [ ] `cmd_version` accepts per-invocation overrides for jar path, game-versions array, loader, and
  version number, without needing six separate static body files
- [ ] the existing `version_number`-already-exists collision check (lines 780-782 of the upstream
  script, per the research) still applies per invocation
- [ ] six separate invocations succeed for a full Wave 1-2 release, each uploading exactly one jar
- [ ] artifact naming follows `grounded_villages-<mc>-<loader>-<version>.jar`
  (`REL-DEC-001`)
- [ ] a `Game versions` array is only ever allowed to span more than one tag within the same
  Java/toolchain generation — never across a Java-version boundary or a loader, enforced or at
  least checked by this tool

## Constraints and prior findings

Blocked by GV-1 (the tool this ticket extends is copied in at bootstrap). Reads
`multi-loader-multi-version-mods-2026.md` "Grounded Villages" §5 for the exact line numbers and
behaviour of the upstream `modrinth-publish.py` this extends.
