package grounded_villages.hook;

/**
 * What {@link VillagePieceHook#onChild} may decide for a proposed piece (domains/pieces.md):
 * accept it (vanilla places it) or reject it (vanilla does not). No "shift" option here --
 * per-piece rejection is a flat accept/reject, unlike the site-level {@link StartDecision}
 * (domains/pieces.md SS3 "Water in footprint": "not a tunable fraction").
 */
public enum PieceDecision {
    ACCEPT,
    REJECT
}
