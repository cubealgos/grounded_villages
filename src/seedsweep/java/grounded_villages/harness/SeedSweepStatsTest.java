package grounded_villages.harness;

import java.util.List;

/**
 * Hand-rolled unit tests for {@link SeedSweepStats} on synthetic samples
 * ({@code docs/spec/operations/testing.md} "Unit" row, GV-10 acceptance criterion "unit-test the
 * statistics"). No JUnit (or other test framework) dependency was added for this ticket -- GV-10's
 * "no new dependencies" rule -- so this is a plain {@code main}-based runner, wired into the build
 * as its own task ({@code harnessStatsTest}, 26.2-fabric node only), asserting with a small local
 * helper and printing a pass/fail count that {@code just sweep}/{@code just check} can read
 * verbatim from the log.
 */
public final class SeedSweepStatsTest {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testPercentileSingleValue();
        testPercentileEvenCount();
        testPercentileOddCount();
        testPercentileAllSame();
        testPercentileUnsorted();
        testHeightSpreadFlatTerrain();
        testHeightSpreadKnownSpread();
        testWaterFractionZero();
        testWaterFractionAll();
        testWaterFractionPartial();
        testPercentileRejectsEmpty();
        testPercentileRejectsOutOfRangeP();
        testWaterFractionRejectsNonPositiveTotal();
        testWaterFractionRejectsOutOfRangeCount();

        System.out.println("SeedSweepStatsTest: " + passed + "/" + (passed + failed) + " passed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void check(boolean condition, String message) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.err.println("FAILED: " + message);
        }
    }

    private static void checkThrows(Runnable action, String message) {
        try {
            action.run();
            failed++;
            System.err.println("FAILED (expected IllegalArgumentException): " + message);
        } catch (IllegalArgumentException expected) {
            passed++;
        }
    }

    private static void testPercentileSingleValue() {
        check(SeedSweepStats.percentile(List.of(42), 10.0) == 42.0, "single value, p10");
        check(SeedSweepStats.percentile(List.of(42), 90.0) == 42.0, "single value, p90");
    }

    private static void testPercentileEvenCount() {
        List<Integer> values = List.of(1, 2, 3, 4);
        check(SeedSweepStats.percentile(values, 0.0) == 1.0, "even count, p0");
        check(SeedSweepStats.percentile(values, 100.0) == 4.0, "even count, p100");
        check(SeedSweepStats.percentile(values, 50.0) == 2.5, "even count, p50 (median)");
    }

    private static void testPercentileOddCount() {
        List<Integer> values = List.of(1, 2, 3, 4, 5);
        check(SeedSweepStats.percentile(values, 50.0) == 3.0, "odd count, p50 (median)");
    }

    private static void testPercentileAllSame() {
        List<Integer> values = List.of(64, 64, 64, 64);
        check(SeedSweepStats.percentile(values, 10.0) == 64.0, "constant sample, p10");
        check(SeedSweepStats.percentile(values, 90.0) == 64.0, "constant sample, p90");
        check(SeedSweepStats.heightSpread(values) == 0.0, "constant sample, height spread is 0");
    }

    private static void testPercentileUnsorted() {
        List<Integer> unsorted = List.of(5, 1, 4, 2, 3);
        check(SeedSweepStats.percentile(unsorted, 50.0) == 3.0, "unsorted input is sorted internally");
    }

    private static void testHeightSpreadFlatTerrain() {
        List<Integer> flat = List.of(70, 70, 70, 70, 70, 70, 70, 70, 70, 70);
        check(SeedSweepStats.heightSpread(flat) == 0.0, "flat terrain has zero spread");
    }

    private static void testHeightSpreadKnownSpread() {
        // 0..100 inclusive by 10s: p90 - p10 is 80 by construction (linear interpolation on an
        // evenly spaced ramp lands exactly on sample values).
        List<Integer> ramp = List.of(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100);
        check(SeedSweepStats.heightSpread(ramp) == 80.0, "known linear ramp, spread is p90 - p10");
    }

    private static void testWaterFractionZero() {
        check(SeedSweepStats.waterFraction(0, 100) == 0.0, "no water samples");
    }

    private static void testWaterFractionAll() {
        check(SeedSweepStats.waterFraction(100, 100) == 1.0, "all water samples");
    }

    private static void testWaterFractionPartial() {
        check(SeedSweepStats.waterFraction(5, 20) == 0.25, "partial water samples");
    }

    private static void testPercentileRejectsEmpty() {
        checkThrows(() -> SeedSweepStats.percentile(List.of(), 50.0), "empty sample rejected");
    }

    private static void testPercentileRejectsOutOfRangeP() {
        checkThrows(() -> SeedSweepStats.percentile(List.of(1, 2), -1.0), "negative percentile rejected");
        checkThrows(() -> SeedSweepStats.percentile(List.of(1, 2), 101.0), "percentile over 100 rejected");
    }

    private static void testWaterFractionRejectsNonPositiveTotal() {
        checkThrows(() -> SeedSweepStats.waterFraction(0, 0), "zero total samples rejected");
    }

    private static void testWaterFractionRejectsOutOfRangeCount() {
        checkThrows(() -> SeedSweepStats.waterFraction(5, 3), "water count over total rejected");
    }
}
