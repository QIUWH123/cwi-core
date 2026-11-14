package net.quiwh.cwi.pollution;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.quiwh.cwi.equipment.protection.HazardProtection;

/** Once-per-second authoritative exposure; all gas rules come from startup data. */
public final class GasExposure {
    private static final Map<Player,Long> LAST_EXPOSURE=new WeakHashMap<>();
    public static void tick(Player player) {
        if(!(player.level() instanceof ServerLevel level)||level.getGameTime()%20!=0||!player.isAlive())return;
        long now=level.getGameTime();
        if(LAST_EXPOSURE.getOrDefault(player,Long.MIN_VALUE)==now)return;
        LAST_EXPOSURE.put(player,now);
        GasRuleExposure.tick(player,GasClouds.existing(level));
    }
    public static float protection(net.minecraft.world.entity.LivingEntity entity,GasProfile gas,GasRoute route,boolean meaningful) {
        return HazardProtection.gasRemaining(entity,entity.level().getGameTime(),gas.routes().get(route),meaningful);
    }
    public static void cure(net.minecraft.world.entity.LivingEntity entity) {GasRuleExposure.cure(entity);}
    private GasExposure() {}
}
