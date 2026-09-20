// Root Stonecutter build script (docs/spec/04-architecture.md "Shape"). `chiseledBuild` and
// `chiseledCheck` are NOT registered automatically by applying the plugin -- both must be
// registered here explicitly, per GV-2's acceptance criteria and `contracts/platform-matrix.md`
// "CI matrix".
//
// CORRECTION to the cited research (multi-loader-multi-version-mods-2026.md "Grounded Villages"
// §5, which quoted `stonecutter registerChiseled tasks.register(name, stonecutter.chiseled) {
// ofTask(...) }` as current for 0.9.8): that `chiseled`/`registerChiseled` API does not exist in
// the 0.9.8 jar (verified 2026-09-20 by decompiling
// ~/.gradle/caches/modules-2/files-2.1/dev.kikugie/stonecutter/0.9.8/**/stonecutter-0.9.8.jar --
// zero occurrences of the string "chiseled" anywhere in it) and the research's own cited source
// (`Trainguy9512/locomotion`) in fact pins Stonecutter `0.5.1`, an old release where that API
// still existed, not `0.9.8`. Current 0.9.8/v2 docs (`codeberg.org/stonecutter/docs`,
// `docs/wiki/v2/reference/gradle-api/project-controller.md` "Task Hooks" > "Aggregation")
// document the replacement: `stonecutter.tasks.named("<task>")` returns a lazy collection of
// that task across every registered node, used as a `dependsOn` below. Same two task names
// (`chiseledBuild`, `chiseledCheck`), same effect, real 0.9.8 API.
plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.1-fabric"

stonecutter parameters {
    val (version, loader) = current.project.split('-', limit = 2)

    // Makes version- and loader-specific properties from stonecutter.properties.toml apply.
    properties {
        tags(version, loader)
    }

    // Adds constants for Stonecutter preprocessor comments (`//? if fabric {...`), not used by
    // any shared source yet -- this ticket ships no mixin/logic code (GV-2 scope).
    constants {
        match(loader, "fabric", "neoforge", "forge")
    }
}

// Aggregate build task: every node's own `buildAndCollect` (a plain `Copy` of the jar, no tests
// -- multi-loader-multi-version-mods-2026.md "Grounded Villages" §5).
tasks.register("chiseledBuild") {
    group = "project"
    description = "Builds every version node's jar and collects it under build/libs/."
    dependsOn(stonecutter.tasks.named("buildAndCollect"))
}

// Aggregate verification task: every node's own `check`, run as its own earlier step
// (standards/tech/separate-verification-from-deployment.md) -- `chiseledBuild` alone runs no
// tests.
tasks.register("chiseledCheck") {
    group = "verification"
    description = "Runs `check` on every version node."
    dependsOn(stonecutter.tasks.named("check"))
}

// GV-11: per-loader game tests, wired into this aggregate for the two nodes ticket GV-11 itself
// is scoped to (26.2-fabric, 1.21.1-neoforge -- see build.neoforge.gradle.kts's own comment for
// why NeoForge runs on 1.21.1, not 26.2). Fabric is 26.2-fabric only, not every fabric node:
// confirmed live (`./gradlew :1.21.1-fabric:compileJava`) that the resolved
// `fabric-gametest-api-v1` module at 1.21.1's own `deps.fabric_api` pin has no
// `net.fabricmc.fabric.api.gametest.v1.GameTest` class at all (a newer addition, present only at
// 26.2's own module version) -- see build.fabric.gradle.kts's own comment on its node-conditional
// block for the full finding. Explicit task paths, not `stonecutter.tasks.named(...)`: that helper
// aggregates one task name across EVERY registered node uniformly, and no single game-test task
// name exists on every node here (the two Forge legs and every non-26.2 fabric node have none at
// all, GV-11's own scope).
tasks.register("chiseledGameTest") {
    group = "verification"
    description = "Runs GV-11's per-loader game tests on this ticket's two primary nodes " +
        "(26.2-fabric's runGameTest, 1.21.1-neoforge's runGameTestServer)."
    dependsOn(
        ":26.2-fabric:runGameTest",
        ":1.21.1-neoforge:runGameTestServer"
    )
}
