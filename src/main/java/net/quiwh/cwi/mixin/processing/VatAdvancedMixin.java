package net.quiwh.cwi.mixin.processing;
import com.drmangotea.tfmg.base.pressure.Pressure;
import com.drmangotea.tfmg.content.machinery.vat.base.*;
import com.drmangotea.tfmg.content.machinery.vat.base.registry.operations.VatOperation;
import com.drmangotea.tfmg.recipes.VatMachineRecipe;
import com.drmangotea.tfmg.registry.TFMGRecipeTypes;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.quiwh.cwi.compat.processing.*;
import net.quiwh.cwi.machine.VesselTransaction;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo
@Mixin(targets="com.drmangotea.tfmg.content.machinery.vat.base.VatBlockEntity",remap=false)
abstract class VatAdvancedMixin extends SmartBlockEntity {
    @Shadow public VatInventory inputInventory;
    @Shadow public VatInventory outputInventory;
    @Shadow public SmartFluidTankBehaviour inputTank;
    @Shadow public SmartFluidTankBehaviour outputTank;
    @Shadow public VatMachineRecipe recipe;
    @Shadow public Map<BlockPos,VatOperation> machineMap;
    @Shadow public boolean areMachinesValid;
    @Shadow int timer;
    @Shadow int heatLevel;
    @Shadow Pressure pressure;
    @Shadow public abstract boolean isController();
    @Shadow public abstract int getTotalTankSize();
    protected VatAdvancedMixin(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    // TFMG's pressure input uses the same InputTanks NBT key as its fluid input.
    // Preserve the actual fluid behaviour independently, including leftover input after completion.
    @Inject(method="write",at=@At("TAIL"))
    private void cwi$saveInputs(CompoundTag tag,HolderLookup.Provider registries,boolean packet,CallbackInfo ci){
        var fluids=new CompoundTag();inputTank.write(fluids,registries,packet);tag.put("CwiVatFluids",fluids);
    }
    @Inject(method="read",at=@At("RETURN"))
    private void cwi$loadInputs(CompoundTag tag,HolderLookup.Provider registries,boolean packet,CallbackInfo ci){
        if(tag.contains("CwiVatFluids"))inputTank.read(tag.getCompound("CwiVatFluids"),registries,packet);
    }
    @Unique private boolean cwi$environment(VatMachineRecipe value){
        if(!isController()||!areMachinesValid||getTotalTankSize()<value.minSize)return false;
        if(!value.allowedVatTypes.isEmpty()&&!value.allowedVatTypes.contains(((VatBlock)getBlockState().getBlock()).getVatType()))return false;
        var active=new ArrayList<>(machineMap.values());for(var required:value.machines)if(!active.remove(required))return false;
        return true;
    }
    @Inject(method="getMatchingRecipe",at=@At("HEAD"),cancellable=true)
    private void cwi$select(CallbackInfoReturnable<VatMachineRecipe> cir){
        if(!(level instanceof ServerLevel server))return;
        var work=NativeWork.of(this);if(work.active()&&work.restore(server)){cir.setReturnValue((VatMachineRecipe)work.recipe());return;}
        var found=NativeWork.candidates(server,TFMGRecipeTypes.VAT.getType(),p->true);
        if(found.stream().noneMatch(h->AdvancedProcessing.execution(h.value())||AdvancedProcessing.priority(h.value())!=0))return;
        var items=NativeStorage.items(inputInventory,outputInventory);var fluids=NativeStorage.fluids(inputTank,outputTank);
        for(var holder:found){var value=(VatMachineRecipe)holder.value();
            if(cwi$environment(value)&&VesselTransaction.eligible(AdvancedProcessing.get(value).transaction(value.getProcessingDuration(),value.getRequiredHeat()),items,fluids,server)){cir.setReturnValue(value);return;}}
        cir.setReturnValue(null);
    }
    @Inject(method="handleRecipe",at=@At("HEAD"),cancellable=true)
    private void cwi$work(CallbackInfo ci){
        if(!(level instanceof ServerLevel server))return;
        var work=NativeWork.of(this);
        if(work.active()){if(!work.restore(server)){recipe=null;timer=0;ci.cancel();return;}recipe=(VatMachineRecipe)work.recipe();}
        if(recipe==null||!AdvancedProcessing.execution(recipe))return;
        ci.cancel();if(!cwi$environment(recipe)||recipe.heatLevel*(recipe.heatLevel-heatLevel)>0||recipe.pressure.getValue()*(recipe.pressure.getValue()-pressure.getValue())>0)return;
        var items=NativeStorage.items(inputInventory,outputInventory);var fluids=NativeStorage.fluids(inputTank,outputTank);
        if(!work.active()){
            var chosen=recipe;var holder=server.getRecipeManager().getRecipes().stream().filter(h->h.value()==chosen).findFirst().orElse(null);
            if(holder==null)return;
            @SuppressWarnings({"rawtypes","unchecked"}) var nativeHolder=(net.minecraft.world.item.crafting.RecipeHolder<? extends com.simibubi.create.content.processing.recipe.ProcessingRecipe<?,?>>)(net.minecraft.world.item.crafting.RecipeHolder)holder;
            if(!work.start(server,nativeHolder,items,fluids))return;
        }
        if(work.step(server,items,fluids,1d/work.duration())){timer=(int)Math.floor(work.progress()*work.duration());setChanged();sendData();}
        if(work.finished()){work.clear();recipe=null;timer=0;}
    }
}
