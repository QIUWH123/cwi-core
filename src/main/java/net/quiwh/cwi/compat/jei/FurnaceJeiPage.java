package net.quiwh.cwi.compat.jei;

import static com.simibubi.create.compat.jei.category.CreateRecipeCategory.getRenderedSlot;

import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.quiwh.cwi.machine.FurnaceHeat;
import net.quiwh.cwi.machine.recipe.MachineRecipe;
import net.quiwh.cwi.machine.recipe.MachineRecipes;

/** Furnace-only layout: solid IO above fluid IO, with a cutaway of the actual structure. */
final class FurnaceJeiPage {
    static final int HEIGHT = 126;
    private static final FurnaceJeiAnimation ANIMATION = new FurnaceJeiAnimation();

    static void setRecipe(IRecipeLayoutBuilder builder, MachineRecipe recipe) {
        int itemCount = recipe.itemOutputs().size() + recipe.outputPools().size();
        for (int i = 0; i < recipe.itemInputs().size(); i++) {
            var input = recipe.itemInputs().get(i);
            var slot = slot(builder, true, false, i, recipe.itemInputs().size())
                    .addItemStacks(Arrays.stream(input.ingredient().getItems()).map(stack -> stack.copyWithCount(input.count())).toList());
            MachineJeiSlots.decorate(MachineRecipes.BLAST_FURNACE, slot, "in_item" + i, input.chance(), input.settings(), true, input.count(), false);
        }
        for (int i = 0; i < recipe.fluidInputs().size(); i++) {
            var input = recipe.fluidInputs().get(i);
            var slot = slot(builder, true, true, i, recipe.fluidInputs().size()).setFluidRenderer(input.ingredient().amount(), false, 16, 16);
            for (var fluid : input.ingredient().getFluids()) slot.addFluidStack(fluid.getFluid(), fluid.getAmount(), fluid.getComponentsPatch());
            MachineJeiSlots.decorate(MachineRecipes.BLAST_FURNACE, slot, "in_fluid" + i, input.chance(), input.settings(), true, input.ingredient().amount(), true);
        }
        for (int i = 0; i < recipe.itemOutputs().size(); i++) {
            var output = recipe.itemOutputs().get(i);
            var slot = slot(builder, false, false, i, itemCount).addItemStack(output.stack());
            MachineJeiSlots.decorate(MachineRecipes.BLAST_FURNACE, slot, "out_item" + i, output.chance(), output.settings(), false, output.stack().getCount(), false);
        }
        for (int i = 0; i < recipe.fluidOutputs().size(); i++) {
            var output = recipe.fluidOutputs().get(i); var fluid = output.stack();
            var slot = slot(builder, false, true, i, recipe.fluidOutputs().size()).setFluidRenderer(fluid.getAmount(), false, 16, 16)
                    .addFluidStack(fluid.getFluid(), fluid.getAmount(), fluid.getComponentsPatch());
            MachineJeiSlots.decorate(MachineRecipes.BLAST_FURNACE, slot, "out_fluid" + i, output.chance(), output.settings(), false, fluid.getAmount(), true);
        }
        for (int i = 0; i < recipe.outputPools().size(); i++) {
            var slot = slot(builder, false, false, recipe.itemOutputs().size() + i, itemCount)
                    .setSlotName("out_pool" + i).setBackground(getRenderedSlot(0f), -1, -1);
            MachineJeiSlots.pool(slot, recipe.outputPools().get(i));
        }
    }

    private static IRecipeSlotBuilder slot(IRecipeLayoutBuilder builder, boolean input, boolean fluid, int index, int count) {
        var role = input ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT;
        return (count > 4 ? builder.addSlot(role) : builder.addSlot(role, (input ? 8 : 134) + index % 2 * 18,
                (fluid ? 57 : 12) + index / 2 * 18)).setBackground(getRenderedSlot(), -1, -1);
    }

    static void extras(IRecipeExtrasBuilder builder, MachineRecipe recipe) {
        var slots = builder.getRecipeSlots();
        for (boolean input : new boolean[]{true, false}) for (boolean fluid : new boolean[]{false, true}) {
            int count = input ? fluid ? recipe.fluidInputs().size() : recipe.itemInputs().size()
                    : fluid ? recipe.fluidOutputs().size() : recipe.itemOutputs().size() + recipe.outputPools().size();
            if (count <= 4) continue;
            String prefix = input ? fluid ? "in_fluid" : "in_item" : fluid ? "out_fluid" : "out_";
            builder.addScrollGridWidget(slots.getSlots(input ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT).stream()
                    .filter(slot -> slot.getSlotName().orElse("").startsWith(prefix)
                            && (input || fluid || !slot.getSlotName().orElse("").startsWith("out_fluid"))).toList(), 2, 2)
                    .setPosition(input ? 8 : 134, fluid ? 57 : 12);
        }
    }

    static int minimumHeat(MachineRecipe recipe) {
        return Math.max(recipe.minimumHeat(), recipe.getRequiredHeat() == HeatCondition.SUPERHEATED ? 1900
                : recipe.getRequiredHeat() == HeatCondition.HEATED ? 1200 : 0);
    }

    static void draw(GuiGraphics graphics, MachineRecipe recipe) {
        AllGuiTextures.JEI_SHADOW.render(graphics, 64, 65);
        ANIMATION.draw(graphics, 90, 50);
        AllGuiTextures.JEI_LONG_ARROW.render(graphics, 57, 78);
        var font = Minecraft.getInstance().font;
        Component heat = Component.translatable(recipe.maximumHeat() == Integer.MAX_VALUE ? "cwi.jei.furnace.minimum" : "cwi.jei.furnace.range",
                minimumHeat(recipe), recipe.maximumHeat());
        graphics.drawCenteredString(font, heat.copy().withStyle(ChatFormatting.GOLD), 89, 99, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("cwi.jei.furnace.base_time", seconds(recipe.getProcessingDuration()))
                .withStyle(ChatFormatting.AQUA), 89, 113, 0xFFFFFF);
    }

    static List<Component> tooltip(MachineRecipe recipe, double x, double y) {
        if (x < 54 || x > 124 || y < 0 || y >= HEIGHT) return List.of();
        var tooltip = new ArrayList<Component>();
        tooltip.add(Component.translatable("cwi.recipe.time", recipe.getProcessingDuration()));
        tooltip.add(Component.translatable("cwi.recipe.furnace_parallel"));
        tooltip.add(Component.translatable("cwi.jei.furnace.per_copy").withStyle(ChatFormatting.GRAY));
        if (minimumHeat(recipe) > 0) tooltip.add(Component.translatable("cwi.recipe.minimum_temperature", minimumHeat(recipe)));
        if (recipe.maximumHeat() != Integer.MAX_VALUE) tooltip.add(Component.translatable("cwi.recipe.maximum_temperature", recipe.maximumHeat()));
        int reference = Math.min(recipe.maximumHeat(), Math.min(2400, Math.max(1200, minimumHeat(recipe))));
        tooltip.add(Component.translatable("cwi.jei.furnace.time_at", seconds(Math.max(1, (int)Math.floor(
                recipe.getProcessingDuration() * FurnaceHeat.durationFactor(reference)))), reference).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("cwi.jei.furnace.sampled_heat").withStyle(ChatFormatting.GRAY));
        return tooltip;
    }

    private static String seconds(int ticks) { return java.math.BigDecimal.valueOf(ticks).divide(java.math.BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString(); }
    private FurnaceJeiPage() {}
}
