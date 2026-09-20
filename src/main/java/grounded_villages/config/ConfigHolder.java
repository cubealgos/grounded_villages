package grounded_villages.config;

/**
 * The one place the mixin hook (`docs/spec/04-architecture.md` "Shape") and every pure-logic
 * class reads the loaded config from. Each loader's entrypoint calls
 * {@link ConfigIo#loadOrCreate(java.nio.file.Path)} once at startup and hands the result to
 * {@link #set(ConfigModel)}; nothing else in this mod ever calls {@link ConfigIo} directly
 * (`CONFIG-REQ-002`: loaded once, at server startup, no live reload at 1.0).
 *
 * <p>Starts holding {@link ConfigDefaults#defaults()} rather than {@code null}, so any code that
 * runs before a loader entrypoint has had a chance to call {@link #set} (a unit test, for
 * instance) still reads a valid config instead of risking a {@code NullPointerException}.
 */
public final class ConfigHolder {

    private static volatile ConfigModel current = ConfigDefaults.defaults();

    private ConfigHolder() {
    }

    /** The currently loaded config; never {@code null}. */
    public static ConfigModel get() {
        return current;
    }

    /** Called once, by each loader's own entrypoint, after {@link ConfigIo#loadOrCreate} returns. */
    public static void set(ConfigModel config) {
        current = config != null ? config : ConfigDefaults.defaults();
    }
}
