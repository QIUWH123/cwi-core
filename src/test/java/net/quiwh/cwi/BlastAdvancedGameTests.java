package net.quiwh.cwi;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.machine.*;
import net.quiwh.cwi.machine.recipe.*;

@GameTestHolder("cwi")
@PrefixGameTestTemplate(false)
public final class BlastAdvancedGameTests {
    static MachineRecipe recipe(GameTestHelper h, String json) {
        return MachineRecipes.BLAST_FURNACE.serializer().codec().codec().parse(h.getLevel().registryAccess()
                .createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(json)).getOrThrow();
    }

    @GameTest(template="empty", templateNamespace="cwi")
    public static void furnaceAcceptsAdvancedRecipesButRejectsToolCosts(GameTestHelper h) {
        var recipe = recipe(h, """
            {"ingredients":[{"item":"minecraft:sand"},{"ingredient":{"item":"minecraft:flint"},"catalyst":true},
              {"ingredient":{"type":"neoforge:single","fluid":"minecraft:water"},"amount":5,"per_tick":2,"tick_mode":"recipe"}],
             "results":[{"id":"minecraft:clay_ball"}],"priority":10,
             "output_pools":[{"entries":[{"stack":{"id":"minecraft:gravel"}}],"show_outputs":true}]}
            """);
        h.assertTrue(recipe.priority()==10 && recipe.outputPools().size()==1 && recipe.periodic(), "furnace retains advanced options");
        for (String cost : new String[]{"damage", "energy"}) {
            boolean rejected=false;
            try { recipe(h,"{\"ingredients\":[{\"ingredient\":{\"item\":\"minecraft:iron_sword\"},\""+cost+"\":1}]}"); }
            catch (RuntimeException expected) { rejected=true; }
            h.assertTrue(rejected,"furnace rejects tool cost "+cost);
        }
        h.succeed();
    }

    @GameTest(template="empty", templateNamespace="cwi")
    public static void legacyFurnaceStorageAppendsOutputSlots(GameTestHelper h) {
        var pos=new BlockPos(1,1,1);h.setBlock(pos,MachineBlocks.BLAST_FURNACE.get());
        BlastFurnaceBlockEntity furnace=h.getBlockEntity(pos);
        var legacy=new net.neoforged.neoforge.items.ItemStackHandler(8);
        legacy.setStackInSlot(0,new ItemStack(Items.IRON_INGOT,17));legacy.setStackInSlot(7,new ItemStack(Items.COAL,9));
        var saved=furnace.saveWithoutMetadata(h.getLevel().registryAccess());saved.put("Items",legacy.serializeNBT(h.getLevel().registryAccess()));
        saved.remove("MachineDataVersion");furnace.loadCustomOnly(saved,h.getLevel().registryAccess());
        h.assertTrue(furnace.items().getSlots()==14,"six dedicated item-output slots appended");
        h.assertTrue(furnace.getItem(0).getCount()==17 && furnace.getItem(7).getCount()==9,"legacy inputs keep their slot identities");
        h.assertTrue(furnace.saveWithoutMetadata(h.getLevel().registryAccess()).getInt("MachineDataVersion")==2,"furnace storage version incremented");
        h.succeed();
    }

