package grounded_villages.piece;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `docs/spec/operations/testing.md` "Unit": {@link PieceGate}'s accept/reject inputs -- flat dry,
 * a lake edge, a cliff, and a slope synthetic footprint (ticket GV-7 build item 4), covering
 * `PIECE-REQ-001`-{@code 004} independently of any Minecraft type.
 */
final class PieceGateTest {

    private static final int MAX_HEIGHT_DEVIATION = 6;

    @Test
    void flatDryFootprintWithinDeviationIsAccepted() {
        FakeHeightSampler sampler = FakeHeightSampler.flat(64);
        PieceFootprint footprint = new PieceFootprint(-8, 8, -8, 8);

        PieceVerdict verdict = PieceGate.evaluate(sampler, footprint, 64, MAX_HEIGHT_DEVIATION);

        assertTrue(verdict.accepted());
        assertEquals(PieceRejectionReason.NONE, verdict.reason());
    }

    @Test
    void aLakeEdgeUnderTheFootprintIsRejectedForWater() {
        // Ground is flat and within deviation everywhere; the footprint's own maxX/maxZ corner
        // sits over a lake (surfaceHeight > groundHeight there only) -- a "lake edge" case where
        // most of the piece is dry but the corner touches water.
        FakeHeightSampler sampler = new FakeHeightSampler(
                (x, z) -> 64,
                (x, z) -> (x == 8 && z == 8) ? 70 : 64);
        PieceFootprint footprint = new PieceFootprint(-8, 8, -8, 8);

        PieceVerdict verdict = PieceGate.evaluate(sampler, footprint, 64, MAX_HEIGHT_DEVIATION);

        assertFalse(verdict.accepted());
        assertEquals(PieceRejectionReason.WATER, verdict.reason());
    }

    @Test
    void aCliffUnderTheFootprintIsRejectedForHeightDeviation() {
        // No water anywhere; the footprint straddles a cliff so its far corner's ground height
        // is 20 blocks from the village's start height, well past the default tolerance (6).
        FakeHeightSampler sampler = new FakeHeightSampler((x, z) -> x < 0 ? 64 : 84, (x, z) -> x < 0 ? 64 : 84);
        PieceFootprint footprint = new PieceFootprint(-8, 8, -8, 8);

        PieceVerdict verdict = PieceGate.evaluate(sampler, footprint, 64, MAX_HEIGHT_DEVIATION);

        assertFalse(verdict.accepted());
        assertEquals(PieceRejectionReason.HEIGHT_DEVIATION, verdict.reason());
    }

    @Test
    void aGentleSlopeWithinToleranceIsAccepted() {
        // A slope of 0.5 blocks/block across a 16-block-wide footprint: at most a 4-block swing
        // from corner to corner (well under the 6-block default tolerance), and no water.
        FakeHeightSampler sampler = new FakeHeightSampler((x, z) -> 64 + x / 2, (x, z) -> 64 + x / 2);
        PieceFootprint footprint = new PieceFootprint(-8, 8, -8, 8);

        PieceVerdict verdict = PieceGate.evaluate(sampler, footprint, 64, MAX_HEIGHT_DEVIATION);

        assertTrue(verdict.accepted());
    }

    @Test
    void aSteeperSlopeExceedingToleranceIsRejectedForHeightDeviation() {
        // A slope of 1 block/block across the same 16-block-wide footprint: an 8-block swing,
        // past the 6-block default tolerance at the far corner.
        FakeHeightSampler sampler = new FakeHeightSampler((x, z) -> 64 + x, (x, z) -> 64 + x);
        PieceFootprint footprint = new PieceFootprint(-8, 8, -8, 8);

        PieceVerdict verdict = PieceGate.evaluate(sampler, footprint, 64, MAX_HEIGHT_DEVIATION);

        assertFalse(verdict.accepted());
        assertEquals(PieceRejectionReason.HEIGHT_DEVIATION, verdict.reason());
    }

