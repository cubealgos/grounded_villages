package grounded_villages.mixin.village;

import grounded_villages.hook.HookDebug;
import grounded_villages.mixinsupport.VillageTagContext;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Sets the {@code #minecraft:village} tag gate (decisions/DEC-007-village-tag-scope.md) that
 * {@link JigsawPlacementMixin} and {@link PlacerMixin} read via {@link VillageTagContext}.
 *
 * <p>{@code Structure.GenerationContext} carries no reference back to the {@code Structure}
 * (and therefore no registry holder) that owns it -- confirmed by a direct {@code javap} read of
 * the record's fields (GV-5), correcting {@code 04-architecture.md} {@code ARCH-DEC-001}'s own
 * phrasing ("gated by testing the structure's registry holder"), which does not name where that
 * holder comes from. {@code findGenerationPoint} is the one place in the vanilla call chain with
 * a {@code this} (the {@code JigsawStructure} instance itself) to test -- {@code
 * BuiltInRegistries.STRUCTURE} named by the research and the architecture doc does not exist
 * either (structures are a datapack-driven <i>dynamic</i> registry, not a built-in one); the real
 * API is {@code context.registryAccess().registryOrThrow(Registries.STRUCTURE)}, confirmed
 * present and stable 1.20.1-26.2 by {@code javap} (GV-5).
 *
 * <p>{@code findGenerationPoint}'s own signature ({@code Structure.GenerationContext ->
 * Optional<Structure.GenerationStub>}) is identical on every targeted version (javap, GV-5) --
 * no Stonecutter preprocessor conditional needed for the injection itself. One is needed for the
 * registry lookup inside the handler body, though: {@code RegistryAccess.registryOrThrow} is
 * renamed {@code lookupOrThrow} in 26.2 (confirmed by {@code javap} against the 26.2 jar, GV-5 --
 * not named in {@code contracts/platform-matrix.md}'s version-delta table, which only tracks
 * {@code JigsawPlacement}/{@code Placer} signatures, not this call).
 */
@Mixin(JigsawStructure.class)
abstract class JigsawStructureMixin {

    @Inject(method = "findGenerationPoint", at = @At("HEAD"))
    private void gv$gateOnVillageTag(Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        //? if <26.1 {
        Registry<Structure> structures = context.registryAccess().registryOrThrow(Registries.STRUCTURE);
        //?} else {
        /*Registry<Structure> structures = context.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        *///?}
        boolean isVillage = structures.wrapAsHolder((Structure) (Object) this).is(StructureTags.VILLAGE);
        VillageTagContext.set(isVillage);
        HookDebug.fired("JigsawStructureMixin#findGenerationPoint", isVillage ? "village-tagged" : "not village-tagged");
    }
}
