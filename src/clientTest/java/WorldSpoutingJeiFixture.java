import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import java.lang.reflect.*;
import java.util.*;
import mezz.jei.api.gui.builder.*;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.*;
import mezz.jei.api.gui.widgets.*;
import mezz.jei.api.recipe.*;
import mezz.jei.api.registration.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.*;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.neoforge.registries.GameData;
import net.quiwh.cwi.interaction.*;

/** Calls the real native recipe codecs and JEI callbacks, without a client or GPU. */
public final class WorldSpoutingJeiFixture {
    static final class Slot {
        RecipeIngredientRole role; int x=-1,y=-1; String name=""; long capacity;
        List<ItemStack> items=new ArrayList<>(); List<Fluid> fluids=new ArrayList<>(); List<Long> amounts=new ArrayList<>();
        List<Component> tips=new ArrayList<>(); Object background,overlay;
        List<IRecipeSlotRichTooltipCallback> callbacks=new ArrayList<>();
    }
    static List<Slot> slots=new ArrayList<>(); static List<String> grids=new ArrayList<>();
    static int assertions;
    static void check(boolean b,String why){assertions++;if(!b)throw new AssertionError(why);}
    static Object def(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;return null;}
    @SuppressWarnings("unchecked") static <T>T proxy(Class<T> t,InvocationHandler h){return (T)Proxy.newProxyInstance(t.getClassLoader(),new Class<?>[]{t},(p,m,a)->{
        if(m.getDeclaringClass()==Object.class)return switch(m.getName()){case "hashCode"->System.identityHashCode(p);case "equals"->p==a[0];default->"fixture";};return h.invoke(p,m,a);});}
    static IRecipeSlotBuilder acceptor(Slot s){return proxy(IRecipeSlotBuilder.class,(p,m,a)->{
        switch(m.getName()){
            case "setSlotName"->s.name=(String)a[0];
            case "setBackground"->s.background=a[0];
            case "setOverlay"->s.overlay=a[0];
            case "setFluidRenderer"->s.capacity=((Number)a[0]).longValue();
            case "addItemStack"->s.items.add(((ItemStack)a[0]).copy());
            case "addItemStacks"->((List<ItemStack>)a[0]).forEach(v->s.items.add(v.copy()));
            case "addIngredients"->{if(a.length==2&&a[1] instanceof List<?> values)for(var value:values)if(value instanceof net.neoforged.neoforge.fluids.FluidStack fluid){s.fluids.add(fluid.getFluid());s.amounts.add((long)fluid.getAmount());}}
            case "addFluidStack"->{s.fluids.add((Fluid)a[0]);s.amounts.add(((Number)a[1]).longValue());}
            case "addRichTooltipCallback"->{s.callbacks.add((IRecipeSlotRichTooltipCallback)a[0]);
                s.tips.addAll(tooltip(s,0,List.of((IRecipeSlotRichTooltipCallback)a[0]))); }
        }return m.getReturnType().isInstance(p)?p:def(m.getReturnType());});}
    static List<Component> tooltip(Slot s,int index,List<IRecipeSlotRichTooltipCallback> callbacks) {
        var tips=new ArrayList<Component>();
        var view=proxy(mezz.jei.api.gui.ingredient.IRecipeSlotView.class,(p,m,a)->switch(m.getName()) {
            case "getDisplayedItemStack"->s.items.isEmpty()?Optional.empty():Optional.of(s.items.get(index));
            default->def(m.getReturnType());});
        for(var callback:callbacks)callback.onRichTooltip(view,proxy(ITooltipBuilder.class,(p,m,a)->{
            if(m.getName().equals("add") && a[0] instanceof Component c)tips.add(c);return def(m.getReturnType());}));
        return tips;
    }
    static IRecipeLayoutBuilder layout(){return proxy(IRecipeLayoutBuilder.class,(p,m,a)->{
        if(m.getName().equals("addSlot")){var s=new Slot();s.role=(RecipeIngredientRole)a[0];if(a.length==3){s.x=(int)a[1];s.y=(int)a[2];}slots.add(s);return acceptor(s);}return def(m.getReturnType());});}
    static IRecipeExtrasBuilder extras(){return proxy(IRecipeExtrasBuilder.class,(p,m,a)->{
        if(m.getName().equals("getRecipeSlots"))return proxy(IRecipeSlotDrawablesView.class,(q,n,b)->slots.stream().filter(s->s.role==b[0]).map(s->proxy(IRecipeSlotDrawable.class,(u,v,w)->switch(v.getName()){
            case "getRole"->s.role;case "getSlotName"->Optional.of(s.name);default->def(v.getReturnType());})).toList());
        if(m.getName().equals("addScrollGridWidget")){int size=((List<?>)a[0]).size();int cols=(int)a[1],rows=(int)a[2];return proxy(IScrollGridWidget.class,(q,n,b)->{
            if(n.getName().equals("setPosition"))grids.add(cols+"x"+rows+":"+b[0]+","+b[1]+":"+size);return n.getReturnType().isInstance(q)?q:def(n.getReturnType());});}return def(m.getReturnType());});}
    static InteractionRecipe recipe(InteractionRecipes.Kind k,String json){return k.serializer().codec().codec().parse(JsonOps.INSTANCE,JsonParser.parseString(json)).getOrThrow();}
    static CreateRecipeCategory<InteractionRecipe> category(InteractionRecipe r)throws Exception{
        Class<?> c=Class.forName("net.quiwh.cwi.compat.jei.WorldSpoutingJeiCategory");
        var d=proxy(IDrawable.class,(p,m,a)->switch(m.getName()){case "getWidth"->178;case "getHeight"->156;default->def(m.getReturnType());});
        var holder=new RecipeHolder<>(r.kind().id().withPrefix("fixture/"),r);
        var info=new CreateRecipeCategory.Info<>(mezz.jei.api.recipe.RecipeType.<InteractionRecipe>createRecipeHolderType(r.kind().id()),Component.literal("fixture"),d,d,()->List.of(holder),List.of(()->new ItemStack(Items.STICK)));
        var cat=(CreateRecipeCategory<InteractionRecipe>)c.getConstructor(CreateRecipeCategory.Info.class).newInstance(info);
        slots.clear();grids.clear();cat.setRecipe(layout(),holder,null);cat.createRecipeExtras(extras(),holder,null);
        int[] registered={0},catalysts={0};cat.registerRecipes(proxy(IRecipeRegistration.class,(p,m,a)->{if(m.getName().equals("addRecipes")){check(((List<?>)a[1]).equals(List.of(holder)),"original native holders registered");registered[0]++;}return def(m.getReturnType());}));
        cat.registerCatalysts(proxy(IRecipeCatalystRegistration.class,(p,m,a)->{if(m.getName().equals("addRecipeCatalyst"))catalysts[0]++;return def(m.getReturnType());}));
        check(registered[0]==1&&catalysts[0]==1,"recipe and spout catalyst registration");return cat;
    }
    static void at(int index,RecipeIngredientRole role,int x,int y){var s=slots.get(index);check(s.role==role&&s.x==x&&s.y==y,"authored geometry "+index);check(s.background!=null,"Create slot background");}
    static TranslatableContents tip(int slot,String key){return slots.get(slot).tips.stream().map(Component::getContents).filter(c->c instanceof TranslatableContents t&&t.getKey().equals(key)).map(c->(TranslatableContents)c).findFirst().orElseThrow(()->new AssertionError("Missing tooltip "+key));}
    public static void main(String[] args)throws Exception{
        MinimalNativeBootstrap.initialize();SharedConstants.tryDetectVersion();Bootstrap.bootStrap();GameData.unfreezeData();var bus=BusBuilder.builder().build();InteractionRecipes.register(bus);
        bus.post(MinimalNativeBootstrap.event(Registries.RECIPE_TYPE,BuiltInRegistries.RECIPE_TYPE));bus.post(MinimalNativeBootstrap.event(Registries.RECIPE_SERIALIZER,BuiltInRegistries.RECIPE_SERIALIZER));
        var growth=recipe(InteractionRecipes.Kind.BUDDING_CATALYSIS,"""
            {"input":"minecraft:budding_amethyst","fluid":{"fluid":"minecraft:water","amount":250},"stages":["minecraft:small_amethyst_bud","minecraft:medium_amethyst_bud","minecraft:large_amethyst_bud","minecraft:amethyst_cluster"],"chance":0.25,"attempts":3}
            """);category(growth);
        at(0,RecipeIngredientRole.INPUT,18,100);at(1,RecipeIngredientRole.INPUT,18,130);
        check(slots.get(0).capacity==250&&slots.get(0).amounts.equals(List.of(250L)),"exact fluid amount/capacity");
        check(slots.get(1).items.getFirst().is(Items.BUDDING_AMETHYST),"native block input");
        Item[] stages={Items.SMALL_AMETHYST_BUD,Items.MEDIUM_AMETHYST_BUD,Items.LARGE_AMETHYST_BUD,Items.AMETHYST_CLUSTER};
        for(int i=0;i<4;i++){at(i+2,RecipeIngredientRole.OUTPUT,142,130-22*i);check(slots.get(i+2).items.getFirst().is(stages[i])&&slots.get(i+2).items.getFirst().getCount()==3,"ordered growth stages/attempt count");tip(i+2,"cwi.jei.spouting.growth_stage");tip(i+2,"cwi.jei.spouting.growth_attempts");}
        check(grids.isEmpty(),"ordinary recipes retain authored layout");
        var filling=recipe(InteractionRecipes.Kind.BLOCK_SPOUTING,"""
            {"input":"minecraft:dirt","fluid":{"fluid":"minecraft:water","amount":1000},"outputs":["minecraft:mud","minecraft:mud","minecraft:clay"]}
            """);category(filling);check(slots.size()==4,"duplicate output entries are weights, not extra products");
        at(2,RecipeIngredientRole.OUTPUT,142,130);at(3,RecipeIngredientRole.OUTPUT,142,108);check(slots.get(2).items.getFirst().getCount()==1,"filling outputs one block");
        check(tip(2,"cwi.jei.spouting.alternative").getArgs()[0].equals("67"),"weighted alternative whole-percent probability");
        check(tip(3,"cwi.jei.spouting.alternative").getArgs()[0].equals("33"),"other random result whole-percent probability");
        var tag=TagKey.create(Registries.BLOCK,ResourceLocation.parse("cwi:fixture/input"));
        BuiltInRegistries.BLOCK.bindTags(Map.of(tag,List.of(Blocks.DIRT.builtInRegistryHolder(),Blocks.STONE.builtInRegistryHolder())));
        var fluidTag=TagKey.create(Registries.FLUID,ResourceLocation.parse("cwi:fixture/fluid"));
        BuiltInRegistries.FLUID.bindTags(Map.of(fluidTag,List.of(Fluids.WATER.builtInRegistryHolder(),Fluids.LAVA.builtInRegistryHolder())));
        var tagged=recipe(InteractionRecipes.Kind.BLOCK_SPOUTING,"""
            {"input":"#cwi:fixture/input","fluid":{"tag":"cwi:fixture/fluid","amount":50},"outputs":["minecraft:mud"]}
            """);category(tagged);check(slots.get(1).items.size()==2,"every tagged block searchable");check(slots.get(0).fluids.equals(List.of(Fluids.WATER,Fluids.LAVA)),"all tagged fluids searchable");
        var blocks=BuiltInRegistries.BLOCK.stream().filter(b->b.asItem()!=Items.AIR).limit(64).toList();
        String outputs=blocks.stream().map(b->"\""+BuiltInRegistries.BLOCK.getKey(b)+"\"").collect(java.util.stream.Collectors.joining(","));
        category(recipe(InteractionRecipes.Kind.BLOCK_SPOUTING,"{\"input\":\"minecraft:dirt\",\"fluid\":{\"fluid\":\"minecraft:water\",\"amount\":50},\"outputs\":["+outputs+"]}"));
        check(slots.size()==66&&grids.equals(List.of("1x6:142,20:64")),"all 64 outputs indexed and accessible in fitting scroll column");
        for(int count:new int[]{1,6,7,16}) {
            String ordered=java.util.stream.IntStream.range(0,count).mapToObj(i->"\"minecraft:small_amethyst_bud\"").collect(java.util.stream.Collectors.joining(","));
            category(recipe(InteractionRecipes.Kind.BUDDING_CATALYSIS,"{\"input\":\"minecraft:budding_amethyst\",\"fluid\":{\"fluid\":\"minecraft:water\",\"amount\":1},\"stages\":["+ordered+"],\"chance\":0,\"attempts\":64}"));
            check(slots.size()==count+2,"all growth stages retained, including repeated configured stages");
            for(int i=2;i<slots.size();i++) {
                check(slots.get(i).items.getFirst().getCount()==64,"maximum attempts count retained");
                check(tip(i,"cwi.jei.spouting.growth_attempts").getArgs()[1].equals("0"),"zero chance displayed safely");
                if(count<=6)check(slots.get(i).y>=0&&slots.get(i).y+16<=156,"all visible stage slots stay inside category");
            }
            check(grids.equals(count>6?List.of("1x6:142,20:"+count):List.of()),"six-slot overflow boundary and maximum 16 stages");
        }
        System.out.println("WorldSpoutingJeiFixture PASS: "+assertions+" assertions; original geometry, native registration/codecs, stage/attempt tooltips, weighted alternatives, tags and 64-output overflow");
    }
}
