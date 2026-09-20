# Loader entrypoint parity (GV-12)

`04-architecture.md`'s "Shape" states the goal plainly: one shared `src/main/` tree, thin
per-loader entrypoints, the same decisions running at the same moment on every leg. GV-9 wired
`ConfigIo.loadOrCreate` into all three loader entrypoints; this ticket is the audit GV-12's own
acceptance criteria ask for -- confirm that wiring is actually identical in substance, not merely
similar in shape, and fix whatever isn't. **One real divergence was found** (the NeoForge dev-check
API, below); everything else audited was already at parity coming into this ticket.

## The parity table

| | Fabric (`GroundedVillagesFabric`) | NeoForge (`GroundedVillagesNeoForge`) | Forge 1.20.1 (`GroundedVillagesForge`) |
|---|---|---|---|
| Entrypoint hook | `ModInitializer.onInitialize()` | `@Mod` constructor (`IEventBus`, `ModContainer`) | `@Mod` constructor (no-arg) |
| Config directory API | `FabricLoader.getInstance().getConfigDir()` | `FMLPaths.CONFIGDIR.get()` (`net.neoforged.fml.loading`) | `FMLPaths.CONFIGDIR.get()` (`net.minecraftforge.fml.loading` -- pre-fork namespace, confirmed live against `fmlloader-1.20.1-47.4.23.jar` via `javap`, GV-9's own comment) |
| Config load call | `ConfigIo.loadOrCreate(...)`, first statement | `ConfigIo.loadOrCreate(...)`, first statement | `ConfigIo.loadOrCreate(...)`, first statement |
| Hook registration order | `HookRegistry.setStartHook` then `setPieceHook`, immediately after config | same order, same place | same order, same place |
| Before any world load? | Yes -- `onInitialize` is Fabric's general-purpose init entrypoint, run to completion before any `ServerLevel`/`ClientLevel` exists | Yes -- the `@Mod` constructor runs during FML's mod-construction phase, strictly before `FMLCommonSetupEvent` and every later lifecycle event, in turn strictly before any level is created | Yes -- same FML lineage as NeoForge, same phase |
| Mixin config declared via | `fabric.mod.json`'s `"mixins": ["grounded_villages.mixins.json"]` array (Fabric's own loader-level mechanism) | `META-INF/neoforge.mods.toml`'s `[[mixins]]` table, `config = "grounded_villages.mixins.json"` (NeoForge's declarative mechanism since 20.3) | Jar manifest `MixinConfigs: grounded_villages.mixins.json` attribute (`build.forge.gradle.kts`'s `named<Jar>("jar") { manifest.attributes(...) }`) -- Forge 1.20.1 has no declarative `[[mixins]]` story; SpongePowered Mixin self-bootstraps via ModLauncher and reads this attribute itself, confirmed by `04-architecture.md` `ARCH-DEC-001`'s own research read |
| Mixin transform relative to entrypoint | Applied by Fabric's own Mixin bootstrap during class loading, strictly before `onInitialize` runs | Applied by ModLauncher's Mixin transformer during class loading, strictly before the `@Mod` constructor runs | Same as NeoForge -- Forge 1.20.1 self-bootstraps the identical SpongePowered Mixin machinery via ModLauncher |
| `HookRegistry`/`ConfigHolder` state at the moment the first structure generates | Set once, synchronously, inside the single-threaded entrypoint call every loader blocks server startup on; no level (and therefore no structure) can exist before that call returns | same | same |

**Net finding**: all three legs run `ConfigIo.loadOrCreate` then `HookRegistry.setStartHook`/
`setPieceHook`, in that order, as the first thing their entrypoint does, and all three loaders'
own mixin-transform step (a class-load-time bytecode rewrite, orthogonal to and always earlier
than mod construction/initialization) is already complete before any of those entrypoints run.
Since world/structure generation cannot begin until the server has finished constructing every
mod (every loader here blocks on that), `HookRegistry`/`ConfigHolder` are provably in the same
state, populated the same way, at the moment the first structure generates -- on all three loaders,
every targeted version. **No divergence in what runs or when**, matching GV-12's own acceptance
criteria wording exactly.

## The one real divergence this ticket found and fixed

Not in the config/hook wiring above -- in a NeoForge-internal API the entrypoint uses to gate the
dev-only seed-sweep harness registration (GV-12's own new code, not GV-9's). `04-architecture.md`'s
own NeoForge entrypoint javadoc previously claimed "no signature drift between" the 1.21.1 and
26.2 nodes; that claim held for the constructor itself, but not for
`net.neoforged.fml.loading.FMLEnvironment`'s dev-environment check, confirmed by direct `javap`
against both real `loader-*.jar` artifacts (GV-12):

| NeoForge version | Bundled FancyModLoader | Dev-environment check |
|---|---|---|
| `21.1.251` (1.21.1 node) | `4.0.44` | `public static final boolean production` -- a field |
| `26.2.0.88` (26.2 node) | `11.0.16` | `public static boolean isProduction()` -- a method |

**A real finding, not just the drift itself**: this ticket first tried the same Stonecutter
preprocessor split `JigsawPlacementMixin`/`PlacerMixin` already use for their own version deltas
(`//? if <26.1 { ... } else { ... }`, `04-architecture.md` `ARCH-DEC-002`) and it silently
compiled the wrong branch on `26.2-neoforge` -- confirmed live this ticket:
`:26.2-neoforge:compileJava` failed on the `<26.1` (field-access) branch, unprocessed, comment
markers and all, on the 26.2 node where it should never have been active. Cause, read out of
`versions/26.2-neoforge/build/generated/stonecutter/main/java/`: **Stonecutter only preprocesses
`src/main/java`** (the conventional main-sourceSet root each `build.<loader>.gradle.kts` gets by
default) -- the extra loader-entrypoint directories every build script adds via a plain
`sourceSets.main.java.srcDir(rootProject.file("src/<loader>/java"))` call (`04-architecture.md`
"Shape") are ordinary Java source roots on that node's compile classpath, never routed through
`stonecutterGenerate` at all. Every existing `//? if` block in this codebase happens to live under
`src/main/java/grounded_villages/mixin/`, so this gap was never exercised before GV-12.
`GroundedVillagesNeoForge.isDevelopment()` resolves the drift reflectively instead (tries
`isProduction()`, falls back to the `production` field, defaults to "not development" if neither
resolves) -- the correct fix for a drift outside Stonecutter's preprocessed tree, not a reason to
widen what that tree covers. **Flagged for Kevin**: any future per-version drift inside
`src/<loader>/java` will hit this same gap; widening `stonecutterGenerate`'s scope to those
directories (if a future ticket needs a real preprocessor split there) is a build-tooling change
this ticket did not make, out of its own "additive, entrypoint-and-docs-only" scope.

## `[[mixins]]` metadata format: confirmed identical across both NeoForge versions

GV-12's own build item 2 asked to verify the exact `[[mixins]]` key for NeoForge `21.1.251` and
`26.2.0.88`, and to say so if 26.2's NeoForge changed the metadata format. It did not: decompiling
`ModFileParser`/`ModFileParser$MixinConfig` out of both real `loader-*.jar` artifacts
(`net.neoforged.fancymodloader:loader:4.0.44` for `21.1.251`, `:11.0.16` for `26.2.0.88`) shows
the identical shape on both -- a top-level `[[mixins]]` array of tables, each with a required
`config` string key (`"Missing \"config\" in [[mixins]] entry"` is the loader's own error text on
both jars) and an optional `requiredMods` list; `grounded_villages.mixins.json`'s own entry in
`neoforge.mods.toml` already used exactly this shape before this ticket (GV-9), and needed no
change. No manifest-based mixin registration exists on the NeoForge build at all
(`build.neoforge.gradle.kts` sets no `MixinConfigs` jar-manifest attribute, unlike
`build.forge.gradle.kts`'s own Forge-1.20.1-only one), so there was nothing redundant to remove --
NeoForge's declarative `[[mixins]]` block was already the only registration path.

## The seed-sweep harness's NeoForge twin

GV-10's own headless seed-sweep harness (`docs/baseline/README.md`) is Fabric-node-bound: its
Gradle tasks (`seedSweep`, `harnessStatsTest`) and its dev-only in-game hook (`SeedSweepCommand`,
`net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents`) live only in
`build.fabric.gradle.kts` / `src/seedsweep/java`, node-conditional on `26.2-fabric`. GV-12 adds the
NeoForge twin as **a second task**, not a `-Pnode=` switch -- mirroring the existing per-node-
conditional-block pattern `build.fabric.gradle.kts` already established, rather than adding a new
cross-cutting flag to a task that would otherwise need to special-case which loader's `runServer`
task path, run directory, and source set to touch:

| | Fabric (GV-10) | NeoForge (GV-12) |
|---|---|---|
| Gradle task | `:26.2-fabric:seedSweep` | `:26.2-neoforge:seedSweep` |
| `just` recipe | `just sweep <N>` | `just sweep-neoforge <N>` |
| Server-started hook | `ServerLifecycleEvents.SERVER_STARTED` (Fabric API) | `net.neoforged.neoforge.event.server.ServerStartedEvent` on `NeoForge.EVENT_BUS` |
| Command registration | Registered directly in the `SERVER_STARTED` handler (server reference already in scope) | `net.neoforged.neoforge.event.RegisterCommandsEvent` on `NeoForge.EVENT_BUS`, server read back via `context.getSource().getServer()` |
| Shared core | `SeedSweepRunner`/`SeedSweepStats`/`VillageSweepResult`/`SeedSweepStatsTest` (`src/seedsweep/java`, zero loader import) | same classes, same source directory, reused verbatim |
| Loader-specific glue | `SeedSweepCommand` (`src/seedsweep/java`, the one Fabric-API-importing file in that directory) | `SeedSweepCommandNeoForge` (`src/seedsweep-neoforge/java`) -- same method shapes, same flow, different event source |

The harness twin is `26.2-neoforge`-only, the same one-node scope GV-10's own Fabric harness
already has (`26.2-fabric`-only) -- `1.21.1-neoforge` never compiles against
`SeedSweepCommandNeoForge` at all (`build.neoforge.gradle.kts`'s own node-conditional block), so
this ticket did not need to verify `ServerStartedEvent`/`RegisterCommandsEvent` across both
NeoForge versions the way the `FMLEnvironment` drift above did. Both classes were confirmed
present and correctly shaped for `26.2.0.88` specifically by direct decompilation of the real
`neoformRuntime`-produced `compiledWithNeoForge` jar this project's own `26.2-neoforge` build
already produces, and live: `:26.2-neoforge:seedSweep`'s own three-seed run (below) proves both
events fire correctly at runtime, not just that the classes exist at compile time.

## Cross-loader determinism: the real parity test

Identical numbers across loaders for the same seed is the test that actually matters here -- see
"NeoForge proof" in `docs/baseline/README.md` for the live-server log evidence and the three-seed
comparison table against the Fabric baseline.
