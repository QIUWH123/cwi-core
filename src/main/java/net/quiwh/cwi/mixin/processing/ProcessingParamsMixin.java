package net.quiwh.cwi.mixin.processing;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.processing.recipe.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.quiwh.cwi.compat.processing.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ProcessingRecipeParams.class,remap=false)
abstract class ProcessingParamsMixin implements AdvancedParams {
    @Shadow protected NonNullList<Ingredient> ingredients;
    @Shadow protected NonNullList<ProcessingOutput> results;
    @Shadow protected NonNullList<SizedFluidIngredient> fluidIngredients;
    @Shadow protected NonNullList<FluidStack> fluidResults;
    @Shadow protected int processingDuration;
    @Shadow protected HeatCondition requiredHeat;
    @Unique private AdvancedProcessing cwi$data;
    public AdvancedProcessing cwi$advanced(){return cwi$data;}
    public void cwi$advanced(AdvancedProcessing value){cwi$data=value;}
    public AdvancedProcessing cwi$current(){
        if(cwi$data!=null)return cwi$data;
        var in=new ArrayList<com.mojang.datafixers.util.Either<net.quiwh.cwi.machine.recipe.MachineRecipe.FluidInput,net.quiwh.cwi.machine.recipe.MachineRecipe.ItemInput>>();
        var out=new ArrayList<com.mojang.datafixers.util.Either<net.quiwh.cwi.machine.recipe.MachineRecipe.FluidOutput,net.quiwh.cwi.machine.recipe.MachineRecipe.ItemOutput>>();
        ingredients.forEach(v->in.add(com.mojang.datafixers.util.Either.right(new net.quiwh.cwi.machine.recipe.MachineRecipe.ItemInput(v,1,1,false))));
        fluidIngredients.forEach(v->in.add(com.mojang.datafixers.util.Either.left(new net.quiwh.cwi.machine.recipe.MachineRecipe.FluidInput(v,1,false))));
        results.forEach(v->out.add(com.mojang.datafixers.util.Either.right(new net.quiwh.cwi.machine.recipe.MachineRecipe.ItemOutput(v.getStack(),v.getChance(),false))));
        fluidResults.forEach(v->out.add(com.mojang.datafixers.util.Either.left(new net.quiwh.cwi.machine.recipe.MachineRecipe.FluidOutput(v,1,false))));
        return new AdvancedProcessing(in,out,0,List.of());
    }
    public int cwi$duration(){return processingDuration;}
    public HeatCondition cwi$heat(){return requiredHeat;}
    public void cwi$load(AdvancedProcessing data,int duration,HeatCondition heat){
        cwi$data=data;processingDuration=duration;requiredHeat=heat;
        data.ingredients().forEach(e->e.ifLeft(v->fluidIngredients.add(v.ingredient())).ifRight(v->ingredients.add(v.ingredient())));
        data.results().forEach(e->e.ifLeft(v->fluidResults.add(v.stack())).ifRight(v->results.add(new ProcessingOutput(v.stack(),v.chance()))));
    }
    @ModifyReturnValue(method="codec",at=@At("RETURN"))
    private static <P extends ProcessingRecipeParams> MapCodec<P> cwi$codec(MapCodec<P> original,Supplier<P> factory){
        return RecordCodecBuilder.mapCodec(i->i.group(
                AdvancedProcessing.MAP_CODEC.forGetter((P p)->((AdvancedParams)p).cwi$current()),
                Codec.INT.optionalFieldOf("processing_time",0).forGetter((P p)->((AdvancedParams)p).cwi$duration()),
                HeatCondition.CODEC.optionalFieldOf("heat_requirement",HeatCondition.NONE).forGetter((P p)->((AdvancedParams)p).cwi$heat())
        ).apply(i,(data,duration,heat)->{
            P value=factory.get();((AdvancedParams)value).cwi$load(data,duration,heat);
            return value;
        }));
    }
    @Inject(method="encode",at=@At("TAIL"))
    private void cwi$encode(RegistryFriendlyByteBuf buffer,CallbackInfo ci){
        ByteBufCodecs.fromCodecWithRegistries(AdvancedProcessing.CODEC).encode(buffer,cwi$current());
    }
    @Inject(method="decode",at=@At("TAIL"))
    private void cwi$decode(RegistryFriendlyByteBuf buffer,CallbackInfo ci){
        cwi$data=ByteBufCodecs.fromCodecWithRegistries(AdvancedProcessing.CODEC).decode(buffer);
    }
}
