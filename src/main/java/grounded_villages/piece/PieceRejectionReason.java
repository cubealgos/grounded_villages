package grounded_villages.piece;

/**
 * Why {@link PieceGate#evaluate} rejected a candidate (`domains/pieces.md` §3 "Rejection
 * criteria") -- {@code NONE} for an accepted candidate. Kept as its own type, richer than {@code
 * grounded_villages.hook.PieceDecision}'s plain accept/reject, so {@link PieceRejectionHook} can
 * log a specific {@code HookDebug} reason and count rejections by reason (ticket build item 3:
 * "HookDebug lines per rejection reason") without changing {@code PieceDecision}'s own, wider
 * contract.
 */
public enum PieceRejectionReason {
    NONE,
    WATER,
    HEIGHT_DEVIATION
}
