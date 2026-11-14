package net.quiwh.cwi;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.interaction.InteractionRecipe;
import net.quiwh.cwi.interaction.InteractionRecipes;
import net.quiwh.cwi.interaction.WorldHazards;

/** Counts actual damage attempts: native invulnerability must not conceal duplicate dispatch. */
@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class FluidHazardGameTests {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void repeatedExposureReusesResolvedDamageSource(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var saved = List.copyOf(manager.getRecipes());
        try {
            install(manager, saved, rule(helper, "cached", "{\"fluid\":\"minecraft:water\"}", 2, 10));
            var pig = new ExposurePig(helper.getLevel());
            pig.contact(Fluids.WATER.getFluidType(), .5);
            WorldHazards.tick(pig, 0);
            WorldHazards.tick(pig, 10);
            helper.assertTrue(pig.sources.size() == 2, "Each due exposure attempts damage");
            helper.assertTrue(pig.sources.get(0) == pig.sources.get(1),
                    "Repeated fluid exposure must reuse its resolved immutable damage source");
        } finally {
            manager.replaceRecipes(saved);
            InteractionRecipes.invalidate();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void overlappingFluidsRunEachDueRuleOnceInIdOrder(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var saved = List.copyOf(manager.getRecipes());
        try {
            install(manager, saved,
                    rule(helper, "a_water", "[{\"fluid\":\"minecraft:water\"},{\"fluid\":\"minecraft:flowing_water\"}]", 1, 2),
                    rule(helper, "b_lava", "{\"fluid\":\"minecraft:lava\"}", 2, 3),
                    rule(helper, "c_both", "[{\"fluid\":\"minecraft:water\"},{\"fluid\":\"minecraft:lava\"}]", 3, 6));
            var pig = new ExposurePig(helper.getLevel());
            pig.contact(Fluids.WATER.getFluidType(), .5);
            pig.contact(Fluids.LAVA.getFluidType(), .25);
            WorldHazards.tick(pig, 6);
            helper.assertTrue(pig.amounts.equals(List.of(1F, 2F, 3F)),
                    "Source/flowing aliases and overlapping fluid groups must not duplicate or reorder rules");
            pig.clearAttempts();
            WorldHazards.tick(pig, 4);
            helper.assertTrue(pig.amounts.equals(List.of(1F)), "Different rule intervals remain independent");
            pig.clearAttempts();
            pig.contact(Fluids.LAVA.getFluidType(), 0);
            WorldHazards.tick(pig, 6);
            helper.assertTrue(pig.amounts.equals(List.of(1F, 3F)), "Merged matches must not mutate the cached water group");
            pig.clearAttempts();
            pig.contact(Fluids.WATER.getFluidType(), 0);
            pig.contact(Fluids.LAVA.getFluidType(), .5);
            WorldHazards.tick(pig, 6);
            helper.assertTrue(pig.amounts.equals(List.of(2F, 3F)), "Merged matches must not mutate the cached lava group");
            pig.clearAttempts();
            pig.contact(Fluids.LAVA.getFluidType(), 0);
            WorldHazards.tick(pig, 6);
            helper.assertTrue(pig.sources.isEmpty(), "Leaving all fluids stops exposure");
        } finally {
            manager.replaceRecipes(saved);
            InteractionRecipes.invalidate();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void flowingSurfaceEffectsAndMissingIdsKeepNativeBehavior(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var saved = List.copyOf(manager.getRecipes());
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var oldBlock = level.getBlockState(pos);
        try {
            install(manager, saved, ruleJson(helper, "surface", """
                    {"fluid":{"fluid":"minecraft:water"},"damage":5,"damage_type":"cwi:missing_damage",
                     "interval":2,"fire_ticks":60,"effects":[
                       {"id":"cwi:missing_effect","duration":40,"amplifier":0},
                       {"id":"minecraft:slowness","duration":40,"amplifier":1}]}
                    """));
            level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 7));
            var pig = new ExposurePig(level);
            pig.setNoAi(true);
            pig.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
            pig.tick();
            WorldHazards.tick(pig, 0);
            helper.assertTrue(!pig.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Above a shallow fluid surface is not contact");
            pig.setPos(pos.getX() + .5, pos.getY() + .01, pos.getZ() + .5);
            pig.tick();
            helper.assertTrue(pig.getFluidTypeHeight(Fluids.WATER.getFluidType()) > 0, "Native flowing contact is established");
            WorldHazards.tick(pig, 1);
            helper.assertTrue(!pig.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Effects respect the configured interval");
            WorldHazards.tick(pig, 2);
            var effect = pig.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            helper.assertTrue(pig.sources.isEmpty() && pig.getRemainingFireTicks() == 60
                            && effect != null && effect.getAmplifier() == 1 && effect.getDuration() == 40,
                    "Missing IDs stay dormant while valid effects and ignition apply to flowing water");
            pig.setRemainingFireTicks(100);
            WorldHazards.tick(pig, 4);
            helper.assertTrue(pig.getRemainingFireTicks() == 100, "Fluid hazards never shorten existing ignition");
            var second = new ExposurePig(level);
            second.contact(Fluids.WATER.getFluidType(), .5);
            WorldHazards.tick(second, 0);
            helper.assertTrue(second.getEffect(MobEffects.MOVEMENT_SLOWDOWN) != effect,
                    "Resolved effect holders are shared, mutable effect instances remain per entity");
        } finally {
            level.setBlockAndUpdate(pos, oldBlock);
            manager.replaceRecipes(saved);
            InteractionRecipes.invalidate();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void moreThanOneMaskWordRetainsEveryRule(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var saved = List.copyOf(manager.getRecipes());
        try {
            var recipes = new ArrayList<RecipeHolder<?>>();
            for (var recipe : saved) if (recipe.value().getType() != InteractionRecipes.Kind.FLUID_HAZARD.type()) recipes.add(recipe);
            for (int i = 0; i < 130; i++)
                recipes.add(rule(helper, "many_%03d".formatted(i), "{\"fluid\":\"minecraft:water\"}", 1, 1));
            manager.replaceRecipes(recipes);
            var pig = new ExposurePig(helper.getLevel());
            pig.contact(Fluids.WATER.getFluidType(), .5);
            WorldHazards.tick(pig, 0);
            helper.assertTrue(pig.sources.size() == 130, "Every rule beyond the first 64 and 128 must execute once");
        } finally {
            manager.replaceRecipes(saved);
            InteractionRecipes.invalidate();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void replacementRemovalAndInvalidationRefreshFluidActions(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var saved = List.copyOf(manager.getRecipes());
        try {
            install(manager, saved, rule(helper, "replace", "{\"fluid\":\"minecraft:water\"}", 2, 1));
            var pig = new ExposurePig(helper.getLevel());
            pig.contact(Fluids.WATER.getFluidType(), .5);
            WorldHazards.tick(pig, 0);
            var original = pig.sources.getFirst();
            install(manager, saved, rule(helper, "replace", "{\"fluid\":\"minecraft:water\"}", 5, 1));
            pig.clearAttempts();
            WorldHazards.tick(pig, 0);
            helper.assertTrue(pig.amounts.equals(List.of(5F)), "Same-ID replacement updates the compiled action immediately");
            var replaced = pig.sources.getFirst();
            helper.assertTrue(replaced != original, "Replacement discards the old resolved source");
            InteractionRecipes.invalidate();
            pig.clearAttempts();
            WorldHazards.tick(pig, 0);
            helper.assertTrue(pig.amounts.equals(List.of(5F)) && pig.sources.getFirst() != replaced,
                    "Tag invalidation discards compiled actions even when recipe objects stay the same");
            install(manager, saved);
            pig.clearAttempts();
            WorldHazards.tick(pig, 0);
            helper.assertTrue(pig.sources.isEmpty(), "Removed fluid hazards must stop immediately");
            install(manager, saved, rule(helper, "replace", "{\"fluid\":\"minecraft:water\"}", 3, 1));
            WorldHazards.tick(pig, 0);
            helper.assertTrue(pig.amounts.equals(List.of(3F)), "New rules replace a cached empty result");
        } finally {
            manager.replaceRecipes(saved);
            InteractionRecipes.invalidate();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void tagRebindingRefreshesAlreadyResolvedFluidIngredients(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var saved = List.copyOf(manager.getRecipes());
        var registry = BuiltInRegistries.FLUID;
        var savedTags = new HashMap<TagKey<Fluid>, List<Holder<Fluid>>>();
        registry.getTags().forEach(pair -> savedTags.put(pair.getFirst(), List.copyOf(pair.getSecond().stream().toList())));
        var tag = TagKey.create(Registries.FLUID, ResourceLocation.parse("cwi:fluid_cache/contact"));
        var tags = new HashMap<>(savedTags);
        try {
            tags.put(tag, List.of(Fluids.WATER.builtInRegistryHolder()));
            registry.bindTags(tags);
            var rule = rule(helper, "tag", "{\"tag\":\"cwi:fluid_cache/contact\"}", 2, 1);
            install(manager, saved, rule);
            var pig = new ExposurePig(level);
            pig.contact(Fluids.WATER.getFluidType(), .5);
            WorldHazards.tick(pig, 0);
            helper.assertTrue(pig.amounts.equals(List.of(2F)), "Initial tagged water causes exposure");
            // Warm the native ingredient's own stack cache as well as Core's rule cache.
            ((net.quiwh.cwi.interaction.InteractionRules.FluidHazard) rule.value().rule()).fluid().getStacks();
            tags.put(tag, List.of(Fluids.LAVA.builtInRegistryHolder()));
            registry.bindTags(tags);
            InteractionRecipes.invalidate();
            pig.clearAttempts();
            WorldHazards.tick(pig, 0);
            helper.assertTrue(pig.amounts.isEmpty(), "Removed tag members stop matching without recipe replacement");
            pig.contact(Fluids.WATER.getFluidType(), 0);
            pig.contact(Fluids.LAVA.getFluidType(), .5);
            WorldHazards.tick(pig, 0);
            helper.assertTrue(pig.amounts.equals(List.of(2F)), "New tag members match after invalidation");
        } finally {
            savedTags.putIfAbsent(tag, List.of());
            registry.bindTags(savedTags);
            manager.replaceRecipes(saved);
            InteractionRecipes.invalidate();
        }
        helper.succeed();
    }

    private static RecipeHolder<InteractionRecipe> rule(GameTestHelper helper, String path, String fluid,
                                                        float damage, int interval) {
        return ruleJson(helper, path, """
                {"fluid":%s,"damage":%s,"damage_type":"minecraft:magic","interval":%d}
                """.formatted(fluid, damage, interval));
    }

    private static RecipeHolder<InteractionRecipe> ruleJson(GameTestHelper helper, String path, String json) {
        var recipe = InteractionRecipes.Kind.FLUID_HAZARD.serializer().codec().codec()
                .parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(json)).getOrThrow();
        return new RecipeHolder<>(ResourceLocation.parse("cwi:fluid_cache/" + path), recipe);
    }

    @SafeVarargs
    private static void install(RecipeManager manager, List<RecipeHolder<?>> saved, RecipeHolder<InteractionRecipe>... rules) {
        var recipes = new ArrayList<RecipeHolder<?>>();
        for (var recipe : saved) if (recipe.value().getType() != InteractionRecipes.Kind.FLUID_HAZARD.type()) recipes.add(recipe);
        recipes.addAll(List.of(rules));
        manager.replaceRecipes(recipes);
    }

    private static final class ExposurePig extends Pig {
        private final List<DamageSource> sources = new ArrayList<>();
        private final List<Float> amounts = new ArrayList<>();

        private ExposurePig(Level level) { super(EntityType.PIG, level); }
        private void contact(FluidType type, double height) { setFluidTypeHeight(type, height); }
        private void clearAttempts() { sources.clear(); amounts.clear(); }
        @Override public boolean hurt(DamageSource source, float amount) {
            sources.add(source);
            amounts.add(amount);
            return super.hurt(source, amount);
        }
    }
}
