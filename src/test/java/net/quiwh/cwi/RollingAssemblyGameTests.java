package net.quiwh.cwi;

import com.mrh0.createaddition.blocks.rolling_mill.RollingMillBlockEntity;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.core.Direction;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import io.netty.buffer.Unpooled;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class RollingAssemblyGameTests {
    @EventBusSubscriber(modid = CwiCore.ID)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
            if (ModList.get().isLoaded("createaddition")) event.register(RollingAssemblyGameTests.class);
        }
    }
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void rollingAssemblyDecodeAndNetwork(GameTestHelper helper) {
        var recipe = assembly(helper);
        helper.assertTrue(recipe.getSequence().size() == 2 && recipe.getLoops() == 2, "native assembly datapack loads rolling steps");
        var step = recipe.getSequence().getFirst().getAsAssemblyRecipe();
        helper.assertTrue(step.supportsAssembly(), "rolling declares native assembly support");
        var machines = new HashSet<ItemLike>(); step.addRequiredMachines(machines);
        helper.assertTrue(machines.contains(BuiltInRegistries.BLOCK.get(ResourceLocation.parse("createaddition:rolling_mill"))),
                "assembly exposes rolling mill catalyst");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            @SuppressWarnings("unchecked") var serializer = (RecipeSerializer<Recipe<?>>)recipe.getSerializer();
            serializer.streamCodec().encode(buffer, recipe);
            var copy = (SequencedAssemblyRecipe)serializer.streamCodec().decode(buffer);
            helper.assertTrue(copy.getSequence().size() == 2 && copy.getSequence().getFirst().getAsAssemblyRecipe() instanceof IAssemblyRecipe
                    && copy.resultPool.getFirst().getStack().getCount() == 3 && buffer.readableBytes() == 0, "native network retains rolling steps and counted final result");
        } finally { buffer.release(); }
        helper.succeed();
    }
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void rollingExecutesLoopsAndSurvivesReload(GameTestHelper helper) {
        assembly(helper); var mill = place(helper);
        helper.assertTrue(mill.capability.insertItem(0, new ItemStack(Items.PAPER, 2), false).isEmpty(), "automation accepts initial assembly ingredient");
        for (int step = 1; step <= 4; step++) {
            run(mill); var result = mill.outputInv.getStackInSlot(0).copy();
            if (step < 4) {
                var progress = result.get(AllDataComponents.SEQUENCED_ASSEMBLY);
                helper.assertTrue(result.is(Items.LEATHER) && progress != null && progress.step() == step && progress.progress() == step / 4f,
                        "rolling advances step " + step + " and progress");
                mill.outputInv.setStackInSlot(0, ItemStack.EMPTY);
                // The untouched second starting item must start at step one, rather than reuse a cached enforced result.
                if (step == 1) {
                    run(mill); var second = mill.outputInv.getStackInSlot(0);
                    helper.assertTrue(second.get(AllDataComponents.SEQUENCED_ASSEMBLY).step() == 1, "cached recipes do not leak another item's progress");
                    mill.outputInv.setStackInSlot(0, ItemStack.EMPTY);
                }
                helper.assertTrue(mill.capability.insertItem(0, result, false).isEmpty(), "automation accepts transitional component stack");
                if (step == 2) {
                    mill.timer = 7;
                    var saved = mill.saveWithoutMetadata(helper.getLevel().registryAccess());
                    var restored = (RollingMillBlockEntity)mill.getType().create(mill.getBlockPos(), mill.getBlockState());
                    restored.setLevel(helper.getLevel()); restored.loadCustomOnly(saved, helper.getLevel().registryAccess());
                    helper.assertTrue(restored.timer == 7 && restored.inputInv.getStackInSlot(0).get(AllDataComponents.SEQUENCED_ASSEMBLY).step() == 2,
                            "in-progress timer and assembly component survive reload");
                    mill = restored; mill.initialize(); mill.attachKinetics();
                }
            } else helper.assertTrue(result.is(Items.EMERALD) && result.getCount() == 3 && !result.has(AllDataComponents.SEQUENCED_ASSEMBLY),
                    "last loop returns counted final result with no progress component");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void rollingFullBufferWaitsWithoutLosingProgress(GameTestHelper helper) {
        assembly(helper); var mill = place(helper); mill.inputInv.setStackInSlot(0, new ItemStack(Items.PAPER));
        for (int i = 0; i < 9; i++) mill.outputInv.setStackInSlot(i, new ItemStack(Items.DIAMOND, 63));
        run(mill);
        helper.assertTrue(mill.inputInv.getStackInSlot(0).is(Items.PAPER), "blocked mill consumes no input or progress");
        for (int i = 0; i < 9; i++) helper.assertTrue(mill.outputInv.getStackInSlot(i).getCount() == 63, "blocked mill writes no partial result");
        mill.outputInv.setStackInSlot(8, ItemStack.EMPTY); run(mill);
        helper.assertTrue(mill.inputInv.getStackInSlot(0).isEmpty() && mill.outputInv.getStackInSlot(8).get(AllDataComponents.SEQUENCED_ASSEMBLY).step() == 1,
                "unblocked mill advances exactly once"); helper.succeed();
    }
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void rollingOrdinaryRecipesStillWork(GameTestHelper helper) {
        var mill = place(helper);
        helper.assertTrue(mill.capability.insertItem(0, new ItemStack(Items.IRON_INGOT), false).isEmpty(), "ordinary automation accepted");
        run(mill);
        helper.assertTrue(mill.inputInv.getStackInSlot(0).isEmpty() && mill.outputInv.getStackInSlot(0).is(BuiltInRegistries.ITEM.get(ResourceLocation.parse("createaddition:iron_rod")))
                && mill.outputInv.getStackInSlot(0).getCount() == 2, "ordinary native rolling result unchanged"); helper.succeed();
    }
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void rollingRespectsOtherAssemblySteps(GameTestHelper helper) {
        var mill = place(helper);
        mill.inputInv.setStackInSlot(0, new ItemStack(Items.STRING)); run(mill);
        var first = mill.outputInv.getStackInSlot(0).copy(); mill.outputInv.setStackInSlot(0, ItemStack.EMPTY);
        helper.assertTrue(first.get(AllDataComponents.SEQUENCED_ASSEMBLY).step() == 1, "mixed sequence starts in rolling mill");
        helper.assertTrue(!mill.capability.isItemValid(0, first), "cached rolling recipe rejects next pressing step");
        mill.inputInv.setStackInSlot(0, first); run(mill);
        helper.assertTrue(ItemStack.matches(first, mill.inputInv.getStackInSlot(0)) && mill.outputInv.getStackInSlot(0).isEmpty(),
                "wrong machine cannot advance or consume a pressing step");
        var press = com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe.getRecipe(helper.getLevel(), first,
                com.simibubi.create.AllRecipeTypes.PRESSING.getType(), com.simibubi.create.content.kinetics.press.PressingRecipe.class).orElseThrow();
        var pressed = press.value().rollResults(helper.getLevel().random).getFirst();
        mill.inputInv.setStackInSlot(0, pressed); run(mill);
        helper.assertTrue(mill.outputInv.getStackInSlot(0).is(Items.GOLD_INGOT), "rolling completes sequence after native pressing advances it");
        helper.succeed();
    }
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void rollingFinalOutcomesAndCapacity(GameTestHelper helper) {
        var mill = place(helper);
        for (int i = 0; i < 9; i++) mill.outputInv.setStackInSlot(i, new ItemStack(Items.EMERALD, 60));
        mill.inputInv.setStackInSlot(0, new ItemStack(Items.POTATO)); run(mill);
        helper.assertTrue(mill.inputInv.getStackInSlot(0).is(Items.POTATO), "final step waits for every possible weighted outcome to fit");
        for (int i = 0; i < 9; i++) mill.outputInv.setStackInSlot(i, ItemStack.EMPTY);
        boolean emerald = false, coal = false;
        for (int i = 0; i < 128; i++) {
            mill.inputInv.setStackInSlot(0, new ItemStack(Items.POTATO)); run(mill);
            var result = mill.outputInv.getStackInSlot(0);
            helper.assertTrue((result.is(Items.EMERALD) && result.getCount() == 3) || (result.is(Items.COAL) && result.getCount() == 2),
                    "native weighted assembly pool supplies counted final outcomes");
            emerald |= result.is(Items.EMERALD); coal |= result.is(Items.COAL);
            helper.assertTrue(!result.has(AllDataComponents.SEQUENCED_ASSEMBLY), "final outcome has no transitional progress");
            mill.outputInv.setStackInSlot(0, ItemStack.EMPTY);
        }
        helper.assertTrue(emerald && coal, "rolling uses both native weighted results"); helper.succeed();
    }
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void rollingKubeJsAssemblyBuilder(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse("cwi:example/rolling_assembly"));
        helper.assertTrue(recipe.isPresent() && recipe.get().value() instanceof SequencedAssemblyRecipe,
                "standard KubeJS rolling(output, input) works inside sequenced_assembly");
        var assembly = (SequencedAssemblyRecipe)recipe.orElseThrow().value();
        helper.assertTrue(assembly.getLoops() == 2 && assembly.getSequence().size() == 2
                && assembly.getSequence().getFirst().getAsAssemblyRecipe().supportsAssembly(), "KubeJS preserves mixed steps and loops");
        helper.succeed();
    }
    private static RollingMillBlockEntity place(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("createaddition:rolling_mill")));
        RollingMillBlockEntity mill = helper.getBlockEntity(new BlockPos(1, 1, 1)); mill.initialize();
        var axis = ((IRotate)mill.getBlockState().getBlock()).getRotationAxis(mill.getBlockState());
        var direction = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        var motorPos = new BlockPos(1, 1, 1).relative(direction);
        helper.setBlock(motorPos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, direction.getOpposite()));
        CreativeMotorBlockEntity motor = helper.getBlockEntity(motorPos); motor.generatedSpeed.setValue(256); motor.initialize();
        mill.attachKinetics(); helper.assertTrue(Math.abs(mill.getSpeed()) == 256 && !mill.isOverStressed(), "real motor drives rolling mill");
        return mill;
    }
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void testScriptRollingAndMixedAssemblyProcess(GameTestHelper helper) {
        var mill = place(helper);
        mill.inputInv.setStackInSlot(0, new ItemStack(Items.NAUTILUS_SHELL)); run(mill);
        helper.assertTrue(mill.inputInv.getStackInSlot(0).isEmpty()
                && mill.outputInv.getStackInSlot(0).is(Items.PRISMARINE_CRYSTALS)
                && mill.outputInv.getStackInSlot(0).getCount() == 2, "Test.js ordinary rolling returns counted outputs");
        if (ModList.get().isLoaded("kubejs_create")) {
            mill.outputInv.setStackInSlot(0, ItemStack.EMPTY);
            ItemStack current = new ItemStack(Items.PRISMARINE);
            for (int loop = 0; loop < 2; loop++) {
                mill.inputInv.setStackInSlot(0, current); run(mill);
                current = mill.outputInv.getStackInSlot(0).copy(); mill.outputInv.setStackInSlot(0, ItemStack.EMPTY);
                helper.assertTrue(current.has(AllDataComponents.SEQUENCED_ASSEMBLY), "Test.js rolling advances the current assembly step");
                var pressing = SequencedAssemblyRecipe.getRecipe(helper.getLevel(), current,
                        com.simibubi.create.AllRecipeTypes.PRESSING.getType(), com.simibubi.create.content.kinetics.press.PressingRecipe.class).orElseThrow();
                current = pressing.value().rollResults(helper.getLevel().random).getFirst();
            }
            helper.assertTrue(current.is(Items.HEART_OF_THE_SEA) && current.getCount() == 2
                    && !current.has(AllDataComponents.SEQUENCED_ASSEMBLY), "Test.js mixed rolling/pressing assembly finishes both loops");
        }
        helper.succeed();
    }
    private static void run(RollingMillBlockEntity mill) { if (mill.timer <= 0) mill.tick(); mill.timer = 1; mill.tick(); }
    private static SequencedAssemblyRecipe assembly(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse("cwi:compat_outputs/rolling_assembly"));
        helper.assertTrue(holder.isPresent() && holder.get().value() instanceof SequencedAssemblyRecipe,
                "rolling sequence must decode through Create's real assembly codec");
        return (SequencedAssemblyRecipe)holder.orElseThrow().value();
    }
}
