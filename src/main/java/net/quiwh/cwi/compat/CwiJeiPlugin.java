package net.quiwh.cwi.compat;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.ProcessingViaFanCategory;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.quiwh.cwi.CwiCore;
import net.quiwh.cwi.compat.jei.EquipmentUpgradeJeiCategory;
import net.quiwh.cwi.compat.jei.HammerJeiCategory;
import net.quiwh.cwi.compat.jei.MachineJeiCategory;
import net.quiwh.cwi.compat.jei.WorldSpoutingJeiCategory;
import net.quiwh.cwi.hammer.HammerRecipe;
import net.quiwh.cwi.hammer.HammerRecipes;
import net.quiwh.cwi.interaction.InteractionRecipe;
import net.quiwh.cwi.interaction.InteractionRecipes;
import net.quiwh.cwi.machine.recipe.MachineRecipe;
import net.quiwh.cwi.machine.recipe.MachineRecipes;
import net.quiwh.cwi.processing.FanRecipe;
import net.quiwh.cwi.processing.FanRecipes;

/** Native Create fan categories retain chance slots and animated fan presentation. */
@JeiPlugin
public final class CwiJeiPlugin implements IModPlugin {
    private EquipmentUpgradeJeiCategory equipmentUpgrades;
    private final List<FanCategory> categories = new ArrayList<>();
    private final List<MachineJeiCategory> machineCategories = new ArrayList<>();
    private final List<HammerJeiCategory> hammerCategories = new ArrayList<>();
    private final List<WorldSpoutingJeiCategory> worldSpoutingCategories = new ArrayList<>();

