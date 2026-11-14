package net.quiwh.cwi.compat.jei;

import com.mojang.math.Axis;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import java.util.LinkedHashMap;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.quiwh.cwi.compat.jei.ProcessingChance;
import net.quiwh.cwi.interaction.BlockSelector;
import net.quiwh.cwi.interaction.InteractionRecipe;
import net.quiwh.cwi.interaction.InteractionRecipes;
import net.quiwh.cwi.interaction.InteractionRules;

/** The pack's authored budding layout, shared by world-block filling and crystal growth. */
public final class WorldSpoutingJeiCategory extends CreateRecipeCategory<InteractionRecipe> {
    public static final int WIDTH = 178, HEIGHT = 156;
    private static final int VISIBLE_OUTPUTS = 6;
    private final WorldSpoutingJeiAnimation spout = new WorldSpoutingJeiAnimation();

    public WorldSpoutingJeiCategory(Info<InteractionRecipe> info) { super(info); }

    public static String titleKey(InteractionRecipes.Kind kind) {
        return kind == InteractionRecipes.Kind.BUDDING_CATALYSIS ? "cwi.recipe.budding_growth" : "cwi.recipe.block_filling";
    }

    private static BlockSelector input(InteractionRecipe recipe) {
        return recipe.rule() instanceof InteractionRules.Budding growth ? growth.input() : ((InteractionRules.Spouting) recipe.rule()).input();
    }

    private static SizedFluidIngredient fluid(InteractionRecipe recipe) {
        return recipe.rule() instanceof InteractionRules.Budding growth ? growth.fluid() : ((InteractionRules.Spouting) recipe.rule()).fluid();
    }

    private static List<Block> outputs(InteractionRecipe recipe) {
        return recipe.rule() instanceof InteractionRules.Budding growth ? growth.stages()
                : ((InteractionRules.Spouting) recipe.rule()).outputs().stream().distinct().toList();
    }

    @Override protected void setRecipe(IRecipeLayoutBuilder builder, InteractionRecipe recipe, IFocusGroup focuses) {
        addFluidSlot(builder, 18, 100, fluid(recipe)).setSlotName("fluid");
        builder.addSlot(RecipeIngredientRole.INPUT, 18, 130).setSlotName("block")
                .setBackground(getRenderedSlot(), -1, -1).addItemStacks(input(recipe).items());
        var outputs = outputs(recipe);
        var weights = new LinkedHashMap<Block, Integer>();
        if (recipe.rule() instanceof InteractionRules.Spouting filling)
            filling.outputs().forEach(block -> weights.merge(block, 1, Integer::sum));
        for (int index = 0; index < outputs.size(); index++) {
            int stage = index + 1;
            boolean budding = recipe.rule() instanceof InteractionRules.Budding;
            double chance = budding ? ((InteractionRules.Budding) recipe.rule()).chance()
                    : (double) weights.get(outputs.get(index)) / ((InteractionRules.Spouting) recipe.rule()).outputs().size();
            int count = budding ? ((InteractionRules.Budding) recipe.rule()).attempts() : 1;
            var slot = (outputs.size() <= VISIBLE_OUTPUTS
                    ? builder.addSlot(RecipeIngredientRole.OUTPUT, 142, 130 - index * 22)
                    : builder.addSlot(RecipeIngredientRole.OUTPUT))
                    .setSlotName("stage" + stage).setBackground(getRenderedSlot((float) chance), -1, -1)
                    .addItemStack(new ItemStack(outputs.get(index), count));
            slot.addRichTooltipCallback((view, tooltip) -> {
                if (budding) {
                    tooltip.add(Component.translatable("cwi.jei.spouting.growth_stage", stage, outputs.size()).withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.translatable("cwi.jei.spouting.growth_attempts", count, percent(chance)).withStyle(ChatFormatting.GOLD));
                    tooltip.add(Component.translatable("cwi.jei.spouting.growth_preview").withStyle(ChatFormatting.GRAY));
                } else if (outputs.size() > 1) {
                    tooltip.add(Component.translatable("cwi.jei.spouting.alternative", percent(chance)).withStyle(ChatFormatting.GOLD));
                }
            });
        }
    }

    @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<InteractionRecipe> holder, IFocusGroup focuses) {
        if (outputs(holder.value()).size() > VISIBLE_OUTPUTS)
            builder.addScrollGridWidget(builder.getRecipeSlots().getSlots(RecipeIngredientRole.OUTPUT), 1, VISIBLE_OUTPUTS).setPosition(142, 20);
    }

    @Override protected void draw(InteractionRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_SHADOW.render(graphics, 65, 109);
        AllGuiTextures.JEI_LONG_ARROW.render(graphics, 54, 135);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(56, 105, 0);
        pose.mulPose(Axis.XP.rotationDegrees(-12.5f));
        pose.mulPose(Axis.YP.rotationDegrees(22.5f));
        var block = slots.findSlotByName("block").flatMap(slot -> slot.getDisplayedItemStack())
                .filter(stack -> stack.getItem() instanceof BlockItem)
                .map(stack -> ((BlockItem) stack.getItem()).getBlock()).orElseGet(() ->
                        net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream()
                                .filter(value -> input(recipe).test(value.defaultBlockState())).findFirst().orElse(null));
        if (block != null)
            AnimatedKinetics.defaultBlockElement(block.defaultBlockState()).rotateBlock(0, 180, 0)
                    .atLocal(0, 0, 2).scale(24).render(graphics);
        FluidStack displayedFluid = slots.findSlotByName("fluid")
                .flatMap(slot -> slot.getDisplayedIngredient(NeoForgeTypes.FLUID_STACK)).orElseGet(() -> {
                    var fluids = fluid(recipe).getFluids();
                    return fluids.length == 0 ? FluidStack.EMPTY : fluids[0];
                });
        pose.translate(0, -48, 48);
        spout.draw(graphics, displayedFluid);
        pose.popPose();
    }

    private static String percent(double chance) {
        return chance == 0 ? "0" : chance == 1 ? "100" : ProcessingChance.percent(chance);
    }
}
