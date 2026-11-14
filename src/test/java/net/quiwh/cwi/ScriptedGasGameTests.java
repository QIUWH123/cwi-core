package net.quiwh.cwi;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.ServerLevelData;
import net.quiwh.cwi.pollution.*;

@net.neoforged.neoforge.gametest.GameTestHolder("cwi")
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class ScriptedGasGameTests {
    @GameTest(template="machine_empty",templateNamespace="cwi")
    public static void profilesWithoutRulesNeverGainChemicalHazards(GameTestHelper h) throws Exception {
        var field=GasProfiles.class.getDeclaredField("profiles");field.setAccessible(true);
        @SuppressWarnings("unchecked") var profiles=(Map<String,GasProfile>)field.get(null);
        var level=h.getLevel();var originalClouds=GasClouds.get(level);long old=level.getGameTime();
        try {
            for(var name:java.util.List.of("chlorine","ammonia","hydrogen_sulfide","carbon_dioxide")) {
                String id="kubejs:"+name;var previous=profiles.get(id);
                var gas=new GasProfile.Builder(id).oxygenFraction(.2095).build();
                profiles.put(id,gas);
                try {
                    ((ServerLevelData)level.getLevelData()).setGameTime((old/20+1)*20);
                    var player=h.makeMockPlayer(GameType.SURVIVAL);var pos=h.absolutePos(new BlockPos(3,3,3));
                    player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
                    var clouds=new GasClouds();level.getDataStorage().set("cwi_gas_pollution",clouds);
                    clouds.emit(level,BlockPos.containing(player.getEyePosition()),gas,2000,false);
                    GasExposure.tick(player);
                    h.assertTrue(player.getHealth()==player.getMaxHealth()&&!player.hasEffect(MobEffects.BLINDNESS)
                            &&!player.hasEffect(MobEffects.WEAKNESS)&&!player.hasEffect(GasPollution.NEUROTOXIN),
                            "a chemical name must not create hazards absent from its script rules: "+name);
                    h.assertTrue(!player.getPersistentData().contains("cwi:airborne_neurotoxin"),"no implicit native dose");
                } finally {if(previous==null)profiles.remove(id);else profiles.put(id,previous);}
            }
        } finally {level.getDataStorage().set("cwi_gas_pollution",originalClouds);((ServerLevelData)level.getLevelData()).setGameTime(old);}
        h.succeed();
    }

    @GameTest(template="machine_empty",templateNamespace="cwi")
    public static void legacyDoseMigratesWithoutCloudsAndKeepsOwnership(GameTestHelper h) {
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        player.getPersistentData().putDouble("cwi:airborne_neurotoxin",100);
        player.getPersistentData().putInt("cwi:airborne_neurotoxin_level",3);
        player.addEffect(new MobEffectInstance(GasPollution.NEUROTOXIN,40,3,true,false,true));
        GasRuleExposure.tick(player,null);
        var saved=player.getPersistentData().getCompound("cwi:gas_doses");
        h.assertTrue(saved.getCompound("kubejs:hydrogen_sulfide|neurotoxin_dose").getDouble("amount")==99,
                "legacy dose imports and recovers once even outside all clouds");
        h.assertTrue(!player.getPersistentData().contains("cwi:airborne_neurotoxin")
                &&!player.getPersistentData().contains("cwi:airborne_neurotoxin_level"),"legacy keys are consumed once");
        h.assertTrue(player.getEffect(GasPollution.NEUROTOXIN).getAmplifier()==2,"imported ownership permits normal dose downgrade");
        GasRuleExposure.tick(player,null);
        h.assertTrue(player.getPersistentData().getCompound("cwi:gas_doses")
                .getCompound("kubejs:hydrogen_sulfide|neurotoxin_dose").getDouble("amount")==98,"migration never reimports old dose");
        h.succeed();
    }

    @GameTest(template="machine_empty",templateNamespace="cwi")
    public static void scriptAliasesPreserveBothCloudSaveVersions(GameTestHelper h) {
        var gas=GasProfiles.byId("kubejs:chlorine");
        h.assertTrue(gas==GasProfiles.byId("cwi:chlorine")&&gas==GasProfiles.byId("chlorine"),"explicit aliases share the canonical profile");
        for(int version:new int[]{1,2}) {
            var cell=new net.minecraft.nbt.CompoundTag();var pos=h.absolutePos(new BlockPos(3,3,3));
            cell.putLong("pos",pos.asLong());cell.putLong("tick",h.getLevel().getGameTime());
            if(version==1)cell.putDouble("chlorine",250);
            else {var mixture=new net.minecraft.nbt.CompoundTag();mixture.putDouble("cwi:chlorine",250);cell.put("gases",mixture);}
            var list=new net.minecraft.nbt.ListTag();list.add(cell);var tag=new net.minecraft.nbt.CompoundTag();tag.putInt("version",version);tag.put("cells",list);
            var clouds=GasClouds.load(tag,h.getLevel().registryAccess());
            h.assertTrue(clouds.amount(pos,gas,h.getLevel().getGameTime())==250,"old cloud amount survives: version "+version);
            var saved=clouds.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess());
            h.assertTrue(saved.getList("cells",10).getCompound(0).getCompound("gases").getDouble(gas.id())==250,"resave canonicalizes the alias");
        }
        var mixture=new net.minecraft.nbt.CompoundTag();mixture.putDouble("cwi:chlorine",200);mixture.putDouble("kubejs:chlorine",300);
        var cell=new net.minecraft.nbt.CompoundTag();var pos=h.absolutePos(new BlockPos(3,3,3));
        cell.putLong("pos",pos.asLong());cell.putLong("tick",h.getLevel().getGameTime());cell.put("gases",mixture);
        var list=new net.minecraft.nbt.ListTag();list.add(cell);var tag=new net.minecraft.nbt.CompoundTag();tag.putInt("version",2);tag.put("cells",list);
        h.assertTrue(GasClouds.load(tag,h.getLevel().registryAccess()).amount(pos,gas,h.getLevel().getGameTime())==500,
                "canonical and alias quantities merge without losing either saved amount");
        h.succeed();
    }

    @GameTest(template="machine_empty",templateNamespace="cwi")
    public static void migratedDosePreservesCommandEffectsAndCures(GameTestHelper h) {
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        player.getPersistentData().putDouble("cwi:airborne_neurotoxin",100);
        player.getPersistentData().putInt("cwi:airborne_neurotoxin_level",3);
        player.addEffect(new MobEffectInstance(GasPollution.NEUROTOXIN,1200,3));
        GasRuleExposure.tick(player,null);
        h.assertTrue(player.getEffect(GasPollution.NEUROTOXIN).getDuration()==1200,"migration cannot take ownership of a command effect");
        player.removeEffect(GasPollution.NEUROTOXIN);
        h.assertTrue(!player.getPersistentData().contains("cwi:gas_doses"),"explicit effect removal clears migrated dose");
        player.getPersistentData().putDouble("cwi:airborne_neurotoxin",80);
        player.removeEffect(GasPollution.NEUROTOXIN);
        GasExposure.cure(player);
        GasRuleExposure.tick(player,null);
        h.assertTrue(!player.getPersistentData().contains("cwi:gas_doses")&&!player.hasEffect(GasPollution.NEUROTOXIN),"cure cannot reimport legacy dose");
        h.succeed();
    }

    @GameTest(template="machine_empty",templateNamespace="cwi")
    public static void scriptedCarbonDioxidePreservesExistingRules(GameTestHelper h) {
        var level=h.getLevel();var original=GasClouds.get(level);long old=level.getGameTime();
        try {
            ((ServerLevelData)level.getLevelData()).setGameTime((old/20+1)*20);
            var player=h.makeMockPlayer(GameType.SURVIVAL);var pos=h.absolutePos(new BlockPos(3,3,3));
            player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
            var gas=GasProfiles.fromFluid(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(
                    net.minecraft.resources.ResourceLocation.parse("kubejs:carbon_dioxide")));
            var clouds=new GasClouds();level.getDataStorage().set("cwi_gas_pollution",clouds);
            clouds.emit(level,BlockPos.containing(player.getEyePosition()),gas,2000,false);
            GasExposure.tick(player);
            h.assertTrue(Math.abs(player.getMaxHealth()-player.getHealth()-2)<.0001,
                    "scripted CO2 retains its former damage alongside shared oxygen");
        } finally {level.getDataStorage().set("cwi_gas_pollution",original);((ServerLevelData)level.getLevelData()).setGameTime(old);}
        h.succeed();
    }
}
