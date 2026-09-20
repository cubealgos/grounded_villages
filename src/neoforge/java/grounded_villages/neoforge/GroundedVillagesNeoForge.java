package grounded_villages.neoforge;

import grounded_villages.config.ConfigHolder;
import grounded_villages.config.ConfigIo;
import grounded_villages.hook.HookRegistry;
import grounded_villages.piece.PieceRejectionHook;
import grounded_villages.site.SiteStartHook;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge entrypoint, shared by the 1.21.1 and 26.2 nodes. The constructor itself has no
 * signature drift between them (GV-5/GV-9), but GV-12 found one real drift this class has to
 * straddle: {@code net.neoforged.fml.loading.FMLEnvironment}'s dev-environment check is the
 * public static final field {@code production} on {@code 1.21.1}'s own FancyModLoader {@code
 * 4.0.44} (bundled by NeoForge {@code 21.1.251}) and the method {@code isProduction()} on {@code
 * 26.2}'s FancyModLoader {@code 11.0.16} (bundled by NeoForge {@code 26.2.0.88}) -- confirmed by
 * {@code javap} against both real {@code loader-*.jar} artifacts, GV-12.
 *
 * <p>{@link #isDevelopment} resolves this reflectively rather than with a Stonecutter
 * preprocessor split, unlike {@code JigsawPlacementMixin}/{@code PlacerMixin}'s own version-delta
 * handling (`04-architecture.md` `ARCH-DEC-002`): those live under {@code src/main/java}, which
 * {@code stonecutterGenerate} actually preprocesses; this class lives under {@code
 * src/neoforge/java}, one of the extra loader-entrypoint source directories every {@code
 * build.<loader>.gradle.kts} adds as a plain {@code sourceSets.main.java.srcDir(...)} -- verified
 * live this ticket: a {@code //? if <26.1 { ... } else { ... }} split placed here compiles
 * unprocessed on every node (both branches' guard comments included verbatim), since only {@code
 * src/main/java} goes through Stonecutter's own generated-source step. Reflection is the correct
 * fix for a drift outside that preprocessed tree, not a reason to expand what Stonecutter
 * preprocesses.
 *
 * <p>Everything else in this file is genuinely identical source on both nodes.
 *
 * <p>Loads the config (GV-9) and registers the SITE hook (GV-6, {@code
 * grounded_villages.site.SiteStartHook}) and the PIECE hook (GV-7, {@code
 * grounded_villages.piece.PieceRejectionHook}) before any world loads -- the {@code @Mod}
 * constructor runs during NeoForge's mod-construction phase, strictly before {@code
 * FMLCommonSetupEvent} and every later lifecycle event, the same "before any world load" timing
 * {@code GroundedVillagesFabric.onInitialize} and {@code GroundedVillagesForge}'s own constructor
 * both give (`docs/loaders.md`'s parity table, GV-12); tier rolling (GV-8) needs no hook
 * registration of its own, wired directly into the mixin.
 *
 * <p>GV-12 also registers the seed-sweep harness's NeoForge twin ({@code
 * grounded_villages.harness.SeedSweepCommandNeoForge}, 26.2-neoforge only) the same way {@code
 * GroundedVillagesFabric} registers {@code SeedSweepCommand} -- looked up reflectively, never by
 * static import, so this shared constructor (compiled on both NeoForge nodes) keeps compiling on
 * 1.21.1-neoforge, which never has that class on its classpath.
 */
@Mod("grounded_villages")
public final class GroundedVillagesNeoForge {
    private static final Logger LOGGER = LoggerFactory.getLogger("grounded_villages");

    /**
     * GV-12's seed-sweep harness twin lives at {@code
     * grounded_villages.harness.SeedSweepCommandNeoForge}, compiled only on the 26.2-neoforge
     * node ({@code src/seedsweep-neoforge/java}, node-conditional in {@code
     * build.neoforge.gradle.kts}) -- absent on 1.21.1-neoforge by design.
     */
    private static final String SEED_SWEEP_HARNESS_CLASS = "grounded_villages.harness.SeedSweepCommandNeoForge";

    public GroundedVillagesNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        ConfigHolder.set(ConfigIo.loadOrCreate(FMLPaths.CONFIGDIR.get()));
        HookRegistry.setStartHook(SiteStartHook.INSTANCE);
        HookRegistry.setPieceHook(PieceRejectionHook.INSTANCE);
        LOGGER.info("Grounded Villages: skeleton loaded (NeoForge)");

        if (isDevelopment()) {
            tryRegisterSeedSweepHarness();
        }
    }

    /**
     * See this class's own javadoc for the confirmed {@code FMLEnvironment} API drift between
     * NeoForge {@code 21.1.251} (a {@code production} field) and {@code 26.2.0.88} (an {@code
     * isProduction()} method) this reflects around. Tries the 26.2 method shape first (the
     * currently-supported wave, `contracts/platform-matrix.md`), falls back to the 1.21.1 field
     * shape, and finally defaults to "not development" (the harness simply never registers) if
     * neither is found -- matching this whole feature's own fail-safe posture: a broken dev-only
     * harness check must never prevent {@link #GroundedVillagesNeoForge} itself from completing
     * config/hook registration.
     */
    private static boolean isDevelopment() {
        try {
            Class<?> env = Class.forName("net.neoforged.fml.loading.FMLEnvironment");
            try {
                return !(boolean) env.getMethod("isProduction").invoke(null);
            } catch (NoSuchMethodException methodNotPresent) {
                return !(boolean) env.getField("production").get(null);
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Grounded Villages: could not determine the NeoForge dev environment, " +
                "assuming production", e);
            return false;
        }
    }

    private void tryRegisterSeedSweepHarness() {
        try {
            Class<?> harness = Class.forName(SEED_SWEEP_HARNESS_CLASS);
            harness.getMethod("register").invoke(null);
        } catch (ClassNotFoundException expectedOnMostNodes) {
            // GV-12's harness only exists on the 26.2-neoforge node; absent here by design.
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Grounded Villages: seed-sweep harness present but failed to register", e);
        }
    }
}
