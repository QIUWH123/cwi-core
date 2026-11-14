package net.quiwh.cwi.compat.processing;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.processing.recipe.*;
import java.util.*;
import net.quiwh.cwi.machine.recipe.*;
import net.quiwh.cwi.machine.recipe.MachineRecipe.*;

/** Complete typed IO; the native parameter lists remain available for matching and integrations. */
public record AdvancedProcessing(List<Either<FluidInput,ItemInput>> ingredients,
        List<Either<FluidOutput,ItemOutput>> results, int priority, List<OutputPool> pools) {
    public static final com.mojang.serialization.MapCodec<AdvancedProcessing> MAP_CODEC=RecordCodecBuilder.mapCodec(i->i.group(
            Codec.either(FluidInput.CODEC,ItemInput.CODEC).listOf(0,128).fieldOf("ingredients").forGetter(AdvancedProcessing::ingredients),
            Codec.either(FluidOutput.CODEC,ItemOutput.CODEC).listOf(0,128).fieldOf("results").forGetter(AdvancedProcessing::results),
            Codec.INT.optionalFieldOf("priority",0).forGetter(AdvancedProcessing::priority),
            OutputPool.CODEC.listOf(0,64).optionalFieldOf("output_pools",List.of()).forGetter(AdvancedProcessing::pools)
    ).apply(i,AdvancedProcessing::new));
    public static final Codec<AdvancedProcessing> CODEC=MAP_CODEC.codec();
    public AdvancedProcessing {ingredients=List.copyOf(ingredients);results=List.copyOf(results);pools=List.copyOf(pools);}
    public boolean execution() {
        return !pools.isEmpty() || ingredients.stream().anyMatch(e->e.map(v->v.chance()!=1||!v.settings().equals(EntrySettings.DEFAULT),v->v.count()!=1||v.chance()!=1||!v.settings().equals(EntrySettings.DEFAULT)))
                || results.stream().anyMatch(e->e.map(v->v.chance()!=1||!v.settings().equals(EntrySettings.DEFAULT),v->!v.settings().equals(EntrySettings.DEFAULT)));
    }
    public MachineRecipe transaction(int duration,HeatCondition heat) {
        var ii=new ArrayList<ItemInput>();var fi=new ArrayList<FluidInput>();var io=new ArrayList<ItemOutput>();var fo=new ArrayList<FluidOutput>();
        ingredients.forEach(e->e.ifLeft(fi::add).ifRight(ii::add));results.forEach(e->e.ifLeft(fo::add).ifRight(io::add));
        // Native adapters check their own heat/pressure/structure before each step.
        return new MachineRecipe(MachineRecipes.MIXING,ii,fi,io,fo,Math.max(1,duration),0,0,Integer.MAX_VALUE,HeatCondition.NONE,Optional.empty(),priority,pools);
    }
    public static AdvancedProcessing get(ProcessingRecipe<?,?> recipe) {
        var data=((AdvancedParams)recipe.getParams()).cwi$advanced();
        if(data!=null)return data;
        var in=new ArrayList<Either<FluidInput,ItemInput>>();var out=new ArrayList<Either<FluidOutput,ItemOutput>>();
        recipe.getIngredients().forEach(v->in.add(Either.right(new ItemInput(v,1,1,false))));
        recipe.getFluidIngredients().forEach(v->in.add(Either.left(new FluidInput(v,1,false))));
        recipe.getRollableResults().forEach(v->out.add(Either.right(new ItemOutput(v.getStack(),v.getChance(),false))));
        recipe.getFluidResults().forEach(v->out.add(Either.left(new FluidOutput(v,1,false))));
        return new AdvancedProcessing(in,out,0,List.of());
    }
    /** Hot-path probes must not materialize native IO when no advanced metadata is present. */
    public static boolean execution(ProcessingRecipe<?,?> recipe) {
        var data=((AdvancedParams)recipe.getParams()).cwi$advanced();
        return data!=null&&data.execution();
    }
    public static int priority(net.minecraft.world.item.crafting.Recipe<?> recipe){
        if(!(recipe instanceof ProcessingRecipe<?,?> p))return 0;
        var data=((AdvancedParams)p.getParams()).cwi$advanced();
        return data==null?0:data.priority();
    }
}
