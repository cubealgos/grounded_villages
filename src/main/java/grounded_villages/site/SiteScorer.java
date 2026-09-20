package grounded_villages.site;

import java.util.Arrays;

/**
 * Scores one candidate village site's whole footprint (`docs/spec/domains/site.md` §3, ticket
 * GV-6 build item 1): a square grid centred on the candidate, {@link #SAMPLE_STEP}-block spacing,
 * radius the caller's own choice -- the tier's {@code max_distance} once tiers exist (GV-8), or
 * the jigsaw structure's own {@code max_distance_from_center} otherwise (what {@link
 * grounded_villages.mixin.village} actually has in scope today, per the ticket's own build item
 * 1: "the tier's max distance, or {@code max_distance} from the jigsaw structure, as the radius").
 *
 * <p>References no Minecraft type (`04-architecture.md` {@code ARCH-DEC-002}): terrain comes in
 * through {@link HeightSampler}, two plain-int queries per column -- the real implementation
 * ({@code grounded_villages.site.SiteStartHook}) wraps {@link
 * grounded_villages.hook.TerrainSampler}; tests wrap a synthetic height field, no live {@code
 * ChunkGenerator} needed, and no Minecraft classpath needed to compile them either (this mod's
 * forge/neoforge nodes do not put Minecraft on {@code src/test}'s own compile classpath at all --
 * `docs/spec/operations/testing.md` "Unit": "no Minecraft imports").
 *
 * <p><b>Water-fraction metric: the cheaper of the two the ticket names.</b> `domains/site.md` §3
 * allows either "{@code WORLD_SURFACE_WG} height exceeds the ocean floor height" or "the base
 * column's block at the surface is water"; this class always uses the height comparison, never
 * a direct block-state read ({@link HeightSampler} does not even expose one). Reasoning, from
 * {@code
 * village-jigsaw-placement-1-20-1-to-26-2.md} §A: {@code getBaseHeight} walks a noise column
 * top-down against a stop predicate and returns on the first match (an early-exit walk, typically
 * short); {@code getBaseColumn} takes {@code null} as its predicate and materialises the *entire*
 * column as a {@code NoiseColumn} (a full walk, no early exit, from the world's build-height
 * ceiling to its floor). Two early-exit {@code getBaseHeight} calls -- one at {@code
 * OCEAN_FLOOR_WG} (needed anyway, for height spread), one at {@code WORLD_SURFACE_WG} -- is
 * strictly cheaper than height spread's one {@code getBaseHeight} call plus a full-column {@code
 * getBaseColumn} walk for the water check. A surface height above the true ground height can only
 * mean something sits between them that counts as "surface" for {@code WORLD_SURFACE_WG} but not
 * for {@code OCEAN_FLOOR_WG} -- water is the only such material in practice
 * (`village-jigsaw-placement-1-20-1-to-26-2.md` §C's own {@code NOT_AIR} vs. {@code
 * MATERIAL_MOTION_BLOCKING} predicate pair); a snow layer or similar block that also blocks
 * motion would be caught by {@code OCEAN_FLOOR_WG} on both sides and not flagged here, an accepted
 * imprecision this domain does not need to resolve (`SITE-FAIL-003`: "proportionate", not exact).
 *
 * <p><b>Column cap ({@code SITE-FAIL-003}):</b> a naive step-8 grid over a 128-block radius (the
 * engine's own hard ceiling, `domains/tiers.md` "Spacing") samples 33x33 = 1089 columns; {@link
 * #MAX_SAMPLE_COLUMNS} bounds that by coarsening the step (never below {@link #SAMPLE_STEP}) so
 * the grid never exceeds roughly {@code sqrt(MAX_SAMPLE_COLUMNS)} points per axis, keeping the
 * per-site cost config-driven and bounded regardless of how large a future tier's radius grows.
 */
public final class SiteScorer {

    /** Grid spacing, in blocks, at or below the column cap (ticket build item 1). */
    public static final int SAMPLE_STEP = 8;

