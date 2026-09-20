---
schema_version: 1
id: 01M2ZZP07MJKD7YGDPAFEX0F3W
key: GV-3
type: chore
title: "Woodpecker CI: chiseledCheck/chiseledBuild running just check"
created_by: kevin
created_at: 2026-09-20T18:01:33Z
---

## Scope

Woodpecker CI running `just check`: `chiseledBuild` (all six nodes), `chiseledCheck` (unit tests,
lint), and game tests where they run headless. Separate steps per
`standards/tech/separate-verification-from-deployment.md` — `buildAndCollect` is a `Copy` task and
runs no tests on its own, so `chiseledCheck` is a second, real verification step, not folded into
the build step.

## Approach

Adapt `villager_voices`' `.woodpecker.yml`/`.ci/install-tools.sh` shape: one Woodpecker pipeline,
JDK image(s) covering 17/21/25 (either all three in one image or Gradle's
`org.gradle.toolchains.foojay-resolver-convention` auto-provisioning them), `just check` as the
single gate. Each wave adds jobs for its own new nodes only (`contracts/platform-matrix.md`'s CI
matrix row) — a Forge 1.20.1 job failing never blocks a Fabric/NeoForge release, since per-node
compilation means the nodes are independent.

## Acceptance criteria

- [ ] `.woodpecker.yml` runs `.ci/install-tools.sh` then `just check` on push/PR/manual, matching
  the sibling's trigger shape
- [ ] `chiseledCheck` is registered and actually invoked by `just check` (not just `chiseledBuild`)
- [ ] CI is green on a trivial diff against the six-node skeleton from GV-2
- [ ] the JDK floor(s) in the CI image match every row's toolchain in `contracts/platform-matrix.md`
- [ ] `PLATFORM-REQ-004`: a red job for one combination does not block another combination's own
  publish path (structural — no cross-node dependency in the pipeline)

## Constraints and prior findings

Blocked by GV-2 (needs real nodes to build and check). `operations/release.md` "CI" row: lint, unit
tests, the shared-source purity check, and game tests per shipped combination all belong in this
gate eventually — game tests per loader land with GV-11/GV-15 and are wired into this pipeline as
they land, not invented here ahead of the code they test.
