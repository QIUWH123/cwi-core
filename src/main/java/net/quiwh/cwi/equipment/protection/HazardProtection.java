package net.quiwh.cwi.equipment.protection;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** A reloadable hazard policy; snapshots protect same-tick exposures even when a piece breaks. */
public record HazardProtection(ProtectiveEnchantments.Type type, int required, int durability, List<EquipmentSlot> slots) {
    public static final HazardProtection NONE = new HazardProtection(null, 4, 0, List.of());
    private static final Map<LivingEntity, Pulse> PULSES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.BODY
    };

    private static final class Pulse {
        final long tick;
        final EnumMap<EquipmentSlot, ItemStack> originals = new EnumMap<>(EquipmentSlot.class);
        final EnumMap<EquipmentSlot, ItemStack> snapshot = new EnumMap<>(EquipmentSlot.class);
        final EnumMap<ProtectiveEnchantments.Type, EnumMap<EquipmentSlot, Integer>> charged =
                new EnumMap<>(ProtectiveEnchantments.Type.class);

        final EnumMap<EquipmentSlot,Integer> gasCharged = new EnumMap<>(EquipmentSlot.class);

        Pulse(LivingEntity entity, long tick) {
            this.tick = tick;
            for (var slot : ARMOR) {
                var stack = entity.getItemBySlot(slot);
                originals.put(slot, stack);
                snapshot.put(slot, stack.copy());
            }
        }
    }

    public HazardProtection {
        slots = List.copyOf(slots);
        if (required < 1 || required > 16 || durability < 0 || durability > 1_000_000)
            throw new IllegalArgumentException("Invalid hazard resistance or durability cost");
    }

    /** Gas routes explicitly choose worn slots. Available pieces and matching enchantments add together. */
    public static float gasRemaining(LivingEntity entity,long tick,
            net.quiwh.cwi.pollution.GasProfile.Protection policy,boolean meaningful) {
        if(!meaningful||policy.enchantments().isEmpty())return 1;
        Pulse pulse;
        synchronized(PULSES) {
            pulse=PULSES.get(entity);
            if(pulse==null||pulse.tick!=tick){pulse=new Pulse(entity,tick);PULSES.put(entity,pulse);}
        }
        int sum=0;
        var registry=entity.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for(var id:policy.enchantments()) {
            var holder=registry.getHolder(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT,id));
            if(holder.isEmpty())continue;
            var paid=pulse.gasCharged;
            for(var slot:policy.slots()) {
                var snapshot=pulse.snapshot.getOrDefault(slot,ItemStack.EMPTY);
                int level=Math.max(0,snapshot.getEnchantmentLevel(holder.get()));
                if(level==0)continue;
                sum=Math.min(1024,sum+level);
                if(entity instanceof Player player&&player.getAbilities().instabuild)continue;
                int previous=paid.getOrDefault(slot,0),extra=Math.max(0,policy.durability()-previous);
                paid.put(slot,Math.max(previous,policy.durability()));
                var stack=entity.getItemBySlot(slot);
                if(extra>0&&stack==pulse.originals.get(slot)&&stack.isDamageableItem())stack.hurtAndBreak(extra,entity,slot);
            }
        }
        return 1-Math.min(1,sum/(float)policy.resistance());
    }

    public float remaining(LivingEntity entity, long tick, boolean meaningful) {
        if (type == null || !type.active || !meaningful) return 1;
        var holder = entity.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(type.key);
        if (holder.isEmpty()) return 1;
        Pulse pulse;
        synchronized (PULSES) {
            pulse = PULSES.get(entity);
            if (pulse == null || pulse.tick != tick) {
                pulse = new Pulse(entity, tick);
                PULSES.put(entity, pulse);
            }
        }
        int sum = 0;
        var levels = new EnumMap<EquipmentSlot, Integer>(EquipmentSlot.class);
        for (var slot : slots.isEmpty() ? List.of(ARMOR) : slots) {
            int level = ProtectiveEnchantments.level(pulse.snapshot.getOrDefault(slot, ItemStack.EMPTY), slot, holder.get());
            if (!slots.isEmpty() && level == 0) return 1;
            if (level > 0) {
                levels.put(slot, level);
                sum += level;
            }
        }
        if (sum == 0) return 1;
        float remaining = 1 - Math.min(1, sum / (float) required);
        if (durability > 0 && !(entity instanceof Player player && player.getAbilities().instabuild)) {
            var paid = pulse.charged.computeIfAbsent(type, ignored -> new EnumMap<>(EquipmentSlot.class));
            for (var slot : levels.keySet()) {
                int previous = paid.getOrDefault(slot, 0);
                int extra = Math.max(0, durability - previous);
                paid.put(slot, Math.max(previous, durability));
                var stack = entity.getItemBySlot(slot);
                if (extra > 0 && stack == pulse.originals.get(slot) && stack.isDamageableItem())
                    stack.hurtAndBreak(extra, entity, slot);
            }
        }
        return remaining;
    }
}
