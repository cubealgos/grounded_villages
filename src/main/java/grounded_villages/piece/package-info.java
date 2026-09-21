/**
 * Per-piece rejection (`docs/spec/domains/pieces.md`, GV-7): a pure Java model, no Minecraft,
 * Fabric, NeoForge or Forge import anywhere in this package (`04-architecture.md`
 * {@code ARCH-DEC-002} -- {@code PieceGate} is one of the four pure-logic units named there
 * alongside {@code SiteSelector}, {@code TierRoller} and {@code ConfigModel}). {@link
 * grounded_villages.piece.PieceGate} samples a candidate piece's footprint for water and height
 * deviation over the same {@link grounded_villages.site.HeightSampler} {@code site} already
 * samples through (`domains/pieces.md` "Approach": "confirmed to generalise cleanly to per-piece
 * checks") -- deliberately reused rather than duplicated, since it is already exactly "two
 * plain-int queries per column, no Minecraft type anywhere". {@link
 * grounded_villages.piece.PieceLadder} is `decisions/DEC-010-shrink-move-vanilla.md`'s own
 * shrink/retry decision, kept pure and separate from the "move" and "vanilla" steps themselves
 * (both of which mean re-invoking vanilla's own jigsaw assembly, necessarily Minecraft-typed, so
 * they live in {@code grounded_villages.mixin.village.JigsawStructureMixin} instead). {@link
 * grounded_villages.piece.PieceRejectionHook} is the live {@link
 * grounded_villages.hook.VillagePieceHook} a loader entrypoint registers into {@link
 * grounded_villages.hook.HookRegistry}, the one Minecraft-typed class in this package.
 */
package grounded_villages.piece;
