package net.quiwh.cwi.compat;

import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.quiwh.cwi.machine.recipe.IncubationDisplay;
import net.quiwh.cwi.machine.recipe.MachineRecipe;
import net.quiwh.cwi.machine.recipe.MachineRecipes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Native equivalents of the pack's authored KubeJS categories and furnace recipe UI. */
public final class MachineJeiCategory extends CreateRecipeCategory<MachineRecipe> {
    private static final ResourceLocation FURNACE_SLOT = ResourceLocation.fromNamespaceAndPath("cwi", "textures/gui/machine_recipe_slot.png");
    private static final IDrawable FURNACE_BACKGROUND = new IDrawable() {
        public int getWidth() { return 18; }
        public int getHeight() { return 18; }
        public void draw(GuiGraphics graphics, int x, int y) { graphics.blit(FURNACE_SLOT, x, y, 0, 0, 18, 18, 18, 18); }
    };
    private final MachineRecipes.Entry entry;
    private final MachineJeiAnimation animation;

    public static int width(MachineRecipes.Entry entry) { return entry == MachineRecipes.BLAST_FURNACE ? 176 : 178; }
    public static int height(MachineRecipes.Entry entry) {
        return entry == MachineRecipes.INCUBATING ? 136 : entry == MachineRecipes.MIXING ? 72 : 100;
    }
    public static String titleKey(MachineRecipes.Entry entry) {
        return entry == MachineRecipes.INCUBATING ? "cwi.recipe.incubating" : entry == MachineRecipes.MIXING
                ? "cwi.recipe.mixing_vessel" : "cwi.blast_furnace_processing";
    }

    public MachineJeiCategory(Info<MachineRecipe> info, MachineRecipes.Entry entry) {
        super(info); this.entry = entry; animation = new MachineJeiAnimation(entry);
    }

    @Override protected void setRecipe(IRecipeLayoutBuilder builder, MachineRecipe recipe, IFocusGroup focuses) {
        int inputCount = recipe.itemInputs().size() + recipe.fluidInputs().size();
        int outputCount = recipe.itemOutputs().size() + recipe.fluidOutputs().size();
        int index = 0;
        for (var input : recipe.itemInputs()) {
            var slot = slot(builder, RecipeIngredientRole.INPUT, index, entry == MachineRecipes.BLAST_FURNACE ? recipe.itemInputs().size() : inputCount, false)
                    .addItemStacks(Arrays.stream(input.ingredient().getItems()).map(stack -> stack.copyWithCount(input.count())).toList());
            decorate(slot, "in" + index++, input.chance(), input.perTick(), true);
        }
        int fluidIndex = 0;
        for (var input : recipe.fluidInputs()) {
            var slot = slot(builder, RecipeIngredientRole.INPUT, entry == MachineRecipes.BLAST_FURNACE ? fluidIndex++ : index,
                    entry == MachineRecipes.BLAST_FURNACE ? recipe.fluidInputs().size() : inputCount, true)
                    .setFluidRenderer(input.ingredient().amount(), false, 16, 16);
            for (var fluid : input.ingredient().getFluids()) slot.addFluidStack(fluid.getFluid(), fluid.getAmount(), fluid.getComponentsPatch());
            decorate(slot, "in" + index++, input.chance(), input.perTick(), true);
        }
        index = 0;
        for (var output : recipe.itemOutputs()) {
            var slot = slot(builder, RecipeIngredientRole.OUTPUT, index, outputCount, false).addItemStack(output.stack());
            decorate(slot, "out" + index++, output.chance(), output.perTick(), false);
            slot.setBackground(getRenderedSlot(output.chance()), -1, -1);
        }
        for (var output : recipe.fluidOutputs()) {
            var fluid = output.stack();
            var slot = slot(builder, RecipeIngredientRole.OUTPUT, index, outputCount, true)
                    .setFluidRenderer(fluid.getAmount(), false, 16, 16)
                    .addFluidStack(fluid.getFluid(), fluid.getAmount(), fluid.getComponentsPatch());
            decorate(slot, "out" + index++, output.chance(), output.perTick(), false);
            if (entry != MachineRecipes.BLAST_FURNACE) slot.setBackground(getRenderedSlot(output.chance()), -1, -1);
        }
        recipe.incubationDisplay().ifPresent(display -> preparation(builder, display));
    }

