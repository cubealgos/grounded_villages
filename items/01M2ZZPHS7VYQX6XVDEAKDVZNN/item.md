---
schema_version: 1
id: 01M2ZZPHS7VYQX6XVDEAKDVZNN
key: GV-15
type: chore
title: "Forge 1.20.1 leg: legacyforge addon, SRG refmap proof"
created_by: kevin
created_at: 2026-09-20T18:01:51Z
---

## Scope

Prove the Forge 1.20.1 leg's build shape: MDG `2.0.147` + `legacyforge` addon, Mixin AP writing
`grounded_villages.refmap.json`, the runtime jar and refmap reobfuscated to SRG via
`legacyforge`'s `obfuscation {}` block, and the mixin actually firing against the SRG runtime name.

## Approach

Per `multi-loader-multi-version-mods-2026.md` §2's table: Forge 1.20.1 is the one leg where the
compiled name (Mojang mappings, source-level) and the runtime name (SRG) genuinely differ, handled
transparently by Mixin's refmap step — the MDK ships the `obfuscation {}` block commented out, so
this ticket must uncomment and verify it, not assume it works by default.

## Acceptance criteria

- [x] Forge 1.20.1 node compiles against Mojang mappings (Parchment-over-official, per
  `legacyforge`'s default)
- [x] the runtime jar and its refmap are correctly reobfuscated to SRG
- [x] a game test proves the mixin hook fires on this leg, using the same scenarios as GV-11
- [x] `mods.toml` and the jar manifest's `MixinConfigs` are wired correctly
- [x] `NOTICE` records Forge's own licence, verified rather than assumed (per `DEC-003`'s
  "to-verify" flag)

## Constraints and prior findings

Blocked by GV-5 (the mixin targets must already be proven on the Wave 1 legs before porting).
1.20.1 needs Java 17, not 21 — the one toolchain fact this spec's first draft could not have
guessed correctly (`decisions/DEC-004-versions-and-toolchain.md`).
