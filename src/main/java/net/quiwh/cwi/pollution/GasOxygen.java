package net.quiwh.cwi.pollution;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** Local ambient-air mixing, evaluated with the existing unfiltered head samples. */
final class GasOxygen {
    static final double AMBIENT_FRACTION=.2095;

    /** Equivalent oxygen-free concentration; preserves the former single-gas gameplay scale. */
    static double deficit(double gasAmount,double oxygenAmount) {
        return Math.max(0,AMBIENT_FRACTION*(1+gasAmount)/oxygenAmount-1);
    }
    static double damage(double deficit) {return 2*Math.max(0,deficit-1.5);}
    static void symptoms(LivingEntity entity,double deficit) {
        if(deficit>=.75)entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,40,0,false,false,true));
        if(deficit>=1)entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,40,0,false,false,true));
    }
    private GasOxygen(){}
}
