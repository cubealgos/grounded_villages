package grounded_villages.hook;

import net.minecraft.core.BlockPos;

/**
 * Called once per candidate village start position, from the {@code JigsawPlacement.addPieces}
 * mixin ({@code grounded_villages.mixin.village.JigsawPlacementMixin}), gated on the structure
 * carrying {@code #minecraft:village} (decisions/DEC-007-village-tag-scope.md). GV-5 shipped the
 * hook family only, no scoring logic: {@link #ACCEPT_ALL} is the shipped default, so behaviour
 * stays vanilla until domains/site.md's own logic (GV-6, {@code grounded_villages.site.SiteStartHook})
 * registers a real implementation via {@link HookRegistry#setStartHook}. GV-6 also wires the
 * decision this interface returns back into the live placement call (the mixin replaces {@code
 * pos} for a {@code Shift} before any piece is placed) -- GV-5 only logged it.
 *
 * <p>{@code maxDistanceFromCenter} (added GV-6): the jigsaw structure's own {@code
 * max_distance_from_center} in blocks, whatever the mixin's own version-specific parameter shape
 * for it decodes to (a plain {@code int} on 1.20.1/1.21.1, {@code
 * JigsawStructure.MaxDistance.horizontal()} on 26.2) -- the whole-village sampling radius
 * `domains/site.md` §3 and this ticket's build item 1 call for ("the tier's max distance, or
 * max_distance from the jigsaw structure, as the radius"; no tier system exists yet, GV-8).
 */
@FunctionalInterface
public interface VillageStartHook {
    VillageStartHook ACCEPT_ALL = (context, startPos, maxDistanceFromCenter) -> StartDecision.keep();

    StartDecision onStart(GenerationContext context, BlockPos startPos, int maxDistanceFromCenter);
}
