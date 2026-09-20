package grounded_villages.hook;

import net.minecraft.world.level.ChunkPos;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The per-piece-rejection counterpart of {@link TierAssignmentRegistry} (ticket GV-7 build item
 * 3: "extend VillageSweepResult with rejected-piece counts and outcome"): the "static
 * last-assignment map keyed by start chunk" the seed-sweep harness ({@code
 * grounded_villages.harness}) reads once assembly finishes, since the harness only ever sees the
 * finished world, not the mixin calls that ran the shrink/move/vanilla ladder. {@code
 * grounded_villages.mixin.village.JigsawStructureMixin} records into this at the same point it
 * finishes the ladder for a village-tagged candidate.
 *
 * <p>Bounded LRU, same reasoning and same {@link TierAssignmentRegistry#MAX_ENTRIES}-equivalent
 * cap as that class' own javadoc: a diagnostic cache for one sweep run or a debugging session,
 * not a durable record.
 */
public final class PieceLadderRegistry {
    private static final int MAX_ENTRIES = 256;

    private static final Map<ChunkPos, PieceLadderResult> LAST_RESULT_BY_CHUNK =
        Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ChunkPos, PieceLadderResult> eldest) {
                return size() > MAX_ENTRIES;
            }
        });

    private PieceLadderRegistry() {
    }

    public static void record(ChunkPos startChunk, PieceLadderResult result) {
        if (startChunk != null && result != null) {
            LAST_RESULT_BY_CHUNK.put(startChunk, result);
        }
    }

    /** {@code null} when no ladder result was ever recorded for {@code startChunk}. */
    public static PieceLadderResult get(ChunkPos startChunk) {
        return LAST_RESULT_BY_CHUNK.get(startChunk);
    }

    /**
     * One village candidate's final ladder outcome (`decisions/DEC-010-shrink-move-vanilla.md`)
     * and rejection tally, exactly what {@code VillageSweepResult} (GV-10, extended this ticket)
     * prints. {@code outcome} is one of {@code "unaffected"}, {@code "shrink"}, {@code "moved"},
     * {@code "vanilla"} -- a plain {@code String} (not {@code
     * grounded_villages.piece.PieceLadder.Outcome} directly) purely so Gson (the harness' own
     * serializer, {@code SeedSweepRunner}) prints a lower-case label without a custom adapter,
     * matching {@code TierAssignmentRegistry}'s own {@code tier().name().toLowerCase(...)}
     * convention for the same reason.
     */
    public record PieceLadderResult(String outcome, int rejectedWater, int rejectedHeight, int acceptedNonStreet, int acceptedStreet) {
    }
}
