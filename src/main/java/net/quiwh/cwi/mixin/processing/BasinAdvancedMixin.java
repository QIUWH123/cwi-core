package net.quiwh.cwi.mixin.processing;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.simibubi.create.content.processing.basin.*;
import java.util.*;
import net.minecraft.world.item.crafting.Recipe;
import net.quiwh.cwi.compat.processing.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(value=BasinOperatingBlockEntity.class,remap=false)
abstract class BasinAdvancedMixin implements NativeBasinOwner {
    @Shadow protected Recipe<?> currentRecipe;
    @Shadow protected abstract Optional<BasinBlockEntity> getBasin();
    @Shadow protected abstract Optional<com.simibubi.create.foundation.advancement.CreateAdvancement> getProcessedRecipeTrigger();
    public Optional<BasinBlockEntity> cwi$basin(){return getBasin();}
    public Recipe<?> cwi$recipe(){return currentRecipe;}
    public void cwi$recipe(Recipe<?> value){currentRecipe=value;}
    public void cwi$completed(){getProcessedRecipeTrigger().ifPresent(((BasinOperatingBlockEntity)(Object)this)::award);}
    @ModifyReturnValue(method="getMatchingRecipes",at=@At("RETURN"))
    private List<Recipe<?>> cwi$priority(List<Recipe<?>> original){
        if(original.stream().noneMatch(r->AdvancedProcessing.priority(r)!=0))return original;
        var sorted=new ArrayList<>(original);sorted.sort(Comparator.<Recipe<?>>comparingInt(AdvancedProcessing::priority).reversed());return sorted;
    }
}