    @Override public ResourceLocation getPluginUid() { return CwiCore.loadResource("jei_plugin"); }

    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        equipmentUpgrades = new EquipmentUpgradeJeiCategory(new CreateRecipeCategory.Info<>(
                RecipeType.<net.quiwh.cwi.equipment.EquipmentUpgradeRecipe>createRecipeHolderType(CwiCore.loadResource("equipment_upgrades")),
                Component.translatable("cwi.recipe.equipment_upgrades"), guiHelper.createBlankDrawable(178, 72),
                new com.simibubi.create.compat.jei.DoubleItemIcon(
                    () -> new ItemStack(net.minecraft.world.item.Items.CRAFTING_TABLE), () -> {
                        var smallHammer = ResourceLocation.parse("kubejs:small_hammer");
                        return BuiltInRegistries.ITEM.containsKey(smallHammer) ? new ItemStack(BuiltInRegistries.ITEM.get(smallHammer)) : HammerJeiCategory.hammerIcon();
                    }),
                () -> {
                    var level = Minecraft.getInstance().level;
                    if (level == null) return List.of();
                    return level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING).stream()
                        .filter(holder -> holder.value() instanceof net.quiwh.cwi.equipment.EquipmentUpgradeRecipe)
                        .map(holder -> new RecipeHolder<>(holder.id(), (net.quiwh.cwi.equipment.EquipmentUpgradeRecipe)holder.value())).toList();
                }, List.of(() -> new ItemStack(net.minecraft.world.item.Items.CRAFTING_TABLE))));
        registration.addRecipeCategories(equipmentUpgrades);
        categories.clear();
        machineCategories.clear();
        hammerCategories.clear();
        worldSpoutingCategories.clear();
        for (FanRecipes.Entry entry : List.of(FanRecipes.CORRODING, FanRecipes.STERILIZING)) {
            var gui = registration.getJeiHelpers().getGuiHelper();
            RecipeType<RecipeHolder<FanRecipe>> type = RecipeType.createRecipeHolderType(entry.mode().recipeId());
            var info = new CreateRecipeCategory.Info<>(type,
                    Component.translatable("cwi.recipe." + entry.mode().recipeId().getPath()),
                    gui.createBlankDrawable(178, 85),
                    gui.createDrawableItemStack(AllBlocks.ENCASED_FAN.asStack()),
                    () -> {
                        var level = Minecraft.getInstance().level;
                        return level == null ? List.<RecipeHolder<FanRecipe>>of()
                                : level.getRecipeManager().getAllRecipesFor(entry.recipeType());
                    }, List.of(AllBlocks.ENCASED_FAN::asStack));
            categories.add(new FanCategory(info, entry));
        }
        registration.addRecipeCategories(categories.toArray(CreateRecipeCategory<?>[]::new));
        for (var entry : MachineRecipes.entries()) {
            var gui = registration.getJeiHelpers().getGuiHelper();
            var type = RecipeType.<MachineRecipe>createRecipeHolderType(entry.getId());
            var info = new CreateRecipeCategory.Info<>(type,
                    Component.translatable(MachineJeiCategory.titleKey(entry)),
                    gui.createBlankDrawable(MachineJeiCategory.width(entry), MachineJeiCategory.height(entry)),
                    gui.createDrawableItemStack(new ItemStack(BuiltInRegistries.ITEM.get(entry.machineId()))),
                    () -> {
                        var level = Minecraft.getInstance().level;
                        return level == null ? List.<RecipeHolder<MachineRecipe>>of()
                                : level.getRecipeManager().getAllRecipesFor(entry.recipeType());
                    }, List.of(() -> new ItemStack(BuiltInRegistries.ITEM.get(entry.machineId()))));
            machineCategories.add(new MachineJeiCategory(info, entry));
        }
        registration.addRecipeCategories(machineCategories.toArray(CreateRecipeCategory<?>[]::new));
        for (var entry : HammerRecipes.entries()) {
            var gui = registration.getJeiHelpers().getGuiHelper();
            var type = RecipeType.<HammerRecipe>createRecipeHolderType(entry.getId());
            var info = new CreateRecipeCategory.Info<>(type,
                    Component.translatable(entry == HammerRecipes.BLOCK_CONVERSION
                            ? "cwi.recipe.block_conversion" : "cwi.recipe.depot_conversion"),
                    gui.createBlankDrawable(178, 72), HammerJeiCategory.hammerIconDrawable(),
                    () -> {
                        var level = Minecraft.getInstance().level;
                        return level == null ? List.<RecipeHolder<HammerRecipe>>of()
                                : level.getRecipeManager().getAllRecipesFor(entry.recipeType());
                    }, List.of(HammerJeiCategory::hammerIcon));
            hammerCategories.add(new HammerJeiCategory(info, entry));
        }
        registration.addRecipeCategories(hammerCategories.toArray(CreateRecipeCategory<?>[]::new));
        for (var kind : List.of(InteractionRecipes.Kind.BLOCK_SPOUTING, InteractionRecipes.Kind.BUDDING_CATALYSIS)) {
            var info = new CreateRecipeCategory.Info<>(
                    RecipeType.<InteractionRecipe>createRecipeHolderType(kind.id()),
                    Component.translatable(WorldSpoutingJeiCategory.titleKey(kind)),
                    guiHelper.createBlankDrawable(WorldSpoutingJeiCategory.WIDTH, WorldSpoutingJeiCategory.HEIGHT),
                    new com.simibubi.create.compat.jei.DoubleItemIcon(AllBlocks.SPOUT::asStack,
                            () -> new ItemStack(kind == InteractionRecipes.Kind.BUDDING_CATALYSIS
                                    ? net.minecraft.world.item.Items.BUDDING_AMETHYST : net.minecraft.world.item.Items.MUD)),
                    () -> {
                        var level = Minecraft.getInstance().level;
                        return level == null ? List.<RecipeHolder<InteractionRecipe>>of() : InteractionRecipes.rules(level, kind);
                    }, List.of(AllBlocks.SPOUT::asStack));
            worldSpoutingCategories.add(new WorldSpoutingJeiCategory(info));
        }
        registration.addRecipeCategories(worldSpoutingCategories.toArray(CreateRecipeCategory<?>[]::new));
    }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        equipmentUpgrades.registerRecipes(registration);
        categories.forEach(category -> category.registerRecipes(registration));
        machineCategories.forEach(category -> category.registerRecipes(registration));
        hammerCategories.forEach(category -> category.registerRecipes(registration));
        worldSpoutingCategories.forEach(category -> category.registerRecipes(registration));
        registration.addItemStackInfo(new ItemStack(BuiltInRegistries.ITEM.get(MachineRecipes.BLAST_FURNACE.machineId())),
                Component.translatable("cwi.info.blast_furnace.heat"), Component.translatable("cwi.info.blast_furnace.outputs"));
    }

    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        equipmentUpgrades.registerCatalysts(registration);
        categories.forEach(category -> category.registerCatalysts(registration));
        machineCategories.forEach(category -> category.registerCatalysts(registration));
        hammerCategories.forEach(category -> category.registerCatalysts(registration));
        worldSpoutingCategories.forEach(category -> category.registerCatalysts(registration));
    }

    private static final class FanCategory extends ProcessingViaFanCategory.MultiOutput<FanRecipe> {
        private final TagKey<Fluid> displayFluids;

        private FanCategory(Info<FanRecipe> info, FanRecipes.Entry entry) {
            super(info);
            displayFluids = TagKey.create(net.minecraft.core.registries.Registries.FLUID,
                    ResourceLocation.fromNamespaceAndPath(CwiCore.ID, "jei/fan_processing_fluids/" + entry.mode().name().toLowerCase(Locale.ROOT)));
        }

        @Override protected void renderAttachedBlock(GuiGraphics graphics) {
            // Presentation has its own fluid tags; gameplay tags also contain framed catalysts.
            List<Fluid> fluids = BuiltInRegistries.FLUID.getTag(displayFluids)
                    .map(holders -> holders.stream().map(holder -> holder.value())
                            .filter(fluid -> fluid.defaultFluidState().createLegacyBlock().getBlock() instanceof LiquidBlock)
                            .map(fluid -> fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid)
                            .distinct().toList()).orElse(List.of());
            if (fluids.isEmpty()) return;
            long frame = System.currentTimeMillis() / 1000;
            Fluid fluid = fluids.get((int) (frame % fluids.size()));
            // Same builder settings as Create's FanWashingCategory; the parent supplies the camera rotation.
            GuiGameElement.of(fluid).scale(SCALE).atLocal(0, 0, 2)
                    .lighting(AnimatedKinetics.DEFAULT_LIGHTING).render(graphics);
        }
    }
}
