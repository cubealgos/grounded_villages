package grounded_villages.tier;

import grounded_villages.config.ConfigModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rolls exactly one {@link Tier} per village instance, deterministically from a single {@code
 * [0, 1)} draw the caller already took off {@code Structure.GenerationContext.random()}
 * (`docs/spec/domains/tiers.md` {@code TIER-REQ-001}) -- this class itself never touches a
 * {@code Random}, so the same draw always produces the same {@link TierAssignment}
 * (`04-architecture.md` {@code ARCH-DEC-002}: zero Minecraft import, pure function over a
 * primitive and a {@link ConfigModel.Tier}).
 *
 * <h2>The performance cap (`TIER-REQ-005`)</h2>
 *
 * {@code tier.performance_cap_multiplier} bounds a village's <em>expected</em> piece count at
 * {@code multiplier &times;} vanilla's own reference piece count ({@link
 * #BASELINE_MEAN_PIECE_COUNT}, GV-10's baseline sweep mean at vanilla's own unmodified depth 6 --
 * this ticket's own instruction: "using the baseline's mean piece count 117 as vanilla's
 * reference"), not the tier's own {@code jigsawDepth} directly, so {@link #capDepth} needs some
 * model of how piece count grows with depth to translate one into the other. It assumes piece
 * count scales <b>linearly</b> with depth, calibrated against that single reference point
 * ({@link #PIECES_PER_DEPTH_UNIT} {@code = 117 / 6}).
 *
 * <p><b>This is a known-rough approximation, not a conservative bound -- corrected after reading
 * this ticket's own tiers-enabled baseline sweep</b>
 * (`docs/baseline/grounded-26.2-fabric-10-seeds-tiers-only.json`): real piece count does not
 * sub-linearize with depth the way an earlier draft of this comment assumed. Observed means from
 * that sweep -- hamlet (depth 3): 42 pieces; village (depth 6): 136; town (depth 8): 297 -- grow
 * <em>faster</em> than linear (pieces-per-depth-unit rises from ~14 to ~23 to ~37 across those
 * three buckets, not flat), and a single depth-8 town in that same sweep (seed 7) hit 350 pieces
 * on its own, right at the default cap (117 &times; 3.0 = 351) even though {@link #capDepth} never
 * touched its depth (8 is already under the ~18-depth ceiling the linear model computes for the
 * default multiplier). Piece count also carries large inherent variance independent of depth --
 * GV-10's own vanilla baseline already ranged 50-230 pieces at the same fixed depth 6, driven by
 * terrain and pool-piece availability, not the depth budget -- so no cheap pre-generation formula
 * bounds real per-village generation cost tightly; {@link #capDepth} is a best-effort dampener on
 * the tier's own configured depth budget, per this ticket's own instruction ("cap the depth"), not
 * a hard runtime piece-count enforcement (that would need counting pieces live during assembly and
 * aborting mid-generation -- a materially heavier mechanism, out of this ticket's scope). Kept
 * linear rather than re-fit to a curve from these 10 noisy samples deliberately: the samples above
 * are too few and too variance-heavy per bucket (2-4 villages each) to fit a trustworthy exponent
 * without overfitting terrain noise -- flagged for Kevin as an open question, not silently
 * "fixed" by curve-fitting a model this data cannot actually support.
 */
public final class TierRoller {
    private static final Logger LOGGER = LoggerFactory.getLogger("grounded_villages/tier");
    private static volatile boolean zeroWeightWarningLogged = false;

    /** {@code MAX_TOTAL_STRUCTURE_RANGE}, the 128-block hard engine ceiling (`domains/tiers.md`
     *  "Spacing", already the same bound {@code ConfigBounds.TIER_MAX_DISTANCE_MAX} enforces at
     *  config-load time -- repeated here so {@link #roll} is self-contained for a caller, such as
     *  a test, that builds a {@link ConfigModel.Tier} directly rather than through config load). */
    public static final int HARD_MAX_DISTANCE = 128;

    /** GV-10's baseline sweep mean piece count at vanilla's own depth (`docs/baseline/README.md`
     *  "Baseline: 10 vanilla seeds" -- 117.1 across 10 seeds, truncated to the whole-piece
     *  reference this class's model uses). */
    public static final double BASELINE_MEAN_PIECE_COUNT = 117.0;

    /** {@code village_plains.json}'s own {@code size} -- vanilla's unmodified jigsaw depth, the
     *  depth the baseline above was measured at (`village-jigsaw-placement...md` §D). */
    public static final int BASELINE_JIGSAW_DEPTH = 6;

    private static final double PIECES_PER_DEPTH_UNIT = BASELINE_MEAN_PIECE_COUNT / BASELINE_JIGSAW_DEPTH;

    /** The safe fallback distribution `TIER-FAIL-002` names ("weighted toward village"), applied
     *  when every configured weight is non-positive. */
    private static final double FALLBACK_WEIGHT_HAMLET = 30.0;
    private static final double FALLBACK_WEIGHT_VILLAGE = 45.0;
    private static final double FALLBACK_WEIGHT_TOWN = 20.0;
    private static final double FALLBACK_WEIGHT_CITY = 5.0;

    private TierRoller() {
    }

    /**
     * Rolls a tier and returns its already-capped write-back budget.
     *
     * @param config the loaded {@code tier.*} config; {@code config.enabled() == false} always
     *     returns {@link Tier#VILLAGE} at its own configured (capped) budget, ignoring {@code
     *     draw} entirely -- callers on the hot path should prefer to skip calling {@link #roll} at
     *     all when tiers are disabled, to avoid consuming a random draw for nothing
     *     (`grounded_villages.mixin.village.JigsawStructureMixin`); this branch exists so the pure
     *     function itself still has a defined, tested answer for every input, per this codebase's
     *     own belt-and-braces convention ({@code ConfigModel}'s compact constructors)
     * @param draw a value in {@code [0, 1)}, e.g. {@code WorldgenRandom.nextDouble()}; a value
     *     outside that range is clamped rather than throwing, so a caller's own rounding error
     *     never crashes world generation
     */
    public static TierAssignment roll(ConfigModel.Tier config, double draw) {
        Tier tier = config.enabled() ? pickTier(config.weights(), draw) : Tier.VILLAGE;
        int rawDepth = jigsawDepthOf(config.jigsawDepth(), tier);
        int rawMaxDistance = maxDistanceOf(config.maxDistance(), tier);
        int cappedDepth = capDepth(rawDepth, config.performanceCapMultiplier());
        int cappedMaxDistance = Math.min(rawMaxDistance, HARD_MAX_DISTANCE);
        return new TierAssignment(tier, cappedDepth, cappedMaxDistance);
    }

    /** Package-visible for {@code TierRollerTest}'s own distribution test. */
    static Tier pickTier(ConfigModel.TierWeights weights, double draw) {
        double hamlet = Math.max(0.0, weights.hamlet());
        double village = Math.max(0.0, weights.village());
        double town = Math.max(0.0, weights.town());
        double city = Math.max(0.0, weights.city());
        double total = hamlet + village + town + city;

        if (total <= 0.0) {
            // TIER-FAIL-002: every weight non-positive falls back to a safe village-weighted
            // distribution, logged once per session rather than once per village (world
            // generation rolls a tier per village instance; a misconfigured server would
            // otherwise spam this warning continuously).
            warnZeroWeightsOnce();
            hamlet = FALLBACK_WEIGHT_HAMLET;
            village = FALLBACK_WEIGHT_VILLAGE;
            town = FALLBACK_WEIGHT_TOWN;
            city = FALLBACK_WEIGHT_CITY;
            total = hamlet + village + town + city;
        }

        double clampedDraw = Math.max(0.0, Math.min(Math.nextDown(1.0), draw));
        double x = clampedDraw * total;
        if (x < hamlet) {
            return Tier.HAMLET;
        }
        x -= hamlet;
        if (x < village) {
            return Tier.VILLAGE;
        }
        x -= village;
        if (x < town) {
            return Tier.TOWN;
        }
        return Tier.CITY;
    }

    private static void warnZeroWeightsOnce() {
        if (!zeroWeightWarningLogged) {
            zeroWeightWarningLogged = true;
            LOGGER.warn(
                "grounded_villages: tier.weights.* summed to zero across every tier "
                    + "(TIER-FAIL-002) -- falling back to the safe village-weighted distribution "
                    + "({}/{}/{}/{} hamlet/village/town/city)",
                FALLBACK_WEIGHT_HAMLET, FALLBACK_WEIGHT_VILLAGE, FALLBACK_WEIGHT_TOWN, FALLBACK_WEIGHT_CITY
            );
        }
    }

    private static int jigsawDepthOf(ConfigModel.TierBudget budget, Tier tier) {
        return switch (tier) {
            case HAMLET -> budget.hamlet();
            case VILLAGE -> budget.village();
            case TOWN -> budget.town();
            case CITY -> budget.city();
        };
    }

    private static int maxDistanceOf(ConfigModel.TierBudget budget, Tier tier) {
        return switch (tier) {
            case HAMLET -> budget.hamlet();
            case VILLAGE -> budget.village();
            case TOWN -> budget.town();
            case CITY -> budget.city();
        };
    }

    /**
     * {@code TIER-REQ-005}/{@code TIER-FAIL-001}: clamps {@code rawDepth} so the linear
     * expected-piece-count model (this class's own javadoc) stays under {@code multiplier
     * &times;} {@link #BASELINE_MEAN_PIECE_COUNT}. Never raises a depth, only lowers it -- a tier
     * whose own configured depth already fits under the cap is returned unchanged.
     */
    private static int capDepth(int rawDepth, double performanceCapMultiplier) {
        double expectedPieceCap = BASELINE_MEAN_PIECE_COUNT * performanceCapMultiplier;
        int maxAllowedDepth = (int) Math.floor(expectedPieceCap / PIECES_PER_DEPTH_UNIT);
        maxAllowedDepth = Math.max(0, maxAllowedDepth);
        return Math.min(rawDepth, maxAllowedDepth);
    }
}
