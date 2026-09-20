package grounded_villages.site;

import grounded_villages.config.ConfigHolder;
import grounded_villages.config.ConfigModel;
import grounded_villages.hook.GenerationContext;
import grounded_villages.hook.StartDecision;
import grounded_villages.hook.TerrainSampler;
import grounded_villages.hook.VillageStartHook;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The live {@link VillageStartHook} a loader entrypoint registers into {@link
 * grounded_villages.hook.HookRegistry#setStartHook} (ticket build item 3: GV-5 modeled {@code
 * Shift}/{@code Vanilla} but shipped only {@link VillageStartHook#ACCEPT_ALL}; this is the first
 * real implementation). The one place in the {@code site} package that touches a Minecraft type
 * (`04-architecture.md` {@code ARCH-DEC-002}: {@link SiteSearch}/{@link SiteScorer} are pure) --
 * adapts {@link TerrainSampler} to the pure {@link HeightSampler} the search logic samples
 * through, and converts its pure {@link SiteDecision} back to the hook family's own {@link
 * StartDecision}, computing the shifted start's advisory Y along the way.
 *
 * <p>Reads {@code site.*} from {@link ConfigHolder} at call time, not at registration time --
 * there is no live-reload command at 1.0 (`decisions/DEC-008-config-file.md` {@code
 * CONFIG-REQ-005}), but nothing about this class needs to change if one is ever added, since it
 * never caches a config snapshot of its own.
 */
public final class SiteStartHook implements VillageStartHook {

    /** Stateless; one shared instance registered by every loader entrypoint. */
    public static final SiteStartHook INSTANCE = new SiteStartHook();

    private SiteStartHook() {
    }

    @Override
    public StartDecision onStart(GenerationContext context, BlockPos startPos, int maxDistanceFromCenter) {
        ConfigModel.Site config = ConfigHolder.get().site();
        TerrainSampler sampler = context.terrainSampler();

        SiteDecision decision = SiteSearch.evaluateStart(
                toHeightSampler(sampler), startPos.getX(), startPos.getZ(), maxDistanceFromCenter, config);

        return toStartDecision(decision, sampler);
    }

    private static HeightSampler toHeightSampler(TerrainSampler sampler) {
        return new HeightSampler() {
            @Override
            public int groundHeight(int x, int z) {
                return sampler.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG);
            }

            @Override
            public int surfaceHeight(int x, int z) {
                return sampler.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG);
            }
        };
    }

    /**
     * {@code SiteDecision.Shift}'s Y is advisory only, per {@code
     * grounded_villages.mixin.village.JigsawPlacementMixin}'s own javadoc: vanilla's own {@code
     * project_start_to_heightmap} re-projection recomputes it from X/Z for every {@code
     * #minecraft:village}-tagged structure (`village-jigsaw-placement-1-20-1-to-26-2.md` §A
     * "Verified facts"). Sampled at {@code WORLD_SURFACE_WG}, the same heightmap type that
     * re-projection itself uses.
     */
    private static StartDecision toStartDecision(SiteDecision decision, TerrainSampler sampler) {
        if (decision instanceof SiteDecision.Shift shift) {
            SiteCoordinate to = shift.to();
            int y = sampler.getBaseHeight(to.x(), to.z(), Heightmap.Types.WORLD_SURFACE_WG);
            return StartDecision.shift(new BlockPos(to.x(), y, to.z()));
        }
        if (decision instanceof SiteDecision.Vanilla) {
            return StartDecision.vanilla();
        }
        return StartDecision.keep();
    }
}
