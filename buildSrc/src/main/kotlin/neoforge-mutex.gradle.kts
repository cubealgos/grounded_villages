import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

// Two NeoForge nodes (1.21.1, 26.2) share this repository; without a mutex, Gradle's own
// `org.gradle.parallel=true` would let both run `createMinecraftArtifacts` (Minecraft
// decompilation/patching) at once, which is memory-hungry enough on a laptop to be worth
// serialising -- verbatim shape from `stonecutter-template-multiloader`'s own
// `buildSrc/src/main/kotlin/neoforge-mutex.gradle.kts`.
interface NeoForgeMutex : BuildService<BuildServiceParameters.None>

val mutex = gradle.sharedServices.registerIfAbsent("createMinecraftArtifactsMutex", NeoForgeMutex::class.java) {
    maxParallelUsages.set(1)
}

tasks.named { it == "createMinecraftArtifacts" }.configureEach {
    usesService(mutex)
}
