package grounded_villages.forge;

import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge 1.20.1 entrypoint. Skeleton only (GV-2): no mixin registration, no config load, no
 * placement logic -- those land in their own domain tickets (SITE/PIECE/TIER/CONFIG,
 * docs/spec/04-architecture.md "Shape").
 */
@Mod("grounded_villages")
public final class GroundedVillagesForge {
    private static final Logger LOGGER = LoggerFactory.getLogger("grounded_villages");

    public GroundedVillagesForge() {
        LOGGER.info("Grounded Villages: skeleton loaded (Forge)");
    }
}
