package grounded_villages.mixinsupport;

/**
 * The village's own "start height" (`03-glossary.md`: "the one ground height a village's site is
 * evaluated and accepted at; every piece's own height-deviation check is measured against this
 * single reference, not against each piece's own local terrain") -- set once per village
 * candidate by {@code grounded_villages.mixin.village.JigsawPlacementMixin}'s own {@code
 * gv$decideStart}, right after it settles on the final X/Z (kept, or shifted by {@code
 * SiteStartHook}), read by {@code grounded_villages.piece.PieceRejectionHook} for every child
 * candidate `PlacerMixin` evaluates afterwards on the same thread.
 *
 * <p>Measured at {@code OCEAN_FLOOR_WG} (true ground), not {@code WORLD_SURFACE_WG} -- deliberately
 * different from {@code SiteStartHook}'s own advisory-Y computation for a {@code Shift} (which
 * uses {@code WORLD_SURFACE_WG} to mirror vanilla's own {@code project_start_to_heightmap}
 * placement arithmetic): this value is never used to place anything, only as {@link
 * grounded_villages.piece.PieceGate}'s own height-deviation reference, and {@code
 * domains/pieces.md} {@code PIECE-REQ-003} compares a piece's own {@code OCEAN_FLOOR_WG} ground
 * height against it -- apples to apples, matching {@code domains/site.md}'s own height-spread
 * metric rather than vanilla's placement heightmap type.
 *
 * <p>Thread-local for the same reason as {@link VillageTagContext}/{@link
 * TierAssignmentContext}: {@code findGenerationPoint -> addPieces -> tryPlacingChildren} runs
 * synchronously on one worker thread per structure-generation call, but many such calls run
 * concurrently across chunks/threads; {@code null} means no village candidate is currently being
 * decided on this thread (not village-tagged, or {@code gv$decideStart} has not run yet for this
 * call) -- {@link grounded_villages.piece.PieceRejectionHook} fails open (accepts) rather than
 * crash when this is unexpectedly {@code null} (see its own javadoc).
 */
public final class StartHeightContext {
    private static final ThreadLocal<Integer> START_HEIGHT = new ThreadLocal<>();

    private StartHeightContext() {
    }

    public static void set(Integer startHeight) {
        START_HEIGHT.set(startHeight);
    }

    /** {@code null} when no village candidate is currently being decided on this thread. */
    public static Integer get() {
        return START_HEIGHT.get();
    }
}
