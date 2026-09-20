---
title: "grounded_villages DEC-002 — Grounded Villages, mod id grounded_villages, Modrinth slug confirmed free"
type: "spec"
category: "grounded_villages"
---

# `DEC-002` — Grounded Villages, mod id `grounded_villages`, Modrinth slug confirmed free

**Status:** decided by Kevin, 2026-09-20; the Modrinth slug is confirmed; the naming-theme divergence
is now a settled standing exception, not an open question; the prior-art question below is resolved
in `decisions/DEC-009-prior-art.md`.

**Name: Grounded Villages.** Kevin chose it over two alternatives he named himself, "Settled
Villages" and "Village Surveyor" — "Grounded" carries the double sense the mod is actually about:
villages that sit *on the ground* (one consistent height, dry footprint) rather than scattered
across cliffs and lakes, and a more level-headed, "grounded" generation result than vanilla's own.
Mod id `grounded_villages` (Java package/registry convention, underscored, matching every sibling
mod's id shape). Repo name `grounded_villages`, matching the mod id, per the same convention
`villager_voices` and `create_firearms` both use.

## Not a Create add-on, so the descriptive-Create convention does not apply

Every `create_*`-prefixed sibling (`create_firearms`, `create_brass_compass`,
`create_synthetic_diamonds`, `create_metered_motor`, `create_villager_customers`) names itself
`Create: <Thing>` because each is literally a Create add-on, extending Create's own in-world
systems. Grounded Villages changes vanilla world generation and works identically with or without
Create installed (`00-context.md`) — the same non-Create shape `villager_voices` is in, and the same
reasoning that mod's own `DEC-002` gives for not carrying a `Create:` prefix. The title stands on its
own: "Grounded Villages," no colon, no prefix.

## Checked against the naming standard — a standing exception, closed

**Proposed by Claude, 2026-09-20, Kevin to confirm at the first ticket** (a naming-policy reading,
not a technical claim, so recorded as a proposal rather than a research finding): `standards/marketing/naming-theme.md`
sets German-maritime as the house style for "products with a public presence," which this mod is: a
public Modrinth listing with its own audience. Read strictly, a German-maritime candidate sweep was
owed here the way it was for `sprout` (`sprout/spec/decisions/DEC-016-name.md`, thirty-plus
candidates generated and verified) — but none was run, and none has ever been run for a Minecraft
mod in this fleet. **Minecraft mods are a standing exception to the German-maritime naming theme**:
seven mods now carry plain, descriptive English names by Kevin's own direct choice each time —
`create_brass_compass`, `create_firearms`, `create_metered_motor`, `create_synthetic_diamonds`,
`create_villager_customers`, `villager_voices`, and this one — with `create_brass_compass` `DEC-002`
("Overrides naming-theme (descriptive English, no theme name)," 2026-09-19) the first recorded
instance. Recorded here as the standing pattern this decision follows, not a fresh divergence to
re-litigate — still logged in `README.md` "Divergences from heimathafen standards" since it is one,
just no longer an open question.

## The Modrinth slug is confirmed free

Proposed slug: `grounded-villages` (the hyphenated form of the mod id, standard Modrinth
convention). **Confirmed free**: checked directly against the live Modrinth API,
`api.modrinth.com/v2/project/grounded-villages`, 2026-09-20 (HTTP 404 = free) — by the
`village-jigsaw-placement-1-20-1-to-26-2.md` research pass, alongside a web search that found no
matching CurseForge project either. Unlike `villager_voices`' own `DEC-002` (whose first-choice slug
was taken and needed a fallback), no fallback is needed here.

## A prior-art mod exists — resolved

The same research pass surfaced **"Improved Village Placement"** (Apollounknowndev, MIT licence,
Fabric/Forge/NeoForge, 1.20.1→26.2 — the identical version/loader matrix this mod targets): it
already rejects whole village sites by sampling a 64-block-radius grid against `WORLD_SURFACE_WG`
height variance (±10 blocks), with one extra fixed "small village" variant. It has **no water/liquid
check and no size-tier system** — the two things this mod's `domains/pieces.md` (per-piece water
rejection) and `domains/tiers.md` (hamlet/village/town/city) add beyond it. **Kevin's ruling,
`decisions/DEC-009-prior-art.md`: build independently, no code reuse, credited as prior art** — this
mod is positioned as the superset (height plus water plus size tiers, in one mod), not a competitor
to avoid naming.

## No domain, no icon decided here

No domain registered, no six-TLD availability check run — no standalone web presence is planned for
this mod, same as `villager_voices`. No icon designed in this pass.
