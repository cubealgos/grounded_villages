package grounded_villages.hook;

import net.minecraft.world.level.ChunkPos;

/**
 * What a hook needs, independent of which of the two vanilla methods fired it
 * (04-architecture.md "Shape"): a {@link TerrainSampler} already built from the
 * {@code ChunkGenerator}/{@code RandomState}/{@code LevelHeightAccessor} in scope at the mixin
 * site, the chunk the structure is generating in, and the world seed the tier roll (GV-8) will
 * need. Not part of this mod's public surface (contracts/public-surface.md: "the exact hook
 * mechanism ... is not public surface") -- internal, shared only between
 * {@code grounded_villages.mixin} and the hook implementations GV-6/7/8 register.
 */
public record GenerationContext(TerrainSampler terrainSampler, ChunkPos chunkPos, long seed) {
}
