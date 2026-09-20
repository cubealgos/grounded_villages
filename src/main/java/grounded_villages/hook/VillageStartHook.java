package grounded_villages.hook;

import net.minecraft.core.BlockPos;

/**
 * Called once per candidate village start position, from the {@code JigsawPlacement.addPieces}
 * mixin ({@code grounded_villages.mixin.village.JigsawPlacementMixin}), gated on the structure
 * carrying {@code #minecraft:village} (decisions/DEC-007-village-tag-scope.md). GV-5 ships the
 * hook family only, no scoring logic: {@link #ACCEPT_ALL} is the shipped default, so behaviour
 * stays vanilla until domains/site.md's own logic (GV-6) registers a real implementation via
 * {@link HookRegistry#setStartHook}.
 */
@FunctionalInterface
public interface VillageStartHook {
    VillageStartHook ACCEPT_ALL = (context, startPos) -> StartDecision.keep();

    StartDecision onStart(GenerationContext context, BlockPos startPos);
}
