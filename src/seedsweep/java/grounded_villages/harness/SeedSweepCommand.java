package grounded_villages.harness;

import com.mojang.brigadier.CommandDispatcher;
import grounded_villages.hook.HookDebug;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * GV-10's dev-only sweep command, and the {@code seedSweep} Gradle task's automated hook into it.
 * Both paths call {@link SeedSweepRunner#run} -- the command is for interactive use in a dev
 * {@code runServer}/{@code runClient} session ({@code docs/spec/operations/testing.md}'s fallback
 * wording: "a mod command ... writing the result to a file rather than parsing log text"); the
 * automated hook is what {@code seedSweep} actually drives per seed, via system properties rather
 * than stdin or console output, so the harness never depends on a fragile log-text contract.
 *
 * <p>Registered reflectively from the shared Fabric entrypoint
 * ({@code src/fabric/java/grounded_villages/fabric/GroundedVillagesFabric.java}) -- this class only
 * exists on the 26.2-fabric node's classpath ({@code src/seedsweep/java}, node-conditional in
 * {@code build.fabric.gradle.kts}), so the entrypoint never references it by a static import.
 */
public final class SeedSweepCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("grounded_villages/seedsweep");

    private static final String OUTPUT_PROPERTY = "groundedvillages.seedsweep.output";
    private static final String SEED_PROPERTY = "groundedvillages.seedsweep.seed";

    private SeedSweepCommand() {
    }

    /** Called reflectively; see the class doc. */
    public static void register() {
        // GV-6's own reporting need (ticket build item 4: "report ... the Keep/Shift/Vanilla
        // counts"), not a GV-10 requirement -- the harness itself never reads this log, only
        // GV-6's own sweep analysis does, by grepping "VillageStartHook fired" lines. Safe to
        // leave on unconditionally: this class only exists in a development environment at all
        // (GroundedVillagesFabric's own isDevelopmentEnvironment() gate before it ever calls
        // #register).
        HookDebug.setEnabled(true);
        ServerLifecycleEvents.SERVER_STARTED.register(SeedSweepCommand::onServerStarted);
        LOGGER.info("Grounded Villages: seed-sweep harness registered (development environment)");
    }

    private static void onServerStarted(MinecraftServer server) {
        registerCommand(server);

        String outputPath = System.getProperty(OUTPUT_PROPERTY);
        if (outputPath == null) {
            return;
        }
        long seed = parseSeed(server, System.getProperty(SEED_PROPERTY));
        runAndStop(server, seed, Paths.get(outputPath));
    }

    private static void registerCommand(MinecraftServer server) {
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        dispatcher.register(
            Commands.literal("grounded_villages")
                .then(Commands.literal("sweep")
                    .executes(context -> {
                        Path defaultOutput = Paths.get("seedsweep-result.json");
                        runSweep(server, server.overworld().getSeed(), defaultOutput, context.getSource());
                        return 1;
                    })
                )
        );
    }

    private static void runAndStop(MinecraftServer server, long seed, Path outputFile) {
        try {
            runSweep(server, seed, outputFile, null);
        } finally {
            server.halt(false);
        }
    }

    private static void runSweep(MinecraftServer server, long seed, Path outputFile, CommandSourceStack source) {
        try {
            VillageSweepResult result = SeedSweepRunner.run(server, seed, outputFile);
            if (source != null) {
                String message = result == null
                    ? "grounded_villages sweep: no village found near spawn"
                    : "grounded_villages sweep: wrote " + outputFile.toAbsolutePath();
                source.sendSuccess(() -> Component.literal(message), false);
            }
        } catch (IOException e) {
            LOGGER.error("grounded_villages sweep failed", e);
            if (source != null) {
                source.sendFailure(Component.literal("grounded_villages sweep failed: " + e.getMessage()));
            }
        }
    }

    private static long parseSeed(MinecraftServer server, String property) {
        if (property != null) {
            try {
                return Long.parseLong(property.trim());
            } catch (NumberFormatException ignored) {
                LOGGER.warn("Invalid {} value '{}', falling back to the world's own seed", SEED_PROPERTY, property);
            }
        }
        return server.overworld().getSeed();
    }
}
