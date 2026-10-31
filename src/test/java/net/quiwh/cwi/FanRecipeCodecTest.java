package net.quiwh.cwi;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.quiwh.cwi.processing.FanRecipe;
import net.quiwh.cwi.processing.FanRecipes;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Requires a correctly bootstrapped NeoForge environment, including normal transformations. */
public final class FanRecipeCodecTest {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registryAccess = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        verify(registryAccess, null);
        System.out.println("FanRecipeCodecTest: 10 assertions passed");
    }

    public static void verify(RegistryAccess registryAccess, Level level) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess);
        var serializer = new com.simibubi.create.content.processing.recipe.StandardProcessingRecipe.Serializer<FanRecipe>(
                params -> new FanRecipe(FanRecipes.CORRODING, params));
        var json = JsonParser.parseString("""
                {"ingredients":[{"item":"minecraft:iron_ingot"}],
                 "results":[{"id":"minecraft:gold_nugget","count":2,"chance":0.32}],
                 "processing_time":120}
                """);
        FanRecipe recipe = serializer.codec().codec().parse(ops, json).getOrThrow();
        require(recipe.matches(new SingleRecipeInput(new ItemStack(Items.IRON_INGOT)), level), "ingredient matches");
        require(!recipe.matches(new SingleRecipeInput(new ItemStack(Items.COPPER_INGOT)), level), "other ingredients do not match");
        require(!recipe.matches(new SingleRecipeInput(ItemStack.EMPTY), level), "empty input never matches");
        require(recipe.getProcessingDuration() == 120, "duration preserved");
        require(recipe.getRollableResults().getFirst().getChance() == 0.32F, "output chance preserved");
        require(recipe.getRollableResults().getFirst().getStack().getCount() == 2, "output count preserved");
        var encoded = serializer.codec().codec().encodeStart(ops, recipe).getOrThrow();
        FanRecipe roundTrip = serializer.codec().codec().parse(ops, encoded).getOrThrow();
        require(roundTrip.getProcessingDuration() == 120, "codec round trip");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
        try {
            serializer.streamCodec().encode(buffer, recipe);
            FanRecipe synced = serializer.streamCodec().decode(buffer);
            require(synced.getProcessingDuration() == 120, "network duration preserved");
            require(synced.getRollableResults().getFirst().getChance() == 0.32F, "network output chance preserved");
            require(synced.matches(new SingleRecipeInput(new ItemStack(Items.IRON_INGOT)), level),
                    "network ingredient preserved");
        } finally {
            buffer.release();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
