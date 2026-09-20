package grounded_villages.hook;

import net.minecraft.core.BlockPos;

/**
 * What {@link VillageStartHook#onStart} may decide for a candidate village start position
 * (domains/site.md): keep it as vanilla proposed it, shift it to an already-found alternative, or
 * fall back to vanilla's own unchecked placement (SITE-REQ-004). GV-5 ships only the hook family
 * and its {@link VillageStartHook#ACCEPT_ALL} default (always {@link #keep()}); wiring a
 * {@link Shift} or {@link Vanilla} decision back into the live placement call is domains/site.md's
 * own scoring logic, added when GV-6 registers a real {@link VillageStartHook}.
 */
public sealed interface StartDecision {
    record Keep() implements StartDecision {
    }

    record Shift(BlockPos to) implements StartDecision {
    }

    record Vanilla() implements StartDecision {
    }

    static StartDecision keep() {
        return new Keep();
    }

    static StartDecision shift(BlockPos to) {
        return new Shift(to);
    }

    static StartDecision vanilla() {
        return new Vanilla();
    }
}