    @Test
    void heightDeviationIsMeasuredAgainstStartHeightNotLocalTerrain() {
        // Flat, dry footprint at 64 everywhere -- but the village's own start height is 90, a
        // 26-block gap this piece's own locally-flat terrain does not reveal on its own.
        FakeHeightSampler sampler = FakeHeightSampler.flat(64);
        PieceFootprint footprint = new PieceFootprint(-8, 8, -8, 8);

        PieceVerdict verdict = PieceGate.evaluate(sampler, footprint, 90, MAX_HEIGHT_DEVIATION);

        assertFalse(verdict.accepted());
        assertEquals(PieceRejectionReason.HEIGHT_DEVIATION, verdict.reason());
    }

    @Test
    void exactlyAtTheDeviationBoundaryIsAccepted() {
        // <= maxHeightDeviation qualifies, mirroring SiteScore#qualifies' own "<=, not <" rule.
        FakeHeightSampler sampler = FakeHeightSampler.flat(70);

        PieceVerdict verdict = PieceGate.evaluate(sampler, new PieceFootprint(-8, 8, -8, 8), 64, MAX_HEIGHT_DEVIATION);

        assertTrue(verdict.accepted());
    }

    @Test
    void aFootprintWithBothWaterAndHeightProblemsReportsWaterFirst() {
        // PIECE-REQ-002 is listed before PIECE-REQ-003; water is checked across every point
        // before height deviation is checked across any -- a stable tie-break, not a priority
        // claim about which failure "matters more" for a footprint failing both ways at once.
        FakeHeightSampler sampler = new FakeHeightSampler(
                (x, z) -> 64 + x, // a cliff -- would fail height deviation on its own
                (x, z) -> (x == 8 && z == 8) ? 999 : 64 + x); // and the far corner is also water
        PieceFootprint footprint = new PieceFootprint(-8, 8, -8, 8);

        PieceVerdict verdict = PieceGate.evaluate(sampler, footprint, 64, MAX_HEIGHT_DEVIATION);

        assertFalse(verdict.accepted());
        assertEquals(PieceRejectionReason.WATER, verdict.reason());
    }

    @Test
    void streetsAndBuildingsShareTheIdenticalCheck() {
        // PIECE-REQ-004: PieceGate takes no piece-kind parameter at all -- the identical call,
        // identical footprint, identical result regardless of what the caller considers this
        // piece to be, is itself the proof there is no special case here.
        FakeHeightSampler sampler = new FakeHeightSampler((x, z) -> 64, (x, z) -> 70);
        PieceFootprint footprint = new PieceFootprint(0, 4, 0, 4);

        PieceVerdict asIfStreet = PieceGate.evaluate(sampler, footprint, 64, MAX_HEIGHT_DEVIATION);
        PieceVerdict asIfBuilding = PieceGate.evaluate(sampler, footprint, 64, MAX_HEIGHT_DEVIATION);

        assertEquals(asIfStreet, asIfBuilding);
        assertEquals(PieceRejectionReason.WATER, asIfStreet.reason());
    }

    // --- sampling shape (package-visible, ticket "Approach": "5 points minimum ... more for a
    // larger footprint") ---

    @Test
    void aSmallFootprintSamplesExactlyFiveCornersAndCentre() {
        List<int[]> points = PieceGate.samplePoints(new PieceFootprint(-4, 4, -4, 4));

        assertEquals(5, points.size());
    }

    @Test
    void aWideFootprintSamplesNineIncludingEdgeMidpoints() {
        List<int[]> points = PieceGate.samplePoints(new PieceFootprint(-20, 20, -20, 20));

        assertEquals(9, points.size());
    }

    @Test
    void aFootprintWideOnOnlyOneAxisSamplesSeven() {
        List<int[]> points = PieceGate.samplePoints(new PieceFootprint(-20, 20, -4, 4));

        assertEquals(7, points.size());
    }

    @Test
    void invertedFootprintBoundsThrow() {
        assertThrows(IllegalArgumentException.class, () -> new PieceFootprint(8, -8, 0, 0));
    }
}
