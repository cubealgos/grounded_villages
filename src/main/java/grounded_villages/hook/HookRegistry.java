package grounded_villages.hook;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The extension point GV-6 (domains/site.md) and GV-7 (domains/pieces.md) plug their real
 * {@code SiteSelector}/{@code PieceGate} logic into (04-architecture.md "Shape"). Defaults accept
 * everything, so the mixins are behaviourally inert until a later ticket registers something
 * here -- GV-5 ships the hook family only, no scoring logic. World generation runs on multiple
 * worker threads concurrently, so both slots are a plain atomic reference rather than a mutable
 * field; a hook registered here must itself be safe to call from any generation thread.
 */
public final class HookRegistry {
    private static final AtomicReference<VillageStartHook> START_HOOK = new AtomicReference<>(VillageStartHook.ACCEPT_ALL);
    private static final AtomicReference<VillagePieceHook> PIECE_HOOK = new AtomicReference<>(VillagePieceHook.ACCEPT_ALL);

    private HookRegistry() {
    }

    public static VillageStartHook startHook() {
        return START_HOOK.get();
    }

    public static VillagePieceHook pieceHook() {
        return PIECE_HOOK.get();
    }

    public static void setStartHook(VillageStartHook hook) {
        START_HOOK.set(hook == null ? VillageStartHook.ACCEPT_ALL : hook);
    }

    public static void setPieceHook(VillagePieceHook hook) {
        PIECE_HOOK.set(hook == null ? VillagePieceHook.ACCEPT_ALL : hook);
    }
}
