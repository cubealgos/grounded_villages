# Grounded Villages

Villages generate on one consistent ground level, dry, and in a genuine range of sizes.

**First draft — GV-1 bootstrap.** The real config table, defaults, and exact version list are
confirmed at their own tickets (`GV-6` through `GV-9`) and this body is rewritten to match what
actually ships, per `GV-13`. Nothing below is a promise beyond what `docs/spec/00-context.md`
already states.

## Project settings

| Field | Value |
|---|---|
| Name | Grounded Villages |
| Slug | `grounded-villages` |
| Summary | Villages generate on one consistent ground level, dry, and in a genuine range of sizes — no more houses scattered across a cliff or streets running through a lake. |
| Categories | Worldgen, Game-Mechanics |
| Licence | MIT |
| Client side | Unsupported (server-authoritative worldgen; see `docs/spec/contracts/platform-matrix.md` "Client and server") |
| Server side | Required |
| Loaders | Fabric, NeoForge, Forge |
| Game versions | 1.20.1, 1.21.1, 26.2 (Wave 1 first; see the version matrix in `README.md`) |
| Dependencies | Fabric API (required, Fabric legs only) |
| Links | Source `https://git.cubealgos.de/cubealgos/grounded_villages` |

## Body

Vanilla village generation places each piece independently: nothing stops one house from landing
on a cliff edge two dozen blocks above its neighbour, or a street from running straight across a
lake. Grounded Villages fixes that — placement only, no terrain edits of its own.

### What it does

- **One ground level, within a tolerance.** Every piece sits close to the height the village
  started at.
- **Dry.** No piece's footprint sits in water — buildings or streets.
- **Compact.** A bad site searches nearby for a better one, rather than sprawling to accommodate
  bad terrain.
- **Sizes that vary.** Four tiers — hamlet, village, town, and a rare city — each a genuinely
  different settlement to find, rolled deterministically from the world seed.

### Prior art

["Improved Village Placement"](https://modrinth.com/mod/improved-village-placement)
(Apollounknowndev, MIT) already rejects whole village sites by height variance — Grounded Villages
is built independently, with no code reuse, and adds water rejection and size tiers on top: height
*plus* water *plus* size, together, in one mod.

### Configuration

`config/grounded_villages.json`, written with shipped defaults on first launch — thresholds, tier
weights, budgets, and the structure-tag scope are all retunable without touching Java. No in-game
reload at 1.0; a config edit takes effect on the next server start.

### Compatibility

Placement only: no terrain edits, so it works on top of any other worldgen mod (Terralith or
similar) with no dedicated compatibility patch and no integration code aimed at any specific mod by
name.

### Privacy

No telemetry, no update checks, no network calls of any kind.

### Support

Issues and questions go through the tracker only. No SLA.
