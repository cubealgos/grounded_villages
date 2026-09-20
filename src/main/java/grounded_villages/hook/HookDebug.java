package grounded_villages.hook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A debug-level log line each time a hook fires, gated by a static flag -- so GV-10's headless
 * harness can count firings (ticket build item 5). Off by default; no config system exists yet
 * (GV-9 owns {@code grounded_villages.config}, not touched here), so this is driven by a system
 * property for now: {@code -Dgrounded_villages.debug=true}, or {@link #setEnabled} once a config
 * loader exists to drive it from the config file.
 */
public final class HookDebug {
    private static final Logger LOGGER = LoggerFactory.getLogger("grounded_villages/hook");
    private static volatile boolean enabled = Boolean.getBoolean("grounded_villages.debug");

    private HookDebug() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void fired(String hook, Object detail) {
        if (enabled) {
            LOGGER.debug("[grounded_villages] {} fired: {}", hook, detail);
        }
    }
}
