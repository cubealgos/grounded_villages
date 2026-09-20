---
schema_version: 1
id: 01M2ZZPHX3A35ATQ5Y30NBA5ZY
key: GV-17
type: chore
title: Choose and add the 1.21.x version nodes with current Fabric API and NeoForge releases
created_by: kevin
created_at: 2026-09-20T18:01:51Z
---

## Scope

At this ticket: check live Fabric API and NeoForge release availability for the 1.21.x point
releases between 1.20.1 and 26.2, choose the set that has both, and add them as Stonecutter nodes.

## Approach

Check `Fabric API`'s and NeoForge's own release feeds for the 1.21.x range at the time this
ticket is actually worked, pick the point release(s) with both, and add them the same way GV-2
added the Wave 1 nodes.

## Acceptance criteria

- [x] the chosen point release(s) are documented here with the date and evidence checked (which
  Fabric API build, which NeoForge build, checked live)
- [x] the corresponding Stonecutter nodes are added, building real jars using the already-proven
  Wave 1 mixin/logic shape
- [x] the hook table (`contracts/platform-matrix.md`) is re-verified against each new version's
  jar as it is added, per `operations/release.md` "Ports" row — never assumed unchanged
- [x] `PLATFORM-REQ-002` holds: a renamed/removed hook target fails the build or mod load with a
  named error, never a silent no-op

## Constraints and prior findings

Blocked by GV-5 (the mixin family must already be proven working before extending it to new
version rungs). `decisions/DEC-004-versions-and-toolchain.md`: "Wave 3 therefore targets the
1.21.x point releases that have both a current Fabric API and a current NeoForge release, chosen at
that wave's own ticket — a decision rule that closes this question without inventing a version
fact." Do not pin a version number before this ticket actually checks live release availability.
