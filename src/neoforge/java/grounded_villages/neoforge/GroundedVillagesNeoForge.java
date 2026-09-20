package grounded_villages.neoforge;

import grounded_villages.config.ConfigHolder;
import grounded_villages.config.ConfigIo;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge entrypoint, shared by the 1.21.1 and 26.2 nodes (no signature drift between them).
 * Loads the config (GV-9); no mixin registration, no placement logic yet -- those land in their
 * own domain tickets (SITE/PIECE/TIER, docs/spec/04-architecture.md "Shape").
 */
@Mod("grounded_villages")
public final class GroundedVillagesNeoForge {
    private static final Logger LOGGER = LoggerFactory.getLogger("grounded_villages");

    public GroundedVillagesNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        ConfigHolder.set(ConfigIo.loadOrCreate(FMLPaths.CONFIGDIR.get()));
        LOGGER.info("Grounded Villages: skeleton loaded (NeoForge)");
    }
}
