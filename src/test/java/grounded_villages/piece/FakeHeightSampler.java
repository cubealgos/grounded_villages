package grounded_villages.piece;

import grounded_villages.site.HeightSampler;

/**
 * A {@link HeightSampler} test double built directly from two height functions -- no {@code
 * ChunkGenerator}, no world, no Minecraft classpath needed to compile this file at all (this
 * mod's forge/neoforge nodes do not put Minecraft on {@code src/test}'s own compile classpath,
 * `docs/spec/operations/testing.md` "Unit": "no Minecraft imports"). Deliberately its own small
 * copy rather than a shared test utility: {@code grounded_villages.site.FakeHeightSampler} is
 * package-private to {@code grounded_villages.site} and this ticket's own instruction is "given
 * synthetic footprint/height inputs" for {@code piece}'s own tests specifically.
 */
final class FakeHeightSampler implements HeightSampler {

    @FunctionalInterface
    interface Field {
        int height(int x, int z);
    }

    private final Field ground;
    private final Field surface;

    FakeHeightSampler(Field ground, Field surface) {
        this.ground = ground;
        this.surface = surface;
    }

    /** A flat, dry, {@code height}-everywhere sampler -- the common "this column is fine" case. */
    static FakeHeightSampler flat(int height) {
        return new FakeHeightSampler((x, z) -> height, (x, z) -> height);
    }

    @Override
    public int groundHeight(int x, int z) {
        return ground.height(x, z);
    }

    @Override
    public int surfaceHeight(int x, int z) {
        return surface.height(x, z);
    }
}
