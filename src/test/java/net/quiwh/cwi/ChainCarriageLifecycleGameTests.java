package net.quiwh.cwi;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.content.carriage.CarriageContent;
import net.quiwh.cwi.content.carriage.ChainCarriageBlockEntity;
import net.quiwh.cwi.content.carriage.ChainCarriageEntity;
import net.quiwh.cwi.content.carriage.ChainCarriageInteraction;
import net.quiwh.cwi.content.carriage.LoadedConveyors;

/** Exercises real assembly and Create's mounted storage, seats, interaction and kinetic APIs. */
@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class ChainCarriageLifecycleGameTests {
    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void assembledBarrelSurvivesNativeSaveAndDisassemblesOnce(GameTestHelper helper) {
        Fixture fixture = prepare(helper, Blocks.BARREL.defaultBlockState());
        BarrelBlockEntity barrel = (BarrelBlockEntity) helper.getLevel().getBlockEntity(fixture.anchor().below());
        barrel.setItem(0, new ItemStack(Items.DIAMOND, 7));
        barrel.setItem(26, new ItemStack(Items.COPPER_INGOT, 23));
        ChainCarriageEntity original = assemble(helper, fixture);
        helper.assertTrue(original.getContraption().getStorage().getAllItemStorages().containsKey(BlockPos.ZERO.below()),
                "a real assembled barrel is mounted through Create's storage registry");
        var inventory = original.getContraption().getStorage().getAllItems();
        helper.assertTrue(count(inventory, Items.DIAMOND) == 7 && count(inventory, Items.COPPER_INGOT) == 23,
                "assembly captures both barrel inventory positions without dropping their contents");
        original.tick();
        var saved = new CompoundTag();
        helper.assertTrue(original.save(saved), "the assembled native storage is saved with its carriage");
        original.discard();
        var restored = (ChainCarriageEntity) EntityType.loadEntityRecursive(saved, helper.getLevel(), entity -> entity);
        helper.assertTrue(restored != null && restored.getContraption().getStorage().getAllItemStorages()
                        .containsKey(BlockPos.ZERO.below()),
                "the registered native entity restores a mounted barrel from saved data");
        inventory = restored.getContraption().getStorage().getAllItems();
        helper.assertTrue(count(inventory, Items.DIAMOND) == 7 && count(inventory, Items.COPPER_INGOT) == 23,
                "saved mounted storage preserves every original item");
        BlockPos target = BlockPos.containing(restored.getAnchorVec().add(.5, .5, .5)).below();
        restored.requestDisassembly();
        restored.tick();
        helper.assertTrue(restored.isRemoved() && helper.getLevel().getBlockEntity(target) instanceof BarrelBlockEntity,
                "queued disassembly restores the actual barrel block entity");
        barrel = (BarrelBlockEntity) helper.getLevel().getBlockEntity(target);
        helper.assertTrue(barrel.getItem(0).is(Items.DIAMOND) && barrel.getItem(0).getCount() == 7
                        && barrel.getItem(26).is(Items.COPPER_INGOT) && barrel.getItem(26).getCount() == 23,
                "native unmount restores inventory and slot positions exactly");
        restored.disassemble();
        helper.assertTrue(barrel.getItem(0).getCount() == 7 && barrel.getItem(26).getCount() == 23
                        && helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(2)).isEmpty(),
                "repeated disassembly cannot duplicate the load or spill captured inventory");
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void nativeMotorStopsAndReversesTheAssembledLoad(GameTestHelper helper) {
        Fixture fixture = prepare(helper, Blocks.STONE.defaultBlockState());
        ChainCarriageEntity entity = assemble(helper, fixture);
        entity.tick();
        BlockPos motorPos = fixture.first().getBlockPos().below();
        helper.getLevel().setBlock(motorPos,
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP), 3);
        var motor = (CreativeMotorBlockEntity) helper.getLevel().getBlockEntity(motorPos);
        motor.generatedSpeed.setValue(64);
        motor.initialize();
        fixture.first().attachKinetics();
        fixture.second().attachKinetics();
        helper.assertTrue(fixture.first().getSpeed() == 64 && fixture.second().getSpeed() == 64,
                "a native motor drives both actual connected conveyor block entities");
        Vec3 before = entity.position();
        for (int i = 0; i < 4; i++) entity.tick();
        Vec3 forward = entity.position().subtract(before);
        helper.assertTrue(forward.x > 0 && Math.abs(forward.length() - 4 * 64.0 / 360) < .001,
                "the assembled load follows native conveyor RPM and linear travel speed");
        motor.generatedSpeed.setValue(0);
        motor.updateGeneratedRotation();
        helper.assertTrue(fixture.first().getSpeed() == 0, "native network propagates the stopped motor");
        before = entity.position();
        for (int i = 0; i < 3; i++) entity.tick();
        helper.assertTrue(entity.position().distanceToSqr(before) < 1e-10 && entity.getDeltaMovement().equals(Vec3.ZERO),
                "zero RPM pauses the actual moving contraption and its reported motion");
        motor.generatedSpeed.setValue(-64);
        motor.updateGeneratedRotation();
        helper.assertTrue(fixture.first().getSpeed() == -64 && fixture.second().getSpeed() == -64,
                "native network propagates reversed rotation through the chain");
        before = entity.position();
        for (int i = 0; i < 4; i++) entity.tick();
        Vec3 backward = entity.position().subtract(before);
        helper.assertTrue(backward.x < 0 && Math.abs(backward.length() - 4 * 64.0 / 360) < .001,
                "reversal moves the assembled load back along the same chain without a positional jump");
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void nativeMovingInteractionRequiresBuildPermissionEmptyHandAndReach(GameTestHelper helper) {
        Fixture fixture = prepare(helper, Blocks.BARREL.defaultBlockState());
        ChainCarriageEntity entity = assemble(helper, fixture);
        entity.tick();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 nearby = entity.position().add(0, 0, -2);
        player.setPos(nearby);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        helper.assertTrue(!entity.handlePlayerInteraction(player, BlockPos.ZERO, Direction.UP, InteractionHand.MAIN_HAND),
                "Create's native moving interaction rejects a held item for carriage disassembly");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        helper.assertTrue(!entity.handlePlayerInteraction(player, BlockPos.ZERO, Direction.UP, InteractionHand.MAIN_HAND),
                "sneaking cannot request carriage disassembly");
        player.setShiftKeyDown(false);
        Player adventure = helper.makeMockPlayer(GameType.ADVENTURE);
        // GameTest's mock player does not apply the GameType to its native abilities.
        adventure.getAbilities().mayBuild = false;
        adventure.setPos(nearby);
        helper.assertTrue(!adventure.mayBuild(), "the adventure fixture has native build permission disabled");
        helper.assertTrue(!entity.handlePlayerInteraction(adventure, BlockPos.ZERO, Direction.UP, InteractionHand.MAIN_HAND),
                "adventure-mode players cannot bypass build permission through the moving-block packet");
        player.setPos(nearby.add(100, 0, 0));
        helper.assertTrue(!entity.handlePlayerInteraction(player, BlockPos.ZERO, Direction.UP, InteractionHand.MAIN_HAND),
                "the actual transformed carriage block must be within native player block reach");
        player.setPos(nearby);
        var interaction = new ChainCarriageInteraction();
        helper.assertTrue(!interaction.handlePlayerInteraction(player, InteractionHand.MAIN_HAND,
                        BlockPos.ZERO.below(), entity)
                        && !interaction.handlePlayerInteraction(player, null, BlockPos.ZERO, entity),
                "the carriage behavior rejects non-carriage local blocks and malformed missing hands");
        var saved = new CompoundTag();
        entity.save(saved);
        helper.assertTrue(!saved.getBoolean("CwiDisassemble"), "denied requests leave the assembled load active");
        helper.assertTrue(entity.handlePlayerInteraction(player, BlockPos.ZERO, Direction.UP, InteractionHand.MAIN_HAND),
                "nearby empty-hand interaction reaches the registered native carriage behavior");
        entity.tick();
        helper.assertTrue(entity.isRemoved(), "only the valid native interaction queues safe disassembly");
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void nativeSeatPassengerSurvivesEntitySaveAndReturnsToSeat(GameTestHelper helper) {
        Fixture fixture = prepare(helper, AllBlocks.SEATS.get(DyeColor.RED).getDefaultState());
        ChainCarriageEntity original = assemble(helper, fixture);
        original.tick();
        helper.assertTrue(original.getContraption().getSeats().equals(java.util.List.of(BlockPos.ZERO.below())),
                "real seat assembly discovers the native passenger seat");
        Pig passenger = EntityType.PIG.create(helper.getLevel());
        helper.assertTrue(passenger != null, "the fixture creates a real native passenger entity");
        passenger.setPos(original.position());
        helper.getLevel().addFreshEntity(passenger);
        original.addSittingPassenger(passenger, 0);
        helper.assertTrue(passenger.getVehicle() == original
                        && original.getContraption().getSeatMapping().get(passenger.getUUID()) == 0,
                "Create's native seat API mounts and maps the passenger to the moving load");
        var saved = new CompoundTag();
        helper.assertTrue(original.save(saved), "native recursive entity persistence saves the seated passenger");
        passenger.discard();
        original.discard();
        var restored = (ChainCarriageEntity) EntityType.loadEntityRecursive(saved, helper.getLevel(), entity -> entity);
        helper.assertTrue(restored != null && restored.getPassengers().size() == 1
                        && restored.getPassengers().getFirst() instanceof Pig,
                "native entity loading restores the actual passenger relationship");
        var restoredPassenger = restored.getPassengers().getFirst();
        helper.assertTrue(restored.getContraption().getSeatMapping().containsKey(restoredPassenger.getUUID()),
                "saved seat mapping resolves the restored passenger identity");
        restored.requestDisassembly();
        restored.tick();
        helper.assertTrue(restored.isRemoved() && restoredPassenger.getVehicle() instanceof SeatEntity,
                "native disassembly transfers the passenger back onto the restored placed seat");
        helper.succeed();
    }

    private record Fixture(ChainConveyorBlockEntity first, ChainConveyorBlockEntity second, BlockPos anchor) {}

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void blockedDisassemblyKeepsMountedInventoryUntilSpaceClears(GameTestHelper helper) {
        Fixture fixture = prepare(helper, Blocks.BARREL.defaultBlockState());
        var barrel = (BarrelBlockEntity) helper.getLevel().getBlockEntity(fixture.anchor().below());
        barrel.setItem(0, new ItemStack(Items.DIAMOND, 7));
        var entity = assemble(helper, fixture);
        entity.tick();
        var target = BlockPos.containing(entity.getAnchorVec().add(.5, .5, .5)).below();
        helper.getLevel().setBlock(target, Blocks.BEDROCK.defaultBlockState(), 3);
        entity.requestDisassembly();
        entity.tick();
        helper.assertTrue(!entity.isRemoved() && helper.getLevel().getBlockState(target).is(Blocks.BEDROCK)
                        && count(entity.getContraption().getStorage().getAllItems(), Items.DIAMOND) == 7,
                "an unplaceable native target keeps the moving barrel and mounted inventory intact");
        helper.getLevel().setBlock(target, Blocks.AIR.defaultBlockState(), 3);
        entity.tick();
        helper.assertTrue(entity.isRemoved() && helper.getLevel().getBlockEntity(target) instanceof BarrelBlockEntity,
                "clearing the target completes the pending safe disassembly");
        var restored = (BarrelBlockEntity) helper.getLevel().getBlockEntity(target);
        helper.assertTrue(restored.getItem(0).is(Items.DIAMOND) && restored.getItem(0).getCount() == 7,
                "the formerly blocked barrel restores its entire mounted inventory");
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void unloadedRoundedDisassemblyTargetDoesNotLoadAChunk(GameTestHelper helper) {
        Fixture fixture = prepare(helper, Blocks.STONE.defaultBlockState());
        var entity = assemble(helper, fixture);
        entity.tick();
        BlockPos far = fixture.anchor().offset(1_000_000, 0, 0);
        helper.assertTrue(helper.getLevel().getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null,
                "the distant restored-block fixture starts in an unloaded chunk");
        BlockPos local = far.subtract(BlockPos.containing(entity.getAnchorVec().add(.5, .5, .5)));
        entity.getContraption().getBlocks().put(local,
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(
                        local, Blocks.STONE.defaultBlockState(), null));
        entity.requestDisassembly();
        entity.tick();
        helper.assertTrue(!entity.isRemoved()
                        && helper.getLevel().getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null,
                "exact transformed target checks retain the load without force-loading beyond its cached bounds");
        helper.succeed();
    }

    private static Fixture prepare(GameTestHelper helper, BlockState load) {
        BlockPos first = new BlockPos(2, 8, 5), second = new BlockPos(14, 8, 5);
        helper.setBlock(first, AllBlocks.CHAIN_CONVEYOR.getDefaultState());
        helper.setBlock(second, AllBlocks.CHAIN_CONVEYOR.getDefaultState());
        ChainConveyorBlockEntity a = helper.getBlockEntity(first), b = helper.getBlockEntity(second);
        a.initialize(); b.initialize();
        a.prepareStats(); b.prepareStats();
        a.addConnectionTo(b.getBlockPos()); b.addConnectionTo(a.getBlockPos());
        a.setSpeed(0); b.setSpeed(0); a.prepareStats(); b.prepareStats();
        LoadedConveyors.add(a); LoadedConveyors.add(b);
        var stats = a.connectionStats.get(second.subtract(first));
        BlockPos anchor = BlockPos.containing(stats.start().lerp(stats.end(), .5).subtract(0, 1, 0));
        helper.getLevel().setBlock(anchor, CarriageContent.BLOCK.getDefaultState(), 3);
        helper.getLevel().setBlock(anchor.below(), load, 3);
        return new Fixture(a, b, anchor);
    }

    private static ChainCarriageEntity assemble(GameTestHelper helper, Fixture fixture) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(fixture.anchor()));
        var state = helper.getLevel().getBlockState(fixture.anchor());
        var result = state.useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(fixture.anchor()), Direction.UP, fixture.anchor(), false));
        helper.assertTrue(result.consumesAction(), "native placed-block interaction queues assembly");
        var blockEntity = (ChainCarriageBlockEntity) helper.getLevel().getBlockEntity(fixture.anchor());
        blockEntity.tick();
        var entities = helper.getLevel().getEntitiesOfClass(ChainCarriageEntity.class,
                new AABB(fixture.anchor()).inflate(3));
        helper.assertTrue(entities.size() == 1, "one queued assembly creates exactly one native carriage entity");
        helper.assertTrue(helper.getLevel().getBlockState(fixture.anchor()).isAir()
                        && helper.getLevel().getBlockState(fixture.anchor().below()).isAir(),
                "native assembly removes its carriage and actual load from the world");
        return entities.getFirst();
    }

    private static int count(net.neoforged.neoforge.items.IItemHandler inventory, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }
}
