---
schema_version: 1
id: 01M2ZZM1WX25G29NH029JHYHVD
key: M4
title: Release
status: todo
created_at: 2026-09-20T18:00:29Z
---

## Goal

Ship 1.0: a real Modrinth listing per shipped combination, a working six-jar publish path, and a
tagged release.

## Scope

Extending the fleet's `modrinth-publish.py` for six per-target invocations, a real listing per
combination, and the release itself (tag, build, changelog, release notes).

## Exit criteria

- Every combination that has landed by this point (at minimum, Wave 1's four) is published on
  Modrinth with its own correct listing and `Game versions` array.
- `just release` builds every shipped jar from a clean checkout at a tag.
- `CHANGELOG.md` and the release notes satisfy `REL-REQ-001` through `REL-REQ-004`.

## Tickets

GV-18, GV-19, GV-20, GV-21.

## Depends on

M1 at minimum (Wave 1 must ship something to release); M2 and M3 extend this release's own scope
if they have landed by then, but do not block a first 1.0 covering only what is ready.
