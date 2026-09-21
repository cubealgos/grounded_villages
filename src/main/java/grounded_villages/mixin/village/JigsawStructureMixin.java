package grounded_villages.mixin.village;

import grounded_villages.config.ConfigHolder;
import grounded_villages.config.ConfigModel;
import grounded_villages.hook.HookDebug;
import grounded_villages.hook.TierAssignmentRegistry;
import grounded_villages.mixinsupport.TierAssignmentContext;
import grounded_villages.mixinsupport.VillageTagContext;
import grounded_villages.tier.TierAssignment;
import grounded_villages.tier.TierRoller;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Sets the {@code #minecraft:village} tag gate (decisions/DEC-007-village-tag-scope.md) that
 * {@link JigsawPlacementMixin} and {@link PlacerMixin} read via {@link VillageTagContext}.
 *
 * <p>{@code Structure.GenerationContext} carries no reference back to the {@code Structure}
 * (and therefore no registry holder) that owns it -- confirmed by a direct {@code javap} read of
 * the record's fields (GV-5), correcting {@code 04-architecture.md} {@code ARCH-DEC-001}'s own
 * phrasing ("gated by testing the structure's registry holder"), which does not name where that
 * holder comes from. {@code findGenerationPoint} is the one place in the vanilla call chain with
 * a {@code this} (the {@code JigsawStructure} instance itself) to test -- {@code
 * BuiltInRegistries.STRUCTURE} named by the research and the architecture doc does not exist
 * either (structures are a datapack-driven <i>dynamic</i> registry, not a built-in one); the real
 * API is {@code context.registryAccess().registryOrThrow(Registries.STRUCTURE)}, confirmed
 * present and stable 1.20.1-26.2 by {@code javap} (GV-5).
 *
 * <p>{@code findGenerationPoint}'s own signature ({@code Structure.GenerationContext ->
 * Optional<Structure.GenerationStub>}) is identical on every targeted version (javap, GV-5) --
 * no Stonecutter preprocessor conditional needed for the injection itself. One is needed for the
 * registry lookup inside the handler body, though: {@code RegistryAccess.registryOrThrow} is
 * renamed {@code lookupOrThrow} well before 26.2 -- GV-5 only had 1.20.1/1.21.1/26.2 jars to
 * {@code javap} and (reasonably, from those three points alone) assumed the rename landed at the
 * same 26.1 boundary {@code JigsawPlacement}'s own parameter-list changes do. GV-17's own {@code
 * javap} against the real 1.21.4 jar (the first Wave 3 node built) found this wrong: {@code
 * registryOrThrow} is already gone on 1.21.4 -- {@code RegistryAccess} exposes only {@code
 * lookupOrThrow} there, confirmed by decompiling the Loom-merged jar directly, not inferred. The
 * exact removal point between 1.21.2 and 1.21.3 is not pinned (this ladder targets neither), but
 * the boundary is real and at latest 1.21.4, not 26.1 -- {@code contracts/platform-matrix.md}
 * "Wave 3 nodes (GV-17)" has the corrected finding. Not named in {@code
 * contracts/platform-matrix.md}'s own version-delta table before this ticket, which only tracked
 * {@code JigsawPlacement}/{@code Placer} signatures, not this call.
 *
 * <p><b>GV-8 also rolls the tier here</b> (`docs/spec/domains/tiers.md`), not inside {@code
 * JigsawPlacementMixin}: this is the one place in the vanilla call chain with a {@code this} to
 * gate on the village tag at all, and {@code Structure.GenerationContext} (the same record object
 * {@code findGenerationPoint} hands straight through to {@code JigsawPlacement.addPieces}, per
 * this class's own bytecode read) already carries {@code random()} -- the seeded {@code
 * WorldgenRandom} {@code TIER-REQ-001} rolls from -- so no new context needs threading in. Rolling
 * here, once, keeps the random draw single and ordering-independent: {@link
 * TierAssignmentContext} is set before {@code JigsawPlacementMixin}'s write-back methods ever run,
 * since {@code findGenerationPoint} calls {@code addPieces} itself (this method's javadoc above),
 * never the other way around.
 *
 * <p><b>GV-7's own shrink/move/vanilla ladder (`decisions/DEC-010-shrink-move-vanilla.md`) does
 * not live here</b>, even though this class gates on the village tag: {@code findGenerationPoint}
 * only ever calls {@code JigsawPlacement.addPieces} once and gets back an {@code Optional<
 * Structure.GenerationStub>} whose actual piece placement is <i>deferred</i> -- {@code addPieces}
 * builds a {@code Consumer<StructurePiecesBuilder>} and hands it to the returned stub
 * unexecuted (confirmed by {@code javap}: the lambda backing {@code tryPlacingChildren}'s whole
 * call chain is only ever passed to {@code new Structure.GenerationStub(pos, consumer)}, never
 * invoked inside {@code addPieces} itself). Vanilla's own caller (outside this whole class,
 * {@code Structure.generate}) is what actually invokes that consumer, with the real {@code
 * StructurePiecesBuilder} -- meaning a redirect scoped to the call to {@code addPieces} from
 * inside {@code findGenerationPoint} (an earlier draft of this ticket's own work) would run its
 * ladder logic before any piece had actually been placed, always seeing zero accepted/rejected.
 * The ladder therefore lives in {@link JigsawPlacementMixin} instead, wrapping the {@code
 * GenerationStub} constructor call directly inside {@code addPieces} itself -- see that class'
 * own javadoc for the full mechanism and why it is safe against re-entrant nested calls.
 */
@Mixin(JigsawStructure.class)
abstract class JigsawStructureMixin {

    @Inject(method = "findGenerationPoint", at = @At("HEAD"))
    private void gv$gateOnVillageTag(Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        //? if <1.21.2 {
        Registry<Structure> structures = context.registryAccess().registryOrThrow(Registries.STRUCTURE);
        //?} else {
        /*Registry<Structure> structures = context.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        *///?}
        boolean isVillage = structures.wrapAsHolder((Structure) (Object) this).is(StructureTags.VILLAGE);
        VillageTagContext.set(isVillage);
        HookDebug.fired("JigsawStructureMixin#findGenerationPoint", isVillage ? "village-tagged" : "not village-tagged");

        gv$rollTier(context, isVillage);
    }

    /**
     * GV-8: rolls the tier once per village candidate, before {@code addPieces} runs -- see this
     * class's own javadoc "GV-8 also rolls the tier here". Skips the random draw entirely rather
     * than rolling and discarding it when {@code tier.enabled} is {@code false}, so a server
     * running with tiers off never perturbs vanilla's own subsequent piece-selection draws for no
     * reason (`docs/spec/domains/config.md` {@code tier.enabled}: "matching pre-mod behaviour").
     */
    private static void gv$rollTier(Structure.GenerationContext context, boolean isVillage) {
        if (!isVillage) {
            TierAssignmentContext.set(null);
            return;
        }
        ConfigModel.Tier tierConfig = ConfigHolder.get().tier();
        if (!tierConfig.enabled()) {
            TierAssignmentContext.set(null);
            return;
        }
        double draw = context.random().nextDouble();
        TierAssignment assignment = TierRoller.roll(tierConfig, draw);
        TierAssignmentContext.set(assignment);
        TierAssignmentRegistry.record(context.chunkPos(), assignment);
        HookDebug.fired("TierRoller", assignment);
    }
}