    /**
     * Upper bound on sampled columns per site -- {@code SITE-FAIL-003}'s "bounded by the same
     * config-driven sample count", applied here as a grid-density cap rather than a config key,
     * since it is an implementation cost bound, not domain behaviour (`contracts/public-surface.md`:
     * "the exact sampling grid ... [is] internal"). {@link SiteSearch#evaluateStart} scores up to
     * 1 + {@code search_attempts} sites per candidate village start (the proposed default 8, so
     * up to 9), and this cost is paid once per candidate cell a broad structure search touches,
     * not once per village -- confirmed live during this ticket's own harness sweep against the
     * seed-sweep harness's {@code findNearestMapStructure} call (`SeedSweepRunner`, vanilla's own
     * {@code /locate}-radius search, 100 chunks), which forces {@code STRUCTURE_STARTS}
     * generation -- and therefore this hook -- for every structure-set cell it scans on the way
     * to the nearest one, all inside one blocking call the server watchdog treats as a single
     * tick.
     *
     * <p><b>Measured, in order, tuning this cap down from a first, naive guess:</b> {@code 400}
     * pushed seed 4 to 56.8s of extra cost (barely inside the 60s {@code
     * server.properties max-tick-time} watchdog, no real margin); {@code 200} still left seed 4
     * at 56.8s for the same reason (8 candidate cells scanned, each paying the full cost); {@code
     * 64} brought seed 4 down to a safe ~11.5s of extra cost (8 cells) -- but seed 1 then crashed
     * the watchdog outright at exactly this cap: over 40 candidate cells scanned inside one
     * blocking call, at roughly 1.4s each, before the 60s ceiling hit. The number of cells a
     * search like this touches is entirely a property of the seed's own terrain (how many nearby
     * structure-set cells satisfy vanilla's own biome/placement eligibility before the first
     * "valid" one), not something this class controls -- so no fixed cap fully eliminates the
     * risk for an arbitrarily unlucky seed. {@code 25} (a 5x5 grid) is the value shipped: at the
     * same ~1.4ms/query this ticket measured, that is roughly 25 x 9 x 2 x 1.25ms ~= 560ms per
     * candidate cell, low enough that even a seed touching 80-100 cells (double the worst one
     * measured) stays under the watchdog with real margin -- while still well above the {@code
     * 5-9} points per piece vanilla's own per-piece precedent uses
     * (`village-jigsaw-placement-1-20-1-to-26-2.md` §C), proportionate to a whole-village
     * footprint rather than one piece. Worth revisiting if a future ticket needs finer whole-site
     * resolution: the real fix for the underlying multiplier is making the search itself cheaper
     * to fail fast (e.g. checking water fraction before height spread and returning early once
     * either threshold is already blown), not shipping a still-larger cap -- flagged here, not
     * built, since it is out of this ticket's own scope.
     */
    public static final int MAX_SAMPLE_COLUMNS = 25;

    private SiteScorer() {
    }

    /**
     * Scores the {@code (2*radius+1) x (2*radius+1)}-block square centred on {@code (centerX,
     * centerZ)}, at the grid step {@link #effectiveStep(int)} derives from {@code radius}.
     *
     * @throws IllegalArgumentException if {@code radius} is negative
     */
    public static SiteScore score(HeightSampler sampler, int centerX, int centerZ, int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("radius must be >= 0, got " + radius);
        }
        int step = effectiveStep(radius);

        int[] groundHeights = new int[axisPoints(radius, step) * axisPoints(radius, step)];
        int sampleCount = 0;
        int waterSamples = 0;
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                int x = centerX + dx;
                int z = centerZ + dz;
                int groundHeight = sampler.groundHeight(x, z);
                int surfaceHeight = sampler.surfaceHeight(x, z);
                groundHeights[sampleCount] = groundHeight;
                sampleCount++;
                if (surfaceHeight > groundHeight) {
                    waterSamples++;
                }
            }
        }

        double heightSpread = heightSpread(groundHeights, sampleCount);
        double waterFraction = sampleCount == 0 ? 0.0 : (double) waterSamples / sampleCount;
        return new SiteScore(heightSpread, waterFraction, sampleCount);
    }

    /**
     * The grid step actually used for {@code radius}: {@link #SAMPLE_STEP} unless that would put
     * more than {@link #MAX_SAMPLE_COLUMNS} columns in the grid, in which case the step is
     * coarsened (never refined below {@link #SAMPLE_STEP}) just enough to fit the cap.
     */
    static int effectiveStep(int radius) {
        if (radius == 0) {
            return SAMPLE_STEP;
        }
        int naiveAxisPoints = axisPoints(radius, SAMPLE_STEP);
        if ((long) naiveAxisPoints * naiveAxisPoints <= MAX_SAMPLE_COLUMNS) {
            return SAMPLE_STEP;
        }
        int cappedAxisPoints = Math.max(3, (int) Math.sqrt(MAX_SAMPLE_COLUMNS));
        // Ceiling division (`Math.ceilDiv` needs Java 18+; the 1.20.1 node builds on 17,
        // 04-architecture.md's toolchain table) -- floor division here would round the step back
        // down to a value that still exceeds the cap at the boundary (e.g. radius 80 rounds to
        // exactly SAMPLE_STEP again with floor division, sampling 21x21 = 441 > 400).
        int divisor = cappedAxisPoints - 1;
        int coarsened = (2 * radius + divisor - 1) / divisor;
        return Math.max(SAMPLE_STEP, coarsened);
    }

    private static int axisPoints(int radius, int step) {
        return (2 * radius) / step + 1;
    }

    /** The 90th-minus-10th-percentile of {@code values[0..count)} (`domains/site.md` §3). */
    private static double heightSpread(int[] values, int count) {
        if (count <= 1) {
            return 0.0;
        }
        int[] sorted = Arrays.copyOf(values, count);
        Arrays.sort(sorted);
        return percentile(sorted, 90.0) - percentile(sorted, 10.0);
    }

    /** Linear-interpolation percentile over an already-sorted array (numpy's default method). */
    private static double percentile(int[] sorted, double p) {
        double rank = (p / 100.0) * (sorted.length - 1);
        int lowIndex = (int) Math.floor(rank);
        int highIndex = (int) Math.ceil(rank);
        if (lowIndex == highIndex) {
            return sorted[lowIndex];
        }
        double fraction = rank - lowIndex;
        return sorted[lowIndex] + (sorted[highIndex] - sorted[lowIndex]) * fraction;
    }
}
