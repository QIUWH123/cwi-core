package net.quiwh.cwi.machine;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.quiwh.cwi.CwiCore;
import net.quiwh.cwi.machine.recipe.MachineRecipes;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class MachineRecipeCacheGameTests {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void cacheRechecksStorageComponentsTagsAndRecipeReplacements(GameTestHelper helper) {
        var type = MachineRecipes.MIXING.recipeType();
        var manager = new RecipeManager(helper.getLevel().registryAccess());
        manager.replaceRecipes(new java.util.ArrayList<net.minecraft.world.item.crafting.RecipeHolder<?>>(
                helper.getLevel().getRecipeManager().getAllRecipesFor(type)));
        var ordered = MachineRecipeIndex.sorted(manager, type);
        helper.assertTrue(!ordered.isEmpty() && ordered == MachineRecipeIndex.sorted(manager, type), "machine ordering is reused");
        var items = new ItemStackHandler(6);
        var tanks = new MultiTank(() -> {}, 1000, 1000, 1000);
        int[] inspections = {0};
        var candidates = new RecipeCandidates(recipe -> { inspections[0]++; return false; });
        candidates.find(manager, type, items, tanks);
        int once = inspections[0];
        for (int i = 0; i < 100; i++) candidates.find(manager, type, items, tanks);
        helper.assertTrue(inspections[0] == once, "100 unchanged negative lookups do not rescan recipes");
        items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        candidates.find(manager, type, items, tanks);
        helper.assertTrue(inspections[0] == once * 2, "insertion invalidates immediately");
        items.getStackInSlot(0).grow(1);
        candidates.find(manager, type, items, tanks);
        helper.assertTrue(inspections[0] == once * 3, "direct count mutations also invalidate without callbacks");
        items.getStackInSlot(0).set(DataComponents.CUSTOM_NAME, Component.literal("Changed"));
        candidates.find(manager, type, items, tanks);
        helper.assertTrue(inspections[0] == once * 4, "component-only changes invalidate");
        tanks.setFluid(0, new FluidStack(Fluids.WATER, 100));
        candidates.find(manager, type, items, tanks);
        tanks.getFluidInTank(0).grow(1);
        candidates.find(manager, type, items, tanks);
        helper.assertTrue(inspections[0] == once * 6, "fluid insertion and direct amount changes invalidate");
        MachineRecipeIndex.invalidate();
        candidates.find(manager, type, items, tanks);
        helper.assertTrue(inspections[0] == once * 7, "tag update invalidates unchanged inputs");
        manager.replaceRecipes(List.copyOf(manager.getRecipes()));
        candidates.find(manager, type, items, tanks);
        helper.assertTrue(inspections[0] == once * 8, "same-ID recipe replacement invalidates immediately");
        manager.replaceRecipes(List.of());
        helper.assertTrue(MachineRecipeIndex.sorted(manager, type).isEmpty(), "removed recipes are not retained");
        helper.succeed();
    }
}
