// Fabric leg, shared by all three MC versions (1.20.1, 1.21.1, 26.2) -- one build script per
// docs/spec/04-architecture.md "Shape". `loom-back-compat` picks the correct Loom major
// internally per node's Minecraft version (needed at the 26.1+ boundary, settings.gradle.kts).
import java.net.ServerSocket

plugins {
    id("dev.kikugie.loom-back-compat")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "${property("mod.id") as String}-fabric"

// docs/spec/contracts/platform-matrix.md "Per-row toolchain": 1.20.1 -> 17, 1.21.1 -> 21,
// 26.2 -> 25 (fabric-loom's own `requiredJava` table, quoted in the same contract).
val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    sc.current.parsed >= "1.18" -> JavaVersion.VERSION_17
    else -> throw GradleException("grounded_villages targets 1.20.1 and above only")
}

// GV-10's seedSweep task points each per-seed `runServer` at its own run directory via this same
// property, read here once so both the `loom { runConfigs }` block below and the harness's
// server.properties/eula.txt writer (further down, 26.2-fabric only) always agree on the
// directory -- computed independently rather than read back off the task, since Loom itself
// resolves `runDirectory` lazily and a second `doFirst` reading `workingDir` could race it.
val serverRunDir: File = (project.findProperty("groundedvillages.runDir") as String?)
    ?.let { rootProject.file(it) }
    ?: rootProject.file("run")

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    // Mojang mappings everywhere (decisions/DEC-004-versions-and-toolchain.md).
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation(fabricApi.module("fabric-lifecycle-events-v1", sc.properties["deps.fabric_api"]))

    // Plain JUnit 5/Jupiter for the shared src/test/java tree (GV-9), no Minecraft classpath
    // needed -- docs/spec/operations/testing.md "Unit" layer.
    testImplementation(platform("org.junit:junit-bom:${property("deps.junit")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        // GV-10's seedSweep task points each per-seed `runServer` invocation at its own run
        // directory (`-Pgroundedvillages.runDir=...`) so N seeds never share one world save;
        // every other invocation keeps the previous, unconditional default (plain "run").
        runDirectory = serverRunDir
    }
}

java {
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava

    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

sourceSets.main {
    // docs/spec/04-architecture.md "Shape": one shared src/main/ tree, entrypoint stubs per
    // loader under src/<loader>/java, selected here by node.
    java.srcDir(rootProject.file("src/fabric/java"))
}

// src/test/java (docs/spec/operations/testing.md "Unit" layer) is already wired to every node's
// own `test` sourceSet by Stonecutter itself, the same "one shared tree" default `src/main/java`
// gets (verified live, GV-9: adding either srcDir a second time here produces a
// "duplicate class" compile error on every non-active node) -- only the JUnit dependencies and
// `useJUnitPlatform()` below are this ticket's own to add.
tasks.test {
    useJUnitPlatform()
}

tasks {
    processResources {
        fun MutableMap<String, String>.register(key: String, property: String) {
            val value: String = sc.properties[property]
            inputs.property(key, value)
            set(key, value)
        }

        val props = buildMap {
            register("id", "mod.id")
            register("name", "mod.name")
            register("version", "mod.version")
            register("minecraft", "mod.mc_compat")
        }

        filesMatching("fabric.mod.json") { expand(props) }

        val mixinJava = "JAVA_${requiredJava.majorVersion}"
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }

        exclude("META-INF/neoforge.mods.toml", "META-INF/mods.toml")
    }

    val modId = project.property("mod.id") as String
    withType<Jar> {
        from(rootProject.file("LICENSE")) { rename { "$it-$modId" } }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the mod jar and copies it to build/libs/{mod version}/"

        inputs.property("version", project.property("mod.version"))
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    }
}

