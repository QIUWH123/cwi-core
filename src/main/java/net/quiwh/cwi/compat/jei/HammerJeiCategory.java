package net.quiwh.cwi.compat.jei;

import com.mojang.math.Axis;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.quiwh.cwi.compat.jei.ProcessingChance;
import net.quiwh.cwi.hammer.HammerRecipe;
import net.quiwh.cwi.hammer.HammerRecipes;

/** Native equivalent of the pack's hammer conversion JEI categories. */
public final class HammerJeiCategory extends CreateRecipeCategory<HammerRecipe> {
    private static final TagKey<net.minecraft.world.item.Item> HAMMERS = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("cwi", "hammers"));
    private final HammerRecipes.Entry entry;

    public HammerJeiCategory(Info<HammerRecipe> info, HammerRecipes.Entry entry) {
        super(info);
        this.entry = entry;
    }

    public static ItemStack hammerIcon() {
        return BuiltInRegistries.ITEM.getTag(HAMMERS).flatMap(holders -> holders.stream().findFirst())
                .map(holder -> new ItemStack(holder.value())).orElseGet(() -> new ItemStack(Items.IRON_SWORD));
    }

    public static IDrawable hammerIconDrawable() { return new HammerDrawable(0); }

    public static IDrawable hammerPreview(int stage) { return new HammerDrawable(Math.clamp(stage, 1, 3)); }

    /** Original handheld models are flat in the GUI; atlas sprites retain their animated overlays. */
    private record HammerDrawable(int stage) implements IDrawable {
        List<ResourceLocation> textures() {
            return stage == 0 ? List.of(ResourceLocation.parse("kubejs:item/tools/iron_hammer"))
                    : List.of(ResourceLocation.parse("kubejs:item/tools/hammer_frame"),
                            ResourceLocation.parse("kubejs:item/tools/charged_overlay_" + stage));
        }

        @Override public int getWidth() { return 16; }
        @Override public int getHeight() { return 16; }

        @Override public void draw(GuiGraphics graphics, int x, int y) {
            var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
            for (ResourceLocation texture : textures())
                graphics.blit(x, y, 0, 16, 16, atlas.apply(texture), 1F, 1F, 1F, 1F);
        }
    }

    @Override protected void setRecipe(IRecipeLayoutBuilder builder, HammerRecipe recipe, IFocusGroup focuses) {
        var input = builder.addSlot(RecipeIngredientRole.INPUT, 18, 48)
                .setSlotName("input").setBackground(getRenderedSlot(), -1, -1);
        for (var stack : recipe.input().getItems()) input.addItemStack(stack);
        if (entry == HammerRecipes.BLOCK_CONVERSION) {
            var outputs = new ArrayList<HammerRecipe.Drop>();
            if (!recipe.target().isEmpty()) outputs.add(new HammerRecipe.Drop(recipe.target(), 1));
            outputs.addAll(recipe.drops());
            for (int index = 0; index < outputs.size(); index++) {
                var drop = outputs.get(index);
                int visible = Math.min(outputs.size() - index / 2 * 2, 2);
                int x = 142 - (visible - 1) * 9 + (index % 2) * 18;
                int y = 48 - index / 2 * 18;
                var slot = (outputs.size() <= 6 ? builder.addSlot(RecipeIngredientRole.OUTPUT, x, y)
                        : builder.addSlot(RecipeIngredientRole.OUTPUT))
                        .setBackground(getRenderedSlot(drop.chance()), -1, -1)
                        .addItemStack(drop.stack());
                addChanceTooltip(slot, drop.chance());
            }
        } else {
            var slot = builder.addSlot(RecipeIngredientRole.OUTPUT, 142, 48)
                    .setBackground(getRenderedSlot(), -1, -1)
                    .addItemStack(recipe.target());
        }
    }

    @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<HammerRecipe> holder, IFocusGroup focuses) {
        var recipe = holder.value();
        if (entry == HammerRecipes.BLOCK_CONVERSION && recipe.drops().size() + (recipe.target().isEmpty() ? 0 : 1) > 6) {
            builder.addScrollGridWidget(builder.getRecipeSlots().getSlots(RecipeIngredientRole.OUTPUT), 2, 3)
                    .setPosition(133, 12);
        }
    }

    @Override public void registerCatalysts(IRecipeCatalystRegistration registration) {
        BuiltInRegistries.ITEM.getTag(HAMMERS).ifPresent(holders -> holders.forEach(holder ->
                registration.addRecipeCatalyst(new ItemStack(holder), getRecipeType())));
    }

    @Override protected void draw(HammerRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_SHADOW.render(graphics, 65, 39);
        AllGuiTextures.JEI_LONG_ARROW.render(graphics, 54, 51);
        ItemStack input = slots.findSlotByName("input").flatMap(slot -> slot.getDisplayedItemStack())
                .orElse(ItemStack.EMPTY);
        graphics.pose().pushPose();
        if (entry == HammerRecipes.BLOCK_CONVERSION && input.getItem() instanceof BlockItem block) {
            graphics.pose().translate(56, 33, 0);
            graphics.pose().mulPose(Axis.XP.rotationDegrees(-12.5F));
            graphics.pose().mulPose(Axis.YP.rotationDegrees(22.5F));
            AnimatedKinetics.defaultBlockElement(block.getBlock().defaultBlockState()).rotateBlock(0, 180, 0)
                    .atLocal(0, 0, 2).scale(24).render(graphics);
        } else if (entry == HammerRecipes.DEPOT_CONVERSION) {
            graphics.pose().translate(89, 34, 0);
            graphics.pose().scale(2, 2, 1);
            graphics.renderItem(input, -8, -8);
        }
        graphics.pose().popPose();
        graphics.pose().pushPose();
        graphics.pose().translate(18, 6, 0);
        graphics.pose().scale(2, 2, 1);
        hammerPreview(recipe.stage()).draw(graphics, 0, 0);
        graphics.pose().popPose();
    }

    @Override protected List<Component> getTooltipStrings(HammerRecipe recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (mouseX < 42 || mouseX > 136 || mouseY < 40 || mouseY > 72) return List.of();
        return List.of(Component.translatable("cwi.recipe.hammer_stage").withStyle(ChatFormatting.GOLD));
    }

    private static void addChanceTooltip(mezz.jei.api.gui.builder.IRecipeSlotBuilder slot, float chance) {
        if (chance != 1F) slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(ProcessingChance.tooltip(chance)));
    }
}
