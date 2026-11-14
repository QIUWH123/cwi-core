import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.fluids.drain.ItemDrainRenderer;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.quiwh.cwi.pollution.GasProfiles;

/** Uses the transformed installed Create tank/drain methods without opening a graphics context. */
public final class GasContainerVisualFixture {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    public static void main(String[] args) throws Exception {
        MinimalNativeBootstrap.initialize();net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);var unsafe=(sun.misc.Unsafe)field.get(null);
        GasProfiles.remoteFluids(java.util.Set.of(ResourceLocation.parse("minecraft:lava")));
        var gas=new FluidStack(Fluids.LAVA,500);var water=new FluidStack(Fluids.WATER,500);
        try {
            for(var type:java.util.List.of(BasinBlockEntity.class,FluidTankBlockEntity.class)) {
                var owner=(SmartBlockEntity)unsafe.allocateInstance(type);
                var behaviour=SmartFluidTankBehaviour.single(owner,1000);var segment=behaviour.getPrimaryTank();
                var rendered=segment.getClass().getDeclaredField("renderedFluid");rendered.setAccessible(true);
                var height=segment.getClass().getDeclaredField("fluidLevel");height.setAccessible(true);
                ((LerpedFloat)height.get(segment)).startWithValue(.5);
                rendered.set(segment,gas);
                check(segment.getRenderedFluid().isEmpty()==(type==BasinBlockEntity.class),"open-container gas surface is hidden without affecting sealed tanks");
                check(segment.getTotalUnits(0)==(type==BasinBlockEntity.class?0:500),"hidden gas cannot raise a basin's liquid surface");
                rendered.set(segment,water);
                check(segment.getRenderedFluid()==water&&segment.getTotalUnits(0)==500,"ordinary liquid animation remains native");
            }
            var renderer=unsafe.allocateInstance(ItemDrainRenderer.class);
            var method=java.util.Arrays.stream(ItemDrainRenderer.class.getDeclaredMethods()).filter(m->m.getName().contains("cwi$noGasLiquid")).findFirst().orElseThrow();
            method.setAccessible(true);int[] submissions={0};
            Operation<Void> original=values->{submissions[0]++;return null;};
            method.invoke(renderer,null,gas,0F,0F,0F,1F,1F,1F,null,null,0,false,false,original);
            check(submissions[0]==0,"drain does not submit a pouring liquid mesh for gas");
            method.invoke(renderer,null,water,0F,0F,0F,1F,1F,1F,null,null,0,false,false,original);
            check(submissions[0]==1,"drain retains ordinary liquid mesh submission");
        }finally{GasProfiles.remoteFluids(null);}
        System.out.println("Gas container visuals: "+checks+" native checks passed");
    }
}
