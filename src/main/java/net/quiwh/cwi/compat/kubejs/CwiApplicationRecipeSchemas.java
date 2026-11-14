package net.quiwh.cwi.compat.kubejs;

import com.google.gson.*;
import dev.latvian.mods.kubejs.recipe.*;
import dev.latvian.mods.kubejs.recipe.component.*;
import dev.latvian.mods.kubejs.recipe.schema.*;
import dev.latvian.mods.kubejs.recipe.schema.function.RecipeFunctionInstance;
import dev.latvian.mods.kubejs.util.JsonUtils;
import dev.latvian.mods.kubejs.util.TickDuration;
import java.util.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.quiwh.cwi.compat.recipevalue.RecipeValueAdapters;
import net.quiwh.cwi.interaction.BlockSelector;

/** Augment Create's schema, retaining its factory and ordinary constructors. */
public final class CwiApplicationRecipeSchemas {
    public static final Set<String> FUNCTIONS = Set.of("blockInput", "heldItem", "emptyHand", "sneaking", "priority",
            "keepBlock", "removeBlock", "damageHeldItem", "consumeHeldItem", "cooldown", "damagePlayer", "drops", "actions", "sound", "particle");
    private CwiApplicationRecipeSchemas() {}
    public static void register(RecipeSchemaRegistry registry) {
        var existing = registry.namespace("create").get("item_application");
        RecipeSchema original;
        if (existing == null || existing instanceof UnknownRecipeSchemaType) {
            var output = CwiProcessingOutputComponent.TYPE.instance().asListOrSelf().withBounds(dev.latvian.mods.kubejs.util.IntBounds.of(0,128)).outputKey("results");
            var input = IngredientComponent.INGREDIENT.instance().asListOrSelf()
                    .withBounds(dev.latvian.mods.kubejs.util.IntBounds.of(2,2)).inputKey("ingredients");
            var keep = BooleanComponent.BOOLEAN.otherKey("keep_held_item").optional(false).exclude();
            original = new RecipeSchema(output, input, keep).setOpFunction("keepHeldItem", keep, true);
        } else original = existing.schema;
        var options = CwiInteractionActionComponent.TYPE.instance().otherKey("cwi_interaction").optional(new JsonObject()).exclude();
        var keys = new ArrayList<>(original.keys); keys.add(options);
        var schema = new RecipeSchema(original.keyOverrides, keys).factory(original.recipeFactory)
                .typeOverride(original.typeOverride).uniqueIds(original.uniqueIds());
        schema.functions.putAll(original.functions); original.postProcessors().forEach(schema::postProcessor);
        // Force materialization before adding the explicit empty constructor.
        for (var ctor : original.constructors().values()) schema.constructor(ctor);
        schema.constructor(new RecipeConstructor().overrideValue(schema.getKey("results"), List.of())
                .overrideValue(schema.getKey("ingredients"), List.of(Ingredient.of(Items.BARRIER), Ingredient.of(Items.BARRIER))));
        for (String name : FUNCTIONS) {
            var params = switch(name) {
                case "emptyHand", "keepBlock", "removeBlock" -> List.<RecipeComponent<?>>of();
                case "sneaking" -> List.<RecipeComponent<?>>of(BooleanComponent.BOOLEAN.instance());
                case "priority", "damageHeldItem", "consumeHeldItem", "damagePlayer" -> List.<RecipeComponent<?>>of(NumberComponent.INT);
                case "cooldown" -> List.<RecipeComponent<?>>of(TimeComponent.TICKS.instance());
                case "sound" -> List.<RecipeComponent<?>>of(StringComponent.ID.instance());
                default -> List.<RecipeComponent<?>>of(IgnoreComponent.INSTANCE);
            };
            schema.function(new RecipeFunctionInstance(name, params, (cx,args) -> apply(cx, options, name, args)));
        }
        registry.namespace("create").register("item_application", schema);
    }
    private static void apply(RecipeScriptContext cx, RecipeKey<JsonElement> key, String name, List<Object> args) {
        JsonElement old = cx.recipe().getValue(key);
        var data = old == null ? new JsonObject() : old.getAsJsonObject().deepCopy();
        switch (name) {
            case "emptyHand" -> { count(name,args,0,0); data.addProperty("empty_hand",true); }
            case "keepBlock", "removeBlock" -> { count(name,args,0,0); data.addProperty("block_mode",name.equals("keepBlock")?"keep":"remove"); }
            case "sneaking" -> {
                count(name,args,0,1);
                Object raw = args.isEmpty() ? true : args.getFirst();
                if (!(raw instanceof Boolean b)) throw new IllegalArgumentException("sneaking expects a boolean");
                data.addProperty("sneaking", b);
            }
            case "priority", "damageHeldItem", "consumeHeldItem", "damagePlayer" -> {
                count(name,args,1,1); Object raw = args.getFirst();
                if (!(raw instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue()!=Math.rint(n.doubleValue())
                        || n.doubleValue()<Integer.MIN_VALUE || n.doubleValue()>Integer.MAX_VALUE) throw new IllegalArgumentException(name+" expects an integer");
                String field = switch(name) { case "priority" -> "priority"; case "damageHeldItem" -> "damage_held";
                    case "consumeHeldItem" -> "consume_held"; default -> "damage_player"; };
                data.addProperty(field,n.intValue());
            }
            case "cooldown" -> {
                count(name,args,1,1); int ticks = Math.toIntExact(TickDuration.wrap(cx.cx(), args.getFirst()).ticks());
                data.addProperty("cooldown", ticks);
            }
            case "blockInput" -> {
                count(name,args,1,2); String selector = String.valueOf(args.getFirst());
                var block = BlockSelector.of(selector); data.addProperty("block",selector);
                if (args.size()==2) data.add("state",JsonUtils.of(cx.cx(),args.get(1)));
                var items = block.items();
                if (!items.isEmpty()) setInput(cx,0,Ingredient.of(items.stream()));
            }
            case "heldItem" -> {
                count(name,args,1,1); setInput(cx,1,RecipeValueAdapters.applicationHeld(cx,args.getFirst()));
                data.addProperty("empty_hand",false);
            }
            case "drops" -> {
                count(name,args,1,1); var drops = array(data,"drops");
                for (Object entry : entries(args.getFirst())) drops.add(RecipeValueAdapters.applicationDrop(cx,entry));
            }
            case "actions" -> {
                count(name,args,1,1); var json = JsonUtils.of(cx.cx(),args.getFirst());
                if (!json.isJsonArray()) throw new IllegalArgumentException("actions expects an array");
                var actions = array(data,"actions"); json.getAsJsonArray().forEach(actions::add);
            }
            case "sound" -> {
                count(name,args,1,3); var action = new JsonObject(); action.addProperty("type","sound");
                action.addProperty("id",String.valueOf(args.getFirst()));
                if(args.size()>1) action.add("volume",JsonUtils.of(cx.cx(),args.get(1)));
                if(args.size()>2) action.add("pitch",JsonUtils.of(cx.cx(),args.get(2)));
                array(data,"actions").add(action);
            }
            case "particle" -> {
                count(name,args,1,1); var json = JsonUtils.of(cx.cx(),args.getFirst());
                if (json.isJsonPrimitive()) { var action=new JsonObject(); action.addProperty("id",json.getAsString()); json=action; }
                if (!json.isJsonObject()) throw new IllegalArgumentException("particle expects an ID or object");
                json.getAsJsonObject().addProperty("type","particle"); array(data,"actions").add(json);
            }
            default -> throw new IllegalArgumentException("Unknown application function "+name);
        }
        // Validate the whole combination now, so a script points to its own source line.
        net.quiwh.cwi.interaction.application.ApplicationOptions.CODEC.parse(cx.ops().json(),data).getOrThrow();
        cx.recipe().setValue(key,data);
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void setInput(RecipeScriptContext cx, int index, Ingredient value) {
        RecipeKey key = cx.recipe().type.schemaType.schema.getKey("ingredients");
        var previous = cx.recipe().getValue(key);
        var list = previous instanceof List<?> values ? new ArrayList(values) : new ArrayList();
        while(list.size()<2) list.add(Ingredient.of(Items.BARRIER));
        list.set(index,value); cx.recipe().setValue(key,list);
    }
    private static JsonArray array(JsonObject json,String key) {
        if(!json.has(key)) json.add(key,new JsonArray()); return json.getAsJsonArray(key);
    }
    private static List<?> entries(Object raw) {
        raw=net.quiwh.cwi.compat.recipevalue.RecipeValues.unwrap(raw);
        if(raw instanceof List<?> list) return list;
        if(raw instanceof Object[] values) return Arrays.asList(values);
        return List.of(raw);
    }
    private static void count(String name,List<?> args,int min,int max) {
        if(args.size()<min || args.size()>max) throw new IllegalArgumentException(name+" expects "+min+".."+max+" arguments");
    }
}
