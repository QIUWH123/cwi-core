package net.quiwh.cwi.equipment;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class EquipmentUpgrades {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, "cwi");
    public static final DeferredHolder<RecipeSerializer<?>, EquipmentUpgradeRecipe.Serializer> SERIALIZER =
            SERIALIZERS.register("equipment_upgrade", EquipmentUpgradeRecipe.Serializer::new);
    public static void register(IEventBus bus) { SERIALIZERS.register(bus); }
    private EquipmentUpgrades() {}
}
