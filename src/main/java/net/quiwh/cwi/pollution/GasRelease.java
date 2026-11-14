package net.quiwh.cwi.pollution;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** One commit-only atmosphere boundary for pipes and open storage. */
public final class GasRelease {
    public static boolean canReleaseAll(ServerLevel level,BlockPos source,Direction direction,java.util.List<FluidStack> stacks) {
        if(!GasPassages.acceptsOutlet(level,source.relative(direction),direction))return false;
        var amounts=new java.util.LinkedHashMap<GasProfile,Long>();
        for(var stack:stacks)if(!stack.isEmpty()) {
            var gas=GasProfiles.fromFluid(stack.getFluid());if(gas==null)return false;
            amounts.merge(gas,(long)stack.getAmount(),Long::sum);
        }
        var clouds=GasClouds.existing(level);
        return clouds==null?amounts.size()<=GasClouds.MAX_GASES_PER_CELL&&amounts.values().stream().allMatch(n->n<=GasClouds.MAX_AMOUNT)
                :clouds.canEmitAll(level,source.relative(direction),amounts);
    }
    public static int release(ServerLevel level,BlockPos source,Direction direction,FluidStack stack,FluidAction action) {
        return release(level,source,direction,stack,action,null);
    }
    public static int release(net.minecraft.world.level.block.entity.BlockEntity owner,FluidStack stack,FluidAction action) {
        var level=(ServerLevel)owner.getLevel();var pos=owner.getBlockPos();
        // A shallow tray emits at its rim, using the same jet implementation as a pipe.
        var opening=action.execute()&&!level.players().isEmpty()?net.minecraft.world.phys.Vec3.atLowerCornerOf(pos).add(.5,
                owner.getBlockState().getCollisionShape(level,pos).max(Direction.Axis.Y)+.015,.5):null;
        return release(level,pos,Direction.UP,stack,action,opening);
    }
    private static int release(ServerLevel level,BlockPos source,Direction direction,FluidStack stack,FluidAction action,net.minecraft.world.phys.Vec3 opening) {
        var gas=stack.isEmpty()?null:GasProfiles.fromFluid(stack.getFluid());
        if(gas==null)return 0;
        var output=source.relative(direction);
        if(!GasPassages.acceptsOutlet(level,output,direction))return 0;
        var clouds=action.simulate()?GasClouds.existing(level):GasClouds.get(level);
        if(clouds==null)return (int)Math.min(stack.getAmount(),GasClouds.MAX_AMOUNT);
        int accepted=clouds.emit(level,output,gas,stack.getAmount(),action.simulate());
        if(action.execute()&&accepted>0)clouds.outlet(level,source,direction,gas,accepted,opening);
        return accepted;
    }
    private GasRelease(){}
}
