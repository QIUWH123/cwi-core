package net.quiwh.cwi.machine;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/** Native components keep common machine data safe on dedicated servers as well as clients. */
final class MachineGoggles {
    static void heading(List<Component> tooltip, Component text) {
        tooltip.add(Component.literal("    ").append(text));
    }
    static void line(List<Component> tooltip, Component text) {
        tooltip.add(Component.literal("     ").append(text.copy().withStyle(ChatFormatting.GRAY)));
    }
    static String number(float value) {
        return value == (int)value ? Integer.toString((int)value) : Float.toString(value);
    }
    static void progress(List<Component> tooltip, int progress, int duration) {
        if (duration > 0) line(tooltip, Component.translatable("cwi.machine.progress", Math.clamp(100L * progress / duration, 0, 100)));
    }
    static void contents(List<Component> tooltip, IItemHandler items, IFluidHandler fluids) {
        boolean empty = true;
        for (int slot = 0; slot < items.getSlots(); slot++) {
            var stack = items.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (empty) heading(tooltip, Component.translatable("cwi.machine.contents"));
            line(tooltip, stack.getHoverName().copy().append(Component.literal(" x" + stack.getCount()).withStyle(ChatFormatting.GREEN)));
            empty = false;
        }
        for (int tank = 0; tank < fluids.getTanks(); tank++) {
            var stack = fluids.getFluidInTank(tank);
            if (stack.isEmpty()) continue;
            if (empty) heading(tooltip, Component.translatable("cwi.machine.contents"));
            line(tooltip, stack.getHoverName().copy().append(Component.literal(" " + stack.getAmount() + " ").withStyle(ChatFormatting.BLUE))
                    .append(Component.translatable("create.generic.unit.millibuckets").withStyle(ChatFormatting.BLUE)));
            empty = false;
        }
        if (empty && (items.getSlots() > 0 || fluids.getTanks() > 0))
            line(tooltip, Component.translatable("cwi.machine.empty"));
    }
    private MachineGoggles() {}
}
