---
schema_version: 1
id: 01M30N1YQJH8BYK54CNK2AG5F1
key: GV-25
type: test
title: Seed-sweep harness task on the Forge 1.20.1 leg, so its release evidence is a repeatable run
created_by: kevin
created_at: 2026-09-21T00:15:04Z
---

## Scope

GV-21 found that `build.forge.gradle.kts` has no `seedSweep` task; the Forge leg's release evidence rests on GV-15's one recorded production-server run. Add the harness on the Forge node as GV-12 did for NeoForge (`ServerStartedEvent`/`RegisterCommandsEvent` equivalents in Forge 1.20.1), so `just sweep` runs there too.

## Approach

A `src/seedsweep-forge/java` source set with the Forge event wiring, reusing `SeedSweepRunner`/`Stats`/`Result`; the Gradle task on the `1.20.1-forge` node; three baseline seeds compared with Fabric 1.20.1.

## Acceptance criteria

- [ ] `./gradlew :1.20.1-forge:seedSweep` runs three baseline seeds and matches the Fabric 1.20.1 numbers.
- [ ] `docs/baseline/README.md` gains the Forge rows; `just check` green; merged through a Forgejo pull request into `development`.

## Constraints and prior findings

GV-12's NeoForge harness; GV-15's Forge proof; the dev runtime uses Mojang names, the production server SRG.
