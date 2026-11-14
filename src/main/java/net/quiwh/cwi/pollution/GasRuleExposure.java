package net.quiwh.cwi.pollution;

import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Bounded once-per-second native evaluation of startup data. */
public final class GasRuleExposure {
    private static final String DOSES="cwi:gas_doses",OWNERS="cwi:gas_effect_owners";
    private static final int MAX_DOSES=128;
    private static final Map<ServerLevel,long[]> LIVING_BUDGET=new WeakHashMap<>();
    private record Exposure(GasProfile gas,Map<GasRoute,Double> concentrations){}
    private record Result(Holder<MobEffect> effect,int amplifier,int duration,boolean dose){}

    public static void tickLiving(LivingEntity entity) {
        if(entity instanceof Player||!(entity.level() instanceof ServerLevel level)||!GasProfiles.hasLivingTargets()
                ||Math.floorMod(level.getGameTime()+entity.getId(),20)!=0)return;
        var budget=LIVING_BUDGET.computeIfAbsent(level,k->new long[]{Long.MIN_VALUE,0});
        if(budget[0]!=level.getGameTime()){budget[0]=level.getGameTime();budget[1]=0;}
        if(budget[1]++>=128)return;
        tick(entity,GasClouds.existing(level));
    }
    public static void tick(LivingEntity entity,GasClouds clouds) {
        if(!(entity.level() instanceof ServerLevel level)||!entity.isAlive())return;
        if(entity instanceof Player p&&(p.isCreative()||p.isSpectator())){cure(entity);return;}
        var eye=entity.getEyePosition();var box=entity.getBoundingBox();
        var body=List.of(new Vec3(entity.getX(),box.minY+.1,entity.getZ()),
                new Vec3(entity.getX(),box.minY+box.getYsize()*.5,entity.getZ()),
                new Vec3(entity.getX(),box.maxY-.1,entity.getZ()));
        var samples=new ArrayList<>(body);samples.add(eye);
        Set<GasProfile> gases=clouds==null?new LinkedHashSet<>():clouds.at(level,samples);
        var data=entity.getPersistentData().getCompound(DOSES);
        importLegacy(entity,data);
        // Saved dose continues to recover after the entity leaves all clouds.
        int doseCount=0;
        for(var key:new ArrayList<>(data.getAllKeys())) {
            if(doseCount++>=MAX_DOSES){data.remove(key);continue;}
            int split=key.indexOf('|');var gas=split<0?null:GasProfiles.byId(key.substring(0,split));
            if(gas!=null&&gas.effects().stream().anyMatch(e->e.dose()!=null&&key.equals(gas.id()+"|"+e.id())))gases.add(gas);
            else data.remove(key);
        }
        var exposures=new ArrayList<Exposure>();double damage=0;
        double gasAmount=0,oxygenAmount=GasOxygen.AMBIENT_FRACTION;boolean oxygenApplies=entity instanceof Player;
        for(var gas:gases) {
            double atEye=clouds==null?0:clouds.concentration(level,eye,gas);
            // Raw mixture composition is independent of toxin filtration and dose rules.
            gasAmount+=atEye;oxygenAmount+=atEye*gas.oxygenFraction();
            if(gas.living()&&atEye>0)oxygenApplies=true;
            if(!(entity instanceof Player)&&!gas.living())continue;
            var concentrations=new EnumMap<GasRoute,Double>(GasRoute.class);
            concentrations.put(GasRoute.INHALATION,atEye);concentrations.put(GasRoute.EYES,atEye);
            double skin=0;if(clouds!=null)for(var p:body)skin+=clouds.concentration(level,p,gas)/body.size();concentrations.put(GasRoute.SKIN,skin);
            for(var route:GasRoute.values()) {
                double raw=concentrations.get(route);
                boolean meaningful=gas.effects().stream().anyMatch(e->e.route()==route&&raw>0&&raw>=e.threshold())
                        ||gas.damage().stream().anyMatch(d->d.route()==route&&raw>0&&raw>=d.threshold());
                concentrations.put(route,raw*GasExposure.protection(entity,gas,route,meaningful));
            }
            for(var rule:gas.damage()) {
                double c=concentrations.get(rule.route());if(c>0&&c>=rule.threshold())damage+=rule.perSecond()*(rule.excess()?Math.max(0,c-rule.threshold()):1);
            }
            exposures.add(new Exposure(gas,concentrations));
        }
        double oxygenDeficit=oxygenApplies?GasOxygen.deficit(gasAmount,oxygenAmount):0;
        damage+=GasOxygen.damage(oxygenDeficit);
        // One hit preserves additive mixture damage through vanilla invulnerability frames.
        if(damage>0)entity.hurt(entity.damageSources().magic(),(float)Math.min(1000,damage));
        if(!entity.isAlive())return;
        GasOxygen.symptoms(entity,oxygenDeficit);
        var results=new LinkedHashMap<ResourceLocation,Result>();
        for(var exposure:exposures)for(var rule:exposure.gas.effects()) {
            var effect=BuiltInRegistries.MOB_EFFECT.getHolder(rule.effect()).orElse(null);if(effect==null)continue;
            double c=exposure.concentrations.get(rule.route());int amplifier=-1;
            if(rule.dose()==null) {if(c>0&&c>=rule.threshold())amplifier=rule.level()-1;}
            else {
                String key=exposure.gas.id()+"|"+rule.id();var saved=data.getCompound(key);double dose=saved.getDouble("amount");
                if(!Double.isFinite(dose)||dose<0)dose=0;
                dose=Math.clamp(dose+(c>0&&c>=rule.threshold()?c*rule.dose().gain():-rule.dose().recovery()),0,rule.dose().thresholds().getLast()*2);
                for(int i=0;i<rule.dose().thresholds().size();i++)if(dose>=rule.dose().thresholds().get(i))amplifier=i;
                if(dose==0)data.remove(key);
                else if(data.contains(key)||data.size()<MAX_DOSES){saved.putDouble("amount",dose);data.put(key,saved);}
                else continue;
            }
            if(amplifier>=0) {
                var candidate=new Result(effect,amplifier,rule.duration(),rule.dose()!=null);
                results.merge(rule.effect(),candidate,(a,b)->a.amplifier>b.amplifier?a:a.amplifier<b.amplifier?b:a.duration>=b.duration?a:b);
            }
        }
        var owners=entity.getPersistentData().getCompound(OWNERS);
        for(var id:new ArrayList<>(owners.getAllKeys()))if(!results.containsKey(ResourceLocation.tryParse(id)))owners.remove(id);
        for(var entry:results.entrySet()) {
            var result=entry.getValue();var current=entity.getEffect(result.effect);String id=entry.getKey().toString();
            var owner=owners.getCompound(id);
            boolean owned=current!=null&&owner.contains("level")&&current.getAmplifier()==owner.getInt("level")
                    &&current.getDuration()<=owner.getInt("duration")&&current.isAmbient();
            if(result.dose&&(current==null||owned||(current.getAmplifier()<=result.amplifier&&current.getDuration()<=result.duration))) {
                entity.forceAddEffect(new MobEffectInstance(result.effect,result.duration,result.amplifier,true,false,true),null);
                owner.putInt("level",result.amplifier);owner.putInt("duration",result.duration);owners.put(id,owner);
            }else if(!result.dose){entity.addEffect(new MobEffectInstance(result.effect,result.duration,result.amplifier,false,false,true));owners.remove(id);}
            else owners.remove(id);
        }
        if(data.isEmpty())entity.getPersistentData().remove(DOSES);else entity.getPersistentData().put(DOSES,data);
        if(owners.isEmpty())entity.getPersistentData().remove(OWNERS);else entity.getPersistentData().put(OWNERS,owners);
    }
    /** Only explicitly configured migration keys are inspected; no chemical identities live here. */
    private static void importLegacy(LivingEntity entity,CompoundTag data) {
        var persistent=entity.getPersistentData();var owners=persistent.getCompound(OWNERS);
        for(var migration:GasProfiles.legacyDoses()) {
            var rule=migration.effect();var dose=rule.dose();String key=migration.gas().id()+"|"+rule.id();
            if(persistent.contains(dose.legacyKey())) {
                double amount=persistent.getDouble(dose.legacyKey());
                if(Double.isFinite(amount)&&amount>0&&(data.contains(key)||data.size()<MAX_DOSES)) {
                    var saved=data.getCompound(key);double existing=saved.getDouble("amount");
                    saved.putDouble("amount",Math.max(Double.isFinite(existing)?existing:0,Math.min(amount,dose.thresholds().getLast()*2)));data.put(key,saved);
                }
                if(dose.legacyOwnerKey()!=null&&persistent.contains(dose.legacyOwnerKey())) {
                    var holder=BuiltInRegistries.MOB_EFFECT.getHolder(rule.effect()).orElse(null);
                    var current=holder==null?null:entity.getEffect(holder);
                    if(current!=null&&current.getAmplifier()==persistent.getInt(dose.legacyOwnerKey())
                            &&current.getDuration()<=rule.duration()&&current.isAmbient()&&!owners.contains(rule.effect().toString())) {
                        var owner=new CompoundTag();owner.putInt("level",current.getAmplifier());owner.putInt("duration",rule.duration());
                        owners.put(rule.effect().toString(),owner);
                    }
                }
            }
            clearLegacy(entity,rule);
        }
        if(!owners.isEmpty())persistent.put(OWNERS,owners);
    }
    private static void clearLegacy(LivingEntity entity,GasProfile.Effect rule) {
        var dose=rule.dose();
        if(dose!=null) {
            if(dose.legacyKey()!=null)entity.getPersistentData().remove(dose.legacyKey());
            if(dose.legacyOwnerKey()!=null)entity.getPersistentData().remove(dose.legacyOwnerKey());
        }
    }
    public static void releaseOwnership(LivingEntity entity,ResourceLocation effect){entity.getPersistentData().getCompound(OWNERS).remove(effect.toString());}
    public static void cure(LivingEntity entity){
        entity.getPersistentData().remove(DOSES);entity.getPersistentData().remove(OWNERS);
        for(var migration:GasProfiles.legacyDoses())clearLegacy(entity,migration.effect());
    }
    public static void cure(LivingEntity entity,ResourceLocation effect) {
        var data=entity.getPersistentData().getCompound(DOSES);
        for(var gas:GasProfiles.values())for(var rule:gas.effects())if(rule.dose()!=null&&rule.effect().equals(effect)) {
            data.remove(gas.id()+"|"+rule.id());clearLegacy(entity,rule);
        }
        releaseOwnership(entity,effect);if(data.isEmpty())entity.getPersistentData().remove(DOSES);
    }
    private GasRuleExposure(){}
}
