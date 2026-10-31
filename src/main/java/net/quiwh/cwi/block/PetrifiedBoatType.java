package net.quiwh.cwi.block;

import java.util.function.Supplier;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;

/** Lazy suppliers avoid loading block/item registries while Minecraft initializes its enums. */
public final class PetrifiedBoatType {
    public static final EnumProxy<Boat.Type> PETRIFIED = new EnumProxy<>(Boat.Type.class,
            (Supplier<Block>) () -> PetrifiedWood.get("petrified_planks"), "cwi:petrified",
            (Supplier<Item>) () -> PetrifiedWood.item("petrified_boat"),
            (Supplier<Item>) () -> PetrifiedWood.item("petrified_chest_boat"),
            (Supplier<Item>) () -> Items.STICK, false);
    private PetrifiedBoatType() {}
}
