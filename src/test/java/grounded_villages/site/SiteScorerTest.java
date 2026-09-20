package grounded_villages.site;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `docs/spec/operations/testing.md` "Unit": {@code SiteScorer}'s accept/reject inputs -- flat,
 * cliff, lake, and slope synthetic height fields (ticket build item 4), covering the height-spread
 * and water-fraction metrics independently. All at {@code radius = 16}, {@code step = 8} (5
 * points per axis, 25 columns, exactly {@link SiteScorer#MAX_SAMPLE_COLUMNS}), so the grid points
 * are the same known set (-16, -8, 0, 8, 16) in every test below.
 */
final class SiteScorerTest {

    private static final int RADIUS = 16;

    @Test
    void flatDryTerrainHasZeroSpreadAndZeroWaterFraction() {
        FakeHeightSampler sampler = FakeHeightSampler.flat(64);

        SiteScore score = SiteScorer.score(sampler, 0, 0, RADIUS);

        assertEquals(0.0, score.heightSpread());
        assertEquals(0.0, score.waterFraction());
        assertEquals(25, score.sampleCount());
    }

    @Test
    void aCliffProducesTheExactHeightSpreadBetweenItsTwoLevels() {
        // x < 0 sits at 60, x >= 0 sits at 100 -- both heightmap types agree (no water), so this
        // is a pure height-spread case. 2 of the 5 axis-x values are negative (-16, -8), 3 are
        // >= 0 (0, 8, 16): 10 low columns, 15 high columns out of 25, sorted ascending -- both
        // percentile ranks land inside one homogeneous block either way, so no interpolation
        // blurs the result.
        FakeHeightSampler.Field field = (x, z) -> x < 0 ? 60 : 100;
        FakeHeightSampler sampler = new FakeHeightSampler(field, field);

        SiteScore score = SiteScorer.score(sampler, 0, 0, RADIUS);

        // rank(10%) = 0.10 * 24 = 2.4 -> within the 10 low columns (indices 0-9) -> 60.
        // rank(90%) = 0.90 * 24 = 21.6 -> within the 15 high columns (indices 10-24) -> 100.
        assertEquals(40.0, score.heightSpread());
        assertEquals(0.0, score.waterFraction());
    }

    @Test
    void aLakeProducesWaterFractionWithNoHeightSpread() {
        // True ground (groundHeight) is flat everywhere; a 17x17-block square lake sits over the
        // middle, so surfaceHeight reads higher than the ground for the 3x3 = 9 sampled columns
        // whose |x| and |z| are both <= 8.
        FakeHeightSampler sampler = new FakeHeightSampler(
                (x, z) -> 64,
                (x, z) -> (Math.abs(x) <= 8 && Math.abs(z) <= 8) ? 70 : 64);

        SiteScore score = SiteScorer.score(sampler, 0, 0, RADIUS);

        assertEquals(0.0, score.heightSpread());
        assertEquals(9.0 / 25.0, score.waterFraction(), 1e-9);
    }

    @Test
    void aSlopeProducesAHeightSpreadProportionalToItsGradient() {
        // Height rises 2 blocks per 8-block step along x only, both heightmap types agree (no
        // water): 5 distinct height values (64..72), 5 columns at each (one per z row), sorted
        // ascending in 5 blocks of 5.
        FakeHeightSampler.Field field = (x, z) -> 64 + ((x + RADIUS) / 8) * 2;
        FakeHeightSampler sampler = new FakeHeightSampler(field, field);

        SiteScore score = SiteScorer.score(sampler, 0, 0, RADIUS);

        // rank(10%) = 2.4 -> block index 2/5 = 0 -> height 64. rank(90%) = 21.6 -> block index
        // 21/5 = 4 -> height 72.
        assertEquals(8.0, score.heightSpread());
        assertEquals(0.0, score.waterFraction());
    }

    @Test
    void qualifiesIsInclusiveAtBothThresholds() {
        SiteScore atTheLine = new SiteScore(12.0, 0.05, 100);

        assertTrue(atTheLine.qualifies(12, 0.05));
    }

    @Test
    void aRadiusPastTheColumnCapIsCoarsenedRatherThanExceedingIt() {
        FakeHeightSampler sampler = FakeHeightSampler.flat(64);

        // The engine's own 128-block hard ceiling (domains/tiers.md "Spacing") -- the largest
        // radius any caller (a future city tier, GV-8) could ever hand SiteScorer.
        SiteScore score = SiteScorer.score(sampler, 0, 0, 128);

        assertTrue(score.sampleCount() <= SiteScorer.MAX_SAMPLE_COLUMNS,
                () -> "sampled " + score.sampleCount() + " columns, cap is " + SiteScorer.MAX_SAMPLE_COLUMNS);
        // A naive step-8 grid at radius 128 would be 33x33 = 1089 -- confirm the cap actually did
        // something, not merely that it happens to hold.
        assertTrue(score.sampleCount() < 1089);
    }

    @Test
    void aNegativeRadiusIsRejected() {
        FakeHeightSampler sampler = FakeHeightSampler.flat(64);

        assertThrows(IllegalArgumentException.class, () -> SiteScorer.score(sampler, 0, 0, -1));
    }
}
