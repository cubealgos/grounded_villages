package grounded_villages.piece;

/**
 * {@link PieceGate#evaluate}'s own pure result: accepted, or rejected with a specific {@link
 * PieceRejectionReason}. Mirrors {@code grounded_villages.site.SiteScore#qualifies}'s
 * boolean-plus-reason shape, just with the reason spelled out rather than left for the caller to
 * re-derive.
 */
public record PieceVerdict(boolean accepted, PieceRejectionReason reason) {

    public static PieceVerdict accept() {
        return new PieceVerdict(true, PieceRejectionReason.NONE);
    }

    public static PieceVerdict rejectWater() {
        return new PieceVerdict(false, PieceRejectionReason.WATER);
    }

    public static PieceVerdict rejectHeight() {
        return new PieceVerdict(false, PieceRejectionReason.HEIGHT_DEVIATION);
    }
}
