package grounded_villages.harness;

import com.google.gson.GsonBuilder;
import grounded_villages.hook.PieceLadderRegistry;
import grounded_villages.hook.TierAssignmentRegistry;
import grounded_villages.tier.TierAssignment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * GV-10's headless seed-sweep harness core: finds the first village near spawn on a live,
 * already-booted {@link ServerLevel} (the {@code runServer}-per-seed fallback named in
 * {@code docs/spec/operations/testing.md} "Verification for the first ticket" -- the datagen-shaped
 * approach was spiked and rejected, see the ticket's own report for why), and measures it exactly
 * as {@code docs/spec/domains/site.md} §3 defines "height spread" and "water fraction": sampled via
 * {@link ChunkGenerator#getBaseHeight}/{@link ChunkGenerator#getBaseColumn} at
 * {@code OCEAN_FLOOR_WG}/{@code WORLD_SURFACE_WG} -- the same pre-placement column queries
 * {@code JigsawPlacement} itself uses -- not by reading blocks vanilla has already placed.
 */
public final class SeedSweepRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger("grounded_villages/seedsweep");

    /** Vanilla's own {@code /locate} default search radius, in chunks. */
    private static final int SEARCH_RADIUS_CHUNKS = 100;

    /** Sampling grid spacing, in blocks, across each piece's footprint. */
    private static final int SAMPLE_STEP = 4;

    private SeedSweepRunner() {
    }

    /**
     * Finds the nearest village to {@code server}'s overworld spawn, samples its footprint, and
     * writes the result as JSON to {@code outputFile}. Returns {@code null} (and writes nothing)
     * if no village is found within {@link #SEARCH_RADIUS_CHUNKS} chunks of spawn.
     */
    public static VillageSweepResult run(MinecraftServer server, long seed, Path outputFile) throws IOException {
        long startMillis = System.currentTimeMillis();
        ServerLevel level = server.overworld();
        BlockPos spawn = sharedSpawnPos(level);

        BlockPos nearest = level.findNearestMapStructure(StructureTags.VILLAGE, spawn, SEARCH_RADIUS_CHUNKS, false);
        if (nearest == null) {
            LOGGER.warn("seed {}: no village found within {} chunks of spawn", seed, SEARCH_RADIUS_CHUNKS);
            return null;
        }

        // Force the start chunk to fully generate so its StructureStart (and every piece's real,
        // terrain-adjusted bounding box) exists. The whole jigsaw graph resolves synchronously
        // within the start chunk's own STRUCTURE_STARTS generation step
        // (village-jigsaw-placement-1-20-1-to-26-2.md §A/§C), so one chunk is enough to see every
        // piece's bounding box, even pieces whose own chunk hasn't generated yet.
        //
        // The chunk coordinates are kept as plain ints (not read back off `startChunk` via
        // `.x()`/`.z()`) because `ChunkPos` is a plain class with public `x`/`z` fields pre-26.x
        // and a record with `x()`/`z()` accessor methods on 26.2 (GV-17, found compiling this
        // harness onto 1.21.8-fabric for the first time) -- no single field/method-call spelling
        // compiles on both, and `src/seedsweep/java` is outside Stonecutter's preprocessed
        // `src/main/java` tree (docs/loaders.md's own GV-12 finding), so a `//? if` conditional
        // here would never be processed either.
        int chunkX = nearest.getX() >> 4;
        int chunkZ = nearest.getZ() >> 4;
        ChunkPos startChunk = new ChunkPos(chunkX, chunkZ);
        level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);

        // Not getStructureWithPieceAt(BlockPos, TagKey): `nearest`'s own Y is whatever
        // JigsawStructure's (inert) `start_height` codec field says -- 0 for village_plains.json
        // (village-jigsaw-placement-1-20-1-to-26-2.md §A) -- so a 3D bounding-box-containment
        // check against it would almost always miss the real, terrain-adjusted piece boxes. A
        // chunk-scoped lookup sidesteps Y entirely: `nearest`'s chunk *is* the structure's start
        // chunk (StructureStart is only ever recorded on its own start chunk, not on every chunk
        // a piece merely passes through), so its own StructureStart is exactly what a
        // tag-filtered startsForStructure on that one chunk returns.
        StructureManager structureManager = level.structureManager();
        Registry<Structure> structureRegistry = structureManager.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        StructureStart start = structureManager
            .startsForStructure(startChunk, candidate -> structureRegistry.wrapAsHolder(candidate).is(StructureTags.VILLAGE))
            .stream()
            .filter(StructureStart::isValid)
            .findFirst()
            .orElse(null);
        if (start == null) {
            LOGGER.warn("seed {}: found a village position {} but no valid StructureStart there", seed, nearest);
            return null;
        }

        List<StructurePiece> pieces = start.getPieces();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();

        List<Integer> groundHeights = new ArrayList<>();
        int waterSamples = 0;
        int totalSamples = 0;
        for (StructurePiece piece : pieces) {
            BoundingBox box = piece.getBoundingBox();
            for (int x = box.minX(); x <= box.maxX(); x += SAMPLE_STEP) {
                for (int z = box.minZ(); z <= box.maxZ(); z += SAMPLE_STEP) {
                    int groundHeight = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState);
                    groundHeights.add(groundHeight);

                    int surfaceHeight = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
                    boolean isWater = surfaceHeight != groundHeight;
                    if (!isWater) {
                        // Belt and braces: WORLD_SURFACE_WG and OCEAN_FLOOR_WG can also disagree
                        // over other non-motion-blocking blocks (e.g. snow layers), not just
                        // water. Confirm with the actual block state -- site.md §3's "direct water
                        // detection" leg -- before counting it.
                        NoiseColumn column = generator.getBaseColumn(x, z, level, randomState);
                        isWater = column.getBlock(surfaceHeight).liquid();
                    }
                    if (isWater) {
                        waterSamples++;
                    }
                    totalSamples++;
                }
            }
        }

        if (totalSamples == 0) {
            LOGGER.warn("seed {}: village at {} had no pieces to sample", seed, nearest);
            return null;
        }

        double heightSpread = SeedSweepStats.heightSpread(groundHeights);
        double waterFraction = SeedSweepStats.waterFraction(waterSamples, totalSamples);
        long runtimeMillis = System.currentTimeMillis() - startMillis;

        // `var`, not a named type: `Registry.getKey` returns `ResourceLocation` pre-1.21.11 and
        // `Identifier` from 1.21.11 on (the same rename `contracts/platform-matrix.md`'s own
        // version-delta table tracks for the mixin) -- src/seedsweep/java is a plain extra
        // sourceSet, outside Stonecutter's preprocessed src/main/java tree (docs/loaders.md's own
        // GV-12 finding: only src/main/java is run through `stonecutterGenerate`), so a `//? if`
        // conditional here would never be processed and both branches would compile literally.
        // `var` sidesteps the class-name question entirely; the only use below is `.toString()`,
        // present on both classes (GV-17, found compiling this harness onto 1.21.8-fabric for the
        // first time -- every prior build of this file only ever targeted 26.2-fabric).
        var structureId = structureRegistry.getKey(start.getStructure());

        // GV-8: the mixin rolls the tier once, from JigsawStructureMixin, well before this
        // harness ever runs -- TierAssignmentRegistry is the "static last-assignment map keyed by
        // start chunk" the ticket names as the read-back mechanism, since nothing in this
        // after-the-fact StructureStart read carries the roll itself. null when tiers are
        // disabled (tier.enabled=false), matching GV-10's own pre-GV-8 default.
        TierAssignment tierAssignment = TierAssignmentRegistry.get(startChunk);
        String tier = tierAssignment == null ? null : tierAssignment.tier().name().toLowerCase(Locale.ROOT);

        // GV-7: the same read-back shape as TierAssignmentRegistry above, for the shrink/move/
        // vanilla ladder's own outcome and rejection tally -- JigsawPlacementMixin's own
        // gv$finishLadder records this once per village-tagged candidate, keyed by the same start
        // chunk. null when piece.enabled=false or the structure was never village-tagged.
        PieceLadderRegistry.PieceLadderResult ladderResult = PieceLadderRegistry.get(startChunk);
        String ladderOutcome = ladderResult == null ? null : ladderResult.outcome();
        int rejectedWater = ladderResult == null ? 0 : ladderResult.rejectedWater();
        int rejectedHeight = ladderResult == null ? 0 : ladderResult.rejectedHeight();

        VillageSweepResult result = new VillageSweepResult(
            seed,
            structureId == null ? "unknown" : structureId.toString(),
            nearest.getX(),
            nearest.getY(),
            nearest.getZ(),
            pieces.size(),
            heightSpread,
            waterFraction,
            totalSamples,
            tier,
            runtimeMillis,
            rejectedWater,
            rejectedHeight,
            ladderOutcome
        );

        Path absoluteOutput = outputFile.toAbsolutePath();
        if (absoluteOutput.getParent() != null) {
            Files.createDirectories(absoluteOutput.getParent());
        }
        Files.writeString(absoluteOutput, new GsonBuilder().setPrettyPrinting().create().toJson(result));

        LOGGER.info(
            "seed {}: village {} at {} -- {} pieces, height spread {}, water fraction {}, tier {}, "
                + "rejected {}w/{}h, ladder {} ({} ms)",
            seed, result.structureId, nearest, pieces.size(), heightSpread, waterFraction, tier,
            rejectedWater, rejectedHeight, ladderOutcome, runtimeMillis
        );
        return result;
    }

    /**
     * The world's own spawn position, resolved reflectively across a real API split GV-17 found
     * compiling this harness onto 1.21.8-fabric for the first time: {@code Level} exposes
     * {@code BlockPos getSharedSpawnPos()} pre-26.x and {@code LevelData.RespawnData
     * getRespawnData()} (whose own {@code .pos()} is the equivalent) from 26.2 on -- confirmed by
     * direct {@code javap} against both jars, neither method present on both versions. Reflection,
     * not a Stonecutter conditional, for the same reason the chunk-coordinate fix above gives:
     * {@code src/seedsweep/java} is outside Stonecutter's preprocessed tree
     * (docs/loaders.md). Mirrors {@code GroundedVillagesNeoForge#isDevelopment()}'s own
     * try-then-fall-back-reflectively shape for the same kind of unpreprocessed-directory drift.
     */
    private static BlockPos sharedSpawnPos(ServerLevel level) {
        try {
            return (BlockPos) ServerLevel.class.getMethod("getSharedSpawnPos").invoke(level);
        } catch (ReflectiveOperationException notPresent) {
            try {
                Object respawnData = ServerLevel.class.getMethod("getRespawnData").invoke(level);
                return (BlockPos) respawnData.getClass().getMethod("pos").invoke(respawnData);
            } catch (ReflectiveOperationException neitherPresent) {
                throw new IllegalStateException(
                    "SeedSweepRunner: neither Level#getSharedSpawnPos() nor Level#getRespawnData() "
                        + "resolved reflectively on this Minecraft version", neitherPresent);
            }
        }
    }
}
