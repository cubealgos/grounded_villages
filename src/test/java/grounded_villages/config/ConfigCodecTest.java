package grounded_villages.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `docs/spec/operations/testing.md` "the config file's default/clamp/fallback behaviour for every
 * malformed-input case in `domains/config.md` §6" -- {@link ConfigCodec}'s parse/serialise round
 * trip, its per-field degrade-to-default behaviour ({@code CONFIG-REQ-004}), its per-key
 * clamp-and-warn behaviour for every bounded key in `domains/config.md` §3, and its
 * ignore-with-a-warning handling of an unrecognised key (`CONFIG-FAIL-002`/{@code
 * SURFACE-REQ-002}).
 */
final class ConfigCodecTest {

    @Test
    void defaultsRoundTripThroughSerializeAndParseUnchanged() {
        ConfigModel defaults = ConfigDefaults.defaults();

        ConfigCodec.ParseOutcome outcome = ConfigCodec.parse(ConfigCodec.serialize(defaults));

        assertEquals(defaults, outcome.config());
        assertFalse(outcome.malformed());
        assertTrue(outcome.warnings().isEmpty(), () -> "the shipped defaults must never warn about themselves: " + outcome.warnings());
    }

    @Test
    void serializedDefaultsMatchDomainsConfigMdSection3Exactly() {
        String expected = """
                {
                  "schema_version": 1,
                  "scope": { "structure_tags": ["minecraft:village"] },
                  "site": {
                    "enabled": true,
                    "max_height_spread": 12,
                    "max_water_fraction": 0.05,
                    "search_radius": 48,
                    "search_step": 16,
                    "search_attempts": 8
                  },
                  "piece": {
                    "enabled": true,
                    "max_height_deviation": 6
                  },
                  "tier": {
                    "enabled": true,
                    "weights": { "hamlet": 30, "village": 45, "town": 20, "city": 5 },
                    "jigsaw_depth": { "hamlet": 3, "village": 6, "town": 8, "city": 10 },
                    "max_distance": { "hamlet": 80, "village": 96, "town": 128, "city": 128 },
                    "hamlet_minimum_pieces": 4,
                    "performance_cap_multiplier": 3.0
                  }
                }
                """;

        assertEquals(expected, ConfigCodec.serialize(ConfigDefaults.defaults()));
    }

    @Test
    void malformedJsonDegradesWhollyToDefaultsAndIsFlagged() {
        ConfigCodec.ParseOutcome outcome = ConfigCodec.parse("{ this is not valid json");

        assertTrue(outcome.malformed());
        assertEquals(ConfigDefaults.defaults(), outcome.config());
    }

    @Test
    void aJsonValueThatIsNotAnObjectIsAlsoFlaggedMalformed() {
        assertTrue(ConfigCodec.parse("[1, 2, 3]").malformed());
        assertTrue(ConfigCodec.parse("\"just a string\"").malformed());
        assertTrue(ConfigCodec.parse("").malformed());
    }

    @Test
    void aMissingKeyFallsBackToItsOwnDefaultWithoutAffectingItsSiblings() {
        String json = """
                { "site": { "max_height_spread": 20 } }
                """;

        ConfigModel config = ConfigCodec.parse(json).config();

        assertEquals(20, config.site().maxHeightSpread());
        // Every key this JSON didn't mention keeps its own shipped default.
        assertEquals(ConfigDefaults.DEFAULT_SITE_MAX_WATER_FRACTION, config.site().maxWaterFraction());
        assertEquals(ConfigDefaults.DEFAULT_SITE_SEARCH_RADIUS, config.site().searchRadius());
        assertEquals(ConfigDefaults.DEFAULT_PIECE_MAX_HEIGHT_DEVIATION, config.piece().maxHeightDeviation());
    }

    @Test
    void aWrongTypedKeyFallsBackToItsDefaultInsteadOfThrowing() {
        String json = """
                { "site": { "max_height_spread": "not a number" }, "piece": { "enabled": "not a boolean" } }
                """;

        ConfigModel config = ConfigCodec.parse(json).config();

        assertEquals(ConfigDefaults.DEFAULT_SITE_MAX_HEIGHT_SPREAD, config.site().maxHeightSpread());
        assertEquals(ConfigDefaults.DEFAULT_PIECE_ENABLED, config.piece().enabled());
    }

    @Test
    void anUnrecognisedTopLevelAndNestedKeyAreIgnoredWithAWarningNeverAnError() {
        String json = """
                {
                  "schema_version": 1,
                  "an_unknown_top_level_key": 123,
                  "site": { "enabled": true, "an_unknown_nested_key": "x" }
                }
                """;

        ConfigCodec.ParseOutcome outcome = ConfigCodec.parse(json);

        assertFalse(outcome.malformed());
        assertTrue(outcome.config().site().enabled(), "unknown keys must not corrupt the keys around them");
        assertTrue(outcome.warnings().stream().anyMatch(w -> w.contains("an_unknown_top_level_key")),
                () -> "expected a warning naming the unknown top-level key, got: " + outcome.warnings());
        assertTrue(outcome.warnings().stream().anyMatch(w -> w.contains("site.an_unknown_nested_key")),
                () -> "expected a warning naming the unknown nested key, got: " + outcome.warnings());
    }

