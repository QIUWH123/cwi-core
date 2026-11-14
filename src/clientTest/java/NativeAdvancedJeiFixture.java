import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.processing.recipe.*;
import java.util.*;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.crafting.*;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.neoforge.registries.GameData;

/** Calls transformed native category wrappers with actual advanced codecs and JEI slot callbacks. */
public final class NativeAdvancedJeiFixture {
    public static void main(String[] args) throws Exception {
        MinimalNativeBootstrap.initialize();SharedConstants.tryDetectVersion();Bootstrap.bootStrap();GameData.unfreezeData();
        var unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        ProcessingJeiFixture.unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        var bus=BusBuilder.builder().build();var field=net.neoforged.neoforge.common.NeoForgeMod.class.getDeclaredField("FLUID_INGREDIENT_TYPES");field.setAccessible(true);
        ((net.neoforged.neoforge.registries.DeferredRegister<?>)field.get(null)).register(bus);
        bus.post(MinimalNativeBootstrap.event(net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_INGREDIENT_TYPES.key(),net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_INGREDIENT_TYPES));
        String[][] cases={
            {"create:milling","com.simibubi.create.compat.jei.category.MillingCategory"},
            {"createaddition:rolling","com.mrh0.createaddition.compat.jei.RollingMillCategory"},
            {"vintageimprovements:vibrating","com.negodya1.vintageimprovements.compat.jei.category.VibratingCategory"},
            {"rubberworks:compressing","io.github.hadron13.rubberworks.compat.jei.category.CompressingCategory"},
            {"create:mixing","com.simibubi.create.compat.jei.category.MixingCategory"},
            {"createmetallurgy:melting","fr.lucreeper74.createmetallurgy.compat.jei.category.MeltingCategory"},
            {"createmetallurgy:alloying","fr.lucreeper74.createmetallurgy.compat.jei.category.AlloyingCategory"},
            {"tfmg:vat","com.drmangotea.tfmg.integration.jei.category.ChemicalVatCategory"},
            {"create:filling","com.simibubi.create.compat.jei.category.SpoutCategory"}
        };
        for(var spec:cases){
            var id=ResourceLocation.parse(spec[0]);RecipeType<?> type=new RecipeType<>(){};Registry.register(BuiltInRegistries.RECIPE_TYPE,id,type);
            boolean compressor=spec[0].startsWith("rubberworks");
            String input=compressor?"{\"ingredient\":{\"type\":\"neoforge:single\",\"fluid\":\"minecraft:water\"},\"amount\":5,\"per_tick\":2}":"{\"ingredient\":{\"item\":\"minecraft:sand\"},\"per_tick\":2}";
            String fluid=compressor?",{\"stack\":{\"id\":\"minecraft:lava\",\"amount\":5},\"per_tick\":2}":"";
            var params=ProcessingRecipeParams.CODEC.codec().parse(JsonOps.INSTANCE,JsonParser.parseString("{\"ingredients\":["+input+"],\"results\":[{\"stack\":{\"id\":\"minecraft:clay_ball\"},\"per_tick\":2}"+fluid+"],\"priority\":99,\"output_pools\":[{\"entries\":[{\"stack\":{\"id\":\"minecraft:flint\"},\"weight\":3},{\"stack\":{\"id\":\"minecraft:gravel\"}}],\"show_outputs\":true},{\"entries\":[{\"stack\":{\"id\":\"minecraft:diamond\"}}]}]}" )).getOrThrow();
            var recipe=(ProcessingRecipe<?,?>)ProcessingJeiFixture.allocate("com.simibubi.create.content.kinetics.millstone.MillingRecipe");
            ProcessingJeiFixture.put(recipe,ProcessingRecipe.class,"params",params);ProcessingJeiFixture.put(recipe,ProcessingRecipe.class,"requiredHeat",HeatCondition.NONE);ProcessingJeiFixture.put(recipe,ProcessingRecipe.class,"type",type);
            var category=(CreateRecipeCategory)ProcessingJeiFixture.category(spec[1]);
            var holder=new RecipeHolder<>(ResourceLocation.parse("cwi:advanced_jei"),recipe);
            WorldSpoutingJeiFixture.slots.clear();WorldSpoutingJeiFixture.grids.clear();
            category.setRecipe(WorldSpoutingJeiFixture.layout(),holder,null);
            category.createRecipeExtras(WorldSpoutingJeiFixture.extras(),holder,null);
            var slots=WorldSpoutingJeiFixture.slots;
            WorldSpoutingJeiFixture.check(slots.size()==(compressor?5:4),spec[0]+" displays typed outputs and both independent pools");
            WorldSpoutingJeiFixture.tip(0,"cwi.recipe.working_interval");WorldSpoutingJeiFixture.tip(1,"cwi.recipe.working_interval");
            var shown=slots.get(slots.size()-2);var hidden=slots.getLast();
            WorldSpoutingJeiFixture.check(shown.items.size()==2&&hidden.items.isEmpty()&&hidden.overlay!=null,"native visible/hidden pools");
            WorldSpoutingJeiFixture.check(shown.background==CreateRecipeCategory.getRenderedSlot(0f),"native chance pool frame");
            WorldSpoutingJeiFixture.check(WorldSpoutingJeiFixture.tip(slots.size()-2,"create.recipe.processing.chance").getArgs()[0].equals("75"),"native cycling pool chance");
            WorldSpoutingJeiFixture.check(slots.stream().flatMap(s->s.tips.stream()).noneMatch(t->t.getString().contains("priority")),"priority hidden");
            if(compressor){
                WorldSpoutingJeiFixture.check(slots.get(2).capacity==5,"compressor gains fluid-output JEI slot");
                WorldSpoutingJeiFixture.at(1,RecipeIngredientRole.OUTPUT,114,4);
            }
            if(spec[0].equals("vintageimprovements:vibrating"))WorldSpoutingJeiFixture.at(0,RecipeIngredientRole.INPUT,15,9);
            if(spec[0].equals("create:mixing"))WorldSpoutingJeiFixture.at(0,RecipeIngredientRole.INPUT,36,51);
        }
        for(String type:List.of("tfmg:distillation","createdieselgenerators:distillation")){
            var id=ResourceLocation.parse(type);Registry.register(BuiltInRegistries.RECIPE_TYPE,id,new RecipeType<>(){});
            var params=ProcessingRecipeParams.CODEC.codec().parse(JsonOps.INSTANCE,JsonParser.parseString("""
                {"ingredients":[{"ingredient":{"type":"neoforge:single","fluid":"minecraft:water"},"amount":5,"per_tick":2}],
                 "results":[{"stack":{"id":"minecraft:water","amount":2},"per_tick":2},{"stack":{"id":"minecraft:lava","amount":3},"per_tick":2}]}
                """)).getOrThrow();
            var recipe=(ProcessingRecipe<?,?>)ProcessingJeiFixture.allocate("com.simibubi.create.content.kinetics.millstone.MillingRecipe");
            ProcessingJeiFixture.put(recipe,ProcessingRecipe.class,"params",params);ProcessingJeiFixture.put(recipe,ProcessingRecipe.class,"requiredHeat",HeatCondition.NONE);ProcessingJeiFixture.put(recipe,ProcessingRecipe.class,"type",BuiltInRegistries.RECIPE_TYPE.get(id));
            var category=(CreateRecipeCategory)ProcessingJeiFixture.category(type.startsWith("tfmg")?"com.drmangotea.tfmg.integration.jei.category.DistillationCategory":"com.jesz.createdieselgenerators.compat.jei.DistillationCategory");
            WorldSpoutingJeiFixture.slots.clear();category.setRecipe(WorldSpoutingJeiFixture.layout(),new RecipeHolder<>(id,recipe),null);
            if(type.startsWith("tfmg")){
                WorldSpoutingJeiFixture.at(0,RecipeIngredientRole.INPUT,18,90);
                WorldSpoutingJeiFixture.at(1,RecipeIngredientRole.OUTPUT,105,83);
                WorldSpoutingJeiFixture.at(2,RecipeIngredientRole.OUTPUT,105,59);
            }else{
                WorldSpoutingJeiFixture.at(0,RecipeIngredientRole.INPUT,17,145);
                WorldSpoutingJeiFixture.at(1,RecipeIngredientRole.OUTPUT,130,127);
                WorldSpoutingJeiFixture.at(2,RecipeIngredientRole.OUTPUT,130,104);
            }
        }
        // Item/fluid input order must not move the native spout channel positions.
        layoutCase("create:filling","com.simibubi.create.compat.jei.category.SpoutCategory", """
            {"ingredients":[{"ingredient":{"type":"neoforge:single","fluid":"minecraft:water"},"amount":5},{"item":"minecraft:sand"}],
             "results":[{"id":"minecraft:clay_ball"}],"output_pools":[{"entries":[{"stack":{"id":"minecraft:diamond"}}]}]}
            """);
        WorldSpoutingJeiFixture.at(0,RecipeIngredientRole.INPUT,27,8);WorldSpoutingJeiFixture.at(1,RecipeIngredientRole.INPUT,27,51);
        // Vat rows are independent channels, including overflow pools and fluid outputs.
        var vat=JsonParser.parseString("""
            {"ingredients":[{"ingredient":{"type":"neoforge:single","fluid":"minecraft:water"},"amount":5},{"ingredient":{"item":"minecraft:sand"},"catalyst":true}],
             "results":[{"stack":{"id":"minecraft:water","amount":2},"per_tick":2},{"id":"minecraft:clay_ball"}]}
            """).getAsJsonObject();
        layoutCase("tfmg:vat","com.drmangotea.tfmg.integration.jei.category.ChemicalVatCategory",vat.toString());
        WorldSpoutingJeiFixture.at(0,RecipeIngredientRole.INPUT,47,85);WorldSpoutingJeiFixture.at(1,RecipeIngredientRole.INPUT,47,64);
        WorldSpoutingJeiFixture.at(2,RecipeIngredientRole.OUTPUT,150,106);WorldSpoutingJeiFixture.at(3,RecipeIngredientRole.OUTPUT,128,106);
        for(int i=1;i<8;i++)vat.getAsJsonArray("ingredients").add(JsonParser.parseString("{\"ingredient\":{\"item\":\"minecraft:flint\"},\"catalyst\":true}"));
        var pools=new com.google.gson.JsonArray();for(int i=0;i<9;i++)pools.add(JsonParser.parseString("{\"entries\":[{\"stack\":{\"id\":\"minecraft:diamond\"}}]}"));vat.add("output_pools",pools);
        layoutCase("tfmg:vat","com.drmangotea.tfmg.integration.jei.category.ChemicalVatCategory",vat.toString());
        WorldSpoutingJeiFixture.at(0,RecipeIngredientRole.INPUT,19,85);WorldSpoutingJeiFixture.at(1,RecipeIngredientRole.INPUT,19,43);
        WorldSpoutingJeiFixture.at(9,RecipeIngredientRole.OUTPUT,153,43);
        WorldSpoutingJeiFixture.check(WorldSpoutingJeiFixture.grids.equals(List.of("2x4:110,43:10")),"vat product overflow excludes its separate fluid output");
        System.out.println("Native advanced JEI contracts PASS: "+WorldSpoutingJeiFixture.assertions);
    }
    static void layoutCase(String type,String categoryClass,String json)throws Exception{
        var params=ProcessingRecipeParams.CODEC.codec().parse(JsonOps.INSTANCE,JsonParser.parseString(json)).getOrThrow();
        var recipe=(ProcessingRecipe<?,?>)ProcessingJeiFixture.allocate("com.simibubi.create.content.kinetics.millstone.MillingRecipe");
        ProcessingJeiFixture.put(recipe,ProcessingRecipe.class,"params",params);ProcessingJeiFixture.put(recipe,ProcessingRecipe.class,"requiredHeat",HeatCondition.NONE);
        ProcessingJeiFixture.put(recipe,ProcessingRecipe.class,"type",BuiltInRegistries.RECIPE_TYPE.get(ResourceLocation.parse(type)));
        var category=(CreateRecipeCategory)ProcessingJeiFixture.category(categoryClass);var holder=new RecipeHolder<>(ResourceLocation.parse("cwi:geometry"),recipe);
        WorldSpoutingJeiFixture.slots.clear();WorldSpoutingJeiFixture.grids.clear();category.setRecipe(WorldSpoutingJeiFixture.layout(),holder,null);category.createRecipeExtras(WorldSpoutingJeiFixture.extras(),holder,null);
    }

}
