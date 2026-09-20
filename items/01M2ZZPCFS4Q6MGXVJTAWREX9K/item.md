---
schema_version: 1
id: 01M2ZZPCFS4Q6MGXVJTAWREX9K
key: GV-10
type: test
title: "Headless seed-sweep harness spike: datagen-modeled generation, one-day-bounded, runServer fallback"
created_by: kevin
created_at: 2026-09-20T18:01:45Z
---

## Scope

The one-day-bounded spike named in `operations/testing.md` "Verification for the first ticket"
(V1): prove whether the datagen-modeled headless harness actually works — specifically, whether a
`GenerationContext`'s `StructureTemplateManager` can be built without a live server, modeled on
`runDatagen` (Fabric) / `GatherDataEvent` (NeoForge).

## Approach

Spend at most one day attempting the in-process shape: a Gradle task on the game classpath (not a
`test`-sourceSet JUnit test) that builds a `ChunkGenerator`+`RandomState` for the overworld and
calls structure placement directly for N seeds. If the `StructureTemplateManager`/`ResourceManager`
gap cannot be closed by end of day, ship the named fallback directly rather than spending further
time on the harder shape: a Fabric-node-only `runServer` booted per seed, `level-seed` in
`server.properties`, a mod command sampling columns under each placed piece and writing JSON
(never parsing log text), then `/stop`.

## Acceptance criteria

- [x] a written verdict: does the in-process `GenerationContext` build succeed, and if not,
  precisely where it fails
- [x] if it works: a working Gradle task generates N seeds' worth of villages and measures height
  spread and water fraction before/after this mod is active
- [x] if it does not work within the one-day bound: the `runServer`-per-seed fallback is shipped
  instead, with the same before/after measurement, assembled from a real seed list
- [x] `TEST-REQ-003`: the chosen harness runs against a fixed, checked-in seed list, reproducible
  release over release
- [x] the realised tier distribution (GV-8) is measured against configured weights as a secondary
  output

## Constraints and prior findings

Blocked by GV-2 only (needs a real node to build the harness task against) — deliberately not
blocked by GV-6/GV-7/GV-8, since the spike's whole purpose is proving the harness shape works at
all, ahead of (or in parallel with) the placement logic it will eventually measure. Flagged by the
research itself as its weakest-evidenced recommendation (`operations/testing.md`) — this ticket
either closes that gap or replaces it with a working, if less elegant, fallback. Do not exceed the
one-day bound chasing the harder shape.
