package net.quiwh.cwi.mixin.pollution;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.quiwh.cwi.pollution.*;
import org.spongepowered.asm.mixin.*;

@Mixin(value=BasinBlockEntity.class,remap=false)
abstract class BasinGasMixin implements GasBasinStorage {
    @Shadow protected List<FluidStack> spoutputFluidBuffer;
    @Override public List<FluidStack> cwi$gasOverflow(){return spoutputFluidBuffer;}

    @WrapMethod(method="tryClearingSpoutputOverflow")
    private void cwi$retainedGasCannotUseSpout(Operation<Void> original) {
        var basin=(BasinBlockEntity)(Object)this;
        if(spoutputFluidBuffer.isEmpty()||!GasContainers.open(basin)){original.call();return;}
        var blocked=new ArrayList<FluidStack>();
        spoutputFluidBuffer.removeIf(fluid->{if(!fluid.isEmpty()&&GasProfiles.isGas(fluid.getFluid())){blocked.add(fluid);return true;}return false;});
        try {original.call();}finally {spoutputFluidBuffer.addAll(blocked);}
    }

    @WrapMethod(method="acceptOutputs")
    private boolean cwi$ventBeforeSpout(List<ItemStack> items,List<FluidStack> fluids,boolean simulate,Operation<Boolean> original) {
        var basin=(BasinBlockEntity)(Object)this;
        if(fluids.isEmpty()||!GasContainers.open(basin)||fluids.stream().noneMatch(s->!s.isEmpty()&&GasProfiles.isGas(s.getFluid())))
            return original.call(items,fluids,simulate);
        var gases=new ArrayList<FluidStack>();var liquids=new ArrayList<FluidStack>();
        for(var fluid:fluids)(!fluid.isEmpty()&&GasProfiles.isGas(fluid.getFluid())?gases:liquids).add(fluid);
        var level=(ServerLevel)basin.getLevel();var pos=basin.getBlockPos();
        if(!GasRelease.canReleaseAll(level,pos,Direction.UP,gases)||!original.call(items,liquids,true))return false;
        if(simulate)return true;
        if(!original.call(items,liquids,false))return false;
        for(var gas:gases) {
            int accepted=GasRelease.release(basin,gas,FluidAction.EXECUTE);
            // Preserve a remainder if another mod's synchronous callback changed outlet capacity.
            if(accepted<gas.getAmount()){spoutputFluidBuffer.add(gas.copyWithAmount(gas.getAmount()-accepted));GasContainers.retry(basin);basin.setChanged();}
        }
        return true;
    }
}
