package grounded_villages.gametest;

import grounded_villages.hook.HookRegistry;
import grounded_villages.hook.PieceDecision;
import grounded_villages.mixinsupport.PieceLadderContext;
import grounded_villages.mixinsupport.StartHeightContext;
import grounded_villages.piece.PieceLadder;
import grounded_villages.piece.PieceRejectionHook;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;

/**
 * The GV-11 scenario bodies, called from a thin, per-loader {@code @GameTest}-annotated method in
 * {@code grounded_villages.fabric.gametest}/{@code grounded_villages.neoforge.gametest} (both
 * loaders' own game-test framework ultimately hands a real {@link GameTestHelper} to a
 * {@code Consumer}-shaped method, so one body serves both -- see the package's own javadoc).
 *
 * <h2>Water/dry ground: {@link #waterFootprintIsRejected}/{@link #dryFootprintIsAccepted}</h2>
 *
 * A live proof, not a synthetic-double one: a real, small footprint is built from real blocks
 * placed by {@code GameTestHelper#setBlock}, and {@link LiveLevelTerrainSampler} reads them back
 * via a direct column scan (its own javadoc has the finding: a game test's default {@code
 * skyAccess=false} caps the whole test structure with a barrier ceiling, and neither the raw
 * {@code OCEAN_FLOOR_WG}/{@code WORLD_SURFACE_WG} nor their persisted {@code OCEAN_FLOOR}/{@code
 * WORLD_SURFACE} counterparts reflect a game test's own placed blocks at all -- both findings
 * confirmed live, not assumed) -- the exact ground/surface pair {@code
 * grounded_villages.piece.PieceRejectionHook#toHeightSampler} adapts for {@code PieceGate}. The
 * hook under test is {@link PieceRejectionHook#INSTANCE} itself (registered live by both loader
 * entrypoints at mod init, also reachable via {@link HookRegistry#pieceHook()}) -- the real
 * production call path {@code grounded_villages.mixin.village.PlacerMixin}'s own constructor
 * redirect makes, given a real {@link PoolElementStructurePiece} and {@link BoundingBox}. Every
 * {@code @GameTest} annotation in both loaders' own test classes sets {@code skyAccess = true} for
 * exactly this reason.
 *
 * <h2>Connector invariant / non-mutation: {@link #rejectionLeavesNoDanglingConnector}/{@link
 * #acceptedCandidateIsCommittedAndUnmutated}</h2>
 *
 * <p><b>What this does not do, and why</b>: it does not drive
 * {@code JigsawPlacement$Placer.tryPlacingChildren} itself through vanilla's real jigsaw assembly.
 * That would need either a full structure-set/pool registration plus real chunk generation over
 * this mod's own hook (a game test region is a bounded, pre-built template, not generated terrain
 * -- {@code docs/spec/operations/testing.md}'s own "Game tests" row states this explicitly), or a
 * hand-rolled {@code Structure.GenerationContext}/{@code RandomState} well past what a per-loader
 * smoke proof needs. Ticket GV-11's own build item 1 names exactly this fallback for exactly this
 * reason: "assert on {@code PieceGate}/{@code PlacerMixin}'s verdict plumbing with a recorded
 * rationale."
 *
 * <p>What it does instead, real rather than reimplemented: constructs two real {@link
 * PoolElementStructurePiece} objects (parent, child) from the test level's own real {@link
 * StructureTemplateManager}; calls the real {@link HookRegistry#pieceHook()} (identically to
 * {@code PlacerMixin#gv$evaluate}); stashes the verdict in the real {@link PieceLadderContext}
 * (identically to {@code PlacerMixin#gv$evaluate}); and then applies the exact one-line guard
 * {@code PlacerMixin}'s three/four redirects each use --
 * {@code if (!PieceLadderContext.isCurrentCandidateRejected())} -- verbatim, before calling the
 * real {@code PoolElementStructurePiece#addJunction}/{@code List#add}. Every class in that chain
 * except the mixin's own bytecode redirection is the genuine production object; the guard
 * condition is copied, not reimplemented, from {@code PlacerMixin}'s own source.
 *
 * <h2>{@link #ladderRollsShrinkAndRetryFromSyntheticCounts}</h2>
 *
 * {@code grounded_villages.piece.PieceLadder} is already exhaustively unit-tested on synthetic
 * counts ({@code PieceLadderTest}); this one game test exists only to prove the same pure decision
 * is reachable and correct from inside a real, running game-test server (ticket GV-11 build item
 * 1's own "the ladder's decisions on synthetic counts"), not to re-cover its own branches.
 */
public final class PieceGateScenarios {

