package grounded_villages.config;

import java.util.List;

/**
 * The pure, loader-free config model (`docs/spec/04-architecture.md` {@code ARCH-DEC-003}):
 * mirrors `docs/spec/domains/config.md` §3's schema table exactly, one nested record per JSON
 * object in the schema. Carries no Minecraft, Fabric, NeoForge or Forge import, and no
 * file-system access of its own -- reading and writing {@code grounded_villages.json} is
 * {@link ConfigIo}'s job, since a config directory is a loader concept this record cannot resolve
 * (`decisions/DEC-008-config-file.md`).
 *
 * <p>Correct by construction, like `villager_voices`' own {@code Config} record: every compact
 * constructor here clamps an out-of-range field to {@link ConfigDefaults}' own bound (never
 * throws), so a {@code ConfigModel} built from a malformed file -- or directly, by a test, or any
 * other future caller -- can never itself be invalid. This is the second line of defence;
 * {@link ConfigCodec} is the first, since only it can log a warning naming the offending key
 * (`docs/spec/domains/config.md` §"Validation rules").
 */
public record ConfigModel(int schemaVersion, Scope scope, Site site, Piece piece, Tier tier) {

    public ConfigModel {
        if (schemaVersion < 1) {
            schemaVersion = ConfigDefaults.CURRENT_SCHEMA_VERSION;
        }
        if (scope == null) {
            scope = ConfigDefaults.defaultScope();
        }
        if (site == null) {
            site = ConfigDefaults.defaultSite();
        }
        if (piece == null) {
            piece = ConfigDefaults.defaultPiece();
        }
        if (tier == null) {
            tier = ConfigDefaults.defaultTier();
        }
    }

    /** {@code scope.structure_tags} (`decisions/DEC-007-village-tag-scope.md`). */
    public record Scope(List<String> structureTags) {
        public Scope {
            if (structureTags == null || structureTags.isEmpty()) {
                structureTags = ConfigDefaults.DEFAULT_STRUCTURE_TAGS;
            } else {
                structureTags = List.copyOf(structureTags);
            }
        }
    }

    /** {@code site.*} -- `domains/site.md`'s own thresholds and search bounds. */
    public record Site(
            boolean enabled,
            int maxHeightSpread,
            double maxWaterFraction,
            int searchRadius,
            int searchStep,
            int searchAttempts) {
        public Site {
            maxHeightSpread = ConfigBounds.clamp(
                    maxHeightSpread, ConfigBounds.SITE_MAX_HEIGHT_SPREAD_MIN, ConfigBounds.SITE_MAX_HEIGHT_SPREAD_MAX);
            maxWaterFraction = ConfigBounds.clamp(
                    maxWaterFraction, ConfigBounds.FRACTION_MIN, ConfigBounds.FRACTION_MAX);
            searchRadius = ConfigBounds.clamp(
                    searchRadius, ConfigBounds.SITE_SEARCH_RADIUS_MIN, ConfigBounds.SITE_SEARCH_RADIUS_MAX);
            searchStep = ConfigBounds.clamp(
                    searchStep, ConfigBounds.SITE_SEARCH_STEP_MIN, ConfigBounds.SITE_SEARCH_STEP_MAX);
            searchAttempts = ConfigBounds.clamp(
                    searchAttempts, ConfigBounds.SITE_SEARCH_ATTEMPTS_MIN, ConfigBounds.SITE_SEARCH_ATTEMPTS_MAX);
        }
    }

    /** {@code piece.*} -- `domains/pieces.md`'s per-piece tolerance. */
    public record Piece(boolean enabled, int maxHeightDeviation) {
        public Piece {
            maxHeightDeviation = ConfigBounds.clamp(
                    maxHeightDeviation, ConfigBounds.PIECE_MAX_HEIGHT_DEVIATION_MIN, ConfigBounds.PIECE_MAX_HEIGHT_DEVIATION_MAX);
        }
    }