    @GameTest(template="machine_empty", templateNamespace="cwi")
    public static void furnaceBatchPausesAndResumesAtomicPeriodicIo(GameTestHelper h) {
        NativeMachineGameTests.buildFurnace(h,net.minecraft.core.Direction.NORTH,4);
        var pos=new BlockPos(6,2,6);h.setBlock(pos.north(),net.minecraft.world.level.block.Blocks.STONE);
        BlastFurnaceBlockEntity furnace=h.getBlockEntity(pos);
        FurnaceHatchBlockEntity hatch=h.getBlockEntity(pos.above());
        var manager=h.getLevel().getRecipeManager();var original=java.util.List.copyOf(manager.getRecipes());
        String source="""
            {"ingredients":[{"ingredient":{"item":"minecraft:sand"},"count":2},
              {"ingredient":{"item":"minecraft:flint"},"catalyst":true},
              {"ingredient":{"item":"minecraft:redstone"},"per_tick":2},
              {"ingredient":{"type":"neoforge:single","fluid":"minecraft:water"},"amount":5,"per_tick":2,"tick_mode":"recipe"}],
             "results":[{"id":"minecraft:clay_ball"},{"stack":{"id":"minecraft:gold_nugget"},"per_tick":2},{"id":"minecraft:lava","amount":25}],
             "processing_time":20,"maximum_heat":2200,"priority":10,
             "output_pools":[{"entries":[{"stack":{"id":"minecraft:gravel"},"weight":3},{"stack":{"id":"minecraft:cobblestone"}}],"show_outputs":true},
              {"entries":[{"stack":{"id":"minecraft:glowstone_dust"}}]}]}
            """;
        var id=net.minecraft.resources.ResourceLocation.parse("cwi:furnace_fixture/z_high");
        try {
            var high=recipe(h,source);
            var low=recipe(h,"{\"ingredients\":[{\"item\":\"minecraft:sand\"}],\"results\":[{\"id\":\"minecraft:emerald\"}],\"priority\":-1}");
            manager.replaceRecipes(java.util.List.of(new net.minecraft.world.item.crafting.RecipeHolder<>(id,high),
                new net.minecraft.world.item.crafting.RecipeHolder<>(net.minecraft.resources.ResourceLocation.parse("cwi:furnace_fixture/a_low"),low)));
            furnace.items().setStackInSlot(0,new ItemStack(Items.SAND,4));
            furnace.items().setStackInSlot(1,new ItemStack(Items.FLINT,2));
            furnace.items().setStackInSlot(2,new ItemStack(Items.REDSTONE,32));
            furnace.fluids().setFluid(0,new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000));
            temperature(h,furnace,2000);furnace.tick();
            h.assertTrue(furnace.saveWithoutMetadata(h.getLevel().registryAccess()).getString("Recipe").equals(id.toString()),"higher priority selected despite ID");
            h.assertTrue(furnace.saveWithoutMetadata(h.getLevel().registryAccess()).getInt("Batch")==2,"catalysts reserve one unit per copy");
            h.assertTrue(furnace.getItem(0).isEmpty() && furnace.getItem(1).getCount()==2,"periodic recipe pays ordinary inputs upfront and retains catalysts");
            for(int slot=8;slot<14;slot++)furnace.items().setStackInSlot(slot,new ItemStack(Items.DIAMOND,64));
            int progress=furnace.progress(),water=furnace.getFluid(0).getAmount(),red=furnace.getItem(2).getCount();
            furnace.tick();
            h.assertTrue(furnace.progress()==progress && furnace.getFluid(0).getAmount()==water && furnace.getItem(2).getCount()==red,"full item outputs pause all due IO and progress");
            var pending=furnace.saveWithoutMetadata(h.getLevel().registryAccess());furnace.loadCustomOnly(pending,h.getLevel().registryAccess());furnace.tick();
            h.assertTrue(furnace.progress()==progress && furnace.saveWithoutMetadata(h.getLevel().registryAccess()).getCompound("Operation").equals(pending.getCompound("Operation")),"blocked saved operation retains exact pending clock and RNG");
            var presentationReload=recipe(h,source.replace("\"priority\":10","\"priority\":-10").replace("\"show_outputs\":true","\"show_outputs\":false"));
            var newPriorityWinner=recipe(h,"{\"ingredients\":[{\"item\":\"minecraft:flint\"}],\"results\":[{\"id\":\"minecraft:emerald\"}],\"priority\":100}");
            manager.replaceRecipes(java.util.List.of(new net.minecraft.world.item.crafting.RecipeHolder<>(id,presentationReload),
                new net.minecraft.world.item.crafting.RecipeHolder<>(net.minecraft.resources.ResourceLocation.parse("cwi:furnace_fixture/new_winner"),newPriorityWinner)));
            furnace.tick();
            var afterReload=furnace.saveWithoutMetadata(h.getLevel().registryAccess());
            h.assertTrue(afterReload.getString("Recipe").equals(id.toString()) && afterReload.getCompound("Operation").equals(pending.getCompound("Operation")),
                    "priority and visibility reload retains the active paid job and never preempts it");
            for(int slot=8;slot<14;slot++)furnace.items().setStackInSlot(slot,ItemStack.EMPTY);
            temperature(h,furnace,2400);furnace.tick();h.assertTrue(furnace.progress()==progress,"excess heat pauses without rewinding");
            temperature(h,furnace,2000);furnace.items().setStackInSlot(1,ItemStack.EMPTY);furnace.tick();
            h.assertTrue(furnace.progress()==progress,"missing reserved catalyst pauses paid batch");
            furnace.items().setStackInSlot(1,new ItemStack(Items.FLINT,2));
            int duration=furnace.duration();
            for(int tick=progress;tick<duration;tick++) {
                furnace.tick();
                if(furnace.progress()>0)h.assertTrue(java.util.stream.IntStream.range(8,14).filter(s->!furnace.getItem(s).isEmpty()).count()==1,
                        "periodic results merge before occupying more output slots");
            }
            h.assertTrue(furnace.getFluid(0).getAmount()==900 && furnace.getFluid(4).getAmount()==50,
                    "base-clock fluid costs and completion fluids scale per copy: water="+furnace.getFluid(0).getAmount()
                    +" lava="+furnace.getFluid(4).getAmount()+" duration="+duration+" progress="+furnace.progress());
            h.assertTrue(furnace.getItem(2).getCount()==32-duration/2*2 && count(furnace,Items.GOLD_NUGGET)==duration/2*2,"working-clock IO scales with successful ticks and batch");
            h.assertTrue(count(furnace,Items.CLAY_BALL)==2 && count(furnace,Items.GLOWSTONE_DUST)==2
                && count(furnace,Items.GRAVEL)+count(furnace,Items.COBBLESTONE)==2,"each copy rolls every independent pool once");
            var needsOwnOutput=recipe(h,"{\"ingredients\":[{\"item\":\"minecraft:clay_ball\"}],\"results\":[{\"id\":\"minecraft:emerald\"}]}");
            manager.replaceRecipes(java.util.List.of(new net.minecraft.world.item.crafting.RecipeHolder<>(id,needsOwnOutput)));
            furnace.tick();
            h.assertTrue(furnace.progress()==0 && count(furnace,Items.CLAY_BALL)==2 && count(furnace,Items.EMERALD)==0,
                    "solid output storage is never used as recipe input");
            var output=hatch.getItemCapability(net.minecraft.core.Direction.DOWN);
            h.assertTrue(output.getSlots()==6 && !output.insertItem(0,new ItemStack(Items.DIRT),false).isEmpty(),"bottom hatch exposes six extraction-only outputs");
            h.assertTrue(!output.extractItem(0,64,true).isEmpty() && output.extractItem(0,64,false).getCount()>0,"automation can simulate and extract solids");
            h.assertTrue(hatch.getItemCapability(net.minecraft.core.Direction.UP).insertItem(8,new ItemStack(Items.DIRT),false).getCount()==1,"other hatch sides cannot insert into output slots");
        } finally {manager.replaceRecipes(original);}
        h.succeed();
    }
    private static int count(BlastFurnaceBlockEntity furnace,net.minecraft.world.item.Item item) {
        int result=0;for(int slot=8;slot<14;slot++)if(furnace.getItem(slot).is(item))result+=furnace.getItem(slot).getCount();return result;
    }
    private static void temperature(GameTestHelper h,BlastFurnaceBlockEntity furnace,double value) {
        var saved=furnace.saveWithoutMetadata(h.getLevel().registryAccess());saved.putDouble("Temperature",value);furnace.loadCustomOnly(saved,h.getLevel().registryAccess());
    }

    @GameTest(template="empty", templateNamespace="cwi")
    public static void scriptedFurnaceAdvancedIoReplacesAndSynchronizes(GameTestHelper h) {
        var recipe=(MachineRecipe)h.getLevel().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("cwi:callable/furnace_advanced")).orElseThrow().value();
        h.assertTrue(recipe.priority()==10 && recipe.itemOutputs().getFirst().stack().is(Items.BRICK)
                && recipe.itemInputs().get(1).settings().catalyst(),"actual KubeJS mixed furnace constructor and metadata");
        h.assertTrue(recipe.outputPools().getFirst().showOutputs() && !recipe.outputPools().get(1).showOutputs()
                && recipe.outputPools().getFirst().entries().getFirst().stack().is(Items.GRAVEL)
                && recipe.outputPools().getFirst().entries().getFirst().weight()==3,"each builder pool retains its visibility and weight");
        var replaced=(MachineRecipe)h.getLevel().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("cwi:callable/furnace_replace")).orElseThrow().value();
        h.assertTrue(replaced.outputPools().getFirst().showOutputs() && !replaced.outputPools().get(1).showOutputs()
                && replaced.outputPools().getFirst().entries().getFirst().stack().is(Items.GOLD_NUGGET)
                && replaced.outputPools().getFirst().entries().getFirst().weight()==7,"loaded furnace pools retain visibility and descriptor replacement");
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            MachineRecipes.BLAST_FURNACE.serializer().streamCodec().encode(buffer,recipe);
            var copy=MachineRecipes.BLAST_FURNACE.serializer().streamCodec().decode(buffer);
            h.assertTrue(copy.fingerprint(h.getLevel().registryAccess()).equals(recipe.fingerprint(h.getLevel().registryAccess())),
                    "furnace packet preserves gameplay fingerprint");
            h.assertTrue(copy.priority()==10 && copy.outputPools().getFirst().showOutputs() && buffer.readableBytes()==0,"complete furnace packet metadata and consumption");
        } finally {buffer.release();}
        h.succeed();
    }

    @GameTest(template="empty", templateNamespace="cwi")
    public static void machinePeriodicIntervalsSurviveRecipePackets(GameTestHelper h) {
        var ops=h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for(var kind:java.util.List.of(MachineRecipes.BLAST_FURNACE,MachineRecipes.MIXING))
            for(int interval:new int[]{0,1,2,20,250}) for(String clock:new String[]{"working","recipe"}) {
                if(interval==0 && clock.equals("recipe")) continue;
                String timing="\"per_tick\":"+(interval==0 ? "false" : interval==1 ? "true" : interval)
                        +",\"tick_mode\":\""+clock+"\"";
                var recipe=kind.serializer().codec().codec().parse(ops,JsonParser.parseString("""
                    {"ingredients":[{"ingredient":{"item":"minecraft:sand"},%s},
                     {"ingredient":{"fluid":"minecraft:water"},"amount":5,%s}],
                     "results":[{"stack":{"id":"minecraft:brick"},%s},
                     {"stack":{"id":"minecraft:lava","amount":5},%s}]}
                    """.formatted(timing,timing,timing,timing))).getOrThrow();
                var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
                try {
                    kind.serializer().streamCodec().encode(buffer,recipe);
                    var synced=kind.serializer().streamCodec().decode(buffer);
                    for(var settings:java.util.List.of(synced.itemInputs().getFirst().settings(),synced.fluidInputs().getFirst().settings(),
                            synced.itemOutputs().getFirst().settings(),synced.fluidOutputs().getFirst().settings()))
                        h.assertTrue(settings.interval()==interval && settings.clock().name().equalsIgnoreCase(clock),
                                kind.getId()+" packet retains every IO interval "+interval+" and clock "+clock);
                    h.assertTrue(buffer.readableBytes()==0,"packet fully consumed");
                } finally {buffer.release();}
            }
        h.succeed();
    }
}
