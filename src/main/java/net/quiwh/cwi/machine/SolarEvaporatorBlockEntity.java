package net.quiwh.cwi.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Storage tray only. Evaporation recipes were never defined in the original content. */
public final class SolarEvaporatorBlockEntity extends MachineBlockEntity {
    public SolarEvaporatorBlockEntity(BlockPos pos, BlockState state) { super(MachineBlocks.SOLAR_EVAPORATOR_ENTITY.get(), pos, state, 3, 3000); }
    @Override protected void addMachineInfo(List<Component> tooltip) {
        MachineGoggles.line(tooltip, Component.translatable("cwi.machine.storage_only"));
    }

    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide || level.getGameTime() % 20 != 0) return;
        AABB tray = new AABB(.12, .12, .12, .88, .5, .88).move(worldPosition);
        for (int slot = 0; slot < 3; slot++) {
            int budget = 64;
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, tray, item -> item.isAlive())) {
                ItemStack stack = entity.getItem();
                int offered = Math.min(budget, stack.getCount());
                ItemStack remainder = inventory.insertItem(slot, stack.copyWithCount(offered), false);
                int inserted = offered - remainder.getCount();
                if (inserted == 0) continue;
                budget -= inserted;
                ItemStack left = stack.copy();
                left.shrink(inserted);
                if (left.isEmpty()) entity.discard(); else entity.setItem(left);
                if (budget == 0) break;
            }
        }
    }
}
