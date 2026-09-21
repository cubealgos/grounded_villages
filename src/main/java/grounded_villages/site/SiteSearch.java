package grounded_villages.site;

import grounded_villages.config.ConfigModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * `domains/site.md` §3/§5: evaluate vanilla's own candidate start, and -- if it fails -- run a
 * bounded, deterministic search for a qualifying alternative (`SITE-REQ-001`-{@code 004}).
 * {@link #evaluateStart} is what {@code grounded_villages.site.SiteStartHook} (the Minecraft-typed
 * adapter a loader entrypoint registers into {@code
 * grounded_villages.hook.HookRegistry#setStartHook}) calls for a fresh village candidate;
 * {@link #searchAlternative} is the reusable bounded-search primitive underneath it, also
 * {@code SITE-REQ-006}'s own move-retry entry point -- `decisions/DEC-010-shrink-move-vanilla.md`
 * step 2 ("move") re-runs this same search when too few pieces survive per-piece rejection, once
 * `domains/pieces.md`'s own logic exists to call it.
 *
 * <p>References no Minecraft type (`04-architecture.md` {@code ARCH-DEC-002}): positions in and
 * out are plain {@link SiteCoordinate}s, terrain comes in through {@link HeightSampler}, and the
 * decision out is this package's own {@link SiteDecision} -- {@code SiteStartHook} is the one
 * place that converts to/from {@code BlockPos}/{@code StartDecision} for the live mixin.
 *
 * <p>No randomness anywhere in this class (`SITE-REQ-003`: "Deterministic ... no random"): the
 * search walks a fixed spiral of offsets in a fixed compass order, so the same input (sampler,
 * origin, config) always produces the same decision.
 */
public final class SiteSearch {

    /**
     * The fixed compass order the spiral walks each ring, clockwise from north: N, NE, E, SE, S,
     * SW, W, NW. Arbitrary but fixed -- determinism only requires *a* stable order, not this
     * particular one.
     */
    private static final int[][] DIRECTIONS = {
        {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}
    };

    private SiteSearch() {
    }

    /**
     * {@code SITE-REQ-001}/{@code 002}/{@code 004}: score the vanilla start at {@code (startX,
     * startZ)}; keep it if it qualifies, otherwise search for a qualifying alternative within
     * {@code config}'s bounds and shift to the best-scoring one, or fall back to {@link
     * SiteDecision#vanilla()} if the search exhausts its attempts (`SITE-FAIL-001`). {@code
     * SITE-REQ-005}: {@code config.enabled()} false always keeps, without sampling anything.
     *
     * @param footprintRadius the whole-village radius to sample (ticket build item 1: "the
     *                        tier's max distance, or max_distance from the jigsaw structure"),
     *                        forwarded to {@link SiteScorer#score} unchanged
     */
    public static SiteDecision evaluateStart(
            HeightSampler sampler, int startX, int startZ, int footprintRadius, ConfigModel.Site config) {
        if (!config.enabled()) {
            return SiteDecision.keep();
        }

        SiteScore vanillaScore = SiteScorer.score(sampler, startX, startZ, footprintRadius);
        if (vanillaScore.qualifies(config.maxHeightSpread(), config.maxWaterFraction())) {
            return SiteDecision.keep();
        }

        Optional<SiteCoordinate> shifted = searchAlternative(sampler, startX, startZ, footprintRadius, config);
        return shifted.<SiteDecision>map(SiteDecision::shift).orElseGet(SiteDecision::vanilla);
    }

    /**
     * {@code SITE-REQ-003}/{@code 006}: the bounded search itself, independent of whether {@code
     * (originX, originZ)} was ever scored -- {@code SITE-REQ-006} calls this directly on a site
     * that already passed its own site-level check but produced too few surviving pieces, with no
     * need to re-evaluate the origin first. Tries up to {@code config.searchAttempts()} offsets,
     * in a fixed spiral of {@code config.searchStep()}-block rings out to {@code
     * config.searchRadius()}, all inside the same structure placement cell (`SITE-REQ-003`:
     * comfortably under the structure set's own 128-block minimum cell separation,
     * `village-jigsaw-placement-1-20-1-to-26-2.md` §D), and returns the best-scoring qualifying
     * one.
     *
     * <p><b>"Best-scoring"</b>: not spec-defined beyond "qualifying" (`domains/site.md` §5 does
     * not rank qualifying candidates against each other), so this method picks by ascending
     * height spread first, ascending water fraction as the tiebreak -- height spread is `SITE`'s
     * primary metric (`domains/site.md` §2 "Unwanted": height spread is named first in every
     * dimension), water fraction narrows ties between two similarly flat candidates. When every
     * examined field still ties (a common case at small {@code footprintRadius}, see {@code
     * SiteSearchTest}), the first qualifying candidate found in spiral order wins -- a second,
     * deliberate determinism guarantee on top of the fixed offset order itself.
     *
     * <p><b>Chunk-boundary safety</b>: every offset stays inside {@code config.searchRadius()},
     * default 48 blocks (3 chunks) -- comfortably inside the structure set's own 128-block
     * minimum separation between village cells (`village-jigsaw-placement-1-20-1-to-26-2.md` §D:
     * {@code spacing: 34} chunks, {@code separation: 8} chunks = 128 blocks), so a shifted start
     * can never cross into a neighbouring village's own cell. Whether the shifted start's *chunk*
     * (as opposed to its cell) matters for {@code /locate} or generation ordering was checked
     * against the known architecture, not a fresh {@code javap} read in this ticket ({@code
     * StructureStart}'s registration and chunk references are both driven by {@code
     * Structure.GenerationContext.chunkPos()}, fixed by the structure set's own placement
     * *before* {@code JigsawStructure.findGenerationPoint}/{@code JigsawPlacement.addPieces} ever
     * run, and post-hoc from the final piece bounding boxes for neighbour references -- neither
     * depends on the block position {@code addPieces} is handed) -- flagged for Kevin to confirm
     * against a real {@code javap} read before relying on it past this ticket's own harness
     * sweep, per the ticket's own instruction to "say so" if the safety of a cross-chunk move is
     * ever in doubt.
     */
    public static Optional<SiteCoordinate> searchAlternative(
            HeightSampler sampler, int originX, int originZ, int footprintRadius, ConfigModel.Site config) {
        List<SiteCoordinate> offsets = spiralOffsets(originX, originZ, config.searchStep(), config.searchRadius(), config.searchAttempts());

        SiteCoordinate best = null;
        SiteScore bestScore = null;
        for (SiteCoordinate candidate : offsets) {
            SiteScore score = SiteScorer.score(sampler, candidate.x(), candidate.z(), footprintRadius);
            if (!score.qualifies(config.maxHeightSpread(), config.maxWaterFraction())) {
                continue;
            }
            if (bestScore == null || isBetter(score, bestScore)) {
                best = candidate;
                bestScore = score;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * A fixed, deterministic spiral of up to {@code attempts} offsets from {@code (originX,
     * originZ)}: ring by ring outward at {@code step}-block spacing, the 8 {@link #DIRECTIONS} in
     * a fixed order per ring, stopping once a ring's own axis distance exceeds {@code radius}
     * (every later ring is strictly farther, so nothing more could qualify) or {@code attempts}
     * offsets have been collected, whichever comes first.
     */
    static List<SiteCoordinate> spiralOffsets(int originX, int originZ, int step, int radius, int attempts) {
        List<SiteCoordinate> result = new ArrayList<>();
        if (step <= 0 || attempts <= 0 || radius <= 0) {
            return result;
        }
        for (int ring = 1; result.size() < attempts; ring++) {
            int distance = ring * step;
            if (distance > radius) {
                // Axis directions (hypot 1) are the closest candidates in every ring; if even
                // they exceed the bound, no later, farther ring can do any better.
                break;
            }
            for (int[] dir : DIRECTIONS) {
                double magnitude = Math.hypot(dir[0], dir[1]) * distance;
                if (magnitude > radius) {
                    continue; // this ring's diagonal directions overshoot the bound; its axis ones don't.
                }
                result.add(new SiteCoordinate(originX + dir[0] * distance, originZ + dir[1] * distance));
                if (result.size() == attempts) {
                    return result;
                }
            }
        }
        return result;
    }

    /** Ascending height spread first, ascending water fraction as the tiebreak (see javadoc above). */
    private static boolean isBetter(SiteScore candidate, SiteScore currentBest) {
        if (candidate.heightSpread() != currentBest.heightSpread()) {
            return candidate.heightSpread() < currentBest.heightSpread();
        }
        return candidate.waterFraction() < currentBest.waterFraction();
    }
}
