# grounded_villages — Architecture Decision Records

Index of the ADRs carried over from the `grounded_villages` spec's own architecture decisions
(`heimathafen` vault `projects/grounded_villages/spec/04-architecture.md`,
`decisions/DEC-004-versions-and-toolchain.md`, `decisions/DEC-008-config-file.md`). Each ADR
corresponds to one architecture decision from that spec; see each file for full
Context/Decision/Consequences.

| # | Title | Decision |
|---|---|---|
| 0001 | Stonecutter, one repository, six version nodes | One repo, `dev.kikugie.stonecutter` `0.9.8`, six real Gradle subprojects for Waves 1-2. |
| 0002 | Shared mixin source tree, compiled per node; no precompiled `common` module | Diverges from `villager_voices`' own precedent — per-node refmaps make a precompiled mixin module unsafe. |
| 0003 | Hand-rolled JSON config, no config library | No config mechanism is both present and loader-neutral across all six nodes. |
