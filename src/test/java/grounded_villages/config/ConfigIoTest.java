package grounded_villages.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `docs/spec/domains/config.md` §2 "Over time": file absent -> defaults written -> loaded and
 * validated -> takes effect next start. Plain file I/O against a JUnit temp directory, no game
 * instance needed -- {@link ConfigIo} takes a {@link Path}, not a loader type
 * (`04-architecture.md` {@code ARCH-DEC-003}).
 */
final class ConfigIoTest {

    @Test
    void firstRunWritesTheShippedDefaultsAndReturnsThem(@TempDir Path configDir) {
        Path file = configDir.resolve(ConfigIo.FILE_NAME);
        assertFalse(Files.exists(file), "precondition: no config file yet");

        ConfigModel loaded = ConfigIo.loadOrCreate(configDir);

        assertEquals(ConfigDefaults.defaults(), loaded);
        assertTrue(Files.exists(file), "CONFIG-REQ-001: the default config must be written on first run");
    }

    @Test
    void defaultsRoundTripThroughARealWriteThenReadOnDisk(@TempDir Path configDir) {
        ConfigIo.loadOrCreate(configDir); // first run: writes the defaults

        ConfigModel reloaded = ConfigIo.loadOrCreate(configDir); // second run: reads them back

        assertEquals(ConfigDefaults.defaults(), reloaded);
    }

    @Test
    void aCustomisedFileOnDiskIsHonouredInsteadOfBeingOverwritten(@TempDir Path configDir) throws IOException {
        ConfigModel custom = new ConfigModel(
                ConfigDefaults.CURRENT_SCHEMA_VERSION,
                ConfigDefaults.defaultScope(),
                new ConfigModel.Site(false, 20, 0.1, 32, 8, 4),
                ConfigDefaults.defaultPiece(),
                ConfigDefaults.defaultTier());
        Files.writeString(configDir.resolve(ConfigIo.FILE_NAME), ConfigCodec.serialize(custom));

        ConfigModel loaded = ConfigIo.loadOrCreate(configDir);

        assertEquals(custom, loaded);
    }

    @Test
    void malformedFileIsBackedUpAsBrokenAndReplacedWithFreshDefaults(@TempDir Path configDir) throws IOException {
        Path file = configDir.resolve(ConfigIo.FILE_NAME);
        String malformed = "{ this is not valid json";
        Files.writeString(file, malformed);

        ConfigModel loaded = ConfigIo.loadOrCreate(configDir);

        assertEquals(ConfigDefaults.defaults(), loaded, "CONFIG-FAIL-001: a malformed file degrades to defaults");

        Path backup = configDir.resolve(ConfigIo.FILE_NAME + ".broken");
        assertTrue(Files.exists(backup), "the malformed file must be preserved for the operator to inspect");
        assertEquals(malformed, Files.readString(backup));
        assertEquals(ConfigCodec.serialize(ConfigDefaults.defaults()), Files.readString(file),
                "the original path must now carry a fresh, valid copy of the defaults");
    }

    @Test
    void anUnwritableConfigDirectoryFallsBackToInMemoryDefaultsWithoutCrashing(@TempDir Path tempDir) throws IOException {
        // CONFIG-FAIL-003: the "directory" is actually occupied by a plain file, so
        // Files.createDirectories(...) must fail -- loadOrCreate must still return valid defaults
        // rather than crash startup.
        Path occupied = tempDir.resolve("config");
        Files.writeString(occupied, "occupied");

        ConfigModel loaded = ConfigIo.loadOrCreate(occupied);

        assertEquals(ConfigDefaults.defaults(), loaded);
        assertTrue(Files.isRegularFile(occupied), "the occupying file must be left alone, not crash the mod");
    }
}