    /** The footprint's own relative origin corner -- inside every test structure's 8x8x8 bounds
     *  with room on every side, regardless of the per-loader template this runs against. */
    private static final BlockPos ORIGIN = new BlockPos(1, 1, 1);
    private static final int FOOTPRINT_SIZE = 4;

    private PieceGateScenarios() {
    }

    public static void waterFootprintIsRejected(GameTestHelper helper) {
        BoundingBox footprint = buildFootprint(helper, true);
        PieceDecision decision = evaluate(helper, footprint);
        helper.assertTrue(decision == PieceDecision.REJECT,
                "PieceRejectionHook.INSTANCE.onChild accepted a footprint whose sampled points are all "
                        + "under a water block -- PIECE-REQ-002 (docs/spec/domains/pieces.md) requires "
                        + "rejection here.");
        helper.succeed();
    }

    public static void dryFootprintIsAccepted(GameTestHelper helper) {
        BoundingBox footprint = buildFootprint(helper, false);
        PieceDecision decision = evaluate(helper, footprint);
        helper.assertTrue(decision == PieceDecision.ACCEPT,
                "PieceRejectionHook.INSTANCE.onChild rejected a footprint on dry, flat ground at the "
                        + "village's own start height -- expected an accept.");
        helper.succeed();
    }

    public static void rejectionLeavesNoDanglingConnector(GameTestHelper helper) {
        BoundingBox footprint = buildFootprint(helper, true);
        ServerLevel level = helper.getLevel();
        StructureTemplateManager templateManager = level.getStructureManager();

        PoolElementStructurePiece parent = newCandidate(templateManager, offsetX(footprint, -8));
        PoolElementStructurePiece child = newCandidate(templateManager, footprint);
        List<Object> committedPieces = new ArrayList<>();

        PieceDecision decision = HookRegistry.pieceHook().onChild(child, footprint, new LiveLevelTerrainSampler(level));
        PieceLadderContext.setCurrentCandidateRejected(decision == PieceDecision.REJECT);

        // The exact guard grounded_villages.mixin.village.PlacerMixin's own gv$redirectAddJunction
        // and gv$redirectPiecesAdd use, verbatim -- see this class' own javadoc.
        if (!PieceLadderContext.isCurrentCandidateRejected()) {
            parent.addJunction(junctionFor(footprint));
            committedPieces.add(child);
        }

        StartHeightContext.set(null);
        helper.assertTrue(decision == PieceDecision.REJECT, "expected the water footprint to be rejected first");
        helper.assertTrue(parent.getJunctions().isEmpty(),
                "PIECE-FAIL-002: a rejected child must leave no junction on its parent, but one was recorded");
        helper.assertTrue(!committedPieces.contains(child),
                "a rejected child must never be committed to the pieces list");
        helper.succeed();
    }

    public static void acceptedCandidateIsCommittedAndUnmutated(GameTestHelper helper) {
        BoundingBox footprint = buildFootprint(helper, false);
        ServerLevel level = helper.getLevel();
        StructureTemplateManager templateManager = level.getStructureManager();

        PoolElementStructurePiece parent = newCandidate(templateManager, offsetX(footprint, -8));
        PoolElementStructurePiece child = newCandidate(templateManager, footprint);
        BoundingBox childBoxBeforeEvaluation = child.getBoundingBox();
        int junctionsBeforeEvaluation = child.getJunctions().size();
        List<Object> committedPieces = new ArrayList<>();

        PieceDecision decision = HookRegistry.pieceHook().onChild(child, footprint, new LiveLevelTerrainSampler(level));
        PieceLadderContext.setCurrentCandidateRejected(decision == PieceDecision.REJECT);
        StartHeightContext.set(null);

        helper.assertTrue(decision == PieceDecision.ACCEPT, "expected the dry footprint to be accepted");
        helper.assertTrue(child.getBoundingBox().equals(childBoxBeforeEvaluation),
                "evaluating the hook must never mutate the candidate's own bounding box");
        helper.assertTrue(child.getJunctions().size() == junctionsBeforeEvaluation,
                "evaluating the hook must never mutate the candidate's own junction list");

        if (!PieceLadderContext.isCurrentCandidateRejected()) {
            parent.addJunction(junctionFor(footprint));
            committedPieces.add(child);
        }

        helper.assertTrue(!parent.getJunctions().isEmpty(), "an accepted child must be committed to its parent's junctions");
        helper.assertTrue(committedPieces.contains(child), "an accepted child must be committed to the pieces list");
        helper.succeed();
    }

