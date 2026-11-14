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

import java.util.List;

public final class WorldHazards {
    public static void tick(LivingEntity entity, long gameTime) {
        if (entity.level().isClientSide || !entity.isAlive()) return;
        if (entity instanceof Player player) {
            for (var holder : InteractionRecipes.rules(entity.level(), InteractionRecipes.Kind.INVENTORY_HAZARD)) {
                var rule = (InteractionRules.InventoryHazard) holder.value().rule();
                if (gameTime % rule.interval() == 0 && count(player, rule.input()) > 0)
                    apply(entity, rule.damage(), rule.damageType(), rule.fireTicks(), rule.effects(), rule.protection(), gameTime);
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
        // NeoForge already tracks source/flowing contact. Dry entities need no fluid-rule lookup.
        if (!entity.isInFluidType()) return;
        InteractionRecipes.fluidHazards(entity.level().getRecipeManager(), entity.level().registryAccess()).tick(entity, gameTime);
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
    private static void apply(LivingEntity entity, float damage, ResourceLocation type, int fireTicks, List<InteractionRules.Effect> effects, net.quiwh.cwi.equipment.protection.HazardProtection protection, long gameTime) {
        var holder = entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolder(ResourceKey.create(Registries.DAMAGE_TYPE, type));
        boolean meaningful = damage > 0 && holder.isPresent() || fireTicks > 0 || effects.stream()
                .anyMatch(effect -> BuiltInRegistries.MOB_EFFECT.containsKey(effect.id()));
        float remaining = protection.remaining(entity, gameTime, meaningful);
        if (remaining == 0) return;
        if (damage > 0 && holder.isPresent()) entity.hurt(new DamageSource(holder.get()), damage * remaining);
        int ignition = (int) (fireTicks * remaining);
        if (ignition > 0) entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), ignition));
        for (var effect : effects) {
            int duration = (int) (effect.duration() * remaining);
            if (duration > 0) addEffect(entity, new InteractionRules.Effect(effect.id(), duration, effect.amplifier()));
        }
    }
    private static void addEffect(LivingEntity entity, InteractionRules.Effect effect) {
        BuiltInRegistries.MOB_EFFECT.getHolder(effect.id()).ifPresent(holder ->
                entity.addEffect(new MobEffectInstance(holder, effect.duration(), effect.amplifier(), true, true)));
    }
    private WorldHazards() {}
}
