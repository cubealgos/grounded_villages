---
schema_version: 1
id: 01M2ZZM1SGHCT2YRFADEWP1VH9
key: M2
title: "Wave 2: Forge 1.20.1"
status: backlog
created_at: 2026-09-20T18:00:29Z
---

## Goal

Add the Forge 1.20.1 leg — the oldest and most widely-installed version in this mod's ladder, and
the one loader this fleet has not targeted before.

## Scope

The `legacyforge` build addon, the SRG refmap step, and proving the already-built mixin/site/piece/
tier/config logic (from M1) works unchanged on this leg.

## Exit criteria

- `just client` boots Forge 1.20.1 with the mod active, same visible effect as the M1 combinations.
- A game test proves the hook fires correctly on this leg.
- `NOTICE` records Forge's licence, verified rather than assumed.

## Tickets

GV-14, GV-15.

## Depends on

M1 — the mixin hook family, and every domain's logic, must already be proven on Fabric/NeoForge
before this wave ports it to a third loader.
