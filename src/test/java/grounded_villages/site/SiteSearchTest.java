package grounded_villages.site;

import grounded_villages.config.ConfigModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `docs/spec/operations/testing.md` "Unit": the bounded search's determinism and fallback (ticket
 * build item 4) -- `SITE-REQ-001` through {@code 006}. Every test below uses {@code
 * footprintRadius = 0} so {@link SiteScorer} samples exactly one column per candidate (its own
 * centre), collapsing height spread to a constant 0 and water fraction to a clean binary
 * wet/dry -- this isolates the search's own offset/ordering/tie-break logic from the scorer's own
 * grid behaviour, already covered by {@link SiteScorerTest}.
 */
final class SiteSearchTest {

    private static final int ORIGIN_X = 0;
    private static final int ORIGIN_Z = 0;

    private static ConfigModel.Site siteConfig(boolean enabled, int searchStep, int searchRadius, int searchAttempts) {
        return new ConfigModel.Site(enabled, 12, 0.0, searchRadius, searchStep, searchAttempts);
    }

    /** {@code groundHeight} always 64; {@code surfaceHeight} 70 (wet) at {@code wetColumns}, 64 (dry) elsewhere. */
    private static FakeHeightSampler samplerWithWetColumns(SiteCoordinate... wetColumns) {
        Set<SiteCoordinate> wet = Set.of(wetColumns);
        return new FakeHeightSampler(
                (x, z) -> 64,
                (x, z) -> wet.contains(new SiteCoordinate(x, z)) ? 70 : 64);
    }

    @Test
    void aVanillaStartThatAlreadyQualifiesIsKept() {
        FakeHeightSampler sampler = samplerWithWetColumns(); // nothing is wet
        ConfigModel.Site config = siteConfig(true, 16, 48, 8);

        SiteDecision decision = SiteSearch.evaluateStart(sampler, ORIGIN_X, ORIGIN_Z, 0, config);

        assertInstanceOf(SiteDecision.Keep.class, decision);
    }

    @Test
    void disabledSiteSelectionAlwaysKeepsWithoutSamplingAnything() {
        FakeHeightSampler sampler = new FakeHeightSampler(
                (x, z) -> {
                    throw new AssertionError("site.enabled=false must skip sampling entirely (SITE-REQ-005)");
                },
                (x, z) -> {
                    throw new AssertionError("site.enabled=false must skip sampling entirely (SITE-REQ-005)");
                });
        ConfigModel.Site config = siteConfig(false, 16, 48, 8);

        SiteDecision decision = SiteSearch.evaluateStart(sampler, ORIGIN_X, ORIGIN_Z, 80, config);

        assertInstanceOf(SiteDecision.Keep.class, decision);
    }

    @Test
    void aFailingVanillaStartShiftsToTheFirstQualifyingCandidateInSpiralOrder() {
        // Wet: the vanilla start itself, and the first spiral direction (N, (0, -16)). Dry
        // (default): every other candidate, including the second direction (NE, (16, -16)) --
        // which must win over the later, equally-dry E/SE/S/SW/W/NW candidates because it was
        // found first (an explicit tie-break determinism proof, not just "some qualifier found").
        FakeHeightSampler sampler = samplerWithWetColumns(new SiteCoordinate(0, 0), new SiteCoordinate(0, -16));
        ConfigModel.Site config = siteConfig(true, 16, 48, 8);

        SiteDecision decision = SiteSearch.evaluateStart(sampler, ORIGIN_X, ORIGIN_Z, 0, config);

        SiteDecision.Shift shift = assertInstanceOf(SiteDecision.Shift.class, decision);
        assertEquals(16, shift.to().x());
        assertEquals(-16, shift.to().z());
    }

    @Test
    void aSearchThatExhaustsEveryAttemptFallsBackToVanilla() {
        // Every column within reach reads wet, vanilla start included.
        FakeHeightSampler sampler = new FakeHeightSampler((x, z) -> 64, (x, z) -> 70);
        ConfigModel.Site config = siteConfig(true, 16, 48, 8);

        SiteDecision decision = SiteSearch.evaluateStart(sampler, ORIGIN_X, ORIGIN_Z, 0, config);

        assertInstanceOf(SiteDecision.Vanilla.class, decision);
    }

