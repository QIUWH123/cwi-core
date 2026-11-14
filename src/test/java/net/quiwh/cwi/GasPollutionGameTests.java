package net.quiwh.cwi;

import com.simibubi.create.content.fluids.OpenEndedPipe;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.equipment.protection.ProtectiveEnchantments.Type;
import net.quiwh.cwi.pollution.*;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class GasPollutionGameTests {
    private static net.minecraft.world.level.material.Fluid fluid(String id) {
        return BuiltInRegistries.FLUID.get(ResourceLocation.parse(id));
    }
    private static net.neoforged.neoforge.fluids.capability.IFluidHandler pipe(GameTestHelper h,BlockPos pos,Direction direction) {
        var pipe=new OpenEndedPipe(new BlockFace(pos,direction));pipe.manageSource(h.getLevel(),null);
        return pipe.provideHandler().getCapability();
    }
    private static double total(GasClouds clouds,GameTestHelper h,GasProfile gas) {
        double sum=0;
        for(var value:clouds.save(new CompoundTag(),h.getLevel().registryAccess()).getList("cells",Tag.TAG_COMPOUND))
            sum+=((CompoundTag)value).getCompound("gases").getDouble(gas.id());
        return sum;
    }
    private static net.minecraft.world.entity.player.Player player(GameTestHelper h,BlockPos pos) {
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        return player;
    }
    private static void pulse(net.minecraft.world.entity.player.Player player) {
        var level=(net.minecraft.server.level.ServerLevel)player.level();long old=level.getGameTime();
        long next=Math.max((old/20+1)*20,player.getPersistentData().getLong("cwi:test_gas_tick")+20);
        player.getPersistentData().putLong("cwi:test_gas_tick",next);
        try { ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(next);NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player)); }
        finally { ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(old); }
    }
    private static double dose(net.minecraft.world.entity.player.Player player) {
        return Math.max(player.getPersistentData().getDouble("cwi:airborne_neurotoxin"),
                player.getPersistentData().getCompound("cwi:gas_doses")
                    .getCompound("kubejs:hydrogen_sulfide|neurotoxin_dose").getDouble("amount"));
    }
    private static void gear(GameTestHelper h,net.minecraft.world.entity.player.Player player,String name,int level) {
        var helmet=new ItemStack(Items.DIAMOND_HELMET);helmet.enchant(ProtectiveEnchantmentGameTests.enchantment(h,name),level);
        player.setItemSlot(EquipmentSlot.HEAD,helmet);
    }
    private static void fillEye(GameTestHelper h,net.minecraft.world.entity.player.Player player,GasProfile gas,int amount) {
        var eye=BlockPos.containing(player.getEyePosition());h.getLevel().setBlockAndUpdate(eye,Blocks.AIR.defaultBlockState());
        h.assertTrue(GasClouds.get(h.getLevel()).emit(h.getLevel(),eye,gas,amount,false)==amount,"fixture emits exact amount");
    }
    static final class ParticleRecipient implements AutoCloseable {
        final net.minecraft.server.level.ServerLevel level;
        final net.minecraft.server.level.ServerPlayer player;
        final java.util.List<net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket> packets=new java.util.ArrayList<>();
        ParticleRecipient(GameTestHelper h,BlockPos pos) {
            level=h.getLevel();
            var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"gas-visual-test"),false);
            player=new net.minecraft.server.level.ServerPlayer(level.getServer(),level,cookie.gameProfile(),cookie.clientInformation());
            new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),
                    new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player,cookie) {
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
                    if(packet instanceof net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket particles)packets.add(particles);
                }
            };
            player.setPos(Vec3.atCenterOf(pos));level.players().add(player);
        }
        @Override public void close() {level.players().remove(player);player.getTextFilter().leave();}
    }
    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID)
    public static void gasVisualsLeaveEveryPipeFaceAndNeverComeFromSimulation(GameTestHelper h) {
        var level=h.getLevel();var source=h.absolutePos(new BlockPos(4,4,4));long old=level.getGameTime();
        var clouds=GasClouds.get(level);
        try(var receiver=new ParticleRecipient(h,source)) {
            long now=(old/20+1)*20+1;
            for(var direction:Direction.values()) {
                level.setBlockAndUpdate(source.relative(direction),Blocks.AIR.defaultBlockState());
                var handler=pipe(h,source,direction);var stack=new FluidStack(fluid("kubejs:chlorine"),500);
                ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(now++);
                receiver.packets.clear();handler.fill(stack,FluidAction.SIMULATE);clouds.tick(level);
                h.assertTrue(receiver.packets.isEmpty(),"simulation never emits visual gas");
                if(now%5==0)now++;
                ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(now++);
                handler.fill(stack,FluidAction.EXECUTE);clouds.tick(level);
                h.assertTrue(!receiver.packets.isEmpty() && receiver.packets.size()<=2,"committed flow has a short bounded stream");
                var mouth=Vec3.atCenterOf(source).add(Vec3.atLowerCornerOf(direction.getNormal()).scale(.515));
                for(var packet:receiver.packets) {
                    var point=new Vec3(packet.getX(),packet.getY(),packet.getZ());
                    var velocity=new Vec3(packet.getXDist(),packet.getYDist(),packet.getZDist());
                    h.assertTrue(point.distanceTo(mouth)<.06,"puffs originate at the mouth, not arbitrary cell positions");
                    double axial=velocity.dot(Vec3.atLowerCornerOf(direction.getNormal()));
                    h.assertTrue(velocity.length()>=.119999 && velocity.length()<=.180001,"initial exit speed is bounded and faster than dispersed gas");
                    h.assertTrue(axial/velocity.length()>=.9659,"exit direction stays inside a 15-degree outward cone");
                    h.assertTrue(packet.getCount()==0 && packet.getMaxSpeed()==1,"native packet retains exact position and velocity");
                }
                int count=receiver.packets.size();clouds.tick(level);
                h.assertTrue(receiver.packets.size()==count,"one world tick cannot duplicate the jet");
                if(now%10==0)now++;
                ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(now++);receiver.packets.clear();clouds.tick(level);
                h.assertTrue(receiver.packets.isEmpty(),"jet stops as soon as actual flow stops");
            }
        } finally {((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(old);}
        h.succeed();
    }
    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID)
    public static void gasStreamsArePacedAndShareOutletsFairly(GameTestHelper h) {
        var level=h.getLevel();var source=h.absolutePos(new BlockPos(4,4,4));long old=level.getGameTime();
        var clouds=new GasClouds();var faces=java.util.EnumSet.noneOf(Direction.class);
        double minSpeed=1,maxSpeed=0,maxAngle=0;
        try(var receiver=new ParticleRecipient(h,source)) {
            long start=(old/20+1)*20;
            for(int tick=0;tick<20;tick++) {
                ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(start+tick);
                for(var direction:Direction.values()) {
                    level.setBlockAndUpdate(source.relative(direction),Blocks.AIR.defaultBlockState());
                    clouds.outlet(level,source,direction,GasProfiles.byId("kubejs:ammonia"),1000);
                }
                int before=receiver.packets.size();clouds.tick(level);
                int sent=receiver.packets.size()-before;
                h.assertTrue(sent>=0 && sent<=1,"outlets are paced to at most one puff per tick");
                for(var packet:receiver.packets.subList(before,receiver.packets.size())) {
                    var delta=new Vec3(packet.getX(),packet.getY(),packet.getZ()).subtract(Vec3.atCenterOf(source));
                    var face=Direction.getNearest(delta.x,delta.y,delta.z);faces.add(face);
                    var velocity=new Vec3(packet.getXDist(),packet.getYDist(),packet.getZDist());
                    minSpeed=Math.min(minSpeed,velocity.length());maxSpeed=Math.max(maxSpeed,velocity.length());
                    double axial=velocity.dot(Vec3.atLowerCornerOf(face.getNormal()));
                    maxAngle=Math.max(maxAngle,Math.acos(Math.clamp(axial/velocity.length(),-1,1)));
                }
            }
            h.assertTrue(receiver.packets.size()==16,"pacing retains the shared 24-puff window cap");
            h.assertTrue(faces.size()==6,"every continuously active pipe face receives visual feedback");
            h.assertTrue(maxSpeed-minSpeed>.02 && maxAngle>.04,"exit speeds and directions vary within the cone");
        } finally {((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(old);}
        h.succeed();
    }
    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID)
    public static void gasCloudPuffsArePacedBetweenSimulationUpdates(GameTestHelper h) {
        var level=h.getLevel();var source=h.absolutePos(new BlockPos(4,4,4));long old=level.getGameTime();
        var clouds=new GasClouds();
        try(var receiver=new ParticleRecipient(h,source)) {
            long start=(old/20+1)*20;
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(start);
            for(int x=1;x<=7;x++)for(int z=1;z<=7;z++) {
                var pos=h.absolutePos(new BlockPos(x,4,z));level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
                clouds.emit(level,pos,GasProfiles.byId("kubejs:chlorine"),1000,false);
            }
            for(int tick=0;tick<20;tick++) {
                ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(start+tick);
                int before=receiver.packets.size();clouds.tick(level);
                int sent=receiver.packets.size()-before;
                h.assertTrue(sent>=0 && sent<=1,"ambient puffs share the one-puff tick cap");
            }
            h.assertTrue(receiver.packets.size()==16,"ambient puffs obey the same shared cap");
        } finally {((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(old);}
        h.succeed();
    }
    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID)
    public static void distantGasStreamsUseFewerParticlesWithoutChangingPollution(GameTestHelper h) {
        var level=h.getLevel();var source=h.absolutePos(new BlockPos(4,4,4));long old=level.getGameTime();
        level.setBlockAndUpdate(source.east(),Blocks.AIR.defaultBlockState());
        var clouds=new GasClouds();
        try(var receiver=new ParticleRecipient(h,source)) {
            long start=(old/10+1)*10+1;int near=0,far=0;
            for(int distance:new int[]{0,23}) {
                receiver.player.setPos(Vec3.atCenterOf(source).add(0,0,distance));receiver.packets.clear();
                for(int tick=0;tick<100;tick++) {
                    ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(start++);
                    clouds.outlet(level,source,Direction.EAST,GasProfiles.byId("kubejs:chlorine"),1000);clouds.tick(level);
                }
                if(distance==0)near=receiver.packets.size();else far=receiver.packets.size();
            }
            h.assertTrue(near==80 && far>0 && far<near*.6,"distant streams retain visible feedback with less packet and fill cost");
            h.assertTrue(clouds.save(new CompoundTag(),level.registryAccess()).getList("cells",Tag.TAG_COMPOUND).isEmpty(),
                    "visual pacing and distance never create or change pollution quantities");
        } finally {((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(old);}
        h.succeed();
    }
    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID)
    public static void gasVisualsShareTheExistingPerPlayerBudget(GameTestHelper h) {
        var level=h.getLevel();var source=h.absolutePos(new BlockPos(4,4,4));long old=level.getGameTime();
        try(var receiver=new ParticleRecipient(h,source)) {
            long start=(old/20+1)*20;
            for(int tick=0;tick<20;tick++) {
                ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(start+tick);
                for(var direction:Direction.values()) {
                    level.setBlockAndUpdate(source.relative(direction),Blocks.AIR.defaultBlockState());
                    pipe(h,source,direction).fill(new FluidStack(fluid("kubejs:ammonia"),1000),FluidAction.EXECUTE);
                }
                GasClouds.get(level).tick(level);
            }
            h.assertTrue(!receiver.packets.isEmpty() && receiver.packets.size()<=16,"many pipes and cloud puffs share 16 packets per player per second");
        } finally {((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(old);}
        h.succeed();
    }
    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID)
    public static void gasVisualDensityUsesAcceptedVolumeAndCoalescesTransfers(GameTestHelper h) {
        var level=h.getLevel();var source=h.absolutePos(new BlockPos(4,4,4));long old=level.getGameTime();
        level.setBlockAndUpdate(source.east(),Blocks.AIR.defaultBlockState());
        var handler=pipe(h,source,Direction.EAST);var clouds=GasClouds.get(level);
        try(var receiver=new ParticleRecipient(h,source)) {
            long now=(old/20+1)*20;
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(now);
            handler.fill(new FluidStack(fluid("kubejs:ammonia"),125),FluidAction.EXECUTE);clouds.tick(level);
            h.assertTrue(receiver.packets.size()==1,"an idle outlet emits immediately even at the window boundary");
            receiver.packets.clear();now+=2;((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(now);
            for(int n=0;n<10;n++)handler.fill(new FluidStack(fluid("kubejs:ammonia"),50),FluidAction.EXECUTE);
            clouds.tick(level);
            h.assertTrue(receiver.packets.size()==1,"greater committed flow is denser while transfers coalesce into a bounded stream");
        } finally {((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(old);}
        h.succeed();
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void neurotoxinAndGasProtectionAreNative(GameTestHelper h) {
        h.assertTrue(BuiltInRegistries.MOB_EFFECT.containsKey(ResourceLocation.parse("cwi:neurotoxin")),"native neurotoxin registered");
        h.assertTrue(Type.GAS_FILTRATION.active && Type.OCULAR_SHIELDING.active,"gas and eye protection now active");
        h.assertTrue(!Type.PARTICULATE_FILTRATION.active && !Type.RADIATION_SHIELDING.active && !Type.PRESSURE_PROTECTION.active,"other reserved protections remain inactive");h.succeed();
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void pipeSimulationAndCommittedAmountsAreIndependent(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(2,2,2));h.getLevel().setBlockAndUpdate(pos.east(),Blocks.AIR.defaultBlockState());
        var clouds=GasClouds.get(h.getLevel());var handler=pipe(h,pos,Direction.EAST);var stack=new FluidStack(fluid("kubejs:chlorine"),375);
        double before=clouds.amount(pos.east(),GasProfiles.byId("kubejs:chlorine"),h.getLevel().getGameTime());
        var snapshot=clouds.save(new CompoundTag(),h.getLevel().registryAccess());
        h.assertTrue(handler.fill(stack,FluidAction.SIMULATE)==375,"simulation accepts 375mB");
        h.assertTrue(snapshot.equals(clouds.save(new CompoundTag(),h.getLevel().registryAccess())),"simulation cannot mutate pollution");
        h.assertTrue(handler.fill(stack,FluidAction.EXECUTE)==375,"commit accepts exact mB");
        h.assertTrue(clouds.amount(pos.east(),GasProfiles.byId("kubejs:chlorine"),h.getLevel().getGameTime())==before+375,"one commit emits once");
        h.assertTrue(handler.fill(new FluidStack(stack.getFluid(),2000),FluidAction.EXECUTE)==1000,"native per-transfer cap remains 1000");
        h.assertTrue(clouds.amount(pos.east(),GasProfiles.byId("kubejs:chlorine"),h.getLevel().getGameTime())==before+1375,"cap is not lost or duplicated");
        h.assertTrue(h.getLevel().getBlockState(pos.east()).isAir(),"gas places no liquid block");h.succeed();
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void blockedWetAndUnloadedOutletsRefuseGas(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(2,2,2));var handler=pipe(h,pos,Direction.EAST);var stack=new FluidStack(fluid("kubejs:chlorine"),100);
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.STONE,Blocks.WATER}) {
            h.getLevel().setBlockAndUpdate(pos.east(),block.defaultBlockState());
            h.assertTrue(handler.fill(stack,FluidAction.SIMULATE)==0 && handler.fill(stack,FluidAction.EXECUTE)==0,"blocked/submerged output retains upstream gas");
        }
        var unloaded=new BlockPos(20_000_000,100,20_000_000);
        h.assertTrue(!h.getLevel().hasChunkAt(unloaded),"far fixture chunk is unloaded");
        h.assertTrue(pipe(h,unloaded,Direction.EAST).fill(stack,FluidAction.EXECUTE)==0 && !h.getLevel().hasChunkAt(unloaded),"venting never force loads");h.succeed();
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void unrelatedFluidsKeepNativeTankAndPlacement(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(2,2,2));h.getLevel().setBlockAndUpdate(pos.east(),Blocks.AIR.defaultBlockState());
        var handler=pipe(h,pos,Direction.EAST);
        // Create's registered water effect uses a 1mB first pulse while the endpoint tank is empty.
        h.assertTrue(handler.fill(new FluidStack(Fluids.WATER,250),FluidAction.SIMULATE)==1,"native initial water-effect simulation");
        h.assertTrue(handler.fill(new FluidStack(Fluids.WATER,250),FluidAction.EXECUTE)==1,"native initial water-effect commit");
        h.assertTrue(handler.fill(new FluidStack(Fluids.WATER,250),FluidAction.EXECUTE)==250,"native pending water now accepts whole amount");
        h.assertTrue(handler.getFluidInTank(0).getAmount()==251,"ordinary pending fluid remains in native tank");h.succeed();
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void allFourSourceAndFlowingIdentitiesResolveWithoutNameGuessing(GameTestHelper h) {
        for(var name:java.util.List.of("chlorine","ammonia","hydrogen_sulfide","carbon_dioxide")) {
            var gas=GasProfiles.byId("kubejs:"+name);
            var source=fluid(gas.id());h.assertTrue(source!=Fluids.EMPTY,"fixture registered");
            h.assertTrue(GasProfiles.fromFluid(source)==gas,"source identity");
            h.assertTrue(GasProfiles.fromFluid(((net.minecraft.world.level.material.FlowingFluid)source).getFlowing())==gas,"flowing identity");
        }
        h.assertTrue(GasProfiles.fromFluid(Fluids.WATER)==null && GasProfiles.fromFluid(Fluids.LAVA)==null,"unrelated fluids never become gas");
        if(net.neoforged.fml.ModList.get().isLoaded("tfmg"))h.assertTrue(GasProfiles.fromFluid(fluid("tfmg:carbon_dioxide"))==GasProfiles.byId("kubejs:carbon_dioxide"),"optional TFMG identity");h.succeed();
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void savedQuantitiesDecayWithoutMovingOrLosingMixtures(GameTestHelper h) {
        var clouds=new GasClouds();var pos=h.absolutePos(new BlockPos(2,2,2));h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        clouds.emit(h.getLevel(),pos,GasProfiles.byId("kubejs:chlorine"),1000,false);clouds.emit(h.getLevel(),pos,GasProfiles.byId("kubejs:ammonia"),500,false);
        long now=h.getLevel().getGameTime();var saved=clouds.save(new CompoundTag(),h.getLevel().registryAccess());
        var loaded=GasClouds.load(saved,h.getLevel().registryAccess());
        h.assertTrue(loaded.amount(pos,GasProfiles.byId("kubejs:chlorine"),now+800)==500,"one chlorine half-life");
        h.assertTrue(Math.abs(loaded.amount(pos,GasProfiles.byId("kubejs:ammonia"),now+800)-125)<1e-8,"mixtures retain independent decay");
        h.assertTrue(loaded.amount(pos.east(),GasProfiles.byId("kubejs:chlorine"),now+800)==0,"loading never catches up movement");h.succeed();
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void geometrySealsWallsDoorsAndSlabFaces(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(2,3,2));
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.STONE.defaultBlockState());
        h.assertTrue(!GasPassages.connects(level,pos,Direction.EAST),"solid wall seals");
        level.setBlockAndUpdate(pos.east(),Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.EAST).setValue(DoorBlock.OPEN,false));
        h.assertTrue(!GasPassages.connects(level,pos,Direction.EAST),"closed door seals");
        level.setBlockAndUpdate(pos.east(),Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.EAST).setValue(DoorBlock.OPEN,true));
        h.assertTrue(GasPassages.connects(level,pos,Direction.EAST),"open door passes");
        level.setBlockAndUpdate(pos,Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.BOTTOM));
        level.setBlockAndUpdate(pos.below(),Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(pos.above(),Blocks.AIR.defaultBlockState());
        h.assertTrue(Math.abs(GasPassages.volume(level,pos)-.5)<1e-8 && !GasPassages.connects(level,pos,Direction.DOWN)
                && GasPassages.connects(level,pos,Direction.UP),"bottom slab preserves half air and seals bottom face");
        h.assertTrue(!GasPassages.containsAir(level,Vec3.atLowerCornerOf(pos).add(.5,.2,.5))
                && GasPassages.containsAir(level,Vec3.atLowerCornerOf(pos).add(.5,.8,.5)),"exposure cannot sample inside solid half");h.succeed();
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void nativeDiffusionConservesAmountsAndRespectsVerticalProfiles(GameTestHelper h) {
        var level=h.getLevel();var center=h.absolutePos(new BlockPos(2,3,2));
        for(var direction:Direction.values())level.setBlockAndUpdate(center.relative(direction),Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(center,Blocks.AIR.defaultBlockState());
        long time=level.getGameTime();
        try {
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(1000);var clouds=new GasClouds();clouds.emit(level,center,GasProfiles.byId("kubejs:ammonia"),1000,false);clouds.emit(level,center,GasProfiles.byId("kubejs:chlorine"),1000,false);
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(1010);clouds.tick(level);
            h.assertTrue(Math.abs(total(clouds,h,GasProfiles.byId("kubejs:ammonia"))-1000*Math.pow(.5,10.0/400))<1e-6,"transport conserves ammonia except configured decay");
            h.assertTrue(clouds.amount(center.above(),GasProfiles.byId("kubejs:ammonia"),1010)>clouds.amount(center.below(),GasProfiles.byId("kubejs:ammonia"),1010),"ammonia rises");
            h.assertTrue(clouds.amount(center.below(),GasProfiles.byId("kubejs:chlorine"),1010)>clouds.amount(center.above(),GasProfiles.byId("kubejs:chlorine"),1010),"chlorine settles");
            var saved=clouds.save(new CompoundTag(),level.registryAccess());clouds.tick(level);
            h.assertTrue(saved.equals(clouds.save(new CompoundTag(),level.registryAccess())),"duplicate tick cannot move twice");
        } finally {((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(time);}h.succeed();
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void gasAndEyeProtectionCoverIndependentChannels(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(2,2,2));var player=player(h,pos);fillEye(h,player,GasProfiles.byId("kubejs:chlorine"),1000);
        gear(h,player,"gas_filtration",4);pulse(player);
        h.assertTrue(player.getHealth()==player.getMaxHealth() && player.hasEffect(MobEffects.BLINDNESS),"filter prevents inhalation but not eye damage");
        h.assertTrue(player.getEffect(MobEffects.BLINDNESS).showIcon() && !player.getEffect(MobEffects.BLINDNESS).isVisible(),"eye irritation has a native HUD icon without swirls");
        player.removeAllEffects();gear(h,player,"ocular_shielding",4);player.invulnerableTime=0;pulse(player);
        h.assertTrue(player.getHealth()<player.getMaxHealth() && !player.hasEffect(MobEffects.BLINDNESS),"goggles do not supply respiratory protection");h.succeed();
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void carbonDioxideBypassesFiltersAndRequiresConcentration(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(2,2,2));var player=player(h,pos);fillEye(h,player,GasProfiles.byId("kubejs:carbon_dioxide"),3000);gear(h,player,"gas_filtration",4);pulse(player);
        h.assertTrue(player.getHealth()<player.getMaxHealth() && player.hasEffect(MobEffects.WEAKNESS),"high CO2 harms despite filtration");
        h.assertTrue(player.getEffect(MobEffects.WEAKNESS).showIcon() && player.getEffect(MobEffects.MOVEMENT_SLOWDOWN).showIcon(),"CO2 symptoms have native HUD icons");
        h.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).getDamageValue()==0,"ineffective filters do not spend wear for CO2");h.succeed();
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void sulfideDoseAccumulatesAndFiltersReduceIt(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(2,2,2));var player=player(h,pos);fillEye(h,player,GasProfiles.byId("kubejs:hydrogen_sulfide"),1000);
        for(int i=0;i<6;i++)pulse(player);
        h.assertTrue(player.hasEffect(GasPollution.NEUROTOXIN),"sustained hydrogen sulfide produces native neurotoxicity");
        h.assertTrue(player.getEffect(GasPollution.NEUROTOXIN).showIcon() && !player.getEffect(GasPollution.NEUROTOXIN).isVisible(),"neurotoxicity has a HUD icon without potion swirls");
        h.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED)<player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED),"native effect slows movement");
        gear(h,player,"gas_filtration",4);double before=dose(player);pulse(player);
        h.assertTrue(dose(player)<before,"full filter permits recovery without instantly curing");h.succeed();
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void neurotoxinCommandsMilkAndEntitySavesUseNativeLifecycle(GameTestHelper h) {
        var player=player(h,h.absolutePos(new BlockPos(2,2,2)));
        double base=player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        player.addEffect(new MobEffectInstance(GasPollution.NEUROTOXIN,200,3));
        h.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED)-base*.6)<1e-7,"level IV is a native 40 percent penalty");
        pulse(player);h.assertTrue(player.hasEffect(GasPollution.NEUROTOXIN),"dose maintenance cannot erase command-applied effects");
        player.getPersistentData().putDouble("cwi:airborne_neurotoxin",32);
        var saved=player.saveWithoutId(new CompoundTag());var loaded=player(h,h.absolutePos(new BlockPos(2,2,2)));loaded.load(saved);
        h.assertTrue(loaded.getPersistentData().getDouble("cwi:airborne_neurotoxin")==32,"dose survives native entity NBT");
        Items.MILK_BUCKET.finishUsingItem(new ItemStack(Items.MILK_BUCKET),h.getLevel(),player);
        h.assertTrue(!player.hasEffect(GasPollution.NEUROTOXIN) && dose(player)==0,"milk cures active neurotoxicity and dose");h.succeed();
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void actualPipeAndPumpOutletsIgnoreTheirOwnCollision(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(2,2,2));
        var states=java.util.List.of(
                com.simibubi.create.AllBlocks.FLUID_PIPE.getDefaultState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST,true)
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST,true),
                com.simibubi.create.AllBlocks.MECHANICAL_PUMP.getDefaultState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.EAST),
                com.simibubi.create.AllBlocks.ENCASED_FLUID_PIPE.getDefaultState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST,true)
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST,true));
        for(var state:states) {
            h.getLevel().setBlockAndUpdate(pos,state);
            h.getLevel().setBlockAndUpdate(pos.east(),Blocks.AIR.defaultBlockState());
            h.assertTrue(pipe(h,pos,Direction.EAST).fill(new FluidStack(fluid("kubejs:ammonia"),250),FluidAction.EXECUTE)==250,
                    "real pipe/pump outlet emits despite its source collision: "+state);
        }
        // The vent starts at the pipe face, so only the receiving face may seal it.
        h.getLevel().setBlockAndUpdate(pos.east(),Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.BOTTOM));
        h.assertTrue(pipe(h,pos,Direction.EAST).fill(new FluidStack(fluid("kubejs:ammonia"),250),FluidAction.EXECUTE)==250,"partial receiving opening accepts gas");
        h.succeed();
    }
    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID,timeoutTicks=180)
    public static void nativePumpedGasLeavesStorageThroughTheOpenEndpoint(GameTestHelper h) {
        BlockPos sourcePos=new BlockPos(2,3,3),pumpPos=new BlockPos(4,3,3),end=new BlockPos(5,3,3);
        h.setBlock(sourcePos,net.quiwh.cwi.machine.MachineBlocks.MIXING_VESSEL.get());
        var pipeBlock=net.neoforged.fml.ModList.get().isLoaded("tfmg")
                ? BuiltInRegistries.BLOCK.get(ResourceLocation.parse("tfmg:steel_pipe"))
                : com.simibubi.create.AllBlocks.FLUID_PIPE.get();
        for(var pos:java.util.List.of(pumpPos.west(),end)) h.setBlock(pos,pipeBlock.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST,true)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST,true));
        h.setBlock(pumpPos,com.simibubi.create.AllBlocks.MECHANICAL_PUMP.getDefaultState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.EAST));
        h.setBlock(pumpPos.below(),com.simibubi.create.AllBlocks.COGWHEEL.getDefaultState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,Direction.Axis.X));
        var motorPos=pumpPos.below().west();h.setBlock(motorPos,com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING,Direction.EAST));
        com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor=h.getBlockEntity(motorPos);
        motor.generatedSpeed.setValue(128);motor.initialize();
        com.simibubi.create.content.kinetics.base.KineticBlockEntity cog=h.getBlockEntity(pumpPos.below());cog.attachKinetics();
        com.simibubi.create.content.fluids.pump.PumpBlockEntity pump=h.getBlockEntity(pumpPos);pump.attachKinetics();
        net.quiwh.cwi.machine.MixingVesselBlockEntity source=h.getBlockEntity(sourcePos);
        source.fluids().fill(new FluidStack(fluid("kubejs:chlorine"),1000),FluidAction.EXECUTE);pump.updatePressureChange();
        h.succeedWhen(()->{
            h.assertTrue(source.getFluid(0).isEmpty(),"actual pump consumes stored gas");
            h.assertTrue(total(GasClouds.get(h.getLevel()),h,GasProfiles.byId("kubejs:chlorine"))>0,"native fluid network commits pollution");
            h.assertTrue(h.getBlockState(end.east()).isAir(),"open outlet never places gas fluid");
        });
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void capacityRefusesNewCellsWithoutDiscardingExistingMixtures(GameTestHelper h) {
        var list=new ListTag();
        for(int i=0;i<GasClouds.MAX_CELLS;i++) {
            var cell=new CompoundTag();cell.putLong("pos",new BlockPos(100000+i,100,100000).asLong());
            cell.putLong("tick",h.getLevel().getGameTime());cell.putDouble("chlorine",1);list.add(cell);
        }
        var tag=new CompoundTag();tag.put("cells",list);var clouds=GasClouds.load(tag,h.getLevel().registryAccess());
        var pos=h.absolutePos(new BlockPos(2,2,2));h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        var before=clouds.save(new CompoundTag(),h.getLevel().registryAccess());
        h.assertTrue(clouds.emit(h.getLevel(),pos,GasProfiles.byId("kubejs:ammonia"),500,true)==0
                && clouds.emit(h.getLevel(),pos,GasProfiles.byId("kubejs:ammonia"),500,false)==0,"capacity blocks simulation and commit");
        h.assertTrue(before.equals(clouds.save(new CompoundTag(),h.getLevel().registryAccess())),"refusal never evicts old gas");h.succeed();
    }
    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID)
    public static void incomingGasKeepsItsStorageReservationWhenTraceDecays(GameTestHelper h) {
        var level=h.getLevel();long old=level.getGameTime(),now=(old/10+1)*10;
        var source=h.absolutePos(new BlockPos(2,3,2));var trace=source.east();
        var other=h.absolutePos(new BlockPos(8,3,8));var list=new ListTag();
        for(var pos:java.util.List.of(source,other)) {
            level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
            for(var face:Direction.values())level.setBlockAndUpdate(pos.relative(face),Blocks.AIR.defaultBlockState());
        }
        // Queue the trace after its sender and before another source. The full store must
        // retain its reserved slot even though its original quantity decays below TRACE.
        for(int i=0;i<GasClouds.MAX_CELLS;i++) {
            var pos=i==0?source:i==1?trace:i==2?other:new BlockPos(100000+i,100,100000);
            var cell=new CompoundTag();cell.putLong("pos",pos.asLong());cell.putLong("tick",now-10);
            cell.putDouble("chlorine",i==1?.01:i<3?1000:1);list.add(cell);
        }
        var tag=new CompoundTag();tag.put("cells",list);var clouds=GasClouds.load(tag,level.registryAccess());
        try {
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(now);clouds.tick(level);
            var saved=clouds.save(new CompoundTag(),level.registryAccess()).getList("cells",Tag.TAG_COMPOUND);
            h.assertTrue(saved.size()==GasClouds.MAX_CELLS,"pending incoming gas must not overfill the hard cell limit");
            h.assertTrue(clouds.amount(trace,GasProfiles.byId("kubejs:chlorine"),now)>0,"pending gas is retained in its existing cell");
            double total=0;
            for(var value:saved) {
                var cell=(CompoundTag)value;
                total+=GasProfiles.byId("kubejs:chlorine").decay(cell.getCompound("gases").getDouble("kubejs:chlorine"),now-cell.getLong("tick"));
            }
            double expected=GasProfiles.byId("kubejs:chlorine").decay(2000+GasClouds.MAX_CELLS-3,10);
            h.assertTrue(Math.abs(total-expected)<1e-6,"capacity accounting conserves gas except the original subtrace amount");
        } finally {((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(old);}
        h.succeed();
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void oneWorldTickCannotDoubleChargeExposure(GameTestHelper h) {
        var player=player(h,h.absolutePos(new BlockPos(2,2,2)));fillEye(h,player,GasProfiles.byId("kubejs:hydrogen_sulfide"),1000);
        var level=h.getLevel();long old=level.getGameTime();
        try {
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime((old/20+1)*20);
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
            double dose=dose(player);
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
            h.assertTrue(dose(player)==dose,"same world tick cannot accumulate dose twice");
        } finally {((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(old);}
        h.succeed();
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void lowDoseMilkRecoveryAndDeathUseNativeLifecycle(GameTestHelper h) throws Exception {
        var player=player(h,h.absolutePos(new BlockPos(2,2,2)));
        player.getPersistentData().putDouble("cwi:airborne_neurotoxin",6);pulse(player);
        h.assertTrue(dose(player)==5 && !player.hasEffect(GasPollution.NEUROTOXIN),"low dose recovers outside cloud");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.MILK_BUCKET));
        player.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        var finish=net.minecraft.world.entity.LivingEntity.class.getDeclaredMethod("completeUsingItem");finish.setAccessible(true);finish.invoke(player);
        h.assertTrue(dose(player)==0 && player.getMainHandItem().is(Items.BUCKET),"normal completed milk use cures even a subthreshold dose");
        player.getPersistentData().putDouble("cwi:airborne_neurotoxin",80);
        player.hurt(player.damageSources().genericKill(),Float.MAX_VALUE);
        h.assertTrue(dose(player)==0,"death clears dose");h.succeed();
    }

    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void finalBreakingProtectionPulseCoversBothChannels(GameTestHelper h) {
        var player=player(h,h.absolutePos(new BlockPos(2,2,2)));gear(h,player,"gas_filtration",4);
        var helmet=player.getItemBySlot(EquipmentSlot.HEAD);
        helmet.enchant(ProtectiveEnchantmentGameTests.enchantment(h,"ocular_shielding"),4);
        helmet.setDamageValue(helmet.getMaxDamage()-1);fillEye(h,player,GasProfiles.byId("kubejs:chlorine"),1000);
        pulse(player);
        h.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),"filter breaks on its last charge");
        h.assertTrue(player.getHealth()==player.getMaxHealth() && !player.hasEffect(MobEffects.BLINDNESS),"breaking pulse retains independent eye/breathing coverage");
        pulse(player);
        h.assertTrue(player.getHealth()<player.getMaxHealth() && player.hasEffect(MobEffects.BLINDNESS),"next second needs new protection");h.succeed();
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void doseDowngradesWithoutCuringAndPreservesCommandEffects(GameTestHelper h) {
        var player=player(h,h.absolutePos(new BlockPos(2,2,2)));
        player.getPersistentData().putDouble("cwi:airborne_neurotoxin",102);pulse(player);
        h.assertTrue(player.getEffect(GasPollution.NEUROTOXIN).getAmplifier()==3,"highest gas dose is stage IV");
        pulse(player);pulse(player);
        h.assertTrue(player.getEffect(GasPollution.NEUROTOXIN).getAmplifier()==2
                && dose(player)==99,"recovery downgrades without curing remaining dose");
        player.forceAddEffect(new MobEffectInstance(GasPollution.NEUROTOXIN,1200,3),null);pulse(player);
        h.assertTrue(player.getEffect(GasPollution.NEUROTOXIN).getAmplifier()==3
                && player.getEffect(GasPollution.NEUROTOXIN).getDuration()==1200,"command effect remains independent of gas-owned refresh");h.succeed();
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void canceledCureAndDeathDoNotClearDose(GameTestHelper h) {
        var player=player(h,h.absolutePos(new BlockPos(2,2,2)));
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.MobEffectEvent.Remove> preventCure=event->{
            if(event.getEntity()==player) event.setCanceled(true);
        };
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDeathEvent> preventDeath=event->{
            if(event.getEntity()==player) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(preventCure);NeoForge.EVENT_BUS.addListener(preventDeath);
        try {
            player.getPersistentData().putDouble("cwi:airborne_neurotoxin",80);
            player.addEffect(new MobEffectInstance(GasPollution.NEUROTOXIN,100,2));player.removeEffect(GasPollution.NEUROTOXIN);
            h.assertTrue(player.hasEffect(GasPollution.NEUROTOXIN) && dose(player)==80,"canceled removal preserves dose");
            NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(player,player.damageSources().generic()));
            h.assertTrue(dose(player)==80,"canceled death preserves dose");
        } finally {NeoForge.EVENT_BUS.unregister(preventCure);NeoForge.EVENT_BUS.unregister(preventDeath);}
        h.succeed();
    }

    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void lethalExposureCannotRecreateDoseAfterDeath(GameTestHelper h) {
        var player=player(h,h.absolutePos(new BlockPos(2,2,2)));
        player.getPersistentData().putDouble("cwi:airborne_neurotoxin",80);
        fillEye(h,player,GasProfiles.byId("kubejs:hydrogen_sulfide"),10000);pulse(player);
        h.assertTrue(!player.isAlive(),"lethal sulfide fixture");
        h.assertTrue(dose(player)==0,"exposure must not recreate a dose after its own lethal damage");h.succeed();
    }

}
