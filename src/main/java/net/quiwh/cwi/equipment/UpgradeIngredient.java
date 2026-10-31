package net.quiwh.cwi.equipment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.crafting.Ingredient;

/** Count expands into distinct shapeless slots, matching the original pack. */
public record UpgradeIngredient(Ingredient ingredient, int count, boolean consume, int damage) {
    public static final Codec<UpgradeIngredient> CODEC = RecordCodecBuilder.create(i -> i.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(UpgradeIngredient::ingredient),
            Codec.intRange(1, 8).optionalFieldOf("count", 1).forGetter(UpgradeIngredient::count),
            Codec.BOOL.optionalFieldOf("consume", true).forGetter(UpgradeIngredient::consume),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("damage", 1).forGetter(UpgradeIngredient::damage)
    ).apply(i, UpgradeIngredient::new));

    public UpgradeIngredient {
        if (ingredient == null || ingredient.isEmpty()) throw new IllegalArgumentException("Upgrade ingredient cannot be empty");
        if (count < 1 || count > 8 || damage < 0) throw new IllegalArgumentException("Invalid upgrade ingredient count or damage");
    }
}
