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

// Mixin refmap generation is NOT wired yet (docs/spec/contracts/platform-matrix.md "Mappings",
// verified against neoforged/ModDevGradle's own LEGACY.md "Mixins" section, 2026-09-20): the
// `mixin { add(...); config(...) }` block plus the `org.spongepowered:mixin:...:processor`
// annotationProcessor makes MDG's `reobfJar` require an AP-generated
// `grounded_villages.refmap.json.mappings.tsrg`, which the AP only writes when a `@Mixin`-
// annotated class exists to process -- GV-2 ships zero (skeleton only, PIECE/SITE domains land in
// their own tickets). Verified live 2026-09-20: wiring the block with no `@Mixin` classes fails
// `reobfJar` with `FileNotFoundException` on that generated file. The mixin-config JSON stub is
// still referenced below via the jar manifest's `MixinConfigs` attribute (SpongePowered Mixin
// bootstraps itself via ModLauncher regardless, `contracts/platform-matrix.md` "Mixin support
// story", and loads a zero-mixin config as a no-op); the `mixin {}` block and its AP dependency
// return once a real `@Mixin` class lands (FIXME GV-15 tracks the Forge leg generally).

sourceSets.main {
    // docs/spec/04-architecture.md "Shape": one shared src/main/ tree, entrypoint stubs per
    // loader under src/<loader>/java, selected here by node.
    java.srcDir(rootProject.file("src/forge/java"))
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
