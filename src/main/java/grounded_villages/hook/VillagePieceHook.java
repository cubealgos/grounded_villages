package grounded_villages.hook;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;

/**
 * Called as vanilla's own jigsaw assembly proposes a piece, from the
 * {@code JigsawPlacement$Placer.tryPlacingChildren} mixin
 * ({@code grounded_villages.mixin.village.PlacerMixin}), gated on the structure carrying
 * {@code #minecraft:village} (decisions/DEC-007-village-tag-scope.md). Streets get no exemption
 * (domains/pieces.md {@code PIECE-REQ-004}) -- this hook does not distinguish piece kind, exactly
 * as the domain requires. GV-5 ships the hook family only, no scoring logic: {@link #ACCEPT_ALL}
 * is the shipped default, so behaviour stays vanilla until domains/pieces.md's own logic (GV-7)
 * registers a real implementation via {@link HookRegistry#setPieceHook}.
 *
 * <p><b>Granularity, stated plainly</b>: {@code tryPlacingChildren} fires once per already-placed
 * piece as vanilla tries to expand <i>its</i> children, not once per individual child candidate
 * (confirmed by direct bytecode read, GV-5 -- vanilla's own candidate loop is private method body,
 * not a parameter boundary a mixin can intercept without a deeper redirect). {@code pieceCandidate}
 * below is therefore the piece being expanded, and {@code boundingBox} its own already-placed
 * footprint -- accurate for logging and for the wiring this ticket proves, but real per-child
 * accept/reject (domains/pieces.md {@code PIECE-REQ-001}-{@code 004}) needs a finer injection
 * GV-7 adds, consistent with {@code PIECE-FAIL-002} already being an open verification item, not
 * a closed design question, per the ticket.
 */
@FunctionalInterface
public interface VillagePieceHook {
    VillagePieceHook ACCEPT_ALL = (pieceCandidate, boundingBox, groundSampler) -> PieceDecision.ACCEPT;

    PieceDecision onChild(PoolElementStructurePiece pieceCandidate, BoundingBox boundingBox, TerrainSampler groundSampler);
}
