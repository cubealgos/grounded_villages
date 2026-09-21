---
title: "grounded_villages DEC-004 — 1.20.1 through 26.2, Stonecutter, Mojang mappings, one repository"
type: "spec"
category: "grounded_villages"
---

# `DEC-004` — 1.20.1 through 26.2, Stonecutter, Mojang mappings, one repository

**Status:** decided by Kevin, 2026-09-20; both the build-tooling half
(`multi-loader-multi-version-mods-2026.md`'s "Grounded Villages" section) and the vanilla jigsaw
hook itself (`village-jigsaw-placement-1-20-1-to-26-2.md`) are now confirmed for Waves 1–2 — both
notes landed 2026-09-20, after this spec's first draft. Wave 3's exact point releases are resolved as
a decision rule rather than a fixed list, below.

Kevin: "For this one I truly want to support as many versions as possible." The version ladder, in
build order:

1. **Fabric + NeoForge, 1.21.1 and 26.2** — first, on the toolchain this fleet already has cached
   experience with (`villager_voices` built the identical four-combination matrix one version
   earlier, `villager_voices/spec/contracts/platform-matrix.md`).
2. **Forge, 1.20.1** — second. The oldest and most widely-installed version in this ladder, and the
   one loader (Forge, on this version) none of this fleet's other mods target yet.
3. **The 1.21.x point releases between 1.20.1 and 26.2, both loaders** — last, filling in the ladder
   once the endpoints and the oldest rung are proven.

One repository for the whole ladder, not one repository per version. **Stonecutter**
(`dev.kikugie.stonecutter` `0.9.8`, confirmed by reading the official templates' own
`settings.gradle.kts` directly) carries the version axis — six real version nodes for Waves 1–2:
`match("1.20.1", "fabric", "forge"); match("1.21.1", "fabric", "neoforge"); match("26.2", "fabric",
"neoforge")`. **Mojang mappings** throughout, rather than Yarn on the Fabric rungs — confirmed as the
default or trivially-selected channel on every leg: Fabric via `loom.officialMojangMappings()`,
NeoForge's MDG by default, and Forge 1.20.1's `legacyforge` addon develops against Mojang mappings
too, reobfuscating only the runtime jar to SRG (old Forge's own loader expectation) transparently via
Mixin's refmap step. Source reads Mojmap on every node; Forge 1.20.1 is the one leg where the
compiled name and the runtime name differ, invisible to this mod's own code.

**Confirmed toolchain, per rung** (`04-architecture.md`'s own table repeats this in full):

| MC version | Java | Fabric plugin | NeoForge/Forge plugin |
|---|---|---|---|
| 1.20.1 | **17** | `fabric-loom` | MDG `2.0.147` + `legacyforge` addon (Forge) |
| 1.21.1 | 21 | `fabric-loom` | MDG `2.0.147` (NeoForge `21.1.251`) |
| 26.2 | 25 | `fabric-loom` | MDG `2.0.147` (NeoForge `26.2.0.88`) |

Root Gradle `9.5.1` across every node. **1.20.1 needs Java 17, not 21** — the one toolchain fact this
spec's first draft could not have guessed correctly, since 1.20.1 sits below the ≥1.20.5 boundary
`fabric-loom`'s own `requiredJava` table uses.

**Wave 3, resolved as a rule, not a list**: neither research note names a curated set of long-lived
1.21.x point releases — Waves 1–2's six nodes are the only ones either research pass covered, and the
jigsaw note's own version-delta table only bisects an unrelated class-shape change to "between
1.21.2 and 1.21.9," not a release-selection claim. Wave 3 therefore targets **the 1.21.x point
releases that have both a current Fabric API and a current NeoForge release, chosen at that wave's
own ticket** — a decision rule that closes this question without inventing a version fact.
`contracts/platform-matrix.md` carries the same rule against its Wave 3 rows.

Alternative considered: Fabric- and NeoForge-only, matching `villager_voices`' own ladder, deferring
Forge indefinitely. Rejected: Kevin's own "as many versions as possible" is explicit, and 1.20.1 is
old enough that Forge, not NeoForge, is the loader most 1.20.1 players and modpacks actually run —
skipping it would mean skipping the version this mod's realism fix matters most for. Cost if wrong:
now essentially nil for both the build-tooling shape (MDG's `legacyforge` addon puts the Forge leg on
the same plugin family as the NeoForge legs) and the hook itself (the same two mixin targets exist on
every version, `ARCH-DEC-001`) — the remaining risk is the version-delta parameter-list mismatch
already priced into the mixin's design, not an open unknown.
