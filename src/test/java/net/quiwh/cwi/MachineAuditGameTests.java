package net.quiwh.cwi;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.machine.MachineBlocks;
import net.quiwh.cwi.machine.SolarEvaporatorBlockEntity;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class MachineAuditGameTests {
    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void solarPickupConservesDiscardedAndOverflowItems(GameTestHelper helper) {
        var pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MachineBlocks.SOLAR_EVAPORATOR.get());
        SolarEvaporatorBlockEntity tray = helper.getBlockEntity(pos);
        var center = helper.absolutePos(pos).getCenter();
        var entities = new java.util.ArrayList<ItemEntity>();
        for (var stack : java.util.List.of(new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.DIAMOND),
                new ItemStack(Items.IRON_INGOT, 64), new ItemStack(Items.IRON_INGOT, 10))) {
            var entity = new ItemEntity(helper.getLevel(), center.x, center.y - .25, center.z, stack);
            entity.setNoGravity(true);
            entity.setDeltaMovement(0, 0, 0);
            helper.getLevel().addFreshEntity(entity);
            entities.add(entity);
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(tray.getItem(0).getCount() + tray.getItem(1).getCount()
                    + tray.getItem(2).getCount() == 66, "three tray slots collect only their available space");
            for (var item : java.util.List.of(Items.GOLD_INGOT, Items.DIAMOND, Items.IRON_INGOT)) {
                int count = 0;
                for (int slot = 0; slot < 3; slot++) if (tray.getItem(slot).is(item)) count += tray.getItem(slot).getCount();
                for (var entity : entities) if (entity.isAlive() && entity.getItem().is(item)) count += entity.getItem().getCount();
                helper.assertTrue(count == (item == Items.IRON_INGOT ? 74 : 1),
                        "pickup preserves each item type across discarded entities and overflow");
            }
        });
    }
}
