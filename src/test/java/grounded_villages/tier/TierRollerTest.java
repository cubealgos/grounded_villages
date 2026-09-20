package grounded_villages.tier;

import grounded_villages.config.ConfigModel;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `docs/spec/domains/tiers.md` `TIER-REQ-001`-`005`, `TIER-FAIL-001`/`002` (GV-8's own acceptance
 * criteria): determinism, the weighted distribution within tolerance, the performance-cap clamp,
 * and the zero-weight fallback. {@link TierRoller} is a pure function over a {@code [0, 1)} draw
 * and a {@link ConfigModel.Tier}, so every case here is a plain unit test -- no Minecraft
 * classpath needed (`docs/spec/operations/testing.md` "Unit" layer).
 */
final class TierRollerTest {

    private static ConfigModel.Tier tierConfig(
            boolean enabled,
            ConfigModel.TierWeights weights,
            ConfigModel.TierBudget jigsawDepth,
            ConfigModel.TierBudget maxDistance,
            double performanceCapMultiplier) {
        return new ConfigModel.Tier(enabled, weights, jigsawDepth, maxDistance, 4, performanceCapMultiplier);
    }

    private static ConfigModel.Tier defaultTierConfig() {
        return tierConfig(
                true,
                new ConfigModel.TierWeights(30, 45, 20, 5),
                new ConfigModel.TierBudget(3, 6, 8, 10),
                new ConfigModel.TierBudget(80, 96, 128, 128),
                3.0);
    }

    // --- TIER-REQ-001: determinism ---

    @Test
    void theSameDrawAndConfigAlwaysRollsTheSameTier() {
        ConfigModel.Tier config = defaultTierConfig();

        TierAssignment first = TierRoller.roll(config, 0.42);
        TierAssignment second = TierRoller.roll(config, 0.42);

        assertEquals(first, second);
    }

    @Test
    void regeneratingTheSameSeedAndPositionRollsTheSameTierAcrossManyRepeats() {
        // Stands in for "same seed + position" (docs/spec/domains/tiers.md TIER-REQ-001): a real
        // WorldgenRandom seeded via setLargeFeatureSeed(worldSeed, chunkPos.x, chunkPos.z)
        // produces the same draw every time it is reseeded identically, so a fixed draw here is
        // the same guarantee, one layer down from the Minecraft-facing seeding mechanism itself.
        ConfigModel.Tier config = defaultTierConfig();
        double draw = 0.876543;

        TierAssignment expected = TierRoller.roll(config, draw);
        for (int i = 0; i < 100; i++) {
            assertEquals(expected, TierRoller.roll(config, draw), "regeneration " + i + " diverged");
        }
    }

    // --- TIER-REQ-002: weighted distribution ---

    @Test
    void tenThousandDrawsMatchTheConfiguredWeightsWithinTolerance() {
        ConfigModel.TierWeights weights = new ConfigModel.TierWeights(30, 45, 20, 5);
        int samples = 10_000;
        Map<Tier, Integer> counts = new EnumMap<>(Tier.class);
        for (Tier tier : Tier.values()) {
            counts.put(tier, 0);
        }

        Random random = new Random(20260920L);
        for (int i = 0; i < samples; i++) {
            Tier rolled = TierRoller.pickTier(weights, random.nextDouble());
            counts.merge(rolled, 1, Integer::sum);
        }

        // 30/45/20/5 out of 100 total weight -> expected fractions 0.30/0.45/0.20/0.05. At
        // n=10,000 the binomial standard deviation for even the largest bucket (p=0.45) is
        // ~0.005; a 0.03 (3 percentage point) tolerance is ~6 standard deviations out, so this
        // assertion is not expected to flake in practice while still catching a genuinely broken
        // weighting.
        double tolerance = 0.03;
        assertFraction(counts.get(Tier.HAMLET), samples, 0.30, tolerance, "hamlet");
        assertFraction(counts.get(Tier.VILLAGE), samples, 0.45, tolerance, "village");
        assertFraction(counts.get(Tier.TOWN), samples, 0.20, tolerance, "town");
        assertFraction(counts.get(Tier.CITY), samples, 0.05, tolerance, "city");
    }

    private static void assertFraction(int count, int total, double expected, double tolerance, String label) {
        double actual = (double) count / total;
        assertTrue(
                Math.abs(actual - expected) <= tolerance,
                label + ": expected fraction ~" + expected + ", got " + actual + " (" + count + "/" + total + ")");
    }

    @Test
    void aTierWithZeroWeightIsNeverRolled() {
        // TIER-REQ-006: tier.weights.city = 0 -> city never rolls.
        ConfigModel.TierWeights weights = new ConfigModel.TierWeights(30, 45, 25, 0);
        Random random = new Random(7L);
        for (int i = 0; i < 5_000; i++) {
            assertTrue(TierRoller.pickTier(weights, random.nextDouble()) != Tier.CITY);
        }
    }

    // --- TIER-REQ-004/TIER-REQ-005/TIER-FAIL-001: the performance cap ---

