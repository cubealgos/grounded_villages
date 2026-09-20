---
schema_version: 1
id: 01M2ZZP05QMB54R0ZCNGG5ZZFC
key: GV-2
type: chore
title: "Stonecutter skeleton: six version nodes building empty jars"
created_by: kevin
created_at: 2026-09-20T18:01:33Z
---

## Scope

The Stonecutter skeleton: six real version nodes each building an empty jar, no mixin or logic
code yet. `match("1.20.1", "fabric", "forge"); match("1.21.1", "fabric", "neoforge");
match("26.2", "fabric", "neoforge")` — `versions/<id>-<loader>` per node, each with its own
`build.<loader>.gradle.kts`. Root registers `chiseledBuild` (`ofTask("buildAndCollect")`) and
`chiseledCheck` (`ofTask("check")`) explicitly — neither is automatic from applying the plugin.

## Approach

`dev.kikugie.stonecutter` `0.9.8`, per `04-architecture.md` "Shape" and `ARCH-DEC-002`, and
`multi-loader-multi-version-mods-2026.md` "Grounded Villages" §1 (the `match()` helper, quoted
verbatim from the multiloader template). Plugin families: `fabric-loom` `1.17-SNAPSHOT` for the
three Fabric legs; MDG `2.0.147` for the two NeoForge legs; MDG's `legacyforge` addon for the Forge
1.20.1 leg. Mojang mappings on every node. One shared `src/main/` tree (Stonecutter's default
shape) — no precompiled `common` subproject (`ARCH-DEC-002`).

## Acceptance criteria

- [x] `settings.gradle.kts` wires Stonecutter with the six `match()` calls from
  `decisions/DEC-004-versions-and-toolchain.md`
- [x] each `versions/<id>-<loader>` node has its own `build.<loader>.gradle.kts` supplying the
  loader entrypoint stub, mixin-config JSON stub, and that loader's mod-metadata file
- [x] root `stonecutter.gradle.kts` registers `chiseledBuild` and `chiseledCheck` explicitly
- [x] `./gradlew chiseledBuild` produces six real, distinct, empty jars
- [x] each node's Java toolchain matches `contracts/platform-matrix.md`'s per-row table
  (1.20.1 -> Java 17; 1.21.1 both loaders -> Java 21; 26.2 both loaders -> Java 25)
- [x] Mojang mappings confirmed on every node (`loom.officialMojangMappings()` on Fabric; MDG/
  `legacyforge` default elsewhere)
- [x] no rejection/tier/config logic exists yet — this ticket is skeleton only, `PIECE`/`SITE`/
  `TIER`/`CONFIG` domains land in their own tickets

## Constraints and prior findings

`ARCH-DEC-002`: the mixin classes themselves are never precompiled — shared `.java` source
compiled fresh per node, each getting its own correctly-wired refmap; the pure-logic packages
(`SiteSelector`/`PieceGate`/`TierRoller`/`ConfigModel`) reference no Minecraft type and carry no
Mixin annotation, so need no refmap. `PLATFORM-REQ-001`: if any row's toolchain version moves,
`just doctor` fails naming the row (GV-4 wires the check; this ticket only needs the version pins
to be correct as shipped). Blocked by GV-1 (needs the bootstrapped repo shape to build into).
