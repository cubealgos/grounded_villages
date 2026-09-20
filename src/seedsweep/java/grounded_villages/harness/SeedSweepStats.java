package grounded_villages.harness;

import java.util.List;

/**
 * Pure statistics used by the seed-sweep harness (GV-10): the 90th-minus-10th-percentile height
 * spread and the water-fraction calculation defined in {@code docs/spec/domains/site.md} §3. No
 * Minecraft imports -- testable without the game classpath
 * ({@code docs/spec/operations/testing.md} "Unit" row), even though this file happens to live in
 * the same node-conditional source set as the harness's Minecraft-facing code
 * ({@code src/seedsweep/java}, 26.2-fabric only, see {@code build.fabric.gradle.kts}).
 */
public final class SeedSweepStats {
    private SeedSweepStats() {
    }

    /**
     * Linear-interpolation percentile (the same "nearest-rank with interpolation" method numpy's
     * default {@code percentile} uses). {@code values} need not be sorted; a sorted copy is taken
     * internally.
     *
     * @throws IllegalArgumentException if {@code values} is empty or {@code p} is outside [0, 100]
     */
    public static double percentile(List<Integer> values, double p) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("percentile of an empty sample is undefined");
        }
        if (p < 0.0 || p > 100.0) {
            throw new IllegalArgumentException("percentile must be within [0, 100], got " + p);
        }

        int[] sorted = values.stream().mapToInt(Integer::intValue).sorted().toArray();
        if (sorted.length == 1) {
            return sorted[0];
        }

        double rank = (p / 100.0) * (sorted.length - 1);
        int lowIndex = (int) Math.floor(rank);
        int highIndex = (int) Math.ceil(rank);
        if (lowIndex == highIndex) {
            return sorted[lowIndex];
        }

        double fraction = rank - lowIndex;
        return sorted[lowIndex] + (sorted[highIndex] - sorted[lowIndex]) * fraction;
    }

    /**
     * The {@code SITE} domain's own metric ({@code docs/spec/domains/site.md} §3): the
     * 90th-minus-10th-percentile of sampled ground heights (true ground, {@code OCEAN_FLOOR_WG}).
     */
    public static double heightSpread(List<Integer> groundHeights) {
        return percentile(groundHeights, 90.0) - percentile(groundHeights, 10.0);
    }

    /**
     * Fraction of sampled columns identified as water, per {@code docs/spec/domains/site.md} §3.
     *
     * @throws IllegalArgumentException if {@code totalSamples} is not positive, or
     *                                   {@code waterSamples} is outside [0, totalSamples]
     */
    public static double waterFraction(int waterSamples, int totalSamples) {
        if (totalSamples <= 0) {
            throw new IllegalArgumentException("totalSamples must be positive, got " + totalSamples);
        }
        if (waterSamples < 0 || waterSamples > totalSamples) {
            throw new IllegalArgumentException(
                "waterSamples (" + waterSamples + ") out of [0, " + totalSamples + "]");
        }
        return (double) waterSamples / (double) totalSamples;
    }
}
