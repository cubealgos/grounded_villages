package grounded_villages.config;

import java.util.Map;

/**
 * The forward-only migration hook `docs/spec/contracts/data-contract.md` "Versioning" names: "a
 * future key addition is forward-compatible ... a removed or renamed key is a documented migration
 * note in release notes, not a silent behaviour change". Nothing has ever incremented
 * {@link ConfigDefaults#CURRENT_SCHEMA_VERSION} past {@code 1} yet, so {@link #migrate} is
 * currently a no-op for every file this mod can actually load -- this class exists so the *next*
 * schema change has one obvious place to add a step, not to migrate anything today.
 */
final class ConfigMigrations {

    private ConfigMigrations() {
    }

    /**
     * @param root the raw, already-parsed JSON object (`ConfigJson.parse`'s own {@code Map} shape)
     * @param fromVersion the file's own {@code schema_version} as read, before migration
     * @return {@code root} unchanged if {@code fromVersion >= CURRENT_SCHEMA_VERSION} (the normal
     *     case today, and the "no-op on current version" case a future migration must keep true) --
     *     a newer file than this build understands is read forward-compatibly by
     *     {@link ConfigCodec} itself (an unrecognised key is ignored with a warning), not migrated
     *     backward here
     */
    static Map<String, Object> migrate(Map<String, Object> root, int fromVersion) {
        Map<String, Object> current = root;
        int version = fromVersion;
        while (version < ConfigDefaults.CURRENT_SCHEMA_VERSION) {
            current = switch (version) {
                // case 1 -> migrateV1ToV2(current); -- the next migration step lands here, once
                //     CURRENT_SCHEMA_VERSION becomes 2.
                default -> current; // no step defined for this version yet: nothing left to do.
            };
            version++;
        }
        return current;
    }
}
