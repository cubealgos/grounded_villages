// NeoForge leg, shared by the 1.21.1 and 26.2 nodes -- one build script per
// docs/spec/04-architecture.md "Shape". `neoforge-mutex` (buildSrc) serialises the two nodes'
// own Minecraft-artifact creation so they don't both decompile at once.
import java.net.ServerSocket

plugins {
    id("net.neoforged.moddev") version "2.0.147"
    id("neoforge-mutex")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "${property("mod.id") as String}-neoforge"

// docs/spec/contracts/platform-matrix.md "Per-row toolchain": 1.21.1 -> 21, 26.2 -> 25.
val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> throw GradleException("grounded_villages NeoForge nodes start at 1.21.1")
}

// GV-12: the NeoForge twin of build.fabric.gradle.kts's own `serverRunDir` -- the same
// -Pgroundedvillages.runDir= switch, read here once so both the `neoForge { runs }` block below
// and the harness's server.properties/eula.txt writer (further down, 26.2-neoforge only) always
// agree on the directory.
val serverRunDir: File = (project.findProperty("groundedvillages.runDir") as String?)
    ?.let { rootProject.file(it) }
    ?: rootProject.file("run")

neoForge {
    // Mojang mappings by default -- no mapping channel to select (decisions/DEC-004).
    version = property("deps.neoforge") as String

    mods {
        register(property("mod.id") as String) {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        register("client") {
            gameDirectory = rootProject.file("run")
            client()
        }
        register("server") {
            // GV-12's own seedSweep task points each per-seed `runServer` invocation at its own
            // run directory (`-Pgroundedvillages.runDir=...`), mirroring build.fabric.gradle.kts;
            // every other invocation keeps the previous, unconditional default (plain "run").
            gameDirectory = serverRunDir
            server()
        }
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

dependencies {
    // Plain JUnit 5/Jupiter for the shared src/test/java tree (GV-9), no Minecraft classpath
    // needed -- docs/spec/operations/testing.md "Unit" layer.
    testImplementation(platform("org.junit:junit-bom:${property("deps.junit")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // ConfigIo's own logger (mirroring the loader entrypoints' already-established
    // org.slf4j.Logger use) is on this node's main runtime classpath already, but unlike
    // fabric-loom, MDG does not extend `test`'s runtime classpath from `main`'s -- verified live,
    // GV-9: NoClassDefFoundError on org.slf4j.LoggerFactory at test runtime without this. Not
    // pinned to match main's own resolved version (MDG resolves a different one per node: 2.0.9
    // on 1.21.1, 2.0.17 on 26.2) since any 1.7+ slf4j-api satisfies the small
    // Logger/LoggerFactory surface this mod calls.
    testRuntimeOnly("org.slf4j:slf4j-api:2.0.9")
}

sourceSets.main {
    // docs/spec/04-architecture.md "Shape": one shared src/main/ tree, entrypoint stubs per
    // loader under src/<loader>/java, selected here by node.
    java.srcDir(rootProject.file("src/neoforge/java"))
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

        filesMatching("META-INF/neoforge.mods.toml") { expand(props) }

        val mixinJava = "JAVA_${requiredJava.majorVersion}"
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }

        exclude("fabric.mod.json", "META-INF/mods.toml")
    }

    val modId = project.property("mod.id") as String
    withType<Jar> {
        from(rootProject.file("LICENSE")) { rename { "$it-$modId" } }
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the mod jar and copies it to build/libs/{mod version}/"

        inputs.property("version", project.property("mod.version"))
        from(jar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    }
}

// GV-12: the headless seed-sweep harness's NeoForge twin, on the 26.2-neoforge node only --
// mirrors build.fabric.gradle.kts's own identically-named block (GV-10) as closely as the two
// loaders' own APIs allow, so the two harnesses stay obviously the same tool, not two
// independently-drifting ones (docs/loaders.md has the full account of what is shared vs.
// loader-specific). Kept in this single node-conditional block so 1.21.1-neoforge never sees
// `src/seedsweep/java` or `src/seedsweep-neoforge/java` at all.
if (sc.current.project == "26.2-neoforge") {
    sourceSets.main {
        // src/seedsweep/java is GV-10's own shared, loader-agnostic harness core (SeedSweepRunner
        // /SeedSweepStats/VillageSweepResult/SeedSweepStatsTest, zero Fabric or NeoForge import) --
        // reused verbatim, not duplicated. Its one Fabric-specific file (SeedSweepCommand.java,
        // `net.fabricmc.fabric.api...` imports) is excluded here; src/seedsweep-neoforge/java
        // supplies this node's own NeoForge-event-shaped equivalent instead (GV-12,
        // SeedSweepCommandNeoForge).
        java.srcDir(rootProject.file("src/seedsweep/java"))
        java.srcDir(rootProject.file("src/seedsweep-neoforge/java"))
        java.exclude("grounded_villages/harness/SeedSweepCommand.java")
    }

    val seedProp = providers.gradleProperty("groundedvillages.seed")
    val outputProp = providers.gradleProperty("groundedvillages.output")
    // Same reasoning as build.fabric.gradle.kts's own portProp: seedSweep hands each per-seed run
    // its own free port so sibling worktrees' own `runServer` invocations on the same machine
    // never collide with this one.
    val portProp = providers.gradleProperty("groundedvillages.port")

    // Same GV-7 watchdog fix as the Fabric leg's own runServer (docs/baseline/README.md "Two
    // operational findings"): the shrink/move/vanilla ladder's own "assemble again" steps can
    // push a single server tick past the vanilla 60s max-tick-time during a broad
    // findNearestMapStructure scan. Raised, not disabled, for the same reason given there.
    tasks.named<JavaExec>("runServer") {
        doFirst {
            if (seedProp.isPresent) {
                // Same GV-7 fix as the Fabric leg: a reused run directory serves a persisted
                // StructureStart straight from its region file, so this mod's own in-memory
                // registries (tier/ladder/rejection tallies) never get a chance to fire a second
                // time -- delete first so every sweep is genuinely one fresh dedicated server.
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
                    motd=grounded_villages seed sweep (neoforge)
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
        description = "GV-12: same hand-rolled SeedSweepStatsTest as the Fabric leg (GV-10), " +
            "re-run against this node's own compiled classes to prove the shared harness core " +
            "compiles and behaves identically off the NeoForge classpath."
        dependsOn("compileJava")
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass = "grounded_villages.harness.SeedSweepStatsTest"
    }

    tasks.named("check") {
        dependsOn(harnessStatsTest)
    }

    tasks.register("seedSweep") {
        group = "verification"
        description = "GV-12: the NeoForge twin of :26.2-fabric:seedSweep -- sweeps N seeds for " +
            "the first village near spawn on 26.2-neoforge, same reporting shape, same fixed " +
            "seed list. `-Pseeds=1,2,3` (ad hoc) or -Pcount=N (first N of " +
            "docs/baseline/seeds.txt)."

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
                val port = ServerSocket(0).use { it.localPort }
                logger.lifecycle("[seedSweep] (neoforge) seed $seed (${index + 1}/${seeds.size}) -> ${outputFile.path}")

                // Plain ProcessBuilder, same reasoning as build.fabric.gradle.kts's own seedSweep.
                val process = ProcessBuilder(
                    "${rootProject.projectDir}/gradlew",
                    ":26.2-neoforge:runServer",
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
}
