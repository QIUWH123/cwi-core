package net.quiwh.cwi.mixin.processing;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.simibubi.create.AllRecipeTypes;
import net.minecraft.server.level.ServerLevel;
import com.simibubi.create.content.processing.recipe.*;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import net.quiwh.cwi.compat.processing.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=CrushingWheelControllerBlockEntity.class,remap=false)
abstract class CrusherAdvancedMixin extends SmartBlockEntity {
    @Shadow public ProcessingInventory inventory;
    @Shadow public float crushingspeed;
    @Shadow public abstract boolean hasEntity();
    @Shadow public abstract Optional<RecipeHolder<StandardProcessingRecipe<RecipeWrapper>>> findRecipe();
    protected CrusherAdvancedMixin(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    @Inject(method="tick",at=@At(value="INVOKE",target="Lcom/simibubi/create/foundation/blockEntity/SmartBlockEntity;tick()V",shift=At.Shift.AFTER),cancellable=true)
    private void cwi$processing(CallbackInfo ci){
        if(hasEntity())return;
        double divisor=Math.max(1,Math.log(Math.max(1,inventory.getStackInSlot(0).getCount()))/Math.log(2));
        double rate=crushingspeed==0?0:Math.clamp(crushingspeed*4d/divisor,0.25,20);
        var choices=findRecipe().stream().map(h->(RecipeHolder<?>)h).toList();
        if(level instanceof ServerLevel server){
            var wrapper=new RecipeWrapper(inventory);
            var crushing=NativeWork.candidates(server,AllRecipeTypes.CRUSHING.getType(),p->((StandardProcessingRecipe<RecipeWrapper>)p).matches(wrapper,level));
            if(crushing.isEmpty())crushing=NativeWork.candidates(server,AllRecipeTypes.MILLING.getType(),p->((StandardProcessingRecipe<RecipeWrapper>)p).matches(wrapper,level));
            choices=crushing.stream().map(h->(RecipeHolder<?>)h).toList();
        }
        if(BatchProcessing.tick(this,inventory,choices,rate,false,source->{}))ci.cancel();
    }
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lcom/simibubi/create/content/processing/recipe/ProcessingInventory;getStackInSlot(I)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack cwi$keepInput(ProcessingInventory inventory,int slot,Operation<ItemStack> call){return BatchProcessing.visible(this,inventory,slot);}
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lcom/simibubi/create/content/processing/recipe/ProcessingInventory;clear()V"))
    private void cwi$clear(ProcessingInventory inventory,Operation<Void> call){BatchProcessing.clearTransport(this,inventory);}
}
