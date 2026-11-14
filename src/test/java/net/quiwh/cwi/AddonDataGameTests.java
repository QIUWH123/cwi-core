package net.quiwh.cwi;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Upstream data must remain usable after Core removes obsolete materials. */
@PrefixGameTestTemplate(false)
public final class AddonDataGameTests {
    @EventBusSubscriber(modid = CwiCore.ID)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterGameTestsEvent event) { event.register(AddonDataGameTests.class); }
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void retainedOreRecipesLoad(GameTestHelper helper) {
        verify(helper.getLevel());
        helper.succeed();
    }

    public static void verify(Level level) {
        if (ModList.get().isLoaded("vintageimprovements")) {
            for (String stone : new String[]{"asurine", "crimsite"}) for (String suffix : new String[]{"", "_recycling"}) {
                var id = ResourceLocation.parse("create:crushing/" + stone + suffix);
                var holder = level.getRecipeManager().byKey(id).orElseThrow(() -> new AssertionError("Missing retained ore recipe " + id));
                if (!(holder.value() instanceof ProcessingRecipe<?, ?> recipe)) throw new AssertionError("Wrong ore recipe type " + id);
                if (recipe.getProcessingDuration() != 250 || recipe.getRollableResults().size() != 2)
                    throw new AssertionError("Ore duration and ordinary outputs must survive " + id);
                float expected = stone.equals("asurine") ? .3f : .4f;
                for (var output : recipe.getRollableResults()) {
                    var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(output.getStack().getItem());
                    if (key.getNamespace().equals("vintageimprovements") || output.getStack().getCount() != 1 || output.getChance() != expected)
                        throw new AssertionError("Ore output/chance changed " + id + ": " + key);
                }
            }
        }
    }
}
