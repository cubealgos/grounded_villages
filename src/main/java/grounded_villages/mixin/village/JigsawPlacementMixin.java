package grounded_villages.mixin.village;

import grounded_villages.config.ConfigHolder;
import grounded_villages.config.ConfigModel;
import grounded_villages.hook.GenerationContext;
import grounded_villages.hook.HookDebug;
import grounded_villages.hook.HookRegistry;
import grounded_villages.hook.PieceLadderRegistry;
import grounded_villages.hook.StartDecision;
import grounded_villages.hook.TerrainSampler;
import grounded_villages.hook.TierAssignmentRegistry;
import grounded_villages.mixinsupport.PieceLadderContext;
import grounded_villages.mixinsupport.StartHeightContext;
import grounded_villages.mixinsupport.TierAssignmentContext;
import grounded_villages.mixinsupport.VillageTagContext;
import grounded_villages.piece.PieceLadder;
import grounded_villages.site.HeightSampler;
import grounded_villages.site.SiteCoordinate;
import grounded_villages.site.SiteSearch;
import grounded_villages.tier.Tier;
import grounded_villages.tier.TierAssignment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;
import java.util.function.Consumer;

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
 *
 * <p><b>GV-7 also wraps the piece-placement consumer here</b>
 * (`decisions/DEC-010-shrink-move-vanilla.md`, `docs/spec/domains/pieces.md` {@code
 * PIECE-REQ-006}/{@code 007}) -- not at the call to {@code addPieces} ({@code
 * JigsawStructureMixin}'s own javadoc has the full reasoning: {@code addPieces} only ever
 * <i>builds</i> a {@code Consumer<StructurePiecesBuilder>} and hands it, unexecuted, to the
 * {@code Structure.GenerationStub} it returns -- the actual placement (everything {@code
 * PlacerMixin} hooks) happens later, whenever vanilla's own {@code Structure.generate} invokes
 * that consumer with the real builder). {@link #gv$onAddPieces} additionally builds and stashes a
 * {@link PieceLadderContext.RetryHandle} -- a closure over every parameter needed to call {@code
 * addPieces} again at a different {@code BlockPos}, captured while they are all still in scope as
 * this method's own parameters -- and the new {@code @Redirect} on {@code Structure.GenerationStub}'s
 * own constructor (confirmed by {@code javap}: identical {@code (BlockPos, Consumer)} signature,
 * occurring exactly once, on all three targeted versions) reads that handle back and wraps the
 * real consumer in one that runs the shrink/move/vanilla ladder the first time it is actually
 * invoked. See {@link #gv$wrapGenerationStub}'s own javadoc for the full mechanism, including how
 * the "move" and "vanilla" steps' own nested {@code addPieces} calls are prevented from wrapping
 * themselves a second time.
 */
@Mixin(JigsawPlacement.class)
abstract class JigsawPlacementMixin {

    //? if <1.21 {
    /*@ModifyVariable(
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
        BlockPos finalPos = gv$decideStart(context, pos, maxDistanceFromCenter);
        PieceLadderContext.setRetryHandle(new PieceLadderContext.RetryHandle(
                context,
                gv$effectiveMaxDistance(maxDistanceFromCenter),
                candidatePos -> JigsawPlacement.addPieces(
                        context, startPool, startJigsawName, maxDepth, candidatePos, useExpansionHack, projectStartToHeightmap, maxDistanceFromCenter)));
        return finalPos;
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

    @Redirect(method = "addPieces(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;Lnet/minecraft/core/Holder;Ljava/util/Optional;ILnet/minecraft/core/BlockPos;ZLjava/util/Optional;I)Ljava/util/Optional;",
            at = @At(value = "NEW", target = "Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationStub;"))
    private static Structure.GenerationStub gv$redirectGenerationStub(BlockPos position, Consumer<StructurePiecesBuilder> generator) {
        return gv$wrapGenerationStub(position, generator);
    }
    *///?} elif <26.1 {
    @ModifyVariable(
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
        BlockPos finalPos = gv$decideStart(context, pos, maxDistanceFromCenter);
        PieceLadderContext.setRetryHandle(new PieceLadderContext.RetryHandle(
                context,
                gv$effectiveMaxDistance(maxDistanceFromCenter),
                candidatePos -> JigsawPlacement.addPieces(
                        context, startPool, startJigsawName, maxDepth, candidatePos, useExpansionHack, projectStartToHeightmap, maxDistanceFromCenter,
                        poolAliasLookup, dimensionPadding, liquidSettings)));
        return finalPos;
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

    @Redirect(method = "addPieces(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;Lnet/minecraft/core/Holder;Ljava/util/Optional;ILnet/minecraft/core/BlockPos;ZLjava/util/Optional;ILnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/pools/DimensionPadding;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)Ljava/util/Optional;",
            at = @At(value = "NEW", target = "Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationStub;"))
    private static Structure.GenerationStub gv$redirectGenerationStub(BlockPos position, Consumer<StructurePiecesBuilder> generator) {
        return gv$wrapGenerationStub(position, generator);
    }
    //?} else {
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
        BlockPos finalPos = gv$decideStart(context, pos, maxDistanceFromCenter.horizontal());
        PieceLadderContext.setRetryHandle(new PieceLadderContext.RetryHandle(
                context,
                gv$effectiveMaxDistance(maxDistanceFromCenter.horizontal()),
                candidatePos -> JigsawPlacement.addPieces(
                        context, startPool, startJigsawName, maxDepth, candidatePos, useExpansionHack, projectStartToHeightmap, maxDistanceFromCenter,
                        poolAliasLookup, dimensionPadding, liquidSettings)));
        return finalPos;
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

    @Redirect(method = "addPieces(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;Lnet/minecraft/core/Holder;Ljava/util/Optional;ILnet/minecraft/core/BlockPos;ZLjava/util/Optional;Lnet/minecraft/world/level/levelgen/structure/structures/JigsawStructure$MaxDistance;Lnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/pools/DimensionPadding;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)Ljava/util/Optional;",
            at = @At(value = "NEW", target = "Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationStub;"))
    private static Structure.GenerationStub gv$redirectGenerationStub(BlockPos position, Consumer<StructurePiecesBuilder> generator) {
        return gv$wrapGenerationStub(position, generator);
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
        // GV-7, DEC-010 step 3 ("vanilla"): the ladder's own final fallback attempt places the
        // village exactly as vanilla would -- "no site check, no piece check" -- so this whole
        // hook is skipped entirely while JigsawStructureMixin has bypass set, not just the piece
        // side PlacerMixin's own gv$prepare already gates.
        if (PieceLadderContext.isBypass()) {
            return pos;
        }
        TerrainSampler sampler = TerrainSampler.of(context.chunkGenerator(), context.randomState(), context.heightAccessor());
        GenerationContext hookContext = new GenerationContext(sampler, context.chunkPos(), context.seed());
        int radius = gv$effectiveMaxDistance(maxDistanceFromCenter);
        StartDecision decision = HookRegistry.startHook().onStart(hookContext, pos, radius);
        HookDebug.fired("VillageStartHook", decision);
        BlockPos finalPos = decision instanceof StartDecision.Shift shift ? shift.to() : pos;

        // GV-7 (docs/spec/domains/pieces.md PIECE-REQ-003, 03-glossary.md "start height"): the
        // one ground-height reference every piece's own height-deviation check is measured
        // against, recorded here (not by SiteStartHook, whose own advisory Y is WORLD_SURFACE_WG
        // for placement, not OCEAN_FLOOR_WG for comparison -- see StartHeightContext's own
        // javadoc) since this is the one point that knows the final X/Z regardless of which of
        // Keep/Shift produced it.
        StartHeightContext.set(sampler.getBaseHeight(finalPos.getX(), finalPos.getZ(), Heightmap.Types.OCEAN_FLOOR_WG));
        return finalPos;
    }

    /**
     * `decisions/DEC-010-shrink-move-vanilla.md`: runs the shrink/move/vanilla ladder immediately
     * against vanilla's own real {@code Consumer<StructurePiecesBuilder>} -- captured here exactly
     * as {@code addPieces} built it, never invoked more than the one real time each attempt needs
     * -- and returns a {@code GenerationStub} built from the {@code
     * com.mojang.datafixers.util.Either.right(StructurePiecesBuilder)} constructor overload (a
     * second constructor {@code Structure.GenerationStub} itself already exposes, confirmed by
     * {@code javap}) rather than the {@code Consumer}-taking one: this bakes in the ladder's own
     * already-decided winning builder directly, so whenever vanilla's own {@code Structure.generate}
     * later calls {@code getPiecesBuilder()} on the stub this method returns, it gets that builder
     * straight back ({@code GenerationStub}'s own {@code Either.right} branch is a plain identity
     * passthrough, confirmed by {@code javap}) rather than re-running anything -- no double
     * invocation, no risk of a second, divergent draw from {@code context.random()} producing a
     * different piece layout than the one this ladder actually evaluated.
     *
     * <p>Passes the real consumer straight through, unwrapped (the plain {@code Consumer}
     * constructor), for every call this ladder should not touch at all: not village-tagged,
     * {@code piece.enabled=false} ({@code PIECE-REQ-005}), no {@link
     * PieceLadderContext.RetryHandle} was ever stashed (defensive -- {@link #gv$onAddPieces}
     * always sets one before this point is ever reached in the same call, so this should be
     * unreachable in practice), or this is itself one of the ladder's own nested retry calls
     * ({@link PieceLadderContext#isSuppressWrap()} -- see {@link PieceLadderContext}'s own javadoc
     * "Suppressing the wrap for a retry's own nested call" for why that guard exists at all).
     */
    private static Structure.GenerationStub gv$wrapGenerationStub(BlockPos position, Consumer<StructurePiecesBuilder> generator) {
        if (!VillageTagContext.isVillage() || !ConfigHolder.get().piece().enabled() || PieceLadderContext.isSuppressWrap()) {
            return new Structure.GenerationStub(position, generator);
        }
        PieceLadderContext.RetryHandle handle = PieceLadderContext.getRetryHandle();
        if (handle == null) {
            return new Structure.GenerationStub(position, generator);
        }
        StructurePiecesBuilder finalBuilder = gv$runLadder(handle, position, generator);
        return new Structure.GenerationStub(position, com.mojang.datafixers.util.Either.right(finalBuilder));
    }

    /**
     * The ladder itself, run exactly once, synchronously, from {@link #gv$wrapGenerationStub} --
     * see that method's and {@link PieceLadderContext}'s own javadoc for the full mechanism.
     *
     * <p><b>Step 1, shrink</b> (`PIECE-REQ-006`): {@code originalConsumer} (vanilla's own real
     * placement logic for the original candidate) runs once against a fresh {@code
     * StructurePiecesBuilder} -- the one, true invocation for this position, firing {@code
     * PlacerMixin}'s real per-child rejection for real. {@link PieceLadderContext.Accumulator}
     * counts what it accepted/rejected; {@link PieceLadder#evaluate} (pure) decides {@code
     * UNAFFECTED} (kept, untouched), {@code SHRINK} (kept, relabelled {@code hamlet}) or {@code
     * RETRY}.
     *
     * <p><b>Step 2, move</b> (`SITE-REQ-006`): one call to {@code SiteSearch.searchAlternative} --
     * already embodying "up to {@code site.search_attempts} shifted candidates" internally, so
     * this is one call, not a hand-rolled loop. A qualifying candidate's own {@code addPieces}
     * call is made through {@link PieceLadderContext.RetryHandle#retry()} with {@link
     * PieceLadderContext#setSuppressWrap} held, so its own {@code GenerationStub} construction
     * (inside that nested {@code addPieces} call) passes through {@link #gv$wrapGenerationStub}
     * unwrapped, carrying the nested call's own raw, never-yet-invoked consumer -- calling {@code
     * getPiecesBuilder()} on that returned stub is therefore the one true invocation for the
     * moved position (vanilla's own {@code GenerationStub#getPiecesBuilder} already does exactly
     * "make a fresh builder, run the consumer against it, return it" internally, confirmed by
     * {@code javap}, so this reuses that rather than re-deriving it). {@link PieceLadder#evaluate}
     * decides again, independently, on this attempt's own tally.
     *
     * <p><b>Step 3, vanilla</b> (`SITE-REQ-004`'s own fallback, reached by this different path):
     * the same retry-and-invoke shape, at {@code originalPos}, with {@link
     * PieceLadderContext#setBypass} additionally held so {@link #gv$decideStart} and {@code
     * PlacerMixin}'s own {@code gv$prepare} both skip their own hooks entirely for this one nested
     * call -- "no site check, no piece check". Falls back to attempt 1's own (mostly-rejected)
     * builder only in the practically-unreachable case that even this retry returns nothing, per
     * this whole domain's own standing rule: no world loses a village outright.
     */
    private static StructurePiecesBuilder gv$runLadder(
            PieceLadderContext.RetryHandle handle, BlockPos originalPos, Consumer<StructurePiecesBuilder> originalConsumer) {
        Structure.GenerationContext context = handle.context();
        int hamletMinimum = ConfigHolder.get().tier().hamletMinimumPieces();

        StructurePiecesBuilder builder = new StructurePiecesBuilder();
        PieceLadderContext.beginAttempt();
        originalConsumer.accept(builder);
        PieceLadderContext.Accumulator firstResult = PieceLadderContext.endAttempt();

        PieceLadder.Outcome outcome = PieceLadder.evaluate(firstResult.totalRejected(), firstResult.acceptedNonStreet, hamletMinimum);
        if (outcome != PieceLadder.Outcome.RETRY) {
            gv$finishLadder(context, outcome == PieceLadder.Outcome.SHRINK ? "shrink" : "unaffected", firstResult, outcome == PieceLadder.Outcome.SHRINK);
            return builder;
        }

        TerrainSampler sampler = TerrainSampler.of(context.chunkGenerator(), context.randomState(), context.heightAccessor());
        ConfigModel.Site siteConfig = ConfigHolder.get().site();
        Optional<SiteCoordinate> shifted =
                SiteSearch.searchAlternative(gv$toHeightSampler(sampler), originalPos.getX(), originalPos.getZ(), handle.radius(), siteConfig);

        if (shifted.isPresent()) {
            SiteCoordinate to = shifted.get();
            int y = sampler.getBaseHeight(to.x(), to.z(), Heightmap.Types.WORLD_SURFACE_WG);
            BlockPos movedPos = new BlockPos(to.x(), y, to.z());

            Optional<Structure.GenerationStub> movedStub = gv$retryUnwrapped(handle, movedPos);
            if (movedStub.isPresent()) {
                PieceLadderContext.beginAttempt();
                StructurePiecesBuilder movedBuilder = movedStub.get().getPiecesBuilder();
                PieceLadderContext.Accumulator movedResult = PieceLadderContext.endAttempt();

                PieceLadder.Outcome movedOutcome = PieceLadder.evaluate(movedResult.totalRejected(), movedResult.acceptedNonStreet, hamletMinimum);
                if (movedOutcome != PieceLadder.Outcome.RETRY) {
                    gv$finishLadder(context, "moved", movedResult, movedOutcome == PieceLadder.Outcome.SHRINK);
                    return movedBuilder;
                }
            }
        }

        HookDebug.fired("PieceLadder", "vanilla fallback");
        PieceLadderRegistry.record(context.chunkPos(), new PieceLadderRegistry.PieceLadderResult("vanilla", 0, 0, 0, 0));
        PieceLadderContext.setBypass(true);
        try {
            Optional<Structure.GenerationStub> vanillaStub = gv$retryUnwrapped(handle, originalPos);
            return vanillaStub.isPresent() ? vanillaStub.get().getPiecesBuilder() : builder;
        } finally {
            PieceLadderContext.setBypass(false);
        }
    }

    /** {@link PieceLadderContext#setSuppressWrap} held around the nested {@code addPieces} call
     *  so its own {@code GenerationStub} construction passes through {@link #gv$wrapGenerationStub}
     *  unwrapped -- see that method's own javadoc. */
    private static Optional<Structure.GenerationStub> gv$retryUnwrapped(PieceLadderContext.RetryHandle handle, BlockPos candidatePos) {
        PieceLadderContext.setSuppressWrap(true);
        try {
            return handle.retry().apply(candidatePos);
        } finally {
            PieceLadderContext.setSuppressWrap(false);
        }
    }

    private static void gv$finishLadder(Structure.GenerationContext context, String outcome, PieceLadderContext.Accumulator result, boolean relabelHamlet) {
        PieceLadderRegistry.record(context.chunkPos(), new PieceLadderRegistry.PieceLadderResult(
                outcome, result.rejectedWater, result.rejectedHeight, result.acceptedNonStreet, result.acceptedStreet));
        if (relabelHamlet) {
            gv$relabelHamlet(context);
        }
        HookDebug.fired("PieceLadder", outcome + " (" + result.acceptedNonStreet + " non-street, " + result.totalRejected() + " rejected)");
    }

    /**
     * {@code PIECE-REQ-006}: "relabelled hamlet regardless of its originally rolled tier" -- keeps
     * the same {@code jigsawDepth}/{@code maxDistance} the successful attempt actually assembled
     * with (only the label changes; re-deriving a fresh hamlet-sized budget after the fact would
     * not match what was really placed). A no-op when tiers are disabled (nothing to relabel) or
     * already {@code hamlet}.
     */
    private static void gv$relabelHamlet(Structure.GenerationContext context) {
        TierAssignment current = TierAssignmentContext.get();
        if (current == null || current.tier() == Tier.HAMLET) {
            return;
        }
        TierAssignment relabeled = new TierAssignment(Tier.HAMLET, current.jigsawDepth(), current.maxDistance());
        TierAssignmentContext.set(relabeled);
        TierAssignmentRegistry.record(context.chunkPos(), relabeled);
        HookDebug.fired("PieceLadder", "relabelled hamlet (was " + current.tier() + ")");
    }

    private static HeightSampler gv$toHeightSampler(TerrainSampler sampler) {
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
