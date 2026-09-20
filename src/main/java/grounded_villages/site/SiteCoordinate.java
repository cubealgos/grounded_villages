package grounded_villages.site;

/**
 * A plain X/Z position, no Y and no Minecraft {@code BlockPos} (`04-architecture.md`
 * {@code ARCH-DEC-002}: this package references no Minecraft type). {@link SiteSearch} only ever
 * decides X/Z -- the shifted start's Y is vanilla's own {@code project_start_to_heightmap}
 * re-projection to do, or the Minecraft-typed adapter's fallback if that field is ever absent
 * (`grounded_villages.mixin.village.JigsawPlacementMixin`'s own javadoc), never this package's.
 */
public record SiteCoordinate(int x, int z) {
}
