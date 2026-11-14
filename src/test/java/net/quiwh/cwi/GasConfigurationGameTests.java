package net.quiwh.cwi;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.quiwh.cwi.pollution.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.Map;

@GameTestHolder("cwi")
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class GasConfigurationGameTests {
    @GameTest(template="machine_empty",templateNamespace="cwi")
    public static void scriptedGasProfileAndSparseSave(GameTestHelper h) {
        var fluid=BuiltInRegistries.FLUID.get(ResourceLocation.parse("kubejs:contract_gas"));
        var gas=GasProfiles.fromFluid(fluid);
        h.assertTrue(gas!=null,"startup fluid builder registers a gas");
        h.assertTrue(gas.color()==0x3366CC && gas.halfLifeTicks()==80 && gas.buoyancy()==-.5,"settings and fluid tint survive registration");
        h.assertTrue(fluid.defaultFluidState().createLegacyBlock().isAir(),"gas has no placeable fluid block");
        var clouds=new GasClouds();var pos=h.absolutePos(new BlockPos(2,2,2));long now=h.getLevel().getGameTime();
        h.assertTrue(clouds.emit(h.getLevel(),pos,gas,800,false)==800,"custom gas accepted");
        var loaded=GasClouds.load(clouds.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(Math.abs(loaded.amount(pos,gas,now+80)-400)<1e-8,"custom profile quantity and decay survive save");h.succeed();
    }
    @GameTest(template="machine_empty",templateNamespace="cwi")
    public static void gasBuilderValidatesAndDefaults(GameTestHelper h) {
        var b=new GasProfile.Builder("cwi:validation");
        boolean rejected=false;try{b.halfLife(1.5);}catch(IllegalArgumentException e){rejected=true;}
        h.assertTrue(rejected,"half life requires whole ticks");
        rejected=false;try{b.effect("minecraft:weakness",Map.of("route","ears"));}catch(IllegalArgumentException e){rejected=true;}
        h.assertTrue(rejected,"unknown route rejected");
        b.route("skin",r->r.protectedBy(java.util.List.of("cwi:chemical_sealing","cwi:heat_shielding")));
        var p=b.build();h.assertTrue(p.routes().get(GasRoute.SKIN).slots().size()==4,"all four armor slots default");
        h.assertTrue(p.routes().get(GasRoute.SKIN).enchantments().size()==2,"list of protections retained");
        h.assertTrue(p.effects().isEmpty()&&p.damage().isEmpty(),"new gas has no implicit harmful effects");h.succeed();
    }
    @GameTest(template="machine_empty",templateNamespace="cwi")
    public static void gasProtectionCombinesPiecesAndEnchantments(GameTestHelper h) {
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var helmet=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET);
        helmet.enchant(ProtectiveEnchantmentGameTests.enchantment(h,"chemical_sealing"),1);
        helmet.enchant(ProtectiveEnchantmentGameTests.enchantment(h,"heat_shielding"),1);
        var boots=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_BOOTS);
        boots.enchant(ProtectiveEnchantmentGameTests.enchantment(h,"chemical_sealing"),2);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,helmet);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET,boots);
        var gas=GasProfiles.fromFluid(BuiltInRegistries.FLUID.get(ResourceLocation.parse("kubejs:contract_gas")));
        float remaining=net.quiwh.cwi.equipment.protection.HazardProtection.gasRemaining(player,h.getLevel().getGameTime(),gas.routes().get(GasRoute.SKIN),true);
        h.assertTrue(remaining==0,"two matching enchantments and available armor pieces combine without requiring chest or legs");
        h.assertTrue(helmet.getDamageValue()==1&&boots.getDamageValue()==1,"each contributing piece wears once per pulse, regardless of matching enchantment count");
        h.succeed();
    }
    @GameTest(template="machine_empty",templateNamespace="cwi")
    public static void customGasEffectUsesSkinAndProtectionBeforeThreshold(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(3,3,3));var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        var gas=GasProfiles.fromFluid(BuiltInRegistries.FLUID.get(ResourceLocation.parse("kubejs:contract_gas")));
        var clouds=new GasClouds();clouds.emit(h.getLevel(),pos,gas,150,false);clouds.emit(h.getLevel(),pos.above(),gas,150,false);
        var helmet=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET);
        helmet.enchant(ProtectiveEnchantmentGameTests.enchantment(h,"chemical_sealing"),2);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,helmet);
        GasRuleExposure.tick(player,clouds);
        h.assertTrue(!player.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS),"half protection reduces concentration below threshold");
        var uncovered=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);uncovered.setPos(player.position());
        GasRuleExposure.tick(uncovered,clouds);
        h.assertTrue(uncovered.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS),"skin rule applies at the body samples");h.succeed();
    }

    @GameTest(template="machine_empty",templateNamespace="cwi")
    public static void gasModificationEventAndWorldPlacement(GameTestHelper h) {
        var repeated=new java.util.concurrent.atomic.AtomicInteger();
        net.quiwh.cwi.compat.CwiKubeJSEvents.GASES.listenJava(dev.latvian.mods.kubejs.script.ScriptType.STARTUP,null,event->{repeated.incrementAndGet();return null;});
        net.quiwh.cwi.compat.GasConfigurationEvent.postStartup();
        h.assertTrue(repeated.get()==0,"gas startup event posts once even when KubeJS creates additional script managers");
        var fluid=BuiltInRegistries.FLUID.get(ResourceLocation.parse("kubejs:contract_existing_gas"));
        var gas=GasProfiles.fromFluid(fluid);h.assertTrue(gas!=null&&gas.halfLifeTicks()==160&&gas.color()==0x998877,"startup event configures an existing fluid once");
        var pos=h.absolutePos(new BlockPos(3,3,3));
        var stack=new net.neoforged.neoforge.fluids.FluidStack(fluid,1000);
        h.assertTrue(!fluid.getFluidType().canBePlacedInLevel(h.getLevel(),pos,stack),"fluid handlers cannot place gas");
        var bucket=(net.minecraft.world.item.BucketItem)fluid.getBucket();
        h.assertTrue(!bucket.emptyContents(h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL),h.getLevel(),pos,null,new net.minecraft.world.item.ItemStack(bucket)),"bucket cannot place gas");
        h.assertTrue(h.getLevel().getFluidState(pos).isEmpty(),"refused placement creates no fluid state");h.succeed();
    }
    @GameTest(template="machine_empty",templateNamespace="cwi")
    public static void customDoseDamageRecoveryAndCure(GameTestHelper h) {
        var fluid=BuiltInRegistries.FLUID.get(ResourceLocation.parse("kubejs:contract_dose_gas"));var gas=GasProfiles.fromFluid(fluid);
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);var pos=h.absolutePos(new BlockPos(3,3,3));
        player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);var eye=BlockPos.containing(player.getEyePosition());
        var clouds=new GasClouds();clouds.emit(h.getLevel(),eye,gas,2000,false);float before=player.getHealth();
        GasRuleExposure.tick(player,clouds);
        h.assertTrue(Math.abs(before-player.getHealth()-1)<.001,"excess damage scales only the concentration above 1.5");
        h.assertTrue(player.getEffect(GasPollution.NEUROTOXIN).getAmplifier()==1,"the strongest simultaneous dose wins even when a lower rule is evaluated last");
        var data=player.getPersistentData().getCompound("cwi:gas_doses");
        h.assertTrue(data.getCompound(gas.id()+"|dose_test").getDouble("amount")==4,"stable rule ID persists dose");
        GasRuleExposure.tick(player,null);
        h.assertTrue(player.getEffect(GasPollution.NEUROTOXIN).getAmplifier()==0,"owned effect downgrades as dose recovers");
        GasExposure.cure(player);h.assertTrue(!player.getPersistentData().contains("cwi:gas_doses"),"cure clears custom saved dose");h.succeed();
    }

    @GameTest(template="machine_empty",templateNamespace="cwi")
    public static void sparseGasEntryLimitsRejectWithoutConsuming(GameTestHelper h) {
        var profiles=GasProfiles.values().stream().toList();var tag=new net.minecraft.nbt.CompoundTag();var cells=new net.minecraft.nbt.ListTag();
        int remaining=GasClouds.MAX_ENTRIES,index=0;
        while(remaining>0) {
            var cell=new net.minecraft.nbt.CompoundTag();cell.putLong("pos",new BlockPos(100000+index++,64,100000).asLong());
            cell.putLong("tick",h.getLevel().getGameTime());var mixture=new net.minecraft.nbt.CompoundTag();
            for(var gas:profiles) {mixture.putDouble(gas.id(),100);if(--remaining==0)break;}
            cell.put("gases",mixture);cells.add(cell);
        }
        tag.putInt("version",2);tag.put("cells",cells);var full=GasClouds.load(tag,h.getLevel().registryAccess());
        var pos=h.absolutePos(new BlockPos(2,2,2));var gas=profiles.getFirst();
        h.assertTrue(full.emit(h.getLevel(),pos,gas,100,true)==0&&full.emit(h.getLevel(),pos,gas,100,false)==0,"entry cap refuses both simulated and committed discharge");
        h.assertTrue(full.amount(pos,gas,h.getLevel().getGameTime())==0,"entry saturation never consumes unrecorded gas");
        var mixture=new GasClouds();
        for(int n=0;n<GasClouds.MAX_GASES_PER_CELL;n++)h.assertTrue(mixture.emit(h.getLevel(),pos,new GasProfile.Builder("cwi:entry_limit_"+n).build(),1,false)==1,"mixture accepts bounded distinct entries");
        h.assertTrue(mixture.emit(h.getLevel(),pos,new GasProfile.Builder("cwi:entry_limit_extra").build(),1,false)==0,"per-cell mixture limit refuses new types");h.succeed();
    }

    @GameTest(template="machine_empty",templateNamespace="cwi")
    public static void mixtureDamageAddsBeforeNativeInvulnerabilityFrames(GameTestHelper h) {
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);var pos=h.absolutePos(new BlockPos(3,3,3));player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        var eye=BlockPos.containing(player.getEyePosition());var clouds=new GasClouds();
        var gas=GasProfiles.fromFluid(BuiltInRegistries.FLUID.get(ResourceLocation.parse("kubejs:contract_dose_gas")));
        clouds.emit(h.getLevel(),eye,gas,2000,false);float before=player.getHealth();
        var second=new GasProfile.Builder("cwi:damage_fixture").oxygenFraction(.2095)
                .damage(Map.of("threshold",.1,"damagePerSecond",2)).build();
        clouds.emit(h.getLevel(),eye,second,200,false);GasRuleExposure.tick(player,clouds);
        h.assertTrue(Math.abs(before-player.getHealth()-3)<.001,"independent scripted damage rules form one additive hit");h.succeed();
    }

}
