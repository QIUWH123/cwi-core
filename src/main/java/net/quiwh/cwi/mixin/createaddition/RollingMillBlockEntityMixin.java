package net.quiwh.cwi.mixin.createaddition;

import com.mrh0.createaddition.index.CARecipes;
import com.mrh0.createaddition.recipe.rolling.RollingRecipe;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import net.quiwh.cwi.compat.ProcessingOutputCapacity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.mrh0.createaddition.blocks.rolling_mill.RollingMillBlockEntity", remap = false)
abstract class RollingMillBlockEntityMixin extends KineticBlockEntity {
    @Shadow public ItemStackHandler inputInv;
    @Shadow public ItemStackHandler outputInv;
    @Shadow public abstract Optional<RecipeHolder<RollingRecipe>> find(RecipeWrapper inventory, Level level);

    protected RollingMillBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

    @Inject(method = "find", at = @At("HEAD"), cancellable = true)
    private void cwi$findAssemblyStep(RecipeWrapper inventory, Level level,
            CallbackInfoReturnable<Optional<RecipeHolder<RollingRecipe>>> cir) {
        ItemStack input = inventory.getItem(0);
        var assembly = SequencedAssemblyRecipe.getRecipe(level, input, CARecipes.ROLLING_TYPE.get(), RollingRecipe.class);
        if (assembly.isPresent() || input.has(AllDataComponents.SEQUENCED_ASSEMBLY)) cir.setReturnValue(assembly);
    }

    // Ingredient matching alone cannot distinguish consecutive steps using the same transitional item.
    @Redirect(method = {"tick", "canProcess"}, at = @At(value = "INVOKE",
            target = "Lcom/mrh0/createaddition/recipe/rolling/RollingRecipe;matches(Lnet/neoforged/neoforge/items/wrapper/RecipeWrapper;Lnet/minecraft/world/level/Level;)Z"),
            require = 2, allow = 2)
    private boolean cwi$validateCurrentStep(RollingRecipe cached, RecipeWrapper inventory, Level level) {
        return find(inventory, level).isPresent();
    }

    @Inject(method = "process", at = @At("HEAD"), cancellable = true)
    private void cwi$processCurrentStep(CallbackInfo ci) {
        ci.cancel();
        var found = find(new RecipeWrapper(inputInv), level);
        if (found.isEmpty()) return;
        var holder = found.get();
        var recipe = holder.value();
        var parent = level.getRecipeManager().byKey(holder.id()).map(RecipeHolder::value).orElse(null);
        List<ItemStack> outputs;
        if (parent instanceof SequencedAssemblyRecipe assembly) {
            var progress = inputInv.getStackInSlot(0).get(AllDataComponents.SEQUENCED_ASSEMBLY);
            int nextStep = progress == null ? 1 : progress.step() + 1;
            if (nextStep >= (long)assembly.getLoops() * assembly.getSequence().size()) {
                // Reserve each possible final outcome before rolling, so blocking cannot reroll the reward.
                for (var result : assembly.resultPool)
                    if (result.getChance() > 0 && !ProcessingOutputCapacity.canFit(outputInv, List.of(result.getStack()))) return;
            }
            // Create's enforced result carries the recipe ID, step, loop progress and weighted final outcome.
            outputs = recipe.rollResults(level.random);
        } else {
            outputs = List.of(recipe.getResultStack().copy());
        }
        if (!ProcessingOutputCapacity.canFit(outputInv, outputs)) return;
        inputInv.extractItem(0, 1, false);
        for (var output : outputs) ItemHandlerHelper.insertItemStacked(outputInv, output.copy(), false);
        sendData();
        setChanged();
    }
}
