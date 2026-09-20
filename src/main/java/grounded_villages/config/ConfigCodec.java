package grounded_villages.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses and serialises {@code grounded_villages.json} on top of {@link ConfigJson}
 * (`docs/spec/domains/config.md` §"Validation rules"). {@link #parse} never throws: malformed
 * JSON degrades to {@link ConfigDefaults#defaults()} wholesale ({@code CONFIG-FAIL-001}), and a
 * single missing or wrong-typed field degrades to just that field's own default
 * ({@code CONFIG-FAIL-002}) -- {@link ConfigModel}'s own compact constructors are the second line
 * of defence, silently clamping any in-range-type-but-out-of-range value the same way. This class
 * is the *first* line, and the only one that can log a warning naming the offending key, since it
 * is the only layer that still has the raw JSON key path in hand.
 */
final class ConfigCodec {

    private ConfigCodec() {
    }

    /**
     * @param config the parsed config -- always valid, per {@link ConfigModel}'s own guarantees
     * @param warnings one human-readable line per clamp applied or unrecognised key ignored
     *     (`CONFIG-REQ-004`), empty when the file was entirely well-formed and in range
     * @param malformed {@code true} when {@code text} was not a JSON object at all -- the whole
     *     file fell back to shipped defaults ({@code CONFIG-FAIL-001}); {@code config} is still
     *     {@link ConfigDefaults#defaults()} in that case
     */
    record ParseOutcome(ConfigModel config, List<String> warnings, boolean malformed) {
    }

    static ParseOutcome parse(String text) {
        List<String> warnings = new ArrayList<>();
        Object parsed;
        try {
            parsed = ConfigJson.parse(text);
        } catch (RuntimeException e) {
            return new ParseOutcome(ConfigDefaults.defaults(), warnings, true);
        }
        if (!(parsed instanceof Map<?, ?> rawRoot)) {
            return new ParseOutcome(ConfigDefaults.defaults(), warnings, true);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) rawRoot;

        int schemaVersion = intField(root, "schema_version", ConfigDefaults.CURRENT_SCHEMA_VERSION, warnings, "schema_version",
                Integer.MIN_VALUE, Integer.MAX_VALUE);
        root = ConfigMigrations.migrate(root, schemaVersion);

        Map<String, Object> scopeObj = objectField(root, "scope");
        Map<String, Object> siteObj = objectField(root, "site");
        Map<String, Object> pieceObj = objectField(root, "piece");
        Map<String, Object> tierObj = objectField(root, "tier");
        warnUnknownKeys("", root, Set.of("schema_version", "scope", "site", "piece", "tier"), warnings);

        ConfigModel.Scope scope = parseScope(scopeObj, warnings);
        ConfigModel.Site site = parseSite(siteObj, warnings);
        ConfigModel.Piece piece = parsePiece(pieceObj, warnings);
        ConfigModel.Tier tier = parseTier(tierObj, warnings);

        ConfigModel config = new ConfigModel(ConfigDefaults.CURRENT_SCHEMA_VERSION, scope, site, piece, tier);
        return new ParseOutcome(config, warnings, false);
    }

    private static ConfigModel.Scope parseScope(Map<String, Object> obj, List<String> warnings) {
        warnUnknownKeys("scope.", obj, Set.of("structure_tags"), warnings);
        Object rawTags = obj.get("structure_tags");
        if (!(rawTags instanceof List<?> list)) {
            return ConfigDefaults.defaultScope();
        }
        List<String> tags = new ArrayList<>();
        for (Object element : list) {
            if (element instanceof String s) {
                tags.add(s);
            }
        }
        if (tags.isEmpty()) {
            return ConfigDefaults.defaultScope();
        }
        return new ConfigModel.Scope(tags);
    }

    private static ConfigModel.Site parseSite(Map<String, Object> obj, List<String> warnings) {
        warnUnknownKeys("site.", obj, Set.of(
                "enabled", "max_height_spread", "max_water_fraction", "search_radius", "search_step", "search_attempts"), warnings);
        return new ConfigModel.Site(
                boolField(obj, "enabled", ConfigDefaults.DEFAULT_SITE_ENABLED),
                intField(obj, "max_height_spread", ConfigDefaults.DEFAULT_SITE_MAX_HEIGHT_SPREAD, warnings, "site.max_height_spread",
                        ConfigBounds.SITE_MAX_HEIGHT_SPREAD_MIN, ConfigBounds.SITE_MAX_HEIGHT_SPREAD_MAX),
                doubleField(obj, "max_water_fraction", ConfigDefaults.DEFAULT_SITE_MAX_WATER_FRACTION, warnings, "site.max_water_fraction",
                        ConfigBounds.FRACTION_MIN, ConfigBounds.FRACTION_MAX),
                intField(obj, "search_radius", ConfigDefaults.DEFAULT_SITE_SEARCH_RADIUS, warnings, "site.search_radius",
                        ConfigBounds.SITE_SEARCH_RADIUS_MIN, ConfigBounds.SITE_SEARCH_RADIUS_MAX),
                intField(obj, "search_step", ConfigDefaults.DEFAULT_SITE_SEARCH_STEP, warnings, "site.search_step",
                        ConfigBounds.SITE_SEARCH_STEP_MIN, ConfigBounds.SITE_SEARCH_STEP_MAX),
                intField(obj, "search_attempts", ConfigDefaults.DEFAULT_SITE_SEARCH_ATTEMPTS, warnings, "site.search_attempts",
                        ConfigBounds.SITE_SEARCH_ATTEMPTS_MIN, ConfigBounds.SITE_SEARCH_ATTEMPTS_MAX));
    }

    private static ConfigModel.Piece parsePiece(Map<String, Object> obj, List<String> warnings) {
        warnUnknownKeys("piece.", obj, Set.of("enabled", "max_height_deviation"), warnings);
        return new ConfigModel.Piece(
                boolField(obj, "enabled", ConfigDefaults.DEFAULT_PIECE_ENABLED),
                intField(obj, "max_height_deviation", ConfigDefaults.DEFAULT_PIECE_MAX_HEIGHT_DEVIATION, warnings,
                        "piece.max_height_deviation", ConfigBounds.PIECE_MAX_HEIGHT_DEVIATION_MIN, ConfigBounds.PIECE_MAX_HEIGHT_DEVIATION_MAX));
    }

    private static ConfigModel.Tier parseTier(Map<String, Object> obj, List<String> warnings) {
        warnUnknownKeys("tier.", obj, Set.of(
                "enabled", "weights", "jigsaw_depth", "max_distance", "hamlet_minimum_pieces", "performance_cap_multiplier"), warnings);

        Map<String, Object> weightsObj = objectField(obj, "weights");
        Map<String, Object> jigsawDepthObj = objectField(obj, "jigsaw_depth");
        Map<String, Object> maxDistanceObj = objectField(obj, "max_distance");
        warnUnknownKeys("tier.weights.", weightsObj, Set.of("hamlet", "village", "town", "city"), warnings);
        warnUnknownKeys("tier.jigsaw_depth.", jigsawDepthObj, Set.of("hamlet", "village", "town", "city"), warnings);
        warnUnknownKeys("tier.max_distance.", maxDistanceObj, Set.of("hamlet", "village", "town", "city"), warnings);

        ConfigModel.TierWeights weights = new ConfigModel.TierWeights(
                doubleField(weightsObj, "hamlet", ConfigDefaults.DEFAULT_TIER_WEIGHT_HAMLET, warnings, "tier.weights.hamlet",
                        ConfigBounds.TIER_WEIGHT_MIN, ConfigBounds.TIER_WEIGHT_MAX),
                doubleField(weightsObj, "village", ConfigDefaults.DEFAULT_TIER_WEIGHT_VILLAGE, warnings, "tier.weights.village",
                        ConfigBounds.TIER_WEIGHT_MIN, ConfigBounds.TIER_WEIGHT_MAX),
                doubleField(weightsObj, "town", ConfigDefaults.DEFAULT_TIER_WEIGHT_TOWN, warnings, "tier.weights.town",
                        ConfigBounds.TIER_WEIGHT_MIN, ConfigBounds.TIER_WEIGHT_MAX),
                doubleField(weightsObj, "city", ConfigDefaults.DEFAULT_TIER_WEIGHT_CITY, warnings, "tier.weights.city",
                        ConfigBounds.TIER_WEIGHT_MIN, ConfigBounds.TIER_WEIGHT_MAX));

        ConfigModel.TierBudget jigsawDepth = new ConfigModel.TierBudget(
                intField(jigsawDepthObj, "hamlet", ConfigDefaults.DEFAULT_TIER_JIGSAW_DEPTH_HAMLET, warnings, "tier.jigsaw_depth.hamlet",
                        ConfigBounds.TIER_JIGSAW_DEPTH_MIN, ConfigBounds.TIER_JIGSAW_DEPTH_MAX),
                intField(jigsawDepthObj, "village", ConfigDefaults.DEFAULT_TIER_JIGSAW_DEPTH_VILLAGE, warnings, "tier.jigsaw_depth.village",
                        ConfigBounds.TIER_JIGSAW_DEPTH_MIN, ConfigBounds.TIER_JIGSAW_DEPTH_MAX),
                intField(jigsawDepthObj, "town", ConfigDefaults.DEFAULT_TIER_JIGSAW_DEPTH_TOWN, warnings, "tier.jigsaw_depth.town",
                        ConfigBounds.TIER_JIGSAW_DEPTH_MIN, ConfigBounds.TIER_JIGSAW_DEPTH_MAX),
                intField(jigsawDepthObj, "city", ConfigDefaults.DEFAULT_TIER_JIGSAW_DEPTH_CITY, warnings, "tier.jigsaw_depth.city",
                        ConfigBounds.TIER_JIGSAW_DEPTH_MIN, ConfigBounds.TIER_JIGSAW_DEPTH_MAX));

        ConfigModel.TierBudget maxDistance = new ConfigModel.TierBudget(
                intField(maxDistanceObj, "hamlet", ConfigDefaults.DEFAULT_TIER_MAX_DISTANCE_HAMLET, warnings, "tier.max_distance.hamlet",
                        ConfigBounds.TIER_MAX_DISTANCE_MIN, ConfigBounds.TIER_MAX_DISTANCE_MAX),
                intField(maxDistanceObj, "village", ConfigDefaults.DEFAULT_TIER_MAX_DISTANCE_VILLAGE, warnings, "tier.max_distance.village",
                        ConfigBounds.TIER_MAX_DISTANCE_MIN, ConfigBounds.TIER_MAX_DISTANCE_MAX),
                intField(maxDistanceObj, "town", ConfigDefaults.DEFAULT_TIER_MAX_DISTANCE_TOWN, warnings, "tier.max_distance.town",
                        ConfigBounds.TIER_MAX_DISTANCE_MIN, ConfigBounds.TIER_MAX_DISTANCE_MAX),
                intField(maxDistanceObj, "city", ConfigDefaults.DEFAULT_TIER_MAX_DISTANCE_CITY, warnings, "tier.max_distance.city",
                        ConfigBounds.TIER_MAX_DISTANCE_MIN, ConfigBounds.TIER_MAX_DISTANCE_MAX));

        return new ConfigModel.Tier(
                boolField(obj, "enabled", ConfigDefaults.DEFAULT_TIER_ENABLED),
                weights,
                jigsawDepth,
                maxDistance,
                intField(obj, "hamlet_minimum_pieces", ConfigDefaults.DEFAULT_TIER_HAMLET_MINIMUM_PIECES, warnings,
                        "tier.hamlet_minimum_pieces", ConfigBounds.TIER_HAMLET_MINIMUM_PIECES_MIN, ConfigBounds.TIER_HAMLET_MINIMUM_PIECES_MAX),
                doubleField(obj, "performance_cap_multiplier", ConfigDefaults.DEFAULT_TIER_PERFORMANCE_CAP_MULTIPLIER, warnings,
                        "tier.performance_cap_multiplier", ConfigBounds.TIER_PERFORMANCE_CAP_MULTIPLIER_MIN, ConfigBounds.TIER_PERFORMANCE_CAP_MULTIPLIER_MAX));
    }

    // --- field extraction, matching `docs/spec/domains/config.md` §"Validation rules" ---

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static Map<String, Object> objectField(Map<String, Object> obj, String key) {
        return asObject(obj.get(key));
    }

    private static boolean boolField(Map<String, Object> obj, String key, boolean fallback) {
        Object value = obj.get(key);
        return value instanceof Boolean b ? b : fallback;
    }

    /** A missing/wrong-typed key falls back silently ({@code CONFIG-REQ-004}); an in-range-type
     *  but out-of-bound value is clamped, with a warning naming {@code keyPath} and the clamp
     *  (`domains/config.md` §"Validation rules"). */
    private static int intField(Map<String, Object> obj, String key, int fallback, List<String> warnings, String keyPath, int min, int max) {
        Object value = obj.get(key);
        if (!(value instanceof Double d)) {
            return fallback;
        }
        int intValue = d.intValue();
        int clamped = ConfigBounds.clamp(intValue, min, max);
        if (clamped != intValue) {
            warnings.add("grounded_villages: config key '" + keyPath + "' = " + intValue + " is out of range, clamped to " + clamped);
        }
        return clamped;
    }

    private static double doubleField(Map<String, Object> obj, String key, double fallback, List<String> warnings, String keyPath, double min, double max) {
        Object value = obj.get(key);
        if (!(value instanceof Double d)) {
            return fallback;
        }
        double clamped = ConfigBounds.clamp(d, min, max);
        if (clamped != d) {
            warnings.add("grounded_villages: config key '" + keyPath + "' = " + d + " is out of range, clamped to " + clamped);
        }
        return clamped;
    }

    private static void warnUnknownKeys(String prefix, Map<String, Object> obj, Set<String> known, List<String> warnings) {
        for (String key : obj.keySet()) {
            if (!known.contains(key)) {
                warnings.add("grounded_villages: config key '" + prefix + key + "' is not recognised, ignored");
            }
        }
    }

    /** The canonical on-disk shape, matching `domains/config.md` §3's proposed JSON exactly. */
    static String serialize(ConfigModel config) {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"schema_version\": ").append(config.schemaVersion()).append(",\n");
        out.append("  \"scope\": { \"structure_tags\": [")
                .append(jsonStringArray(config.scope().structureTags()))
                .append("] },\n");
        out.append("  \"site\": {\n");
        out.append("    \"enabled\": ").append(config.site().enabled()).append(",\n");
        out.append("    \"max_height_spread\": ").append(config.site().maxHeightSpread()).append(",\n");
        out.append("    \"max_water_fraction\": ").append(number(config.site().maxWaterFraction())).append(",\n");
        out.append("    \"search_radius\": ").append(config.site().searchRadius()).append(",\n");
        out.append("    \"search_step\": ").append(config.site().searchStep()).append(",\n");
        out.append("    \"search_attempts\": ").append(config.site().searchAttempts()).append("\n");
        out.append("  },\n");
        out.append("  \"piece\": {\n");
        out.append("    \"enabled\": ").append(config.piece().enabled()).append(",\n");
        out.append("    \"max_height_deviation\": ").append(config.piece().maxHeightDeviation()).append("\n");
        out.append("  },\n");
        out.append("  \"tier\": {\n");
        out.append("    \"enabled\": ").append(config.tier().enabled()).append(",\n");
        out.append("    \"weights\": { \"hamlet\": ").append(number(config.tier().weights().hamlet()))
                .append(", \"village\": ").append(number(config.tier().weights().village()))
                .append(", \"town\": ").append(number(config.tier().weights().town()))
                .append(", \"city\": ").append(number(config.tier().weights().city())).append(" },\n");
        out.append("    \"jigsaw_depth\": { \"hamlet\": ").append(config.tier().jigsawDepth().hamlet())
                .append(", \"village\": ").append(config.tier().jigsawDepth().village())
                .append(", \"town\": ").append(config.tier().jigsawDepth().town())
                .append(", \"city\": ").append(config.tier().jigsawDepth().city()).append(" },\n");
        out.append("    \"max_distance\": { \"hamlet\": ").append(config.tier().maxDistance().hamlet())
                .append(", \"village\": ").append(config.tier().maxDistance().village())
                .append(", \"town\": ").append(config.tier().maxDistance().town())
                .append(", \"city\": ").append(config.tier().maxDistance().city()).append(" },\n");
        out.append("    \"hamlet_minimum_pieces\": ").append(config.tier().hamletMinimumPieces()).append(",\n");
        out.append("    \"performance_cap_multiplier\": ")
                .append(String.format(Locale.ROOT, "%.1f", config.tier().performanceCapMultiplier())).append("\n");
        out.append("  }\n");
        out.append("}\n");
        return out.toString();
    }

    private static String jsonStringArray(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('"').append(values.get(i)).append('"');
        }
        return sb.toString();
    }

    /** A whole number prints without a decimal point (matching `domains/config.md` §3's own
     *  {@code "hamlet": 30} style); anything else prints its shortest exact decimal form. */
    private static String number(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
