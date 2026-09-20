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

    @Override
    public void onInitialize() {
        ConfigHolder.set(ConfigIo.loadOrCreate(FabricLoader.getInstance().getConfigDir()));
        LOGGER.info("Grounded Villages: skeleton loaded (Fabric)");
    }
}
