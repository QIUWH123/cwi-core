package net.quiwh.cwi.compat.kubejs;
import java.util.*;
import dev.latvian.mods.kubejs.recipe.*;
import dev.latvian.mods.kubejs.recipe.component.*;
import dev.latvian.mods.kubejs.recipe.schema.*;
import dev.latvian.mods.kubejs.recipe.schema.function.RecipeFunctionInstance;
import net.quiwh.cwi.compat.processing.NativeRecipeTypes;
import net.quiwh.cwi.compat.recipevalue.RecipeValueAdapters;
import net.quiwh.cwi.machine.recipe.OutputPool;

public final class CwiNativeProcessingSchemas {
    @SuppressWarnings({"rawtypes","unchecked"})
    public static void register(RecipeSchemaRegistry registry){
        for(String id:NativeRecipeTypes.ALL){
            String[] parts=id.split(":");var old=registry.namespace(parts[0]).get(parts[1]);
            // KubeJS Create is optional: supply only missing base-Create processing schemas.
            if((old==null||old instanceof UnknownRecipeSchemaType)&&parts[0].equals("create")){
                registry.namespace(parts[0]).register(parts[1],CwiAddonRecipeSchemas.processing(false));
                old=registry.namespace(parts[0]).get(parts[1]);
            }
            if(old==null||old instanceof UnknownRecipeSchemaType)continue;
            var input=CwiMachineRecipeComponents.FLUID_INPUT.instance().or(CwiMachineRecipeComponents.ITEM_INPUT.instance()).asListOrSelf().withBounds(dev.latvian.mods.kubejs.util.IntBounds.of(0,128)).inputKey("ingredients");
            var output=CwiMachineRecipeComponents.FLUID_OUTPUT.instance().or(CwiMachineRecipeComponents.ITEM_OUTPUT.instance()).asListOrSelf().withBounds(dev.latvian.mods.kubejs.util.IntBounds.of(0,128)).outputKey("results");
            var priority=NumberComponent.INT.otherKey("priority").optional(0).exclude();
            var pools=CwiMachineRecipeComponents.OUTPUT_POOL.instance().asList().withBounds(dev.latvian.mods.kubejs.util.IntBounds.of(0,64)).outputKey("output_pools").optional(List.of()).exclude();
            var keys=new ArrayList<RecipeKey<?>>();var replaced=new IdentityHashMap<RecipeKey<?>,RecipeKey<?>>();
            for(var key:old.schema.keys){var next=key.name.equals("ingredients")?input:key.name.equals("results")?output:key;keys.add(next);replaced.put(key,next);}
            if(keys.stream().noneMatch(k->k.name.equals("ingredients")))keys.add(input);
            if(keys.stream().noneMatch(k->k.name.equals("results")))keys.add(output);
            if(id.equals("createaddition:rolling")&&keys.stream().noneMatch(k->k.name.equals("processing_time")))
                keys.add(NumberComponent.INT.otherKey("processing_time").optional(0));
            keys.add(priority);keys.add(pools);
            var overrides=new IdentityHashMap<RecipeKey<?>,RecipeOptional<?>>();old.schema.keyOverrides.forEach((k,v)->overrides.put(replaced.getOrDefault(k,k),v));
            var schema=new RecipeSchema(overrides,keys).factory(old.schema.recipeFactory).typeOverride(old.schema.typeOverride).uniqueIds(old.schema.uniqueIds().stream().<RecipeKey<?>>map(k->replaced.getOrDefault(k,k)).toList());
            for(var function:old.schema.functions.values()){
                if(function.function() instanceof dev.latvian.mods.kubejs.recipe.schema.function.AddToListFunction.Resolved<?> add){
                    var next=replaced.getOrDefault(add.key(),add.key());
                    schema.function(new RecipeFunctionInstance(function.name(),new dev.latvian.mods.kubejs.recipe.schema.function.AddToListFunction.Resolved((RecipeKey)next)));
                }else schema.function(function);
            }
            old.schema.postProcessors().forEach(schema::postProcessor);
            if(!old.schema.constructorsGenerated())for(var constructor:old.schema.constructors().values()){
                var copy=new RecipeConstructor(constructor.keys.stream().<RecipeKey<?>>map(k->replaced.getOrDefault(k,k)).toList());
                copy.overrides=new IdentityHashMap<>();constructor.overrides.forEach((k,v)->copy.overrides.put(replaced.getOrDefault(k,k),v));
                copy.defaultValues=new IdentityHashMap<>();constructor.defaultValues.forEach((k,v)->copy.defaultValues.put(replaced.getOrDefault(k,k),v));schema.constructor(copy);}
            for(var key:List.of(input,output))schema.function(new RecipeFunctionInstance(key.name,List.of(IgnoreComponent.INSTANCE),(cx,args)->{
                if(args.size()!=1)throw new IllegalArgumentException(key.name+" expects one item/fluid list");
                cx.recipe().setValue((RecipeKey)key,RecipeValueAdapters.wrap(cx,key,args.getFirst()));}));
            schema.function(new RecipeFunctionInstance("priority",List.of(IgnoreComponent.INSTANCE),(cx,args)->{
                if(args.size()!=1||!(args.getFirst() instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=Math.rint(n.doubleValue())||n.doubleValue()<Integer.MIN_VALUE||n.doubleValue()>Integer.MAX_VALUE)throw new IllegalArgumentException("priority expects a signed 32-bit integer");
                cx.recipe().setValue(priority,((Number)args.getFirst()).intValue());}));
            schema.function(new RecipeFunctionInstance("outputPool",List.of(IgnoreComponent.INSTANCE),(cx,args)->{
                if(!NativeRecipeTypes.itemOutput(id))throw new IllegalArgumentException("This machine has no item outputs");
                if(args.size()!=1)throw new IllegalArgumentException("outputPool expects one item array");
                var list=new ArrayList<OutputPool>(cx.recipe().getValue(pools));if(list.size()>=64)throw new IllegalArgumentException("Maximum 64 pools");
                list.add(RecipeValueAdapters.pool(cx,args.getFirst()));cx.recipe().setValue(pools,list);}));
            schema.function(new RecipeFunctionInstance("showOutputs",List.of(IgnoreComponent.INSTANCE),(cx,args)->{
                if(args.size()>1||!args.isEmpty()&&!(args.getFirst() instanceof Boolean))throw new IllegalArgumentException("showOutputs expects no argument or a boolean");
                var list=new ArrayList<OutputPool>(cx.recipe().getValue(pools));if(list.isEmpty())throw new IllegalArgumentException("showOutputs must follow outputPool");
                list.set(list.size()-1,new OutputPool(list.getLast().entries(),args.isEmpty()||(Boolean)args.getFirst()));cx.recipe().setValue(pools,list);}));
            registry.namespace(parts[0]).register(parts[1],schema);
        }
    }
    private CwiNativeProcessingSchemas(){}
}
