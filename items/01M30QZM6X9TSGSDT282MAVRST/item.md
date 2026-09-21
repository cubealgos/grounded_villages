---
schema_version: 1
id: 01M30QZM6X9TSGSDT282MAVRST
key: GV-26
type: docs
title: "Icon: bell at 70% fit box on plain navy (icon rulings 2026-09-21)"
created_by: kevin
created_at: 2026-09-21T01:06:14Z
---

## Scope

`tools/icon.py`'s badge for the village bell, per Kevin's icon rulings (2026-09-21): pixel-art
items sit at 70% of the previous fit box, and mods that are not Create add-ons get a plain navy
badge without the blueprint grid. Grounded Villages is not a Create Fly add-on, so both rulings
apply: the bell's fit box shrinks to 70% and the badge drops the blueprint grid (and its centre
glow) for a plain navy disc. `docs/modrinth/icon.png` is regenerated from the new badge; not the
candidate-sheet machinery (`--sheet`, `--pick`) or the other candidates it can still render.

## Approach

`tools/icon.py` already exposes `FIT_BOX = 320` and a `badge()` function it composes the pixel-art
`flat-sprite` candidate onto (`compose_pixelart(badge(), sprite)`, the default `--pick`). Add a
`grid: bool = True` parameter to `badge()` that skips the grid-line/glow layer when `False`
(mirroring the fleet tool's `navy-badge.py --no-grid`), and shrink `FIT_BOX` by the 70% ruling
(`round(320 * 0.7)` = 224, or a `FIT = 0.7` multiplier applied where `FIT_BOX` is used, to keep the
ruling visible as a multiplier rather than a new magic number). Call `badge(grid=False)` in
`main()`'s icon path so `docs/modrinth/icon.png` renders plain navy. Regenerate with `just icon`
(or `python3 tools/icon.py`) and confirm against the reviewed candidate (`bell @ 70%, plain navy`
in the icon-variants contact sheet, matching style `1a bell / plain navy`).

## Acceptance criteria

- [x] `tools/icon.py`'s `badge()` takes a `grid` parameter; the default icon path renders with
      `grid=False` (plain navy, no blueprint grid, no centre glow).
- [x] The bell's fit box is 70% of its previous value (320 -> 224px).
- [x] `docs/modrinth/icon.png` is regenerated and visually matches the reviewed "bell @ 70%, plain
      navy" candidate (style `1a`).
- [x] `just check` (or at minimum the tools tests and `just map`) passes.
- [x] Committed on `documentation/gv-26-icon-size-70` and pushed; no PR, no `kontor finish`.

## Constraints and prior findings

Kevin's icon rulings, 2026-09-21: pixel-art items sit at 70% of the previous fit box; mods that are
not Create add-ons get a plain navy badge without the blueprint grid. Grounded Villages adds no
block or item of its own (`00-context.md`) and is not part of the Create Fly add-on family the
blueprint-grid theme signals, so both rulings apply together here -- this is the one repo of the
three that also drops the grid. Style choice `1a bell / plain navy` (flat solid disc, no grid, no
glow) confirmed against
`/private/tmp/claude-501/-Users-kevin-Documents-git-personal-create-civilization/
bb7bc244-01b6-4890-9ca3-da5d384dc21e/scratchpad/icon-variants.png`, sections 1 and 2. The default
`--pick` is already `flat-sprite` (pixel-art mode), so the size/grid change only touches
`FIT_BOX`/`badge()`, not which candidate is picked.
