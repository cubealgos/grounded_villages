# ADR 0002 — Shared mixin source tree, compiled per node; no precompiled `common` module

**Status:** accepted (as of 2026-09-20) · carried over from `grounded_villages` spec `04-architecture.md` `ARCH-DEC-002`

## Context

`villager_voices`, this fleet's own newest Minecraft mod, uses a precompiled `common` Gradle
subproject holding logic called from mixins. Applying that same shape here looks like the obvious
default — until the research (`multi-loader-multi-version-mods-2026.md` "Grounded Villages" §3)
identified that each Stonecutter node's own Mixin annotation-processor run produces its own
`refmap.json`; a precompiled mixin jar would carry the wrong refmap everywhere except the one node
that compiled it. A second finding narrowed the actual boundary further: the refmap problem only
touches classes that carry `@Mixin` annotations or reference Minecraft/loader types — `SiteSelector`,
`PieceGate`, `TierRoller`, and `ConfigModel` reference neither, and could in principle still be a
precompiled module.

## Decision

**One shared `src/main/` tree (Stonecutter's own default shape), compiled fresh per node — no
precompiled `common` subproject.** The mixin classes themselves (`JigsawPlacement`/`Placer`
targets) are shared `.java` source, never precompiled, each node getting its own correctly-wired
refmap. The pure-logic classes (`SiteSelector`/`PieceGate`/`TierRoller`/`ConfigModel`) stay in the
same tree as plain packages (`grounded_villages.site`/`.config` importing no `net.minecraft.*`,
`.mixin` importing no loader) rather than being promoted to their own Stonecutter branch — a
dedicated `common` branch would cost three more version nodes (nine, not six) for a boundary this
mod does not need today.

## Consequences

This is a deliberate divergence from `villager_voices`' own precedent, recorded rather than
silently diverging: that mod's `common` holds logic *called from* mixins on a smaller (four-node)
matrix; here the mixins *are* the logic and compile only against Minecraft, so the economics of a
separate module are different. The shared-source purity check (grep-based, adapted from
`villager_voices/common/build.gradle.kts`'s `verifyLoaderFree`) is scoped per package rather than
per module, and enforced by `TEST-REQ-002`'s deliberate-break proof (GV-11). Promoting the two
loader-free packages to a real `common` module later, if the matrix grows enough to justify it, is
mechanical — the package boundary already exists.