    /** {@code tier.*} -- `domains/tiers.md`'s weights, budgets and the performance cap. */
    public record Tier(
            boolean enabled,
            TierWeights weights,
            TierBudget jigsawDepth,
            TierBudget maxDistance,
            int hamletMinimumPieces,
            double performanceCapMultiplier) {
        public Tier {
            if (weights == null) {
                weights = ConfigDefaults.defaultWeights();
            }
            if (jigsawDepth == null) {
                jigsawDepth = ConfigDefaults.defaultJigsawDepth();
            }
            if (maxDistance == null) {
                maxDistance = ConfigDefaults.defaultMaxDistance();
            }
            // TierBudget is one shared shape for two different-bounded keys (jigsaw_depth vs.
            // max_distance) -- clamped here, per field, rather than in TierBudget's own compact
            // constructor, since only the caller knows which bound applies.
            jigsawDepth = new TierBudget(
                    ConfigBounds.clamp(jigsawDepth.hamlet(), ConfigBounds.TIER_JIGSAW_DEPTH_MIN, ConfigBounds.TIER_JIGSAW_DEPTH_MAX),
                    ConfigBounds.clamp(jigsawDepth.village(), ConfigBounds.TIER_JIGSAW_DEPTH_MIN, ConfigBounds.TIER_JIGSAW_DEPTH_MAX),
                    ConfigBounds.clamp(jigsawDepth.town(), ConfigBounds.TIER_JIGSAW_DEPTH_MIN, ConfigBounds.TIER_JIGSAW_DEPTH_MAX),
                    ConfigBounds.clamp(jigsawDepth.city(), ConfigBounds.TIER_JIGSAW_DEPTH_MIN, ConfigBounds.TIER_JIGSAW_DEPTH_MAX));
            maxDistance = new TierBudget(
                    ConfigBounds.clamp(maxDistance.hamlet(), ConfigBounds.TIER_MAX_DISTANCE_MIN, ConfigBounds.TIER_MAX_DISTANCE_MAX),
                    ConfigBounds.clamp(maxDistance.village(), ConfigBounds.TIER_MAX_DISTANCE_MIN, ConfigBounds.TIER_MAX_DISTANCE_MAX),
                    ConfigBounds.clamp(maxDistance.town(), ConfigBounds.TIER_MAX_DISTANCE_MIN, ConfigBounds.TIER_MAX_DISTANCE_MAX),
                    ConfigBounds.clamp(maxDistance.city(), ConfigBounds.TIER_MAX_DISTANCE_MIN, ConfigBounds.TIER_MAX_DISTANCE_MAX));
            hamletMinimumPieces = ConfigBounds.clamp(
                    hamletMinimumPieces, ConfigBounds.TIER_HAMLET_MINIMUM_PIECES_MIN, ConfigBounds.TIER_HAMLET_MINIMUM_PIECES_MAX);
            performanceCapMultiplier = ConfigBounds.clamp(
                    performanceCapMultiplier, ConfigBounds.TIER_PERFORMANCE_CAP_MULTIPLIER_MIN, ConfigBounds.TIER_PERFORMANCE_CAP_MULTIPLIER_MAX);
        }
    }

    /** {@code tier.weights.<hamlet|village|town|city>} -- relative roll weight per tier. */
    public record TierWeights(double hamlet, double village, double town, double city) {
        public TierWeights {
            hamlet = ConfigBounds.clamp(hamlet, ConfigBounds.TIER_WEIGHT_MIN, ConfigBounds.TIER_WEIGHT_MAX);
            village = ConfigBounds.clamp(village, ConfigBounds.TIER_WEIGHT_MIN, ConfigBounds.TIER_WEIGHT_MAX);
            town = ConfigBounds.clamp(town, ConfigBounds.TIER_WEIGHT_MIN, ConfigBounds.TIER_WEIGHT_MAX);
            city = ConfigBounds.clamp(city, ConfigBounds.TIER_WEIGHT_MIN, ConfigBounds.TIER_WEIGHT_MAX);
        }
    }

    /**
     * A per-tier integer budget: {@code tier.jigsaw_depth.*} ({@code maxDepth} fed to vanilla's
     * jigsaw assembly) or {@code tier.max_distance.*} ({@code max_distance_from_center}), each
     * clamped by its own caller since the two share this shape but not the same bounds.
     */
    public record TierBudget(int hamlet, int village, int town, int city) {}
}