// GV-10: the headless seed-sweep harness, on the 26.2-fabric node only
// (docs/spec/operations/testing.md "Verification for the first ticket" V1). Kept in this single
// node-conditional block so the other five nodes (1.20.1-fabric, 1.21.1-fabric, both forge/neoforge
// legs) never see `src/seedsweep/java` or these extra tasks at all.
if (sc.current.project == "26.2-fabric") {
    sourceSets.main {
        java.srcDir(rootProject.file("src/seedsweep/java"))
    }

    // GV-11: per-loader game tests -- 26.2-fabric only, NOT every fabric node. Confirmed live
    // (`./gradlew :1.21.1-fabric:compileJava`): the resolved fabric-gametest-api-v1 module at
    // 1.21.1's own deps.fabric_api pin (0.116.17+1.21.1) has no
    // `net.fabricmc.fabric.api.gametest.v1.GameTest` class at all -- that annotation is a newer
    // addition, present only at 26.2's own module version (4.0.22+4a7fa0819e, confirmed present
    // and confirmed compiling live on this node). 1.20.1 fails the same way, one version further
    // back. `grounded_villages.gametest` (the shared scenario bodies) also needs `LiquidSettings`,
    // itself absent before 1.21 -- another reason this whole tree stays node-gated rather than
    // shared unconditionally like `src/fabric/java` above.
    dependencies {
        modImplementation(fabricApi.module("fabric-gametest-api-v1", sc.properties["deps.fabric_api"]))
    }
    sourceSets.main {
        java.srcDir(rootProject.file("src/gametest/java"))
        java.srcDir(rootProject.file("src/fabricgametest/java"))
    }
    loom.runConfigs.register("gameTest") {
        // GV-11: `-Dfabric-api.gametest=true` is the documented switch `fabric-gametest-api-v1`
        // reads to run every discovered `@GameTest`-annotated method headlessly and exit non-zero
        // on any failure, instead of booting an interactive dedicated server
        // (`net.fabricmc.fabric.impl.gametest.GameTestSystemProperties`, confirmed present in the
        // resolved module jar). Registered as its own Loom run config (task name `runGameTest`,
        // Loom's own "run" + capitalised config name convention) rather than folded into the
        // existing `server` config, so `just gametest`/CI can invoke it without touching the
        // config a person's own `just server`/`just client` session already uses.
        server()
        name("Game Test")
        source(sourceSets.main.get())
        vmArg("-Dfabric-api.gametest=true")
        // A dedicated directory, not the `runConfigs.all` default ("run", the same directory
        // `just server`/`just client` and seedSweep's own runServer use) -- confirmed live:
        // running this alongside :1.21.1-neoforge:runGameTestServer (itself defaulting to "run"
        // too) under Gradle's own parallel execution (org.gradle.parallel=true,
        // gradle.properties) crashed with a DirectoryLock$LockException, both trying to open the
        // same save at once.
        runDir("run-gametest-fabric")
        vmArg("-Dfabric-api.gametest.report-file=${rootProject.file("build/gametest/fabric-report.xml")}")
    }

    val seedProp = providers.gradleProperty("groundedvillages.seed")
    val outputProp = providers.gradleProperty("groundedvillages.output")
    // seedSweep hands each per-seed run its own free port (picked once per seed, before this
    // process starts) -- sibling worktrees (GV-3/GV-4/GV-5/GV-9) run their own `runServer` on the
    // default 25565 independently, on the same machine, and did collide with it during this
    // ticket's own manual verification. A single-seed manual `runServer` call without
    // -Pgroundedvillages.port keeps the vanilla default.
    val portProp = providers.gradleProperty("groundedvillages.port")

    // GV-7's own harness sweep, seed 1: the shrink/move/vanilla ladder's own "assemble again"
    // steps (up to 3x a village's own full jigsaw-assembly cost per candidate, against the SAME
    // "findNearestMapStructure forces many structure-set cells to generate synchronously" stress
    // GV-6's own SiteScorer.MAX_SAMPLE_COLUMNS tuning already fought) tipped a real run past the
    // vanilla 60s default max-tick-time and crashed the watchdog outright (`A single server tick
    // took 60.01 seconds`) -- this default server.properties key does not otherwise exist.
    // Raised, not disabled (`-1`), so a genuine hang still eventually crashes the harness rather
    // than running forever: `docs/baseline/README.md`'s own GV-7 section has the measured cost
    // and the honest flag that a fail-fast ladder (bail the "move"/"vanilla" steps early once a
    // shifted candidate's own site-level score is already hopeless) is the real fix, out of this
    // ticket's own scope, same as GV-6's own column-cap finding was. This is a harness-only
    // server.properties value -- a real dedicated server loads chunks incrementally as players
    // explore, never forcing dozens of candidate villages to fully assemble inside one tick.
    tasks.named<JavaExec>("runServer") {
        doFirst {
            if (seedProp.isPresent) {
                // GV-7's own harness sweep found a real gap here: a `run-<seed>` directory left
                // over from an earlier invocation (the same seed swept twice, or a standalone
                // debug run before a full sweep) carries a persisted world -- every chunk this
                // run's own `getChunk(FULL)` call touches, Minecraft loads straight from that
                // saved region file rather than regenerating, so `findGenerationPoint` (and every
                // grounded_villages hook downstream of it) never fires for an already-saved
                // village at all. The village itself still reports correctly (piece count, height
                // spread, water fraction all come from the same persisted data), but `tier`/
                // `ladderOutcome`/the rejection tally silently come back null -- this mod's own
                // registries are in-memory only, fresh per JVM, with nothing left to read back.
                // Confirmed live this ticket: seed 1 swept three times in the same directory
                // showed real tier/ladder data on the first (fresh) run and null on the second and
                // third (reused-world) runs, identical placement each time. Deleting first makes
                // every sweep genuinely "one fresh dedicated server per seed", matching what this
                // task's own docstring already claims.
                serverRunDir.deleteRecursively()
                serverRunDir.mkdirs()
                File(serverRunDir, "eula.txt").writeText("eula=true\n")
                File(serverRunDir, "server.properties").writeText(
                    """
                    level-seed=${seedProp.get()}
                    level-name=world
                    generate-structures=true
                    spawn-protection=0
                    view-distance=6
                    simulation-distance=6
                    online-mode=false
                    enable-command-block=false
                    max-players=0
                    server-port=${portProp.getOrElse("25565")}
                    motd=grounded_villages seed sweep
                    max-tick-time=300000
                    """.trimIndent() + "\n"
                )
            }
        }
        if (seedProp.isPresent && outputProp.isPresent) {
            systemProperty("groundedvillages.seedsweep.output", outputProp.get())
            systemProperty("groundedvillages.seedsweep.seed", seedProp.get())
        }
    }

    val harnessStatsTest = tasks.register<JavaExec>("harnessStatsTest") {
        group = "verification"
        description = "GV-10: hand-rolled unit tests for the seed-sweep statistics (no JUnit " +
            "dependency added for this ticket)."
        dependsOn("compileJava")
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass = "grounded_villages.harness.SeedSweepStatsTest"
    }

    tasks.named("check") {
        dependsOn(harnessStatsTest)
    }

    tasks.register("seedSweep") {
        group = "verification"
        description = "GV-10: sweeps N seeds for the first village near spawn, reporting height " +
            "spread/water fraction/piece count as a table and JSON. `-Pseeds=1,2,3` (ad hoc) or " +
            "-Pcount=N (first N of docs/baseline/seeds.txt, the fixed reproducible list)."

        doLast {
            val fixedSeeds = rootProject.file("docs/baseline/seeds.txt")
                .readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }

            val explicitSeeds = (project.findProperty("seeds") as String?)
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
            val seeds: List<String> = explicitSeeds ?: run {
                val count = (project.findProperty("count") as String?)?.toIntOrNull() ?: 10
                if (count > fixedSeeds.size) {
                    throw GradleException(
                        "seedSweep: -Pcount=$count exceeds docs/baseline/seeds.txt's " +
                            "${fixedSeeds.size} fixed seeds; pass -Pseeds=... explicitly for ad " +
                            "hoc seeds beyond the checked-in list."
                    )
                }
                fixedSeeds.take(count)
            }
            if (seeds.isEmpty()) {
                throw GradleException("seedSweep: no seeds to sweep")
            }

            val outDir = layout.buildDirectory.dir("seedsweep").get().asFile
            outDir.mkdirs()
            val perSeedFiles = mutableListOf<File>()

            seeds.forEachIndexed { index, seed ->
                val runDir = "build/seedsweep/run-$seed"
                val outputFile = File(outDir, "seed-$seed.json")
                // A fresh free port per seed: sibling worktrees run their own `runServer` on the
                // default 25565 independently, on the same machine, and did collide with it
                // during this ticket's own manual verification.
                val port = ServerSocket(0).use { it.localPort }
                logger.lifecycle("[seedSweep] seed $seed (${index + 1}/${seeds.size}) -> ${outputFile.path}")

                // Plain ProcessBuilder, not `project.exec {}` -- Gradle 9's Kotlin DSL dropped
                // that extension in favour of injected ExecOperations, and this task is an eager
                // `doLast` action anyway (org.gradle.configuration-cache=false), so a plain
                // subprocess is both simpler and version-proof.
                val process = ProcessBuilder(
                    "${rootProject.projectDir}/gradlew",
                    ":26.2-fabric:runServer",
                    "-Pgroundedvillages.seed=$seed",
                    "-Pgroundedvillages.output=${outputFile.absolutePath}",
                    "-Pgroundedvillages.runDir=$runDir",
                    "-Pgroundedvillages.port=$port",
                    "--console=plain"
                ).directory(rootProject.projectDir).inheritIO().start()
                val exitCode = process.waitFor()
                if (exitCode != 0 || !outputFile.exists()) {
                    throw GradleException(
                        "seedSweep: seed $seed failed (gradlew exit $exitCode, " +
                            "output written=${outputFile.exists()})"
                    )
                }
                perSeedFiles += outputFile
            }

            // Captured and re-printed via `logger.lifecycle`, not `.inheritIO()`: a task's own
            // ProcessBuilder inherits the Gradle *daemon's* file descriptors, not the `gradlew`
            // client's -- inheritIO'd output lands in the daemon's own log, never in this
            // invocation's own console (confirmed during this ticket's own manual verification:
            // the per-seed runServer output above is invisible to the caller for the same
            // reason, which is fine there since nothing parses it -- TEST-REQ-003's own "assert on
            // the file, not on log text" -- but the whole point of this final report is to be
            // seen).
            val reportProcess = ProcessBuilder(
                listOf("python3", "tools/seed_sweep_report.py") + perSeedFiles.map { it.absolutePath }
            ).directory(rootProject.projectDir).redirectErrorStream(true).start()
            val reportOutput = reportProcess.inputStream.bufferedReader().readText()
            val reportExitCode = reportProcess.waitFor()
            logger.lifecycle(reportOutput)
            if (reportExitCode != 0) {
                throw GradleException("seedSweep: tools/seed_sweep_report.py failed (exit $reportExitCode)")
            }
        }
    }

    // GV-8's own game test (ticket build item 3: "a game test on 26.2-fabric that a fixed seed
    // yields a fixed tier"). No GameTest-framework task exists anywhere in this project yet (see
    // this file's own seedSweep/harnessStatsTest comments, and docs/baseline/README.md "The
    // verdict: datagen-shaped, rejected" -- GV-10 already spiked and rejected the heavier
    // in-process bootstrap a full net.minecraft.gametest.framework integration would need); this
    // reuses GV-10's own proven live-server-per-seed mechanism instead of adding a second, parallel
    // headless-verification story, and proves the same property a GameTest would: real, live
    // world generation, exercising the actual mixin/tier-roll code path end to end, not a unit
    // test of TierRoller alone. Not wired into `check` -- like seedSweep, it boots a real
    // dedicated server (twice, here), too slow for the fast local/CI loop `just check` promises;
    // run explicitly (`./gradlew :26.2-fabric:tierGameTest`).
    tasks.register("tierGameTest") {
        group = "verification"
        description = "GV-8: proves a fixed seed rolls a fixed tier -- runs the dedicated server " +
            "twice against the same seed and diffs the tier field. `-Pseed=N` (default: the " +
            "first seed in docs/baseline/seeds.txt)."

        doLast {
            val fixedSeeds = rootProject.file("docs/baseline/seeds.txt")
                .readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
            val seed = (project.findProperty("seed") as String?) ?: fixedSeeds.first()

            val outDir = layout.buildDirectory.dir("tiergametest").get().asFile
            outDir.mkdirs()

            val tierPattern = Regex("\"tier\"\\s*:\\s*(null|\"([^\"]*)\")")

            val tiers = (1..2).map { run ->
                val runDir = "build/tiergametest/run-$run"
                val outputFile = File(outDir, "run-$run.json")
                // A fresh free port per run, same reasoning as seedSweep's own above.
                val port = ServerSocket(0).use { it.localPort }
                logger.lifecycle("[tierGameTest] seed $seed, run $run/2 -> ${outputFile.path}")

                val process = ProcessBuilder(
                    "${rootProject.projectDir}/gradlew",
                    ":26.2-fabric:runServer",
                    "-Pgroundedvillages.seed=$seed",
                    "-Pgroundedvillages.output=${outputFile.absolutePath}",
                    "-Pgroundedvillages.runDir=$runDir",
                    "-Pgroundedvillages.port=$port",
                    "--console=plain"
                ).directory(rootProject.projectDir).inheritIO().start()
                val exitCode = process.waitFor()
                if (exitCode != 0 || !outputFile.exists()) {
                    throw GradleException(
                        "tierGameTest: run $run failed (gradlew exit $exitCode, " +
                            "output written=${outputFile.exists()})"
                    )
                }

                val json = outputFile.readText()
                val match = tierPattern.find(json)
                    ?: throw GradleException("tierGameTest: run $run's output has no \"tier\" field: $json")
                match.groupValues[2].ifEmpty { null }
            }

            val first = tiers[0]
            val second = tiers[1]
            if (first == null || second == null) {
                throw GradleException(
                    "tierGameTest: seed $seed produced a null tier (tier.enabled=false, or no " +
                        "village found near spawn) -- both runs must roll a real tier to prove " +
                        "anything"
                )
            }
            if (first != second) {
                throw GradleException(
                    "tierGameTest: seed $seed rolled different tiers across two runs: " +
                        "'$first' vs '$second' -- TIER-REQ-001 (same seed + position -> same " +
                        "tier) is broken"
                )
            }
            logger.lifecycle("[tierGameTest] seed $seed -> tier '$first' (fixed across 2 runs)")
        }
    }
}
