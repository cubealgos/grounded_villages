package grounded_villages.hook;

import grounded_villages.tier.TierAssignment;
import net.minecraft.world.level.ChunkPos;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The "static last-assignment map keyed by start chunk" GV-8's own ticket names as the way for
 * the seed-sweep harness ({@code grounded_villages.harness}) to read the tier a village actually
 * rolled, since the harness only ever sees the finished world, not the mixin call that rolled it.
 * {@code grounded_villages.mixin.village.JigsawStructureMixin} records into this at the same
 * point it rolls the tier ({@link grounded_villages.mixinsupport.TierAssignmentContext}'s own
 * cross-mixin handoff, plus this one for anything outside the mixin call stack).
 *
 * <p>Bounded, not a plain unbounded map: a long-running real server (not just one harness sweep)
 * calls {@code findGenerationPoint} for every village-tagged structure-generation attempt across
 * however much of the map ever gets explored, including candidates that do not end up producing a
 * visible village -- an unbounded map here would be a slow memory leak over a long session. A
 * small LRU (eviction order = access order) is more than enough for the harness's own
 * one-lookup-per-sweep use and for interactive debugging, without accumulating forever.
 */
public final class TierAssignmentRegistry {
    /** Generous for one sweep run or a debugging session; not a meaningful bound on how many
     *  villages a real server may ever generate over its lifetime -- this is a diagnostic cache,
     *  not a durable record (`docs/spec/04-architecture.md` "Placement only" -- no world state of
     *  this mod's own is authoritative here). */
    private static final int MAX_ENTRIES = 256;

    private static final Map<ChunkPos, TierAssignment> LAST_ASSIGNMENT_BY_CHUNK =
        Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ChunkPos, TierAssignment> eldest) {
                return size() > MAX_ENTRIES;
            }
        });

    private TierAssignmentRegistry() {
    }

    public static void record(ChunkPos startChunk, TierAssignment assignment) {
        if (startChunk != null && assignment != null) {
            LAST_ASSIGNMENT_BY_CHUNK.put(startChunk, assignment);
        }
    }

    /** {@code null} when no roll was ever recorded for {@code startChunk} (tiers disabled, the
     *  structure was not village-tagged, or the entry has since been evicted). */
    public static TierAssignment get(ChunkPos startChunk) {
        return LAST_ASSIGNMENT_BY_CHUNK.get(startChunk);
    }
}
