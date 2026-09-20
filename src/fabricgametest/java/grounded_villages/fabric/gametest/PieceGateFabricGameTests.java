package grounded_villages.fabric.gametest;

import grounded_villages.gametest.PieceGateScenarios;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Ticket GV-11: the Fabric leg of the per-loader game-test coverage
 * (`docs/spec/operations/testing.md` "Game tests" row, `TEST-REQ-004`). One thin
 * {@code @GameTest}-annotated method per {@code grounded_villages.gametest.PieceGateScenarios}
 * scenario -- Fabric API's own gametest module (`fabric-gametest-api-v1`) reflects every
 * {@code @GameTest}-annotated, public, non-static, {@code GameTestHelper}-taking declared method
 * off this class (confirmed live: {@code static} fails {@code TestAnnotationLocator}'s own
 * {@code validateMethod} with "must not be static" -- the fabric-specific inverse of vanilla's own
 * classic annotation, since Fabric Loader already instantiates this class for the entrypoint
 * itself and invokes each found method on that same instance) and bridges it into whatever the
 * running Minecraft version's own game-test registry actually needs (the classic
 * annotation-scanned system through 1.21.1, the rewritten {@code TestInstance}/{@code
 * TestFunctionLoader} registry from 26.2).
 *
 * <p>{@code structure = "fabric-gametest-api-v1:empty"} is Fabric API's own bundled, pre-built
 * empty platform (`data/fabric-gametest-api-v1/gametest/structure/empty.snbt`, confirmed present
 * in the resolved {@code fabric-gametest-api-v1} jar) -- every scenario places its own blocks
 * inside that bounds at runtime (`PieceGateScenarios`' own javadoc), matching this ticket's own
 * "a structure template ... with a small pool of water" build item.
 *
 * <p><b>Discovery, confirmed live</b>: {@code TestAnnotationLocator} does not scan the classpath
 * at large -- it reads {@code FabricLoader.getEntrypointContainers("fabric-gametest", ...)} and
 * reflects every {@code @GameTest}-annotated declared method off each entrypoint's own class
 * (confirmed by direct {@code javap}/constant-pool read of the resolved module jar, and by a
 * first, empty-handed live run: {@code fabric.mod.json}'s own {@code "fabric-gametest"}
 * entrypoint, naming this class, is required -- the same lazy, custom-entrypoint-key mechanism
 * Fabric's own "Data Generation" setup guide uses for a datagen-only class that similarly does not
 * exist in every source set/node). Fabric Loader only resolves a custom entrypoint key's class
 * list when something actually asks for it -- {@code fabric-gametest-api-v1}'s own runner does,
 * but only when {@code -Dfabric-api.gametest=true} is set (this ticket's own {@code runGameTest}
 * run config, 26.2-fabric only) -- so declaring this class unconditionally in the one shared
 * {@code fabric.mod.json} is safe even though the class itself does not exist on the other fabric
 * nodes' own compiled output.
 *
 * <p>Requires a public no-arg constructor (kept, deliberately, over the private-constructor/
 * utility-class shape every other class in this ticket uses): Fabric Loader instantiates every
 * custom entrypoint's own class to read {@code getEntrypoint().getClass()} off it.
 */
public final class PieceGateFabricGameTests {

    public PieceGateFabricGameTests() {
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", skyAccess = true)
    public void gv_waterFootprintIsRejected(GameTestHelper helper) {
        PieceGateScenarios.waterFootprintIsRejected(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", skyAccess = true)
    public void gv_dryFootprintIsAccepted(GameTestHelper helper) {
        PieceGateScenarios.dryFootprintIsAccepted(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", skyAccess = true)
    public void gv_rejectionLeavesNoDanglingConnector(GameTestHelper helper) {
        PieceGateScenarios.rejectionLeavesNoDanglingConnector(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", skyAccess = true)
    public void gv_acceptedCandidateIsCommittedAndUnmutated(GameTestHelper helper) {
        PieceGateScenarios.acceptedCandidateIsCommittedAndUnmutated(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", skyAccess = true)
    public void gv_ladderRollsShrinkAndRetryFromSyntheticCounts(GameTestHelper helper) {
        PieceGateScenarios.ladderRollsShrinkAndRetryFromSyntheticCounts(helper);
    }
}
