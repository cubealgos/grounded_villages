package grounded_villages.piece;

/**
 * A candidate piece's own X/Z footprint -- no Y and no Minecraft {@code BoundingBox}
 * (`04-architecture.md` {@code ARCH-DEC-002}: this package references no Minecraft type), mirroring
 * {@code grounded_villages.site.SiteCoordinate}'s own reasoning for the {@code site} package. The
 * live adapter ({@link PieceRejectionHook}) is the one place that converts a real {@code
 * net.minecraft.world.level.levelgen.structure.BoundingBox} to this.
 */
public record PieceFootprint(int minX, int maxX, int minZ, int maxZ) {

    public PieceFootprint {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException(
                "footprint min must not exceed max: minX=" + minX + " maxX=" + maxX + " minZ=" + minZ + " maxZ=" + maxZ);
        }
    }
}
