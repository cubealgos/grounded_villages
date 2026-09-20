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
 * NeoForge entrypoint, shared by the 1.21.1 and 26.2 nodes (no signature drift between them).
 * Loads the config (GV-9) and registers the SITE hook (GV-6, {@code
 * grounded_villages.site.SiteStartHook}) and the PIECE hook (GV-7, {@code
 * grounded_villages.piece.PieceRejectionHook}); tier rolling (GV-8) needs no hook registration of
 * its own, wired directly into the mixin.
 */
@Mod("grounded_villages")
public final class GroundedVillagesNeoForge {
    private static final Logger LOGGER = LoggerFactory.getLogger("grounded_villages");

    public GroundedVillagesNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        ConfigHolder.set(ConfigIo.loadOrCreate(FMLPaths.CONFIGDIR.get()));
        HookRegistry.setStartHook(SiteStartHook.INSTANCE);
        HookRegistry.setPieceHook(PieceRejectionHook.INSTANCE);
        LOGGER.info("Grounded Villages: skeleton loaded (NeoForge)");
    }
}
