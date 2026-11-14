package net.quiwh.cwi;

import static net.quiwh.cwi.NativeAdvancedTestSupport.*;
import com.drmangotea.tfmg.content.decoration.tanks.steel.SteelTankBlockEntity;
import com.drmangotea.tfmg.content.machinery.oil_processing.distillation_tower.controller.DistillationControllerBlockEntity;
import com.drmangotea.tfmg.content.machinery.oil_processing.distillation_tower.output.DistillationOutputBlockEntity;
import com.jesz.createdieselgenerators.content.distillation.DistillationTankBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.compat.processing.NativeWork;

@PrefixGameTestTemplate(false)
public final class NativeTowerAdvancedGameTests {
    @EventBusSubscriber(modid=CwiCore.ID) public static final class Registration{
        @SubscribeEvent public static void register(RegisterGameTestsEvent event){if(ModList.get().isLoaded("tfmg")&&ModList.get().isLoaded("createdieselgenerators"))event.register(NativeTowerAdvancedGameTests.class);}
    }
    static String json(){return """
        {"ingredients":[{"ingredient":{"type":"neoforge:single","fluid":"minecraft:lava"},"amount":3,"per_tick":2,"tick_mode":"recipe"}],
         "results":[{"stack":{"id":"minecraft:water","amount":2},"per_tick":2,"tick_mode":"recipe"},
                    {"stack":{"id":"minecraft:water","amount":4},"per_tick":2,"tick_mode":"recipe"}],"processing_time":8,"priority":1}
        """;}
    @GameTest(template="empty",templateNamespace="cwi")
    public static void tfmgPeriodicResultsKeepTheirStageAcrossSave(GameTestHelper h){
        var manager=h.getLevel().getRecipeManager();var previous=manager.getRecipes();var holder=recipe(h,"tfmg:distillation","tfmg",json());
        try{
            manager.replaceRecipes(List.of(holder));var p=new BlockPos(1,1,1);
            DistillationControllerBlockEntity controller=place(h,p,"tfmg:steel_distillation_controller");SteelTankBlockEntity steel=place(h,p.east(),"tfmg:steel_fluid_tank");
            steel.setHeight(4);steel.setWidth(1);steel.activeHeat=1;
            DistillationOutputBlockEntity first=place(h,p.above(),"tfmg:steel_distillation_output"),second=place(h,p.above(2),"tfmg:steel_distillation_output");
            controller.outputs.add(first.getBlockPos());controller.outputs.add(second.getBlockPos());controller.tank.setFluid(new FluidStack(Fluids.LAVA,12));
            first.tank.setFluid(new FluidStack(Fluids.WATER,first.tank.getCapacity()));controller.currentRecipe=controller.findRecipe();controller.manageRecipe(steel);
            h.assertTrue(controller.tank.getFluidAmount()==12,"blocked stage preserves periodic input");first.tank.setFluid(FluidStack.EMPTY);
            for(int i=0;i<4;i++)controller.manageRecipe(steel);controller=reload(h,controller);
            for(int i=0;i<4;i++)controller.manageRecipe(steel);
            h.assertTrue(controller.tank.isEmpty()&&first.tank.getFluidAmount()==8&&second.tank.getFluidAmount()==16,"TFMG keeps identical fluid results in separate stages across save; first="+first.tank.getFluidAmount()+" second="+second.tank.getFluidAmount()+" work="+NativeWork.of(controller).save());
        }finally{manager.replaceRecipes(previous);}h.succeed();
    }
    @GameTest(template="empty",templateNamespace="cwi")
    public static void tfmgVoidOverflowDoesNotBlockOtherStages(GameTestHelper h){
        var manager=h.getLevel().getRecipeManager();var previous=manager.getRecipes();var holder=recipe(h,"tfmg:distillation","void_overflow",json());
        try{
            manager.replaceRecipes(List.of(holder));var p=new BlockPos(1,1,1);
            DistillationControllerBlockEntity controller=place(h,p,"tfmg:steel_distillation_controller");SteelTankBlockEntity steel=place(h,p.east(),"tfmg:steel_fluid_tank");
            steel.setHeight(4);steel.setWidth(1);steel.activeHeat=1;
            DistillationOutputBlockEntity first=place(h,p.above(),"tfmg:steel_distillation_output"),second=place(h,p.above(2),"tfmg:steel_distillation_output");
            first.mode.setValue(DistillationOutputBlockEntity.DistillationOutputMode.VOID_WHEN_FULL.ordinal());
            first.tank.setFluid(new FluidStack(Fluids.WATER,first.tank.getCapacity()-1));
            controller.outputs.add(first.getBlockPos());controller.outputs.add(second.getBlockPos());controller.tank.setFluid(new FluidStack(Fluids.LAVA,12));
            controller.currentRecipe=controller.findRecipe();
            for(int i=0;i<8;i++)controller.manageRecipe(steel);
            h.assertTrue(controller.tank.isEmpty()&&first.tank.getFluidAmount()==first.tank.getCapacity()&&second.tank.getFluidAmount()==16,"void mode caps its own tank while all routed stages complete");
        }finally{manager.replaceRecipes(previous);}h.succeed();
    }
    @GameTest(template="empty",templateNamespace="cwi")
    public static void dieselSelectsAvailableStagesAndPersistsPeriodicWork(GameTestHelper h){
        var manager=h.getLevel().getRecipeManager();var previous=manager.getRecipes();var holder=recipe(h,"createdieselgenerators:distillation","diesel",json());
        var invalid=com.google.gson.JsonParser.parseString(json()).getAsJsonObject();invalid.addProperty("priority",99);invalid.getAsJsonArray("results").add(invalid.getAsJsonArray("results").get(0).deepCopy());
        var taller=recipe(h,"createdieselgenerators:distillation","too_tall",invalid.toString());
        try{
            manager.replaceRecipes(List.of(taller,holder));var p=new BlockPos(1,1,1);
            DistillationTankBlockEntity controller=place(h,p,"createdieselgenerators:distillation_tank");controller.preventConnectivityUpdate();
            DistillationTankBlockEntity first=place(h,p.above(),"createdieselgenerators:distillation_tank"),second=place(h,p.above(2),"createdieselgenerators:distillation_tank");first.preventConnectivityUpdate();second.preventConnectivityUpdate();
            controller.tankInventory.setFluid(new FluidStack(Fluids.LAVA,12));
            for(int i=0;i<4;i++){set(controller,"highestHeatLevel",BlazeBurnerBlock.HeatLevel.KINDLED);set(controller,"numberOfHighestHeat",1);controller.tick();}
            controller=reload(h,controller);controller.preventConnectivityUpdate();
            for(int i=0;i<4;i++){set(controller,"highestHeatLevel",BlazeBurnerBlock.HeatLevel.KINDLED);set(controller,"numberOfHighestHeat",1);controller.tick();}
            h.assertTrue(controller.tankInventory.isEmpty()&&first.tankInventory.getFluidAmount()==8&&second.tankInventory.getFluidAmount()==16,"Diesel skips a recipe missing its third stage and routes periodic results through save; first="+first.tankInventory.getFluidAmount()+" second="+second.tankInventory.getFluidAmount()+" work="+NativeWork.of(controller).save());
        }finally{manager.replaceRecipes(previous);}h.succeed();
    }
}
