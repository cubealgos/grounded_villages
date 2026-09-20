package grounded_villages.piece;

import grounded_villages.site.HeightSampler;

import java.util.ArrayList;
import java.util.List;

/**
 * `docs/spec/domains/pieces.md` §3/§5, ticket GV-7 build item 1: evaluates one candidate piece's
 * footprint against `PIECE-REQ-001`-{@code 004} -- water in footprint (a flat any-water rejection,
 * not a tunable fraction) and height deviation from the village's own start height
 * (`piece.max_height_deviation`). Streets and buildings share this exact code path with no
 * exemption (`PIECE-REQ-004`): {@link #evaluate} takes only a footprint and two heightmap
 * queries, never the piece's own kind.
 *
 * <p>References no Minecraft type (`04-architecture.md` {@code ARCH-DEC-002}): terrain comes in
 * through {@link HeightSampler} -- {@code grounded_villages.site}'s own pure abstraction, reused
 * rather than duplicated (see this package's own {@code package-info.java}) -- and the result is
 * this package's own {@link PieceVerdict}. {@link PieceRejectionHook} is the one Minecraft-typed
 * adapter that wraps a real {@code BoundingBox}/{@code TerrainSampler} for this.
 *
 * <p><b>Sampling</b> (`domains/pieces.md` §3, ticket "Approach": "5 points minimum"): the
 * footprint's four corners and centre always; for a footprint wider than {@link
 * #WIDE_FOOTPRINT_THRESHOLD} blocks on an axis, that axis' two edge midpoints are added too (up to
 * 9 points total) -- "more for a larger footprint", proportionate to the per-piece precedent
 * without re-implementing {@code SiteScorer}'s own whole-village grid at piece scale.
 *
 * <p><b>Order</b>: every sampled point is checked for water first, then for height deviation --
 * a footprint with both problems is reported as a water rejection (`PIECE-REQ-002`'s own listing
 * order), not because water matters more, only because that is a stable, arbitrary tie-break
 * (either criterion alone is sufficient to reject; the accept/reject outcome does not depend on
 * check order, only which single reason gets logged).
 */
public final class PieceGate {

    /** Footprint axis length (blocks) above which that axis' own edge midpoints are also sampled. */
    static final int WIDE_FOOTPRINT_THRESHOLD = 16;

    private PieceGate() {
    }

    /**
     * @param startHeight the village's own start height (`03-glossary.md`: "the one ground height
     *     a village's site is evaluated and accepted at"), measured the same way {@code site}
     *     measures ground height ({@code OCEAN_FLOOR_WG}) so the two are directly comparable
     * @param maxHeightDeviation {@code piece.max_height_deviation}, blocks
     */
    public static PieceVerdict evaluate(HeightSampler sampler, PieceFootprint footprint, int startHeight, int maxHeightDeviation) {
        List<int[]> points = samplePoints(footprint);

        for (int[] point : points) {
            int ground = sampler.groundHeight(point[0], point[1]);
            int surface = sampler.surfaceHeight(point[0], point[1]);
            if (surface > ground) {
                return PieceVerdict.rejectWater();
            }
        }
        for (int[] point : points) {
            int ground = sampler.groundHeight(point[0], point[1]);
            if (Math.abs(ground - startHeight) > maxHeightDeviation) {
                return PieceVerdict.rejectHeight();
            }
        }
        return PieceVerdict.accept();
    }

    /** Package-visible for {@code PieceGateTest}'s own sampling-shape assertions. */
    static List<int[]> samplePoints(PieceFootprint footprint) {
        int minX = footprint.minX();
        int maxX = footprint.maxX();
        int minZ = footprint.minZ();
        int maxZ = footprint.maxZ();
        int midX = (minX + maxX) / 2;
        int midZ = (minZ + maxZ) / 2;

        List<int[]> points = new ArrayList<>(9);
        points.add(new int[] {minX, minZ});
        points.add(new int[] {minX, maxZ});
        points.add(new int[] {maxX, minZ});
        points.add(new int[] {maxX, maxZ});
        points.add(new int[] {midX, midZ});

        if ((maxX - minX) > WIDE_FOOTPRINT_THRESHOLD) {
            points.add(new int[] {midX, minZ});
            points.add(new int[] {midX, maxZ});
        }
        if ((maxZ - minZ) > WIDE_FOOTPRINT_THRESHOLD) {
            points.add(new int[] {minX, midZ});
            points.add(new int[] {maxX, midZ});
        }
        return points;
    }
}
