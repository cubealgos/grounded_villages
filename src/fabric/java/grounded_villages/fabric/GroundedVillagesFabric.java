package grounded_villages.fabric;

import grounded_villages.config.ConfigHolder;
import grounded_villages.config.ConfigIo;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric entrypoint. Loads the config (GV-9); no mixin registration, no placement logic yet --
 * those land in their own domain tickets (SITE/PIECE/TIER, docs/spec/04-architecture.md "Shape").
 */
public final class GroundedVillagesFabric implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("grounded_villages");

    /**
     * GV-10's seed-sweep harness lives at {@code grounded_villages.harness.SeedSweepCommand},
     * compiled only on the 26.2-fabric node ({@code src/seedsweep/java}, node-conditional in
     * {@code build.fabric.gradle.kts}) -- absent on every other node by design. Looked up
     * reflectively, never by static import, so this shared entrypoint (compiled on all three
     * Fabric nodes) keeps compiling everywhere regardless of whether the class exists.
     */
    private static final String SEED_SWEEP_HARNESS_CLASS = "grounded_villages.harness.SeedSweepCommand";

    @Override
    public void onInitialize() {
        ConfigHolder.set(ConfigIo.loadOrCreate(FabricLoader.getInstance().getConfigDir()));
        LOGGER.info("Grounded Villages: skeleton loaded (Fabric)");

        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            tryRegisterSeedSweepHarness();
        }
    }

    private void tryRegisterSeedSweepHarness() {
        try {
            Class<?> harness = Class.forName(SEED_SWEEP_HARNESS_CLASS);
            harness.getMethod("register").invoke(null);
        } catch (ClassNotFoundException expectedOnMostNodes) {
            // GV-10's harness only exists on the 26.2-fabric node; absent here by design.
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Grounded Villages: seed-sweep harness present but failed to register", e);
        }
    }
}
