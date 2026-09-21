---
title: "grounded_villages spec — compliance, security and governance"
type: "spec"
category: "grounded_villages"
---

# Compliance, security and governance (`COMP`, sheet §6)

| Area | Position |
|---|---|
| GDPR: what leaves the user's machine | Nothing. No telemetry, no update check, no outbound network call of any kind (`COMP-REQ-001`). |
| Hosted parts we run | None. Modrinth hosts the file and its page. |
| Impressumspflicht | Attaches to a public web presence; there is none beyond the platform pages. Revisit if a site exists. |
| Licence and notices | MIT (`decisions/DEC-003-licence.md`); `NOTICE` credits Fabric API (Apache-2.0) plus NeoForge and Forge, whose own licences are to verify at the first ticket, before each of those modules lands (`decisions/DEC-003-licence.md`). **This mod ships no original creative asset of its own** — no lines, no audio, no new textures, models, or structure pieces (`00-context.md` "what it will not do") — so unlike `villager_voices`, there is no project-authored-content notice to track, only the dependency one. |
| AI-content disclosure | Not applicable — no AI-generated or AI-assisted asset of any kind ships in this mod, since it ships no asset at all beyond compiled code and a config default. |
| Supply chain and release integrity | Builds from a tagged commit with pinned dependencies; the release checksum is in the release notes; no signing at 1.0. |
| Vulnerability disclosure | The public issue tracker only, on the GitHub mirror; no private channel, no e-mail address published. Forgejo stays the source of truth for code. |
| Server trust boundary | Site selection, per-piece rejection, and tier rolling are entirely server-side world-generation decisions (`04-architecture.md` "Runtime topology"); no client packet or input this mod trusts for any of it. |
| AI Act, GoBD, sector regulation | Not applicable — no AI feature, no financial-records handling, no regulated sector. |

`COMP-REQ-001`: the mod shall make no network call of its own, at build time in shipped code or at
runtime; a source-scan test asserts it, the same discipline the Create Fly siblings and
`villager_voices` `COMP-REQ-001` use.
`COMP-REQ-002`: the mod shall never edit, delete, or corrupt terrain or structures outside the scope
`decisions/DEC-007-village-tag-scope.md` and `decisions/DEC-005-placement-only.md` define — a
least-privilege boundary on what a world-generation mod is allowed to touch, stated plainly because
world generation is destructive and unattended by nature.

Open: release signing (minisign) before 1.0 or after, the same open item every sibling mod carries.
