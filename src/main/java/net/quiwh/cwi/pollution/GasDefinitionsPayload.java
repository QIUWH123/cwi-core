package net.quiwh.cwi.pollution;

import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Only fluid identities need login sync; each visual packet carries its own server presentation. */
public record GasDefinitionsPayload(Set<ResourceLocation> fluids) implements CustomPacketPayload {
    private static final int LIMIT=1024;
    public static final Type<GasDefinitionsPayload> TYPE=new Type<>(ResourceLocation.parse("cwi:gas_definitions"));
    public static final StreamCodec<RegistryFriendlyByteBuf,GasDefinitionsPayload> CODEC=StreamCodec.of(
        (b,p)->{b.writeVarInt(p.fluids.size());for(var id:p.fluids)b.writeResourceLocation(id);},
        b->{int count=b.readVarInt();if(count<0||count>LIMIT)throw new IllegalArgumentException("Too many gas fluid IDs");
            var ids=new HashSet<ResourceLocation>();for(int i=0;i<count;i++)ids.add(b.readResourceLocation());return new GasDefinitionsPayload(ids);});
    public GasDefinitionsPayload {if(fluids.size()>LIMIT)throw new IllegalArgumentException("Too many gas fluid IDs");fluids=Set.copyOf(fluids);}
    @Override public Type<GasDefinitionsPayload> type(){return TYPE;}
}
