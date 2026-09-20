---
schema_version: 1
id: 01M2ZZM1V6VZBRST75SMH8BA6M
key: M3
title: "Wave 3: the 1.21.x points"
status: todo
created_at: 2026-09-20T18:00:29Z
---

## Goal

Fill in the 1.21.x point releases between 1.20.1 and 26.2, chosen at this wave's own ticket rather
than enumerated ahead of time.

## Scope

Checking live Fabric API and NeoForge release availability for the 1.21.x range, adding the chosen
point release(s) as Stonecutter nodes, and re-verifying the hook table against each new version's
jar as it is added.

## Exit criteria

- The chosen point release(s) are documented with the evidence checked, not invented.
- Each new node builds, and the mixin hook is re-confirmed against that version's own jar.

## Tickets

GV-16, GV-17.

## Depends on

M1 — the mixin hook family must already be proven working before extending it to new version
rungs. Not dependent on M2 (Forge is a separate loader axis, not on this wave's own path).
