package net.quiwh.cwi.compat.jei;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Create's ordinary whole-percent presentation, shared by item drains and weighted pools. */
public final class ProcessingChance {
    public static Component tooltip(double chance) {
        return Component.translatable("create.recipe.processing.chance", percent(chance)).withStyle(ChatFormatting.GOLD);
    }
    public static String percent(double chance) {
        return chance < .01 ? "<1" : chance > .99 ? ">99" : Long.toString(Math.round(chance * 100));
    }
    private ProcessingChance() { }
}
