package grounded_villages.site;

/**
 * One candidate site's whole-footprint terrain measurement (`docs/spec/domains/site.md` §3):
 * height spread (the 90th-minus-10th-percentile of sampled ground heights at {@code
 * OCEAN_FLOOR_WG}) and water fraction (the share of sampled columns identified as water), plus
 * how many columns {@link SiteScorer#score} actually sampled -- {@link SiteScorer}'s own column
 * cap ({@code SITE-FAIL-003}) can make this fewer than a naive step-8 grid over the full radius
 * would sample, so a caller that wants the real cost reads it from here rather than recomputing
 * it.
 */
public record SiteScore(double heightSpread, double waterFraction, int sampleCount) {

    /**
     * {@code SITE-REQ-002}: a candidate qualifies only if both thresholds hold -- {@code <=}, not
     * {@code <}, since a site exactly at the configured ceiling is not itself "exceeding" it.
     */
    public boolean qualifies(int maxHeightSpread, double maxWaterFraction) {
        return heightSpread <= maxHeightSpread && waterFraction <= maxWaterFraction;
    }
}
