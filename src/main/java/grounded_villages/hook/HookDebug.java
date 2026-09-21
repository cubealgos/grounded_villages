package grounded_villages.hook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A log line each time a hook fires, gated by a static flag -- so GV-10's headless harness can
 * count firings (ticket build item 5), and so GV-6's own harness sweep can count Keep/Shift/
 * Vanilla decisions the same way. Off by default; no config system exists yet (GV-9 owns {@code
 * grounded_villages.config}, not touched here), so this is driven by a system property for now:
 * {@code -Dgrounded_villages.debug=true}, or {@link #setEnabled} once a config loader exists to
 * drive it from the config file.
 *
 * <p>Logged at {@code INFO}, not {@code DEBUG} (changed GV-6, confirmed live during this ticket's
 * own harness sweep): a dedicated server's default log4j2 configuration filters {@code DEBUG}
 * out entirely, so a {@code LOGGER.debug} call here was invisible even with {@link #enabled} true
 * -- since every call site already gates on that same flag (opt-in, off by default), logging at
 * {@code INFO} once opted in costs nothing extra and is the only level that is actually visible
 * without also shipping a custom log4j2 config.
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
            LOGGER.info("[grounded_villages] {} fired: {}", hook, detail);
        }
    }
}