    private IRecipeSlotBuilder slot(IRecipeLayoutBuilder builder, RecipeIngredientRole role, int index, int count, boolean fluid) {
        boolean input = role == RecipeIngredientRole.INPUT;
        IRecipeSlotBuilder slot;
        if (entry == MachineRecipes.INCUBATING) {
            // Physical incubation consumes one sealed plate; unusually long output lists remain accessible below.
            slot = count <= (input ? 1 : 2) ? builder.addSlot(role, input ? 21 : 141 + index * 18, 90) : builder.addSlot(role);
        } else if (entry == MachineRecipes.MIXING) {
            int rowLength = Math.min(2, count - index / 2 * 2);
            int x = (input ? 18 : 142) - (rowLength - 1) * 9 + index % 2 * 18;
            slot = count <= 6 ? builder.addSlot(role, x, 48 - index / 2 * 18) : builder.addSlot(role);
        } else {
            int visible = input ? 4 : 2;
            slot = count <= visible ? builder.addSlot(role, (input ? 6 : 136) + index * 18, fluid ? 27 : 6) : builder.addSlot(role);
        }
        return slot.setBackground(entry == MachineRecipes.BLAST_FURNACE ? FURNACE_BACKGROUND : getRenderedSlot(), -1, -1);
    }

    private static void preparation(IRecipeLayoutBuilder builder, IncubationDisplay display) {
        builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStack(display.microbe());
        builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).addItemStack(display.microbe());
        for (int index = 0; index < display.steps().size(); index++) {
            var step = display.steps().get(index);
            var slot = (display.steps().size() <= 6 ? builder.addSlot(RecipeIngredientRole.RENDER_ONLY, 50 + index * 20, 16)
                    : builder.addSlot(RecipeIngredientRole.RENDER_ONLY)).setSlotName("feed" + index).setBackground(getRenderedSlot(), -1, -1);
            if (step.type() == IncubationDisplay.Type.FILLING) {
                var fluid = step.displayFluid();
                slot.setFluidRenderer(fluid.getAmount(), false, 16, 16).addFluidStack(fluid.getFluid(), fluid.getAmount(), fluid.getComponentsPatch());
                builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addFluidStack(fluid.getFluid(), fluid.getAmount(), fluid.getComponentsPatch());
            } else {
                slot.addItemStack(step.displayItem());
                builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStack(step.displayItem());
            }
            int stepNumber = index + 1;
            slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("cwi.jei.incubator.feed_step", stepNumber,
                    Component.translatable(step.type() == IncubationDisplay.Type.FILLING ? "block.create.spout" : "block.create.deployer"), step.count())
                    .withStyle(ChatFormatting.GRAY)));
        }
    }

    private static IRecipeSlotBuilder decorate(IRecipeSlotBuilder slot, String name, float chance, boolean perTick, boolean input) {
        return slot.setSlotName(name).addRichTooltipCallback((view, tooltip) -> {
            if (chance != 1) tooltip.add(Component.translatable(input ? "cwi.recipe.consume_chance" : "cwi.recipe.output_chance",
                    String.format(Locale.ROOT, "%.2f", chance * 100)).withStyle(input ? ChatFormatting.GRAY : ChatFormatting.GOLD));
            if (perTick) tooltip.add(Component.translatable("cwi.recipe.per_tick").withStyle(ChatFormatting.GRAY));
        });
    }

    @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<MachineRecipe> holder, IFocusGroup focuses) {
        var recipe = holder.value();
        var slots = builder.getRecipeSlots();
        if (entry == MachineRecipes.MIXING) {
            if (recipe.itemInputs().size() + recipe.fluidInputs().size() > 6)
                builder.addScrollGridWidget(slots.getSlots(RecipeIngredientRole.INPUT), 2, 3).setPosition(9, 12);
            if (recipe.itemOutputs().size() + recipe.fluidOutputs().size() > 6)
                builder.addScrollGridWidget(slots.getSlots(RecipeIngredientRole.OUTPUT), 2, 3).setPosition(133, 12);
        } else if (entry == MachineRecipes.INCUBATING) {
            if (recipe.itemOutputs().size() > 2)
                builder.addScrollGridWidget(slots.getSlots(RecipeIngredientRole.OUTPUT), 2, 2).setPosition(133, 90);
            if (recipe.incubationDisplay().map(display -> display.steps().size() > 6).orElse(false))
                builder.addScrollGridWidget(slots.getSlots(RecipeIngredientRole.RENDER_ONLY), 6, 1).setPosition(50, 16);
        } else {
            if (recipe.itemInputs().size() > 4)
                builder.addScrollGridWidget(slots.getSlots(RecipeIngredientRole.INPUT).stream().filter(slot -> slot.getSlotName().orElse("").matches("in[0-9]+") &&
                        Integer.parseInt(slot.getSlotName().orElseThrow().substring(2)) < recipe.itemInputs().size()).toList(), 4, 1).setPosition(6, 6);
            if (recipe.fluidInputs().size() > 4)
                builder.addScrollGridWidget(slots.getSlots(RecipeIngredientRole.INPUT).stream().filter(slot -> slot.getSlotName().orElse("").startsWith("in") &&
                        Integer.parseInt(slot.getSlotName().orElseThrow().substring(2)) >= recipe.itemInputs().size()).toList(), 4, 1).setPosition(6, 27);
            if (recipe.fluidOutputs().size() > 2)
                builder.addScrollGridWidget(slots.getSlots(RecipeIngredientRole.OUTPUT), 2, 1).setPosition(136, 27);
        }
    }

    @Override protected void draw(MachineRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        if (entry == MachineRecipes.BLAST_FURNACE) {
            for (int i = 0; i < 4; i++) FURNACE_BACKGROUND.draw(graphics, 5 + i * 18, 5);
            for (int i = 0; i < Math.max(3, Math.min(4, recipe.fluidInputs().size())); i++) FURNACE_BACKGROUND.draw(graphics, 5 + i * 18, 26);
            FURNACE_BACKGROUND.draw(graphics, 135, 26); FURNACE_BACKGROUND.draw(graphics, 153, 26);
            return;
        }
        boolean incubator = entry == MachineRecipes.INCUBATING;
        AllGuiTextures.JEI_SHADOW.render(graphics, 65, incubator ? 81 : 39);
        AllGuiTextures.JEI_LONG_ARROW.render(graphics, 54, incubator ? 93 : 51);
        animation.draw(graphics, 56, incubator ? 75 : 33);
        if (!incubator) return;
        var font = Minecraft.getInstance().font;
        recipe.incubationDisplay().ifPresent(display -> {
            if (display.steps().size() <= 6) for (int index = 0; index < display.steps().size(); index++)
                drawAuthoredText(graphics, font, Component.literal(Integer.toString(index + 1)).withStyle(ChatFormatting.GOLD), 54 + index * 20, 36, 16, 0xFFFFFF);
            var pose = graphics.pose();
            pose.pushPose(); pose.translate(10, 10, 0); pose.scale(1.5f, 1.5f, 1);
            graphics.renderItem(display.microbe(), 0, 0); pose.popPose();
            String yield = "x" + display.yieldCount();
            pose.pushPose(); pose.translate(0, 0, 200);
            drawAuthoredText(graphics, font, Component.literal(yield).withStyle(ChatFormatting.GOLD), 34 - font.width(yield), 28, 24, 0xFFFFFF);
            pose.popPose();
        });
        drawAuthoredText(graphics, font, Component.literal(String.format(Locale.ROOT, "%.1f min", recipe.getProcessingDuration() / 1200d))
                .withStyle(ChatFormatting.GOLD), 130, 58, 48, 0xFFFFFF);
        drawAuthoredText(graphics, font, Component.literal(format(Math.max(64, recipe.minimumRpm())) + " RPM").withStyle(ChatFormatting.GOLD), 130, 71, 48, 0xFFFFFF);
    }

    @Override protected List<Component> getTooltipStrings(MachineRecipe recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        boolean hover = entry == MachineRecipes.BLAST_FURNACE ? mouseY >= 45 : mouseX >= 54 && mouseX <= 119 && mouseY >= 45;
        if (!hover) return List.of();
        var tooltip = new ArrayList<Component>();
        tooltip.add(Component.translatable(entry == MachineRecipes.MIXING ? "cwi.recipe.time_at_256" : "cwi.recipe.time", recipe.getProcessingDuration()));
        if (entry != MachineRecipes.BLAST_FURNACE) tooltip.add(recipe.minimumRpm() > 0 || entry == MachineRecipes.INCUBATING
                ? Component.translatable("cwi.recipe.minimum_rpm", format(Math.max(recipe.minimumRpm(), entry == MachineRecipes.INCUBATING ? 64 : 0)))
                : Component.translatable("cwi.recipe.rotation_required"));
        if (recipe.minimumHeat() > 0) tooltip.add(Component.translatable("cwi.recipe.minimum_temperature", recipe.minimumHeat()));
        if (recipe.getRequiredHeat() != HeatCondition.NONE) tooltip.add(Component.translatable(recipe.getRequiredHeat().getTranslationKey()));
        return tooltip;
    }

    private static void drawAuthoredText(GuiGraphics graphics, net.minecraft.client.gui.Font font, Component text,
                                         int x, int y, int maxWidth, int color) {
        // The pack's shared drawWordWrap helper uses shadows and lineHeight + 1 spacing.
        for (var line : font.split(text, maxWidth)) {
            graphics.drawString(font, line, x, y, color, true);
            y += font.lineHeight + 1;
        }
    }

    private static String format(float value) { return value == (int) value ? Integer.toString((int) value) : Float.toString(value); }
}
