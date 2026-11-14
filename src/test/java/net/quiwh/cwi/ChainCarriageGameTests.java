package net.quiwh.cwi;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.quiwh.cwi.content.carriage.CarriageContent;
import net.quiwh.cwi.content.carriage.ChainCarriageBlockEntity;
import net.quiwh.cwi.content.carriage.ChainCarriageEntity;
import java.util.function.Consumer;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class ChainCarriageGameTests {
    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void brokenStoppedChainDisassemblesItsCarriage(GameTestHelper helper) {
        BlockPos first = new BlockPos(2, 6, 5), second = new BlockPos(10, 6, 5);
        helper.setBlock(first, AllBlocks.CHAIN_CONVEYOR.getDefaultState());
        helper.setBlock(second, AllBlocks.CHAIN_CONVEYOR.getDefaultState());
        ChainConveyorBlockEntity a = helper.getBlockEntity(first), b = helper.getBlockEntity(second);
        a.initialize(); b.initialize(); a.prepareStats(); b.prepareStats();
        a.addConnectionTo(b.getBlockPos()); b.addConnectionTo(a.getBlockPos());
        var stats = a.connectionStats.get(second.subtract(first));
        BlockPos anchor = BlockPos.containing(stats.start().lerp(stats.end(), .5).subtract(0, 1, 0));
        helper.getLevel().setBlock(anchor, CarriageContent.BLOCK.getDefaultState(), 3);
        helper.getLevel().setBlock(anchor.below(), Blocks.STONE.defaultBlockState(), 3);
        ChainCarriageBlockEntity blockEntity = (ChainCarriageBlockEntity) helper.getLevel().getBlockEntity(anchor);
        blockEntity.queueAssembly(); blockEntity.tick();
        var entities = helper.getLevel().getEntitiesOfClass(ChainCarriageEntity.class, new AABB(anchor).inflate(3));
        helper.assertTrue(entities.size() == 1, "fixture assembled on a stopped native chain");
        ChainCarriageEntity entity = entities.getFirst();
        entity.tick();
        a.removeConnectionTo(b.getBlockPos()); b.removeConnectionTo(a.getBlockPos());
        entity.tick();
        helper.assertTrue(entity.isRemoved(), "removing the supporting connection disassembles even at zero RPM");
        helper.assertTrue(helper.getLevel().getBlockState(anchor).is(CarriageContent.BLOCK.get())
                        && helper.getLevel().getBlockState(anchor.below()).is(Blocks.STONE),
                "the stopped chain's removed attachment returns its exact native load");
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void cancelledCarriageSpawnRestoresItsLoad(GameTestHelper helper) {
        BlockPos first = new BlockPos(2, 6, 5), second = new BlockPos(10, 6, 5);
        helper.setBlock(first, AllBlocks.CHAIN_CONVEYOR.getDefaultState());
        helper.setBlock(second, AllBlocks.CHAIN_CONVEYOR.getDefaultState());
        ChainConveyorBlockEntity a = helper.getBlockEntity(first), b = helper.getBlockEntity(second);
        a.initialize(); b.initialize(); a.prepareStats(); b.prepareStats();
        a.addConnectionTo(b.getBlockPos()); b.addConnectionTo(a.getBlockPos());
        BlockPos anchor = BlockPos.containing(a.connectionStats.get(second.subtract(first)).start()
                .lerp(a.connectionStats.get(second.subtract(first)).end(), .5).subtract(0, 1, 0));
        helper.getLevel().setBlock(anchor, CarriageContent.BLOCK.getDefaultState(), 3);
        helper.getLevel().setBlock(anchor.below(), Blocks.BARREL.defaultBlockState(), 3);
        var barrel = (net.minecraft.world.level.block.entity.BarrelBlockEntity) helper.getLevel().getBlockEntity(anchor.below());
        barrel.setItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
        Consumer<EntityJoinLevelEvent> cancel = event -> {
            if (event.getEntity() instanceof ChainCarriageEntity && event.getLevel() == helper.getLevel()) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            ChainCarriageBlockEntity blockEntity = (ChainCarriageBlockEntity) helper.getLevel().getBlockEntity(anchor);
            blockEntity.queueAssembly(); blockEntity.tick();
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
        helper.assertTrue(helper.getLevel().getBlockState(anchor).is(CarriageContent.BLOCK.get())
                        && helper.getLevel().getBlockEntity(anchor.below()) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity,
                "entity-join cancellation restores the carriage and the real load");
        barrel = (net.minecraft.world.level.block.entity.BarrelBlockEntity) helper.getLevel().getBlockEntity(anchor.below());
        helper.assertTrue(barrel.getItem(0).is(net.minecraft.world.item.Items.DIAMOND) && barrel.getItem(0).getCount() == 3
                        && helper.getLevel().getEntitiesOfClass(ChainCarriageEntity.class, new AABB(anchor).inflate(3)).isEmpty(),
                "cancellation neither deletes nor duplicates mounted inventory or spawns an orphan entity");
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID, timeoutTicks = 80)
    public static void carriageAssemblesUnderNativeConveyorChain(GameTestHelper helper) {
        Block carriage = BuiltInRegistries.BLOCK.getOptional(CwiCore.loadResource("chain_conveyor_carriage")).orElse(null);
        helper.assertTrue(carriage != null, "Core registers a chain conveyor carriage without BnB");
        BlockPos first = new BlockPos(2, 6, 5), second = new BlockPos(10, 6, 5);
        helper.setBlock(first, AllBlocks.CHAIN_CONVEYOR.getDefaultState());
        helper.setBlock(second, AllBlocks.CHAIN_CONVEYOR.getDefaultState());
        ChainConveyorBlockEntity a = helper.getBlockEntity(first), b = helper.getBlockEntity(second);
        a.prepareStats(); b.prepareStats();
        a.addConnectionTo(b.getBlockPos()); b.addConnectionTo(a.getBlockPos());
        a.prepareStats();
        var stats = a.connectionStats.get(second.subtract(first));
        Vec3 middle = stats.start().lerp(stats.end(), .5);
        BlockPos anchor = BlockPos.containing(middle.subtract(0, 1, 0));
        helper.getLevel().setBlock(anchor, carriage.defaultBlockState(), 3);
        helper.getLevel().setBlock(anchor.below(), Blocks.STONE.defaultBlockState(), 3);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(anchor));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var result = carriage.defaultBlockState().useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(anchor), Direction.UP, anchor, false));
        helper.assertTrue(result.consumesAction(), "empty-hand interaction queues assembly");
        helper.succeedWhen(() -> {
            var entities = helper.getLevel().getEntitiesOfClass(AbstractContraptionEntity.class,
                    new AABB(anchor).inflate(3), entity -> entity.getType() == BuiltInRegistries.ENTITY_TYPE
                            .get(CwiCore.loadResource("chain_conveyor_carriage")));
            helper.assertTrue(entities.size() == 1, "assembly spawns exactly one Core contraption");
            var contraption = entities.getFirst().getContraption();
            helper.assertTrue(contraption.getBlocks().values().stream().anyMatch(info -> info.state().is(carriage))
                            && contraption.getBlocks().values().stream().anyMatch(info -> info.state().is(Blocks.STONE)),
                    "carriage and its supported load are assembled using Create contraptions");
            helper.assertTrue(helper.getLevel().getBlockState(anchor).isAir()
                            && helper.getLevel().getBlockState(anchor.below()).isAir(),
                    "assembled blocks are removed exactly once");
        });
    }
}
