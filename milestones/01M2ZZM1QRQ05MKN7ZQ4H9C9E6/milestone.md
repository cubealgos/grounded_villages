---
schema_version: 1
id: 01M2ZZM1QRQ05MKN7ZQ4H9C9E6
key: M1
title: "Wave 1: Fabric + NeoForge on 1.21.1 and 26.2"
status: done
created_at: 2026-09-20T18:00:29Z
---

## Goal

The first shippable combinations: villages generate on one consistent ground level, dry, with real
size variety, on Fabric and NeoForge for 1.21.1 and 26.2.

## Scope

The mixin hook family into vanilla's jigsaw pipeline; whole-site selection (height spread, water
fraction, bounded search-and-relocate); per-piece rejection (water, height deviation, streets
included) with the shrink/move/vanilla fallback ladder; the four size tiers rolled from the world
seed; the hand-rolled JSON config file; the headless seed-sweep harness spike; game tests per
loader; NeoForge entrypoint parity; the first Modrinth listing body.

## Exit criteria

- `just client` boots Fabric and NeoForge for both 1.21.1 and 26.2 with the mod active.
- A village generated in a fresh test world on any Wave 1 combination visibly shows the mod's
  effect: a consistent ground level, no piece on water, and size variety across several villages.
- The headless harness (or its documented fallback) produces reproducible before/after numbers for
  height spread and water fraction over a fixed seed list.
- Every `SITE-REQ`, `PIECE-REQ`, `TIER-REQ`, and `CONFIG-REQ` in `docs/spec/` has a passing test.

## Tickets

GV-5, GV-6, GV-7, GV-8, GV-9, GV-10, GV-11, GV-12, GV-13.

## Depends on

M0 (Foundation) — the Stonecutter skeleton, CI, and doctor checks GV-5 through GV-13 build on.
