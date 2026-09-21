---
title: "grounded_villages DEC-009 — Prior art: independent superset, no code reuse, credited"
type: "spec"
category: "grounded_villages"
---

# `DEC-009` — Prior art: independent superset, no code reuse, credited

**Status:** decided by Kevin, 2026-09-20, closing the open question `decisions/DEC-002-name.md`
raised.

"Improved Village Placement" (Apollounknowndev, MIT, the identical Fabric/Forge/NeoForge
1.20.1→26.2 matrix) already does whole-site height-variance rejection
(`village-jigsaw-placement-1-20-1-to-26-2.md`, verified facts). Kevin's ruling: **build
independently — our own site scoring, no code reuse — and credit it as prior art**, positioning
Grounded Villages as the superset: height *plus* water *plus* size tiers in one mod, where the
prior-art mod covers only the height half and has no water check and no tier system.

- **No code reuse.** `domains/site.md`'s sampling and scoring mechanism (`SITE-REQ-001`–`005`) is
  designed and implemented independently; nothing is ported, copied, or derived from Improved
  Village Placement's own source, which was never read by either research pass (only its public
  Modrinth listing and description).
- **Credited in `00-context.md`** as prior art in the "who it is for" / positioning framing, and in
  the Modrinth listing's own body copy once the repo exists — not silently ignored, not named as a
  competitor to avoid, simply acknowledged as an existing tool covering part of the same problem.
- **Differentiation is the positioning**, not a marketing claim to prove: height-spread rejection,
  water rejection, and hamlet/village/town/city size tiers together, in one mod — the superset
  Improved Village Placement does not offer today.

This is a narrower, more specific question than `villager_voices`' own positioning ruling
(`villager_voices/spec/decisions/DEC-009-positioning.md`, which bars any mention of a specific
commercial IP by name): there is no IP-holder concern here, MIT permits reuse outright, and Kevin's
ruling is to credit rather than avoid naming it.

Alternative considered: fork or directly reuse Improved Village Placement's own site-rejection code
as a starting point, since MIT permits it. Rejected: `domains/site.md`'s design (whole-radius
height-spread *and* water-fraction sampling together, `SITE-REQ-001`–`002`) is more than a
height-only variance check, and building independently keeps the codebase's own shape fully under
this mod's own architecture (`04-architecture.md`) rather than adapting someone else's. Cost if
wrong: negligible — nothing here forecloses citing or studying the prior art's approach later if a
specific technique proves genuinely reusable; "no code reuse" is a starting posture, not a
irreversible constraint.
