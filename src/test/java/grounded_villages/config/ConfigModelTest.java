package grounded_villages.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * `decisions/DEC-008-config-file.md`: "a record whose compact constructor clamps out-of-range
 * values ... rather than throwing". This is the second line of defence behind
 * {@link ConfigCodecTest}'s own clamp-and-warn coverage: even a {@link ConfigModel} built
 * directly (by a test, or any future caller) rather than parsed from disk can never itself be
 * invalid.
 */
final class ConfigModelTest {

    @Test
    void aNullNestedRecordFallsBackToItsShippedDefaultRatherThanThrowing() {
        ConfigModel config = new ConfigModel(1, null, null, null, null);

        assertEquals(ConfigDefaults.defaults(), config);
    }

    @Test
    void anOutOfRangeSiteFieldIsClampedEvenWhenConstructedDirectly() {
        ConfigModel.Site site = new ConfigModel.Site(true, -50, 4.0, -5, 0, -1);

        assertEquals(0, site.maxHeightSpread());
        assertEquals(1.0, site.maxWaterFraction());
        assertEquals(0, site.searchRadius());
        assertEquals(1, site.searchStep());
        assertEquals(0, site.searchAttempts());
    }

    @Test
    void anOutOfRangeTierBudgetIsClampedByTheOwningTierRecord() {
        ConfigModel.Tier tier = new ConfigModel.Tier(
                true,
                new ConfigModel.TierWeights(-1, -1, -1, -1),
                new ConfigModel.TierBudget(-1, 999, -1, 999),
                new ConfigModel.TierBudget(-1, 999, -1, 999),
                -1,
                0);

        assertEquals(0, tier.weights().hamlet());
        assertEquals(0, tier.jigsawDepth().hamlet());
        assertEquals(32, tier.jigsawDepth().village());
        assertEquals(0, tier.maxDistance().hamlet());
        assertEquals(128, tier.maxDistance().village());
        assertEquals(0, tier.hamletMinimumPieces());
        assertEquals(0.1, tier.performanceCapMultiplier());
    }
}
