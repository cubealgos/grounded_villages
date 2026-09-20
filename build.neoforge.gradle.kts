// NeoForge leg, shared by the 1.21.1 and 26.2 nodes -- one build script per
// docs/spec/04-architecture.md "Shape". `neoforge-mutex` (buildSrc) serialises the two nodes'
// own Minecraft-artifact creation so they don't both decompile at once.
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
            gameDirectory = rootProject.file("run")
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

// GV-11: per-loader game tests, on 1.21.1-neoforge only -- NOT 26.2-neoforge. 26.2 shipped a full
// rewrite of vanilla's own game-test framework (net.minecraft.gametest.framework, confirmed by
// direct javap read of the 26.2 merged-deobf jar this ticket) that drops the classic
// @GameTest/@GameTestHolder/RegisterGameTestsEvent.register(Class) API this file wires below in
// favour of a registry-driven GameTestInstance/TestData/TestFunctionLoader model; NeoForge
// 26.2.0.88's own RegisterGameTestsEvent was rewritten to match (confirmed: no GameTestHolder
// class and no register(Class)/register(Method) overload anywhere in
// neoforge-26.2.0.88-universal.jar, unlike neoforge-21.1.251-universal.jar, which still carries
// both) with no annotation-scanning bridge shipped -- unlike Fabric API's own
// fabric-gametest-api-v1 module, which does bridge the same rewrite transparently on 26.2-fabric
// (grounded_villages.fabric.gametest, wired only there -- see build.fabric.gradle.kts's own
// comment: the same v1.GameTest annotation class is itself a newer addition, absent from the
// module versions 1.20.1/1.21.1's own fabric-api pins resolve). Hand-rolling a
// FunctionGameTestInstance against 26.2-neoforge's new, sparsely-documented registry blind was
// judged too high-risk for this ticket's own scope and time box.
// 1.21.1-neoforge is itself a live node on this mod's own platform matrix
// (contracts/platform-matrix.md), not a version this mod drops -- this is real coverage on a real
// shipped combination, not a downgrade to satisfy the letter of "NeoForge" alone.
// 26.2-neoforge coverage is deferred, flagged here for whichever ticket next touches NeoForge's
// game-test wiring, rather than shipped unverified.
if (sc.current.project == "1.21.1-neoforge") {
    sourceSets.main {
        // The loader-agnostic scenario bodies (grounded_villages.gametest, shared with every
        // Fabric node) plus this node's own @GameTest/@GameTestHolder/RegisterGameTestsEvent
        // wiring (grounded_villages.neoforge.gametest) -- kept out of the always-compiled
        // src/neoforge/java tree so 26.2-neoforge never sees either.
        java.srcDir(rootProject.file("src/gametest/java"))
        java.srcDir(rootProject.file("src/neoforgegametest/java"))
    }

    neoForge.runs.register("gameTestServer") {
        // MDG's own userdev runType key (confirmed present in neoforge-21.1.251's own
        // config.json: main class net.neoforged.fml.startup.GameTestServer,
        // neoforge.enableGameTest=true) -- generates the runGameTestServer task this ticket wires
        // into just gametest below, matching the sibling Fabric runGameTest task's own shape.
        type = "gameTestServer"
        // A dedicated directory, not the shared "run" the client/server runs above and a
        // person's own `just client`/`just server` session use -- confirmed live: running this
        // alongside `:26.2-fabric:runGameTest` (itself defaulting to "run" too,
        // build.fabric.gradle.kts's own `runConfigs.all`) under Gradle's own parallel execution
        // (`org.gradle.parallel=true`, gradle.properties) crashed with a
        // `DirectoryLock$LockException` on "run/world/session.lock", both trying to open the same
        // save at once.
        gameDirectory = rootProject.file("run-gametest-neoforge")
    }
}
