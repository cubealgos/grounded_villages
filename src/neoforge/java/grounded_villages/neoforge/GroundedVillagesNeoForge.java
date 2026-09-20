package grounded_villages.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge entrypoint, shared by the 1.21.1 and 26.2 nodes (no signature drift between them).
 * Skeleton only (GV-2): no mixin registration, no config load, no placement logic -- those land
 * in their own domain tickets (SITE/PIECE/TIER/CONFIG, docs/spec/04-architecture.md "Shape").
 */
@Mod("grounded_villages")
public final class GroundedVillagesNeoForge {
    private static final Logger LOGGER = LoggerFactory.getLogger("grounded_villages");

    public GroundedVillagesNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Grounded Villages: skeleton loaded (NeoForge)");
    }
}
