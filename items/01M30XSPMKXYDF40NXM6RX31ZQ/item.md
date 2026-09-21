---
schema_version: 1
id: 01M30XSPMKXYDF40NXM6RX31ZQ
key: GV-27
type: chore
title: Defaults confirmed by Kevin; city depth 9 for 1.0
created_by: kevin
created_at: 2026-09-21T02:47:51Z
---

## Scope

Kevin, 2026-09-21: the shipped defaults are confirmed as final (height spread 12, water 0.05, per-piece tolerance 6, four attempts in 48-block steps, tier weights 30/45/20/5, hamlet minimum 4, performance cap 3.0; villages on rough terrain shrink rather than fall back), and the city tier's jigsaw depth drops from 10 to 9 for 1.0 after the 718-piece seed-18 city. A live piece-count bound stays a later ticket.

## Approach

`ConfigDefaults` city depth 9; the spec's `domains/config.md`, `domains/tiers.md`, `domains/site.md`, `domains/pieces.md` lose every "proposed by Claude, Kevin to confirm" marker in favour of "confirmed by Kevin, 2026-09-21" and the tiers table says 9; vault first, then `docs/spec` synced; `docs/modrinth/body.md` config table and `CHANGELOG.md` 1.0.0 entry updated; tests that pin the defaults updated; a harness run on seed 18 showing the city under the cap.

## Acceptance criteria

- [ ] City depth 9 in code, spec, listing and changelog; no "proposed" marker left in the four domain files.
- [ ] Seed 18 city piece count reported after the change; `just check` green; merged through a Forgejo pull request into `development`.

## Constraints and prior findings

GV-8's cap finding (piece count grows faster than linearly with depth); GV-21's release preparation already merged, so the changelog entry is edited in place.
