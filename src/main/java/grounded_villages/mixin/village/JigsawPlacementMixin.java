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
import org.spongepowered.asm.mixin.injection.ModifyVariable;

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
 * None of the three variants below need those renamed parameters -- only {@code context},
 * {@code pos}, {@code maxDepth} and {@code maxDistanceFromCenter}, whose positions are otherwise
 * identical across all three -- so only the injection selector string and the {@code
 * maxDistanceFromCenter} extraction differ per branch, not the hook-calling logic itself.
 *
 * <p><b>GV-6 (domains/site.md): {@code @ModifyVariable}, not {@code @Inject}.</b> GV-5 shipped
 * only an {@code @Inject} at {@code HEAD} that read {@code pos} and logged the hook's decision --
 * an {@code @Inject} handler receives copies of the target method's local variables/parameters,
 * so modifying its own copy of {@code pos} never wrote back to the actual local the rest of
 * {@code addPieces} goes on to use. {@code @ModifyVariable} targets the same local variable slot
 * directly: the value {@link #gv$onAddPieces} returns replaces {@code pos} for the remainder of
 * the method body, including its own {@code project_start_to_heightmap} re-projection a few lines
 * later (if {@code projectStartToHeightmap} is present -- true for every {@code
 * #minecraft:village}-tagged structure, `village-jigsaw-placement-1-20-1-to-26-2.md` §A "Verified
 * facts" -- vanilla itself recomputes Y from the new X/Z there; this mixin does not need to
 * duplicate that arithmetic, only change X/Z, which is why {@code SiteSearch}'s own advisory Y,
 * `grounded_villages.site.SiteSearch` javadoc, is never load-bearing for a tagged village).
 *
 * <p><b>Handler signature, the part that is easy to get wrong (confirmed live against a real
 * Mixin apply failure during this ticket's own harness sweep):</b> Mixin's "full args" {@code
 * @ModifyVariable} form is not "the captured value, then the other parameters" -- it is the
 * captured value <i>prepended</i> to a complete, unmodified copy of the target method's own
 * parameter list, {@code pos} included in its original position. {@code pos} therefore appears
 * twice in {@link #gv$onAddPieces} below: once as parameter 0 (the value this method returns,
 * which replaces the local), and again at its original index further along (an unused,
 * always-equal-at-entry copy Mixin requires as part of the "full" signature, not a second
 * injection point). Dropping the second occurrence throws {@code InvalidInjectionException} at
 * mixin-apply time -- a silent-until-runtime failure category `04-architecture.md` {@code
 * ARCH-FAIL-003} already names ("mod load fails with a named error ... never a silent no-op"),
 * caught here by actually booting a server during the harness sweep rather than by {@code
 * chiseledCheck} alone (Mixin signature validation happens at apply time, not at {@code javac}
 * compile time).
 *
 * <p><b>GV-8 write-back</b> (`docs/spec/domains/tiers.md`): {@code maxDepth} and {@code
 * maxDistanceFromCenter} are plain method parameters read by the rest of {@code addPieces}' own
 * body (and threaded down into the private recursive worker), not locals a single {@code @Inject}
 * can rewrite -- {@code @ModifyVariable} is the same mechanism GV-6 uses for {@code pos}, one
 * method per parameter ({@link #gv$modifyMaxDepth}/{@link #gv$modifyMaxDistance}), targeting the
 * same version-branched selector strings as {@link #gv$onAddPieces} (LVT slot {@code index}, not
 * {@code ordinal}, since {@code maxDepth} and {@code maxDistanceFromCenter} are both single-slot
 * values -- {@code int} pre-26.2, and 26.2's own {@code JigsawStructure.MaxDistance} is a
 * reference type, also one slot -- and an {@code ordinal} count would be ambiguous between
 * {@code maxDepth} and {@code maxDistanceFromCenter} on the {@code int} branches; explicit slot
 * indices -- 3 for {@code maxDepth}, 7 for {@code maxDistanceFromCenter} -- are stable across all
 * three branches since every parameter ahead of {@code maxDistanceFromCenter} keeps the same type
 * and position in all three, per the version-delta correction above; re-verified slot-by-slot for
 * this merge, not re-guessed). Both read the tier {@link JigsawStructureMixin} already rolled and
 * stashed in {@link TierAssignmentContext} -- rolling happens once, in {@code
 * JigsawStructureMixin}, not here (see that class's own javadoc "GV-8 also rolls the tier here"
 * for why).
 *
 * <p><b>Three {@code @ModifyVariable} handlers per branch, three different local slots, no
 * injector-order dependency between them (GV-6 + GV-8, merged):</b> {@link #gv$onAddPieces}
 * targets {@code pos} (ordinal 0, the method's only {@code BlockPos} parameter); {@link
 * #gv$modifyMaxDepth}/{@link #gv$modifyMaxDistance} target slots 3 and 7. None of the three slots
 * overlap, so all three coexist as independent injectors on the same method and the same {@code
 * @At("HEAD")} point without touching each other's target local. {@link #gv$onAddPieces} also
 * needs "the tier's max distance ... once tiers exist" for {@code SiteScorer}'s own sampling
 * footprint (`grounded_villages.site.SiteScorer`'s own javadoc) -- the naive way to get it would
 * be reading {@code maxDistanceFromCenter} as context and relying on {@link #gv$modifyMaxDistance}
 * having already overwritten that slot by the time this injected call executes, but Mixin's
 * multi-injector ordering for same-point {@code @ModifyVariable}s is controlled by an {@code
 * order} annotation member this ticket confirmed is <b>not available on every targeted Mixin
 * build</b>: Forge 1.20.1 (via MDG's {@code legacyforge} addon) bundles {@code
 * org.spongepowered:mixin:0.8.5}, whose {@code @ModifyVariable} has no {@code order()} member at
 * all (confirmed live, {@code javap} on that exact jar during this merge) -- only the
 * Fabric/NeoForge-shared {@code net.fabricmc:sponge-mixin} fork has it. Rather than special-case
 * one loader's ordering or split this into two mixin classes to use {@code @Mixin}'s own
 * class-level {@code priority} instead, {@link #gv$onAddPieces} sidesteps the question entirely:
 * it calls {@link #gv$effectiveMaxDistance}, its own independent lookup of {@link
 * TierAssignmentContext} (the same thread-local {@link #gv$tieredMaxDistance} reads), rather than
 * trusting whatever the {@code maxDistanceFromCenter} parameter happens to already hold. Same
 * answer as the ordering approach would have given, zero dependency on injector application order
 * on any of the six nodes.
 */
@Mixin(JigsawPlacement.class)
abstract class JigsawPlacementMixin {

    //? if <1.21 {
    @ModifyVariable(
            method = "addPieces(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;Lnet/minecraft/core/Holder;Ljava/util/Optional;ILnet/minecraft/core/BlockPos;ZLjava/util/Optional;I)Ljava/util/Optional;",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private static BlockPos gv$onAddPieces(
            BlockPos pos,
            Structure.GenerationContext context,
            Holder<StructureTemplatePool> startPool,
            Optional<net.minecraft.resources.ResourceLocation> startJigsawName,
            int maxDepth,
            BlockPos posOriginal,
            boolean useExpansionHack,
            Optional<Heightmap.Types> projectStartToHeightmap,
            int maxDistanceFromCenter) {
        return gv$decideStart(context, pos, maxDistanceFromCenter);
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
    /*@ModifyVariable(
            method = "addPieces(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;Lnet/minecraft/core/Holder;Ljava/util/Optional;ILnet/minecraft/core/BlockPos;ZLjava/util/Optional;ILnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/pools/DimensionPadding;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)Ljava/util/Optional;",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private static BlockPos gv$onAddPieces(
            BlockPos pos,
            Structure.GenerationContext context,
            Holder<StructureTemplatePool> startPool,
            Optional<net.minecraft.resources.ResourceLocation> startJigsawName,
            int maxDepth,
            BlockPos posOriginal,
            boolean useExpansionHack,
            Optional<Heightmap.Types> projectStartToHeightmap,
            int maxDistanceFromCenter,
            net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup poolAliasLookup,
            net.minecraft.world.level.levelgen.structure.pools.DimensionPadding dimensionPadding,
            net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings liquidSettings) {
        return gv$decideStart(context, pos, maxDistanceFromCenter);
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
    /*@ModifyVariable(
            method = "addPieces(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;Lnet/minecraft/core/Holder;Ljava/util/Optional;ILnet/minecraft/core/BlockPos;ZLjava/util/Optional;Lnet/minecraft/world/level/levelgen/structure/structures/JigsawStructure$MaxDistance;Lnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/pools/DimensionPadding;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)Ljava/util/Optional;",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private static BlockPos gv$onAddPieces(
            BlockPos pos,
            Structure.GenerationContext context,
            Holder<StructureTemplatePool> startPool,
            Optional<net.minecraft.resources.Identifier> startJigsawName,
            int maxDepth,
            BlockPos posOriginal,
            boolean useExpansionHack,
            Optional<Heightmap.Types> projectStartToHeightmap,
            net.minecraft.world.level.levelgen.structure.structures.JigsawStructure.MaxDistance maxDistanceFromCenter,
            net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup poolAliasLookup,
            net.minecraft.world.level.levelgen.structure.pools.DimensionPadding dimensionPadding,
            net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings liquidSettings) {
        return gv$decideStart(context, pos, maxDistanceFromCenter.horizontal());
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

    /**
     * GV-6 ({@code domains/site.md}): decides keep/shift/vanilla for the start position and
     * enacts a {@code Shift} by returning the new {@code BlockPos} -- see the class javadoc
     * ("no injector-order dependency") for why the sampling radius is computed via {@link
     * #gv$effectiveMaxDistance} rather than trusting the raw {@code maxDistanceFromCenter}
     * parameter as-is.
     */
    private static BlockPos gv$decideStart(Structure.GenerationContext context, BlockPos pos, int maxDistanceFromCenter) {
        if (!VillageTagContext.isVillage()) {
            return pos;
        }
        TerrainSampler sampler = TerrainSampler.of(context.chunkGenerator(), context.randomState(), context.heightAccessor());
        GenerationContext hookContext = new GenerationContext(sampler, context.chunkPos(), context.seed());
        int radius = gv$effectiveMaxDistance(maxDistanceFromCenter);
        StartDecision decision = HookRegistry.startHook().onStart(hookContext, pos, radius);
        HookDebug.fired("VillageStartHook", decision);
        if (decision instanceof StartDecision.Shift shift) {
            return shift.to();
        }
        return pos;
    }

    /**
     * GV-6's own independent tier-aware radius lookup -- deliberately not a read of the {@code
     * maxDistanceFromCenter} parameter after {@link #gv$modifyMaxDistance} might have run (see the
     * class javadoc for why that ordering cannot be relied on across every targeted Mixin build).
     * Same logic as {@link #gv$tieredMaxDistance}, independently: village-tagged and {@code
     * tier.enabled} with a roll on this thread wins, otherwise vanilla's own {@code
     * maxDistanceFromCenter} (already normalised to a plain {@code int} by every {@link
     * #gv$onAddPieces} branch, including 26.2's own {@code .horizontal()} call).
     */
    private static int gv$effectiveMaxDistance(int vanillaMaxDistance) {
        if (!VillageTagContext.isVillage() || !ConfigHolder.get().tier().enabled()) {
            return vanillaMaxDistance;
        }
        TierAssignment assignment = TierAssignmentContext.get();
        return assignment != null ? assignment.maxDistance() : vanillaMaxDistance;
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
