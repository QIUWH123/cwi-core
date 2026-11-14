package net.quiwh.cwi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.gametest.*;
import net.quiwh.cwi.pollution.*;

@GameTestHolder("cwi")
@PrefixGameTestTemplate(false)
public final class GasOxygenTransportGameTests {
    private static net.minecraft.world.entity.player.Player player(GameTestHelper h, BlockPos pos) {
        var p=h.makeMockPlayer(GameType.SURVIVAL);p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);return p;
    }
    @GameTest(template="machine_empty",templateNamespace="cwi")
    public static void mixturesShareOxygenAndFiltersCannotSupplyIt(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(3,3,3));var p=player(h,pos);
        var eye=BlockPos.containing(p.getEyePosition());var clouds=new GasClouds();
        var a=new GasProfile.Builder("cwi:inert_a").build();var b=new GasProfile.Builder("cwi:inert_b").build();
        clouds.emit(h.getLevel(),eye,a,900,false);
        GasRuleExposure.tick(p,clouds);
        h.assertTrue(p.getHealth()==p.getMaxHealth(),"one moderate inert concentration does not cause severe oxygen damage");
        var helmet=new ItemStack(Items.DIAMOND_HELMET);
        helmet.enchant(ProtectiveEnchantmentGameTests.enchantment(h,"gas_filtration"),4);
        p.setItemSlot(EquipmentSlot.HEAD,helmet);p.invulnerableTime=0;
        clouds.emit(h.getLevel(),eye,b,900,false);GasRuleExposure.tick(p,clouds);
        h.assertTrue(p.getHealth()<p.getMaxHealth(),"two individually non-damaging gases jointly displace oxygen");
        h.assertTrue(helmet.getDamageValue()==0,"oxygen shortage neither uses nor wears an ordinary gas filter");
        h.succeed();
    }
    @GameTest(template="machine_empty",templateNamespace="cwi")
    public static void oxygenAndAirSupplyRestoreSharedAvailability(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(3,3,3));var eye=BlockPos.containing(player(h,pos).getEyePosition());
        var inert=new GasProfile.Builder("cwi:inert_supply").build();
        for(var id:java.util.List.of("kubejs:oxygen","kubejs:air")) {
            var gas=GasProfiles.fromFluid(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(net.minecraft.resources.ResourceLocation.parse(id)));
            h.assertTrue(gas!=null,"supply gas registered through KubeJS");
            var clouds=new GasClouds();clouds.emit(h.getLevel(),eye,inert,4000,false);clouds.emit(h.getLevel(),eye,gas,20000,false);
            var supplied=player(h,pos);GasRuleExposure.tick(supplied,clouds);
            h.assertTrue(supplied.getHealth()==supplied.getMaxHealth()&&!supplied.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS),"oxygen-bearing supply restores breathable mixture: "+id);
        }
        var clean=player(h,pos);GasRuleExposure.tick(clean,null);
        h.assertTrue(clean.getHealth()==clean.getMaxHealth()&&!clean.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS),"ordinary ambient air is safe without cloud data");
        h.succeed();
    }
    @GameTest(template="machine_empty",templateNamespace="cwi")
    public static void oxygenDoesNotCancelToxinsAndCompositionIsValidated(GameTestHelper h) {
        var b=new GasProfile.Builder("cwi:composition_validation");
        for(double value:new double[]{-.01,1.01,Double.NaN,Double.POSITIVE_INFINITY}) {
            boolean rejected=false;try{b.oxygenFraction(value);}catch(IllegalArgumentException e){rejected=true;}
            h.assertTrue(rejected,"oxygen fraction rejects invalid compositions");
        }
        h.assertTrue(b.build().oxygenFraction()==0,"unspecified gas contains no oxygen");
        var oxygen=GasProfiles.byId("kubejs:oxygen");var air=GasProfiles.byId("kubejs:air");
        h.assertTrue(oxygen.oxygenFraction()==1&&air.oxygenFraction()==.2095&&oxygen.color()==0xFFFFFF,"real startup callbacks retain compositions and untinted gas defaults");
        var pos=h.absolutePos(new BlockPos(3,3,3));var p=player(h,pos);var eye=BlockPos.containing(p.getEyePosition());
        var clouds=new GasClouds();clouds.emit(h.getLevel(),eye,GasProfiles.byId("kubejs:chlorine"),2000,false);clouds.emit(h.getLevel(),eye,oxygen,10000,false);
        float before=p.getHealth();GasRuleExposure.tick(p,clouds);
        h.assertTrue(before-p.getHealth()==4&&!p.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS),"oxygen prevents deprivation without canceling toxin damage");
        var mob=h.spawn(net.minecraft.world.entity.EntityType.COW,new BlockPos(3,3,3));
        var inert=new GasProfile.Builder("cwi:living_inert").targets("living").build();
        var mobClouds=new GasClouds();mobClouds.emit(h.getLevel(),BlockPos.containing(mob.getEyePosition()),inert,2000,false);
        float health=mob.getHealth();GasRuleExposure.tick(mob,mobClouds);
        h.assertTrue(mob.getHealth()<health,"living-target gas participates in shared oxygen exposure for mobs");
        var rescued=h.spawn(net.minecraft.world.entity.EntityType.COW,new BlockPos(3,3,3));
        mobClouds.emit(h.getLevel(),BlockPos.containing(rescued.getEyePosition()),oxygen,10000,false);GasRuleExposure.tick(rescued,mobClouds);
        h.assertTrue(rescued.getHealth()==rescued.getMaxHealth(),"supply composition still contributes when supply profile targets players");
        var unaffected=h.spawn(net.minecraft.world.entity.EntityType.COW,new BlockPos(3,3,3));var playersOnly=new GasClouds();
        playersOnly.emit(h.getLevel(),BlockPos.containing(unaffected.getEyePosition()),new GasProfile.Builder("cwi:players_only").build(),2000,false);
        GasRuleExposure.tick(unaffected,playersOnly);
        h.assertTrue(unaffected.getHealth()==unaffected.getMaxHealth(),"players-only clouds do not opt mobs into oxygen exposure");
        h.succeed();
    }
    @GameTest(template="machine_empty",templateNamespace="cwi")
    public static void unloadedChunkTimeIsNotMovementTime(GameTestHelper h) {
        var level=h.getLevel();long old=level.getGameTime();var pos=h.absolutePos(new BlockPos(3,4,3));
        for(var d:Direction.values())level.setBlockAndUpdate(pos.relative(d),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());var existing=GasClouds.get(level);var clouds=new GasClouds();
        level.getDataStorage().set("cwi_gas_pollution",clouds);
        try {
            ((ServerLevelData)level.getLevelData()).setGameTime(1000);clouds.emit(level,pos,GasProfiles.byId("kubejs:chlorine"),1000,false);clouds.tick(level);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ChunkEvent.Unload(level.getChunkAt(pos)));
            ((ServerLevelData)level.getLevelData()).setGameTime(1100);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ChunkEvent.Load(level.getChunkAt(pos),false));
            level.setBlockAndUpdate(pos.east(),Blocks.AIR.defaultBlockState());
            ((ServerLevelData)level.getLevelData()).setGameTime(1110);clouds.tick(level);
            double total=GasProfiles.byId("kubejs:chlorine").decay(1000,110);
            h.assertTrue(Math.abs(clouds.amount(pos.east(),GasProfiles.byId("kubejs:chlorine"),1110)-total*.125)<1e-7,"resumed chunk spreads for ten loaded ticks, never the unloaded interval");
        } finally {level.getDataStorage().set("cwi_gas_pollution",existing);((ServerLevelData)level.getLevelData()).setGameTime(old);}
        h.succeed();
    }
    @GameTest(template="machine_empty",templateNamespace="cwi")
    public static void savedMovementResumesWithoutOfflineCatchupAndCannotOvershoot(GameTestHelper h) {
        var level=h.getLevel();long old=level.getGameTime();var pos=h.absolutePos(new BlockPos(3,4,3));
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        for(var d:Direction.values())level.setBlockAndUpdate(pos.relative(d),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos.east(),Blocks.AIR.defaultBlockState());
        for(var d:Direction.values())if(!pos.east().relative(d).equals(pos))level.setBlockAndUpdate(pos.east().relative(d),Blocks.STONE.defaultBlockState());
        var tag=new CompoundTag();var cell=new CompoundTag();cell.putLong("pos",pos.asLong());cell.putLong("tick",1000);cell.putDouble("chlorine",1000);
        var list=new ListTag();list.add(cell);tag.put("cells",list);var clouds=GasClouds.load(tag,level.registryAccess());
        try {
            ((ServerLevelData)level.getLevelData()).setGameTime(1100);clouds.tick(level);
            double total=GasProfiles.byId("kubejs:chlorine").decay(1000,100);
            h.assertTrue(Math.abs(clouds.amount(pos.east(),GasProfiles.byId("kubejs:chlorine"),1100)-total*.125)<1e-7,"old saved tick affects decay, not offline movement");
            ((ServerLevelData)level.getLevelData()).setGameTime(2100);clouds.tick(level);
            double left=clouds.amount(pos,GasProfiles.byId("kubejs:chlorine"),2100),right=clouds.amount(pos.east(),GasProfiles.byId("kubejs:chlorine"),2100);
            h.assertTrue(left>=right&&right>=0,"large delayed transfer cannot invert a pair's concentration gradient");
            h.assertTrue(Math.abs(left+right-GasProfiles.byId("kubejs:chlorine").decay(1000,1100))<1e-7,"long catch-up conserves gas without negatives");
        } finally {((ServerLevelData)level.getLevelData()).setGameTime(old);}
        h.succeed();
    }
    @GameTest(template="machine_empty",templateNamespace="cwi")
    public static void queuedTransportRetainsElapsedTimeAndConservesQuantity(GameTestHelper h) {
        var level=h.getLevel();long old=level.getGameTime();var pos=h.absolutePos(new BlockPos(3,4,3));
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        for(var d:Direction.values())level.setBlockAndUpdate(pos.relative(d),Blocks.STONE.defaultBlockState());
        var list=new ListTag();
        for(int i=0;i<2049;i++) {
            var cell=new CompoundTag();cell.putLong("pos",(i==0?pos:new BlockPos(100000+i,64,100000)).asLong());
            cell.putLong("tick",1000);cell.putDouble("chlorine",1000);list.add(cell);
        }
        var tag=new CompoundTag();tag.put("cells",list);var clouds=GasClouds.load(tag,level.registryAccess());
        try {
            ((ServerLevelData)level.getLevelData()).setGameTime(1000);clouds.tick(level);
            level.setBlockAndUpdate(pos.east(),Blocks.AIR.defaultBlockState());
            ((ServerLevelData)level.getLevelData()).setGameTime(1020);clouds.tick(level);
            double total=GasProfiles.byId("kubejs:chlorine").decay(1000,20);
            double moved=clouds.amount(pos.east(),GasProfiles.byId("kubejs:chlorine"),1020);
            h.assertTrue(moved>total*.2&&moved<total*.25,"a delayed queue visit retains twenty ticks of movement instead of clamping to ten");
            h.assertTrue(Math.abs(clouds.amount(pos,GasProfiles.byId("kubejs:chlorine"),1020)+moved-total)<1e-7,"catch-up conserves quantity except configured decay");
            var saved=clouds.save(new CompoundTag(),level.registryAccess());clouds.tick(level);
            h.assertTrue(saved.equals(clouds.save(new CompoundTag(),level.registryAccess())),"same-tick call does not repeat catch-up");
        } finally {((ServerLevelData)level.getLevelData()).setGameTime(old);}
        h.succeed();
    }
}
