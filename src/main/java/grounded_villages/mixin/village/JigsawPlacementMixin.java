package grounded_villages.mixin.village;

import grounded_villages.config.ConfigHolder;
import grounded_villages.hook.GenerationContext;
import grounded_villages.hook.HookDebug;
import grounded_villages.hook.HookRegistry;
import grounded_villages.hook.StartDecision;
import grounded_villages.hook.TerrainSampler;
import grounded_villages.mixinsupport.TierAssignmentContext;
import grounded_villages.mixinsupport.VillageTagContext;
import grounded_villages.tier.TierAssignment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * The start-piece hook (ticket build item 1): fires once per village candidate, from the
 * <b>public</b> {@code JigsawPlacement.addPieces} overload -- the call
 * {@code JigsawStructure.findGenerationPoint} makes directly, confirmed by
 * {@code village-jigsaw-placement-1-20-1-to-26-2.md} SS B/C and this ticket's own {@code javap}
 * read (GV-5). {@code JigsawPlacement} also declares a second, <b>private</b> {@code addPieces}
 * overload (the recursive assembly worker the public one hands off to) -- the two are
 * disambiguated below by full method descriptor, not by name alone.
 *
 * <p><b>Correction to {@code contracts/platform-matrix.md}'s version-delta table (GV-5,
 * {@code javap -s} on the Loom-cached 1.20.1/1.21.1/26.2 jars)</b>: the public overload's
 * signature is not a two-way split (1.20.1 vs. "1.21.1 and 26.2 identically"), it is a
 * three-way one -- 26.2 additionally replaces the plain {@code int maxDistanceFromCenter}
 * trailing parameter with a {@code JigsawStructure.MaxDistance} record and
 * {@code Optional<ResourceLocation>} with {@code Optional<Identifier>} (the class rename already
 * known from the same table, just not previously connected to this specific parameter list).
 * None of the three variants below need those trailing/renamed parameters -- only
 * {@code context} and {@code pos}, whose types and position are identical across all three -- so
 * only the {@code @Inject} selector string differs per branch, not the hook-calling logic itself.
 *
 * <p>GV-5 ships the hook family only, no scoring logic (ticket scope): this fires
 * {@link HookRegistry#startHook()} for its decision and logs it, but does not yet write a
 * {@link StartDecision.Shift} or {@link StartDecision.Vanilla} outcome back into the live
 * placement call -- {@link grounded_villages.hook.VillageStartHook#ACCEPT_ALL} never returns
 * either, so there is nothing to enact yet. Wiring that write-back (a {@code BlockPos} local
 * capture/rewrite) is domains/site.md's own scoring logic, added with GV-6's real
 * {@code SiteSelector}.
 *
 * <p><b>GV-8 write-back</b> (`docs/spec/domains/tiers.md`): {@code maxDepth} and {@code
 * maxDistanceFromCenter} are plain method parameters read by the rest of {@code addPieces}' own
 * body (and threaded down into the private recursive worker), not locals this method's own
 * {@code @Inject} can rewrite from a {@code CallbackInfoReturnable} -- {@code @ModifyVariable} is
 * the mechanism that lets a mixin substitute a parameter's value as the method body itself reads
 * it, one method per parameter below ({@code gv$modifyMaxDepth}/{@code gv$modifyMaxDistance}),
 * targeting the same version-branched selector strings as {@code gv$onAddPieces} above (LVT slot
 * {@code index}, not {@code ordinal}, since {@code maxDepth} and {@code maxDistanceFromCenter} are
 * both {@code int} in the 1.20.1/1.21.1 branches and an {@code ordinal} count would be ambiguous
 * between them; explicit slot indices -- 3 for {@code maxDepth}, 7 for {@code
 * maxDistanceFromCenter} -- are stable across all three branches since every parameter ahead of
 * {@code maxDistanceFromCenter} keeps the same type and position in all three, only the trailing
 * parameters after it differ, per the version-delta correction above). Both read the tier {@link
 * JigsawStructureMixin} already rolled and stashed in {@link TierAssignmentContext} -- rolling
 * happens once, in {@code JigsawStructureMixin}, not here (see that class's own javadoc "GV-8 also
 * rolls the tier here" for why).
 */
@Mixin(JigsawPlacement.class)
abstract class JigsawPlacementMixin {

    //? if <1.21 {
    @Inject(
            method = "addPieces(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;Lnet/minecraft/core/Holder;Ljava/util/Optional;ILnet/minecraft/core/BlockPos;ZLjava/util/Optional;I)Ljava/util/Optional;",
            at = @At("HEAD")
    )
    private static void gv$onAddPieces(
            Structure.GenerationContext context,
            Holder<StructureTemplatePool> startPool,
            Optional<net.minecraft.resources.ResourceLocation> startJigsawName,
            int maxDepth,
            BlockPos pos,
            boolean useExpansionHack,
            Optional<Heightmap.Types> projectStartToHeightmap,
            int maxDistanceFromCenter,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        gv$fireStartHook(context, pos);
    }

    @ModifyVariable(
            method = "addPieces(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;Lnet/minecraft/core/Holder;Ljava/util/Optional;ILnet/minecraft/core/BlockPos;ZLjava/util/Optional;I)Ljava/util/Optional;",
            at = @At("HEAD"),
            index = 3
    )
    private static int gv$modifyMaxDepth(int maxDepth) {
        return gv$tieredDepth(maxDepth);
    }

    @ModifyVariable(
            method = "addPieces(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;Lnet/minecraft/core/Holder;Ljava/util/Optional;ILnet/minecraft/core/BlockPos;ZLjava/util/Optional;I)Ljava/util/Optional;",
            at = @At("HEAD"),
            index = 7
    )
    private static int gv$modifyMaxDistance(int maxDistanceFromCenter) {
        return gv$tieredMaxDistance(maxDistanceFromCenter);
    }
    //?} elif <26.1 {
    /*@Inject(
            method = "addPieces(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;Lnet/minecraft/core/Holder;Ljava/util/Optional;ILnet/minecraft/core/BlockPos;ZLjava/util/Optional;ILnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/pools/DimensionPadding;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)Ljava/util/Optional;",
            at = @At("HEAD")
    )
    private static void gv$onAddPieces(
            Structure.GenerationContext context,
            Holder<StructureTemplatePool> startPool,
            Optional<net.minecraft.resources.ResourceLocation> startJigsawName,
            int maxDepth,
            BlockPos pos,
            boolean useExpansionHack,
            Optional<Heightmap.Types> projectStartToHeightmap,
            int maxDistanceFromCenter,
            net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup poolAliasLookup,
            net.minecraft.world.level.levelgen.structure.pools.DimensionPadding dimensionPadding,
            net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings liquidSettings,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        gv$fireStartHook(context, pos);
    }

    @ModifyVariable(
            method = "addPieces(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;Lnet/minecraft/core/Holder;Ljava/util/Optional;ILnet/minecraft/core/BlockPos;ZLjava/util/Optional;ILnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/pools/DimensionPadding;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)Ljava/util/Optional;",
            at = @At("HEAD"),
            index = 3
    )
    private static int gv$modifyMaxDepth(int maxDepth) {
        return gv$tieredDepth(maxDepth);
    }

    @ModifyVariable(
            method = "addPieces(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;Lnet/minecraft/core/Holder;Ljava/util/Optional;ILnet/minecraft/core/BlockPos;ZLjava/util/Optional;ILnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/pools/DimensionPadding;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)Ljava/util/Optional;",
            at = @At("HEAD"),
            index = 7
    )
    private static int gv$modifyMaxDistance(int maxDistanceFromCenter) {
        return gv$tieredMaxDistance(maxDistanceFromCenter);
    }
    *///?} else {
    /*@Inject(
            method = "addPieces(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;Lnet/minecraft/core/Holder;Ljava/util/Optional;ILnet/minecraft/core/BlockPos;ZLjava/util/Optional;Lnet/minecraft/world/level/levelgen/structure/structures/JigsawStructure$MaxDistance;Lnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/pools/DimensionPadding;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)Ljava/util/Optional;",
            at = @At("HEAD")
    )
    private static void gv$onAddPieces(
            Structure.GenerationContext context,
            Holder<StructureTemplatePool> startPool,
            Optional<net.minecraft.resources.Identifier> startJigsawName,
            int maxDepth,
            BlockPos pos,
            boolean useExpansionHack,
            Optional<Heightmap.Types> projectStartToHeightmap,
            net.minecraft.world.level.levelgen.structure.structures.JigsawStructure.MaxDistance maxDistanceFromCenter,
            net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup poolAliasLookup,
            net.minecraft.world.level.levelgen.structure.pools.DimensionPadding dimensionPadding,
            net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings liquidSettings,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        gv$fireStartHook(context, pos);
    }

    @ModifyVariable(
            method = "addPieces(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;Lnet/minecraft/core/Holder;Ljava/util/Optional;ILnet/minecraft/core/BlockPos;ZLjava/util/Optional;Lnet/minecraft/world/level/levelgen/structure/structures/JigsawStructure$MaxDistance;Lnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/pools/DimensionPadding;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)Ljava/util/Optional;",
            at = @At("HEAD"),
            index = 3
    )
    private static int gv$modifyMaxDepth(int maxDepth) {
        return gv$tieredDepth(maxDepth);
    }

    @ModifyVariable(
            method = "addPieces(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;Lnet/minecraft/core/Holder;Ljava/util/Optional;ILnet/minecraft/core/BlockPos;ZLjava/util/Optional;Lnet/minecraft/world/level/levelgen/structure/structures/JigsawStructure$MaxDistance;Lnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/pools/DimensionPadding;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)Ljava/util/Optional;",
            at = @At("HEAD"),
            index = 7
    )
    private static JigsawStructure.MaxDistance gv$modifyMaxDistance(JigsawStructure.MaxDistance maxDistanceFromCenter) {
        return gv$tieredMaxDistance(maxDistanceFromCenter);
    }
    *///?}

    private static void gv$fireStartHook(Structure.GenerationContext context, BlockPos pos) {
        if (!VillageTagContext.isVillage()) {
            return;
        }
        TerrainSampler sampler = TerrainSampler.of(context.chunkGenerator(), context.randomState(), context.heightAccessor());
        GenerationContext hookContext = new GenerationContext(sampler, context.chunkPos(), context.seed());
        StartDecision decision = HookRegistry.startHook().onStart(hookContext, pos);
        HookDebug.fired("VillageStartHook", decision);
    }

    /**
     * {@code TIER-REQ-004}: feeds the rolled tier's already-capped {@code jigsawDepth} in place of
     * vanilla's own {@code maxDepth}, for village-tagged structures, when {@code tier.enabled}.
     * Returns {@code vanillaMaxDepth} unchanged for every other case (not village-tagged, tiers
     * disabled, or -- defensively -- no assignment was ever rolled for this thread's current call)
     * so a disabled or not-yet-rolled tier system is always a true no-op, never a silent default
     * substitution.
     */
    private static int gv$tieredDepth(int vanillaMaxDepth) {
        if (!VillageTagContext.isVillage() || !ConfigHolder.get().tier().enabled()) {
            return vanillaMaxDepth;
        }
        TierAssignment assignment = TierAssignmentContext.get();
        return assignment != null ? assignment.jigsawDepth() : vanillaMaxDepth;
    }

    //? if <26.1 {
    /**
     * {@code TIER-REQ-004}: the 1.20.1/1.21.1 {@code int max_distance_from_center} write-back --
     * see {@link #gv$tieredDepth} for the shared no-op conditions.
     */
    private static int gv$tieredMaxDistance(int vanillaMaxDistance) {
        if (!VillageTagContext.isVillage() || !ConfigHolder.get().tier().enabled()) {
            return vanillaMaxDistance;
        }
        TierAssignment assignment = TierAssignmentContext.get();
        return assignment != null ? assignment.maxDistance() : vanillaMaxDistance;
    }
    //?} else {
    /*
    private static JigsawStructure.MaxDistance gv$tieredMaxDistance(JigsawStructure.MaxDistance vanillaMaxDistance) {
        if (!VillageTagContext.isVillage() || !ConfigHolder.get().tier().enabled()) {
            return vanillaMaxDistance;
        }
        TierAssignment assignment = TierAssignmentContext.get();
        if (assignment == null) {
            return vanillaMaxDistance;
        }
        // village-jigsaw-placement-1-20-1-to-26-2.md SS A: "built from a single int sets both
        // horizontal and vertical to that value" -- the tier system rolls one symmetric distance,
        // same as every pre-26.2 version's own plain int did.
        return new JigsawStructure.MaxDistance(assignment.maxDistance(), assignment.maxDistance());
    }
    *///?}
}
