package net.quiwh.cwi.pollution;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.quiwh.cwi.CwiCore;

/** Normal native effect; both command-applied and exposure-driven instances use the same capped curve. */
public final class NeurotoxinEffect extends MobEffect {
    public NeurotoxinEffect() {
        super(MobEffectCategory.HARMFUL,0x8C9F71);
        addAttributeModifier(Attributes.MOVEMENT_SPEED,CwiCore.loadResource("neurotoxin_movement"),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,level->-.1*Math.min(4,Math.max(1,level+1)));
        addAttributeModifier(Attributes.ATTACK_SPEED,CwiCore.loadResource("neurotoxin_attack"),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,level->-.1*Math.min(4,Math.max(1,level+1)));
    }
}
