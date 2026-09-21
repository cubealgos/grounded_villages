/**
 * The size-tier roll (`docs/spec/domains/tiers.md`, GV-8): a pure Java model, no Minecraft,
 * Fabric, NeoForge or Forge import anywhere in this package (`04-architecture.md`
 * {@code ARCH-DEC-002} -- {@code TierRoller} is one of the four pure-logic units named there
 * alongside {@code SiteSelector}, {@code PieceGate} and {@code ConfigModel}). Depends only on
 * {@link grounded_villages.config.ConfigModel}'s own tier records, never the other direction.
 *
 * <p>Not public surface (`docs/spec/contracts/public-surface.md`: "the {@code
 * SiteSelector}/{@code PieceGate}/{@code TierRoller} internal class shapes") -- every type here is
 * package-private except {@link grounded_villages.tier.Tier} and
 * {@link grounded_villages.tier.TierAssignment}, which the mixin package
 * ({@code grounded_villages.mixin.village}) and the harness ({@code grounded_villages.harness})
 * both need to reference across package boundaries.
 */
package grounded_villages.tier;
