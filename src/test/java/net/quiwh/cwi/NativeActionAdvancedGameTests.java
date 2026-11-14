package net.quiwh.cwi;

import static net.quiwh.cwi.NativeAdvancedTestSupport.*;
import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import net.quiwh.cwi.compat.processing.NativeDrainProducts;

@GameTestHolder("cwi") @PrefixGameTestTemplate(false)
public final class NativeActionAdvancedGameTests {
    @GameTest(template="empty",templateNamespace="cwi")
    public static void drainQueuesPersistAndDoNotReprocessPoolProducts(GameTestHelper h){
        var manager=h.getLevel().getRecipeManager();var previous=manager.getRecipes();var holder=recipe(h,"create:emptying","drain", """
            {"ingredients":[{"item":"minecraft:sand"}],"results":[{"id":"minecraft:clay_ball"},{"id":"minecraft:water","amount":5}],
             "output_pools":[{"entries":[{"stack":{"id":"minecraft:sand"}}]},{"entries":[{"stack":{"id":"minecraft:diamond"}}]}]}
            """);
        try{
            manager.replaceRecipes(List.of(holder));ItemDrainBlockEntity drain=place(h,new BlockPos(1,1,1),"create:item_drain");
            var inserted=new TransportedItemStack(new ItemStack(Items.SAND));drain.getBehaviour(com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour.TYPE).handleInsertion(inserted,Direction.EAST,false);
            for(int i=0;i<20;i++)drain.tick();
            h.assertTrue(((NativeDrainProducts)drain).cwi$pendingProducts(),"drain stores additional pool products behind held result");
            h.assertTrue(drain.getBehaviour(com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour.TYPE).handleInsertion(inserted,Direction.EAST,false).is(Items.SAND),"pending products reject new belt input");
            drain=reload(h,drain);for(int i=0;i<40;i++)drain.tick();
            var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(drain.getBlockPos()).inflate(2));
            for(Item item:new Item[]{Items.CLAY_BALL,Items.SAND,Items.DIAMOND})h.assertTrue(drops.stream().filter(e->e.getItem().is(item)).mapToInt(e->e.getItem().getCount()).sum()==1,"drain emits one "+item+" without reprocessing saved products");
            h.assertTrue(!((NativeDrainProducts)drain).cwi$pendingProducts()&&drain.getHeldItemStack().isEmpty(),"drain releases all saved products");
        }finally{manager.replaceRecipes(previous);}h.succeed();
    }
    @GameTest(template="empty",templateNamespace="cwi")
    public static void breakingDrainDropsPendingProductsExactlyOnce(GameTestHelper h){
        var manager=h.getLevel().getRecipeManager();var previous=manager.getRecipes();var holder=recipe(h,"create:emptying","drain_break", """
            {"ingredients":[{"item":"minecraft:sand"}],"results":[{"id":"minecraft:clay_ball"},{"id":"minecraft:water","amount":5}],
             "output_pools":[{"entries":[{"stack":{"id":"minecraft:diamond"}}]}]}
            """);
        try{
            manager.replaceRecipes(List.of(holder));ItemDrainBlockEntity drain=place(h,new BlockPos(1,1,1),"create:item_drain");
            var inserted=new TransportedItemStack(new ItemStack(Items.SAND));drain.getBehaviour(com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour.TYPE).handleInsertion(inserted,Direction.EAST,false);
            for(int i=0;i<20;i++)drain.tick();
            h.assertTrue(((NativeDrainProducts)drain).cwi$pendingProducts(),"pending drain result exists before destruction");
            drain=reload(h,drain);drain.destroy();drain.destroy();
            var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(drain.getBlockPos()).inflate(2));
            h.assertTrue(drops.stream().filter(e->e.getItem().is(Items.DIAMOND)).mapToInt(e->e.getItem().getCount()).sum()==1,"destroyed drain drops saved pending results once");
            h.assertTrue(!((NativeDrainProducts)drain).cwi$pendingProducts(),"destruction clears the pending queue");
        }finally{manager.replaceRecipes(previous);}h.succeed();
    }
    @GameTest(template="empty",templateNamespace="cwi")
    public static void spoutTransportsEveryIndependentPool(GameTestHelper h) throws Exception {
        var manager=h.getLevel().getRecipeManager();var previous=manager.getRecipes();var holder=recipe(h,"create:filling","spout", """
            {"ingredients":[{"item":"minecraft:sand"},{"type":"neoforge:single","fluid":"minecraft:water","amount":5}],
             "results":[{"id":"minecraft:clay_ball"}],"output_pools":[{"entries":[{"stack":{"id":"minecraft:flint"}}]},{"entries":[{"stack":{"id":"minecraft:diamond"}}]}]}
            """);
        try{
            manager.replaceRecipes(List.of(holder));SpoutBlockEntity spout=place(h,new BlockPos(1,3,1),"create:spout");
            var tank=(com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour)get(spout,"tank");tank.getPrimaryHandler().setFluid(new FluidStack(Fluids.WATER,10));set(spout,"processingTicks",5);
            var source=new TransportedItemStack(new ItemStack(Items.SAND,2));var results=new ArrayList<TransportedItemStackHandlerBehaviour.TransportedResult>();
            var handler=new TransportedItemStackHandlerBehaviour(spout,(distance,callback)->results.add(callback.apply(source)));
            var method=SpoutBlockEntity.class.getDeclaredMethod("whenItemHeld",TransportedItemStack.class,TransportedItemStackHandlerBehaviour.class);method.setAccessible(true);method.invoke(spout,source,handler);
            h.assertTrue(results.size()==1&&results.getFirst().getOutputs().size()==3,"actual spout path transports normal result and both pools");
            h.assertTrue(results.getFirst().getHeldOutput().stack.is(Items.SAND)&&results.getFirst().getHeldOutput().stack.getCount()==1&&tank.getPrimaryHandler().getFluidAmount()==5,"spout pays exactly one item/fluid batch and retains remaining input");
        }finally{manager.replaceRecipes(previous);}h.succeed();
    }
    @GameTest(template="empty",templateNamespace="cwi")
    public static void deployerRetainsCatalystAndUsesConfiguredDamage(GameTestHelper h){
        for(boolean tool:new boolean[]{false,true}){
            var holder=recipe(h,"create:deploying",tool?"deployer_damage":"deployer_catalyst","{\"ingredients\":[{\"item\":\"minecraft:sand\"},{\"ingredient\":{\"item\":\"minecraft:"+(tool?"iron_sword":"flint")+"\"},"+(tool?"\"damage\":10":"\"catalyst\":true")+"}],\"results\":[{\"id\":\"minecraft:clay_ball\"}],\"output_pools\":[{\"entries\":[{\"stack\":{\"id\":\"minecraft:diamond\"}}]}]}");
            DeployerBlockEntity deployer=place(h,new BlockPos(1,3,1),"create:deployer");var player=deployer.getPlayer();var held=new ItemStack(tool?Items.IRON_SWORD:Items.FLINT);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,held);
            var source=new TransportedItemStack(new ItemStack(Items.SAND));var results=new ArrayList<TransportedItemStackHandlerBehaviour.TransportedResult>();
            var handler=new TransportedItemStackHandlerBehaviour(deployer,(distance,callback)->results.add(callback.apply(source)));
            BeltDeployerCallbacks.activate(source,handler,deployer,holder.value());
            h.assertTrue(results.size()==1&&results.getFirst().getOutputs().size()==2,"deployer native recipe applier includes pool result");
            h.assertTrue(tool?held.getDamageValue()==10:held.getCount()==1,"deployer honors configured tool cost or retained catalyst");
            if(tool){
                var inv=new net.neoforged.neoforge.items.ItemStackHandler(2);inv.setStackInSlot(0,new ItemStack(Items.SAND));
                var unbreakable=new ItemStack(Items.IRON_SWORD);unbreakable.set(net.minecraft.core.component.DataComponents.UNBREAKABLE,new net.minecraft.world.item.component.Unbreakable(true));inv.setStackInSlot(1,unbreakable);
                var recipe=(ItemApplicationRecipe)holder.value();
                h.assertTrue(!recipe.matches(new net.neoforged.neoforge.items.wrapper.RecipeWrapper(inv),h.getLevel()),"configured damage cannot use an unbreakable held tool");
                held.setDamageValue(held.getMaxDamage()-2);
                results.clear();var wornSource=new TransportedItemStack(new ItemStack(Items.SAND));var wornHandler=new TransportedItemStackHandlerBehaviour(deployer,(distance,callback)->results.add(callback.apply(wornSource)));
                BeltDeployerCallbacks.activate(wornSource,wornHandler,deployer,recipe);
                h.assertTrue(held.isEmpty()&&results.getFirst().getOutputs().size()==2,"configured cost allows a worn tool to break while producing its results");
            }
            h.setBlock(new BlockPos(1,3,1),net.minecraft.world.level.block.Blocks.AIR);
        }h.succeed();
    }
}