    @Test
    void searchAlternativeNeverReEvaluatesTheOriginItself() {
        // SITE-REQ-006: the move-retry entry point reuses this search on a site that already
        // passed its own check -- it must not re-score the origin, only the offsets around it.
        // Marking the origin wet here must have no bearing on the result.
        FakeHeightSampler sampler = samplerWithWetColumns(new SiteCoordinate(ORIGIN_X, ORIGIN_Z));
        ConfigModel.Site config = siteConfig(true, 16, 48, 1);

        Optional<SiteCoordinate> result = SiteSearch.searchAlternative(sampler, ORIGIN_X, ORIGIN_Z, 0, config);

        assertTrue(result.isPresent());
        assertEquals(new SiteCoordinate(0, -16), result.get());
    }

    @Test
    void spiralOffsetsWalksTheFixedCompassOrderPerRing() {
        List<SiteCoordinate> offsets = SiteSearch.spiralOffsets(ORIGIN_X, ORIGIN_Z, 16, 48, 8);

        assertEquals(
                List.of(
                        new SiteCoordinate(0, -16),   // N
                        new SiteCoordinate(16, -16),  // NE
                        new SiteCoordinate(16, 0),    // E
                        new SiteCoordinate(16, 16),   // SE
                        new SiteCoordinate(0, 16),    // S
                        new SiteCoordinate(-16, 16),  // SW
                        new SiteCoordinate(-16, 0),   // W
                        new SiteCoordinate(-16, -16)  // NW
                ),
                offsets);
    }

    @Test
    void spiralOffsetsStopsAtTheAttemptsCapBeforeExhaustingTheRadius() {
        List<SiteCoordinate> offsets = SiteSearch.spiralOffsets(ORIGIN_X, ORIGIN_Z, 16, 48, 2);

        assertEquals(List.of(new SiteCoordinate(0, -16), new SiteCoordinate(16, -16)), offsets);
    }

    @Test
    void spiralOffsetsNeverExceedsTheConfiguredRadius() {
        // radius 16 < step 32: even ring 1's axis distance (32) already exceeds it, so the
        // search must come back empty rather than searching unboundedly (SITE-FAIL-002's
        // partner property: the search itself never overruns its own configured bound).
        List<SiteCoordinate> offsets = SiteSearch.spiralOffsets(ORIGIN_X, ORIGIN_Z, 32, 16, 8);

        assertTrue(offsets.isEmpty());
    }

    @Test
    void spiralOffsetsIsDeterministicAcrossRepeatedCalls() {
        List<SiteCoordinate> first = SiteSearch.spiralOffsets(ORIGIN_X, ORIGIN_Z, 16, 48, 8);
        List<SiteCoordinate> second = SiteSearch.spiralOffsets(ORIGIN_X, ORIGIN_Z, 16, 48, 8);

        assertEquals(first, second);
    }

    @Test
    void aSecondRingIsReachedOnlyWhenTheFirstRingIsExhaustedByTheAttemptsCap() {
        // step 8, radius 24, attempts 16: ring 1 (distance 8) contributes all 8 directions, ring
        // 2 (distance 16) contributes its own 8 -- 16 total, exactly the attempts cap, so the
        // search returns before ever reaching ring 3.
        List<SiteCoordinate> offsets = SiteSearch.spiralOffsets(ORIGIN_X, ORIGIN_Z, 8, 24, 16);

        assertEquals(16, offsets.size());
        assertEquals(new SiteCoordinate(0, -8), offsets.get(0));
        assertEquals(new SiteCoordinate(0, -16), offsets.get(8)); // first offset of ring 2
    }

    @Test
    void notEnoughOffsetsGeneratedMeansTheSearchHonestlyReturnsFewerThanAttempts() {
        // radius 20, step 16: ring 1's axis distance (16) fits but its diagonals (16*sqrt2 =
        // 22.6) don't, so only the 4 axis directions (N/E/S/W) qualify; ring 2's own axis
        // distance (32) exceeds the radius outright, so the search stops there. The search must
        // honestly return 4 rather than fabricating more candidates to hit `attempts`.
        List<SiteCoordinate> offsets = SiteSearch.spiralOffsets(ORIGIN_X, ORIGIN_Z, 16, 20, 32);

        assertEquals(4, offsets.size());
    }

    @Test
    void aZeroOrNegativeStepRadiusOrAttemptsProducesNoOffsets() {
        assertTrue(SiteSearch.spiralOffsets(ORIGIN_X, ORIGIN_Z, 0, 48, 8).isEmpty());
        assertTrue(SiteSearch.spiralOffsets(ORIGIN_X, ORIGIN_Z, 16, 0, 8).isEmpty());
        assertTrue(SiteSearch.spiralOffsets(ORIGIN_X, ORIGIN_Z, 16, 48, 0).isEmpty());
    }
}
