package net.quiwh.cwi.block;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

final class PackBrushableBlock extends BrushableBlock {
    private final ResourceLocation target;

    PackBrushableBlock(String target, Block fallback) {
        super(fallback, SoundType.SUSPICIOUS_GRAVEL.getStepSound(), SoundType.ANVIL.getHitSound(),
                BlockBehaviour.Properties.ofFullCopy(Blocks.SUSPICIOUS_GRAVEL));
        this.target = ResourceLocation.parse(target);
    }

    @Override
    public Block getTurnsInto() {
        // KubeJS may register the target after Core. Resolve only when needed, and
        // retain a vanilla fallback when the pack's optional startup script is absent.
        return BuiltInRegistries.BLOCK.getOptional(target).orElseGet(super::getTurnsInto);
    }
}
