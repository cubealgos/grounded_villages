package grounded_villages.mixinsupport;

import grounded_villages.tier.TierAssignment;

/**
 * Bridges the tier roll {@code JigsawStructureMixin} makes once per village candidate (GV-8,
 * `docs/spec/domains/tiers.md`) to {@code JigsawPlacementMixin}'s write-back of {@code maxDepth}/
 * {@code max_distance_from_center} one call frame down -- the same cross-mixin-class handoff
 * shape {@link VillageTagContext} already uses for the {@code #minecraft:village} tag gate, and
 * for the same reason: {@code Structure.GenerationContext}'s own fields carry no reference back
 * to the tier roll made against it (`VillageTagContext`'s own javadoc explains the underlying
 * {@code javap} finding this mirrors).
 *
 * <p>Deliberately not in {@code grounded_villages.mixin} -- see {@link VillageTagContext}'s
 * javadoc for why a plain support class cannot live inside a declared Mixin package.
 *
 * <p>{@code null} means "no roll happened for this thread's current structure call" -- either the
 * structure is not village-tagged, or {@code tier.enabled} is {@code false}
 * (`grounded_villages.mixin.village.JigsawStructureMixin` skips the roll entirely rather than
 * consuming a random draw for nothing when tiers are off). Thread-local for the same reason as
 * {@link VillageTagContext}: {@code findGenerationPoint -> addPieces -> tryPlacingChildren} runs
 * synchronously on one worker thread per structure-generation call, but many such calls run
 * concurrently across chunks/threads, and each {@code findGenerationPoint} call overwrites the
 * previous value for its own thread before its own {@code addPieces} call reads it.
 */
public final class TierAssignmentContext {
    private static final ThreadLocal<TierAssignment> ASSIGNMENT = new ThreadLocal<>();

    private TierAssignmentContext() {
    }

    public static void set(TierAssignment assignment) {
        ASSIGNMENT.set(assignment);
    }

    /** {@code null} when no roll happened for this thread's current structure call. */
    public static TierAssignment get() {
        return ASSIGNMENT.get();
    }
}
