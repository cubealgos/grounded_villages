package grounded_villages.config;

/**
 * The "sane range" bounds `docs/spec/domains/config.md` §"Validation rules" names by example
 * ("a negative radius, a fraction outside 0-1") but does not enumerate key-by-key -- this class is
 * that enumeration, one bound pair per numeric key in `docs/spec/domains/config.md` §3's schema
 * table, plus the {@link #clamp(int, int, int)}/{@link #clamp(double, double, double)} helpers
 * both {@link ConfigModel} (silent, defensive) and {@link ConfigCodec} (logged, per-key) clamp
 * against -- one set of numbers, not two.
 *
 * <p>Every bound below is a guardrail against a corrupted or hand-edited file, not a game-balance
 * decision (`00-context.md` "never invent deferred content") -- most floors are the literal
 * examples the validation table gives (non-negative), and the two ceilings that are not simply
 * "no sane upper bound" are ones the spec itself already states elsewhere: {@code 0-1} for a
 * fraction (§3's own type column), and the {@code 128}-block hard engine ceiling
 * (`domains/tiers.md` "Spacing", `MAX_TOTAL_STRUCTURE_RANGE`).
 */
final class ConfigBounds {

    private ConfigBounds() {
    }

    static final double FRACTION_MIN = 0.0;
    static final double FRACTION_MAX = 1.0;

    /** No natural ceiling narrower than the build-height span; a generous, corruption-only guard. */
    static final int SITE_MAX_HEIGHT_SPREAD_MIN = 0;
    static final int SITE_MAX_HEIGHT_SPREAD_MAX = 384;

    /** `domains/tiers.md` "Spacing": the 128-block hard engine ceiling bounds every block-radius key. */
    static final int SITE_SEARCH_RADIUS_MIN = 0;
    static final int SITE_SEARCH_RADIUS_MAX = 128;

    /** Must be positive -- a zero step never advances the search. */
    static final int SITE_SEARCH_STEP_MIN = 1;
    static final int SITE_SEARCH_STEP_MAX = 128;

    static final int SITE_SEARCH_ATTEMPTS_MIN = 0;
    static final int SITE_SEARCH_ATTEMPTS_MAX = 64;

    static final int PIECE_MAX_HEIGHT_DEVIATION_MIN = 0;
    static final int PIECE_MAX_HEIGHT_DEVIATION_MAX = 384;

    /** A relative roll weight; no sane ceiling narrower than "very large", only non-negative. */
    static final double TIER_WEIGHT_MIN = 0.0;
    static final double TIER_WEIGHT_MAX = 1_000_000.0;

    /** Vanilla's own jigsaw depth is a small integer (`03-glossary.md` "jigsaw depth"). */
    static final int TIER_JIGSAW_DEPTH_MIN = 0;
    static final int TIER_JIGSAW_DEPTH_MAX = 32;

    /** The 128-block hard engine ceiling, explicit in `domains/tiers.md` "Spacing". */
    static final int TIER_MAX_DISTANCE_MIN = 0;
    static final int TIER_MAX_DISTANCE_MAX = 128;

    static final int TIER_HAMLET_MINIMUM_PIECES_MIN = 0;
    static final int TIER_HAMLET_MINIMUM_PIECES_MAX = 1_000;

    /** Must be positive to mean anything as a multiplier on vanilla's own piece count. */
    static final double TIER_PERFORMANCE_CAP_MULTIPLIER_MIN = 0.1;
    static final double TIER_PERFORMANCE_CAP_MULTIPLIER_MAX = 100.0;

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
