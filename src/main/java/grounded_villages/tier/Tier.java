package grounded_villages.tier;

/**
 * The four named size tiers (`docs/spec/domains/tiers.md` §3), rolled exactly once per village
 * instance ({@code TIER-REQ-001}). Ordered small to large, matching the enumeration table and
 * {@code grounded_villages.config.ConfigModel.TierBudget}/{@code TierWeights}' own
 * hamlet/village/town/city field order -- {@link TierRoller} relies on this ordering nowhere
 * except readability and the weighted-roll bucket order, not by ordinal lookup.
 */
public enum Tier {
    HAMLET,
    VILLAGE,
    TOWN,
    CITY
}
