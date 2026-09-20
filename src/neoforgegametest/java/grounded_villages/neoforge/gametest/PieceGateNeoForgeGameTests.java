package grounded_villages.neoforge.gametest;

import grounded_villages.gametest.PieceGateScenarios;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;

/**
 * Ticket GV-11: the NeoForge leg of the per-loader game-test coverage
 * (`docs/spec/operations/testing.md` "Game tests" row, `TEST-REQ-004`).
 *
 * <p><b>Node scope, and why this is 1.21.1-neoforge, not 26.2-neoforge</b>: 26.2 shipped a full
 * rewrite of vanilla's own game-test framework (confirmed by direct {@code javap} read of the
 * 26.2 merged-deobf jar this ticket): the classic {@code @GameTest}/{@code @GameTestHolder}/
 * {@code RegisterGameTestsEvent.register(Class)} annotation-scanning API this file uses is gone
 * from {@code net.minecraft.gametest.framework} entirely, replaced by a registry-driven {@code
 * GameTestInstance}/{@code TestData}/{@code TestFunctionLoader} model. Fabric API's own
 * {@code fabric-gametest-api-v1} module bridges that rewrite transparently on 26.2-fabric --
 * {@code grounded_villages.fabric.gametest} is wired there for exactly this reason (its own
 * {@code v1.GameTest} annotation class is itself a newer addition, absent from the module
 * versions 1.20.1/1.21.1's own {@code fabric-api} pins resolve, confirmed live by this ticket) --
 * but NeoForge {@code 26.2.0.88}'s own {@code
 * RegisterGameTestsEvent} was rewritten around the same new registry model with no equivalent
 * annotation-scanning bridge shipped (confirmed: no {@code GameTestHolder} class and no
 * {@code register(Class)}/{@code register(Method)} overload anywhere in the {@code
 * neoforge-26.2.0.88-universal.jar}, unlike the {@code neoforge-21.1.251-universal.jar}, which
 * still carries both). Hand-rolling a {@code FunctionGameTestInstance} against that new,
 * sparsely-documented registry blind was judged too high-risk for this ticket's own scope and
 * time box; 1.21.1-neoforge keeps the classic, well-understood API this file actually uses, and is
 * itself a live NeoForge node on this mod's own platform matrix (`contracts/platform-matrix.md`),
 * not a version this mod drops. 26.2-neoforge coverage is deferred, flagged here for Kevin and for
 * whichever ticket next touches NeoForge's game-test wiring, rather than shipped unverified.
 *
 * <p>{@code template = "gametest/empty"} is this mod's own bundled, pre-built empty platform --
 * NeoForge does not bundle Fabric API's own equivalent, so this ticket ships a matching one under
 * this mod's own namespace instead of depending on a Fabric-only resource. Confirmed live, three
 * findings deep: {@code template} is a bare path, not a full {@code namespace:path} resource
 * location -- a first attempt with the namespace already included ({@code
 * "grounded_villages:gametest/empty"}) crashed the game test server with a {@code
 * ResourceLocationException} on the doubled, malformed id it produced. Once bare, vanilla's own
 * {@code StructureUtils#prepareTestStructure} still resolves the real structure at {@code
 * <namespace>:<declaring class's simple name, lowercased>.<template>} -- this class' own lowercased
 * name prefixed, dot-joined, ahead of the template path -- not just {@code <namespace>:<template>}
 * as the classic annotation's own field name alone would suggest (confirmed by the exact "Missing
 * test structure" id a second crash named). Third: once the file lived at the correctly-derived
 * path as {@code .snbt} text (the format `fabric-gametest-api-v1:empty`, its own Fabric equivalent,
 * ships as -- see `grounded_villages.fabric.gametest`'s own javadoc), 1.21.1's own {@code
 * StructureTemplateManager} still reported it missing; a plain gzipped binary {@code .nbt} (the
 * classic structure-block format, hand-written, no external tool) at the identical path resolved
 * immediately. The structure file therefore lives at
 * `src/main/resources/data/grounded_villages/structure/piecegateneoforgegametests.gametest/empty.nbt`
 * -- both the directory name (coupled to this class' own name by vanilla's own convention, not a
 * choice this ticket made) and the {@code .nbt} extension are load-bearing; renaming this class
 * means renaming that directory to match.
 */
// `bus = ...` deliberately omitted: deprecated-for-removal on this NeoForge version (confirmed
// live, `./gradlew :1.21.1-neoforge:compileJava`) now that RegisterGameTestsEvent's own
// IModBusEvent marker lets @EventBusSubscriber infer the mod bus without it.
@GameTestHolder("grounded_villages")
@EventBusSubscriber(modid = "grounded_villages")
public final class PieceGateNeoForgeGameTests {

    private PieceGateNeoForgeGameTests() {
    }

    @SubscribeEvent
    static void onRegisterGameTests(RegisterGameTestsEvent event) {
        event.register(PieceGateNeoForgeGameTests.class);
    }

    @GameTest(template = "gametest/empty", skyAccess = true)
    public static void gv_waterFootprintIsRejected(GameTestHelper helper) {
        PieceGateScenarios.waterFootprintIsRejected(helper);
    }

    @GameTest(template = "gametest/empty", skyAccess = true)
    public static void gv_dryFootprintIsAccepted(GameTestHelper helper) {
        PieceGateScenarios.dryFootprintIsAccepted(helper);
    }

    @GameTest(template = "gametest/empty", skyAccess = true)
    public static void gv_rejectionLeavesNoDanglingConnector(GameTestHelper helper) {
        PieceGateScenarios.rejectionLeavesNoDanglingConnector(helper);
    }

    @GameTest(template = "gametest/empty", skyAccess = true)
    public static void gv_acceptedCandidateIsCommittedAndUnmutated(GameTestHelper helper) {
        PieceGateScenarios.acceptedCandidateIsCommittedAndUnmutated(helper);
    }

    @GameTest(template = "gametest/empty", skyAccess = true)
    public static void gv_ladderRollsShrinkAndRetryFromSyntheticCounts(GameTestHelper helper) {
        PieceGateScenarios.ladderRollsShrinkAndRetryFromSyntheticCounts(helper);
    }
}
