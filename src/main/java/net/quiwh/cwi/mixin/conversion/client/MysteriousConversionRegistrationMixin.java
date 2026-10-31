package net.quiwh.cwi.mixin.conversion.client;

import com.simibubi.create.compat.jei.ConversionRecipe;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Synthetic conversions bypass datapack recipe removal, including Power Grid's direct JEI registration. */
@Mixin(targets = "mezz.jei.library.load.registration.RecipeRegistration")
public abstract class MysteriousConversionRegistrationMixin {
    @Unique private static final Set<ResourceLocation> cwi$disabledConversionOutputs = Set.of(
            ResourceLocation.parse("create:blaze_burner"),
            ResourceLocation.parse("powergrid:magnet"),
            ResourceLocation.parse("create_power_loader:andesite_chunk_loader"),
            ResourceLocation.parse("create_power_loader:brass_chunk_loader"));

    @ModifyArg(method = "addRecipes", at = @At(value = "INVOKE",
            target = "Lmezz/jei/library/recipes/RecipeManagerInternal;addRecipes(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V"), index = 1)
    private List<?> cwi$filterDisabledConversions(List<?> recipes) {
        return recipes.stream().filter(entry -> !(entry instanceof RecipeHolder<?> holder
                && holder.value() instanceof ConversionRecipe conversion
                && conversion.getRollableResults().stream().anyMatch(result -> cwi$disabledConversionOutputs.contains(
                        BuiltInRegistries.ITEM.getKey(result.getStack().getItem()))))).toList();
    }
}
