package net.quiwh.cwi.interaction;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

public final class WorldHazards {
    public static void tick(LivingEntity entity, long gameTime) {
        if (entity.level().isClientSide || !entity.isAlive()) return;
        if (entity instanceof Player player) {
            for (var holder : InteractionRecipes.rules(entity.level(), InteractionRecipes.Kind.INVENTORY_HAZARD)) {
                var rule = (InteractionRules.InventoryHazard) holder.value().rule();
                if (gameTime % rule.interval() == 0 && count(player, rule.input()) > 0)
                    apply(entity, rule.damage(), rule.damageType(), rule.fireTicks(), rule.effects());
            }
            for (var holder : InteractionRecipes.rules(entity.level(), InteractionRecipes.Kind.TOXIN_EXPOSURE)) {
                var rule = (InteractionRules.Toxin) holder.value().rule();
                if (gameTime % rule.interval() != 0) continue;
                var data = player.getPersistentData();
                if (!data.contains(rule.stateKey()) && net.neoforged.fml.ModList.get().isLoaded("kubejs"))
                    net.quiwh.cwi.compat.LegacyInteractionData.copyToxin(player, rule.stateKey());
                double current = data.getDouble(rule.stateKey());
                if (!Double.isFinite(current) || current < 0) current = 0;
                double count = count(player, rule.input()) + rule.additionalWeight() * count(player, rule.additionalInput());
                double next = rule.retention() * (current + Math.sqrt(count) * rule.scale());
                int amplifier = (int) Math.floor(current - rule.threshold());
                if (amplifier >= 0) addEffect(entity, new InteractionRules.Effect(rule.effect(), rule.duration(), Math.min(255, amplifier)));
                data.putDouble(rule.stateKey(), next);
            }
        }
        for (var holder : InteractionRecipes.rules(entity.level(), InteractionRecipes.Kind.FLUID_HAZARD)) {
            var rule = (InteractionRules.FluidHazard) holder.value().rule();
            if (gameTime % rule.interval() != 0) continue;
            // NeoForge tracks both source and flowing contact per entity. No world-entity scan.
            boolean touching = false;
            for (var fluid : rule.fluid().getStacks()) {
                if (entity.getFluidTypeHeight(fluid.getFluid().getFluidType()) > 0) { touching = true; break; }
            }
            if (touching) apply(entity, rule.damage(), rule.damageType(), rule.fireTicks(), rule.effects());
        }
    }

    public static void afterAttack(LivingEntity target, Player attacker) {
        if (target.level().isClientSide) return;
        var item = attacker.getMainHandItem();
        for (var holder : InteractionRecipes.rules(target.level(), InteractionRecipes.Kind.TOXIN_EXPOSURE)) {
            var rule = (InteractionRules.Toxin) holder.value().rule();
            if (!rule.input().test(item)) continue;
            boolean strong = rule.additionalInput().test(item);
            double chance = strong ? rule.strongMeleeChance() : rule.meleeChance();
            if (target.getRandom().nextDouble() >= chance) continue;
            int maxDuration = strong ? rule.strongMeleeDuration() : rule.meleeDuration();
            int duration = (int) Math.round(target.getRandom().nextDouble() * maxDuration);
            int amplifier = strong ? (int) Math.round(target.getRandom().nextDouble() * .7) : 0;
            if (duration > 0) addEffect(target, new InteractionRules.Effect(rule.effect(), duration, amplifier));
        }
    }

    private static int count(Player player, Ingredient ingredient) {
        if (ingredient.isEmpty()) return 0;
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (ingredient.test(stack)) count += stack.getCount();
        }
        return count;
    }
    private static void apply(LivingEntity entity, float damage, ResourceLocation type, int fireTicks, List<InteractionRules.Effect> effects) {
        var holder = entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolder(ResourceKey.create(Registries.DAMAGE_TYPE, type));
        if (damage > 0 && holder.isPresent()) entity.hurt(new DamageSource(holder.get()), damage);
        if (fireTicks > 0) entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), fireTicks));
        effects.forEach(effect -> addEffect(entity, effect));
    }
    private static void addEffect(LivingEntity entity, InteractionRules.Effect effect) {
        BuiltInRegistries.MOB_EFFECT.getHolder(effect.id()).ifPresent(holder ->
                entity.addEffect(new MobEffectInstance(holder, effect.duration(), effect.amplifier(), true, true)));
    }
    private WorldHazards() {}
}
