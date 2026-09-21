package grounded_villages.forge;

import grounded_villages.config.ConfigHolder;
import grounded_villages.config.ConfigIo;
import grounded_villages.hook.HookRegistry;
import grounded_villages.piece.PieceRejectionHook;
import grounded_villages.site.SiteStartHook;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge 1.20.1 entrypoint. Loads the config (GV-9) and registers the SITE hook (GV-6, {@code
 * grounded_villages.site.SiteStartHook}) and the PIECE hook (GV-7, {@code
 * grounded_villages.piece.PieceRejectionHook}); tier rolling (GV-8) needs no hook registration of
 * its own, wired directly into the mixin. Forge 1.20.1 predates the NeoForge fork, so its own
 * {@code FMLPaths} is still {@code net.minecraftforge}-namespaced, unlike the NeoForge nodes' {@code
 * net.neoforged} one (verified 2026-09-20 against {@code fmlloader-1.20.1-47.4.23.jar} via {@code
 * javap}).
 */
@Mod("grounded_villages")
public final class GroundedVillagesForge {
    private static final Logger LOGGER = LoggerFactory.getLogger("grounded_villages");

    public GroundedVillagesForge() {
        ConfigHolder.set(ConfigIo.loadOrCreate(FMLPaths.CONFIGDIR.get()));
        HookRegistry.setStartHook(SiteStartHook.INSTANCE);
        HookRegistry.setPieceHook(PieceRejectionHook.INSTANCE);
        LOGGER.info("Grounded Villages: skeleton loaded (Forge)");
    }
}
