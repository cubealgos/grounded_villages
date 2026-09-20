package grounded_villages.harness;

import com.mojang.brigadier.CommandDispatcher;
import grounded_villages.hook.HookDebug;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * GV-12's NeoForge twin of {@code SeedSweepCommand} (GV-10, the Fabric leg) -- same dev-only
 * sweep command and the same {@code seedSweep} Gradle-task hook into {@link SeedSweepRunner#run},
 * adapted only for NeoForge's own event shape: no {@code ServerLifecycleEvents.SERVER_STARTED}
 * (a Fabric API module this project does not depend on for the NeoForge legs), so this class
 * subscribes to {@code net.neoforged.neoforge.event.server.ServerStartedEvent} and {@code
 * net.neoforged.neoforge.event.RegisterCommandsEvent} on {@link NeoForge#EVENT_BUS} instead --
 * both confirmed present and stable across NeoForge {@code 21.1.251} and {@code 26.2.0.88} by
 * direct decompilation of the real {@code neoforge-*-userdev.jar} artifacts (GV-12), unlike the
 * one real API drift {@code GroundedVillagesNeoForge} itself has to straddle.
 *
 * <p>Registered reflectively from the shared NeoForge entrypoint ({@code
 * src/neoforge/java/grounded_villages/neoforge/GroundedVillagesNeoForge.java}) -- this class only
 * exists on the 26.2-neoforge node's classpath ({@code src/seedsweep-neoforge/java},
 * node-conditional in {@code build.neoforge.gradle.kts}), so the entrypoint (compiled on both
 * NeoForge nodes) never references it by a static import and keeps compiling on 1.21.1-neoforge,
 * which never has this class at all.
 *
 * <p>{@link SeedSweepRunner}, {@link SeedSweepStats}, and {@link VillageSweepResult} are the same
 * loader-agnostic classes ({@code src/seedsweep/java}, zero Fabric/NeoForge import) GV-10's own
 * Fabric harness already uses -- this class is the only new code GV-12 adds, mirroring {@code
 * SeedSweepCommand}'s own shape method-for-method so the two loaders' harnesses stay obviously
 * the same tool with a different front door, not two independent implementations that could
 * silently drift.
 */
public final class SeedSweepCommandNeoForge {
    private static final Logger LOGGER = LoggerFactory.getLogger("grounded_villages/seedsweep");

    private static final String OUTPUT_PROPERTY = "groundedvillages.seedsweep.output";
    private static final String SEED_PROPERTY = "groundedvillages.seedsweep.seed";

    private SeedSweepCommandNeoForge() {
    }

    /** Called reflectively; see the class doc. */
    public static void register() {
        // Mirrors SeedSweepCommand#register's own reasoning: safe to leave on unconditionally,
        // since this class only exists in a development environment at all
        // (GroundedVillagesNeoForge's own isDevelopment() gate runs before it ever calls
        // #register).
        HookDebug.setEnabled(true);
        NeoForge.EVENT_BUS.register(SeedSweepCommandNeoForge.class);
        LOGGER.info("Grounded Villages: seed-sweep harness registered (development environment, NeoForge)");
    }

    /**
     * Static {@code @SubscribeEvent} methods, registered via {@code NeoForge.EVENT_BUS.register}
     * called with a {@code Class} (not an instance) in {@link #register} above -- confirmed live
     * against the real {@code net.neoforged:bus:8.0.5} jar (GV-12): {@code IEventBus.register}
     * accepts either an instance (non-static listeners) or a {@code Class} (static listeners),
     * and this event bus's own error message for a static method registered via an instance
     * ("because register() was called with a class type ... make the method non-static, or call
     * register(%s.class)") is the direct evidence for the class-type path this uses.
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("grounded_villages")
                .then(Commands.literal("sweep")
                    .executes(context -> {
                        MinecraftServer server = context.getSource().getServer();
                        Path defaultOutput = Paths.get("seedsweep-result.json");
                        runSweep(server, server.overworld().getSeed(), defaultOutput, context.getSource());
                        return 1;
                    })
                )
        );
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        String outputPath = System.getProperty(OUTPUT_PROPERTY);
        if (outputPath == null) {
            return;
        }
        long seed = parseSeed(server, System.getProperty(SEED_PROPERTY));
        runAndStop(server, seed, Paths.get(outputPath));
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