    @Test
    void aTierWithinThePerformanceCapKeepsItsOwnConfiguredDepth() {
        ConfigModel.Tier config = defaultTierConfig(); // cap = 3x117 = 351 -> max depth ~18, well above city's 10
        TierAssignment city = TierRoller.roll(config, drawFor(Tier.CITY, config.weights()));

        assertEquals(Tier.CITY, city.tier());
        assertEquals(10, city.jigsawDepth());
    }

    @Test
    void aRollWhoseBudgetExceedsThePerformanceCapIsClampedNotDenied() {
        // A deliberately tiny cap multiplier (the config floor, 0.1x) forces clamping: expected
        // cap = 117 * 0.1 = 11.7 pieces, well under city's un-clamped depth-10 budget.
        ConfigModel.Tier config = tierConfig(
                true,
                new ConfigModel.TierWeights(30, 45, 20, 5),
                new ConfigModel.TierBudget(3, 6, 8, 10),
                new ConfigModel.TierBudget(80, 96, 128, 128),
                0.1);

        TierAssignment city = TierRoller.roll(config, drawFor(Tier.CITY, config.weights()));

        // TIER-FAIL-001: still labelled and shaped as its rolled tier -- just capped.
        assertEquals(Tier.CITY, city.tier());
        assertTrue(city.jigsawDepth() < 10, "expected the depth to be clamped below city's own configured 10");
        assertTrue(city.jigsawDepth() >= 0);
    }

    @Test
    void thePerformanceCapNeverRaisesADepthAboveItsOwnConfiguredValue() {
        // A huge cap multiplier must never inflate hamlet's own small depth budget.
        ConfigModel.Tier config = tierConfig(
                true,
                new ConfigModel.TierWeights(30, 45, 20, 5),
                new ConfigModel.TierBudget(3, 6, 8, 10),
                new ConfigModel.TierBudget(80, 96, 128, 128),
                100.0);

        TierAssignment hamlet = TierRoller.roll(config, drawFor(Tier.HAMLET, config.weights()));

        assertEquals(Tier.HAMLET, hamlet.tier());
        assertEquals(3, hamlet.jigsawDepth());
    }

    // --- domains/tiers.md "Spacing": the 128-block hard cap ---

    @Test
    void maxDistanceIsNeverAboveThe128BlockHardCap() {
        ConfigModel.Tier config = defaultTierConfig();
        for (Tier tier : Tier.values()) {
            TierAssignment assignment = TierRoller.roll(config, drawFor(tier, config.weights()));
            assertTrue(assignment.maxDistance() <= TierRoller.HARD_MAX_DISTANCE,
                    tier + ": max distance " + assignment.maxDistance() + " exceeds the 128-block hard cap");
        }
    }

    // --- TIER-FAIL-002: weights summing to zero ---

    @Test
    void zeroWeightsAcrossEveryTierFallsBackToTheSafeVillageWeightedDistributionRatherThanCrashing() {
        ConfigModel.TierWeights allZero = new ConfigModel.TierWeights(0, 0, 0, 0);

        // Never throws; the fallback distribution (30/45/20/5, matching TIER-FAIL-002's "weighted
        // toward village" wording) is itself rollable -- every tier remains reachable.
        Tier rolledAtStart = TierRoller.pickTier(allZero, 0.0);
        Tier rolledNearEnd = TierRoller.pickTier(allZero, 0.999);
        assertEquals(Tier.HAMLET, rolledAtStart);
        assertEquals(Tier.CITY, rolledNearEnd);
    }

    // --- tier.enabled = false: "every village rolls village" ---

    @Test
    void aDisabledTierConfigAlwaysRollsVillageIgnoringTheDraw() {
        ConfigModel.Tier disabled = tierConfig(
                false,
                new ConfigModel.TierWeights(30, 45, 20, 5),
                new ConfigModel.TierBudget(3, 6, 8, 10),
                new ConfigModel.TierBudget(80, 96, 128, 128),
                3.0);

        for (double draw : new double[] {0.0, 0.1, 0.5, 0.9, 0.999}) {
            TierAssignment assignment = TierRoller.roll(disabled, draw);
            assertEquals(Tier.VILLAGE, assignment.tier());
            assertEquals(6, assignment.jigsawDepth());
            assertEquals(96, assignment.maxDistance());
        }
    }

    /** Finds a draw in {@code [0, 1)} that {@link TierRoller#pickTier} maps to {@code target}
     *  under {@code weights}, by linear-scanning a fine grid -- avoids hand-computing cumulative
     *  weight boundaries per test case above. */
    private static double drawFor(Tier target, ConfigModel.TierWeights weights) {
        for (int i = 0; i < 10_000; i++) {
            double draw = i / 10_000.0;
            if (TierRoller.pickTier(weights, draw) == target) {
                return draw;
            }
        }
        throw new IllegalStateException("no draw in [0,1) maps to " + target + " under " + weights);
    }
}
