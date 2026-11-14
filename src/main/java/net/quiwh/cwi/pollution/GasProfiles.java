package net.quiwh.cwi.pollution;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

/** Startup-owned profiles. Fluid aliases share one chemical quantity; arbitrary gases use their fluid ID. */
public final class GasProfiles {
    public static final int MAX_PROFILES=256;
    private static final Map<String,GasProfile> profiles=new LinkedHashMap<>();
    private static final Map<String,GasProfile.Builder> pending=new LinkedHashMap<>();
    private static final Map<ResourceLocation,String> aliases=new HashMap<>();
    private static final Map<Fluid,GasProfile> fluidCache=new IdentityHashMap<>();
    private static boolean frozen,livingTargets;
    public record LegacyDose(GasProfile gas,GasProfile.Effect effect) {}
    private static List<LegacyDose> legacyDoses=List.of();
    public static List<LegacyDose> legacyDoses(){return legacyDoses;}
    private static volatile Set<ResourceLocation> remoteFluids;
    public static void remoteFluids(Set<ResourceLocation> fluids){remoteFluids=fluids==null?null:Set.copyOf(fluids);}
    public static boolean isGas(Fluid fluid) {
        if(fluid instanceof FlowingFluid flowing)fluid=flowing.getSource();
        var remote=remoteFluids;
        return remote!=null?remote.contains(BuiltInRegistries.FLUID.getKey(fluid)):fromFluid(fluid)!=null;
    }
    public static Set<ResourceLocation> fluidIds() {
        var ids=new HashSet<ResourceLocation>();
        for(var fluid:BuiltInRegistries.FLUID)if(fromFluid(fluid)!=null)ids.add(BuiltInRegistries.FLUID.getKey(fluid));
        return ids;
    }
    public static GasProfile byId(String id) {
        var key=ResourceLocation.tryParse(id.contains(":")?id:"cwi:"+id);
        return key==null?null:profiles.get(resolve(key.toString()));
    }
    private static String resolve(String id) {
        var seen=new HashSet<String>();
        while(aliases.containsKey(ResourceLocation.parse(id))) {
            if(!seen.add(id))throw new IllegalArgumentException("Cyclic gas alias: "+id);
            id=aliases.get(ResourceLocation.parse(id));
        }
        return id;
    }
    /** Explicit fluid or saved-cloud identity; aliases never create a chemical profile. */
    public static void alias(String aliasId,String profileId) {
        if(frozen)throw new IllegalStateException("Gas aliases are startup-only");
        var alias=ResourceLocation.parse(aliasId);String target=ResourceLocation.parse(profileId).toString();
        if(alias.toString().equals(target)||pending.containsKey(alias.toString())||profiles.containsKey(alias.toString()))
            throw new IllegalArgumentException("Gas alias conflicts with a profile: "+alias);
        var previous=aliases.get(alias);
        if(previous!=null&&!previous.equals(target))throw new IllegalArgumentException("Gas alias already configured: "+alias);
        if(previous==null&&aliases.size()>=4096)throw new IllegalArgumentException("At most 4096 gas aliases");
        aliases.put(alias,target);
        try {resolve(alias.toString());}catch(RuntimeException e){if(previous==null)aliases.remove(alias);throw e;}
    }
    public static Collection<GasProfile> values() { return List.copyOf(profiles.values()); }
    public static GasProfile.Builder configure(String fluidId) {
        if(frozen)throw new IllegalStateException("Gases can only be configured in startup scripts");
        var fluid=ResourceLocation.parse(fluidId);
        if(aliases.containsKey(fluid))throw new IllegalArgumentException("Cannot configure an aliased gas: "+fluid);
        String id=fluid.toString();
        var existing=pending.get(id);
        if(existing==null&&profiles.size()+pending.size()>=MAX_PROFILES)
            throw new IllegalArgumentException("At most "+MAX_PROFILES+" gas profiles");
        if(existing!=null)return existing;
        var builder=new GasProfile.Builder(id);
        pending.put(id,builder);return builder;
    }
    /** Called after fluid registration; catches invalid existing-fluid IDs once, before worlds load. */
    public static void freeze() {
        if(frozen)return;
        for(var id:pending.keySet())if(!BuiltInRegistries.FLUID.containsKey(ResourceLocation.parse(id)))throw new IllegalArgumentException("Unknown gas fluid: "+id);
        for(var id:aliases.keySet())if(!pending.containsKey(resolve(id.toString()))&&!profiles.containsKey(resolve(id.toString())))
            throw new IllegalArgumentException("Unknown gas alias target: "+id);
        pending.forEach((id,b)->profiles.put(id,b.build()));
        for(var profile:profiles.values())for(var rule:profile.effects())
            if(!BuiltInRegistries.MOB_EFFECT.containsKey(rule.effect()))throw new IllegalArgumentException("Unknown gas effect: "+rule.effect());
        var legacyKeys=new HashSet<String>();
        for(var profile:profiles.values())for(var effect:profile.effects())if(effect.dose()!=null) {
            var dose=effect.dose();
            for(var key:new String[]{dose.legacyKey(),dose.legacyOwnerKey()})if(key!=null&&!legacyKeys.add(key))
                throw new IllegalArgumentException("Duplicate legacy gas data key: "+key);
        }
        legacyDoses=profiles.values().stream().flatMap(g->g.effects().stream()
                .filter(e->e.dose()!=null&&e.dose().legacyKey()!=null).map(e->new LegacyDose(g,e))).toList();
        livingTargets=profiles.values().stream().anyMatch(GasProfile::living);
        pending.clear();fluidCache.clear();frozen=true;
    }
    public static GasProfile fromFluid(Fluid fluid) {
        if(fluid instanceof FlowingFluid flowing)fluid=flowing.getSource();
        if(fluidCache.containsKey(fluid))return fluidCache.get(fluid);
        var id=BuiltInRegistries.FLUID.getKey(fluid);
        var profile=id==null?null:byId(id.toString());
        if(frozen)fluidCache.put(fluid,profile);
        return profile;
    }
    public static boolean hasLivingTargets(){return livingTargets;}
    public static void validateEnchantmentIds(net.minecraft.core.RegistryAccess access) {
        var registry=access.registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        for(var profile:profiles.values())for(var route:profile.routes().values())for(var id:route.enchantments())
            if(!registry.containsKey(id))throw new IllegalArgumentException("Unknown gas protection enchantment: "+id);
    }
    private GasProfiles() {}
}
