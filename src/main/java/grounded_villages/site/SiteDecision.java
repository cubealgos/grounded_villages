package grounded_villages.site;

/**
 * {@link SiteSearch#evaluateStart}'s own pure result -- structurally the same three outcomes as
 * {@code grounded_villages.hook.StartDecision} (keep/shift/vanilla, `domains/site.md`), kept as a
 * separate type so this package references no Minecraft type (`04-architecture.md`
 * {@code ARCH-DEC-002}: {@code StartDecision.Shift} carries a {@code BlockPos}, this carries a
 * pure {@link SiteCoordinate}). {@code grounded_villages.site.SiteStartHook} is the one place
 * that converts between the two.
 */
public sealed interface SiteDecision {
    record Keep() implements SiteDecision {
    }

    record Shift(SiteCoordinate to) implements SiteDecision {
    }

    record Vanilla() implements SiteDecision {
    }

    static SiteDecision keep() {
        return new Keep();
    }

    static SiteDecision shift(SiteCoordinate to) {
        return new Shift(to);
    }

    static SiteDecision vanilla() {
        return new Vanilla();
    }
}
