package grounded_villages.config;

import java.util.List;

/**
 * The shipped defaults: `docs/spec/domains/config.md` §3's proposed default JSON, verbatim, as
 * Java constants -- one source of truth {@link ConfigCodec} reads to fall back a missing or
 * wrong-typed key, and {@link ConfigModel}'s own compact constructors read to fall back a
 * {@code null} nested record. **Every numeric value below is proposed by Claude, 2026-09-20,
 * Kevin to confirm at the first ticket** (`docs/spec/README.md` "Open questions gathered") --
 * config-overridable regardless of outcome, and this class is the one place they would change.
 */
final class ConfigDefaults {

    private ConfigDefaults() {
    }

    /** The config file's own schema version at 1.0 (`docs/spec/contracts/data-contract.md`). */
    static final int CURRENT_SCHEMA_VERSION = 1;

    /** The file's name inside the game's config directory (`decisions/DEC-008-config-file.md`). */
    static final String FILE_NAME = "grounded_villages.json";

    static final List<String> DEFAULT_STRUCTURE_TAGS = List.of("minecraft:village");

    static final boolean DEFAULT_SITE_ENABLED = true;
    static final int DEFAULT_SITE_MAX_HEIGHT_SPREAD = 12;
    static final double DEFAULT_SITE_MAX_WATER_FRACTION = 0.05;
    static final int DEFAULT_SITE_SEARCH_RADIUS = 48;
    // GV-6, tuned from the first harness sweep (docs/baseline/README.md): step == radius puts the
    // search's own ring 1 at the full 48-block bound in one step, exploring only the 4 cardinal
    // directions (the two diagonal ones exceed the radius at step*sqrt(2) and are geometrically
    // excluded, SiteSearch#spiralOffsets) -- strictly cheaper than the original step 16 (4
    // evaluations instead of up to 8) while reaching 3x farther, which measurably raised the
    // qualifying-shift rate on the baseline's worst seeds without any extra sampling cost.
    static final int DEFAULT_SITE_SEARCH_STEP = 48;
    // Matches what step == radius actually makes reachable (4 cardinal offsets); higher values
    // cost nothing extra here since spiralOffsets stops once ring 2 (distance 96) exceeds the
    // 48-block radius, but 4 documents the real ceiling rather than implying unused headroom.
    static final int DEFAULT_SITE_SEARCH_ATTEMPTS = 4;

    static final boolean DEFAULT_PIECE_ENABLED = true;
    static final int DEFAULT_PIECE_MAX_HEIGHT_DEVIATION = 6;

    static final boolean DEFAULT_TIER_ENABLED = true;
    static final double DEFAULT_TIER_WEIGHT_HAMLET = 30;
    static final double DEFAULT_TIER_WEIGHT_VILLAGE = 45;
    static final double DEFAULT_TIER_WEIGHT_TOWN = 20;
    static final double DEFAULT_TIER_WEIGHT_CITY = 5;
    static final int DEFAULT_TIER_JIGSAW_DEPTH_HAMLET = 3;
    static final int DEFAULT_TIER_JIGSAW_DEPTH_VILLAGE = 6;
    static final int DEFAULT_TIER_JIGSAW_DEPTH_TOWN = 8;
    static final int DEFAULT_TIER_JIGSAW_DEPTH_CITY = 10;
    static final int DEFAULT_TIER_MAX_DISTANCE_HAMLET = 80;
    static final int DEFAULT_TIER_MAX_DISTANCE_VILLAGE = 96;
    static final int DEFAULT_TIER_MAX_DISTANCE_TOWN = 128;
    static final int DEFAULT_TIER_MAX_DISTANCE_CITY = 128;
    static final int DEFAULT_TIER_HAMLET_MINIMUM_PIECES = 4;
    static final double DEFAULT_TIER_PERFORMANCE_CAP_MULTIPLIER = 3.0;

    static ConfigModel.Scope defaultScope() {
        return new ConfigModel.Scope(DEFAULT_STRUCTURE_TAGS);
    }

    static ConfigModel.Site defaultSite() {
        return new ConfigModel.Site(
                DEFAULT_SITE_ENABLED,
                DEFAULT_SITE_MAX_HEIGHT_SPREAD,
                DEFAULT_SITE_MAX_WATER_FRACTION,
                DEFAULT_SITE_SEARCH_RADIUS,
                DEFAULT_SITE_SEARCH_STEP,
                DEFAULT_SITE_SEARCH_ATTEMPTS);
    }

    static ConfigModel.Piece defaultPiece() {
        return new ConfigModel.Piece(DEFAULT_PIECE_ENABLED, DEFAULT_PIECE_MAX_HEIGHT_DEVIATION);
    }

    static ConfigModel.TierWeights defaultWeights() {
        return new ConfigModel.TierWeights(
                DEFAULT_TIER_WEIGHT_HAMLET, DEFAULT_TIER_WEIGHT_VILLAGE, DEFAULT_TIER_WEIGHT_TOWN, DEFAULT_TIER_WEIGHT_CITY);
    }

    static ConfigModel.TierBudget defaultJigsawDepth() {
        return new ConfigModel.TierBudget(
                DEFAULT_TIER_JIGSAW_DEPTH_HAMLET,
                DEFAULT_TIER_JIGSAW_DEPTH_VILLAGE,
                DEFAULT_TIER_JIGSAW_DEPTH_TOWN,
                DEFAULT_TIER_JIGSAW_DEPTH_CITY);
    }

    static ConfigModel.TierBudget defaultMaxDistance() {
        return new ConfigModel.TierBudget(
                DEFAULT_TIER_MAX_DISTANCE_HAMLET,
                DEFAULT_TIER_MAX_DISTANCE_VILLAGE,
                DEFAULT_TIER_MAX_DISTANCE_TOWN,
                DEFAULT_TIER_MAX_DISTANCE_CITY);
    }

    static ConfigModel.Tier defaultTier() {
        return new ConfigModel.Tier(
                DEFAULT_TIER_ENABLED,
                defaultWeights(),
                defaultJigsawDepth(),
                defaultMaxDistance(),
                DEFAULT_TIER_HAMLET_MINIMUM_PIECES,
                DEFAULT_TIER_PERFORMANCE_CAP_MULTIPLIER);
    }

    /** The shipped defaults, matching `docs/spec/domains/config.md` §3's JSON block exactly. */
    static ConfigModel defaults() {
        return new ConfigModel(CURRENT_SCHEMA_VERSION, defaultScope(), defaultSite(), defaultPiece(), defaultTier());
    }
}
