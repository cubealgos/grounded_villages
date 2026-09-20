---
schema_version: 1
id: 01M2ZZPCNRV0FXQ7E9QF1NQWQW
key: GV-13
type: docs
title: "First Modrinth listing body: positioning, prior-art credit, config table"
created_by: kevin
created_at: 2026-09-20T18:01:45Z
---

## Scope

The first real `docs/modrinth/body.md`, replacing GV-1's placeholder draft: positioning per
`00-context.md` (one ground level, dry, size variety — height plus water plus size tiers together),
`decisions/DEC-009-prior-art.md`'s credit to "Improved Village Placement," the shipped config
table, and a plain statement of what's still `planned` per `contracts/platform-matrix.md`.

## Approach

Write the listing against what GV-6 through GV-9 actually shipped, not the spec's proposed numbers
verbatim — confirm the shipped defaults first, then describe them.

## Acceptance criteria

- [x] the body credits "Improved Village Placement" as prior art and positions this mod as the
  superset (height + water + size tiers), never as a competitor to avoid naming
- [x] the config table in the body matches `domains/config.md` §3's defaults **as actually
  shipped** by GV-6/GV-7/GV-8/GV-9, not the spec's proposed numbers if they changed at those
  tickets
- [x] no promise beyond 1.0 scope — `00-context.md` "what it will not do" is respected verbatim
- [x] `REL-REQ-003`: the body states the default thresholds, tier weights, budget multipliers, and
  performance cap in force for the release it accompanies
- [x] `REL-REQ-004`: states plainly which combinations are new in Wave 1 and which remain `planned`

## Constraints and prior findings

Blocked by GV-6, GV-7, GV-8 (needs the actually-shipped behaviour and defaults to describe
accurately, not the spec's proposed ones). This is a docs ticket, not a design ticket — any
numeric or behavioural question it surfaces goes back to the relevant domain ticket, not decided
inline here.
