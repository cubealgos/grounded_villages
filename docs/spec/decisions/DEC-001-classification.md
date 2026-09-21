---
title: "grounded_villages DEC-001 — Distributed product, full spec sheet"
type: "spec"
category: "grounded_villages"
---

# `DEC-001` — Distributed product, full spec sheet

**Status:** decided, 2026-09-20 (classification is mechanical: every cubealgos mod gets the full
chunked spec, `create_civilization/CLAUDE.md`'s routing table and the villager_voices/create_firearms
precedent both apply without a separate ruling being needed).

A new, standalone Minecraft mod, not a `create_civilization` building block and not a Create add-on
— it changes vanilla world generation and works with or without Create installed
(`00-context.md`). It ships to real users on Modrinth, targets a wide version/loader matrix
(1.20.1 through 26.2, three loaders across the ladder, `decisions/DEC-004-versions-and-toolchain.md`),
and touches a shared, high-risk surface — world generation, which runs unattended on every server
that installs it and cannot be undone once a chunk is written. That combination is exactly what a
full spec sheet exists to de-risk before the first line of Java is written, the same reasoning
`villager_voices` `DEC-001` and `create_firearms` `DEC-001` both record for their own, smaller
matrices.

Alternative considered: a short design note instead of the full chunked format, on the grounds that
"site selection plus per-piece rejection" sounds like a small feature. Rejected: the feature touches
vanilla's jigsaw structure pipeline across the full 1.20.1–26.2 version ladder and three loaders —
even now that both research notes have confirmed the exact hooks
(`04-architecture.md` `ARCH-DEC-001`), the surface remains genuinely large: three loaders, a
version-delta parameter mismatch to manage, a confirmed real tension between the `city` tier's ruling
and vanilla's own single-start model, and a close prior-art mod surfaced only because the research
went looking (`README.md` "Open questions gathered") — precisely the kind of finding the chunked
format's mandatory dimensions and enumeration tables exist to surface, not something a short note
would have caught. Cost if wrong: an evening of spec for a mod whose first shippable combination is
still small (one loader/version pair).
