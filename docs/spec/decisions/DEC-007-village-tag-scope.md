---
title: "grounded_villages DEC-007 — Scope is every structure in #minecraft:village, configurable"
type: "spec"
category: "grounded_villages"
---

# `DEC-007` — Scope is every structure in `#minecraft:village`, configurable

**Status:** decided by Kevin, 2026-09-20.

This mod's site-selection and per-piece rejection logic applies to **every structure carrying the
`#minecraft:village` structure tag** — every vanilla village variant (plains, desert, savanna,
taiga, snowy) and every modded structure a datapack or another mod registers into that same tag,
automatically and with no per-structure integration work. Any other structure — pillager outposts,
ocean monuments, other structure tags entirely — is untouched; this mod has no opinion about
anything outside the village tag.

**Configurable**: an operator can widen or narrow the tag(s) this mod acts on
(`domains/config.md`), for a modpack that wants the same realism fix applied to a village-shaped
structure that, for whatever reason, isn't tagged `#minecraft:village`, or wants to exclude a
specific modded village variant from the treatment entirely.

This is what makes the scope automatic rather than a maintained roster: a datapack author who adds a
new village variant to the vanilla tag gets site selection and piece rejection for free, the same way
`villager_voices`' `ACTORS-005` gets a datapack-driven line catalogue for free — no code change on
either side is required for a new tagged structure to be covered.

Alternative considered: an explicit, maintained allow-list of structure IDs. Rejected: it would need
updating for every new modded village variant a modpack adds, exactly the maintenance burden the
vanilla tag already exists to avoid, and Kevin's own framing ("every structure in the
`#minecraft:village` tag") names the tag directly rather than a list. Cost if wrong: a config-level
allow/deny list on top of the tag (already planned, `domains/config.md`) covers the rare case where a
tag-based default is wrong for one specific structure, without reverting to a maintained roster for
the common case.
