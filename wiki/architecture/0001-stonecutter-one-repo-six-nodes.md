# ADR 0001 — Stonecutter, one repository, six version nodes

**Status:** accepted (as of 2026-09-20) · carried over from `grounded_villages` spec `04-architecture.md` `ARCH-DEC-002`, `decisions/DEC-004-versions-and-toolchain.md`

## Context

This mod targets 1.20.1 through 26.2, three loaders (Fabric, NeoForge, Forge), across a version
ladder Kevin explicitly asked to be as wide as possible ("For this one I truly want to support as
many versions as possible"). One repository per version/loader combination would multiply the
maintenance burden of a single shared mixin fix by six; a monorepo needs a real mechanism for
building six distinct jars from one shared source tree without hand-duplicating version-specific
code.

## Decision

**Stonecutter** (`dev.kikugie.stonecutter` `0.9.8`) carries the version axis, in one repository.
Six real version nodes for Waves 1-2: `match("1.20.1", "fabric", "forge");
match("1.21.1", "fabric", "neoforge"); match("26.2", "fabric", "neoforge")`, each a real Gradle
subproject at `versions/<id>-<loader>`. A root `stonecutter.gradle.kts` registers `chiseledBuild`
(the aggregate build task) and `chiseledCheck` (the aggregate verification task) explicitly —
neither exists automatically from applying the plugin. Plugin families: `fabric-loom` for the three
Fabric legs; ModDevGradle (MDG) `2.0.147` for the two NeoForge legs; MDG's own `legacyforge` addon
for the Forge 1.20.1 leg — two plugin families, not three, avoiding a ForgeGradle-vs-Loom
classloader conflict entirely.

## Consequences

Adding a new Minecraft version or loader (Wave 3's 1.21.x points) is an additive Stonecutter node —
a new `build.<loader>.gradle.kts` and mixin-config entry — not a repository split or a module
rewrite. The cost is that Stonecutter's own preprocessor syntax (`//? if <condition> { ... //?}`)
must be used at the one real signature boundary the research identified (`tryPlacingChildren`'s
parameter-list difference between 1.20.1 and 1.21.1+), rather than duplicating the mixin source by
hand per node. `contracts/platform-matrix.md`'s CI matrix runs one job per shipped combination, so a
red job for one node never blocks another node's own release.
