package grounded_villages.harness;

/**
 * One village's seed-sweep measurement (GV-10 acceptance criteria): start position, piece count,
 * height spread and water fraction under its footprint, and the tier if the mod has set one
 * ({@code docs/spec/domains/tiers.md} -- {@link #tier} is {@code null} when {@code tier.enabled}
 * is {@code false}, otherwise the rolled tier's lower-case name, e.g. {@code "hamlet"}, read back
 * via {@code grounded_villages.hook.TierAssignmentRegistry}, GV-8). Plain data holder, serialized
 * with Gson (already on the game classpath via Minecraft itself -- no new dependency).
 *
 * <p>GV-7 (ticket build item 3) adds the per-piece rejection tally and the shrink/move/vanilla
 * ladder's own outcome (`decisions/DEC-010-shrink-move-vanilla.md`), read back via {@code
 * grounded_villages.hook.PieceLadderRegistry} the same way {@link #tier} is -- {@link #ladderOutcome}
 * is {@code null} when {@code piece.enabled} is {@code false} or no ladder result was ever
 * recorded for this village's start chunk (e.g. it was not village-tagged), otherwise one of
 * {@code "unaffected"}, {@code "shrink"}, {@code "moved"}, {@code "vanilla"}.
 */
public final class VillageSweepResult {
    public final long seed;
    public final String structureId;
    public final int startX;
    public final int startY;
    public final int startZ;
    public final int pieceCount;
    public final double heightSpread;
    public final double waterFraction;
    public final int sampleCount;
    public final String tier;
    public final long runtimeMillis;
    public final int rejectedWater;
    public final int rejectedHeight;
    public final String ladderOutcome;

    public VillageSweepResult(
        long seed,
        String structureId,
        int startX,
        int startY,
        int startZ,
        int pieceCount,
        double heightSpread,
        double waterFraction,
        int sampleCount,
        String tier,
        long runtimeMillis,
        int rejectedWater,
        int rejectedHeight,
        String ladderOutcome
    ) {
        this.seed = seed;
        this.structureId = structureId;
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.pieceCount = pieceCount;
        this.heightSpread = heightSpread;
        this.waterFraction = waterFraction;
        this.sampleCount = sampleCount;
        this.tier = tier;
        this.runtimeMillis = runtimeMillis;
        this.rejectedWater = rejectedWater;
        this.rejectedHeight = rejectedHeight;
        this.ladderOutcome = ladderOutcome;
    }
}
