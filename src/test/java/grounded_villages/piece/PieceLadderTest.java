package grounded_villages.piece;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * `docs/spec/operations/testing.md` "Unit": {@link PieceLadder}'s own shrink/retry decision
 * (ticket GV-7 build item 4: "a shrink-triggering case; a move-triggering case") --
 * `decisions/DEC-010-shrink-move-vanilla.md` and `domains/pieces.md` {@code PIECE-REQ-006}/{@code
 * 007}, plus the {@code UNAFFECTED} carve-out {@link PieceLadder}'s own javadoc records for
 * Kevin's confirmation.
 */
final class PieceLadderTest {

    private static final int HAMLET_MINIMUM = 4;

    @Test
    void zeroRejectionsIsUnaffectedRegardlessOfSurvivorCount() {
        assertEquals(PieceLadder.Outcome.UNAFFECTED, PieceLadder.evaluate(0, 117, HAMLET_MINIMUM));
        assertEquals(PieceLadder.Outcome.UNAFFECTED, PieceLadder.evaluate(0, 0, HAMLET_MINIMUM));
    }

    @Test
    void someRejectionsButSurvivorsStillClearTheHamletMinimumShrinks() {
        // 30 pieces attempted, 4 rejected, 26 non-street survivors -- well past the minimum, so
        // the village keeps its assembled result, relabelled hamlet.
        assertEquals(PieceLadder.Outcome.SHRINK, PieceLadder.evaluate(4, 26, HAMLET_MINIMUM));
    }

    @Test
    void survivorsExactlyAtTheHamletMinimumShrinks() {
        // >=, not >, mirroring SiteScore#qualifies' own "<=, not <" boundary convention.
        assertEquals(PieceLadder.Outcome.SHRINK, PieceLadder.evaluate(1, HAMLET_MINIMUM, HAMLET_MINIMUM));
    }

    @Test
    void survivorsBelowTheHamletMinimumRetries() {
        // Most of the village fell to rejection: only 2 non-street pieces survived, short of the
        // hamlet floor of 4 -- the move-triggering case.
        assertEquals(PieceLadder.Outcome.RETRY, PieceLadder.evaluate(20, 2, HAMLET_MINIMUM));
    }

    @Test
    void zeroSurvivorsWithAnyRejectionRetries() {
        assertEquals(PieceLadder.Outcome.RETRY, PieceLadder.evaluate(1, 0, HAMLET_MINIMUM));
    }

    @Test
    void aHigherConfiguredMinimumCanTurnTheSameTallyIntoARetry() {
        // The same 26-survivor tally that shrinks against the default minimum (4) retries
        // against a server-configured minimum of 30.
        assertEquals(PieceLadder.Outcome.SHRINK, PieceLadder.evaluate(4, 26, 4));
        assertEquals(PieceLadder.Outcome.RETRY, PieceLadder.evaluate(4, 26, 30));
    }
}
