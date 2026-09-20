// GV-24: the one jar-name expression every build.<loader>.gradle.kts, `buildAndCollect`,
// `tools/jar_naming.py` (release_notes.py, docs/modrinth/targets.json, doctor.py's own
// `check_targets`) and `tools/test_doctor.py` must agree on --
// `docs/spec/operations/release.md`'s "Artifact naming follows
// `grounded_villages-<mc>-<loader>-<version>.jar`" line, itself adapted from REL-DEC-001's own
// `<mod>-<mc>-<loader>` version scheme.
//
// `node` is Stonecutter's own `sc.current.project` value, already exactly "<mc>-<loader>"
// (settings.gradle.kts's own `match()` helper: `version("$project-$loader", ...)`;
// stonecutter.gradle.kts's own `current.project.split('-', limit = 2)`) -- so this expression
// needs no separate mc/loader parameters, and a node a later ticket adds (GV-17) is covered
// automatically, with nothing here to edit.
//
// Placed in buildSrc's default package (no `package` declaration) so every root-level
// `build.<loader>.gradle.kts` -- each its own separately compiled Kotlin DSL script -- can call it
// with no import, the same way Gradle's own generated build-script accessors work.
fun gvJarFileName(modId: String, node: String, version: String): String = "$modId-$node-$version.jar"
