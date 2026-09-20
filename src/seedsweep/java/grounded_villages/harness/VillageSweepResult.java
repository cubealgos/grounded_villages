package grounded_villages.harness;

/**
 * One village's seed-sweep measurement (GV-10 acceptance criteria): start position, piece count,
 * height spread and water fraction under its footprint, and the tier if the mod has set one
 * ({@code docs/spec/domains/tiers.md} -- not implemented as of this ticket, so {@link #tier} is
 * always {@code null} today; GV-8 fills it in). Plain data holder, serialized with Gson (already
 * on the game classpath via Minecraft itself -- no new dependency).
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
        long runtimeMillis
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
    }
}
