package net.quiwh.cwi.interaction;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.util.List;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.CwiCore;

/** A shared recipe manager must never reuse damage holders from a different registry context. */
@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class FluidHazardContextGameTests {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void resolvedActionsFollowTheirRegistryContext(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var saved = List.copyOf(manager.getRecipes());
        try {
            var recipe = InteractionRecipes.Kind.FLUID_HAZARD.serializer().codec().codec()
                    .parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString("""
                            {"fluid":{"fluid":"minecraft:water"},"damage":2,"interval":1}
                            """)).getOrThrow();
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("cwi:fluid_cache/context"), recipe)));
            var pig = new ContextPig(level);
            InteractionRecipes.fluidHazards(manager, level.registryAccess()).tick(pig, 0);
            helper.assertTrue(pig.source != null && pig.source.type().msgId().equals("magic"), "Original damage registry is used");

            var registry = new MappedRegistry<DamageType>(Registries.DAMAGE_TYPE, Lifecycle.stable());
            Registry.register(registry, ResourceLocation.parse("minecraft:magic"), new DamageType("other_context", 0));
            registry.freeze();
            var other = new RegistryAccess.ImmutableRegistryAccess(List.of(registry));
            InteractionRecipes.fluidHazards(manager, other).tick(pig, 1);
            helper.assertTrue(pig.source.type().msgId().equals("other_context"), "Same recipes resolve against the replacement registry context");
            InteractionRecipes.fluidHazards(manager, level.registryAccess()).tick(pig, 2);
            helper.assertTrue(pig.source.type().msgId().equals("magic"), "Returning to the original context restores its own holder");
        } finally {
            manager.replaceRecipes(saved);
            InteractionRecipes.invalidate();
        }
        helper.succeed();
    }

    private static final class ContextPig extends Pig {
        private DamageSource source;
        private ContextPig(Level level) {
            super(EntityType.PIG, level);
            setFluidTypeHeight(Fluids.WATER.getFluidType(), .5);
        }
        @Override public boolean hurt(DamageSource source, float amount) {
            this.source = source;
            return super.hurt(source, amount);
        }
    }
}
