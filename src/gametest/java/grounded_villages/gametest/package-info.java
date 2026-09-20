/**
 * Ticket GV-11 (`docs/spec/operations/testing.md` "Game tests" row, `TEST-REQ-004`): live,
 * loader-agnostic game-test scenarios proving the mixin hook fires correctly against a known,
 * pre-built scenario -- not the seed-driven statistics `SeedSweepRunner` (GV-10) already owns.
 *
 * <p>This package references only vanilla Minecraft types plus this mod's own shared production
 * classes ({@code grounded_villages.hook}, {@code grounded_villages.piece},
 * {@code grounded_villages.mixinsupport}) -- no Fabric, NeoForge or Forge import, so one file here
 * compiles under every loader's own {@code GameTestHelper}-taking test method without a per-loader
 * fork. The per-loader annotation/registration shim lives in {@code grounded_villages.fabric.gametest}
 * and {@code grounded_villages.neoforge.gametest} instead, each just a thin call into this
 * package's own {@link grounded_villages.gametest.PieceGateScenarios}.
 */
package grounded_villages.gametest;
