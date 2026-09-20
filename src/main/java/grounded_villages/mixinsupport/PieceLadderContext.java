package grounded_villages.mixinsupport;

import grounded_villages.hook.TerrainSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Optional;
import java.util.function.Function;

/**
 * Cross-mixin, per-thread state {@code grounded_villages.mixin.village}'s per-child rejection
 * (GV-7, `docs/spec/domains/pieces.md`) and shrink/move/vanilla ladder
 * (`decisions/DEC-010-shrink-move-vanilla.md`) share, for the same reason every other context in
 * this package exists: {@code findGenerationPoint -> addPieces -> tryPlacingChildren} runs
 * synchronously on one worker thread per structure-generation call, but many such calls run
 * concurrently across chunks/threads, and a plain support class cannot live inside the declared
 * {@code grounded_villages.mixin} package ({@link VillageTagContext}'s own javadoc has the
 * {@code IllegalClassLoadError} this was moved to avoid).
 *
 * <h2>The current candidate's decision</h2>
 *
 * {@code PlacerMixin}'s constructor-redirect (the real per-child interception point, its own
 * javadoc has the bytecode evidence) evaluates {@code PieceRejectionHook} exactly once per
 * candidate and stashes the verdict here; the three to four redirects downstream of it in the
 * very same bytecode sequence (both {@code addJunction} calls, {@code pieces.add}, and -- only if
 * vanilla's own depth check allows recursion -- the {@code placing} queue's own {@code add}) all
 * read the same stashed verdict and no-op instead of committing the piece when it is {@code
 * true}. No reset between candidates is needed: bytecode within {@code tryPlacingChildren} always
 * visits construct -> junctions -> commit in that exact order for whichever candidate reaches
 * construction at all (every earlier candidate that failed vanilla's own collision/bounds check
 * never reaches the constructor redirect in the first place), so each candidate's own decision is
 * consumed before the next one is ever set -- the same "no explicit clear needed" property {@link
 * VillageTagContext} and {@link TierAssignmentContext} already rely on.
 *
 * <h2>The ladder's per-attempt accumulator</h2>
 *
 * {@code JigsawPlacementMixin}'s own wrapped {@code Structure.GenerationStub} consumer (the
 * ladder's "assemble" step -- see that class' own javadoc for why the wrap happens at the {@code
 * GenerationStub} constructor, not at the call to {@code addPieces} itself) opens one {@link
 * Accumulator} per attempt via {@link #beginAttempt()}, lets that one attempt's real, synchronous
 * placement run (every {@code PieceRejectionHook} call for this attempt records into it), then
 * reads it back via {@link #endAttempt()} to decide shrink/move/vanilla ({@code
 * grounded_villages.piece.PieceLadder}'s own pure decision).
 *
 * <h2>The retry handle</h2>
 *
 * {@link RetryHandle} bundles everything {@code JigsawPlacementMixin}'s {@code
 * GenerationStub}-constructor redirect needs to run the "move" and "vanilla" steps later, when
 * the wrapped consumer it built is finally invoked (by vanilla's own {@code Structure.generate},
 * possibly after this thread's own {@code addPieces} call frame has already returned) --
 * {@code gv$onAddPieces} builds and stores one via {@link #setRetryHandle} while every parameter
 * it needs is still directly in scope as a method parameter; the constructor redirect reads it
 * back once, synchronously, within the very same {@code addPieces} call (see {@code
 * JigsawPlacementMixin}'s own javadoc for why capturing it into a local right there, rather than
 * re-reading the thread-local later inside the wrapped consumer itself, is what makes this safe
 * regardless of exactly when vanilla invokes that consumer).
 *
 * <h2>Suppressing the wrap for a retry's own nested call</h2>
 *
 * The "move" and "vanilla" steps both re-invoke {@code JigsawPlacement.addPieces} themselves
 * (via {@link RetryHandle#retry()}) to get a second/third attempt's own real consumer -- but that
 * nested call would otherwise trigger the very same {@code GenerationStub}-constructor redirect
 * again, wrapping it in another full ladder and recursing. {@link #setSuppressWrap} is held while
 * making that nested call so the redirect passes the nested {@code GenerationStub} through
 * unwrapped, and the ladder itself invokes that raw consumer exactly once, manually.
 *
 * <h2>Bypass</h2>
 *
 * {@code DEC-010} step 3 ("vanilla"): the ladder's own final fallback attempt must place the
 * village exactly as vanilla would -- "no site check, no piece check" -- so {@link #setBypass}
 * tells both {@code JigsawPlacementMixin}'s site hook and {@code PlacerMixin}'s piece hook to
 * skip evaluation entirely for the one nested {@code addPieces} call made with it set.
 */
