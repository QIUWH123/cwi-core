package net.quiwh.cwi.mixin.protection;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.quiwh.cwi.equipment.protection.ProtectiveEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LivingEntity.class)
abstract class LivingProtectionMixin {
    @ModifyArg(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setTicksFrozen(I)V", ordinal = 0), index = 0)
    private int cwi$coldGrowth(int proposed) {
        return ProtectiveEnchantments.freezing((LivingEntity) (Object) this, proposed);
    }

    @ModifyExpressionValue(method = "travel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getFriction(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)F"))
    private float cwi$traction(float original) {
        return ProtectiveEnchantments.friction((LivingEntity) (Object) this, original);
    }

    @WrapOperation(method = "travel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double cwi$swimming(LivingEntity entity, Holder<Attribute> attribute, Operation<Double> original) {
        double value = original.call(entity, attribute);
        return attribute == NeoForgeMod.SWIM_SPEED ? ProtectiveEnchantments.swimming(entity, value) : value;
    }
}
