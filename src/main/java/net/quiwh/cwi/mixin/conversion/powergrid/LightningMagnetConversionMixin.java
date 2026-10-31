package net.quiwh.cwi.mixin.conversion.powergrid;

import com.simibubi.create.AllItems;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;

// Wrap the complete method, including Power Grid's injected capture handler. Alloy already
// has lightning immunity in Power Grid; keep that behavior without producing magnets.
@Mixin(Entity.class)
public abstract class LightningMagnetConversionMixin {
    @WrapMethod(method = "thunderHit")
    private void cwi$disableLightningConversion(ServerLevel level, LightningBolt bolt, Operation<Void> original) {
        if ((Object)this instanceof ItemEntity item && AllItems.ANDESITE_ALLOY.isIn(item.getItem())) return;
        original.call(level, bolt);
    }
}
