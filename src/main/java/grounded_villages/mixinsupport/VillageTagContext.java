package grounded_villages.mixinsupport;

/**
 * Bridges {@code JigsawStructureMixin}'s {@code #minecraft:village} tag test (run once per
 * {@code JigsawStructure.findGenerationPoint} call -- the only place in the vanilla call chain
 * with a {@code this} to test against the structure registry, decisions
 * DEC-007-village-tag-scope.md) to {@code JigsawPlacementMixin} and {@code PlacerMixin}, both
 * static methods one level down whose own parameters carry no structure reference back to the
 * {@code Structure}/{@code Holder} that owns them ({@code Structure.GenerationContext}'s record
 * fields, confirmed by {@code javap}, GV-5).
 *
 * <p><b>Deliberately not in {@code grounded_villages.mixin}</b> (moved there during GV-5's own
 * runtime proof, live-verified 2026-09-20): {@code grounded_villages.mixins.json} declares that
 * whole package tree as a Mixin-owned package, and SpongePowered Mixin refuses to let any class
 * load or be referenced from inside a declared mixin package except through its own
 * transformation process -- confirmed live: {@code IllegalClassLoadError:
 * grounded_villages.mixin.village.VillageTagContext is in a defined mixin package
 * grounded_villages.mixin.* owned by grounded_villages.mixins.json and cannot be referenced
 * directly}, thrown by {@code MixinProcessor.applyMixins} the first time a village-tagged
 * structure's generation reached {@code findGenerationPoint} on a real server. A plain support
 * class the mixins call into, however small, cannot live in the same package tree as the
 * {@code @Mixin} classes themselves.
 *
 * <p>Thread-local because world generation runs {@code findGenerationPoint -> addPieces ->
 * tryPlacingChildren} synchronously on one worker thread per structure-generation call, but
 * several such calls run concurrently across different chunks/threads. Each
 * {@code findGenerationPoint} call overwrites the previous value for its own thread before its
 * own {@code addPieces}/{@code tryPlacingChildren} calls run, so no explicit clear is needed.
 */
public final class VillageTagContext {
    private static final ThreadLocal<Boolean> IS_VILLAGE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private VillageTagContext() {
    }

    public static void set(boolean isVillage) {
        IS_VILLAGE.set(isVillage);
    }

    public static boolean isVillage() {
        return IS_VILLAGE.get();
    }
}
