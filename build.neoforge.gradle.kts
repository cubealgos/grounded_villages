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
