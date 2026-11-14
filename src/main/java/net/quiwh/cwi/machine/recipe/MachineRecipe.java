package net.quiwh.cwi.machine.recipe;

import static net.quiwh.cwi.machine.recipe.MachineRecipeCodecs.CHANCE;
import static net.quiwh.cwi.machine.recipe.MachineRecipeCodecs.POSITIVE;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.foundation.codec.CreateCodecs;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.quiwh.cwi.machine.MachineKinetics;

/** Counted machine IO. Chance zero means never consume/produce, including on the network. */
public final class MachineRecipe extends StandardProcessingRecipe<RecipeInput> {
    public static final int MAX_ENTRIES = 64;

    public record ItemInput(Ingredient ingredient, int count, float chance, EntrySettings settings) {
        private static final Codec<ItemInput> ADVANCED_CODEC = RecordCodecBuilder.create(i -> i.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(ItemInput::ingredient),
                POSITIVE.optionalFieldOf("count", 1).forGetter(ItemInput::count),
                CHANCE.optionalFieldOf("chance", 1f).forGetter(ItemInput::chance),
                EntrySettings.CODEC.forGetter(ItemInput::settings)
        ).apply(i, ItemInput::new));
        public static final Codec<ItemInput> CODEC = Codec.either(ADVANCED_CODEC, Ingredient.CODEC_NONEMPTY).xmap(
                value -> value.map(v -> v, v -> new ItemInput(v, 1, 1, false)),
                v -> v.count() != 1 || v.chance() != 1 || !v.settings().equals(EntrySettings.DEFAULT) ? Either.left(v) : Either.right(v.ingredient()));
        public ItemInput(Ingredient ingredient, int count, float chance, boolean perTick) { this(ingredient, count, chance, EntrySettings.tick(perTick)); }
        public boolean perTick() { return settings.periodic(); }
        public ItemInput {
            settings.validate(chance, true, false, count);
            if (ingredient == null || ingredient.isEmpty() || count <= 0) throw new IllegalArgumentException("Empty/nonpositive item input");
            requireChance(chance);
            if (settings.damage() > 0 && ingredient.getItems().length > 0 && Arrays.stream(ingredient.getItems())
                    .noneMatch(stack -> stack.isDamageableItem() && !stack.has(net.minecraft.core.component.DataComponents.UNBREAKABLE)))
                throw new IllegalArgumentException("Damage inputs must match damageable, breakable tools");
        }
    }

    public record FluidInput(SizedFluidIngredient ingredient, float chance, EntrySettings settings) {
        private static final Codec<FluidInput> ADVANCED_CODEC = RecordCodecBuilder.create(i -> i.group(
                FluidIngredient.CODEC_NON_EMPTY.fieldOf("ingredient").forGetter(v -> v.ingredient().ingredient()),
                POSITIVE.fieldOf("amount").forGetter(v -> v.ingredient().amount()),
                CHANCE.optionalFieldOf("chance", 1f).forGetter(FluidInput::chance),
                EntrySettings.CODEC.forGetter(FluidInput::settings)
        ).apply(i, (ingredient, amount, chance, perTick) -> new FluidInput(new SizedFluidIngredient(ingredient, amount), chance, perTick)));
        public static final Codec<FluidInput> CODEC = Codec.either(ADVANCED_CODEC, CreateCodecs.SIZED_FLUID_INGREDIENT).xmap(
                value -> value.map(v -> v, v -> new FluidInput(v, 1, false)),
                v -> v.chance() != 1 || !v.settings().equals(EntrySettings.DEFAULT) ? Either.left(v) : Either.right(v.ingredient()));
        public FluidInput(SizedFluidIngredient ingredient, float chance, boolean perTick) { this(ingredient, chance, EntrySettings.tick(perTick)); }
        public boolean perTick() { return settings.periodic(); }
        public FluidInput {
            settings.validate(chance, true, true, 1); if (ingredient == null) throw new IllegalArgumentException("Null fluid input"); requireChance(chance); }
    }

    public record ItemOutput(ItemStack stack, float chance, EntrySettings settings) {
        private static final Codec<ItemOutput> ADVANCED_CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.CODEC.fieldOf("stack").forGetter(ItemOutput::stack),
                CHANCE.optionalFieldOf("chance", 1f).forGetter(ItemOutput::chance),
                EntrySettings.CODEC.forGetter(ItemOutput::settings)
        ).apply(i, ItemOutput::new));
        private static final Codec<ProcessingOutput> NATIVE_CODEC = ProcessingOutput.CODEC.validate(output ->
                Float.isFinite(output.getChance()) && output.getChance() <= 1
                        ? DataResult.success(output) : DataResult.error(() -> "Chance must be finite and in [0,1]"));
        public static final Codec<ItemOutput> CODEC = Codec.either(ADVANCED_CODEC, NATIVE_CODEC).xmap(
                value -> value.map(v -> v, v -> new ItemOutput(v.getStack(), v.getChance(), false)),
                v -> !v.settings().equals(EntrySettings.DEFAULT) || v.chance() == 0 ? Either.left(v) : Either.right(new ProcessingOutput(v.stack(), v.chance())));
        public ItemOutput(ItemStack stack, float chance, boolean perTick) { this(stack, chance, EntrySettings.tick(perTick)); }
        public boolean perTick() { return settings.periodic(); }
        public ItemOutput {
            settings.validate(chance, false, false, 1);
            if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("Empty item output");
            stack = stack.copy(); requireChance(chance);
        }
        @Override public ItemStack stack() { return stack.copy(); }
    }

    public record FluidOutput(FluidStack stack, float chance, EntrySettings settings) {
        private static final Codec<FluidOutput> ADVANCED_CODEC = RecordCodecBuilder.create(i -> i.group(
                FluidStack.CODEC.fieldOf("stack").forGetter(FluidOutput::stack),
                CHANCE.optionalFieldOf("chance", 1f).forGetter(FluidOutput::chance),
                EntrySettings.CODEC.forGetter(FluidOutput::settings)
        ).apply(i, FluidOutput::new));
        public static final Codec<FluidOutput> CODEC = Codec.either(ADVANCED_CODEC, FluidStack.CODEC).xmap(
                value -> value.map(v -> v, v -> new FluidOutput(v, 1, false)),
                v -> v.chance() != 1 || !v.settings().equals(EntrySettings.DEFAULT) ? Either.left(v) : Either.right(v.stack()));
        public FluidOutput(FluidStack stack, float chance, boolean perTick) { this(stack, chance, EntrySettings.tick(perTick)); }
        public boolean perTick() { return settings.periodic(); }
        public FluidOutput {
            settings.validate(chance, false, true, 1);
            if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("Empty fluid output");
            stack = stack.copy(); requireChance(chance);
        }
        @Override public FluidStack stack() { return stack.copy(); }
    }

    private final MachineRecipes.Entry entry;
    private final List<ItemInput> itemInputs;
    private final List<FluidInput> fluidInputs;
    private final List<ItemOutput> itemOutputs;
    private final List<FluidOutput> fluidOutputs;
    private final int priority;
    private final List<OutputPool> outputPools;
    private final float minimumRpm;
    private final int minimumHeat;
    private final int maximumHeat;
    private final Optional<IncubationDisplay> incubationDisplay;
    private volatile HolderLookup.Provider fingerprintRegistries;
    private volatile String cachedFingerprint;

    public MachineRecipe(MachineRecipes.Entry entry, List<ItemInput> itemInputs, List<FluidInput> fluidInputs,
                         List<ItemOutput> itemOutputs, List<FluidOutput> fluidOutputs, int duration,
                         float minimumRpm, int minimumHeat, HeatCondition heat) {
        this(entry, itemInputs, fluidInputs, itemOutputs, fluidOutputs, duration, minimumRpm, minimumHeat, heat, Optional.empty());
    }

    public MachineRecipe(MachineRecipes.Entry entry, List<ItemInput> itemInputs, List<FluidInput> fluidInputs,
                         List<ItemOutput> itemOutputs, List<FluidOutput> fluidOutputs, int duration,
                         float minimumRpm, int minimumHeat, HeatCondition heat, Optional<IncubationDisplay> incubationDisplay) {
        this(entry, itemInputs, fluidInputs, itemOutputs, fluidOutputs, duration, minimumRpm, minimumHeat,
                Integer.MAX_VALUE, heat, incubationDisplay);
    }

    public MachineRecipe(MachineRecipes.Entry entry, List<ItemInput> itemInputs, List<FluidInput> fluidInputs,
                         List<ItemOutput> itemOutputs, List<FluidOutput> fluidOutputs, int duration,
                         float minimumRpm, int minimumHeat, int maximumHeat, HeatCondition heat,
                         Optional<IncubationDisplay> incubationDisplay) {
        this(entry, itemInputs, fluidInputs, itemOutputs, fluidOutputs, duration, minimumRpm, minimumHeat,
                maximumHeat, heat, incubationDisplay, 0, List.of());
    }

    public MachineRecipe(MachineRecipes.Entry entry, List<ItemInput> itemInputs, List<FluidInput> fluidInputs,
                         List<ItemOutput> itemOutputs, List<FluidOutput> fluidOutputs, int duration,
                         float minimumRpm, int minimumHeat, int maximumHeat, HeatCondition heat,
                         Optional<IncubationDisplay> incubationDisplay, int priority, List<OutputPool> outputPools) {
        super(entry, new Params(itemInputs, fluidInputs, itemOutputs, fluidOutputs, duration, heat));
        if (itemInputs.isEmpty() && fluidInputs.isEmpty()) throw new IllegalArgumentException("Machine recipes need an input");
        if (duration <= 0 || !Float.isFinite(minimumRpm) || minimumRpm < 0 || minimumHeat < 0 || maximumHeat < 0)
            throw new IllegalArgumentException("Invalid duration/RPM/temperature");
        for (var list : List.of(itemInputs, fluidInputs, itemOutputs, fluidOutputs))
            if (list.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many recipe entries");
        String validation = MachineRecipeValidation.error(entry, itemInputs, fluidInputs, itemOutputs, fluidOutputs,
                minimumRpm, minimumHeat, maximumHeat, heat);
        if (validation != null) throw new IllegalArgumentException(validation);
        if (incubationDisplay.isPresent() && entry != MachineRecipes.INCUBATING)
            throw new IllegalArgumentException("Only incubator recipes support incubation_display");
        if (outputPools.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many output pools");
        if (entry == MachineRecipes.INCUBATING && (priority != 0 || !outputPools.isEmpty()
                || itemInputs.stream().anyMatch(v -> v.settings().advanced())
                || fluidInputs.stream().anyMatch(v -> v.settings().advanced())
                || itemOutputs.stream().anyMatch(v -> v.settings().advanced())
                || fluidOutputs.stream().anyMatch(v -> v.settings().advanced())))
            throw new IllegalArgumentException("Advanced IO and priority are unsupported by incubators");
        this.priority = priority; this.outputPools = List.copyOf(outputPools);
        this.entry = entry;
        this.itemInputs = List.copyOf(itemInputs);
        this.fluidInputs = List.copyOf(fluidInputs);
        this.itemOutputs = List.copyOf(itemOutputs);
        this.fluidOutputs = List.copyOf(fluidOutputs);
        this.minimumRpm = minimumRpm;
        this.minimumHeat = minimumHeat;
        this.maximumHeat = maximumHeat;
        this.incubationDisplay = incubationDisplay;
    }

    public int priority() { return priority; }
    public List<OutputPool> outputPools() { return outputPools; }
    public boolean periodic() { return itemInputs.stream().anyMatch(ItemInput::perTick) || fluidInputs.stream().anyMatch(FluidInput::perTick)
            || itemOutputs.stream().anyMatch(ItemOutput::perTick) || fluidOutputs.stream().anyMatch(FluidOutput::perTick); }
    public List<ItemInput> itemInputs() { return itemInputs; }
    public List<FluidInput> fluidInputs() { return fluidInputs; }
    public List<ItemOutput> itemOutputs() { return itemOutputs; }
    public List<FluidOutput> fluidOutputs() { return fluidOutputs; }
    public float minimumRpm() { return minimumRpm; }
    /** Absolute furnace temperature threshold; independent from Create's burner heat stage. */
    public int minimumHeat() { return minimumHeat; }
    /** Inclusive furnace temperature ceiling; Integer.MAX_VALUE means no upper limit. */
    public int maximumHeat() { return maximumHeat; }
    public Optional<IncubationDisplay> incubationDisplay() { return incubationDisplay; }
    public int durationAtRpm(float rpm) {
        if (!Float.isFinite(rpm) || Math.abs(rpm) <= 0) return Integer.MAX_VALUE;
        return MachineKinetics.mixerDuration(getProcessingDuration(), rpm);
    }

    /** Recipe identity includes semantic content so /reload cannot reuse stale saved progress. */
    public String fingerprint(HolderLookup.Provider registries) {
        if (fingerprintRegistries == registries && cachedFingerprint != null) return cachedFingerprint;
        JsonElement encoded = entry.serializer().codec().codec().encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), this).getOrThrow();
        encoded.getAsJsonObject().remove("priority");
        if (encoded.getAsJsonObject().has("output_pools")) encoded.getAsJsonObject().getAsJsonArray("output_pools")
                .forEach(pool -> pool.getAsJsonObject().remove("show_outputs"));
        try {
            byte[] bytes = (entry.getId() + "\n" + canonical(encoded)).getBytes(StandardCharsets.UTF_8);
            String result = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            cachedFingerprint = result;
            fingerprintRegistries = registries;
            return result;
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static JsonElement canonical(JsonElement json) {
        if (json.isJsonObject()) {
            JsonObject sorted = new JsonObject();
            json.getAsJsonObject().keySet().stream().sorted().forEach(key -> sorted.add(key, canonical(json.getAsJsonObject().get(key))));
            return sorted;
        }
        if (json.isJsonArray()) {
            JsonArray sorted = new JsonArray();
            json.getAsJsonArray().forEach(value -> sorted.add(canonical(value)));
            return sorted;
        }
        return json;
    }

    @Override public boolean matches(RecipeInput input, Level level) {
        return MachineRecipeMatching.matches(itemInputs, fluidInputs, input);
    }

    public record Input(List<ItemStack> items, List<FluidStack> fluids) implements RecipeInput {
        public Input { items = List.copyOf(items); fluids = List.copyOf(fluids); }
        @Override public ItemStack getItem(int slot) { return items.get(slot); }
        @Override public int size() { return items.size(); }
        @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty) && fluids.stream().allMatch(FluidStack::isEmpty); }
    }

    @Override protected int getMaxInputCount() { return MAX_ENTRIES * MAX_ENTRIES; }
    @Override protected int getMaxOutputCount() { return MAX_ENTRIES; }
    @Override protected int getMaxFluidInputCount() { return MAX_ENTRIES; }
    @Override protected int getMaxFluidOutputCount() { return MAX_ENTRIES; }
    @Override protected boolean canSpecifyDuration() { return true; }
    @Override protected boolean canRequireHeat() { return true; }

    private static void requireChance(float chance) {
        if (!Float.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("Chance must be finite and in [0,1]");
    }

    private static final class Params extends ProcessingRecipeParams {
        private Params(List<ItemInput> items, List<FluidInput> fluids, List<ItemOutput> outputs,
                       List<FluidOutput> fluidOutputs, int duration, HeatCondition heat) {
            long units = items.stream().mapToLong(ItemInput::count).sum();
            if (units > MAX_ENTRIES * MAX_ENTRIES) throw new IllegalArgumentException("Too many item input units (maximum 4096)");
            items.forEach(input -> {
                for (int unit = 0; unit < input.count(); unit++) ingredients.add(input.ingredient());
            });
            fluids.forEach(input -> fluidIngredients.add(input.ingredient()));
            outputs.forEach(output -> results.add(new ProcessingOutput(output.stack(), output.chance())));
            fluidOutputs.forEach(output -> fluidResults.add(output.stack()));
            processingDuration = duration; requiredHeat = heat;
        }
    }

    public static final class Serializer implements RecipeSerializer<MachineRecipe> {
        private final MapCodec<MachineRecipe> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> streamCodec;
        public Serializer(MachineRecipes.Entry entry) {
            codec = MachineRecipeCodecs.create(entry);
            streamCodec = ByteBufCodecs.fromCodecWithRegistries(codec.codec());
        }
        @Override public MapCodec<MachineRecipe> codec() { return codec; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> streamCodec() { return streamCodec; }
    }
}
