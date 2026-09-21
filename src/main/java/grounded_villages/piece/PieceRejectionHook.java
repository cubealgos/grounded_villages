package grounded_villages.piece;

import grounded_villages.config.ConfigHolder;
import grounded_villages.config.ConfigModel;
import grounded_villages.hook.HookDebug;
import grounded_villages.hook.PieceDecision;
import grounded_villages.hook.TerrainSampler;
import grounded_villages.hook.VillagePieceHook;
import grounded_villages.mixinsupport.PieceLadderContext;
import grounded_villages.mixinsupport.StartHeightContext;
import grounded_villages.site.HeightSampler;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * The live {@link VillagePieceHook} a loader entrypoint registers into {@link
 * grounded_villages.hook.HookRegistry#setPieceHook} (ticket GV-7 build item 1/3): the first real
 * implementation, now finally called per real child candidate rather than once per parent piece
 * (see {@code grounded_villages.mixin.village.PlacerMixin}'s own javadoc for the bytecode
 * evidence of where that changed). The one place in the {@code piece} package that touches a
 * Minecraft type (`04-architecture.md` {@code ARCH-DEC-002}) -- adapts {@link TerrainSampler}/
 * {@link BoundingBox} to the pure {@link HeightSampler}/{@link PieceFootprint} {@link PieceGate}
 * samples through, reads {@code piece.*} from {@link ConfigHolder} at call time (no live-reload
 * command at 1.0, `decisions/DEC-008-config-file.md` {@code CONFIG-REQ-005}, so nothing here
 * caches a config snapshot), and folds the result into {@link PieceLadderContext}'s own
 * per-attempt accumulator ({@code grounded_villages.mixin.village.JigsawStructureMixin}'s own
 * ladder reads that back once the whole assembly attempt finishes).
 *
 * <p>Streets and buildings ({@code PIECE-REQ-004}) are told apart only for the ladder's own
 * "non-street piece count" (`decisions/DEC-010-shrink-move-vanilla.md`), never for the
 * accept/reject criteria themselves (identical for both, {@link PieceGate#evaluate} takes no
 * piece-kind parameter at all): {@code element.getProjection() == TERRAIN_MATCHING} is
 * `domains/pieces.md` §3's own confirmed signal ("every street element ... already uses
 * TERRAIN_MATCHING").
 */
public final class PieceRejectionHook implements VillagePieceHook {

    /** Stateless; one shared instance registered by every loader entrypoint. */
    public static final PieceRejectionHook INSTANCE = new PieceRejectionHook();

    private PieceRejectionHook() {
    }

    @Override
    public PieceDecision onChild(PoolElementStructurePiece pieceCandidate, BoundingBox boundingBox, TerrainSampler groundSampler) {
        ConfigModel.Piece config = ConfigHolder.get().piece();
        boolean isStreet = pieceCandidate.getElement().getProjection() == StructureTemplatePool.Projection.TERRAIN_MATCHING;

        if (!config.enabled()) {
            // PIECE-REQ-005: behave exactly as vanilla, without sampling anything -- still
            // counted, so a harness run with piece.enabled=false reads as "0 rejected", not as
            // silence (PieceLadderContext#recordAccept is itself a no-op outside a ladder
            // attempt, so this is also safe to call when JigsawStructureMixin's own ladder wrap
            // was skipped entirely for the same reason).
            PieceLadderContext.recordAccept(isStreet);
            return PieceDecision.ACCEPT;
        }

        Integer startHeight = StartHeightContext.get();
        if (startHeight == null) {
            // Defensive: JigsawPlacementMixin's own gv$decideStart always runs before
            // tryPlacingChildren for a village-tagged structure (addPieces calls
            // tryPlacingChildren itself, never the reverse), so this should be unreachable in
            // practice -- but a missing value must never crash world generation
            // (04-architecture.md ARCH-FAIL-003's own "never a silent corruption" standard reads
            // the other way here: fail open to vanilla's own accept, not reject everything).
            PieceLadderContext.recordAccept(isStreet);
            HookDebug.fired("PieceGate", "accept (no start height recorded, failing open)");
            return PieceDecision.ACCEPT;
        }

        PieceFootprint footprint = new PieceFootprint(boundingBox.minX(), boundingBox.maxX(), boundingBox.minZ(), boundingBox.maxZ());
        PieceVerdict verdict = PieceGate.evaluate(toHeightSampler(groundSampler), footprint, startHeight, config.maxHeightDeviation());

        if (verdict.accepted()) {
            PieceLadderContext.recordAccept(isStreet);
            HookDebug.fired("PieceGate", isStreet ? "accept street" : "accept building");
            return PieceDecision.ACCEPT;
        }
        PieceLadderContext.recordReject(isStreet, verdict.reason());
        HookDebug.fired("PieceGate", (isStreet ? "reject street (" : "reject building (") + verdict.reason() + ")");
        return PieceDecision.REJECT;
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
}
