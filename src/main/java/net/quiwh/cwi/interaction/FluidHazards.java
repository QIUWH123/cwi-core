package net.quiwh.cwi.interaction;

import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import javax.annotation.Nullable;
import net.quiwh.cwi.equipment.protection.HazardProtection;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

/** Immutable fluid groups and actions for one recipe generation and registry context. */
final class FluidHazards {
    private record Group(FluidType type, BitSet rules) {}
    private record Effect(Holder<MobEffect> type, int duration, int amplifier) {}
    private record Action(int interval, float damage, @Nullable DamageSource source, int fireTicks, List<Effect> effects, HazardProtection protection) {
        void apply(LivingEntity entity, long gameTime) {
            float remaining = protection.remaining(entity, gameTime, damage > 0 && source != null || fireTicks > 0 || !effects.isEmpty());
            if (remaining == 0) return;
            if (damage > 0 && source != null) entity.hurt(source, damage * remaining);
            int ignition = (int) (fireTicks * remaining);
            if (ignition > 0) entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), ignition));
            for (var effect : effects) {
                int duration = (int) (effect.duration() * remaining);
                if (duration > 0) entity.addEffect(new MobEffectInstance(effect.type(), duration, effect.amplifier(), true, true));
            }
        }
    }

    private final List<Group> groups;
    private final List<Action> actions;

    private FluidHazards(List<Group> groups, List<Action> actions) {
        this.groups = groups;
        this.actions = List.copyOf(actions);
    }

    static FluidHazards compile(List<RecipeHolder<InteractionRecipe>> recipes, RegistryAccess registries) {
        var groups = new LinkedHashMap<FluidType, BitSet>();
        var actions = new ArrayList<Action>();
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        for (var holder : recipes) {
            var recipe = holder.value();
            var rule = (InteractionRules.FluidHazard) recipe.rule();
            DamageSource source = rule.damage() > 0 ? registries.registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolder(ResourceKey.create(Registries.DAMAGE_TYPE, rule.damageType())).map(DamageSource::new).orElse(null) : null;
            var effects = new ArrayList<Effect>();
            for (var effect : rule.effects())
                BuiltInRegistries.MOB_EFFECT.getHolder(effect.id()).ifPresent(type ->
                        effects.add(new Effect(type, effect.duration(), effect.amplifier())));
            int index = actions.size();
            actions.add(new Action(rule.interval(), rule.damage(), source, rule.fireTicks(), List.copyOf(effects), rule.protection()));

            // A native ingredient can retain its old getStacks() cache after tags are rebound.
            // Resolve a fresh ingredient only when this compiled index is rebuilt.
            var fluid = FluidIngredient.CODEC_NON_EMPTY.parse(ops, recipe.definition().get("fluid")).getOrThrow();
            for (var stack : fluid.getStacks())
                groups.computeIfAbsent(stack.getFluid().getFluidType(), type -> new BitSet()).set(index);
        }
        return new FluidHazards(groups.entrySet().stream().map(entry -> new Group(entry.getKey(), entry.getValue())).toList(), actions);
    }

    void tick(LivingEntity entity, long gameTime) {
        BitSet matching = null;
        boolean copied = false;
        for (var group : groups) {
            if (entity.getFluidTypeHeight(group.type()) <= 0) continue;
            if (matching == null) matching = group.rules();
            else {
                // The usual single-fluid case reuses the immutable group without allocating.
                if (!copied) { matching = (BitSet) matching.clone(); copied = true; }
                matching.or(group.rules());
            }
        }
        if (matching == null) return;
        // Bit positions retain recipe-ID order and deduplicate rules matching several fluids.
        for (int index = matching.nextSetBit(0); index >= 0; index = matching.nextSetBit(index + 1)) {
            var action = actions.get(index);
            if (gameTime % action.interval() == 0) action.apply(entity, gameTime);
        }
    }
}