public final class PieceLadderContext {
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Boolean> SUPPRESS_WRAP = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Boolean> CURRENT_CANDIDATE_REJECTED = new ThreadLocal<>();
    private static final ThreadLocal<Accumulator> ACCUMULATOR = new ThreadLocal<>();
    private static final ThreadLocal<TerrainSampler> SAMPLER = new ThreadLocal<>();
    private static final ThreadLocal<RetryHandle> RETRY_HANDLE = new ThreadLocal<>();

    private PieceLadderContext() {
    }

    public static void setBypass(boolean bypass) {
        BYPASS.set(bypass);
    }

    public static boolean isBypass() {
        return BYPASS.get();
    }

    public static void setSuppressWrap(boolean suppress) {
        SUPPRESS_WRAP.set(suppress);
    }

    public static boolean isSuppressWrap() {
        return SUPPRESS_WRAP.get();
    }

    /** The one {@link TerrainSampler} {@code PlacerMixin}'s own {@code gv$prepare} builds once
     *  per {@code tryPlacingChildren} call and every candidate in that call reuses; {@code null}
     *  when this call should not be evaluated at all (see {@code PlacerMixin#gv$prepare}'s own
     *  javadoc for the three reasons). */
    public static void setSampler(TerrainSampler sampler) {
        SAMPLER.set(sampler);
    }

    public static TerrainSampler getSampler() {
        return SAMPLER.get();
    }

    /** Set by the constructor-redirect for the candidate currently mid-commit on this thread. */
    public static void setCurrentCandidateRejected(boolean rejected) {
        CURRENT_CANDIDATE_REJECTED.set(rejected);
    }

    /** {@code false} (accept) when nothing was ever set -- fails open, never silently rejects. */
    public static boolean isCurrentCandidateRejected() {
        Boolean value = CURRENT_CANDIDATE_REJECTED.get();
        return value != null && value;
    }

    public static void setRetryHandle(RetryHandle handle) {
        RETRY_HANDLE.set(handle);
    }

    public static RetryHandle getRetryHandle() {
        return RETRY_HANDLE.get();
    }

    public static void beginAttempt() {
        ACCUMULATOR.set(new Accumulator());
    }

    /** Ends the current attempt, returning what it accumulated (never {@code null}). */
    public static Accumulator endAttempt() {
        Accumulator accumulator = ACCUMULATOR.get();
        ACCUMULATOR.remove();
        return accumulator == null ? new Accumulator() : accumulator;
    }

    /** A no-op outside an open attempt (e.g. {@code piece.enabled=false}, no ladder wrap at all). */
    public static void recordAccept(boolean street) {
        Accumulator accumulator = ACCUMULATOR.get();
        if (accumulator != null) {
            if (street) {
                accumulator.acceptedStreet++;
            } else {
                accumulator.acceptedNonStreet++;
            }
        }
    }

    public static void recordReject(boolean street, grounded_villages.piece.PieceRejectionReason reason) {
        Accumulator accumulator = ACCUMULATOR.get();
        if (accumulator != null) {
            if (reason == grounded_villages.piece.PieceRejectionReason.WATER) {
                accumulator.rejectedWater++;
            } else if (reason == grounded_villages.piece.PieceRejectionReason.HEIGHT_DEVIATION) {
                accumulator.rejectedHeight++;
            }
            if (street) {
                accumulator.rejectedStreet++;
            } else {
                accumulator.rejectedNonStreet++;
            }
        }
    }

    public static final class Accumulator {
        public int acceptedNonStreet;
        public int acceptedStreet;
        public int rejectedNonStreet;
        public int rejectedStreet;
        public int rejectedWater;
        public int rejectedHeight;

        public int totalRejected() {
            return rejectedNonStreet + rejectedStreet;
        }
    }

    /**
     * @param context the same {@code Structure.GenerationContext} every attempt (original, moved,
     *     vanilla-fallback) shares -- one seeded {@code random()}, one chunk position
     * @param radius the whole-village sampling radius (the tier's effective max distance) {@code
     *     SiteSearch#searchAlternative} needs for the "move" step
     * @param retry re-invokes {@code JigsawPlacement.addPieces} at a candidate {@code BlockPos},
     *     with every other parameter fixed to what this village candidate actually rolled --
     *     built once, while those parameters are all still in scope as {@code gv$onAddPieces}'s
     *     own method parameters
     */
    public record RetryHandle(Structure.GenerationContext context, int radius, Function<BlockPos, Optional<Structure.GenerationStub>> retry) {
    }
}
