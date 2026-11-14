package net.quiwh.cwi;

import static net.quiwh.cwi.NativeAdvancedTestSupport.*;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.crusher.*;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;
import net.quiwh.cwi.compat.processing.*;

@GameTestHolder("cwi") @PrefixGameTestTemplate(false)
public final class NativeProcessingPathGameTests {
    @GameTest(template="empty",templateNamespace="cwi")
    public static void crusherAndSawPeriodicInputsAndTransport(GameTestHelper h){
        for(boolean saw:new boolean[]{false,true}){
            var manager=h.getLevel().getRecipeManager();var previous=manager.getRecipes();
            var recipe=recipe(h,saw?"create:cutting":"create:crushing",saw?"saw":"crusher",periodicItems());
            try{
                manager.replaceRecipes(java.util.List.of(recipe));com.simibubi.create.foundation.recipe.RecipeFinder.LISTENER.onResourceManagerReload(h.getLevel().getServer().getResourceManager());var p=new BlockPos(1,2,1);
                com.simibubi.create.foundation.blockEntity.SmartBlockEntity machine;
                com.simibubi.create.content.processing.recipe.ProcessingInventory inventory;
                if(saw){
                    h.setBlock(p,AllBlocks.MECHANICAL_SAW.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP));
                    SawBlockEntity cutting=h.getBlockEntity(p);cutting.initialize();drive(h,cutting,48);machine=cutting;inventory=cutting.inventory;
                }else{
                    h.setBlock(p,AllBlocks.CRUSHING_WHEEL_CONTROLLER.getDefaultState().setValue(BlockStateProperties.FACING,Direction.DOWN));
                    CrushingWheelControllerBlockEntity crusher=h.getBlockEntity(p);crusher.initialize();crusher.crushingspeed=1;machine=crusher;inventory=crusher.inventory;
                }
                inventory.setStackInSlot(0,new ItemStack(Items.SAND,4));
                for(int i=0;i<2;i++)machine.tick();machine=reload(h,machine);
                for(int i=0;i<40;i++)machine.tick();
                var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(machine.getBlockPos()).inflate(3));
                int clay=drops.stream().filter(e->e.getItem().is(Items.CLAY_BALL)).mapToInt(e->e.getItem().getCount()).sum();
                int diamond=drops.stream().filter(e->e.getItem().is(Items.DIAMOND)).mapToInt(e->e.getItem().getCount()).sum();
                h.assertTrue(clay==4&&diamond==1,(saw?"saw":"crusher")+" transports all periodic products and one pool across save; clay="+clay+" diamond="+diamond+" work="+NativeWork.of(machine).save());
                h.assertTrue(!NativeWork.of(machine).active(),"empty bulk operation is cleared after transport");
                if(saw)h.assertTrue(((ItemStack)get(machine,"playEvent")).is(Items.SAND),"saved advanced saw operation retains native completion sound/particle source");
                for(var drop:drops)drop.discard();h.setBlock(p,net.minecraft.world.level.block.Blocks.AIR);
            }finally{manager.replaceRecipes(previous);com.simibubi.create.foundation.recipe.RecipeFinder.LISTENER.onResourceManagerReload(h.getLevel().getServer().getResourceManager());}
        }h.succeed();
    }
    @GameTest(template="empty",templateNamespace="cwi")
    public static void mixerRetainsCatalystDamagesToolAndUsesRecipeClock(GameTestHelper h){
        var manager=h.getLevel().getRecipeManager();var previous=manager.getRecipes();var chosen=recipe(h,"create:mixing","mixer", """
            {"ingredients":[{"ingredient":{"item":"minecraft:flint"},"catalyst":true},
             {"ingredient":{"item":"minecraft:iron_sword"},"damage":10},
             {"ingredient":{"item":"minecraft:sand"},"per_tick":2,"tick_mode":"recipe"}],
             "results":[{"stack":{"id":"minecraft:clay_ball"},"per_tick":2,"tick_mode":"recipe"}],"processing_time":8,
             "output_pools":[{"entries":[{"stack":{"id":"minecraft:diamond"}}]}]}
            """);
        try{
            manager.replaceRecipes(java.util.List.of(chosen));var p=new BlockPos(1,1,1);
            BasinBlockEntity basin=place(h,p,"create:basin");MechanicalMixerBlockEntity mixer=place(h,p.above(2),"create:mechanical_mixer");drive(h,mixer,256);
            basin.getInputInventory().setStackInSlot(0,new ItemStack(Items.FLINT));var sword=new ItemStack(Items.IRON_SWORD);sword.setDamageValue(sword.getMaxDamage()-2);
            basin.getInputInventory().setStackInSlot(1,sword);basin.getInputInventory().setStackInSlot(2,new ItemStack(Items.SAND,4));
            h.assertTrue(com.simibubi.create.content.processing.basin.BasinRecipe.match(basin,chosen.value()),"advanced basin native matching sees catalyst/tool/periodic resources");
            ((NativeBasinOwner)mixer).cwi$recipe(chosen.value());mixer.running=true;mixer.runningTicks=20;
            mixer.tick();mixer=reload(h,mixer);for(int i=0;i<5;i++)mixer.tick();
            h.assertTrue(total(basin.getInputInventory(),Items.FLINT)==1&&total(basin.getInputInventory(),Items.IRON_SWORD)==0,"basin retains catalyst and allows worn tool to break");
            h.assertTrue(total(basin.getOutputInventory(),Items.CLAY_BALL)==4&&total(basin.getOutputInventory(),Items.DIAMOND)==1,"mixer recipe clock emits all boundaries and one pool");
        }finally{manager.replaceRecipes(previous);com.simibubi.create.foundation.recipe.RecipeFinder.LISTENER.onResourceManagerReload(h.getLevel().getServer().getResourceManager());}h.succeed();
    }
    @GameTest(template="empty",templateNamespace="cwi")
    public static void nativeInvalidChannelsFailCodec(GameTestHelper h){
        var ops=h.getLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        for(String type:new String[]{"create:milling","create:crushing","create:filling","create:pressing"}){
            var serializer=net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER.get(net.minecraft.resources.ResourceLocation.parse(type));
            var json=com.google.gson.JsonParser.parseString("{\"ingredients\":[{\"ingredient\":{\"item\":\"minecraft:flint\"},\"catalyst\":true}],\"results\":[{\"id\":\"minecraft:diamond\"}]}");
            h.assertTrue(serializer.codec().codec().parse(ops,json).isError(),type+" rejects an unsupported retained input rather than ignoring it");
        }h.succeed();
    }
    @GameTest(template="empty",templateNamespace="cwi")
    public static void compactingUsesNativePressCycleAndRetainsCatalyst(GameTestHelper h){
        var manager=h.getLevel().getRecipeManager();var previous=manager.getRecipes();var chosen=recipe(h,"create:compacting","compacting", """
            {"ingredients":[{"ingredient":{"item":"minecraft:flint"},"catalyst":true},
             {"ingredient":{"item":"minecraft:sand"},"per_tick":2,"tick_mode":"recipe"}],
             "results":[{"stack":{"id":"minecraft:clay_ball"},"per_tick":2,"tick_mode":"recipe"}],"processing_time":8,
             "output_pools":[{"entries":[{"stack":{"id":"minecraft:diamond"}}]}]}
            """);
        try{
            manager.replaceRecipes(java.util.List.of(chosen));var p=new BlockPos(1,1,1);
            BasinBlockEntity basin=place(h,p,"create:basin");
            com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity press=place(h,p.above(2),"create:mechanical_press");drive(h,press,64);
            basin.getInputInventory().setStackInSlot(0,new ItemStack(Items.FLINT));basin.getInputInventory().setStackInSlot(1,new ItemStack(Items.SAND,4));
            ((NativeBasinOwner)press).cwi$recipe(chosen.value());press.startProcessingBasin();
            for(int i=0;i<10;i++)press.tick();press=reload(h,press);for(int i=0;i<100;i++)press.tick();
            h.assertTrue(total(basin.getInputInventory(),Items.FLINT)==1&&total(basin.getInputInventory(),Items.SAND)==0,"native compacting retains its catalyst");
            h.assertTrue(total(basin.getOutputInventory(),Items.CLAY_BALL)==4&&total(basin.getOutputInventory(),Items.DIAMOND)==1,"native press cycle completes periodic transaction once across save; work="+NativeWork.of(press).save());
        }finally{manager.replaceRecipes(previous);com.simibubi.create.foundation.recipe.RecipeFinder.LISTENER.onResourceManagerReload(h.getLevel().getServer().getResourceManager());}h.succeed();
    }
    @GameTest(template="empty",templateNamespace="cwi")
    public static void bulkPrioritySkipsUnavailableCountedInputs(GameTestHelper h){
        for(boolean saw:new boolean[]{false,true}){
            var manager=h.getLevel().getRecipeManager();var previous=manager.getRecipes();String type=saw?"create:cutting":"create:crushing";
            String json="""
                {"ingredients":[{"ingredient":{"item":"minecraft:sand"},"count":2}],
                 "results":[{"id":"minecraft:clay_ball"}],"processing_time":8,"priority":10,
                 "output_pools":[{"entries":[{"stack":{"id":"minecraft:diamond"}}]}]}
                """;
            var high=recipe(h,type,"priority_high",json);var low=recipe(h,type,"priority_low",json.replace("\"count\":2","\"count\":1").replace("\"priority\":10","\"priority\":1"));
            try{
                manager.replaceRecipes(java.util.List.of(low,high));com.simibubi.create.foundation.recipe.RecipeFinder.LISTENER.onResourceManagerReload(h.getLevel().getServer().getResourceManager());var p=new BlockPos(1,2,1);
                com.simibubi.create.foundation.blockEntity.SmartBlockEntity machine;
                com.simibubi.create.content.processing.recipe.ProcessingInventory inventory;
                if(saw){
                    h.setBlock(p,AllBlocks.MECHANICAL_SAW.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP));
                    SawBlockEntity cutting=h.getBlockEntity(p);cutting.initialize();drive(h,cutting,48);machine=cutting;inventory=cutting.inventory;
                }else{
                    h.setBlock(p,AllBlocks.CRUSHING_WHEEL_CONTROLLER.getDefaultState().setValue(BlockStateProperties.FACING,Direction.DOWN));
                    CrushingWheelControllerBlockEntity crusher=h.getBlockEntity(p);crusher.initialize();crusher.crushingspeed=1;machine=crusher;inventory=crusher.inventory;
                }
                inventory.setStackInSlot(0,new ItemStack(Items.SAND));machine.tick();machine.tick();
                h.assertTrue(NativeWork.of(machine).save().getString("Recipe").equals(low.id().toString()),(saw?"saw":"crusher")+" falls back to the highest eligible priority when counted inputs are absent: work="+NativeWork.of(machine).save()+" duration="+inventory.remainingTime+" inventory="+inventory.getStackInSlot(0));
                h.setBlock(p,net.minecraft.world.level.block.Blocks.AIR);
            }finally{manager.replaceRecipes(previous);com.simibubi.create.foundation.recipe.RecipeFinder.LISTENER.onResourceManagerReload(h.getLevel().getServer().getResourceManager());}
        }h.succeed();
    }

}
