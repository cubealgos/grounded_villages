package grounded_villages.site;

/**
 * The one pure abstraction {@link SiteScorer} and {@link SiteSearch} sample terrain through --
 * two plain-int queries per column, no Minecraft type anywhere (`04-architecture.md`
 * {@code ARCH-DEC-002}: "pure functions over positions, heights, booleans"). The real
 * implementation ({@code grounded_villages.site.SiteStartHook}, the Minecraft-typed adapter a
 * loader entrypoint registers) wraps {@link grounded_villages.hook.TerrainSampler}; tests wrap a
 * synthetic height field instead -- neither needs a live {@code ChunkGenerator}.
 *
 * <p>Two heights rather than one column query mirrors `domains/site.md` §3 exactly: {@link
 * #groundHeight} is {@code OCEAN_FLOOR_WG} (true ground, sees through water), {@link
 * #surfaceHeight} is {@code WORLD_SURFACE_WG} (treats water as "surface") -- a column is water
 * wherever the two disagree ({@link SiteScorer}'s own javadoc explains why this is cheaper than a
 * direct block-state check).
 */
public interface HeightSampler {
    int groundHeight(int x, int z);

    int surfaceHeight(int x, int z);
}
