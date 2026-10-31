package net.quiwh.cwi.compat;

import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.quiwh.cwi.equipment.EquipmentUpgradeRecipe;
import net.quiwh.cwi.equipment.UpgradeIngredient;

import java.util.List;

/** The pack's authored 178x72 upgrade layout, driven by native synced recipes. */
public final class EquipmentUpgradeJeiCategory extends CreateRecipeCategory<EquipmentUpgradeRecipe> {
    public EquipmentUpgradeJeiCategory(Info<EquipmentUpgradeRecipe> info) { super(info); }
    private static List<UpgradeIngredient> column(EquipmentUpgradeRecipe recipe, boolean consumed) {
        return recipe.ingredients().stream().filter(i -> i.consume() == consumed).toList();
    }
    @Override protected void setRecipe(IRecipeLayoutBuilder builder, EquipmentUpgradeRecipe recipe, IFocusGroup focuses) {
        builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addIngredients(recipe.input());
        var minecraft = Minecraft.getInstance();
        var registries = minecraft == null || minecraft.level == null ? null : minecraft.level.registryAccess();
        if (registries != null) builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).addItemStack(recipe.getResultItem(registries));
        for (boolean consumed : List.of(false, true)) {
            var column = column(recipe, consumed);
            for (int index = 0; index < column.size(); index++) {
                var ingredient = column.get(index);
                var slot = (column.size() > 3 ? builder.addSlot(RecipeIngredientRole.INPUT)
                        : builder.addSlot(RecipeIngredientRole.INPUT, consumed ? 32 : 10, 10 + index * 18))
                        .setSlotName((consumed ? "consumed_" : "tool_") + index).setBackground(getRenderedSlot(), -1, -1);
                for (var stack : ingredient.ingredient().getItems()) slot.addItemStack(stack.copyWithCount(ingredient.count()));
                if (!consumed) slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(
                        "cwi.recipe.upgrade_tool_wear", ingredient.damage()).withStyle(ChatFormatting.GRAY)));
            }
        }
    }
    @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<EquipmentUpgradeRecipe> holder, IFocusGroup focuses) {
        for (boolean consumed : List.of(false, true)) {
            if (column(holder.value(), consumed).size() <= 3) continue;
            String prefix = consumed ? "consumed_" : "tool_";
            var slots = builder.getRecipeSlots().getSlots(RecipeIngredientRole.INPUT).stream()
                    .filter(slot -> slot.getSlotName().orElse("").startsWith(prefix)).toList();
            builder.addScrollGridWidget(slots, 1, 3).setPosition(consumed ? 32 : 10, 10);
        }
    }
    @Override protected void draw(EquipmentUpgradeRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_SHADOW.render(graphics, 63, 48);
        ItemStack[] equipment = recipe.input().getItems();
        if (equipment.length > 0) {
            var stack = equipment[(int)(System.currentTimeMillis() / 1000 % equipment.length)];
            graphics.pose().pushPose(); graphics.pose().translate(89, 38, 0); graphics.pose().scale(2, 2, 1);
            graphics.renderItem(stack, -8, -8); graphics.pose().popPose();
        }
        wrap(graphics, recipe.enchantment().value().description().copy().withStyle(ChatFormatting.AQUA), 64, 4, 120, 0xffdba6);
        wrap(graphics, Component.translatable("cwi.recipe.upgrade_max_level", recipe.maxLevel()), 120, 24, 90, 0xcccccc);
        wrap(graphics, Component.translatable("cwi.recipe.upgrade_damage", Math.round(recipe.durabilityMultiplier() * 100)), 120, 36, 90, 0xcccccc);
    }
    private static void wrap(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        var font = Minecraft.getInstance().font;
        for (var line : font.split(text, width)) {
            graphics.drawString(font, line, x, y, color, true); y += font.lineHeight + 1;
        }
    }
}
