package grounded_villages.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Reads {@code grounded_villages.json} from the game's config directory at server startup,
 * writing the shipped defaults there the first time it is missing (`CONFIG-REQ-001`), and
 * validating an existing file on every later run (`CONFIG-REQ-002`). Takes the config directory
 * itself, not a loader-specific type, so it stays the one loader-agnostic startup path
 * `04-architecture.md` {@code ARCH-DEC-003} describes -- each loader's own entrypoint supplies
 * only the path ({@code FabricLoader.getInstance().getConfigDir()} on Fabric,
 * {@code FMLPaths.CONFIGDIR.get()} on NeoForge/Forge) and hands the result to
 * {@link ConfigHolder#set(ConfigModel)}.
 *
 * <p>{@link #loadOrCreate} never throws and never blocks server startup ({@code CONFIG-FAIL-001}
 * through {@code CONFIG-FAIL-003}): a malformed file is backed up alongside itself as
 * {@code grounded_villages.json.broken} (so an operator can see what was rejected) and replaced
 * with a fresh copy of the defaults; a config directory that cannot be written to at all falls
 * back to shipped defaults for the session, with a warning naming the path.
 */
public final class ConfigIo {

    private static final Logger LOGGER = LoggerFactory.getLogger("grounded_villages");

    /** {@code grounded_villages.json}'s own name inside the game's config directory. */
    public static final String FILE_NAME = ConfigDefaults.FILE_NAME;

    private ConfigIo() {
    }

    /**
     * @param configDir the game's config directory
     * @return a always-valid {@link ConfigModel}: freshly written defaults if the file was
     *     missing, the parsed-and-validated file if it existed and was readable, or
     *     {@link ConfigDefaults#defaults()} in-memory alone if the file existed but could not be
     *     read, or was malformed and could not be replaced either
     */
    public static ConfigModel loadOrCreate(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        if (Files.notExists(file)) {
            ConfigModel defaults = ConfigDefaults.defaults();
            writeQuietly(file, defaults);
            return defaults;
        }

        String text;
        try {
            text = Files.readString(file);
        } catch (IOException e) {
            LOGGER.warn("grounded_villages: failed to read {}, using the shipped defaults for this session: {}",
                    file, e.getMessage());
            return ConfigDefaults.defaults();
        }

        ConfigCodec.ParseOutcome outcome = ConfigCodec.parse(text);
        for (String warning : outcome.warnings()) {
            LOGGER.warn(warning);
        }

        if (outcome.malformed()) {
            LOGGER.warn("grounded_villages: {} is not valid JSON, backing it up and writing the shipped defaults", file);
            backUpQuietly(file);
            writeQuietly(file, outcome.config());
        }

        return outcome.config();
    }

    /** {@code CONFIG-FAIL-001}: a malformed file is moved aside, never deleted, so an operator can inspect it. */
    private static void backUpQuietly(Path file) {
        Path backup = file.resolveSibling(file.getFileName() + ".broken");
        try {
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("grounded_villages: backed up the malformed config to {}", backup);
        } catch (IOException e) {
            LOGGER.warn("grounded_villages: could not back up the malformed config at {} to {}: {}", file, backup, e.getMessage());
        }
    }

    /** {@code CONFIG-FAIL-003}: an unwritable config directory degrades to in-memory defaults, never a crash. */
    private static void writeQuietly(Path file, ConfigModel config) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, ConfigCodec.serialize(config));
            LOGGER.info("grounded_villages: wrote the config to {}", file);
        } catch (IOException e) {
            LOGGER.warn("grounded_villages: could not write the config to {}, running on in-memory defaults for this session: {}",
                    file, e.getMessage());
        }
    }
}
