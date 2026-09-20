package grounded_villages.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * `docs/spec/contracts/data-contract.md` "Versioning": the forward-only migration hook. Nothing
 * has ever incremented {@link ConfigDefaults#CURRENT_SCHEMA_VERSION} past {@code 1}, so
 * {@link ConfigMigrations#migrate} must be a no-op for every file this build can actually load.
 */
final class ConfigMigrationsTest {

    @Test
    void migrationIsANoOpOnTheCurrentSchemaVersion() {
        Map<String, Object> root = Map.of("schema_version", 1.0, "site", Map.of("enabled", true));

        Map<String, Object> migrated = ConfigMigrations.migrate(root, ConfigDefaults.CURRENT_SCHEMA_VERSION);

        assertSame(root, migrated, "no migration step should run when the file is already current");
    }

    @Test
    void aFileFromANewerBuildIsLeftForConfigCodecsOwnForwardCompatibilityRatherThanMigratedBackward() {
        Map<String, Object> root = Map.of("schema_version", 2.0);

        Map<String, Object> migrated = ConfigMigrations.migrate(root, 2);

        assertEquals(root, migrated);
    }
}
