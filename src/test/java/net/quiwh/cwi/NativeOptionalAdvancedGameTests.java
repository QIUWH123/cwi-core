package net.quiwh.cwi;

import static net.quiwh.cwi.NativeAdvancedTestSupport.*;
import com.drmangotea.tfmg.content.machinery.vat.base.VatBlockEntity;
import com.drmangotea.tfmg.recipes.VatMachineRecipe;
import com.mrh0.createaddition.blocks.rolling_mill.RollingMillBlockEntity;
import com.negodya1.vintageimprovements.content.kinetics.vibration.VibratingTableBlockEntity;
import com.negodya1.vintageimprovements.content.kinetics.centrifuge.CentrifugeBlockEntity;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import io.github.hadron13.rubberworks.blocks.compressor.CompressorBlockEntity;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.quiwh.cwi.compat.processing.*;

@PrefixGameTestTemplate(false)
public final class NativeOptionalAdvancedGameTests {
    @EventBusSubscriber(modid=CwiCore.ID) public static final class Registration{
        @SubscribeEvent public static void register(RegisterGameTestsEvent event){if(List.of("tfmg","createaddition","rubberworks","vintageimprovements","createmetallurgy").stream().allMatch(id->ModList.get().isLoaded(id)))event.register(NativeOptionalAdvancedGameTests.class);}
    }
    @GameTest(template="empty",templateNamespace="cwi")
    public static void rollingAndVibratingPeriodicItemsSaveAndCapacity(GameTestHelper h){
        for(boolean rolling:new boolean[]{true,false}){
            var manager=h.getLevel().getRecipeManager();var previous=manager.getRecipes();var recipe=recipe(h,rolling?"createaddition:rolling":"vintageimprovements:vibrating",rolling?"rolling":"vibrating",periodicItems());
            try{
                manager.replaceRecipes(List.of(recipe));var p=new BlockPos(1,1,1);KineticBlockEntity machine=place(h,p,rolling?"createaddition:rolling_mill":"vintageimprovements:vibrating_table");drive(h,machine,64);
                IItemHandlerModifiable input=rolling?((RollingMillBlockEntity)machine).inputInv:((VibratingTableBlockEntity)machine).inputInv;
                IItemHandlerModifiable output=rolling?((RollingMillBlockEntity)machine).outputInv:((VibratingTableBlockEntity)machine).outputInv;
                input.setStackInSlot(0,new ItemStack(Items.SAND,4));for(int slot=0;slot<output.getSlots();slot++)output.setStackInSlot(slot,new ItemStack(Items.COBBLESTONE,64));
                machine.tick();h.assertTrue(input.getStackInSlot(0).getCount()==4,"full output pauses without paying periodic input");
                for(int slot=0;slot<output.getSlots();slot++)output.setStackInSlot(slot,ItemStack.EMPTY);
                machine.tick();
                int timer=rolling?((RollingMillBlockEntity)machine).timer:((VibratingTableBlockEntity)machine).timer;
                h.assertTrue(timer>0&&machine.getUpdateTag(h.getLevel().registryAccess()).getInt("Timer")==timer,"advanced rolling/vibrating synchronizes a live native particle timer");
                machine=reload(h,machine);for(int i=0;i<12;i++)machine.tick();
                output=rolling?((RollingMillBlockEntity)machine).outputInv:((VibratingTableBlockEntity)machine).outputInv;
                h.assertTrue(total(output,Items.CLAY_BALL)==4&&total(output,Items.DIAMOND)==1,(rolling?"rolling":"vibrating")+" produces every periodic item and one pool across native reload; work="+NativeWork.of(machine).save()+" clay="+total(output,Items.CLAY_BALL));
                h.setBlock(p,net.minecraft.world.level.block.Blocks.AIR);
            }finally{manager.replaceRecipes(previous);}
        }h.succeed();
    }
    @GameTest(template="empty",templateNamespace="cwi")
    public static void vatCatalystsPeriodicFluidsAndNativeConstraints(GameTestHelper h){
        var manager=h.getLevel().getRecipeManager();var previous=manager.getRecipes();var holder=recipe(h,"tfmg:vat","vat", """
            {"ingredients":[{"ingredient":{"item":"minecraft:flint"},"catalyst":true},
             {"ingredient":{"type":"neoforge:single","fluid":"minecraft:water"},"amount":3,"per_tick":2,"tick_mode":"recipe"}],
             "results":[{"stack":{"id":"minecraft:lava","amount":2},"per_tick":2,"tick_mode":"recipe"}],"processing_time":8,
             "output_pools":[{"entries":[{"stack":{"id":"minecraft:diamond"}}]}]}
            """);
        try{
            manager.replaceRecipes(List.of(holder));VatBlockEntity vat=place(h,new BlockPos(1,1,1),"tfmg:steel_chemical_vat");
            vat.inputInventory.setStackInSlot(0,new ItemStack(Items.FLINT));vat.inputTank.getPrimaryHandler().fill(new FluidStack(Fluids.WATER,12),FluidAction.EXECUTE);
            h.assertTrue(vat.inputTank.getPrimaryHandler().getFluidAmount()==12,"vat fixture fills primary input");
            vat.recipe=(VatMachineRecipe)holder.value();vat.handleRecipe();var saved=vat.saveWithoutMetadata(h.getLevel().registryAccess());vat=reload(h,vat);vat.recipe=(VatMachineRecipe)holder.value();
            for(int i=0;i<10;i++)vat.handleRecipe();
            h.assertTrue(vat.inputTank.isEmpty()&&vat.outputTank.getPrimaryHandler().getFluidAmount()==8&&total(vat.outputInventory,Items.DIAMOND)==1,"vat periodic fluids and independent pool resume exactly once; input="+vat.inputTank.getPrimaryHandler().getFluid()+" output="+vat.outputTank.getPrimaryHandler().getFluid()+" catalyst="+vat.inputInventory.getStackInSlot(0)+" saved="+saved+" work="+NativeWork.of(vat).save());
            h.assertTrue(total(vat.inputInventory,Items.FLINT)==1,"vat catalyst remains available");
        }finally{manager.replaceRecipes(previous);}h.succeed();
    }
    @GameTest(template="empty",templateNamespace="cwi")
    public static void centrifugeRequiresBasinsAndRetainsCatalyst(GameTestHelper h){
        var manager=h.getLevel().getRecipeManager();var previous=manager.getRecipes();var holder=recipe(h,"vintageimprovements:centrifugation","centrifuge", """
            {"ingredients":[{"ingredient":{"item":"minecraft:flint"},"catalyst":true},{"item":"minecraft:sand"}],
             "results":[{"stack":{"id":"minecraft:clay_ball"},"per_tick":2,"tick_mode":"recipe"}],"processing_time":8,
             "output_pools":[{"entries":[{"stack":{"id":"minecraft:diamond"}}]}]}
            """);
        try{
            manager.replaceRecipes(List.of(holder));CentrifugeBlockEntity machine=place(h,new BlockPos(1,1,1),"vintageimprovements:centrifuge");drive(h,machine,128);
            machine.inputInv.setStackInSlot(0,new ItemStack(Items.FLINT));machine.inputInv.setStackInSlot(1,new ItemStack(Items.SAND));
            for(int i=0;i<4;i++)machine.tick();h.assertTrue(total(machine.inputInv,Items.SAND)==1&&total(machine.outputInv,Items.CLAY_BALL)==0,"centrifuge cannot process without four native basins");
            for(int i=0;i<4;i++)machine.addBasin(AllBlocks.BASIN.asStack());machine.tick();machine=reload(h,machine);for(int i=0;i<20;i++)machine.tick();
            h.assertTrue(total(machine.inputInv,Items.FLINT)==1&&total(machine.inputInv,Items.SAND)==0&&total(machine.outputInv,Items.CLAY_BALL)==4&&total(machine.outputInv,Items.DIAMOND)==1,"centrifuge uses native basins and typed periodic transaction; work="+NativeWork.of(machine).save());
        }finally{manager.replaceRecipes(previous);}h.succeed();
    }
    @GameTest(template="empty",templateNamespace="cwi")
    public static void compressorFluidProductsAreMouthOnlyAndPersist(GameTestHelper h){
        var manager=h.getLevel().getRecipeManager();var previous=manager.getRecipes();var holder=recipe(h,"rubberworks:compressing","compressor", """
            {"ingredients":[{"ingredient":{"type":"neoforge:single","fluid":"minecraft:water"},"amount":3,"per_tick":2,"tick_mode":"recipe"}],
             "results":[{"stack":{"id":"minecraft:lava","amount":2},"per_tick":2,"tick_mode":"recipe"}],"processing_time":8,
             "output_pools":[{"entries":[{"stack":{"id":"minecraft:diamond"}}]}]}
            """);
        try{
            manager.replaceRecipes(List.of(holder));var p=new BlockPos(1,2,1);h.setBlock(p.below(),net.minecraft.world.level.block.Blocks.CAMPFIRE);
            CompressorBlockEntity machine=place(h,p,"rubberworks:compressor");var motor=drive(h,machine,64);if(!machine.validSpeed())motor.generatedSpeed.setValue(-64);
            machine.tank.getPrimaryHandler().fill(new FluidStack(Fluids.WATER,12),FluidAction.EXECUTE);
            for(int i=0;i<3;i++)machine.tick();machine=reload(h,machine);for(int i=0;i<12;i++)machine.tick();
            var products=machine.getBehaviour(SmartFluidTankBehaviour.OUTPUT);
            h.assertTrue(products!=null&&products.getPrimaryHandler().getFluidAmount()==8&&total(machine.output,Items.DIAMOND)==1,"compressor saves item/fluid products and clocks; work="+NativeWork.of(machine).save());
            for(Direction side:Direction.values()){
                var item=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,machine.getBlockPos(),side);h.assertTrue(item==null||item.getSlots()==0,"compressor products have no external item extraction");
                var fluid=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,machine.getBlockPos(),side);h.assertTrue(fluid==null||fluid.drain(1000,FluidAction.EXECUTE).isEmpty(),"compressor products have no external fluid extraction");
            }
            var mouth=p.below().relative(machine.getBlockState().getValue(HorizontalKineticBlock.HORIZONTAL_FACING));BasinBlockEntity receiver=place(h,mouth,"create:basin");
            for(int i=0;i<5;i++)machine.tick();
            h.assertTrue(total(receiver.getInputInventory(),Items.DIAMOND)==1&&receiver.getTanks().getFirst().getPrimaryHandler().getFluidAmount()==8,"native mouth transfers both product channels to a basin");
        }finally{manager.replaceRecipes(previous);}h.succeed();
    }
    @GameTest(template="empty",templateNamespace="cwi")
    public static void foundryMeltingAndAlloyingKeepHeatAndLidConstraints(GameTestHelper h){
        for(boolean alloy:new boolean[]{false,true}){
            var manager=h.getLevel().getRecipeManager();var previous=manager.getRecipes();var holder=recipe(h,alloy?"createmetallurgy:alloying":"createmetallurgy:melting",alloy?"alloying":"melting", """
                {"ingredients":[{"ingredient":{"item":"minecraft:flint"},"catalyst":true},
                 {"ingredient":{"item":"minecraft:sand"},"per_tick":2,"tick_mode":"recipe"}],
                 "results":[{"stack":{"id":"minecraft:water","amount":2},"per_tick":2,"tick_mode":"recipe"}],
                 "processing_time":8,"heat_requirement":"heated","output_pools":[{"entries":[{"stack":{"id":"minecraft:diamond"}}]}]}
                """);
            try{
                manager.replaceRecipes(List.of(holder));var p=new BlockPos(1,2,1);BasinBlockEntity basin=place(h,p,"createmetallurgy:foundry_basin");
                var lidPos=p.above();h.setBlock(lidPos,net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("createmetallurgy:foundry_lid")).defaultBlockState()
                        .setValue(fr.lucreeper74.createmetallurgy.content.blocks.foundry_lid.FoundryLidBlock.OPEN,false));
                SmartBlockEntity lid=h.getBlockEntity(lidPos);lid.initialize();
                SmartBlockEntity machine=alloy?place(h,p.above(2),"createmetallurgy:foundry_mixer"):lid;
                if(alloy)drive(h,(KineticBlockEntity)machine,256);
                basin.getInputInventory().setStackInSlot(0,new ItemStack(Items.FLINT));basin.getInputInventory().setStackInSlot(1,new ItemStack(Items.SAND,4));
                ((NativeBasinOwner)machine).cwi$recipe(holder.value());set(machine,"running",true);if(alloy)set(machine,"runningTicks",20);
                for(int i=0;i<3;i++)machine.tick();h.assertTrue(total(basin.getInputInventory(),Items.SAND)==4,"foundry cannot process without native heat");
                h.setBlock(p.below(),AllBlocks.BLAZE_BURNER.getDefaultState().setValue(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL,com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED));
                basin.tick();
                machine.tick();machine=reload(h,machine);
                h.setBlock(lidPos,h.getBlockState(lidPos).setValue(fr.lucreeper74.createmetallurgy.content.blocks.foundry_lid.FoundryLidBlock.OPEN,true));
                NativeWork.of(machine).restore(h.getLevel());var progress=NativeWork.of(machine).progress();for(int i=0;i<3;i++)machine.tick();h.assertTrue(NativeWork.of(machine).progress()==progress,"open foundry lid pauses advanced processing");
                h.setBlock(lidPos,h.getBlockState(lidPos).setValue(fr.lucreeper74.createmetallurgy.content.blocks.foundry_lid.FoundryLidBlock.OPEN,false));
                for(int i=0;i<30;i++)machine.tick();
                h.assertTrue(total(basin.getInputInventory(),Items.FLINT)==1&&total(basin.getInputInventory(),Items.SAND)==0&&total(basin.getOutputInventory(),Items.DIAMOND)==1&&basin.getTanks().getSecond().getPrimaryHandler().getFluidAmount()==8,"foundry "+(alloy?"alloying":"melting")+" resumes native typed IO exactly once; work="+NativeWork.of(machine).save());
                h.setBlock(p.above(2),net.minecraft.world.level.block.Blocks.AIR);h.setBlock(lidPos,net.minecraft.world.level.block.Blocks.AIR);h.setBlock(p,net.minecraft.world.level.block.Blocks.AIR);h.setBlock(p.below(),net.minecraft.world.level.block.Blocks.AIR);
            }finally{manager.replaceRecipes(previous);}
        }h.succeed();
    }
}
