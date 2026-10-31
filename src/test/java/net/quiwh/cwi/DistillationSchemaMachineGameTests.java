package net.quiwh.cwi;

import com.drmangotea.tfmg.content.decoration.tanks.steel.SteelTankBlockEntity;
import com.drmangotea.tfmg.content.machinery.metallurgy.coke_oven.CokeOvenBlockEntity;
import com.drmangotea.tfmg.content.machinery.oil_processing.distillation_tower.controller.DistillationControllerBlockEntity;
import com.drmangotea.tfmg.content.machinery.oil_processing.distillation_tower.output.DistillationOutputBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Execute the Test.js recipes in the installed machines, including native progress saves. */
@PrefixGameTestTemplate(false)
public final class DistillationSchemaMachineGameTests {
    @EventBusSubscriber(modid = CwiCore.ID)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
            if (ModList.get().isLoaded("tfmg")) event.register(DistillationSchemaMachineGameTests.class);
        }
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void testScriptCokingProcessesAndResumes(GameTestHelper helper) {
        var pos = new BlockPos(1, 1, 1);
        place(helper, pos, "coke_oven");
        CokeOvenBlockEntity oven = helper.getBlockEntity(pos);
        oven.initialize(); oven.createNextTick = false;
        oven.inventory.setStackInSlot(0, new ItemStack(Items.QUARTZ_BRICKS));
        oven.executeRecipe();
        for (int i = 0; i < 20; i++) oven.tickRecipe();
        var saved = oven.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.assertTrue(saved.getInt("Timer") == 20, "native coke oven saves mid-recipe timer; " + saved);
        oven.setRemoved();
        var restored = (CokeOvenBlockEntity) oven.getType().create(oven.getBlockPos(), oven.getBlockState());
        restored.setLevel(helper.getLevel()); restored.loadCustomOnly(saved, helper.getLevel().registryAccess());
        helper.getLevel().setBlockEntity(restored); restored.initialize(); restored.createNextTick = false;
        oven = restored;
        for (int i = 0; i < 21; i++) oven.tickRecipe();
        helper.assertTrue(oven.inventory.isEmpty(), "Test.js coking consumes the input");
        // TFMG emits the fluid amounts once per processing tick, rather than once per batch.
        helper.assertTrue(oven.primaryTank.getFluid().is(Fluids.WATER) && oven.primaryTank.getFluidAmount() == 40
                && oven.secondaryTank.getFluid().is(Fluids.LAVA) && oven.secondaryTank.getFluidAmount() == 40,
                "Test.js coking fills both native output tanks exactly once per progress tick");
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(oven.getBlockPos()).inflate(2));
        helper.assertTrue(drops.stream().anyMatch(item -> item.getItem().is(Items.CHARCOAL) && item.getItem().getCount() == 1), "Test.js coking drops its item result");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void testScriptDistillationProcessesAndResumes(GameTestHelper helper) {
        var pos = new BlockPos(1, 1, 1);
        var tankPos = new BlockPos(2, 1, 1);
        var firstPos = new BlockPos(1, 2, 1); var secondPos = new BlockPos(1, 3, 1);
        place(helper, pos, "steel_distillation_controller"); place(helper, tankPos, "steel_fluid_tank");
        place(helper, firstPos, "steel_distillation_output"); place(helper, secondPos, "steel_distillation_output");
        DistillationControllerBlockEntity controller = helper.getBlockEntity(pos); controller.initialize();
        SteelTankBlockEntity steel = helper.getBlockEntity(tankPos); steel.initialize();
        steel.setHeight(4); steel.setWidth(1); steel.activeHeat = 1;
        controller.outputs.add(helper.absolutePos(firstPos)); controller.outputs.add(helper.absolutePos(secondPos));
        controller.tank.fill(new FluidStack(Fluids.LAVA, 100), FluidAction.EXECUTE);
        controller.currentRecipe = controller.findRecipe();
        helper.assertTrue(controller.currentRecipe != null && controller.currentRecipe.getProcessingDuration() == 40, "Test.js distillation matches native fluid and outlet requirements");
        for (int i = 0; i < 20; i++) controller.manageRecipe(steel);
        var saved = controller.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.assertTrue(saved.getInt("Timer") == 20, "native distillation saves mid-recipe timer");
        controller.setRemoved();
        var restored = (DistillationControllerBlockEntity) controller.getType().create(controller.getBlockPos(), controller.getBlockState());
        restored.setLevel(helper.getLevel()); restored.loadCustomOnly(saved, helper.getLevel().registryAccess());
        helper.getLevel().setBlockEntity(restored); restored.initialize(); controller = restored;
        controller.currentRecipe = controller.findRecipe();
        for (int i = 0; i < 21; i++) controller.manageRecipe(steel);
        DistillationOutputBlockEntity first = helper.getBlockEntity(firstPos); DistillationOutputBlockEntity second = helper.getBlockEntity(secondPos);
        helper.assertTrue(controller.tank.isEmpty() && controller.currentRecipe == null, "Test.js distillation drains exactly one input batch");
        helper.assertTrue(first.tank.getFluid().is(Fluids.WATER) && first.tank.getFluidAmount() == 50
                && second.tank.getFluid().is(Fluids.LAVA) && second.tank.getFluidAmount() == 50, "Test.js distillation sends results to separate native outlets");
        helper.succeed();
    }
    private static void place(GameTestHelper helper, BlockPos pos, String block) {
        var id = ResourceLocation.parse("tfmg:" + block);
        helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "native block exists: " + id);
        helper.setBlock(pos, BuiltInRegistries.BLOCK.get(id));
    }
}
