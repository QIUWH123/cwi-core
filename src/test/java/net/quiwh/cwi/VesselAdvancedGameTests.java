package net.quiwh.cwi;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.machine.*;
import net.quiwh.cwi.machine.recipe.*;

@GameTestHolder("cwi")
@PrefixGameTestTemplate(false)
public final class VesselAdvancedGameTests {
    @net.neoforged.fml.common.EventBusSubscriber(modid="cwi", bus=net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
    public static final class EnergyFixture {
        @net.neoforged.bus.api.SubscribeEvent
        public static void capabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
            event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM, (stack, unused) ->
                new net.neoforged.neoforge.energy.IEnergyStorage() {
                    private int stored() { return stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getInt("Energy"); }
                    public int receiveEnergy(int n, boolean simulate) { return 0; }
                    public int extractEnergy(int n, boolean simulate) {
                        int amount=Math.min(n,stored());
                        if (!simulate) { var tag=new net.minecraft.nbt.CompoundTag(); tag.putInt("Energy",stored()-amount);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag)); }
                        return amount;
                    }
                    public int getEnergyStored(){return stored();} public int getMaxEnergyStored(){return 1000;}
                    public boolean canExtract(){return true;} public boolean canReceive(){return false;}
                }, Items.COMPASS);
        }
    }
    @GameTest(template="empty", templateNamespace="cwi")
    public static void energyCostsAndDamageStayAtomicWithBlockedOutputs(GameTestHelper h) {
        var recipe=recipe(h,"""
            {"ingredients":[{"ingredient":{"item":"minecraft:compass"},"energy":20},
              {"ingredient":{"item":"minecraft:iron_sword"},"damage":10}],"results":[{"id":"minecraft:clay_ball"}]}
            """);
        var items=new ItemStackHandler(3);var compass=new ItemStack(Items.COMPASS);var tag=new net.minecraft.nbt.CompoundTag();tag.putInt("Energy",40);
        compass.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));items.setStackInSlot(0,compass);
        items.setStackInSlot(1,new ItemStack(Items.IRON_SWORD));items.setStackInSlot(2,new ItemStack(Items.DIAMOND,64));
        var fluids=new MultiTank(()->{},1000);var op=new VesselOperation(91);op.prepare(1);
        h.assertTrue(!VesselTransaction.step(recipe,op,items,fluids,h.getLevel()),"output block pauses energy and wear together");
        h.assertTrue(items.getStackInSlot(0).getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM).getEnergyStored()==40
                && items.getStackInSlot(1).getDamageValue()==0,"failed transaction spends neither energy nor durability");
        op=VesselOperation.load(op.save());items.setStackInSlot(2,ItemStack.EMPTY);
        h.assertTrue(VesselTransaction.step(recipe,op,items,fluids,h.getLevel()),"saved cost transaction resumes");
        h.assertTrue(items.getStackInSlot(0).getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM).getEnergyStored()==20
                && items.getStackInSlot(1).getDamageValue()==10 && count(items,Items.CLAY_BALL)==1,"exactFE and wear once, both original items kept");
        var insufficient=new ItemStackHandler(3);insufficient.setStackInSlot(0,new ItemStack(Items.COMPASS));insufficient.setStackInSlot(1,new ItemStack(Items.IRON_SWORD));
        var empty=new VesselOperation(1);empty.prepare(1);h.assertTrue(!VesselTransaction.step(recipe,empty,insufficient,fluids,h.getLevel()),"no partial energy payment");
        var unbreakable=new ItemStack(Items.IRON_SWORD);unbreakable.set(net.minecraft.core.component.DataComponents.UNBREAKABLE,new net.minecraft.world.item.component.Unbreakable(true));
        items.setStackInSlot(1,unbreakable);var noWear=new VesselOperation(2);noWear.prepare(1);
        h.assertTrue(!VesselTransaction.step(recipe,noWear,items,fluids,h.getLevel()),"unbreakable tools cannot pay wear");h.succeed();
    }
    @GameTest(template="empty", templateNamespace="cwi")
    public static void priorityOrderingAndTieRulesFollowNativeIndex(GameTestHelper h) {
        var high=recipe(h,"{\"ingredients\":[{\"item\":\"minecraft:sand\"}],\"priority\":10}");
        var low=recipe(h,"{\"ingredients\":[{\"item\":\"minecraft:sand\"},{\"item\":\"minecraft:sand\"}],\"priority\":-1}");
        var manager=new net.minecraft.world.item.crafting.RecipeManager(h.getLevel().registryAccess());
        manager.replaceRecipes(List.of(new net.minecraft.world.item.crafting.RecipeHolder<>(net.minecraft.resources.ResourceLocation.parse("cwi:z_high"),high),
                new net.minecraft.world.item.crafting.RecipeHolder<>(net.minecraft.resources.ResourceLocation.parse("cwi:a_low"),low)));
        h.assertTrue(MachineRecipeIndex.sorted(manager,MachineRecipes.MIXING.recipeType()).getFirst().value()==high,"higher priority overrides specificity and ID");
        manager.replaceRecipes(List.of(new net.minecraft.world.item.crafting.RecipeHolder<>(net.minecraft.resources.ResourceLocation.parse("cwi:a_high"),high),
                new net.minecraft.world.item.crafting.RecipeHolder<>(net.minecraft.resources.ResourceLocation.parse("cwi:z_high"),high)));
        h.assertTrue(MachineRecipeIndex.sorted(manager,MachineRecipes.MIXING.recipeType()).getFirst().id().getPath().equals("a_high"),"equal priority ties use full ID");h.succeed();
    }
    @GameTest(template="empty", templateNamespace="cwi")
    public static void activeVesselNeverPreemptsAndSurvivesPriorityOnlyReload(GameTestHelper h) {
        var manager=h.getLevel().getRecipeManager();var original=List.copyOf(manager.getRecipes());
        var lowId=net.minecraft.resources.ResourceLocation.parse("cwi:vessel_fixture/low");
        var highId=net.minecraft.resources.ResourceLocation.parse("cwi:vessel_fixture/high");
        String lowJson="""
            {"ingredients":[{"item":"minecraft:nether_star"}],"results":[{"id":"minecraft:emerald"}],"processing_time":100,"priority":-1}
            """;
        String highJson="""
            {"ingredients":[{"item":"minecraft:nether_star"},{"ingredient":{"item":"minecraft:flint"},"catalyst":true}],
             "results":[{"id":"minecraft:diamond"}],"processing_time":100,"priority":10}
            """;
        var low=recipe(h,lowJson);var high=recipe(h,highJson);
        try {
            var recipes=new java.util.ArrayList<net.minecraft.world.item.crafting.RecipeHolder<?>>();
            recipes.add(new net.minecraft.world.item.crafting.RecipeHolder<>(lowId,low));recipes.add(new net.minecraft.world.item.crafting.RecipeHolder<>(highId,high));manager.replaceRecipes(recipes);
            var pos=new net.minecraft.core.BlockPos(1,1,1);h.setBlock(pos,MachineBlocks.MIXING_VESSEL.get());MixingVesselBlockEntity vessel=h.getBlockEntity(pos);
            var motorPos=pos.below();h.setBlock(motorPos,com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,net.minecraft.core.Direction.UP));
            com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor=h.getBlockEntity(motorPos);motor.generatedSpeed.setValue(256);motor.initialize();vessel.attachKinetics();
            vessel.items().setStackInSlot(0,new ItemStack(Items.NETHER_STAR,2));vessel.tick();
            h.assertTrue(vessel.saveWithoutMetadata(h.getLevel().registryAccess()).getString("Recipe").equals(lowId.toString()),"eligible fallback starts");
            vessel.items().setStackInSlot(1,new ItemStack(Items.FLINT));vessel.tick();
            h.assertTrue(vessel.progress()==2 && vessel.saveWithoutMetadata(h.getLevel().registryAccess()).getString("Recipe").equals(lowId.toString()),"newly eligible higher priority cannot preempt");
            var save=vessel.saveWithoutMetadata(h.getLevel().registryAccess());vessel.loadCustomOnly(save,h.getLevel().registryAccess());
            motor.generatedSpeed.setValue(0);vessel.tick();h.assertTrue(vessel.progress()==2,"zero RPM pauses saved operation");
            motor.generatedSpeed.setValue(256);for(int tick=0;tick<14;tick++)vessel.tick();
            h.assertTrue(count(vessel.items(),Items.EMERALD)==1 && count(vessel.items(),Items.NETHER_STAR)==1,"fallback finishes once after stop/load");
            vessel.tick();h.assertTrue(vessel.progress()==1 && vessel.saveWithoutMetadata(h.getLevel().registryAccess()).getString("Recipe").equals(highId.toString()),"next operation reevaluates priority");
            recipes.removeIf(holder->holder.id().equals(highId));recipes.add(new net.minecraft.world.item.crafting.RecipeHolder<>(highId,recipe(h,highJson.replace("\"priority\":10","\"priority\":20"))));manager.replaceRecipes(recipes);
            vessel.tick();h.assertTrue(vessel.progress()==2,"priority-only reload retains active operation");
            vessel.items().setStackInSlot(1,ItemStack.EMPTY);vessel.tick();h.assertTrue(vessel.progress()==2,"catalyst removal pauses without selecting fallback");
            vessel.items().setStackInSlot(1,new ItemStack(Items.FLINT));for(int tick=0;tick<14;tick++)vessel.tick();
            h.assertTrue(count(vessel.items(),Items.DIAMOND)==1 && count(vessel.items(),Items.FLINT)==1,"catalyst resumes and survives exactly one result");
        } finally {manager.replaceRecipes(original);}
        h.succeed();
    }
    private static MachineRecipe recipe(GameTestHelper h, String json) {
        return MachineRecipes.MIXING.serializer().codec().codec().parse(h.getLevel().registryAccess()
                .createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(json)).getOrThrow();
    }
    private static int count(ItemStackHandler items, net.minecraft.world.item.Item item) {
        int count = 0; for (int i=0;i<items.getSlots();i++) if (items.getStackInSlot(i).is(item)) count += items.getStackInSlot(i).getCount(); return count;
    }
    @GameTest(template="empty", templateNamespace="cwi")
    public static void bothClocksTransferExactAmountsAcrossSpeeds(GameTestHelper h) {
        for (boolean recipeClock : new boolean[]{false,true}) for (int rpm : new int[]{128,256}) {
            var recipe = recipe(h, """
                {"ingredients":[{"item":"minecraft:sand"},
                  {"ingredient":{"type":"neoforge:single","fluid":"minecraft:water"},"amount":5,"per_tick":2%s}],
                 "results":[{"id":"minecraft:clay_ball"}],"processing_time":100}
                """.formatted(recipeClock ? ",\"tick_mode\":\"recipe\"" : ""));
            var items = new ItemStackHandler(6); items.setStackInSlot(0,new ItemStack(Items.SAND));
            var fluids = new MultiTank(()->{},1000,1000,1000); fluids.setFluid(0,new FluidStack(Fluids.WATER,1000));
            var op = new VesselOperation(42); int duration = recipe.durationAtRpm(rpm);
            for (int tick=0;tick<duration;tick++) {
                op.prepare(duration);
                h.assertTrue(VesselTransaction.step(recipe,op,items,fluids,h.getLevel()),"due transaction succeeds");
                if (tick==0) h.assertTrue(count(items,Items.SAND)==0,"periodic recipes pay ordinary inputs on first successful tick");
                if (tick==4) op=VesselOperation.load(op.save());
            }
            int expected = recipeClock ? 250 : rpm==128 ? 75 : 40;
            h.assertTrue(fluids.getFluidInTank(0).getAmount()==1000-expected,"exact total for clock and RPM: "+recipeClock+"/"+rpm);
            h.assertTrue(op.completed()==1 && count(items,Items.CLAY_BALL)==1,"one completion, no remainder transfer");
        }
        h.succeed();
    }
    @GameTest(template="empty", templateNamespace="cwi")
    public static void catalystsReserveSeparateUnitsAndPauseWithoutPayment(GameTestHelper h) {
        var recipe = recipe(h,"""
            {"ingredients":[{"ingredient":{"item":"minecraft:flint"},"catalyst":true},{"item":"minecraft:flint"},
              {"ingredient":{"type":"neoforge:single","fluid":"minecraft:water"},"amount":20,"catalyst":true},
              {"ingredient":{"type":"neoforge:single","fluid":"minecraft:water"},"amount":5,"per_tick":1}],
             "results":[{"id":"minecraft:clay_ball"}]}
            """);
        var items=new ItemStackHandler(6); items.setStackInSlot(0,new ItemStack(Items.FLINT));
        var fluids=new MultiTank(()->{},1000); fluids.setFluid(0,new FluidStack(Fluids.WATER,25));
        var op=new VesselOperation(9);op.prepare(4);
        h.assertTrue(!VesselTransaction.step(recipe,op,items,fluids,h.getLevel()),"one flint cannot be both catalyst and consumed");
        h.assertTrue(op.ticks()==0 && fluids.getFluidInTank(0).getAmount()==25,"failed transfer pays nothing");
        items.setStackInSlot(0,new ItemStack(Items.FLINT,2));
        h.assertTrue(VesselTransaction.step(recipe,op,items,fluids,h.getLevel()),"separate units allow work");
        h.assertTrue(count(items,Items.FLINT)==1 && fluids.getFluidInTank(0).getAmount()==20,"both catalysts retained");
        op.prepare(4);items.setStackInSlot(0,ItemStack.EMPTY);
        h.assertTrue(!VesselTransaction.step(recipe,op,items,fluids,h.getLevel()),"missing catalyst pauses paid operation");
        items.setStackInSlot(0,new ItemStack(Items.FLINT));fluids.setFluid(0,new FluidStack(Fluids.WATER,25));
        h.assertTrue(VesselTransaction.step(recipe,op,items,fluids,h.getLevel()) && count(items,Items.FLINT)==1,"resume never pays ordinary input twice");
        h.succeed();
    }
    @GameTest(template="empty", templateNamespace="cwi")
    public static void damageCanBreakNearlyBrokenToolAndRequiresReplacement(GameTestHelper h) {
        var recipe=recipe(h,"""
            {"ingredients":[{"ingredient":{"item":"minecraft:iron_sword"},"damage":10,"per_tick":2}],
             "results":[{"id":"minecraft:clay_ball"}]}
            """);
        var items=new ItemStackHandler(6);var sword=new ItemStack(Items.IRON_SWORD);sword.setDamageValue(sword.getMaxDamage()-2);items.setStackInSlot(0,sword);
        var fluids=new MultiTank(()->{},1000);var op=new VesselOperation(13);
        for(int tick=0;tick<2;tick++){op.prepare(4);h.assertTrue(VesselTransaction.step(recipe,op,items,fluids,h.getLevel()),"nearly broken tool use succeeds");}
        h.assertTrue(items.getStackInSlot(0).isEmpty(),"2 remaining durability can pay10 and break");
        op.prepare(4);h.assertTrue(VesselTransaction.step(recipe,op,items,fluids,h.getLevel()),"between tool uses no replacement needed");
        op.prepare(4);h.assertTrue(!VesselTransaction.step(recipe,op,items,fluids,h.getLevel()) && count(items,Items.CLAY_BALL)==0,"next due use pauses atomically");
        items.setStackInSlot(0,new ItemStack(Items.IRON_SWORD));
        h.assertTrue(VesselTransaction.step(recipe,op,items,fluids,h.getLevel()) && items.getStackInSlot(0).getDamageValue()==10,"replacement takes exactly one due cost");
        h.assertTrue(count(items,Items.CLAY_BALL)==1,"only one completed result");h.succeed();
    }
    @GameTest(template="empty", templateNamespace="cwi")
    public static void breakingToolMayFreeTheOnlyOutputSlot(GameTestHelper h) {
        var recipe=recipe(h,"""
            {"ingredients":[{"ingredient":{"item":"minecraft:iron_sword"},"damage":10}],
             "results":[{"id":"minecraft:clay_ball"}]}
            """);
        var items=new ItemStackHandler(1);var sword=new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(sword.getMaxDamage()-2);items.setStackInSlot(0,sword);
        var fluids=new MultiTank(()->{},1000);
        h.assertTrue(VesselTransaction.eligible(recipe,items,fluids,h.getLevel()),"breaking the tool can free the only output slot during selection");
        h.assertTrue(items.getStackInSlot(0).getDamageValue()==sword.getMaxDamage()-2,"selection never damages the original tool");
        var op=new VesselOperation(17);op.prepare(1);
        h.assertTrue(VesselTransaction.step(recipe,op,items,fluids,h.getLevel()) && count(items,Items.CLAY_BALL)==1,
                "tool break and output insertion commit together");h.succeed();
    }
    @GameTest(template="empty", templateNamespace="cwi")
    public static void blockedToolAlternativesKeepEveryPendingWearDecision(GameTestHelper h) {
        var recipe=recipe(h,"""
            {"ingredients":[{"ingredient":{"item":"minecraft:iron_sword"},"damage":10}],
             "results":[{"id":"minecraft:clay_ball"}]}
            """);
        var items=new ItemStackHandler(2);items.setStackInSlot(0,new ItemStack(Items.IRON_SWORD));
        var second=new ItemStack(Items.IRON_SWORD);second.setDamageValue(1);items.setStackInSlot(1,second);
        var fluids=new MultiTank(()->{},1000);var op=new VesselOperation(19);op.prepare(1);
        h.assertTrue(!VesselTransaction.step(recipe,op,items,fluids,h.getLevel()),"both tool alternatives are blocked by full outputs");
        var decisions=op.save().getCompound("ToolRolls").getList("0:0",net.minecraft.nbt.Tag.TAG_COMPOUND);
        h.assertTrue(decisions.size()==2,"both candidate tool states retain their own native wear result");
        var saved=op.save();op=VesselOperation.load(saved);
        h.assertTrue(!VesselTransaction.step(recipe,op,items,fluids,h.getLevel()) && saved.equals(op.save()),
                "retry after reload reuses every pending wear decision");
        items.setStackInSlot(1,ItemStack.EMPTY);
        h.assertTrue(VesselTransaction.step(recipe,op,items,fluids,h.getLevel()) && items.getStackInSlot(0).getDamageValue()==10,
                "unblocking applies exactly the saved wear result");h.succeed();
    }
    @GameTest(template="empty", templateNamespace="cwi")
    public static void independentPoolsAndChanceStayFixedWhenBlockedAndSaved(GameTestHelper h) {
        var recipe=recipe(h,"""
            {"ingredients":[{"item":"minecraft:sand"}],"results":[{"id":"minecraft:clay_ball","count":4,"chance":0.25}],
             "output_pools":[{"entries":[{"stack":{"id":"minecraft:gravel","count":2},"weight":3},{"stack":{"id":"minecraft:cobblestone"}}],"show_outputs":true},
                             {"entries":[{"stack":{"id":"minecraft:redstone"}},{"stack":{"id":"minecraft:glowstone_dust"}}]}]}
            """);
        var fluids=new MultiTank(()->{},1000);
        for (int seed=0;seed<20;seed++) {
            var blocked=new ItemStackHandler(6);blocked.setStackInSlot(0,new ItemStack(Items.SAND,2));for(int s=1;s<6;s++)blocked.setStackInSlot(s,new ItemStack(Items.DIAMOND,64));
            var op=new VesselOperation(seed);op.prepare(1);
            h.assertTrue(!VesselTransaction.step(recipe,op,blocked,fluids,h.getLevel()),"blocked ordinary outputs consume nothing");
            h.assertTrue(count(blocked,Items.SAND)==2 && op.ticks()==0,"deferred inputs and progress retained");
            op=VesselOperation.load(op.save());for(int s=1;s<6;s++)blocked.setStackInSlot(s,ItemStack.EMPTY);
            h.assertTrue(VesselTransaction.step(recipe,op,blocked,fluids,h.getLevel()),"unblocked saved operation completes");
            var control=new ItemStackHandler(6);control.setStackInSlot(0,new ItemStack(Items.SAND,2));var fresh=new VesselOperation(seed);fresh.prepare(1);
            h.assertTrue(VesselTransaction.step(recipe,fresh,control,fluids,h.getLevel()),"control completes");
            for (int s=0;s<6;s++)h.assertTrue(ItemStack.matches(blocked.getStackInSlot(s),control.getStackInSlot(s)),"blocked and saved RNG never rerolls");
            h.assertTrue(count(blocked,Items.GRAVEL)+count(blocked,Items.COBBLESTONE)>=1 && count(blocked,Items.REDSTONE)+count(blocked,Items.GLOWSTONE_DUST)==1,"both independent whole-stack pools produced");
        }
        h.succeed();
    }
    @GameTest(template="empty", templateNamespace="cwi")
    public static void displayPriorityReloadPreservesGameplayFingerprint(GameTestHelper h) {
        String source="""
            {"ingredients":[{"item":"minecraft:sand"}],"priority":-1,
             "output_pools":[{"entries":[{"stack":{"id":"minecraft:gravel"}}]}]}
            """;
        var a=recipe(h,source);var b=recipe(h,source.replace("-1","10").replace("\"entries\":","\"show_outputs\":true,\"entries\":"));
        h.assertTrue(a.fingerprint(h.getLevel().registryAccess()).equals(b.fingerprint(h.getLevel().registryAccess())),"display and priority never cancel work");
        var c=recipe(h,source.replace("gravel","clay_ball"));h.assertTrue(!a.fingerprint(h.getLevel().registryAccess()).equals(c.fingerprint(h.getLevel().registryAccess())),"gameplay changes cancel");
        var ops=h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try {MachineRecipes.MIXING.serializer().streamCodec().encode(buffer,b);var synced=MachineRecipes.MIXING.serializer().streamCodec().decode(buffer);
            h.assertTrue(synced.priority()==10 && synced.outputPools().getFirst().showOutputs(),"selection and independent visibility sync to clients");}
        finally {buffer.release();}
        for (var entry:List.of(MachineRecipes.INCUBATING)) h.assertTrue(entry.serializer().codec().codec().parse(ops,JsonParser.parseString(source)).isError(),"other machines reject new options");
        for (String fields:List.of("\"per_tick\":0","\"per_tick\":-2","\"catalyst\":true,\"chance\":0.5","\"damage\":2,\"count\":2","\"damage\":3,\"catalyst\":true"))
            h.assertTrue(MachineRecipes.MIXING.serializer().codec().codec().parse(ops,JsonParser.parseString("{\"ingredients\":[{\"ingredient\":{\"item\":\"minecraft:sand\"},"+fields+"}]}")).isError(),"invalid entry rejected: "+fields);
        for (String json : List.of(
                "{\"ingredients\":[{\"item\":\"minecraft:sand\",\"catalyst\":true}]}",
                "{\"ingredients\":[{\"item\":\"minecraft:sand\"}],\"results\":[{\"id\":\"minecraft:clay_ball\",\"per_tick\":2}]}",
                "{\"ingredients\":[{\"item\":\"minecraft:sand\"}],\"output_pools\":[{\"entries\":[{\"stack\":{\"id\":\"minecraft:gravel\"},\"chance\":0.25}]}]}"))
            h.assertTrue(MachineRecipes.MIXING.serializer().codec().codec().parse(ops,JsonParser.parseString(json)).isError(),"native codecs never silently drop advanced behavior");
        h.succeed();
    }
}
