package grounded_villages.mixin.village;

import grounded_villages.hook.GenerationContext;
import grounded_villages.hook.HookDebug;
import grounded_villages.hook.HookRegistry;
import grounded_villages.hook.StartDecision;
import grounded_villages.hook.TerrainSampler;
import grounded_villages.mixinsupport.VillageTagContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
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
 * {@code pos}, and {@code maxDistanceFromCenter} (GV-6, whose type is the one genuine three-way
 * split), whose positions are otherwise identical across all three -- so only the injection
 * selector string and the {@code maxDistanceFromCenter} extraction differ per branch, not the
 * hook-calling logic itself.
 *
 * <p><b>GV-6 (domains/site.md): {@code @ModifyVariable}, not {@code @Inject}.</b> GV-5 shipped
 * only an {@code @Inject} at {@code HEAD} that read {@code pos} and logged the hook's decision --
 * an {@code @Inject} handler receives copies of the target method's local variables/parameters,
 * so modifying its own copy of {@code pos} never wrote back to the actual local the rest of
 * {@code addPieces} goes on to use. {@code @ModifyVariable} targets the same local variable slot
 * directly: the value this handler returns replaces {@code pos} for the remainder of the method
 * body, including its own {@code project_start_to_heightmap} re-projection a few lines later (if
 * {@code projectStartToHeightmap} is present -- true for every {@code #minecraft:village}-tagged
 * structure, `village-jigsaw-placement-1-20-1-to-26-2.md` §A "Verified facts" -- vanilla itself
 * recomputes Y from the new X/Z there; this mixin does not need to duplicate that arithmetic, only
 * change X/Z, which is why {@code SiteSearch}'s own advisory Y, `grounded_villages.site.SiteSearch`
 * javadoc, is never load-bearing for a tagged village). {@code argsOnly = true} plus an explicit
 * {@code ordinal = 0}: {@code pos} is the method's own only {@code BlockPos}-typed parameter, not
 * a true local declared inside the body.
 *
 * <p><b>Handler signature, the part that is easy to get wrong (confirmed live against a real
 * Mixin apply failure during this ticket's own harness sweep):</b> Mixin's "full args" {@code
 * @ModifyVariable} form is not "the captured value, then the other parameters" -- it is the
 * captured value <i>prepended</i> to a complete, unmodified copy of the target method's own
 * parameter list, {@code pos} included in its original position. {@code pos} therefore appears
 * twice in every handler below: once as parameter 0 (the value this method returns, which
 * replaces the local), and again at its original index further along (an unused, always-equal-at-
 * entry copy Mixin requires as part of the "full" signature, not a second injection point).
 * Dropping the second occurrence throws {@code InvalidInjectionException} at mixin-apply time --
 * a silent-until-runtime failure category `04-architecture.md` {@code ARCH-FAIL-003} already
 * names ("mod load fails with a named error ... never a silent no-op"), caught here by actually
 * booting a server during the harness sweep rather than by {@code chiseledCheck} alone (Mixin
 * signature validation happens at apply time, not at {@code javac} compile time).
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
    *///?}

    private static BlockPos gv$decideStart(Structure.GenerationContext context, BlockPos pos, int maxDistanceFromCenter) {
        if (!VillageTagContext.isVillage()) {
            return pos;
        }
        TerrainSampler sampler = TerrainSampler.of(context.chunkGenerator(), context.randomState(), context.heightAccessor());
        GenerationContext hookContext = new GenerationContext(sampler, context.chunkPos(), context.seed());
        StartDecision decision = HookRegistry.startHook().onStart(hookContext, pos, maxDistanceFromCenter);
        HookDebug.fired("VillageStartHook", decision);
        if (decision instanceof StartDecision.Shift shift) {
            return shift.to();
        }
        return pos;
    }
}
