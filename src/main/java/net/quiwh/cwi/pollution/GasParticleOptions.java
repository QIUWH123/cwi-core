package net.quiwh.cwi.pollution;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Presentation travels with each particle, so remote clients use the server's color and motion. */
public record GasParticleOptions(int color,float opacity,float buoyancy) implements ParticleOptions {
    public static final MapCodec<GasParticleOptions> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
        Codec.intRange(0,0xFFFFFF).fieldOf("color").forGetter(GasParticleOptions::color),
        Codec.floatRange(0,1).fieldOf("opacity").forGetter(GasParticleOptions::opacity),
        Codec.floatRange(-1,1).fieldOf("buoyancy").forGetter(GasParticleOptions::buoyancy)
    ).apply(i,GasParticleOptions::new));
    public static final StreamCodec<RegistryFriendlyByteBuf,GasParticleOptions> STREAM_CODEC=StreamCodec.of(
        (buffer,value)->{buffer.writeInt(value.color);buffer.writeFloat(value.opacity);buffer.writeFloat(value.buoyancy);},
        buffer->new GasParticleOptions(buffer.readInt(),buffer.readFloat(),buffer.readFloat()));
    public GasParticleOptions {
        if(color<0||color>0xFFFFFF||!Float.isFinite(opacity)||opacity<0||opacity>1||!Float.isFinite(buoyancy)||buoyancy< -1||buoyancy>1)
            throw new IllegalArgumentException("Invalid gas particle presentation");
    }
    public GasParticleOptions(GasProfile gas){this(gas.color(),gas.opacity(),(float)gas.buoyancy());}
    @Override public ParticleType<GasParticleOptions> getType(){return GasPollution.SMOKE.get();}
}
