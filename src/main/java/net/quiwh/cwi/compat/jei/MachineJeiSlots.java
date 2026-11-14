package net.quiwh.cwi.compat.jei;

import static com.simibubi.create.compat.jei.category.CreateRecipeCategory.getRenderedSlot;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.quiwh.cwi.machine.recipe.EntrySettings;
import net.quiwh.cwi.machine.recipe.MachineRecipes;

/** Shared machine slot frames and ingredient hover styling. Layout stays in the category. */
final class MachineJeiSlots {
    private static final IDrawable CATALYST_FRAME = externalFrame("catalyst_slot", 0xFFDCB64A);
    private static final IDrawable PERIODIC_FRAME = externalFrame("per_tick_slot", 0xFF60BED5);
    private static IDrawable externalFrame(String name, int color) {
        var sprite = ResourceLocation.fromNamespaceAndPath("cwi", "jei/" + name);
        return new IDrawable() {
            public int getWidth() { return 18; } public int getHeight() { return 18; }
            public void draw(GuiGraphics graphics, int x, int y) {
                // The GUI atlas applies .mcmeta animation and is rebuilt on resource reload.
                if (Minecraft.getInstance().getGuiSprites().getSprite(sprite).contents().name().equals(sprite))
                    graphics.blitSprite(sprite, x, y, 18, 18);
                else {
                    AllGuiTextures.JEI_SLOT.render(graphics, x, y);
                    graphics.fill(x, y, x + 18, y + 1, color); graphics.fill(x, y + 17, x + 18, y + 18, color);
                    graphics.fill(x, y + 1, x + 1, y + 17, color); graphics.fill(x + 17, y + 1, x + 18, y + 17, color);
                }
            }
        };
    }
    static final IDrawable HIDDEN_POOL = new IDrawable() {
        public int getWidth() { return 16; } public int getHeight() { return 16; }
        public void draw(GuiGraphics graphics, int x, int y) {
            var font = Minecraft.getInstance().font; var text = Component.literal("?").withStyle(ChatFormatting.BOLD);
            graphics.drawString(font, text, x + 7 - font.width(text) / 2, y + 4, 0xEFEFEF);
        }
    };
    static IRecipeSlotBuilder decorate(MachineRecipes.Entry entry, IRecipeSlotBuilder slot, String name, float chance, EntrySettings settings, boolean input, int amount, boolean fluid) {
        if (entry == MachineRecipes.MIXING || entry == MachineRecipes.BLAST_FURNACE) {
            if (settings.periodic()) slot.setBackground(PERIODIC_FRAME, -1, -1);
            else if (settings.catalyst() || settings.tool()) slot.setBackground(CATALYST_FRAME, -1, -1);
            else if (chance != 1) slot.setBackground(getRenderedSlot(chance), -1, -1);
        }
        return slot.setSlotName(name).addRichTooltipCallback((view, tooltip) -> {
            if (input && (settings.catalyst() || chance == 0 && !settings.tool())) tooltip.add(Component.translatable("cwi.recipe.catalyst").withStyle(ChatFormatting.GRAY));
            else if (chance != 1) tooltip.add(input
                    ? Component.translatable("cwi.recipe.consume_chance", ProcessingChance.percent(chance)).withStyle(ChatFormatting.GRAY)
                    : ProcessingChance.tooltip(chance));
            if (settings.damage() > 0) tooltip.add(Component.translatable("cwi.recipe.damage_cost", settings.damage()).withStyle(ChatFormatting.GRAY));
            if (settings.energy() > 0) tooltip.add(Component.translatable("cwi.recipe.energy_cost", settings.energy()).withStyle(ChatFormatting.GRAY));
            if (settings.periodic()) {
                if (settings.interval() == 1 && settings.clock() == EntrySettings.Clock.WORKING)
                    tooltip.add(Component.translatable(fluid ? "cwi.recipe.fluid_rate" : "cwi.recipe.item_rate", amount).withStyle(ChatFormatting.GRAY));
                else tooltip.add(Component.translatable(settings.clock() == EntrySettings.Clock.RECIPE ? "cwi.recipe.recipe_interval" : "cwi.recipe.working_interval", settings.interval()).withStyle(ChatFormatting.GRAY));
            }
        });
    }

    static void pool(IRecipeSlotBuilder slot, net.quiwh.cwi.machine.recipe.OutputPool pool) {
        if (pool.showOutputs()) slot.addItemStacks(pool.entries().stream().map(net.quiwh.cwi.machine.recipe.OutputPool.Entry::stack).toList());
        else slot.setOverlay(HIDDEN_POOL, 0, 0);
        slot.addRichTooltipCallback((view, tooltip) -> {
            if (!pool.showOutputs()) tooltip.add(Component.translatable("create.recipe.assembly.junk"));
            else if (view != null) view.getDisplayedItemStack().ifPresent(displayed -> {
                double weight = pool.entries().stream().filter(e -> net.minecraft.world.item.ItemStack.matches(e.stack(), displayed))
                        .mapToDouble(net.quiwh.cwi.machine.recipe.OutputPool.Entry::weight).sum();
                tooltip.add(ProcessingChance.tooltip(weight / pool.totalWeight()));
            });
        });
    }
    private MachineJeiSlots() {}
}
