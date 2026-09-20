package grounded_villages.gametest;

import grounded_villages.hook.TerrainSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A {@link TerrainSampler} over a game test's own live {@link ServerLevel}, backed by a direct
 * column scan of real block state rather than {@link ServerLevel#getHeight}'s own tracked
 * heightmaps -- the "synthetic {@code TerrainSampler} over the test level" ticket GV-11's own
 * build item names.
 *
 * <p><b>Why a scan, not {@code getHeight}, confirmed live, two findings deep</b>: {@code
 * grounded_villages.piece.PieceRejectionHook} always asks for {@link Heightmap.Types#OCEAN_FLOOR_WG}/
 * {@link Heightmap.Types#WORLD_SURFACE_WG} -- transient heightmaps vanilla populates only while a
 * chunk is actively generating ({@code Heightmap.Types#keepAfterWorldgen} is {@code false} for
 * both); a game test's own chunk is placed via {@code StructureTemplate}, never generated through
 * that pipeline, so a first diagnostic run read back a fixed, placement-unrelated value from both
 * raw {@code _WG} types regardless of blocks this package's own scenarios had just placed.
 * Substituting the persisted, block-maintained {@link Heightmap.Types#OCEAN_FLOOR}/{@link
 * Heightmap.Types#WORLD_SURFACE} counterparts (identical height predicate, kept live-updated as
 * blocks change) did not fix it either -- a second diagnostic run read back the test
 * <i>structure's own bounding-box roof</i> instead ({@code ground=-48 surface=-48} for a structure
 * whose own top layer was exactly Y -48, regardless of a confirmed-present water block at Y -55
 * one block above the confirmed-present stone floor at Y -56): {@code GameTest}'s own default
 * {@code skyAccess=false} caps the whole test structure with an invisible barrier ceiling at its
 * own top layer to simulate "indoors", and that ceiling -- solid, non-fluid, so it satisfies
 * <i>both</i> the ground and the surface predicate identically -- was the first non-air block
 * either heightmap variant found scanning down from the world's own real top, masking every block
 * this package's own scenarios placed below it. Both loaders' own {@code @GameTest} annotations
 * set {@code skyAccess = true} for exactly this reason (confirmed live: doing so alone would have
 * been enough to fix {@code getHeight} too) -- kept alongside the direct scan below regardless,
 * since a scan depends on no heightmap variant's own maintenance behaviour on a game-test chunk at
 * all, reading the one thing unambiguously real here: the block states this package's own
 * scenarios placed.
 *
 * <p><b>Fixed scan bounds, not {@code LevelHeightAccessor}'s own min/max-Y accessors</b>:
 * confirmed live that those accessors were themselves renamed at some boundary between 1.21.1 and
 * 26.2 ({@code getMinBuildHeight}/{@code getMaxBuildHeight} vs {@code getMinY}/{@code getMaxY}) --
 * unlike {@code grounded_villages.mixin.village}, this package is not part of Stonecutter's own
 * per-node preprocessed tree (confirmed live: a {@code //? if} conditional here compiled
 * unconditionally, the same on every node, since {@code src/gametest/java} is wired as a plain
 * extra {@code sourceSets.main.java.srcDir}, not through {@code stonecutterGenerate}'s own per-node
 * copy the way {@code src/main/java} is), so no preprocessor conditional applies here at all.
 * {@link #SCAN_TOP}/{@link #SCAN_BOTTOM} are deliberately generous fixed literals instead --
 * every Minecraft version this mod targets keeps the overworld within [-64, 320], and this
 * package's own scenarios always place their blocks well inside that band regardless of where a
 * game test's own structure happens to spawn.
 */
final class LiveLevelTerrainSampler implements TerrainSampler {
    private static final int SCAN_TOP = 320;
    private static final int SCAN_BOTTOM = -64;

    private final ServerLevel level;

    LiveLevelTerrainSampler(ServerLevel level) {
        this.level = level;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type) {
        boolean ignoreFluids = type == Heightmap.Types.OCEAN_FLOOR || type == Heightmap.Types.OCEAN_FLOOR_WG;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = SCAN_TOP; y >= SCAN_BOTTOM; y--) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            boolean counts = !state.isAir() && (!ignoreFluids || state.getFluidState().isEmpty());
            if (counts) {
                return y + 1;
            }
        }
        return SCAN_BOTTOM;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z) {
        throw new UnsupportedOperationException(
                "LiveLevelTerrainSampler backs only PieceGate's OCEAN_FLOOR_WG/WORLD_SURFACE_WG height "
                        + "queries (see this class' own javadoc); no GV-11 scenario calls getBaseColumn.");
    }

    @Override
    public int getSeaLevel() {
        return level.getSeaLevel();
    }
}
