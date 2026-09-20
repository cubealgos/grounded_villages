---
title: "grounded_villages DEC-010 — Too many pieces lost: shrink, then move, then vanilla"
type: "spec"
category: "grounded_villages"
---

# `DEC-010` — Too many pieces lost: shrink, then move, then vanilla

**Status:** decided by Kevin, 2026-09-20, closing `domains/pieces.md` `PIECE-FAIL-001`.

A three-step fallback ladder, tried in order, so **no world loses a village outright**:

1. **Shrink.** If the pieces that survive per-piece rejection (`domains/pieces.md`) still reach the
   `hamlet` tier's minimum non-street piece count (`domains/tiers.md`'s `hamlet` minimum,
   `TIER-REQ-007`), keep the village as generated — labelled `hamlet` regardless of which tier was
   originally rolled, since a shrunk `town` that ends up hamlet-sized is, for every purpose that
   matters (footprint, piece count), a hamlet.
2. **Move.** If the surviving pieces fall short of even the hamlet minimum, retry the whole village
   at a shifted site — up to `site.search_attempts` shifted candidates (the same bounded search
   mechanism `domains/site.md` `SITE-REQ-003` already defines for a rejected *site*, reused here for
   a site that passed its own site-level check but still produced too few surviving pieces).
3. **Vanilla.** If no shifted candidate does any better, place the village exactly as vanilla would
   — no site check, no piece check — so the world never ends up with a missing village. This is the
   same fallback `domains/site.md` `SITE-REQ-004` already uses when the bounded site search itself is
   exhausted; step 3 here is that identical fallback, reached by a different path.

This resolves `domains/pieces.md`'s open question by picking neither of the two candidates it
named outright, but a specific ordering of both: shrink is tried before move, and move is tried
before falling back to vanilla, rather than jumping straight to a full retry or straight to
"accept whatever survives" unconditionally.

Alternative considered: always shrink, never retry the site. Rejected: a genuinely bad site (most
pieces rejected for water, say) could shrink all the way down to a token scattering of one or two
buildings, which reads as broken rather than as a valid hamlet — the hamlet-minimum threshold
(`TIER-REQ-007`) exists precisely to catch that case and escalate to a retry instead. Alternative
considered: always retry, never shrink. Rejected: unnecessary cost on the common case where a
village loses one or two pieces to a lake edge but easily still clears the hamlet minimum — shrinking
in place is strictly cheaper and produces a perfectly legible result. Cost if wrong: the three-step
order is itself config-adjacent behaviour (`domains/config.md`), not a hard architectural commitment
— re-ordering or dropping a step later is a logic change in one place, not a redesign.
