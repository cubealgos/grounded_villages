package grounded_villages.fabric;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric entrypoint. Skeleton only (GV-2): no mixin registration, no config load, no placement
 * logic -- those land in their own domain tickets (SITE/PIECE/TIER/CONFIG,
 * docs/spec/04-architecture.md "Shape").
 */
public final class GroundedVillagesFabric implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("grounded_villages");

    @Override
    public void onInitialize() {
        LOGGER.info("Grounded Villages: skeleton loaded (Fabric)");
    }
}
