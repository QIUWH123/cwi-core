import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import java.lang.reflect.Method;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.neoforge.registries.GameData;
import net.quiwh.cwi.compat.jei.MachineJeiCategory;
import net.quiwh.cwi.machine.recipe.*;

/** Executes actual machine codecs and JEI callbacks; shares the existing native JEI capture harness. */
public final class MachineRecipeJeiFixture {
    static void check(boolean value, String reason) { WorldSpoutingJeiFixture.check(value, reason); }
    static MachineRecipe decode(MachineRecipes.Entry entry, String json) {
        return entry.serializer().codec().codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }
    static MachineJeiCategory category(MachineRecipes.Entry entry, MachineRecipe recipe) {
        var drawable = WorldSpoutingJeiFixture.proxy(IDrawable.class, (p,m,a) -> switch(m.getName()) {
            case "getWidth" -> MachineJeiCategory.width(entry);
            case "getHeight" -> MachineJeiCategory.height(entry);
            default -> WorldSpoutingJeiFixture.def(m.getReturnType());
        });
        var holder = new RecipeHolder<>(ResourceLocation.parse("cwi:fixture/machine"), recipe);
        var info = new CreateRecipeCategory.Info<>(mezz.jei.api.recipe.RecipeType.<MachineRecipe>createRecipeHolderType(entry.getId()),
                Component.literal("fixture"), drawable, drawable, () -> List.of(holder), List.of());
        var category = new MachineJeiCategory(info, entry);
        WorldSpoutingJeiFixture.slots.clear(); WorldSpoutingJeiFixture.grids.clear();
        var layout = WorldSpoutingJeiFixture.layout();
        var captures = WorldSpoutingJeiFixture.proxy(IRecipeLayoutBuilder.class, (p,m,a) -> {
            if (m.getName().equals("addInvisibleIngredients")) return WorldSpoutingJeiFixture.acceptor(new WorldSpoutingJeiFixture.Slot());
            return m.invoke(layout, a);
        });
        category.setRecipe(captures, holder, null);
        category.createRecipeExtras(WorldSpoutingJeiFixture.extras(), holder, null);
        return category;
    }
    @SuppressWarnings("unchecked")
    static List<Component> time(MachineJeiCategory category, MachineRecipe recipe) throws Exception {
        Method tooltip = MachineJeiCategory.class.getDeclaredMethod("getTooltipStrings", MachineRecipe.class, IRecipeSlotsView.class, double.class, double.class);
        tooltip.setAccessible(true);
        return (List<Component>)tooltip.invoke(category, recipe, null, 60d, 50d);
    }
    static TranslatableContents tip(List<Component> tips, String key) {
        return tips.stream().map(Component::getContents).filter(v -> v instanceof TranslatableContents t && t.getKey().equals(key))
                .map(v -> (TranslatableContents)v).findFirst().orElseThrow(() -> new AssertionError("Missing tooltip: " + key));
    }
    public static void main(String[] args) throws Exception {
        MinimalNativeBootstrap.initialize(); SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); GameData.unfreezeData();
        var bus = BusBuilder.builder().build(); MachineRecipes.register(bus);
        var fluidTypes = net.neoforged.neoforge.common.NeoForgeMod.class.getDeclaredField("FLUID_INGREDIENT_TYPES");
        fluidTypes.setAccessible(true);
        ((net.neoforged.neoforge.registries.DeferredRegister<?>)fluidTypes.get(null)).register(bus);
        bus.post(MinimalNativeBootstrap.event(net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_INGREDIENT_TYPES.key(),
                net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_INGREDIENT_TYPES));
        bus.post(MinimalNativeBootstrap.event(Registries.RECIPE_TYPE, BuiltInRegistries.RECIPE_TYPE));
        bus.post(MinimalNativeBootstrap.event(Registries.RECIPE_SERIALIZER, BuiltInRegistries.RECIPE_SERIALIZER));
        var mixed = decode(MachineRecipes.MIXING, """
            {"ingredients":[{"item":"minecraft:sand"},{"item":"minecraft:sand"},
              {"type":"neoforge:single","fluid":"minecraft:water","amount":250}],
             "results":[{"id":"minecraft:clay_ball","count":3,"chance":0.25},{"id":"minecraft:lava","amount":75}],"processing_time":100}
            """);
        var category = category(MachineRecipes.MIXING, mixed);
        var slots = WorldSpoutingJeiFixture.slots;
        check(slots.size()==4, "coalesced native inputs preserve authored slot layout");
        check(mixed.getIngredients().size()==2 && slots.getFirst().items.getFirst().getCount()==2,
                "native ingredient units and JEI counted stack agree");
        WorldSpoutingJeiFixture.at(0, RecipeIngredientRole.INPUT, 9, 48);
        WorldSpoutingJeiFixture.at(1, RecipeIngredientRole.INPUT, 27, 48);
        WorldSpoutingJeiFixture.at(2, RecipeIngredientRole.OUTPUT, 133, 48);
        WorldSpoutingJeiFixture.at(3, RecipeIngredientRole.OUTPUT, 151, 48);
        check(slots.get(2).items.getFirst().is(Items.CLAY_BALL) && slots.get(2).items.getFirst().getCount()==3,
                "item quantity matches decoded result");
        check(WorldSpoutingJeiFixture.tip(2,"create.recipe.processing.chance").getArgs()[0].equals("25"),"native whole-percent item chance");
        check(slots.get(1).capacity==250 && slots.get(3).capacity==75, "native fluid quantities");
        check(tip(time(category,mixed),"cwi.recipe.mixing_base_time").getArgs()[0].equals(100),"base Create processing time");
        check(tip(time(category,mixed),"cwi.recipe.time_at_256").getArgs()[0].equals(16),"active mixer duration uses execution formula");
        var advanced=decode(MachineRecipes.MIXING,"""
            {"ingredients":[{"ingredient":{"item":"minecraft:paper"},"chance":0},
              {"ingredient":{"fluid":"minecraft:water"},"amount":10,"per_tick":true}],
             "results":[{"stack":{"id":"minecraft:clay_ball","count":2},"per_tick":true},
              {"stack":{"id":"minecraft:lava","amount":5},"chance":0.5,"per_tick":true}]}
            """);
        category(MachineRecipes.MIXING,advanced);
        WorldSpoutingJeiFixture.tip(0,"cwi.recipe.catalyst");
        check(WorldSpoutingJeiFixture.tip(1,"cwi.recipe.fluid_rate").getArgs()[0].equals(10),"fluid input rate");
        check(WorldSpoutingJeiFixture.tip(2,"cwi.recipe.item_rate").getArgs()[0].equals(2),"item output rate");
        check(WorldSpoutingJeiFixture.tip(3,"cwi.recipe.fluid_rate").getArgs()[0].equals(5),"fluid output rate");
        check(slots.subList(1,4).stream().flatMap(s -> s.tips.stream()).noneMatch(c -> c.getContents() instanceof TranslatableContents t
                && t.getKey().equals("cwi.recipe.working_interval")),"per-tick rate replaces the duplicate one-tick interval");
        var roles=decode(MachineRecipes.MIXING,"""
            {"ingredients":[{"ingredient":{"item":"minecraft:flint"},"catalyst":true},
              {"ingredient":{"item":"minecraft:iron_sword"},"damage":10},
              {"ingredient":{"type":"neoforge:single","fluid":"minecraft:water"},"amount":5,"per_tick":2,"tick_mode":"recipe"},
              {"ingredient":{"item":"minecraft:sand"},"chance":0.25}],
             "results":[{"stack":{"id":"minecraft:clay_ball"},"per_tick":2}],"priority":10,
             "output_pools":[{"entries":[{"stack":{"id":"minecraft:gravel","count":2},"weight":3},{"stack":{"id":"minecraft:cobblestone"}}],"show_outputs":true},
                             {"entries":[{"stack":{"id":"minecraft:redstone"}}]}]}
            """);
        var rolesCategory=category(MachineRecipes.MIXING,roles);
        check(slots.size()==7,"one separate slot for each independent pool");
        WorldSpoutingJeiFixture.tip(0,"cwi.recipe.catalyst"); WorldSpoutingJeiFixture.tip(1,"cwi.recipe.damage_cost");
        check(WorldSpoutingJeiFixture.tip(2,"cwi.recipe.consume_chance").getArgs()[0].equals("25"),"whole-percent consumption chance");
        check(WorldSpoutingJeiFixture.tip(3,"cwi.recipe.recipe_interval").getArgs()[0].equals(2),"recipe interval tooltip");
        check(WorldSpoutingJeiFixture.tip(4,"cwi.recipe.working_interval").getArgs()[0].equals(2),"longer active intervals remain visible");
        check(slots.get(0).background==slots.get(1).background && slots.get(0).background!=slots.get(3).background
                && slots.get(4).background==slots.get(3).background,"catalyst/tool and periodic entries use distinct frames");
        check(slots.get(5).items.size()==2 && slots.get(5).overlay==null && slots.get(6).items.isEmpty() && slots.get(6).overlay!=null,
                "shown pool cycles alternatives; hidden pool draws native-style question mark");
        check(slots.get(5).background==CreateRecipeCategory.getRenderedSlot(0f),"visible pool uses Just Enough Time's native chance frame");
        check(slots.get(5).tips.size()==1,"visible pool adds only the displayed item's chance, without the hidden salvage label");
        WorldSpoutingJeiFixture.tip(6,"create.recipe.assembly.junk");
        check(WorldSpoutingJeiFixture.tip(5,"create.recipe.processing.chance").getArgs()[0].equals("75"),"first visible pool entry chance");
        var secondTip=WorldSpoutingJeiFixture.tooltip(slots.get(5),1,slots.get(5).callbacks);
        check(tip(secondTip,"create.recipe.processing.chance").getArgs()[0].equals("25"),"chance follows the actually displayed cycling entry");
        check(time(rolesCategory,roles).stream().noneMatch(c->c.getString().toLowerCase().contains("priority")),"priority stays out of JEI");
        var precisePools=decode(MachineRecipes.MIXING,"""
            {"ingredients":[{"item":"minecraft:sand"}],
             "output_pools":[{"entries":[{"stack":{"id":"minecraft:gravel","count":2},"weight":1},
                                          {"stack":{"id":"minecraft:cobblestone"},"weight":9999}],"show_outputs":true},
                             {"entries":[{"stack":{"id":"minecraft:redstone"},"weight":1},
                                          {"stack":{"id":"minecraft:glowstone_dust"},"weight":2}],"show_outputs":true},
                             {"entries":[{"stack":{"id":"minecraft:diamond"}}],"show_outputs":true}]}
            """);
        category(MachineRecipes.MIXING,precisePools);
        String[][] chances={{"<1",">99"},{"33","67"},{">99"}};
        for(int pool=0;pool<chances.length;pool++) {
            var slot=slots.get(pool+1);
            for(int item=0;item<chances[pool].length;item++) {
                var tooltip=WorldSpoutingJeiFixture.tooltip(slot,item,slot.callbacks);
                check(tooltip.size()==1 && tip(tooltip,"create.recipe.processing.chance").getArgs()[0].equals(chances[pool][item]),
                        "independent visible pools use native whole percentages for each cycling item");
                check(tooltip.getFirst().getStyle().getColor().getValue()==net.minecraft.ChatFormatting.GOLD.getColor(),
                        "visible pool chance uses native Create gold styling");
            }
        }
        check(slots.get(1).items.getFirst().getCount()==2,"visible cycling pool preserves the whole output stack count");
        var overflowJson=JsonParser.parseString("{\"ingredients\":[{\"item\":\"minecraft:sand\"}],\"output_pools\":[]}").getAsJsonObject();
        var poolJson=JsonParser.parseString("{\"entries\":[{\"stack\":{\"id\":\"minecraft:gravel\"}}]}");
        for(int i=0;i<7;i++)overflowJson.getAsJsonArray("output_pools").add(poolJson.deepCopy());
        category(MachineRecipes.MIXING,decode(MachineRecipes.MIXING,overflowJson.toString()));
        check(WorldSpoutingJeiFixture.grids.contains("2x3:133,12:7"),"overflow pools remain accessible in output scroll grid");
        var furnace=decode(MachineRecipes.BLAST_FURNACE,"""
            {"ingredients":[{"item":"minecraft:iron_ingot"}],"results":[{"id":"minecraft:lava","amount":100}],"heat_requirement":"superheated","maximum_heat":2200}
            """);
        var furnaceCategory=category(MachineRecipes.BLAST_FURNACE,furnace);
        tip(time(furnaceCategory,furnace),"cwi.recipe.furnace_parallel");
        check(tip(time(furnaceCategory,furnace),"cwi.recipe.maximum_temperature").getArgs()[0].equals(2200),
                "JEI displays the actual maximum furnace temperature");
        check(MachineJeiCategory.width(MachineRecipes.BLAST_FURNACE)==178 && MachineJeiCategory.height(MachineRecipes.BLAST_FURNACE)==126,
                "new furnace page has room for separate IO and readable heat/time");
        var advancedFurnace=decode(MachineRecipes.BLAST_FURNACE,"""
            {"ingredients":[{"item":"minecraft:sand"},{"ingredient":{"item":"minecraft:flint"},"catalyst":true},
              {"ingredient":{"type":"neoforge:single","fluid":"minecraft:water"},"amount":5,"per_tick":2,"tick_mode":"recipe"}],
             "results":[{"stack":{"id":"minecraft:gold_nugget"},"per_tick":2},{"id":"minecraft:lava","amount":25}],
             "output_pools":[{"entries":[{"stack":{"id":"minecraft:gravel"},"weight":3},{"stack":{"id":"minecraft:cobblestone"}}],"show_outputs":true},
                              {"entries":[{"stack":{"id":"minecraft:redstone"}}]}],"priority":10}
            """);
        category(MachineRecipes.BLAST_FURNACE,advancedFurnace);
        WorldSpoutingJeiFixture.at(0,RecipeIngredientRole.INPUT,8,12);
        WorldSpoutingJeiFixture.at(1,RecipeIngredientRole.INPUT,26,12);
        WorldSpoutingJeiFixture.at(2,RecipeIngredientRole.INPUT,8,57);
        WorldSpoutingJeiFixture.at(3,RecipeIngredientRole.OUTPUT,134,12);
        WorldSpoutingJeiFixture.at(4,RecipeIngredientRole.OUTPUT,134,57);
        check(slots.get(0).background==CreateRecipeCategory.getRenderedSlot(),"furnace uses native Create slot");
        check(slots.get(1).background!=slots.get(0).background && slots.get(2).background==slots.get(3).background,
                "furnace catalysts and periodic IO retain external frames without being overwritten");
        check(slots.get(5).items.size()==2 && slots.get(6).overlay!=null,"furnace pools have independent visible/hidden presentation");
        var longFurnace=JsonParser.parseString("{\"ingredients\":[{\"item\":\"minecraft:sand\"}],\"results\":[]}").getAsJsonObject();
        for(int i=0;i<7;i++)longFurnace.getAsJsonArray("results").add(JsonParser.parseString("{\"id\":\"minecraft:stone\"}"));
        category(MachineRecipes.BLAST_FURNACE,decode(MachineRecipes.BLAST_FURNACE,longFurnace.toString()));
        check(WorldSpoutingJeiFixture.grids.contains("2x2:134,12:7"),"furnace overflow uses item-only scroll grid");
        var incubating=decode(MachineRecipes.INCUBATING,"""
            {"ingredients":[{"item":"minecraft:paper"}],"results":[{"id":"minecraft:map"}],
             "incubation_display":{"microbe":{"id":"minecraft:slime_ball"},"yield_count":2,"steps":[{"type":"deploying","item":{"id":"minecraft:sugar"},"count":3}]}}
            """);
        category(MachineRecipes.INCUBATING,incubating);
        check(incubating.getIngredients().size()==1 && WorldSpoutingJeiFixture.slots.stream().filter(s -> s.role==RecipeIngredientRole.INPUT).count()==1,
                "preparation guidance remains display-only");
        check(WorldSpoutingJeiFixture.slots.stream().filter(s -> s.role==RecipeIngredientRole.RENDER_ONLY).count()==1,"preparation shown separately");
        System.out.println("Machine recipe JEI contracts PASS: " + WorldSpoutingJeiFixture.assertions);
    }
}
