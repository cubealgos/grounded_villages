# grounded_villages

A Fabric/NeoForge/Forge Minecraft mod, 1.20.1 through 26.2: villages generate on one consistent
ground level, dry, with a genuine range of sizes. Placement only — no terrain edits of this mod's
own.

**This file routes. It does not hold content.** The specification is `docs/spec/`.

## Read this before you do that

| about to… | read first |
|---|---|
| anything at all | `docs/spec/README.md`, then the **one** domain file you need |
| find where something lives | `docs/map.md`; generated, never edited |
| touch the mixin hook (`JigsawPlacement`/`Placer`) | `docs/spec/04-architecture.md` `ARCH-DEC-001` |
| touch site selection | `docs/spec/domains/site.md` |
| touch per-piece rejection, including streets | `docs/spec/domains/pieces.md` |
| touch size tiers | `docs/spec/domains/tiers.md` |
| touch the config file | `docs/spec/domains/config.md`, `docs/spec/decisions/DEC-008-config-file.md` |
| add a loader or Minecraft version | `docs/spec/contracts/platform-matrix.md`, `docs/spec/04-architecture.md` `ARCH-DEC-002` |
| add a dependency | `docs/spec/decisions/DEC-003-licence.md` (MIT, no CLA) |
| commit | scope `grounded_villages`, the ticket key (`GV-N`) in the subject |

## Working here

```
kontor claim GV-N
kontor branch new GV-N <slug>
just check
```

`just --list` shows the task surface; `just spec-sync` refreshes `docs/spec/` from the vault;
`just map` regenerates the map.

## Standing rules

- **Placement only.** No terrain edits of this mod's own — it only decides which of vanilla's own
  candidate sites and pieces are allowed to proceed (`docs/spec/decisions/DEC-005-placement-only.md`).
- The spec is authoritative; `docs/spec/` is a copy of heimathafen's vault, never a symlink.
- **A design question the spec does not answer is asked, never decided inline.**
- No new buildings, no new villager behaviour, no village removal
  (`docs/spec/00-context.md` "what it will not do").
- The mod makes no network call of its own, at build time in shipped code or at runtime
  (`docs/spec/operations/compliance.md` `COMP-REQ-001`).
- Always keep a playable build once Wave 1 lands: `just client` boots on a Wave 1 combination at
  every merge.
