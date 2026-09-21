/**
 * Whole-village site selection (`docs/spec/domains/site.md`): {@link grounded_villages.site.SiteScorer}
 * samples a candidate's footprint for height spread and water fraction over the pure {@link
 * grounded_villages.site.HeightSampler}; {@link grounded_villages.site.SiteSearch} decides
 * keep/shift/vanilla ({@link grounded_villages.site.SiteDecision}) and runs the bounded,
 * deterministic search for a qualifying alternative when the vanilla start fails
 * (`SITE-REQ-001`-{@code 004}); {@link grounded_villages.site.SiteStartHook} is the live {@link
 * grounded_villages.hook.VillageStartHook} a loader entrypoint registers into {@link
 * grounded_villages.hook.HookRegistry}, adapting the pure logic above to and from {@link
 * grounded_villages.hook.TerrainSampler}/{@code BlockPos}/{@code StartDecision}, and to whatever
 * {@code site.*} {@link grounded_villages.config.ConfigHolder} currently holds.
 *
 * <p><b>Zero Minecraft imports outside {@link grounded_villages.site.SiteStartHook}</b>
 * (`04-architecture.md` {@code ARCH-DEC-002}: "pure functions over positions, heights, booleans"),
 * not merely loader-free -- this mod's forge/neoforge nodes do not put Minecraft on {@code
 * src/test}'s own compile classpath at all (`docs/spec/operations/testing.md` "Unit": "no
 * Minecraft imports"), so {@link grounded_villages.site.SiteScorer} and {@link
 * grounded_villages.site.SiteSearch} genuinely cannot reference one and still compile there.
 * {@link grounded_villages.site.SiteCoordinate} stands in for {@code BlockPos}, {@link
 * grounded_villages.site.HeightSampler} stands in for {@link
 * grounded_villages.hook.TerrainSampler}, and {@link grounded_villages.site.SiteDecision} stands
 * in for {@code StartDecision} -- {@code SiteStartHook} is the one, Minecraft-typed adapter
 * between this package and the mixin.
 */
package grounded_villages.site;
