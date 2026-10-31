package net.quiwh.cwi.kubejs;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Successful charged entity hit, exposed as bean properties to KubeJS callbacks. */
public record HammerImpactContext(ItemStack getItem, Level getLevel, Player getPlayer,
                                  LivingEntity getTarget, InteractionHand getHand, int getStage,
                                  float getDamage) {}