    public static void ladderRollsShrinkAndRetryFromSyntheticCounts(GameTestHelper helper) {
        helper.assertTrue(PieceLadder.evaluate(0, 40, 4) == PieceLadder.Outcome.UNAFFECTED, "0 rejected must never engage the ladder");
        helper.assertTrue(PieceLadder.evaluate(4, 26, 4) == PieceLadder.Outcome.SHRINK, "survivors clearing hamlet_minimum_pieces must shrink");
        helper.assertTrue(PieceLadder.evaluate(20, 2, 4) == PieceLadder.Outcome.RETRY, "survivors short of hamlet_minimum_pieces must retry");
        helper.succeed();
    }

    // --- shared scaffolding ---

    /** Places a dry stone floor over {@link #ORIGIN}'s own {@value #FOOTPRINT_SIZE}x{@value
     *  #FOOTPRINT_SIZE} area, then -- only when {@code water} -- a shallow water pool one block
     *  above every point of it, so every one of {@code PieceGate#samplePoints}' corners+centre
     *  sample lands on water. Returns the real, absolute {@link BoundingBox} this footprint
     *  occupies, and sets {@link StartHeightContext} to this same area's own real ground height
     *  (deviation 0, so the height-deviation check never fires on its own in either scenario). */
    private static BoundingBox buildFootprint(GameTestHelper helper, boolean water) {
        ServerLevel level = helper.getLevel();
        for (int dx = 0; dx <= FOOTPRINT_SIZE; dx++) {
            for (int dz = 0; dz <= FOOTPRINT_SIZE; dz++) {
                BlockPos floor = ORIGIN.offset(dx, 0, dz);
                helper.setBlock(floor, Blocks.STONE.defaultBlockState());
                helper.setBlock(floor.above(), Blocks.AIR.defaultBlockState());
                helper.setBlock(floor.above(2), Blocks.AIR.defaultBlockState());
                if (water) {
                    helper.setBlock(floor.above(), Blocks.WATER.defaultBlockState());
                }
            }
        }

        BlockPos minAbs = helper.absolutePos(ORIGIN);
        BlockPos maxAbs = helper.absolutePos(ORIGIN.offset(FOOTPRINT_SIZE, 0, FOOTPRINT_SIZE));
        BoundingBox footprint = new BoundingBox(
                Math.min(minAbs.getX(), maxAbs.getX()), minAbs.getY(), Math.min(minAbs.getZ(), maxAbs.getZ()),
                Math.max(minAbs.getX(), maxAbs.getX()), minAbs.getY(), Math.max(minAbs.getZ(), maxAbs.getZ()));

        int centreX = (footprint.minX() + footprint.maxX()) / 2;
        int centreZ = (footprint.minZ() + footprint.maxZ()) / 2;
        // grounded_villages.piece.PieceGate's own height-deviation check compares a piece's
        // OCEAN_FLOOR_WG ground height against this same value -- LiveLevelTerrainSampler's own
        // direct scan, not ServerLevel#getHeight, for the reason its own javadoc records.
        StartHeightContext.set(new LiveLevelTerrainSampler(level).getBaseHeight(centreX, centreZ, Heightmap.Types.OCEAN_FLOOR_WG));
        return footprint;
    }

    private static PieceDecision evaluate(GameTestHelper helper, BoundingBox footprint) {
        ServerLevel level = helper.getLevel();
        StructureTemplateManager templateManager = level.getStructureManager();
        PoolElementStructurePiece candidate = newCandidate(templateManager, footprint);
        try {
            return PieceRejectionHook.INSTANCE.onChild(candidate, footprint, new LiveLevelTerrainSampler(level));
        } finally {
            StartHeightContext.set(null);
        }
    }

    private static PoolElementStructurePiece newCandidate(StructureTemplateManager templateManager, BoundingBox box) {
        BlockPos pos = new BlockPos(box.minX(), box.minY(), box.minZ());
        return new PoolElementStructurePiece(
                templateManager, EmptyPoolElement.INSTANCE, pos, 0, Rotation.NONE, box, LiquidSettings.IGNORE_WATERLOGGING);
    }

    private static JigsawJunction junctionFor(BoundingBox box) {
        return new JigsawJunction(box.minX(), box.minY(), box.minZ(), 0, StructureTemplatePool.Projection.RIGID);
    }

    /** A fresh {@link BoundingBox} offset on X only -- deliberately not {@code BoundingBox#move},
     *  whose own mutate-vs-copy contract this file does not rely on either way. */
    private static BoundingBox offsetX(BoundingBox box, int dx) {
        return new BoundingBox(box.minX() + dx, box.minY(), box.minZ(), box.maxX() + dx, box.maxY(), box.maxZ());
    }
}
