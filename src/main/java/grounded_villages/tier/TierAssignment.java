package grounded_villages.tier;

/**
 * The result of one {@link TierRoller#roll} call: the tier a village instance rolled, and the
 * {@code maxDepth}/{@code max_distance_from_center} budget vanilla's own jigsaw assembly should
 * actually receive -- already {@code tier.performance_cap_multiplier}-clamped ({@code
 * TIER-REQ-005}) and 128-block-hard-cap-clamped ({@code TIER-REQ-004}, `domains/tiers.md`
 * "Spacing"), so the mixin write-back (`grounded_villages.mixin.village.JigsawPlacementMixin`)
 * never has to re-derive either bound. {@link #tier} is still the rolled tier even when its own
 * {@link #jigsawDepth} was clamped down (`TIER-FAIL-001`: "the village stays labelled and shaped
 * as its rolled tier, just capped").
 *
 * <p>Also the shape the seed-sweep harness prints (ticket build item 1's own "a {@code
 * TierAssignment} record the harness can print") -- {@code grounded_villages.harness}'s own
 * {@code VillageSweepResult.tier} is filled from {@link #tier}'s name, lower-cased, read back via
 * {@code grounded_villages.hook.TierAssignmentRegistry}.
 */
public record TierAssignment(Tier tier, int jigsawDepth, int maxDistance) {
}
