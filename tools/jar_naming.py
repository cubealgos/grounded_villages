"""GV-24: the one jar-name expression `tools/release_notes.py`, `docs/modrinth/targets.json` and
`tools/doctor.py`'s `check_targets` all derive from -- `docs/spec/operations/release.md`'s own
"Artifact naming follows `grounded_villages-<mc>-<loader>-<version>.jar`" line, adapted from
REL-DEC-001's own `<mod>-<mc>-<loader>` version scheme. The Kotlin twin of this same expression is
`buildSrc/src/main/kotlin/GvJarNaming.kt`'s `gvJarFileName`, used by every `build.<loader>.gradle.
kts` and `buildAndCollect`.

`node` is a Stonecutter node name, already "<mc>-<loader>" (settings.gradle.kts's own `match()`
helper: `version("$project-$loader", ...)`; `docs/modrinth/targets.json`'s own `node` field) -- so
these two functions need no separate mc/loader parameters, and a node a later ticket adds (GV-17)
is covered with nothing here to edit.
"""
from __future__ import annotations

MOD_ID = "grounded_villages"


def jar_file_name(node: str, version: str, mod_id: str = MOD_ID) -> str:
    """`grounded_villages-<mc>-<loader>-<version>.jar`, e.g. `jar_file_name("1.21.1-fabric",
    "0.1.0")` -> `"grounded_villages-1.21.1-fabric-0.1.0.jar"`."""
    return f"{mod_id}-{node}-{version}.jar"


def jar_path(node: str, version: str, mod_id: str = MOD_ID) -> str:
    """The jar's path under Stonecutter's own `chiseledBuild` collection directory,
    `build/libs/<mod version>/` (every `build.<loader>.gradle.kts`'s own `buildAndCollect`)."""
    return f"build/libs/{version}/{jar_file_name(node, version, mod_id)}"
