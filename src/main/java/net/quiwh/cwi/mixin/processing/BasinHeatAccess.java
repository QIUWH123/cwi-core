package net.quiwh.cwi.mixin.processing;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(value=BasinBlockEntity.class,remap=false)
public interface BasinHeatAccess {
    @Invoker("getHeatLevel") BlazeBurnerBlock.HeatLevel cwi$heat();
}
