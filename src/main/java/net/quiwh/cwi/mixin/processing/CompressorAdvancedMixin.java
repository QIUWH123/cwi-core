package net.quiwh.cwi.mixin.processing;
import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.item.SmartInventory;
import io.github.hadron13.rubberworks.blocks.compressor.CompressingRecipe;
import io.github.hadron13.rubberworks.register.RubberworksRecipeTypes;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.*;
import net.quiwh.cwi.compat.processing.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="io.github.hadron13.rubberworks.blocks.compressor.CompressorBlockEntity",remap=false)
abstract class CompressorAdvancedMixin extends KineticBlockEntity {
    @Shadow public SmartFluidTankBehaviour tank;
    @Shadow public SmartInventory output;
    @Shadow protected IItemHandlerModifiable itemCapability;
    @Shadow public int timer;
    @Shadow public CompressingRecipe recipe;
    @Shadow public List<Integer> spoutputIndex;
    @Shadow public abstract boolean validSpeed();
    @Shadow public abstract int getProcessingSpeed();
    @Shadow public abstract BlazeBurnerBlock.HeatLevel getHeat();
    @Unique private SmartFluidTankBehaviour cwi$products;
    protected CompressorAdvancedMixin(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    @Inject(method="<init>",at=@At("RETURN"))
    private void cwi$noProductCapability(BlockEntityType<?> type,BlockPos pos,BlockState state,CallbackInfo ci){itemCapability=NativeStorage.EMPTY_ITEMS;}
    @Inject(method="addBehaviours",at=@At("TAIL"))
    private void cwi$fluidProducts(List<BlockEntityBehaviour> behaviours,CallbackInfo ci){
        tank.forbidExtraction();
        cwi$products=new SmartFluidTankBehaviour(SmartFluidTankBehaviour.OUTPUT,this,6,1000,true).forbidInsertion().forbidExtraction();
        behaviours.add(cwi$products);
    }
    @Unique private boolean cwi$heat(CompressingRecipe recipe){
        var required=recipe.getRequiredHeat();var heat=getHeat();
        return required==com.simibubi.create.content.processing.recipe.HeatCondition.NONE?heat==BlazeBurnerBlock.HeatLevel.SMOULDERING:required.testBlazeBurner(heat);
    }
    @Inject(method="tick",at=@At(value="INVOKE",target="Lio/github/hadron13/rubberworks/blocks/compressor/CompressorBlockEntity;validSpeed()Z"),cancellable=true)
    private void cwi$work(CallbackInfo ci){
        if(!(level instanceof ServerLevel server))return;
        cwi$mouth();
        var work=NativeWork.of(this);var items=NativeStorage.items(NativeStorage.EMPTY_ITEMS,output);var fluids=NativeStorage.fluids(tank,cwi$products);
        if(!work.active()){
            var found=NativeWork.candidates(server,RubberworksRecipeTypes.COMPRESSING.getType(),p->cwi$heat((CompressingRecipe)p));
            // All compressor recipes use the mouth-only product storage, including ordinary ones.
            for(var holder:found)if(work.start(server,holder,items,fluids)){recipe=(CompressingRecipe)holder.value();timer=(int)work.duration();setChanged();sendData();ci.cancel();return;}
        }
        ci.cancel();if(!work.active()||!work.restore(server))return;recipe=(CompressingRecipe)work.recipe();
        if(!validSpeed()||!cwi$heat(recipe))return;
        if(work.step(server,items,fluids,getProcessingSpeed()/work.duration())){
            for(int slot=0;slot<output.getSlots();slot++)if(!output.getStackInSlot(slot).isEmpty()&&!spoutputIndex.contains(slot))spoutputIndex.add(slot);
            timer=(int)Math.ceil(work.duration()*(1-work.progress()));setChanged();sendData();
        }
        if(work.finished()){work.clear();recipe=null;timer=0;}
    }
    @Unique private void cwi$mouth(){
        Direction dir=getBlockState().getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
        if(!BasinBlock.canOutputTo(level,getBlockPos(),dir))return;
        var destination=getBlockPos().below().relative(dir);
        var receiver=level.getCapability(Capabilities.FluidHandler.BLOCK,destination,dir.getOpposite());
        if(receiver==null)return;
        var filter=BlockEntityBehaviour.get(level,destination,FilteringBehaviour.TYPE);
        if(filter!=null&&filter.isRecipeFilter())filter=null;
        for(var segment:cwi$products.getTanks()){
            var storage=((TankSegmentAccess)segment).cwi$tank();var stack=storage.getFluid();
            if(stack.isEmpty()||filter!=null&&!filter.test(stack))continue;
            var action=IFluidHandler.FluidAction.SIMULATE;
            int filled=receiver instanceof SmartFluidTankBehaviour.InternalFluidHandler internal?internal.forceFill(stack.copy(),action):receiver.fill(stack.copy(),action);
            if(filled<=0)continue;
            var transfer=stack.copyWithAmount(Math.min(filled,stack.getAmount()));
            int moved=receiver instanceof SmartFluidTankBehaviour.InternalFluidHandler internal?internal.forceFill(transfer,IFluidHandler.FluidAction.EXECUTE):receiver.fill(transfer,IFluidHandler.FluidAction.EXECUTE);
            if(moved>0){storage.setFluid(stack.copyWithAmount(Math.max(0,stack.getAmount()-moved)));setChanged();sendData();}
        }
    }
}
