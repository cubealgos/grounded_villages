---
title: "grounded_villages spec — release engineering and distribution"
type: "spec"
category: "grounded_villages"
---

# Release engineering, distribution and support (`REL`, sheet §7)

| Item | Position |
|---|---|
| Version scheme | `<mod>-<mc>-<loader>` — e.g. `grounded_villages-1.21.1-fabric`, `grounded_villages-26.2-neoforge`, `grounded_villages-1.20.1-forge`. Both `<mc>` **and** `<loader>` are encoded, the same reasoning `villager_voices` `REL-DEC-001` gives for its own scheme: a fabric jar and a neoforge (or forge) jar for the same Minecraft version are two different artifacts, not one build with two loader tags. |
| Branches | gitkontor's: `development`, `production`; releases are tags on `production` |
| Channels | Modrinth only; CurseForge deferred |
| CI | Woodpecker: `./gradlew chiseledBuild` — Stonecutter's own aggregate task, registered explicitly in the root build script (not automatic from applying the plugin), invoking each node's `buildAndCollect`-shaped task in turn (`multi-loader-multi-version-mods-2026.md` "Grounded Villages" §5); plus lint, unit tests, and the shared-source purity check (`04-architecture.md` `ARCH-DEC-002`), game tests per shipped combination (`contracts/platform-matrix.md`), live from the first push since the repo is public on Forgejo from the bootstrap, GitHub mirror carrying the public issue tracker |
| Always a playable build | `just client` boots on Wave 1's combinations at every merge once Wave 1 lands, and a village generated in a fresh test world visibly shows the mod's effect (a consistent ground level, no piece on water) |
| Support | Issue tracker only; no SLA; a `SUPPORT.md` says so |
| Ports | A new Minecraft version or loader is an additive Stonecutter node (`04-architecture.md` `ARCH-DEC-002`), not a rewrite — each node compiles the shared mixin source fresh against its own loader/mappings, so adding one costs a `build.<loader>.gradle.kts` and mixin-config entry, not a module split; the hook table (`contracts/platform-matrix.md`) is re-verified against each new version's jar as it is added, since every row's hook is currently unconfirmed |

## Modrinth publish matrix, per wave

Following the same reading `villager_voices` `REL` gives of
`standards/marketing/modrinth/modrinth-publish.py`, confirmed unchanged for this mod
(`multi-loader-multi-version-mods-2026.md` "Grounded Villages" §5): one `cmd_version` call uploads
exactly one jar, so **Waves 1–2's six confirmed nodes need six separate invocations** for a full
release at that point in the ladder — Wave 3 adds one invocation per its own eventual point-release
nodes, not yet counted since they are not yet enumerated (`contracts/platform-matrix.md`). Artifact
naming follows `grounded_villages-<mc>-<loader>-<version>.jar`, adapted from the official template's
own `base.archivesName`/`version` pattern. A `Game versions` array may legitimately span more than
one tag only within the same Java/toolchain generation; never across a Java-version boundary and
never across loaders.

`REL-REQ-001`: every release jar is built by `just release` from a clean checkout at a tag.
`REL-REQ-002`: the release notes list the Minecraft version, loader, and loader version tested for
that specific jar.
`REL-REQ-003`: the release notes state the default thresholds, tier weights, budget multipliers, and
performance cap in force for that release (`contracts/public-surface.md`).
`REL-REQ-004`: each wave's first release notes state plainly which combinations are new in that wave
and which structure/version combinations remain `planned` (`contracts/platform-matrix.md`), so an
operator on an unshipped version knows to wait rather than file a bug.

## Decisions

- `REL-DEC-001` — **`<mod>-<mc>-<loader>`, Kevin's own scheme**, not `villager_voices`'
  `<semver>[-alpha.N]+<mc>-<loader>`. Recorded as this project's own version-string format because
  Kevin specified it directly rather than this sheet proposing one. **Cost if wrong**: a
  version-string format change before the first tagged release is free; after one exists, it is a
  documentation note in release history, not a functional break.
