package net.quiwh.cwi.shadow;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Client render rules without dependencies on Shadow Drop, KubeJS, or rendering classes. */
public final class ShadowDropRules {
    public record Style(Integer alpha, Integer rgb) {
        public static final Style INHERIT = new Style(null, null);
        public Style overlay(Style other) {
            return new Style(other.alpha != null ? other.alpha : alpha, other.rgb != null ? other.rgb : rgb);
        }
    }

    private static volatile Style defaults = Style.INHERIT;
    private static volatile Map<Item, Style> items = Map.of();
    private static final ThreadLocal<Style> RENDER_STYLE = new ThreadLocal<>();

    private ShadowDropRules() {}

    public static void defaults(Style replacement) { defaults = replacement; }
    public static Style itemStyle(Item item) { return items.getOrDefault(item, Style.INHERIT); }
    public static synchronized void itemStyle(Item item, Style style) {
        var copy = new IdentityHashMap<>(items);
        if (style.equals(Style.INHERIT)) copy.remove(item);
        else copy.put(item, style);
        items = Collections.unmodifiableMap(copy);
    }
    public static Style resolve(ItemStack stack) {
        var own = itemStyle(stack.getItem());
        return own.equals(Style.INHERIT) ? defaults : defaults.overlay(own);
    }

    /** Scope the stackless native color lookup to this render, including nested item renders. */
    public static Style enterRender(ItemStack stack) {
        var previous = RENDER_STYLE.get();
        RENDER_STYLE.set(resolve(stack));
        return previous;
    }
    public static void leaveRender(Style previous) {
        if (previous == null) RENDER_STYLE.remove();
        else RENDER_STYLE.set(previous);
    }
    public static Integer renderColor() {
        var current = RENDER_STYLE.get();
        return (current == null ? defaults : current).rgb;
    }

    public static int alpha(double amount) {
        if (!Double.isFinite(amount) || amount < 0 || amount > 1)
            throw new IllegalArgumentException("Shadow amount must be a finite number from 0 to 1");
        return (int) Math.round(amount * 255);
    }
    public static int color(String hex) {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}"))
            throw new IllegalArgumentException("Shadow color must be #RRGGBB");
        return Integer.parseInt(hex.substring(1), 16);
    }
    public static int color(double rgb) {
        if (!Double.isFinite(rgb) || rgb != Math.rint(rgb) || rgb < 0 || rgb > 0xFFFFFF)
            throw new IllegalArgumentException("Shadow color must be an RGB integer from 0x000000 to 0xFFFFFF");
        return (int) rgb;
    }
}
