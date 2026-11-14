package net.quiwh.cwi;

import com.jesz.createdieselgenerators.content.basin_lid.BasinLidBlock;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.pollution.*;

@PrefixGameTestTemplate(false)
public final class GasLidGameTests {
    @EventBusSubscriber(modid=CwiCore.ID) public static final class Registration {
        @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
            if(ModList.get().isLoaded("createdieselgenerators"))event.register(GasLidGameTests.class);
        }
    }
    @GameTest(template="machine_empty",templateNamespace="cwi")
    public static void dieselLidOpeningAndRemovalReleaseStoredGas(GameTestHelper h) throws Exception {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,3,4));
        var lid=(BasinLidBlock)BuiltInRegistries.BLOCK.get(ResourceLocation.parse("createdieselgenerators:basin_lid"));
        level.setBlockAndUpdate(pos,AllBlocks.BASIN.getDefaultState());
        level.setBlockAndUpdate(pos.above(),lid.defaultBlockState());
        var basin=(BasinBlockEntity)level.getBlockEntity(pos);var tank=basin.getTanks().getFirst().getPrimaryHandler();
        var gas=new FluidStack(BuiltInRegistries.FLUID.get(ResourceLocation.parse("kubejs:ammonia")),600);
        h.assertTrue(tank.fill(gas,FluidAction.EXECUTE)==600&&tank.getFluidAmount()==600,"closed lid keeps gas in the basin");
        var use=BasinLidBlock.class.getDeclaredMethod("useItemOn",ItemStack.class,BlockState.class,net.minecraft.world.level.Level.class,
                BlockPos.class,net.minecraft.world.entity.player.Player.class,InteractionHand.class,BlockHitResult.class);
        use.setAccessible(true);
        use.invoke(lid,ItemStack.EMPTY,level.getBlockState(pos.above()),level,pos.above(),h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL),InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos.above()),Direction.UP,pos.above(),false));
        h.assertTrue(tank.isEmpty(),"opening the real Diesel Generators lid immediately vents stored gas");
        h.assertTrue(GasClouds.get(level).amount(pos.above(),GasProfiles.byId("kubejs:ammonia"),level.getGameTime())==600,"opening emits the stored amount once");
        level.setBlockAndUpdate(pos.above(),lid.defaultBlockState());
        tank.fill(gas,FluidAction.EXECUTE);
        level.setBlockAndUpdate(pos.above(),Blocks.AIR.defaultBlockState());
        h.assertTrue(tank.isEmpty(),"removing the lid immediately vents the retained gas");
        h.assertTrue(GasClouds.get(level).amount(pos.above(),GasProfiles.byId("kubejs:ammonia"),level.getGameTime())==1200,"removal cannot lose or duplicate gas");
        h.succeed();
    }

    @GameTest(template="machine_empty",templateNamespace="cwi")
    public static void dieselLidStaysSealedAcrossReloadAndVentsOnRedstoneOpening(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,3,4));
        var lid=(BasinLidBlock)BuiltInRegistries.BLOCK.get(ResourceLocation.parse("createdieselgenerators:basin_lid"));
        level.setBlockAndUpdate(pos,AllBlocks.BASIN.getDefaultState());level.setBlockAndUpdate(pos.above(),lid.defaultBlockState());
        var basin=(BasinBlockEntity)level.getBlockEntity(pos);
        var gas=new FluidStack(BuiltInRegistries.FLUID.get(ResourceLocation.parse("kubejs:ammonia")),600);
        basin.getTanks().getFirst().getPrimaryHandler().fill(gas,FluidAction.EXECUTE);
        basin=NativeAdvancedTestSupport.reload(h,basin);
        h.assertTrue(basin.getTanks().getFirst().getPrimaryHandler().getFluidAmount()==600,"closed lid keeps saved gas through initialization");
        level.setBlockAndUpdate(pos.above().east(),Blocks.REDSTONE_BLOCK.defaultBlockState());
        lid.neighborChanged(level.getBlockState(pos.above()),level,pos.above(),Blocks.REDSTONE_BLOCK,pos.above().east(),false);
        h.assertTrue(level.getBlockState(pos.above()).getValue(BasinLidBlock.OPEN),"native redstone opens the real lid");
        h.assertTrue(basin.getTanks().getFirst().isEmpty(),"flags-2 redstone opening releases retained contents immediately");
        h.assertTrue(GasClouds.get(level).amount(pos.above(),GasProfiles.byId("kubejs:ammonia"),level.getGameTime())==600,"redstone opening emits exactly once");
        h.succeed();
    }
}