    @Test
    void schemaVersionAlwaysComesBackAsTheCurrentOne() {
        assertEquals(ConfigDefaults.CURRENT_SCHEMA_VERSION, ConfigCodec.parse("{}").config().schemaVersion());
        assertEquals(ConfigDefaults.CURRENT_SCHEMA_VERSION,
                ConfigCodec.parse("{ \"schema_version\": 1 }").config().schemaVersion());
    }

    /** One case per bounded key in `domains/config.md` §3 -- `CONFIG-REQ-004`'s "clamp it" half. */
    private record ClampCase(String keyPath, String json, double expected, ToDoubleFunction<ConfigModel> extractor) {
        @Override
        public String toString() {
            return keyPath;
        }
    }

    private static String withSite(String key, Object value) {
        return "{ \"site\": { \"" + key + "\": " + value + " } }";
    }

    private static String withPiece(String key, Object value) {
        return "{ \"piece\": { \"" + key + "\": " + value + " } }";
    }

    private static String withTier(String key, Object value) {
        return "{ \"tier\": { \"" + key + "\": " + value + " } }";
    }

    private static String withTierNested(String group, String key, Object value) {
        return "{ \"tier\": { \"" + group + "\": { \"" + key + "\": " + value + " } } }";
    }

    private static List<ClampCase> clampCases() {
        return List.of(
                new ClampCase("site.max_height_spread", withSite("max_height_spread", -5), 0,
                        c -> c.site().maxHeightSpread()),
                new ClampCase("site.max_water_fraction", withSite("max_water_fraction", 1.5), 1.0,
                        c -> c.site().maxWaterFraction()),
                new ClampCase("site.search_radius", withSite("search_radius", 999), 128,
                        c -> c.site().searchRadius()),
                new ClampCase("site.search_step", withSite("search_step", 0), 1,
                        c -> c.site().searchStep()),
                new ClampCase("site.search_attempts", withSite("search_attempts", -3), 0,
                        c -> c.site().searchAttempts()),
                new ClampCase("piece.max_height_deviation", withPiece("max_height_deviation", -1), 0,
                        c -> c.piece().maxHeightDeviation()),
                new ClampCase("tier.weights.hamlet", withTierNested("weights", "hamlet", -10), 0,
                        c -> c.tier().weights().hamlet()),
                new ClampCase("tier.weights.village", withTierNested("weights", "village", -10), 0,
                        c -> c.tier().weights().village()),
                new ClampCase("tier.weights.town", withTierNested("weights", "town", -10), 0,
                        c -> c.tier().weights().town()),
                new ClampCase("tier.weights.city", withTierNested("weights", "city", -10), 0,
                        c -> c.tier().weights().city()),
                new ClampCase("tier.jigsaw_depth.hamlet", withTierNested("jigsaw_depth", "hamlet", 999), 32,
                        c -> c.tier().jigsawDepth().hamlet()),
                new ClampCase("tier.jigsaw_depth.village", withTierNested("jigsaw_depth", "village", 999), 32,
                        c -> c.tier().jigsawDepth().village()),
                new ClampCase("tier.jigsaw_depth.town", withTierNested("jigsaw_depth", "town", 999), 32,
                        c -> c.tier().jigsawDepth().town()),
                new ClampCase("tier.jigsaw_depth.city", withTierNested("jigsaw_depth", "city", 999), 32,
                        c -> c.tier().jigsawDepth().city()),
                new ClampCase("tier.max_distance.hamlet", withTierNested("max_distance", "hamlet", 999), 128,
                        c -> c.tier().maxDistance().hamlet()),
                new ClampCase("tier.max_distance.village", withTierNested("max_distance", "village", 999), 128,
                        c -> c.tier().maxDistance().village()),
                new ClampCase("tier.max_distance.town", withTierNested("max_distance", "town", 999), 128,
                        c -> c.tier().maxDistance().town()),
                new ClampCase("tier.max_distance.city", withTierNested("max_distance", "city", 999), 128,
                        c -> c.tier().maxDistance().city()),
                new ClampCase("tier.hamlet_minimum_pieces", withTier("hamlet_minimum_pieces", -1), 0,
                        c -> c.tier().hamletMinimumPieces()),
                new ClampCase("tier.performance_cap_multiplier", withTier("performance_cap_multiplier", 0), 0.1,
                        c -> c.tier().performanceCapMultiplier()));
    }

    private static Stream<Arguments> clampCaseArguments() {
        return clampCases().stream().map(Arguments::of);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("clampCaseArguments")
    void clampsEveryBoundedKeyToItsNearestValidBoundAndWarnsNamingTheKey(ClampCase testCase) {
        ConfigCodec.ParseOutcome outcome = ConfigCodec.parse(testCase.json());

        assertEquals(testCase.expected(), testCase.extractor().applyAsDouble(outcome.config()), 1e-9,
                () -> testCase.keyPath() + " was not clamped to its nearest valid bound");
        assertTrue(outcome.warnings().stream().anyMatch(w -> w.contains(testCase.keyPath())),
                () -> "expected a warning naming '" + testCase.keyPath() + "', got: " + outcome.warnings());
    }
}
