package grounded_villages.forge;

import grounded_villages.config.ConfigHolder;
import grounded_villages.config.ConfigIo;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge 1.20.1 entrypoint. Loads the config (GV-9); no mixin registration, no placement logic
 * yet -- those land in their own domain tickets (SITE/PIECE/TIER, docs/spec/04-architecture.md
 * "Shape"). Forge 1.20.1 predates the NeoForge fork, so its own {@code FMLPaths} is still
 * {@code net.minecraftforge}-namespaced, unlike the NeoForge nodes' {@code net.neoforged} one
 * (verified 2026-09-20 against {@code fmlloader-1.20.1-47.4.23.jar} via {@code javap}).
 */
@Mod("grounded_villages")
public final class GroundedVillagesForge {
    private static final Logger LOGGER = LoggerFactory.getLogger("grounded_villages");

    public GroundedVillagesForge() {
        ConfigHolder.set(ConfigIo.loadOrCreate(FMLPaths.CONFIGDIR.get()));
        LOGGER.info("Grounded Villages: skeleton loaded (Forge)");
    }
}
