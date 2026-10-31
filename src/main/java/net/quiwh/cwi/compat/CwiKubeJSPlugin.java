package net.quiwh.cwi.compat;

import com.simibubi.create.AllParticleTypes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentTypeRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;
import dev.latvian.mods.kubejs.registry.BuilderTypeRegistry;
import dev.latvian.mods.kubejs.registry.RegistryType;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.plugin.builtin.event.ServerEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.quiwh.cwi.CwiCore;
import net.quiwh.cwi.kubejs.HammerBuilder;

import java.time.LocalDateTime;

/** Shared script conveniences. Recipe schemas are data driven in KubeJS 7. */
public final class CwiKubeJSPlugin implements KubeJSPlugin {
    @Override public void registerEvents(dev.latvian.mods.kubejs.event.EventGroupRegistry registry) {
        registry.register(CwiKubeJSEvents.GROUP);
    }

    @Override public void beforeRecipeLoading(dev.latvian.mods.kubejs.recipe.RecipesKubeEvent event,
            dev.latvian.mods.kubejs.core.RecipeManagerKJS manager,
            java.util.Map<ResourceLocation, com.google.gson.JsonElement> recipes) {
        // KubeJS can receive restored original definitions after the native resource scan.
        VintageMaterialPolicy.filterData("recipe", recipes);
        var upgrades = new EquipmentUpgradeEvent();
        EquipmentUpgradeEvent.EVENT.post(upgrades);
        upgrades.generate(event, recipes);
    }

    @Override public void registerBuilderTypes(BuilderTypeRegistry registry) {
        registry.of(Registries.BLOCK, blocks -> blocks.add(ResourceLocation.parse("cwi:drying"),
                DryingBlockBuilder.class, DryingBlockBuilder::new));
        registry.of(Registries.ITEM, items ->
                items.add(ResourceLocation.fromNamespaceAndPath("kubejs", "hammer"),
                        HammerBuilder.class, HammerBuilder::new));
    }

    @Override public void registerRecipeComponents(RecipeComponentTypeRegistry registry) {
        registry.register(CwiProcessingOutputComponent.TYPE);
        registry.register(CwiUpgradeIngredientComponent.TYPE);
        CwiMachineRecipeComponents.register(registry);
        CwiHammerRecipeComponents.register(registry);
        CwiAddonRecipeSchemas.registerComponents(registry);
        registry.register(CwiInteractionActionComponent.TYPE);
        registry.register(CwiInteractionActionComponent.EFFECT);
    }

    @Override public void beforeScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType == ScriptType.SERVER)
            ServerEvents.RECIPE_SCHEMA_REGISTRY.listenJava(ScriptType.SERVER, null,
                    event -> { CwiAddonRecipeSchemas.register((RecipeSchemaRegistry) event); return null; });
    }

    @Override public void afterScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType == ScriptType.STARTUP) RegistrationBlockEvent.postStartup();
        if (manager.scriptType == ScriptType.CLIENT) ShadowDropEvent.postClient();
    }

    @Override public void registerRecipeSchemas(RecipeSchemaRegistry registry) {
        CwiMachineRecipeSchemas.register(registry);
    }

    @Override public void registerBindings(BindingRegistry bindings) {
        bindings.add("Cwi", CwiCore.class);
        bindings.add("RegistryType", RegistryType.class);
        bindings.add("LocalDateTime", LocalDateTime.class);
        bindings.add("Player", Player.class);
        bindings.add("Level", Level.class);
        bindings.add("InteractionHand", InteractionHand.class);
        bindings.add("Rarity", Rarity.class);
        bindings.add("ItemTags", ItemTags.class);
        bindings.add("BlockTags", BlockTags.class);
        bindings.add("FluidTags", FluidTags.class);
        bindings.add("NeoForgeTags", Tags.class);
        bindings.add("NeoForgeTags$Items", Tags.Items.class);
        bindings.add("NeoForgeTags$Blocks", Tags.Blocks.class);
        bindings.add("NeoForgeTags$Fluids", Tags.Fluids.class);
        bindings.add("ForgeTags", Tags.class);
        bindings.add("ForgeTags$Items", Tags.Items.class);
        bindings.add("ForgeTags$Blocks", Tags.Blocks.class);
        bindings.add("ForgeTags$Fluids", Tags.Fluids.class);
        bindings.add("ChatFormatting", ChatFormatting.class);
        bindings.add("NeoForgeRegistries", NeoForgeRegistries.class);
        bindings.add("BuiltInRegistries", BuiltInRegistries.class);
        bindings.add("ParticleTypes", ParticleTypes.class);
        bindings.add("Item$Properties", Item.Properties.class);
        bindings.add("BlockBehaviour$Properties", BlockBehaviour.Properties.class);
        bindings.add("BlockItem", BlockItem.class);
        bindings.add("AllSoundEvents", AllSoundEvents.class);
        bindings.add("AllParticleTypes", AllParticleTypes.class);
        bindings.add("TooltipHelper", TooltipHelper.class);
        bindings.add("CreateLang", CreateLang.class);
    }
}
