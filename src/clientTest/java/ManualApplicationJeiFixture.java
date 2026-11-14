import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.compat.jei.category.*;
import com.simibubi.create.content.kinetics.deployer.*;
import java.util.*;
import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.registries.GameData;
import net.quiwh.cwi.interaction.application.*;
import net.quiwh.cwi.compat.jei.ApplicationJei;

/** Native transformed category and serializer; no mocked application behavior. */
public final class ManualApplicationJeiFixture {
    static int checks;
    static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    static ItemApplicationRecipe.Serializer<ManualApplicationRecipe> SERIALIZER;
    static List<Component> emptyTips=new ArrayList<>();
    static int emptyLabels;
    static ManualApplicationRecipe recipe(String options,String results){
        return SERIALIZER.codec().codec().parse(JsonOps.INSTANCE,JsonParser.parseString("""
            {"ingredients":[{"item":"minecraft:iron_block"},{"item":"minecraft:brush"}],"results":%s%s}
            """.formatted(results,options.isEmpty()?"":",\"cwi_interaction\":"+options))).getOrThrow();
    }
    static ItemApplicationCategory category(ManualApplicationRecipe r) {
        var d=WorldSpoutingJeiFixture.proxy(IDrawable.class,(p,m,a)->switch(m.getName()){case "getWidth"->177;case "getHeight"->60;default->WorldSpoutingJeiFixture.def(m.getReturnType());});
        RecipeHolder<ItemApplicationRecipe> holder=new RecipeHolder<>(ResourceLocation.parse("cwi:fixture/manual"),r);
        var info=new CreateRecipeCategory.Info<>(mezz.jei.api.recipe.RecipeType.<ItemApplicationRecipe>createRecipeHolderType(ResourceLocation.parse("create:item_application")),
                Component.literal("fixture"),d,d,()->List.of(holder),List.of());
        var cat=new ItemApplicationCategory(info);
        WorldSpoutingJeiFixture.slots.clear();WorldSpoutingJeiFixture.grids.clear();
        cat.setRecipe(WorldSpoutingJeiFixture.layout(),holder,null);
        emptyTips.clear();emptyLabels=0;
        var delegate=WorldSpoutingJeiFixture.extras();
        cat.createRecipeExtras(WorldSpoutingJeiFixture.proxy(mezz.jei.api.gui.widgets.IRecipeExtrasBuilder.class,(p,m,a)->{
            if(m.getName().equals("addText")) {
                emptyLabels++;
                return WorldSpoutingJeiFixture.proxy(mezz.jei.api.gui.widgets.ITextWidget.class,(q,n,b)->n.getReturnType().isInstance(q)?q:WorldSpoutingJeiFixture.def(n.getReturnType()));
            }
            if(m.getName().equals("addTooltipArea")) return WorldSpoutingJeiFixture.proxy(mezz.jei.api.gui.widgets.IDrawableWidget.class,(q,n,b)->{
                if(n.getName().equals("setTooltip")&&b[0] instanceof Collection<?> values) values.forEach(v->emptyTips.add((Component)v));
                return n.getReturnType().isInstance(q)?q:WorldSpoutingJeiFixture.def(n.getReturnType());
            });
            return m.invoke(delegate,a);
        }),holder,null);
        return cat;
    }
    static boolean tip(int slot,String key){return WorldSpoutingJeiFixture.slots.get(slot).tips.stream().map(Component::getContents).anyMatch(c->c instanceof TranslatableContents t&&t.getKey().equals(key));}
    public static void main(String[] args)throws Exception{
        MinimalNativeBootstrap.initialize();SharedConstants.tryDetectVersion();Bootstrap.bootStrap();GameData.unfreezeData();
        for(var registry:BuiltInRegistries.REGISTRY) if(registry instanceof net.neoforged.neoforge.registries.BaseMappedRegistry<?>){
            var f=net.neoforged.neoforge.registries.BaseMappedRegistry.class.getDeclaredField("sync");f.setAccessible(true);f.set(registry,true);
        }
        var bus=net.neoforged.bus.api.BusBuilder.builder().build();
        com.simibubi.create.AllRecipeTypes.register(bus);
        bus.post(MinimalNativeBootstrap.event(net.minecraft.core.registries.Registries.RECIPE_TYPE,BuiltInRegistries.RECIPE_TYPE));
        bus.post(MinimalNativeBootstrap.event(net.minecraft.core.registries.Registries.RECIPE_SERIALIZER,BuiltInRegistries.RECIPE_SERIALIZER));
        SERIALIZER=new ItemApplicationRecipe.Serializer<>(ManualApplicationRecipe::new);
        var r=recipe("""
            {"block_mode":"keep","empty_hand":true,"sneaking":true,"cooldown":15,
             "drops":[{"stack":{"id":"minecraft:flint","count":4},"chance":0.25}],
             "actions":[{"type":"if","chance":0.23,"then":[
                {"type":"set_block","block":"minecraft:gold_block"},
                {"type":"drop","stack":{"id":"minecraft:diamond","count":2}}]}]}
            ""","[]");
        category(r);
        var slots=WorldSpoutingJeiFixture.slots;
        check(slots.size()==5,"block and hand plus all three outputs");
        check(slots.get(0).x==27&&slots.get(0).y==38&&slots.get(1).x==51&&slots.get(1).y==5,"native Create ingredient layout");
        check(slots.get(1).items.isEmpty()&&tip(1,"cwi.jei.application.empty_hand")&&tip(1,"cwi.jei.application.sneaking"),"empty hand and sneak requirements");
        check(emptyLabels==0&&emptyTips.stream().map(Component::getContents).anyMatch(c->c instanceof TranslatableContents t&&t.getKey().equals("cwi.jei.application.empty_hand"))
                &&emptyTips.stream().map(Component::getContents).anyMatch(c->c instanceof TranslatableContents t&&t.getKey().equals("cwi.jei.application.cooldown")),"empty hand keeps its hover information without a visible label");
        check(tip(0,"cwi.jei.application.keep")&&tip(1,"cwi.jei.application.cooldown"),"retention and cooldown explanation");
        check(slots.get(2).items.getFirst().getCount()==4&&tip(2,"create.recipe.processing.chance")&&tip(2,"cwi.jei.application.whole_stack"),"whole-stack chance presentation");
        check(slots.get(3).items.getFirst().is(Items.GOLD_BLOCK)&&tip(3,"cwi.jei.application.block_result"),"conditional transformed block is discoverable");
        check(ApplicationJei.outputs(r).get(1).chance()==0.23f&&ApplicationJei.outputs(r).get(2).chance()==0.23f,"grouped block/drop share the branch probability");
        var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try{SERIALIZER.streamCodec().encode(buffer,r);var copy=SERIALIZER.streamCodec().decode(buffer);
            check(((ApplicationExtension)copy).cwi$interaction().emptyHand&&buffer.readableBytes()==0,"client packet has empty-hand metadata");
            SERIALIZER.codec().codec().parse(JsonOps.INSTANCE,SERIALIZER.codec().codec().encodeStart(JsonOps.INSTANCE,copy).getOrThrow()).getOrThrow();checks++;
        }finally{buffer.release();}
        var many=new StringBuilder("{\"block_mode\":\"keep\",\"drops\":[");
        for(int i=0;i<10;i++){if(i>0)many.append(',');many.append("{\"stack\":{\"id\":\"minecraft:flint\"}}");}many.append("]}");
        category(recipe(many.toString(),"[]"));
        check(WorldSpoutingJeiFixture.slots.size()==12&&WorldSpoutingJeiFixture.grids.equals(List.of("2x3:132,1:10")),"all outputs use an accessible scrolling grid");
        category(recipe("","[{\"id\":\"minecraft:gold_block\"}]"));
        check(WorldSpoutingJeiFixture.slots.size()==3,"ordinary Create layout still executes");
        System.out.println("Manual item application JEI checks passed: "+checks);
    }
}
