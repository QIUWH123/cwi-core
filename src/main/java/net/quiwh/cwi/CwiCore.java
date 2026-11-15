package net.quiwh.cwi;

import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.quiwh.cwi.processing.CwiFanProcessingType;
import net.quiwh.cwi.processing.FanRecipes;
import net.quiwh.cwi.hammer.HammerRecipes;
import net.quiwh.cwi.block.CwiBlocks;
import net.minecraft.resources.ResourceLocation;
import com.simibubi.create.foundation.data.CreateRegistrate;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(CwiCore.ID)
public final class CwiCore {
    public static final String ID = "cwi";
    public static final String MODID = ID;
    public static final String NAME = "CWI Core";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(ID);
    public static final DeferredRegister<FanProcessingType> FAN_TYPES = DeferredRegister.create(CreateRegistries.FAN_PROCESSING_TYPE, ID);

    static {
        FAN_TYPES.register("corroding", () -> new CwiFanProcessingType(FanRecipes.CORRODING));
        FAN_TYPES.register("sterilzing", () -> new CwiFanProcessingType(FanRecipes.STERILIZING));
    }

    public CwiCore(IEventBus modBus) {
        net.quiwh.cwi.sound.CwiSounds.register(modBus);
        CwiBlocks.register(modBus);
        net.quiwh.cwi.block.PetrifiedWood.register(modBus);
        net.quiwh.cwi.block.BuddingBlocks.register(modBus);
        net.quiwh.cwi.content.weathered.WeatheredContent.register(modBus);
        net.quiwh.cwi.content.carriage.CarriageContent.register(modBus);
        net.quiwh.cwi.compat.TfmgSlagVariants.register(modBus);
        FanRecipes.register(modBus);
        HammerRecipes.register(modBus);
        net.quiwh.cwi.interaction.InteractionRecipes.register(modBus);
        net.quiwh.cwi.interaction.WorldInteractions.register();
        net.quiwh.cwi.equipment.EquipmentUpgrades.register(modBus);
        net.quiwh.cwi.equipment.protection.ProtectiveEnchantments.register();
        net.quiwh.cwi.machine.MachineBlocks.register(modBus);
        net.quiwh.cwi.machine.MachineEffects.register(modBus);
        net.quiwh.cwi.machine.FurnaceMaterials.register(modBus);
        net.quiwh.cwi.machine.recipe.MachineRecipes.register(modBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.TagsUpdatedEvent event) -> net.quiwh.cwi.machine.MachineRecipeIndex.invalidate());
        net.quiwh.cwi.pollution.GasPollution.register(modBus);
        FAN_TYPES.register(modBus);
        REGISTRATE.registerEventListeners(modBus);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) -> {
            if (net.neoforged.fml.ModList.get().isLoaded("kubejs")) {
                event.enqueueWork(net.quiwh.cwi.compat.crystal.BuddingBlockBuilder::validateAll);
            }
        });
    }

    public static ResourceLocation loadResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }
}
