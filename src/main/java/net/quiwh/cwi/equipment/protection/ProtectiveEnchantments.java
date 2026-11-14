package net.quiwh.cwi.equipment.protection;

import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

/** Upgrade recipes control item eligibility; effects read enchantments worn in their native slots. */
public final class ProtectiveEnchantments {
    public enum Type {
        CHEMICAL_SEALING(true), HEAT_SHIELDING(true), COLD_INSULATION(true), ELECTRICAL_INSULATION(true),
        SURE_FOOTING(true), SWIMMING_EFFICIENCY(true), PARTICULATE_FILTRATION(false), GAS_FILTRATION(true),
        RADIATION_SHIELDING(false), PRESSURE_PROTECTION(false), OCULAR_SHIELDING(true);

        public final ResourceKey<Enchantment> key;
        public final boolean active;

        Type(boolean active) {
            this.active = active;
            key = ResourceKey.create(Registries.ENCHANTMENT,
                    ResourceLocation.fromNamespaceAndPath("cwi", name().toLowerCase(Locale.ROOT)));
        }
    }

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.BODY
    };

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ProtectiveEnchantments::damage);
        NeoForge.EVENT_BUS.addListener(ProtectiveEnchantments::knockback);
    }

    public static boolean owns(Holder<Enchantment> enchantment) {
        for (var type : Type.values()) if (enchantment.is(type.key)) return true;
        return false;
    }

    public static int level(LivingEntity entity, Type type) {
        if (!type.active) return 0;
        var holder = entity.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(type.key);
        if (holder.isEmpty()) return 0;
        int total = 0;
        for (var slot : SLOTS) total += level(entity.getItemBySlot(slot), slot, holder.get());
        return Math.min(4, total);
    }

    static int level(ItemStack stack, EquipmentSlot slot, Holder<Enchantment> enchantment) {
        if (stack.isEmpty() || !enchantment.value().matchingSlot(slot)) return 0;
        return Math.min(4, stack.getEnchantmentLevel(enchantment));
    }

    private static void damage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Type type = event.getSource().is(DamageTypeTags.IS_FREEZING) ? Type.COLD_INSULATION :
                event.getSource().is(DamageTypes.LIGHTNING_BOLT) ? Type.ELECTRICAL_INSULATION : null;
        if (type == null) return;
        int level = level(event.getEntity(), type);
        if (level > 0) {
            event.setAmount(event.getAmount() * (1 - level / 4F));
            if (level == 4) event.setCanceled(true);
        }
    }

    private static void knockback(LivingKnockBackEvent event) {
        if (event.getEntity().onGround())
            event.setStrength(event.getStrength() * (1 - .15F * level(event.getEntity(), Type.SURE_FOOTING)));
    }

    /** Skip a fraction of native growth ticks without adding persistent per-entity state. */
    public static int freezing(LivingEntity entity, int proposed) {
        int previous = entity.getTicksFrozen();
        if (proposed <= previous) return proposed;
        int level = level(entity, Type.COLD_INSULATION);
        return level > 0 && Math.floorMod(entity.tickCount, 4) < level ? previous : proposed;
    }

    public static float friction(LivingEntity entity, float original) {
        if (!entity.onGround() || original < .9F) return original;
        return original + (.6F - original) * (level(entity, Type.SURE_FOOTING) / 4F);
    }

    public static double swimming(LivingEntity entity, double original) {
        return entity.isSwimming() && entity.isInWater() ?
                original * (1 + .1 * level(entity, Type.SWIMMING_EFFICIENCY)) : original;
    }

    private ProtectiveEnchantments() {}
}
