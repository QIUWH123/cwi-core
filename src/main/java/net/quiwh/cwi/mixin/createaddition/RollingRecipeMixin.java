package net.quiwh.cwi.mixin.createaddition;

import com.mrh0.createaddition.compat.jei.RollingMillAssemblySubCategory;
import com.mrh0.createaddition.index.CABlocks;
import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.mrh0.createaddition.recipe.rolling.RollingRecipe", remap = false)
abstract class RollingRecipeMixin implements IAssemblyRecipe {
    @Override public void addAssemblyIngredients(List<Ingredient> ingredients) { }
    @Override public void addRequiredMachines(Set<ItemLike> machines) { machines.add(CABlocks.ROLLING_MILL.get()); }
    @OnlyIn(Dist.CLIENT)
    @Override public Component getDescriptionForAssembly() { return Component.translatable("createaddition.recipe.rolling.sequence"); }
    @Override public Supplier<Supplier<SequencedAssemblySubCategory>> getJEISubCategory() {
        return () -> RollingMillAssemblySubCategory::new;
    }
}
