---
schema_version: 1
id: 01M2ZZPHV53S7KPXR0RYYD6W4C
key: GV-16
type: epic
title: "Wave 3: the 1.21.x point releases"
created_by: kevin
created_at: 2026-09-20T18:01:51Z
---

## Scope

Epic. Land whichever 1.21.x point releases have both a current Fabric API and a current NeoForge
release at the time this wave is worked — a decision rule, not an enumerated list
(`decisions/DEC-004-versions-and-toolchain.md`, `contracts/platform-matrix.md`) — tracked via its
child ticket(s), the first of which is GV-17.

## Approach

Not designed further here; the concrete work — checking live release availability and choosing the
point release(s) — is scoped at GV-17, per `decisions/DEC-004-versions-and-toolchain.md`'s
decision-rule closure of this question.

## Acceptance criteria

- [x] every child ticket of this epic reaches `done`

## Constraints and prior findings

Do not invent specific 1.21.x version numbers ahead of GV-17 — the spec deliberately leaves this
open as a rule to apply at the ticket, not a list to guess now.
