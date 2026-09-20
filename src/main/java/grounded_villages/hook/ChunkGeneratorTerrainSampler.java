package grounded_villages.hook;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * {@link TerrainSampler} over a real {@link ChunkGenerator}. Package-private: constructed only
 * via {@link TerrainSampler#of}, since the pure SITE/PIECE logic GV-6/7 add is meant to depend on
 * the {@link TerrainSampler} interface, not this Minecraft-backed implementation
 * (04-architecture.md ARCH-DEC-002).
 */
final class ChunkGeneratorTerrainSampler implements TerrainSampler {
    private final ChunkGenerator chunkGenerator;
    private final RandomState randomState;
    private final LevelHeightAccessor heightAccessor;

    ChunkGeneratorTerrainSampler(ChunkGenerator chunkGenerator, RandomState randomState, LevelHeightAccessor heightAccessor) {
        this.chunkGenerator = chunkGenerator;
        this.randomState = randomState;
        this.heightAccessor = heightAccessor;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type) {
        return chunkGenerator.getBaseHeight(x, z, type, heightAccessor, randomState);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z) {
        return chunkGenerator.getBaseColumn(x, z, heightAccessor, randomState);
    }

    @Override
    public int getSeaLevel() {
        return chunkGenerator.getSeaLevel();
    }
}
