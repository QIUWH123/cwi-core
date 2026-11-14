package net.quiwh.cwi;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllRecipeTypes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.quiwh.cwi.NativeAdvancedTestSupport.*;
import net.quiwh.cwi.compat.processing.NativeWork;

@GameTestHolder("cwi")
@PrefixGameTestTemplate(false)
public final class NativeAdvancedGameTests {
    @GameTest(template="empty", templateNamespace="cwi")
    public static void nativePriorityAndPoolsSurviveCodec(GameTestHelper h) {
        var serializer=AllRecipeTypes.MILLING.<net.minecraft.world.item.crafting.RecipeSerializer<com.simibubi.create.content.kinetics.millstone.MillingRecipe>>getSerializer();
        var ops=h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var json=JsonParser.parseString("""
                {"ingredients":[{"item":"minecraft:sand"}],"results":[{"id":"minecraft:clay_ball"}],
                 "processing_time":80,"priority":17,"output_pools":[
                  {"entries":[{"stack":{"id":"minecraft:flint"},"weight":3}],"show_outputs":true},
                  {"entries":[{"stack":{"id":"minecraft:gravel"}}]}]}
                """);
        var recipe=serializer.codec().codec().parse(ops,json).getOrThrow();
        var encoded=serializer.codec().codec().encodeStart(ops,recipe).getOrThrow().getAsJsonObject();
        h.assertTrue(encoded.has("priority")&&encoded.get("priority").getAsInt()==17,"native selection priority must survive JSON");
        h.assertTrue(encoded.getAsJsonArray("output_pools").size()==2,"both independent output pools survive JSON");
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            serializer.streamCodec().encode(buffer,recipe);
            var synced=serializer.streamCodec().decode(buffer);
            var copy=serializer.codec().codec().encodeStart(ops,synced).getOrThrow().getAsJsonObject();
            h.assertTrue(copy.equals(encoded)&&buffer.readableBytes()==0,"native packet preserves independent visibility and all fields");
        } finally {buffer.release();}
        h.succeed();
    }
    @GameTest(template="empty",templateNamespace="cwi")
    public static void millstonePeriodicItemsPauseAndResumeAfterSave(GameTestHelper h){
        var manager=h.getLevel().getRecipeManager();var old=manager.getRecipes();
        var serializer=AllRecipeTypes.MILLING.<net.minecraft.world.item.crafting.RecipeSerializer<com.simibubi.create.content.kinetics.millstone.MillingRecipe>>getSerializer();
        var recipe=serializer.codec().codec().parse(h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString("""
            {"ingredients":[{"ingredient":{"item":"minecraft:sand"},"per_tick":2,"tick_mode":"recipe"}],
             "results":[{"stack":{"id":"minecraft:clay_ball"},"per_tick":2,"tick_mode":"recipe"},
                        {"id":"minecraft:diamond"}],"processing_time":8}
            """)).getOrThrow();
        try{
            manager.replaceRecipes(java.util.List.of(new net.minecraft.world.item.crafting.RecipeHolder<>(net.minecraft.resources.ResourceLocation.parse("cwi:test_periodic_milling"),recipe)));
            var pos=new net.minecraft.core.BlockPos(1,1,1);h.setBlock(pos,com.simibubi.create.AllBlocks.MILLSTONE.getDefaultState());
            com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity mill=h.getBlockEntity(pos);
            h.setBlock(pos.below(),com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,net.minecraft.core.Direction.UP));
            com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor=h.getBlockEntity(pos.below());
            motor.generatedSpeed.setValue(32);motor.initialize();mill.attachKinetics();
            mill.inputInv.setStackInSlot(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SAND,4));
            mill.tick();
            h.assertTrue(mill.timer>0&&mill.getUpdateTag(h.getLevel().registryAccess()).getInt("Timer")==mill.timer,"advanced milling synchronizes a live native particle timer");
            for(int n=0;n<2;n++)mill.tick();
            var save=mill.saveWithoutMetadata(h.getLevel().registryAccess());mill.loadCustomOnly(save,h.getLevel().registryAccess());
            mill.attachKinetics();
            for(int n=0;n<10;n++)mill.tick();
            int clay=0,diamond=0;for(int n=0;n<mill.outputInv.getSlots();n++){
                var stack=mill.outputInv.getStackInSlot(n);if(stack.is(net.minecraft.world.item.Items.CLAY_BALL))clay+=stack.getCount();
                if(stack.is(net.minecraft.world.item.Items.DIAMOND))diamond+=stack.getCount();}
            h.assertTrue(clay==4&&diamond==1&&mill.inputInv.getStackInSlot(0).isEmpty(),"millstone transfers periodic inputs/results exactly once through save: clay="+clay+" diamond="+diamond+" sand="+mill.inputInv.getStackInSlot(0)+" speed="+mill.getSpeed()+" work="+mill.saveWithoutMetadata(h.getLevel().registryAccess()).getCompound("CwiProcessing"));
        }finally{manager.replaceRecipes(old);}h.succeed();
    }
    @GameTest(template="empty",templateNamespace="cwi")
    public static void workingClockPriorityBlockedRngAndDisplayReload(GameTestHelper h){
        var manager=h.getLevel().getRecipeManager();var previous=manager.getRecipes();
        String json="""
            {"ingredients":[{"ingredient":{"item":"minecraft:sand"},"per_tick":2}],
             "results":[{"stack":{"id":"minecraft:clay_ball"},"per_tick":2}],"processing_time":8,"priority":9,
             "output_pools":[{"entries":[{"stack":{"id":"minecraft:diamond"},"weight":3},{"stack":{"id":"minecraft:emerald"}}]}]}
            """;
        var high=recipe(h,"create:milling","working",json);var low=recipe(h,"create:milling","working_low",json.replace("clay_ball","flint").replace("\"priority\":9","\"priority\":0"));
        try{
            manager.replaceRecipes(java.util.List.of(low,high));
            com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity mill=place(h,new net.minecraft.core.BlockPos(1,1,1),"create:millstone");drive(h,mill,32);
            mill.inputInv.setStackInSlot(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SAND,2));mill.tick();
            h.assertTrue(NativeWork.of(mill).save().getString("Recipe").equals(high.id().toString()),"higher priority wins independently of recipe-manager order");
            for(int i=0;i<mill.outputInv.getSlots();i++)mill.outputInv.setStackInSlot(i,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE,64));
            mill.tick();var pending=NativeWork.of(mill).save();for(int i=0;i<5;i++)mill.tick();
            h.assertTrue(pending.equals(NativeWork.of(mill).save())&&mill.inputInv.getStackInSlot(0).getCount()==2,"blocked working boundary pauses time and seeded decisions without paying input");
            manager.replaceRecipes(java.util.List.of(low,recipe(h,"create:milling","working",json.replace("\"priority\":9","\"priority\":-10").replace("\"output_pools\":[{","\"output_pools\":[{\"show_outputs\":true,"))));
            mill=reload(h,mill);for(int i=0;i<mill.outputInv.getSlots();i++)mill.outputInv.setStackInSlot(i,net.minecraft.world.item.ItemStack.EMPTY);
            for(int i=0;i<6;i++)mill.tick();
            h.assertTrue(total(mill.outputInv,net.minecraft.world.item.Items.CLAY_BALL)==2&&total(mill.outputInv,net.minecraft.world.item.Items.FLINT)==0,"working intervals follow successful real ticks; priority/display-only reload retains active recipe");
            h.assertTrue(total(mill.outputInv,net.minecraft.world.item.Items.DIAMOND)+total(mill.outputInv,net.minecraft.world.item.Items.EMERALD)==1,"blocked/reloaded operation rolls exactly one final pool product");
        }finally{manager.replaceRecipes(previous);}h.succeed();
    }

    /** Real script lifecycle and native packet checks for every staged adapter example. */
    @SuppressWarnings({"rawtypes","unchecked"})
    public static java.util.Map<String,String> verifyScripts(GameTestHelper h){
        var expected=new java.util.LinkedHashMap<String,String>();
        for(String type:java.util.List.of("milling","crushing","cutting","mixing","compacting","deploying","pressing","filling","emptying"))expected.put(type,"create:"+type);
        if(net.neoforged.fml.ModList.get().isLoaded("createaddition"))expected.put("rolling","createaddition:rolling");
        if(net.neoforged.fml.ModList.get().isLoaded("vintageimprovements")){expected.put("vibrating","vintageimprovements:vibrating");expected.put("centrifugation","vintageimprovements:centrifugation");}
        if(net.neoforged.fml.ModList.get().isLoaded("createmetallurgy")){expected.put("melting","createmetallurgy:melting");expected.put("alloying","createmetallurgy:alloying");}
        if(net.neoforged.fml.ModList.get().isLoaded("tfmg")){expected.put("vat","tfmg:vat");expected.put("tfmg_distillation","tfmg:distillation");}
        if(net.neoforged.fml.ModList.get().isLoaded("createdieselgenerators"))expected.put("diesel_distillation","createdieselgenerators:distillation");
        if(net.neoforged.fml.ModList.get().isLoaded("rubberworks"))expected.put("compressing","rubberworks:compressing");
        var fingerprints=new java.util.LinkedHashMap<String,String>();
        var ops=h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for(var entry:expected.entrySet()){
            var id=net.minecraft.resources.ResourceLocation.parse("cwi:example/native_advanced/"+entry.getKey());
            var value=h.getLevel().getRecipeManager().byKey(id).orElseThrow(()->new AssertionError("Missing advanced script example "+id)).value();
            h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(value.getType()).toString().equals(entry.getValue()),"advanced builder retains native type "+id);
            var recipe=(com.simibubi.create.content.processing.recipe.ProcessingRecipe<?,?>)value;
            var data=net.quiwh.cwi.compat.processing.AdvancedProcessing.get(recipe);
            h.assertTrue(data.priority()==10&&data.execution(),"advanced metadata survives actual KubeJS load "+id);
            if(!entry.getKey().endsWith("distillation"))h.assertTrue(data.pools().size()==2&&data.pools().getFirst().showOutputs()&&!data.pools().getLast().showOutputs(),"independent pool visibility survives builder "+id);
            if(!net.quiwh.cwi.compat.processing.NativeRecipeTypes.ACTIONS.contains(entry.getValue()))h.assertTrue(data.ingredients().stream().anyMatch(v->v.map(f->f.settings().periodic(),i->i.settings().periodic())),"periodic inputs survive builder "+id);
            var serializer=(net.minecraft.world.item.crafting.RecipeSerializer)recipe.getSerializer();
            var encoded=(com.google.gson.JsonElement)serializer.codec().codec().encodeStart(ops,recipe).getOrThrow();
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
            try{
                serializer.streamCodec().encode(buffer,recipe);var synced=serializer.streamCodec().decode(buffer);
                var copy=(com.google.gson.JsonElement)serializer.codec().codec().encodeStart(ops,synced).getOrThrow();
                // TFMG Pressure uses object identity for its omitted-zero codec default.
                // The packet reconstructs an equivalent zero object and encodes it explicitly.
                if(entry.getKey().equals("vat")&&!encoded.getAsJsonObject().has("pressure")&&copy.getAsJsonObject().has("pressure")&&copy.getAsJsonObject().get("pressure").getAsInt()==0)copy.getAsJsonObject().remove("pressure");
                h.assertTrue(encoded.equals(copy)&&buffer.readableBytes()==0,"scripted native fields survive packets "+id);
            }finally{buffer.release();}
            fingerprints.put(entry.getKey(),encoded.toString());
        }
        return fingerprints;
    }

}
