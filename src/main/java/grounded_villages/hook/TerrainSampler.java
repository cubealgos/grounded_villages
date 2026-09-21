package grounded_villages.hook;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * A pre-placement ground sampler, wrapping exactly the {@link ChunkGenerator} column queries
 * {@code village-jigsaw-placement-1-20-1-to-26-2.md} SS A/C names: {@code getBaseHeight} (ground
 * height at a heightmap type), {@code getBaseColumn} (the whole block-state column, the direct
 * way to detect water rather than infer it from a heightmap type), and {@code getSeaLevel}. Both
 * mixin injection points ({@code grounded_villages.mixin.village}) already have a
 * {@link ChunkGenerator}, a {@link RandomState}, and a {@link LevelHeightAccessor} in scope, so
 * building one of these threads in no new context (the ticket's own acceptance criterion).
 *
 * <p>Signatures confirmed identical across 1.20.1, 1.21.1 and 26.2 by direct {@code javap} read
 * of {@code ChunkGenerator} (GV-5) -- one implementation, no per-version variant needed here,
 * unlike the mixins themselves.
 */
public interface TerrainSampler {
    int getBaseHeight(int x, int z, Heightmap.Types type);

    NoiseColumn getBaseColumn(int x, int z);

    int getSeaLevel();

    static TerrainSampler of(ChunkGenerator chunkGenerator, RandomState randomState, LevelHeightAccessor heightAccessor) {
        return new ChunkGeneratorTerrainSampler(chunkGenerator, randomState, heightAccessor);
    }
}
