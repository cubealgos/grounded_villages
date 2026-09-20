package grounded_villages.mixin.village;

import grounded_villages.config.ConfigHolder;
import grounded_villages.hook.HookRegistry;
import grounded_villages.hook.PieceDecision;
import grounded_villages.hook.TerrainSampler;
import grounded_villages.mixinsupport.PieceLadderContext;
import grounded_villages.mixinsupport.VillageTagContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Deque;
import java.util.List;

/**
 * The per-piece hook (`docs/spec/domains/pieces.md`, ticket GV-7 build item 1): real, per-child
 * accept/reject, superseding GV-5's coarse per-parent-only wiring (this class' own javadoc used
 * to read "real per-child accept/reject ... needs a finer injection GV-7 adds" -- this is that
 * injection).
 *
 * <h2>Where vanilla actually commits a child candidate (bytecode evidence, all three targeted
 * versions, {@code javap -p -c -l} on the Loom-mapped jars)</h2>
 *
 * {@code tryPlacingChildren} is four nested loops: per jigsaw connector of the parent piece, per
 * candidate {@code StructurePoolElement} for that connector, per {@code Rotation} of that
 * candidate, per jigsaw block of that candidate+rotation trying to find one that both {@code
 * JigsawBlock.canAttach}es and does not collide with an already-placed piece's own {@code
 * VoxelShape} (the {@code Shapes.joinIsNotEmpty(..., ONLY_SECOND)} check vanilla already runs).
 * Only once ALL of those already pass does vanilla reach {@code new PoolElementStructurePiece(...)}
 * -- at that point vanilla has already decided this exact candidate fits; there is no further
 * "does it fit" branch downstream. Immediately after construction, three more calls commit it
 * unconditionally: {@code parentPiece.addJunction(...)}, {@code newPiece.addJunction(...)}, then
 * {@code this.pieces.add(newPiece)} (ignoring the returned {@code boolean}), and -- only if
 * vanilla's own depth budget allows recursion -- the {@code placing} queue's own {@code add}
 * ({@code SequencedPriorityIterator.add(Object, int)} on 1.21.1/26.2, {@code
 * Deque.addLast(Object)} on 1.20.1, the one version-shaped delta in this whole sequence; confirmed
 * unambiguous per version -- exactly one {@code new PoolElementStructurePiece}, one raw {@code
 * List.add(Object)} distinct from the pool-gathering {@code List.addAll} calls, and one placing-
 * queue add, all inside {@code tryPlacingChildren} alone).
 *
 * <p><b>The dangling-connector question ({@code PIECE-FAIL-002}), now bytecode-confirmed, not
 * just structurally likely</b>: every exit from the candidate-selection loops -- successfully
 * committing a piece, exhausting every rotation of one candidate, exhausting every candidate for
 * one connector -- converges on the exact same {@code goto} target (the outer per-connector
 * loop's own back-edge). A connector that runs out of fitting candidates already falls through to
 * "move on to the next connector" with nothing placed there; this is not a failure path this mod
 * invents, it is vanilla's own existing "sometimes a connector legitimately gets nothing"
 * behaviour ({@code EmptyPoolElement} is one of the pool's own candidate entries for exactly this
 * reason). Rejecting a candidate at the exact point below -- after {@code new
 * PoolElementStructurePiece} but before either {@code addJunction} call or {@code pieces.add} --
 * and no-op'ing those downstream calls for it therefore lands this mod's own rejection on that
 * same, already-correct vanilla path: no partial junction is ever recorded on the parent (no
 * dangling reference), the child is never added to {@code pieces} (never rendered) and never
 * queued for further recursion (no floating grandchildren attached to an invisible parent, the
 * strictly worse failure a redirect on {@code pieces.add} alone -- without also gating {@code
 * addJunction} and the {@code placing} queue -- would have produced), and control simply falls
 * through to the same {@code goto} the "no candidate fit this connector" case already uses. No
 * cancel, no synthesized jump: every rejected candidate below still runs every one of vanilla's
 * own remaining bytecode instructions for that path, each one just individually redirected to a
 * no-op.
 *
 * <p><b>Mechanism</b>: {@link #gv$redirectNewChild} (per-version-descriptor {@code @Redirect} on
 * the {@code NEW}) is the one real per-child evaluation point -- it constructs the genuine
 * candidate (so nothing downstream ever sees a null or placeholder piece; a redirect must still
 * return a real object), evaluates {@link HookRegistry#pieceHook()} against its real, already
 * terrain-projected {@code BoundingBox}, and stashes the verdict in {@link
 * PieceLadderContext#setCurrentCandidateRejected}. {@link #gv$redirectAddJunction}, {@link
 * #gv$redirectPiecesAdd} and the version-branched {@code placing}-queue redirect all read that
 * same stashed verdict and no-op instead of committing when it says reject -- see {@link
 * PieceLadderContext}'s own javadoc for why no reset between candidates is needed. {@link
 * #gv$prepare} (the surviving {@code @Inject at HEAD}, now purely a per-parent-call setup step,
 * not itself a decision point -- GV-5's original coarse {@code gv$firePieceHook} call is gone,
 * superseded by the mechanism above) builds the one {@link TerrainSampler} this whole candidate
 * loop reuses and gates it on village tag / {@code piece.enabled} / the ladder's own bypass flag
 * (`decisions/DEC-010-shrink-move-vanilla.md` step 3) in one place, so every downstream redirect
 * only has to check "is there a sampler" rather than re-deriving all three conditions itself.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement$Placer")
abstract class PlacerMixin {

    @Shadow
    @Final
    private ChunkGenerator chunkGenerator;

    //? if <1.21 {
    /*@Inject(method = "tryPlacingChildren", at = @At("HEAD"))
    private void gv$onTryPlacingChildren(
            PoolElementStructurePiece piece,
            MutableObject<?> freeSpace,
            int depth,
            boolean useExpansionHack,
            LevelHeightAccessor heightAccessor,
            RandomState randomState,
            CallbackInfo ci) {
        gv$prepare(heightAccessor, randomState);
    }

    @Redirect(method = "tryPlacingChildren", at = @At(value = "NEW", target = "Lnet/minecraft/world/level/levelgen/structure/PoolElementStructurePiece;"))
    private PoolElementStructurePiece gv$redirectNewChild(
            StructureTemplateManager structureTemplateManager,
            StructurePoolElement element,
            BlockPos pos,
            int groundLevelDelta,
            Rotation rotation,
            BoundingBox boundingBox) {
        PoolElementStructurePiece candidate =
                new PoolElementStructurePiece(structureTemplateManager, element, pos, groundLevelDelta, rotation, boundingBox);
        gv$evaluate(candidate, boundingBox);
        return candidate;
    }

    @Redirect(method = "tryPlacingChildren", at = @At(value = "INVOKE", target = "Ljava/util/Deque;addLast(Ljava/lang/Object;)V"))
    private void gv$redirectPlacingAdd(Deque<Object> instance, Object element) {
        if (!PieceLadderContext.isCurrentCandidateRejected()) {
            instance.addLast(element);
        }
    }
    *///?} else {
    @Inject(method = "tryPlacingChildren", at = @At("HEAD"))
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
        gv$prepare(heightAccessor, randomState);
    }

    @Redirect(method = "tryPlacingChildren", at = @At(value = "NEW", target = "Lnet/minecraft/world/level/levelgen/structure/PoolElementStructurePiece;"))
    private PoolElementStructurePiece gv$redirectNewChild(
            StructureTemplateManager structureTemplateManager,
            StructurePoolElement element,
            BlockPos pos,
            int groundLevelDelta,
            Rotation rotation,
            BoundingBox boundingBox,
            net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings liquidSettings) {
        PoolElementStructurePiece candidate =
                new PoolElementStructurePiece(structureTemplateManager, element, pos, groundLevelDelta, rotation, boundingBox, liquidSettings);
        gv$evaluate(candidate, boundingBox);
        return candidate;
    }

    @Redirect(method = "tryPlacingChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/SequencedPriorityIterator;add(Ljava/lang/Object;I)V"))
    private void gv$redirectPlacingAdd(net.minecraft.util.SequencedPriorityIterator<Object> instance, Object element, int priority) {
        if (!PieceLadderContext.isCurrentCandidateRejected()) {
            instance.add(element, priority);
        }
    }
    //?}

    @Redirect(method = "tryPlacingChildren", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/PoolElementStructurePiece;addJunction(Lnet/minecraft/world/level/levelgen/structure/pools/JigsawJunction;)V"))
    private void gv$redirectAddJunction(PoolElementStructurePiece instance, JigsawJunction junction) {
        if (!PieceLadderContext.isCurrentCandidateRejected()) {
            instance.addJunction(junction);
        }
    }

    @Redirect(method = "tryPlacingChildren", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private boolean gv$redirectPiecesAdd(List<Object> instance, Object element) {
        if (PieceLadderContext.isCurrentCandidateRejected()) {
            return false;
        }
        return instance.add(element);
    }

    /**
     * Builds the one {@link TerrainSampler} the whole candidate loop below reuses, or clears it
     * ({@code null}) when this call should not be evaluated at all: not a village-tagged
     * structure, {@code piece.enabled} is {@code false} ({@code PIECE-REQ-005}), or the ladder's
     * own vanilla-fallback attempt has {@link PieceLadderContext#isBypass} set
     * (`decisions/DEC-010-shrink-move-vanilla.md` step 3: "no piece check"). {@link #gv$evaluate}
     * fails open (accepts) whenever the sampler is {@code null}, so this one gate covers every
     * no-op case for the whole rest of this class.
     */
    private void gv$prepare(LevelHeightAccessor heightAccessor, RandomState randomState) {
        if (!VillageTagContext.isVillage() || PieceLadderContext.isBypass() || !ConfigHolder.get().piece().enabled()) {
            PieceLadderContext.setSampler(null);
            return;
        }
        PieceLadderContext.setSampler(TerrainSampler.of(chunkGenerator, randomState, heightAccessor));
    }

    /** The one real per-child evaluation point -- see this class' own javadoc for the mechanism. */
    private void gv$evaluate(PoolElementStructurePiece candidate, BoundingBox boundingBox) {
        TerrainSampler sampler = PieceLadderContext.getSampler();
        if (sampler == null) {
            PieceLadderContext.setCurrentCandidateRejected(false);
            return;
        }
        PieceDecision decision = HookRegistry.pieceHook().onChild(candidate, boundingBox, sampler);
        PieceLadderContext.setCurrentCandidateRejected(decision == PieceDecision.REJECT);
    }
}
