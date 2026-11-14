package net.quiwh.cwi.mixin.processing;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.quiwh.cwi.compat.processing.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=SawBlockEntity.class,remap=false)
abstract class SawAdvancedMixin extends BlockBreakingKineticBlockEntity {
    @Shadow public ProcessingInventory inventory;
    @Shadow protected abstract boolean canProcess();
    @Shadow private List<RecipeHolder<? extends Recipe<?>>> getRecipes(){throw new AssertionError();}
    @Shadow private int recipeIndex;
    @Shadow private ItemStack playEvent;
    protected SawAdvancedMixin(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    @ModifyReturnValue(method="getRecipes",at=@At("RETURN"))
    private List<RecipeHolder<? extends Recipe<?>>> cwi$priority(List<RecipeHolder<? extends Recipe<?>>> original){
        if(!(level instanceof net.minecraft.server.level.ServerLevel server)||original.stream().noneMatch(h->h.value() instanceof com.simibubi.create.content.processing.recipe.ProcessingRecipe<?,?> p&&(AdvancedProcessing.execution(p)||AdvancedProcessing.priority(p)!=0)))return original;
        var eligible=original.stream().filter(h->!(h.value() instanceof com.simibubi.create.content.processing.recipe.ProcessingRecipe<?,?> p)||BatchProcessing.eligible(server,inventory,p)).toList();
        // Keep a candidate while waiting for resources, so native processing cannot bypass metadata.
        var choices=eligible.isEmpty()?original:eligible;
        int max=choices.stream().mapToInt(h->AdvancedProcessing.priority(h.value())).max().orElse(0);
        return choices.stream().filter(h->AdvancedProcessing.priority(h.value())==max).toList();
    }
    @Inject(method="tick",at=@At(value="INVOKE",target="Lcom/simibubi/create/content/kinetics/base/BlockBreakingKineticBlockEntity;tick()V",shift=At.Shift.AFTER),cancellable=true)
    private void cwi$processing(CallbackInfo ci){
        if(!canProcess()||inventory.remainingTime<0)return;
        var choices=getRecipes();if(!choices.isEmpty())choices=List.of(choices.get(Math.floorMod(recipeIndex,choices.size())));
        double rate=getSpeed()==0?0:Math.clamp(Math.abs(getSpeed())/24d,1,128);
        if(BatchProcessing.tick(this,inventory,choices,rate,true,source->{playEvent=source;award(com.simibubi.create.foundation.advancement.AllAdvancements.SAW_PROCESSING);}))ci.cancel();
    }
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lcom/simibubi/create/content/processing/recipe/ProcessingInventory;getStackInSlot(I)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack cwi$keepInput(ProcessingInventory inventory,int slot,Operation<ItemStack> call){return BatchProcessing.visible(this,inventory,slot);}
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lcom/simibubi/create/content/processing/recipe/ProcessingInventory;clear()V"))
    private void cwi$clear(ProcessingInventory inventory,Operation<Void> call){BatchProcessing.clearTransport(this,inventory);}
}
