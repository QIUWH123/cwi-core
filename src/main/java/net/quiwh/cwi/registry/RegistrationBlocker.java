package net.quiwh.cwi.registry;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Common registry state. Deliberately independent of KubeJS and mod constructors. */
public final class RegistrationBlocker {
    private static volatile Map<ResourceLocation, Set<ResourceLocation>> rules = Map.of();
    private static volatile boolean configured;
    private static final Map<ResourceKey<?>, Holder.Reference<?>> detached = new ConcurrentHashMap<>();

    private RegistrationBlocker() {}

    public static boolean isConfigured() { return configured; }

    public static synchronized void configure(Set<ResourceLocation> blocks, Set<ResourceLocation> items) {
        if (configured) throw new IllegalStateException("Registration rules require a full game restart");
        rules = Map.of(Registries.BLOCK.location(), Set.copyOf(blocks), Registries.ITEM.location(), Set.copyOf(items));
        configured = true;
    }

    public static boolean blocks(ResourceKey<?> key) {
        return blocks(key.registry(), key.location());
    }

    public static boolean blocks(ResourceLocation registry, @Nullable ResourceLocation id) {
        if (net.quiwh.cwi.compat.VintageMaterialPolicy.blocks(registry, id)) return true;
        var ids = rules.get(registry);
        return id != null && ids != null && ids.contains(id);
    }

    public static boolean blocks(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItemHolder().unwrapKey().map(key -> blocks(Registries.ITEM.location(), key.location())).orElse(false);
    }

    public static <T> void remember(ResourceKey<T> key, Holder.Reference<T> holder) {
        if (detached.putIfAbsent(key, holder) != null)
            throw new IllegalStateException("Duplicate blocked registration: " + key);
        LogUtils.getLogger().info("CWI prevented registration of {} in {}", key.location(), key.registry());
    }

    @SuppressWarnings("unchecked")
    @Nullable public static <T> Holder.Reference<T> detached(ResourceKey<T> key) {
        return (Holder.Reference<T>) detached.get(key);
    }
}
