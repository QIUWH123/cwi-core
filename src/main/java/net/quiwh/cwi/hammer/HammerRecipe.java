package net.quiwh.cwi.hammer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.List;

/** A stage-gated conversion performed by a charged CWI hammer. */
public final class HammerRecipe implements Recipe<RecipeInput> {
    public static final int MAX_ENTRIES = 64;
    private static final Codec<Float> CHANCE = Codec.FLOAT.validate(value -> Float.isFinite(value) && value >= 0 && value <= 1
            ? DataResult.success(value) : DataResult.error(() -> "Chance must be finite and in [0,1]"));
    private static final Codec<Integer> POSITIVE = Codec.intRange(1, Integer.MAX_VALUE);

    public record Drop(ItemStack stack, float chance) {
        public static final Codec<Drop> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.CODEC.fieldOf("stack").forGetter(Drop::stack),
                CHANCE.optionalFieldOf("chance", 1F).forGetter(Drop::chance)
        ).apply(i, Drop::new));

        public Drop {
            if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("Hammer drops cannot be empty");
            stack = stack.copy();
            if (!Float.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("Invalid drop chance");
        }

        @Override public ItemStack stack() { return stack.copy(); }
    }

    private final HammerRecipes.Entry entry;
    private final Ingredient input;
    private final ItemStack target;
    private final List<Drop> drops;
    private final int stage;

    public HammerRecipe(HammerRecipes.Entry entry, Ingredient input, ItemStack target, List<Drop> drops, int stage) {
        if (input == null || input.isEmpty()) throw new IllegalArgumentException("Hammer recipes need an input");
        if (target == null) throw new IllegalArgumentException("Hammer recipe target cannot be null");
        if (stage <= 0) throw new IllegalArgumentException("Hammer stage must be positive");
        if (drops.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many hammer drops");
        if (entry == HammerRecipes.BLOCK_CONVERSION && !target.isEmpty() && !(target.getItem() instanceof BlockItem))
            throw new IllegalArgumentException("Block conversions must target a block item or air");
        if (entry == HammerRecipes.DEPOT_CONVERSION && target.isEmpty())
            throw new IllegalArgumentException("Depot conversions need a non-empty target");
        this.entry = entry;
        this.input = input;
        this.target = target.copy();
        this.drops = drops.stream().map(drop -> new Drop(drop.stack(), drop.chance())).toList();
        this.stage = stage;
    }

    public HammerRecipes.Entry entry() { return entry; }
    public Ingredient input() { return input; }
    public ItemStack target() { return target.copy(); }
    public List<Drop> drops() { return drops; }
    public int stage() { return stage; }
    public boolean matches(ItemStack stack) { return stack != null && !stack.isEmpty() && input.test(stack); }

    @Override public boolean matches(RecipeInput input, Level level) {
        return input != null && input.size() == 1 && matches(input.getItem(0));
    }

    @Override public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) { return target(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width > 0 && height > 0; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return target(); }
    @Override public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(input);
        return ingredients;
    }
    @Override public RecipeSerializer<?> getSerializer() { return entry.serializer(); }
    @Override public RecipeType<?> getType() { return entry.recipeType(); }

    public static final class Serializer implements RecipeSerializer<HammerRecipe> {
        private final MapCodec<HammerRecipe> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, HammerRecipe> streamCodec;

        public Serializer(HammerRecipes.Entry entry) {
            MapCodec<Payload> raw = RecordCodecBuilder.mapCodec(i -> i.group(
                    Ingredient.CODEC_NONEMPTY.fieldOf("input").forGetter(Payload::input),
                    ItemStack.CODEC.optionalFieldOf("target", ItemStack.EMPTY).forGetter(Payload::target),
                    Drop.CODEC.listOf(0, MAX_ENTRIES).optionalFieldOf("drops", List.of()).forGetter(Payload::drops),
                    POSITIVE.optionalFieldOf("stage", 1).forGetter(Payload::stage)
            ).apply(i, Payload::new));
            codec = raw.flatXmap(data -> {
                try {
                    return DataResult.success(new HammerRecipe(entry, data.input, data.target, data.drops, data.stage));
                } catch (IllegalArgumentException error) {
                    return DataResult.error(error::getMessage);
                }
            }, recipe -> DataResult.success(new Payload(recipe.input, recipe.target, recipe.drops, recipe.stage)));
            streamCodec = ByteBufCodecs.fromCodecWithRegistries(codec.codec());
        }

        @Override public MapCodec<HammerRecipe> codec() { return codec; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, HammerRecipe> streamCodec() { return streamCodec; }
        private record Payload(Ingredient input, ItemStack target, List<Drop> drops, int stage) {}
    }
}
