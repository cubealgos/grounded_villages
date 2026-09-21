package grounded_villages.piece;

/**
 * `decisions/DEC-010-shrink-move-vanilla.md`'s own shrink/retry decision, kept pure and separate
 * from the "move" (re-run {@code grounded_villages.site.SiteSearch#searchAlternative} for a
 * shifted site) and "vanilla" (place unchecked) steps themselves, both of which mean re-invoking
 * vanilla's own jigsaw assembly and so necessarily live in {@code
 * grounded_villages.mixin.village.JigsawStructureMixin} instead (`04-architecture.md`
 * {@code ARCH-DEC-002}: this package references no Minecraft type).
 *
 * <p><b>{@link Outcome#UNAFFECTED} is deliberately its own case, not folded into {@link
 * Outcome#SHRINK}</b>: `domains/pieces.md` {@code PIECE-REQ-006}/{@code PIECE-FAIL-001} name the
 * ladder as the response to a village that "loses enough pieces to rejection" -- a village that
 * lost <i>none</i> has nothing to shrink from, and DEC-010's own words ("keep the village as
 * generated") describe a no-op for that case, not a relabel. Read literally and without this
 * carve-out, {@code PIECE-REQ-006}'s threshold (surviving count {@code >=}
 * {@code hamlet_minimum_pieces}) would relabel <i>every</i> village "hamlet" regardless of tier
 * the moment piece rejection is enabled at all, since {@code hamlet_minimum_pieces}' own default
 * (4) is far below a typical village's real piece count (GV-10's own vanilla baseline: 50-230) --
 * clearly not the intent behind a mod whose whole other half (`domains/tiers.md`, GV-8) exists to
 * make tier labels meaningful. Flagged for Kevin to confirm alongside every other proposed default
 * this ticket carries forward, not decided silently: this reading is recorded here, in
 * {@code docs/spec/domains/pieces.md} `SS8` (added this ticket), and in {@code PieceLadderTest}.
 */
public final class PieceLadder {

    public enum Outcome {
        /** No piece was rejected for this village at all -- the ladder never engages. */
        UNAFFECTED,
        /** Some pieces were rejected, but the survivors still clear {@code hamlet_minimum_pieces}
         *  (`PIECE-REQ-006`): keep this attempt, relabelled {@code hamlet}. */
        SHRINK,
        /** Some pieces were rejected and the survivors fall short even of {@code
         *  hamlet_minimum_pieces} (`PIECE-REQ-007`): this attempt is not good enough on its own --
         *  move, then vanilla. */
        RETRY
    }

    private PieceLadder() {
    }

    /**
     * @param rejectedCount how many pieces (of any kind) {@link PieceGate} rejected during this
     *     one assembly attempt
     * @param nonStreetSurvivorCount how many non-street pieces {@link PieceGate} accepted
     * @param hamletMinimumPieces {@code tier.hamlet_minimum_pieces}
     */
    public static Outcome evaluate(int rejectedCount, int nonStreetSurvivorCount, int hamletMinimumPieces) {
        if (rejectedCount <= 0) {
            return Outcome.UNAFFECTED;
        }
        return nonStreetSurvivorCount >= hamletMinimumPieces ? Outcome.SHRINK : Outcome.RETRY;
    }
}
