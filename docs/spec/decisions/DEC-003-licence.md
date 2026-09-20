---
title: "grounded_villages DEC-003 — MIT, no CLA, public under cubealgos from the first commit"
type: "spec"
category: "grounded_villages"
---

# `DEC-003` — MIT, no CLA, public under cubealgos from the first commit

**Status:** decided by Kevin, 2026-09-20.

MIT, no CLA. Public under the `cubealgos` organisation on Forgejo from the bootstrap, mirrored to
GitHub with the issue tracker there, and listed on Modrinth — the identical shape every sibling mod
ships under (`villager_voices` `DEC-003`, `create_firearms` `DEC-003`). `NOTICE` credits every
build-time and runtime dependency this mod actually reuses source or binaries from: at minimum
Fabric API (Apache-2.0) and NeoForge, with NeoForge's own licence still to verify at the first ticket
exactly as `villager_voices`' `NOTICE` flags for itself, plus Minecraft Forge and Mojang mappings
once the 1.20.1 combination's dependency set is confirmed (`decisions/DEC-004-versions-and-toolchain.md`).
Unlike `villager_voices`, this mod ships no original creative assets of its own — no lines, no
audio, no new textures or models (`00-context.md` "what it will not do") — so there is no
project-authored-content notice obligation to track, only the dependency one.

gitkontor's file-based ticket workflow; ticket prefix to be chosen when the repo is bootstrapped
(the same open item `create_firearms` `DEC-003` left for itself, rather than guessed at here). Spec
copied into the repository once bootstrapped, per `create_civilization/CLAUDE.md`'s standing rule
that the vault copy is the drafting record and the in-repo copy is the copy.

This diverges from two heimathafen defaults, the same two every Minecraft-mod sibling already
diverges on: `standards/legal/dependency-license-policy.md`'s Apache-2.0-plus-CLA default, and "no
remote until justified" (every repo starts local-only). This project inherits the divergence rather
than deciding it fresh, the same reasoning `villager_voices` `DEC-003` and `create_synthetic_diamonds`
`DEC-003` both give for their own identical choice. Cost if wrong: MIT and a public remote are hard
to walk back once someone has forked.
