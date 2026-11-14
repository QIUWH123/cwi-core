package net.quiwh.cwi.mixin.application;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.kinetics.deployer.*;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.quiwh.cwi.interaction.application.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ItemApplicationRecipe.Serializer.class, remap = false)
abstract class ApplicationSerializerMixin<R extends ItemApplicationRecipe> {
    @ModifyReturnValue(method = "codec", at = @At("RETURN"))
    private MapCodec<R> cwi$options(MapCodec<R> original) {
        return RecordCodecBuilder.<R>mapCodec(i -> i.group(original.forGetter(r -> r),
                ApplicationOptions.CODEC.optionalFieldOf("cwi_interaction").forGetter(r -> Optional.ofNullable(((ApplicationExtension)r).cwi$interaction())))
                .apply(i, (r, options) -> { ((ApplicationExtension)r).cwi$interaction(options.orElse(null)); return r; }))
                .validate(r -> {
                    if (((ApplicationExtension)r).cwi$interaction() != null) {
                        if (!(r instanceof ManualApplicationRecipe)) return DataResult.error(() -> "cwi_interaction is manual item application only");
                        if (r.getIngredients().size() != 2) return DataResult.error(() -> "Manual interaction needs a block ingredient and a held ingredient");
                    }
                    return DataResult.success(r);
                });
    }
    @ModifyReturnValue(method = "streamCodec", at = @At("RETURN"))
    private StreamCodec<RegistryFriendlyByteBuf,R> cwi$network(StreamCodec<RegistryFriendlyByteBuf,R> original) {
        var options = ByteBufCodecs.fromCodecWithRegistries(ApplicationOptions.CODEC);
        return StreamCodec.of((b,r) -> {
            original.encode(b,r); var data = ((ApplicationExtension)r).cwi$interaction();
            b.writeBoolean(data != null); if (data != null) options.encode(b,data);
        }, b -> { var r = original.decode(b); ((ApplicationExtension)r).cwi$interaction(b.readBoolean() ? options.decode(b) : null); return r; });
    }
}
