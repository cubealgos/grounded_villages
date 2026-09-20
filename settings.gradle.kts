// Six Stonecutter version nodes, one repository (docs/spec/04-architecture.md ARCH-DEC-002,
// decisions/DEC-004-versions-and-toolchain.md): the version/loader axis is entirely Stonecutter's
// own `match()` helper below, quoted from `stonecutter-template-multiloader`'s own
// settings.gradle.kts (multi-loader-multi-version-mods-2026.md "Grounded Villages" §1) and
// extended with the Forge 1.20.1 leg this mod's ladder adds beyond that template.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    // dev.kikugie.stonecutter 0.9.8 -- verified present on the Gradle Plugin Portal 2026-09-20
    // (docs/spec/contracts/platform-matrix.md).
    id("dev.kikugie.stonecutter") version "0.9.8"

    // Cross-compat shim so one `build.fabric.gradle.kts` covers both the pre-26.1 AccessWidener
    // loom major and the 26.1+ ClassTweaker loom major (26.2 is the one node past that boundary)
    // -- verified present at maven.kikugie.dev 2026-09-20.
    id("dev.kikugie.loom-back-compat") version "0.4.2"

    // Auto-provisions Java 17/21/25 into ~/.gradle/jdks without sudo (GV-2 ticket instructions):
    // no other JDK than the one running this daemon is installed on this machine.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        /** Creates one subproject per (project, loader) pair at `versions/<project>-<loader>`,
         *  each wired to `build.<loader>.gradle.kts` -- verbatim shape from the multiloader
         *  template, `multi-loader-multi-version-mods-2026.md` "Grounded Villages" §1. */
        fun match(project: String, vararg loaders: String, version: String = project) {
            for (loader in loaders) version("$project-$loader", version).buildscript("build.$loader.gradle.kts")
        }

        // decisions/DEC-004-versions-and-toolchain.md: Waves 1-2, six nodes.
        match("1.20.1", "fabric", "forge")
        match("1.21.1", "fabric", "neoforge")
        match("26.2", "fabric", "neoforge")
        vcsVersion = "1.21.1-fabric"
    }
}

rootProject.name = "grounded_villages"
