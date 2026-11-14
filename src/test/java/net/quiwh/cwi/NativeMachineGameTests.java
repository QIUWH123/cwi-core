package net.quiwh.cwi;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.machine.BlastFurnaceBlockEntity;
import net.quiwh.cwi.machine.FurnaceHatchBlockEntity;
import net.quiwh.cwi.machine.FurnaceMaterials;
import net.quiwh.cwi.machine.FurnaceStructure;
import net.quiwh.cwi.machine.IncubatorBlockEntity;
import net.quiwh.cwi.machine.LavaExtractorBlockEntity;
import net.quiwh.cwi.machine.MachineBlock;
import net.quiwh.cwi.machine.MachineBlockEntity;
import net.quiwh.cwi.machine.MachineBlocks;
import net.quiwh.cwi.machine.MixingVesselBlockEntity;
import net.quiwh.cwi.machine.SolarEvaporatorBlockEntity;
import net.quiwh.cwi.machine.StirlingEngineBlockEntity;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class NativeMachineGameTests {
    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void furnaceNorth(GameTestHelper helper) { furnaceRotations(helper, Direction.NORTH); }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void furnaceEast(GameTestHelper helper) { furnaceRotations(helper, Direction.EAST); }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void furnaceSouth(GameTestHelper helper) { furnaceRotations(helper, Direction.SOUTH); }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void furnaceWest(GameTestHelper helper) { furnaceRotations(helper, Direction.WEST); }

    private static final BlockPos FURNACE_ORIGIN = new BlockPos(6, 2, 6);

    private static BlockPos furnaceAt(Direction front, int right, int up, int back) {
        return FURNACE_ORIGIN.relative(front.getClockWise(), right).above(up).relative(front.getOpposite(), back);
    }

    private static void furnaceRotations(GameTestHelper helper, Direction front) {
        buildFurnace(helper, front, 8);
        BlockPos hatchPos = furnaceAt(front, 0, 1, 0);
        BlastFurnaceBlockEntity furnace = helper.getBlockEntity(FURNACE_ORIGIN);
        FurnaceHatchBlockEntity hatch = helper.getBlockEntity(hatchPos);
        helper.assertTrue(furnace.ownsHatch(helper.absolutePos(hatchPos)), "rotated hatch belongs to the controller");
        helper.assertTrue(hatch.getItemCapability(Direction.UP).getSlots() == 14, "formed hatch exposes controller storage");
        IFluidHandler hatchFluids = hatch.getFluidCapability(Direction.UP);
        helper.assertTrue(hatchFluids.getTanks() == 4, "hatch exposes both ordinary and gas input tanks");
        helper.assertTrue(hatchFluids.fill(new FluidStack(Fluids.WATER, 200), IFluidHandler.FluidAction.EXECUTE) == 200,
                "hatch accepts ordinary input fluid");
        helper.assertTrue(hatchFluids.drain(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.SIMULATE).getAmount() == 100
                && furnace.getFluid(0).getAmount() == 200, "hatch simulates extraction without mutation");
        helper.assertTrue(hatchFluids.drain(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.EXECUTE).getAmount() == 100
                && furnace.getFluid(0).getAmount() == 100, "hatch supports the authored BOTH fluid IO");
        helper.assertTrue(furnace.getFluidCapability(front).getTanks() == 2 && furnace.getFluidCapability(front.getOpposite()) == null,
                "molten output follows the controller front");
        hatch.getItemCapability(Direction.UP).insertItem(0, new ItemStack(Items.IRON_INGOT, 3), false);
        var saved = furnace.saveWithoutMetadata(helper.getLevel().registryAccess());
        saved.putDouble("Temperature", 1777);
        furnace.loadCustomOnly(saved, helper.getLevel().registryAccess());
        helper.assertTrue(furnace.ownsHatch(helper.absolutePos(hatchPos)) && furnace.temperature() == 1777,
                "structure revalidation after loading preserves saved heat");
        checkFurnaceHeight(helper, front, furnace, hatch, 8);
    }

    static void buildFurnace(GameTestHelper helper, Direction front, int height) {
        for (int y = 0; y < height; y++) for (int z = 0; z < 5; z++) for (int x = 0; x < 5; x++) {
            char predicate = FurnaceStructure.predicate(x, y, z);
            if (predicate == '5') continue;
            var block = switch (predicate) {
                case '1', '2' -> FurnaceMaterials.CAST_REINFORCEMENT.get();
                case '3', '4' -> FurnaceMaterials.FIREPROOF_BRICKS.get();
                default -> Blocks.AIR;
            };
            helper.setBlock(furnaceAt(front, x - 2, y - 1, z - 1), block);
        }
        BlockPos hatchPos = furnaceAt(front, 0, 1, 0);
        helper.setBlock(hatchPos, MachineBlocks.FURNACE_HATCH.get());
        helper.setBlock(FURNACE_ORIGIN, MachineBlocks.BLAST_FURNACE.get().defaultBlockState().setValue(MachineBlock.FACING, front));
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void furnaceHonorsInclusiveMaximumTemperature(GameTestHelper helper) {
        buildFurnace(helper, Direction.NORTH, 4);
        helper.setBlock(FURNACE_ORIGIN.north(), Blocks.STONE);
        Runnable checks = () -> {
            BlastFurnaceBlockEntity furnace = helper.getBlockEntity(FURNACE_ORIGIN);
            furnace.hatchItems().insertItem(0, new ItemStack(Items.NAUTILUS_SHELL), false);
            var registries = helper.getLevel().registryAccess();
            var saved = furnace.saveWithoutMetadata(registries);
            saved.putDouble("Temperature", 1600.5);
            furnace.loadCustomOnly(saved, registries);
            furnace.tick();
            helper.assertTrue(furnace.progress() == 0 && !furnace.working() && furnace.getItem(0).getCount() == 1,
                    "recipe cannot start above its maximum temperature");
            saved = furnace.saveWithoutMetadata(registries); saved.putDouble("Temperature", 1600);
            furnace.loadCustomOnly(saved, registries); furnace.tick();
            helper.assertTrue(furnace.progress() == 1 && furnace.working(), "upper bound is inclusive");
            furnace.tick(); furnace.tick();
            helper.assertTrue(furnace.progress() == 3, "in-range processing advances normally");
            saved = furnace.saveWithoutMetadata(registries); saved.putDouble("Temperature", 1600.5);
            furnace.loadCustomOnly(saved, registries); furnace.tick();
            helper.assertTrue(!furnace.working() && furnace.progress() == 3 && furnace.getItem(0).getCount() == 1
                    && furnace.getFluid(4).isEmpty(), "overheating interrupts an active recipe without consuming ordinary IO");
            saved = furnace.saveWithoutMetadata(registries); saved.putDouble("Temperature", 1200);
            furnace.loadCustomOnly(saved, registries);
            for (int tick = 0; tick < 20 && !furnace.getItem(0).isEmpty(); tick++) furnace.tick();
            helper.assertTrue(furnace.getItem(0).isEmpty() && furnace.getFluid(4).getAmount() == 125,
                    "cooling back into range resumes and completes the recipe once");
            helper.succeed();
        };
        // Keep these synchronous boundary checks clear of the ten-tick cooling update.
        if (helper.getLevel().getGameTime() % 10 == 0) helper.runAfterDelay(1, checks); else checks.run();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void testScriptFurnaceProcesses(GameTestHelper helper) {
        buildFurnace(helper, Direction.NORTH, 4);
        BlastFurnaceBlockEntity furnace = helper.getBlockEntity(FURNACE_ORIGIN);
        furnace.hatchItems().insertItem(0, new ItemStack(Items.QUARTZ_BLOCK), false);
        var saved = furnace.saveWithoutMetadata(helper.getLevel().registryAccess()); saved.putDouble("Temperature", 2000);
        furnace.loadCustomOnly(saved, helper.getLevel().registryAccess());
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(furnace.formed() && furnace.getItem(0).isEmpty() && furnace.getFluid(4).is(Fluids.LAVA)
                    && furnace.getFluid(4).getAmount() == 125, "Test.js furnace recipe runs in a formed native furnace");
            helper.succeed();
        });
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void furnaceProcessesDeferredBatch(GameTestHelper helper) {
        Direction front=Direction.NORTH;buildFurnace(helper,front,4);
        helper.setBlock(FURNACE_ORIGIN.relative(front),Blocks.STONE);
        BlastFurnaceBlockEntity furnace=helper.getBlockEntity(FURNACE_ORIGIN);
        FurnaceHatchBlockEntity hatch=helper.getBlockEntity(furnaceAt(front,0,1,0));
        helper.assertTrue(furnace.ownsHatch(hatch.getBlockPos()),"processing fixture forms");
        var saved=furnace.saveWithoutMetadata(helper.getLevel().registryAccess());saved.putDouble("Temperature",2000);
        furnace.loadCustomOnly(saved,helper.getLevel().registryAccess());
        hatch.getItemCapability(Direction.UP).insertItem(0,new ItemStack(Items.COBBLESTONE,8),false);
        furnace.tick();
        helper.assertTrue(furnace.progress()==1 && furnace.duration()>=37 && furnace.duration()<=40,"furnace samples temperature-derived batch duration");
        helper.assertTrue(furnace.getItem(0).getCount()==8 && furnace.getFluid(4).isEmpty(),"furnace defers ordinary IO until completion");
        helper.runAfterDelay(45,()->{
            helper.assertTrue(furnace.getItem(0).isEmpty() && furnace.getFluid(4).getAmount()==2000,"eight-copy heated batch consumes once and produces molten output");
            helper.assertTrue(furnace.temperature()>=1200,"stored heat survives processing and ordinary cooling");
            helper.succeed();
        });
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void furnaceRoutesThreeDistinctInputs(GameTestHelper helper) {
        Direction front=Direction.NORTH;buildFurnace(helper,front,4);
        helper.setBlock(FURNACE_ORIGIN.relative(front),Blocks.STONE);
        BlastFurnaceBlockEntity furnace=helper.getBlockEntity(FURNACE_ORIGIN);
        FurnaceHatchBlockEntity hatch=helper.getBlockEntity(furnaceAt(front,0,1,0));
        helper.assertTrue(furnace.ownsHatch(hatch.getBlockPos()),"three-fluid fixture forms");
        IFluidHandler input=hatch.getFluidCapability(Direction.NORTH);
        var honey=BuiltInRegistries.FLUID.get(ResourceLocation.parse("create:honey"));
        helper.assertTrue(input.fill(new FluidStack(Fluids.WATER,100),IFluidHandler.FluidAction.EXECUTE)==100
                && input.fill(new FluidStack(Fluids.LAVA,100),IFluidHandler.FluidAction.EXECUTE)==100
                && input.fill(new FluidStack(honey,100),IFluidHandler.FluidAction.EXECUTE)==100,"one hatch accepts three distinct fluids simultaneously");
        helper.assertTrue(furnace.getFluid(0).is(Fluids.WATER) && furnace.getFluid(1).is(Fluids.LAVA) && furnace.getFluid(2).is(honey),
                "test-tagged third fluid routes to dedicated gas inventory");
        var saved=furnace.saveWithoutMetadata(helper.getLevel().registryAccess());saved.putDouble("Temperature",2000);
        furnace.loadCustomOnly(saved,helper.getLevel().registryAccess());
        hatch.getItemCapability(Direction.NORTH).insertItem(0,new ItemStack(Items.COBBLED_DEEPSLATE),false);
        furnace.tick();
        helper.assertTrue(furnace.progress()==1 && furnace.getFluid(2).getAmount()==100,"ordinary three-fluid input remains deferred");
        helper.runAfterDelay(12,()->{
            helper.assertTrue(furnace.getFluid(0).isEmpty() && furnace.getFluid(1).isEmpty() && furnace.getFluid(2).isEmpty(),"three distinct inputs consumed exactly once");
            helper.assertTrue(furnace.getFluid(4).is(Fluids.LAVA) && furnace.getFluid(4).getAmount()==100
                    && furnace.getFluid(5).is(Fluids.WATER) && furnace.getFluid(5).getAmount()==100,"two distinct molten outputs stay separate");
            helper.succeed();
        });
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID, timeoutTicks = 120)
    public static void extractorRatesAndLiveFloor(GameTestHelper helper) {
        if(helper.getLevel().getGameTime()%40==0)helper.runAfterDelay(1,()->extractorFixture(helper));
        else extractorFixture(helper);
    }

    private static void extractorFixture(GameTestHelper helper) {
        LavaExtractorBlockEntity[] heads=new LavaExtractorBlockEntity[3];
        int[] speeds={128,-128,256};
        for(int index=0;index<3;index++) {
            BlockPos headPos=new BlockPos(2+index*4,1,5);
            helper.setBlock(headPos.below(),FurnaceMaterials.ERUPTIVE_BEDROCK.get());
            helper.setBlock(headPos,MachineBlocks.LAVA_EXTRACTOR_HEAD.get());
            for(int pump=1;pump<=index+1;pump++)helper.setBlock(headPos.above(pump),MachineBlocks.LAVA_EXTRACTOR_PUMP.get());
            BlockPos top=headPos.above(index+2);
            helper.setBlock(top,MachineBlocks.LAVA_EXTRACTOR_STRESS_INPUT.get());
            helper.setBlock(top.above(),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING,Direction.DOWN));
            CreativeMotorBlockEntity motor=helper.getBlockEntity(top.above());
            motor.generatedSpeed.setValue(speeds[index]);motor.initialize();
            for(int pump=1;pump<=index+2;pump++) {
                KineticBlockEntity part=helper.getBlockEntity(headPos.above(pump));part.attachKinetics();
                helper.assertTrue(Math.abs(part.getSpeed())==Math.abs(speeds[index]),"extractor shaft propagates through every pump and top");
            }
            KineticBlockEntity first=helper.getBlockEntity(headPos.above());
            helper.assertTrue(first.getOrCreateNetwork().calculateStress()==32*(index+1)*Math.abs(speeds[index]),"each extractor pump contributes32SU perRPM to the actual network");
            heads[index]=helper.getBlockEntity(headPos);
            helper.assertTrue(heads[index].fluids().fill(new FluidStack(Fluids.WATER,1000),IFluidHandler.FluidAction.EXECUTE)==0,"extractor rejects foreign fluid");
        }
        long untilBurst=40-helper.getLevel().getGameTime()%40+1;
        helper.runAfterDelay(untilBurst,()->{
            int[] bursts={250,500,1500};
            for(int index=0;index<3;index++) {
                helper.assertTrue(heads[index].isFormed() && heads[index].pumpCount()==index+1,"live extractor recognizes pump count");
                helper.assertTrue(heads[index].getFluid(0).getAmount()==bursts[index],"one40tick burst matches pump count and absoluteRPM; actual="+heads[index].getFluid(0).getAmount());
                heads[index].fluids().drain(4000,IFluidHandler.FluidAction.EXECUTE);
                helper.setBlock(new BlockPos(2+index*4,0,5),Blocks.STONE);
            }
            helper.runAfterDelay(41,()->{
                for(var head:heads)helper.assertTrue(!head.isFormed() && head.getFluid(0).isEmpty(),"floor replacement immediately invalidates production and prevents the next burst");
                helper.succeed();
            });
        });
    }

    private static void checkFurnaceHeight(GameTestHelper helper, Direction front, BlastFurnaceBlockEntity furnace,
                                         FurnaceHatchBlockEntity hatch, int height) {
        helper.assertTrue(furnace.ownsHatch(hatch.getBlockPos()) && furnace.height() == height,
                "rotated furnace forms at height " + height);
        helper.assertTrue(furnace.parallel() == height * 2 && furnace.armor() == 8 * height - 1,
                "rotated furnace counts each reinforcement and brick exactly once");
        if (height > 4) {
            for (int z = 0; z < 5; z++) for (int x = 0; x < 5; x++)
                helper.setBlock(furnaceAt(front, x - 2, height - 2, z - 1), Blocks.AIR);
            helper.runAfterDelay(1, () -> checkFurnaceHeight(helper, front, furnace, hatch, height - 1));
            return;
        }
        BlockPos requiredBrick = furnaceAt(front, 0, -1, 0);
        helper.setBlock(requiredBrick, Blocks.AIR);
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(!furnace.ownsHatch(hatch.getBlockPos()) && !furnace.formed(), "breaking a required brick invalidates the furnace");
            helper.assertTrue(hatch.getItemCapability(Direction.UP).getSlots() == 0 && hatch.getFluidCapability(Direction.UP).getTanks() == 0,
                    "existing hatch proxies become inert when the structure breaks");
            helper.assertTrue(hatch.getFluidCapability(Direction.UP).drain(100, IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                    "invalid hatch proxy cannot drain retained controller fluid");
            helper.setBlock(requiredBrick, FurnaceMaterials.FIREPROOF_BRICKS.get());
            helper.runAfterDelay(1, () -> {
                helper.assertTrue(furnace.ownsHatch(hatch.getBlockPos()) && furnace.height() == 4, "repair reforms the rotated furnace");
                helper.assertTrue(furnace.getItem(0).getCount() == 3 && furnace.temperature() > 0,
                        "reformation preserves controller inventory and stored heat");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void storageAndSideContracts(GameTestHelper helper) {
        BlockPos solarPos = new BlockPos(1, 1, 1), lavaPos = new BlockPos(3, 1, 1), mixerPos = new BlockPos(5, 1, 1);
        helper.setBlock(solarPos, MachineBlocks.SOLAR_EVAPORATOR.get());
        helper.setBlock(lavaPos, MachineBlocks.LAVA_EXTRACTOR_HEAD.get());
        helper.setBlock(mixerPos, MachineBlocks.MIXING_VESSEL.get());
        SolarEvaporatorBlockEntity solar = helper.getBlockEntity(solarPos);
        solar.items().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 31));
        solar.fluids().fill(new FluidStack(Fluids.WATER, 1234), IFluidHandler.FluidAction.EXECUTE);
        var saved = solar.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.assertTrue(saved.getInt("MachineDataVersion") == MachineBlockEntity.DATA_VERSION, "current machine storage has a named format version");
        saved.remove("MachineDataVersion"); // Original native saves predate the marker; their storage shape is unchanged.
        solar.items().setStackInSlot(0, ItemStack.EMPTY);
        solar.fluids().drain(3000, IFluidHandler.FluidAction.EXECUTE);
        solar.loadCustomOnly(saved, helper.getLevel().registryAccess());
        helper.assertTrue(solar.getItem(0).getCount() == 31, "saved tray inventory survives reload");
        helper.assertTrue(solar.getFluid(0).getAmount() == 1234, "saved fluid survives reload");
        helper.assertTrue(solar.saveWithoutMetadata(helper.getLevel().registryAccess()).getInt("MachineDataVersion") == MachineBlockEntity.DATA_VERSION,
                "legacy unversioned storage rewrites as the current version without losing inventory/fluid");
        LavaExtractorBlockEntity lava = helper.getBlockEntity(lavaPos);
        helper.assertTrue(lava.fluids().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 0, "extractor refuses foreign fluid");
        helper.assertTrue(lava.getFluidCapability(Direction.UP) == null && lava.getFluidCapability(Direction.DOWN) == null, "extractor blocks vertical fluid IO");
        helper.assertTrue(lava.getFluidCapability(Direction.NORTH) != null, "extractor exposes horizontal tank");
        MixingVesselBlockEntity mixer = helper.getBlockEntity(mixerPos);
        helper.assertTrue(!mixer.getItemCapability(Direction.UP).insertItem(4, new ItemStack(Items.IRON_INGOT), false).isEmpty(), "automation cannot insert output slot");
        helper.assertTrue(mixer.items().insertItem(4, new ItemStack(Items.IRON_INGOT), false).isEmpty(), "internal recipes retain BOTH IO");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void stoppedMixerAndGeneratorRefresh(GameTestHelper helper) {
        BlockPos mixerPos = new BlockPos(1, 1, 1), enginePos = new BlockPos(3, 1, 1);
        helper.setBlock(mixerPos, MachineBlocks.MIXING_VESSEL.get());
        MixingVesselBlockEntity mixer = helper.getBlockEntity(mixerPos);
        mixer.items().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
        mixer.setSpeed(0); mixer.tick();
        helper.assertTrue(mixer.progress() == 0 && mixer.batch() == 0, "stopped mixer cannot select or progress a batch");
        helper.setBlock(enginePos.below(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        helper.setBlock(enginePos, MachineBlocks.STIRLING_ENGINE.get());
        StirlingEngineBlockEntity engine = helper.getBlockEntity(enginePos);
        engine.tick();
        helper.assertTrue(engine.getGeneratedSpeed() == 0, "unlit heater produces no rotation");
        helper.setBlock(enginePos.below(), Blocks.MAGMA_BLOCK);
        engine.tick();
        helper.assertTrue(Math.abs(engine.getGeneratedSpeed()) == 32, "live passive heat refreshes generator");
        helper.setBlock(enginePos.below(), Blocks.STONE);
        engine.tick();
        helper.assertTrue(engine.getGeneratedSpeed() == 0, "removing heat stops generator");
        helper.succeed();
    }
}
