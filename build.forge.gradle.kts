// Forge leg, 1.20.1 only -- MDG's `legacyforge` addon, same plugin family as the NeoForge legs
// rather than ForgeGradle (docs/spec/04-architecture.md ARCH-DEC-002,
// decisions/DEC-004-versions-and-toolchain.md). Riskiest leg in this ladder
// (`village-jigsaw-placement-1-20-1-to-26-2.md`); see FIXME GV-15 below if this node is not
// green.
plugins {
    id("net.neoforged.moddev.legacyforge") version "2.0.147"
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "${property("mod.id") as String}-forge"

// docs/spec/contracts/platform-matrix.md "Per-row toolchain": 1.20.1 -> Java 17, the one rung
// below fabric-loom's own >=1.20.5 -> 21 boundary -- Mojang ships Java 17 to end users on 1.20.1.
java {
    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(17)
    }
}

legacyForge {
    // Compiled against Mojang mappings; the runtime jar is reobfuscated to SRG transparently
    // (decisions/DEC-004-versions-and-toolchain.md) -- no `parchment {}` block, since this mod
    // does not use Parchment's parameter-name layer.
    version = "${sc.current.version}-${property("deps.forge")}"

    validateAccessTransformers = true

    mods {
        register(property("mod.id") as String) {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        register("client") {
            client()
            gameDirectory = rootProject.file("run")
        }
        register("server") {
            server()
            gameDirectory = rootProject.file("run")
        }
    }
}

// Mixin refmap generation, wired back in now that real `@Mixin` classes exist (GV-5,
// grounded_villages.mixin.village) -- GV-2 left this block out deliberately (see its own git
// history) because MDG legacyforge's `reobfJar` requires an AP-generated
// `grounded_villages.refmap.json.mappings.tsrg`, and the Mixin annotation processor only writes
// that file when there is at least one `@Mixin`-annotated class to process; wiring the block with
// zero mixins (GV-2's skeleton) failed `reobfJar` with a `FileNotFoundException` on that
// generated file, verified live at the time. `mixin {}` is a top-level extension registered by
// the `legacyforge` plugin (confirmed by decompiling `LegacyForgeModDevPlugin`,
// `moddev-gradle-2.0.147.jar`, GV-5: `project.extensions.create("mixin", MixinExtension.class,
// ...)`), not nested inside `legacyForge {}`.
mixin {
    add(sourceSets.main.get(), "grounded_villages.refmap.json")
    config("grounded_villages.mixins.json")
}

dependencies {
    // Confirmed present on `maven.minecraftforge.net`, HTTP 200 (docs/spec/contracts/platform-matrix.md
    // "Verified coordinates (GV-2)"); required for the Mixin AP to run at all on this leg.
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
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
    // pinned to match main's own resolved version (2.0.0 here) since any 1.7+ slf4j-api
    // satisfies the small Logger/LoggerFactory surface this mod calls.
    testRuntimeOnly("org.slf4j:slf4j-api:2.0.9")
}

sourceSets.main {
    // docs/spec/04-architecture.md "Shape": one shared src/main/ tree, entrypoint stubs per
    // loader under src/<loader>/java, selected here by node.
    java.srcDir(rootProject.file("src/forge/java"))
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

        filesMatching("META-INF/mods.toml") { expand(props) }

        // The Forge 1.20.1 leg is the one leg where the AP-generated compatibility level differs
        // from the other two loaders' own requiredJava (both Java 17 here, no drift).
        filesMatching("*.mixins.json") { expand("java" to "JAVA_17") }

        exclude("fabric.mod.json", "META-INF/neoforge.mods.toml")
    }

    val modId = project.property("mod.id") as String
    named<Jar>("jar") {
        manifest.attributes("MixinConfigs" to "grounded_villages.mixins.json")
        from(rootProject.file("LICENSE")) { rename { "$it-$modId" } }
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the mod jar and copies it to build/libs/{mod version}/"

        inputs.property("version", project.property("mod.version"))
        from(named<Jar>("reobfJar").flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    }
}
