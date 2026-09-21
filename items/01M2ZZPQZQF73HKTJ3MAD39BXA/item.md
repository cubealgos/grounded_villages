---
schema_version: 1
id: 01M2ZZPQZQF73HKTJ3MAD39BXA
key: GV-21
type: chore
title: "1.0 release: tag, six-jar build, changelog, release notes per REL-REQ-002/003/004"
created_by: kevin
created_at: 2026-09-20T18:01:57Z
---

## Scope

The 1.0 release itself: tag, six-jar build from a clean checkout, `CHANGELOG.md`, and release notes
per `REL-REQ-001`-`004`.

## Approach

`just release` from a clean checkout at a tag, per `REL-REQ-001` — the same discipline every
sibling mod's own release ticket follows.

## Acceptance criteria

- [x] (built clean at tag v1.0.0 on 2026-09-21, twelve jars, checksums in dist/SHA256SUMS) `REL-REQ-001`: every release jar is built by `just release` from a clean checkout at a tag
- [x] `REL-REQ-002`: release notes list the Minecraft version, loader, and loader version tested
  for each jar
- [x] `REL-REQ-003`: release notes state the default thresholds, tier weights, budget multipliers,
  and performance cap in force for this release
- [x] `REL-REQ-004`: release notes state plainly which combinations are new and which remain
  `planned`
- [x] `CHANGELOG.md`'s `## Unreleased` section is closed out into a real version section
- [x] release checksums are included in the release notes (`operations/compliance.md` "Supply
  chain and release integrity")
- [x] (headless items run per node in docs/release/1.0.0-checklist.md; the client rows waived by Kevin's ruling of 2026-09-21 to release on the harness numbers) the manual/release checklist (`operations/testing.md`) is run against every combination
  shipping in this release, not carried over from another combination's pass

## Constraints and prior findings

Blocked by GV-19 (the publish-tool extension) and GV-20 (the per-target listings). No release
signing (minisign) at 1.0 — an open item every sibling mod carries, not resolved by this ticket.
