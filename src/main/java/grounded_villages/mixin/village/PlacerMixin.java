package grounded_villages.mixin.village;

import grounded_villages.hook.HookDebug;
import grounded_villages.hook.HookRegistry;
import grounded_villages.hook.PieceDecision;
import grounded_villages.hook.TerrainSampler;
import grounded_villages.mixinsupport.VillageTagContext;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The per-piece hook (ticket build item 1): fires once per already-placed piece as vanilla's own
 * jigsaw assembly tries to expand it, from the package-private
 * {@code JigsawPlacement$Placer.tryPlacingChildren} -- referenced via {@code targets} (a string,
 * not a class literal) since {@code Placer} has default (package) access and cannot otherwise be
 * named from {@code grounded_villages.mixin.village}.
 *
 * <p>Unlike the start hook, this method's descriptor is a genuine two-way split, matching
 * {@code contracts/platform-matrix.md}'s version-delta table exactly: 1.20.1 has six parameters,
 * 1.21.1 and 26.2 share an identical eight-parameter descriptor (confirmed identical by
 * {@code javap -s} on both jars, GV-5, unlike {@code addPieces}' three-way split --
 * see {@link JigsawPlacementMixin}).
 *
 * <p>Granularity: see {@link grounded_villages.hook.VillagePieceHook}'s own javadoc -- this
 * fires once per parent piece being expanded, with that piece's own already-computed bounding
 * box, not once per individual child candidate vanilla's private candidate loop tries internally.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement$Placer")
abstract class PlacerMixin {

    @Shadow
    @Final
    private ChunkGenerator chunkGenerator;

    //? if <1.21 {
    @Inject(method = "tryPlacingChildren", at = @At("HEAD"))
    private void gv$onTryPlacingChildren(
            PoolElementStructurePiece piece,
            MutableObject<?> freeSpace,
            int depth,
            boolean useExpansionHack,
            LevelHeightAccessor heightAccessor,
            RandomState randomState,
            CallbackInfo ci) {
        gv$firePieceHook(piece, heightAccessor, randomState);
    }
    //?} else {
    /*@Inject(method = "tryPlacingChildren", at = @At("HEAD"))
    private void gv$onTryPlacingChildren(
            PoolElementStructurePiece piece,
            MutableObject<?> freeSpace,
            int depth,
            boolean useExpansionHack,
            LevelHeightAccessor heightAccessor,
            RandomState randomState,
            net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup poolAliasLookup,
            net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings liquidSettings,
            CallbackInfo ci) {
        gv$firePieceHook(piece, heightAccessor, randomState);
    }
    *///?}

    private void gv$firePieceHook(PoolElementStructurePiece piece, LevelHeightAccessor heightAccessor, RandomState randomState) {
        if (!VillageTagContext.isVillage()) {
            return;
        }
        TerrainSampler sampler = TerrainSampler.of(chunkGenerator, randomState, heightAccessor);
        BoundingBox boundingBox = piece.getBoundingBox();
        PieceDecision decision = HookRegistry.pieceHook().onChild(piece, boundingBox, sampler);
        HookDebug.fired("VillagePieceHook", decision);
    }
}
