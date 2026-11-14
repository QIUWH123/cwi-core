package net.quiwh.cwi.pollution;

import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.quiwh.cwi.machine.SolarEvaporatorBlockEntity;
import net.quiwh.cwi.mixin.processing.TankSegmentAccess;

/** Explicit open-container policy. Only blocked, nonempty releases enter the budgeted retry queue. */
public final class GasContainers {
    private static final net.minecraft.resources.ResourceLocation BASIN_LID=net.minecraft.resources.ResourceLocation.parse("createdieselgenerators:basin_lid");
    private static final Map<ServerLevel,LinkedHashSet<BlockPos>> pending=new WeakHashMap<>();
    public static boolean supported(BlockEntity owner) {
        return owner.getClass()==BasinBlockEntity.class||owner.getClass()==ItemDrainBlockEntity.class
                ||owner instanceof SolarEvaporatorBlockEntity;
    }
    public static boolean open(BlockEntity owner) {
        if(!supported(owner)||!(owner.getLevel() instanceof ServerLevel level)||owner.isRemoved())return false;
        if(owner instanceof BasinBlockEntity) {
            var lid=level.getBlockState(owner.getBlockPos().above());
            if(BuiltInRegistries.BLOCK.getKey(lid.getBlock()).equals(BASIN_LID)
                    &&lid.hasProperty(BlockStateProperties.OPEN)&&!lid.getValue(BlockStateProperties.OPEN))return false;
        }
        return true;
    }
    public static void retry(BlockEntity owner) {
        if(owner.getLevel() instanceof ServerLevel level&&!owner.isRemoved())
            pending.computeIfAbsent(level,key->new LinkedHashSet<>()).add(owner.getBlockPos().immutable());
    }
    public static void releaseStored(BlockEntity owner) {
        if(!supported(owner)||!(owner.getLevel() instanceof ServerLevel level))return;
        var queue=pending.get(level);if(queue!=null)queue.remove(owner.getBlockPos());
        if(!open(owner))return;
        if(owner instanceof BasinBlockEntity basin) {
            release(basin.getTanks().getFirst());release(basin.getTanks().getSecond());
            var overflow=((GasBasinStorage)basin).cwi$gasOverflow();boolean changed=false;
            for(var iterator=overflow.iterator();iterator.hasNext();) {
                var fluid=iterator.next();if(fluid.isEmpty()||!GasProfiles.isGas(fluid.getFluid()))continue;
                int accepted=GasRelease.release(owner,fluid,
                        net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                if(accepted>0){fluid.shrink(accepted);changed=true;if(fluid.isEmpty())iterator.remove();}
                if(!fluid.isEmpty())retry(owner);
            }
            if(changed){basin.notifyChangeOfContents();basin.setChanged();basin.sendData();}
        } else if(owner instanceof ItemDrainBlockEntity drain) {
            release((SmartFluidTankBehaviour)drain.getBehaviour(SmartFluidTankBehaviour.TYPE));
        } else if(owner instanceof SolarEvaporatorBlockEntity solar) {
            for(int tank=0;tank<solar.fluids().getTanks();tank++)release(solar.fluids().tank(tank));
        }
    }
    private static void release(SmartFluidTankBehaviour behaviour) {
        if(behaviour!=null)for(var segment:behaviour.getTanks())release(((TankSegmentAccess)segment).cwi$tank());
    }
    private static void release(FluidTank tank) {
        if(!tank.isEmpty()&&GasProfiles.fromFluid(tank.getFluid().getFluid())!=null)tank.setFluid(tank.getFluid().copy());
    }
    public static void tick(ServerLevel level) {
        var queue=pending.get(level);
        if(queue==null||queue.isEmpty()||level.getGameTime()%5!=0)return;
        int count=Math.min(64,queue.size());
        for(int n=0;n<count;n++) {
            var iterator=queue.iterator();var pos=iterator.next();iterator.remove();
            // Initialization re-registers loaded contents. Never force-load a stored position.
            if(!level.hasChunkAt(pos))continue;
            var owner=level.getBlockEntity(pos);if(owner!=null)releaseStored(owner);
        }
        if(queue.isEmpty())pending.remove(level);
    }
    private GasContainers(){}
}
