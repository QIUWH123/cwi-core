package net.quiwh.cwi.mixin.jei;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipeSerializer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.quiwh.cwi.compat.AssemblyOutputVisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SequencedAssemblyRecipeSerializer.class, remap = false)
abstract class AssemblySerializerMixin {
    @ModifyReturnValue(method = "codec", at = @At("RETURN"))
    private MapCodec<SequencedAssemblyRecipe> cwi$displayCodec(MapCodec<SequencedAssemblyRecipe> original) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                original.forGetter((SequencedAssemblyRecipe recipe) -> recipe),
                Codec.BOOL.optionalFieldOf("show_outputs", false).forGetter(recipe -> ((AssemblyOutputVisibility) recipe).cwi$showOutputs()))
                .apply(instance, (recipe, show) -> { ((AssemblyOutputVisibility) recipe).cwi$showOutputs(show); return recipe; }));
    }

    @ModifyReturnValue(method = "streamCodec", at = @At("RETURN"))
    private StreamCodec<RegistryFriendlyByteBuf, SequencedAssemblyRecipe> cwi$displayStream(
            StreamCodec<RegistryFriendlyByteBuf, SequencedAssemblyRecipe> original) {
        return StreamCodec.of((buffer, recipe) -> {
            original.encode(buffer, recipe);
            buffer.writeBoolean(((AssemblyOutputVisibility) recipe).cwi$showOutputs());
        }, buffer -> {
            var recipe = original.decode(buffer);
            ((AssemblyOutputVisibility) recipe).cwi$showOutputs(buffer.readBoolean());
            return recipe;
        });
    }
}
