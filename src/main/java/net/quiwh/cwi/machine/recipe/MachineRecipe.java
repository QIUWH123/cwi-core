package net.quiwh.cwi.machine.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
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
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/** Counted machine IO. Chance zero means never consume/produce, including on the network. */
public final class MachineRecipe extends StandardProcessingRecipe<RecipeInput> {
    public static final int MAX_ENTRIES = 64;
    private static final Codec<Float> CHANCE = Codec.FLOAT.validate(value -> Float.isFinite(value) && value >= 0 && value <= 1
            ? DataResult.success(value) : DataResult.error(() -> "Chance must be finite and in [0,1]"));
    private static final Codec<Float> RPM = Codec.FLOAT.validate(value -> Float.isFinite(value) && value >= 0
            ? DataResult.success(value) : DataResult.error(() -> "RPM must be finite and nonnegative"));
    private static final Codec<Integer> POSITIVE = Codec.intRange(1, Integer.MAX_VALUE);

    public record ItemInput(Ingredient ingredient, int count, float chance, boolean perTick) {
        public static final Codec<ItemInput> CODEC = RecordCodecBuilder.create(i -> i.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(ItemInput::ingredient),
                POSITIVE.optionalFieldOf("count", 1).forGetter(ItemInput::count),
                CHANCE.optionalFieldOf("chance", 1f).forGetter(ItemInput::chance),
                Codec.BOOL.optionalFieldOf("per_tick", false).forGetter(ItemInput::perTick)
        ).apply(i, ItemInput::new));
        public ItemInput {
            if (ingredient == null || ingredient.isEmpty() || count <= 0) throw new IllegalArgumentException("Empty/nonpositive item input");
            requireChance(chance);
        }
    }

    public record FluidInput(SizedFluidIngredient ingredient, float chance, boolean perTick) {
        public static final Codec<FluidInput> CODEC = RecordCodecBuilder.create(i -> i.group(
                FluidIngredient.CODEC_NON_EMPTY.fieldOf("ingredient").forGetter(v -> v.ingredient().ingredient()),
                POSITIVE.fieldOf("amount").forGetter(v -> v.ingredient().amount()),
                CHANCE.optionalFieldOf("chance", 1f).forGetter(FluidInput::chance),
                Codec.BOOL.optionalFieldOf("per_tick", false).forGetter(FluidInput::perTick)
        ).apply(i, (ingredient, amount, chance, perTick) -> new FluidInput(new SizedFluidIngredient(ingredient, amount), chance, perTick)));
        public FluidInput { if (ingredient == null) throw new IllegalArgumentException("Null fluid input"); requireChance(chance); }
    }

    public record ItemOutput(ItemStack stack, float chance, boolean perTick) {
        public static final Codec<ItemOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.CODEC.fieldOf("stack").forGetter(ItemOutput::stack),
                CHANCE.optionalFieldOf("chance", 1f).forGetter(ItemOutput::chance),
                Codec.BOOL.optionalFieldOf("per_tick", false).forGetter(ItemOutput::perTick)
        ).apply(i, ItemOutput::new));
        public ItemOutput {
            if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("Empty item output");
            stack = stack.copy(); requireChance(chance);
        }
        @Override public ItemStack stack() { return stack.copy(); }
    }

    public record FluidOutput(FluidStack stack, float chance, boolean perTick) {
        public static final Codec<FluidOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
                FluidStack.CODEC.fieldOf("stack").forGetter(FluidOutput::stack),
                CHANCE.optionalFieldOf("chance", 1f).forGetter(FluidOutput::chance),
                Codec.BOOL.optionalFieldOf("per_tick", false).forGetter(FluidOutput::perTick)
        ).apply(i, FluidOutput::new));
        public FluidOutput {
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
    private final float minimumRpm;
    private final int minimumHeat;
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
        super(entry, new Params(itemInputs, fluidInputs, itemOutputs, fluidOutputs, duration, heat));
        if (itemInputs.isEmpty() && fluidInputs.isEmpty()) throw new IllegalArgumentException("Machine recipes need an input");
        if (duration <= 0 || !Float.isFinite(minimumRpm) || minimumRpm < 0 || minimumHeat < 0)
            throw new IllegalArgumentException("Invalid duration/RPM/temperature");
        for (var list : List.of(itemInputs, fluidInputs, itemOutputs, fluidOutputs))
            if (list.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many recipe entries");
        String validation = MachineRecipeValidation.error(entry, itemInputs, fluidInputs, itemOutputs, fluidOutputs, minimumRpm, minimumHeat, heat);
        if (validation != null) throw new IllegalArgumentException(validation);
        if (incubationDisplay.isPresent() && entry != MachineRecipes.INCUBATING)
            throw new IllegalArgumentException("Only incubator recipes support incubation_display");
        this.entry = entry;
        this.itemInputs = List.copyOf(itemInputs);
        this.fluidInputs = List.copyOf(fluidInputs);
        this.itemOutputs = List.copyOf(itemOutputs);
        this.fluidOutputs = List.copyOf(fluidOutputs);
        this.minimumRpm = minimumRpm;
        this.minimumHeat = minimumHeat;
        this.incubationDisplay = incubationDisplay;
    }

    public List<ItemInput> itemInputs() { return itemInputs; }
    public List<FluidInput> fluidInputs() { return fluidInputs; }
    public List<ItemOutput> itemOutputs() { return itemOutputs; }
    public List<FluidOutput> fluidOutputs() { return fluidOutputs; }
    public float minimumRpm() { return minimumRpm; }
    /** Absolute furnace temperature threshold; independent from Create's burner heat stage. */
    public int minimumHeat() { return minimumHeat; }
    public Optional<IncubationDisplay> incubationDisplay() { return incubationDisplay; }
    public int durationAtRpm(float rpm) {
        if (!Float.isFinite(rpm) || Math.abs(rpm) <= 0) return Integer.MAX_VALUE;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, Math.floor((double) getProcessingDuration() * 256 / Math.abs(rpm))));
    }

    /** Recipe identity includes semantic content so /reload cannot reuse stale saved progress. */
    public String fingerprint(HolderLookup.Provider registries) {
        if (fingerprintRegistries == registries && cachedFingerprint != null) return cachedFingerprint;
        JsonElement encoded = entry.serializer().codec().codec().encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), this).getOrThrow();
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
        if (!fluidInputs.isEmpty() && !(input instanceof Input)) return false;
        int slots = input.size();
        long[][] flow = new long[2 + itemInputs.size() + slots][2 + itemInputs.size() + slots];
        int target = flow.length - 1;
        long required = 0;
        for (int i = 0; i < itemInputs.size(); i++) {
            ItemInput ingredient = itemInputs.get(i);
            flow[0][1 + i] = ingredient.count(); required += ingredient.count();
            for (int slot = 0; slot < slots; slot++) if (ingredient.ingredient().test(input.getItem(slot)))
                flow[1 + i][1 + itemInputs.size() + slot] = Integer.MAX_VALUE;
        }
        for (int slot = 0; slot < slots; slot++) flow[1 + itemInputs.size() + slot][target] = input.getItem(slot).getCount();
        if (maximumFlow(flow, target) != required) return false;
        if (!(input instanceof Input full)) return true;
        long[][] fluids = new long[2 + fluidInputs.size() + full.fluids().size()][2 + fluidInputs.size() + full.fluids().size()];
        target = fluids.length - 1; required = 0;
        for (int i = 0; i < fluidInputs.size(); i++) {
            FluidInput ingredient = fluidInputs.get(i);
            fluids[0][1 + i] = ingredient.ingredient().amount(); required += ingredient.ingredient().amount();
            for (int tank = 0; tank < full.fluids().size(); tank++) if (ingredient.ingredient().ingredient().test(full.fluids().get(tank)))
                fluids[1 + i][1 + fluidInputs.size() + tank] = Integer.MAX_VALUE;
        }
        for (int tank = 0; tank < full.fluids().size(); tank++) fluids[1 + fluidInputs.size() + tank][target] = full.fluids().get(tank).getAmount();
        return maximumFlow(fluids, target) == required;
    }

    private static long maximumFlow(long[][] residual, int target) {
        long flow = 0;
        int[] parent = new int[residual.length];
        while (true) {
            Arrays.fill(parent, -1); parent[0] = 0;
            var queue = new ArrayDeque<Integer>(); queue.add(0);
            while (!queue.isEmpty() && parent[target] == -1) {
                int node = queue.remove();
                for (int next = 0; next < residual.length; next++) if (parent[next] == -1 && residual[node][next] > 0) {
                    parent[next] = node; queue.add(next);
                }
            }
            if (parent[target] == -1) return flow;
            long amount = Long.MAX_VALUE;
            for (int node = target; node != 0; node = parent[node]) amount = Math.min(amount, residual[parent[node]][node]);
            for (int node = target; node != 0; node = parent[node]) { residual[parent[node]][node] -= amount; residual[node][parent[node]] += amount; }
            flow += amount;
        }
    }

    public record Input(List<ItemStack> items, List<FluidStack> fluids) implements RecipeInput {
        public Input { items = List.copyOf(items); fluids = List.copyOf(fluids); }
        @Override public ItemStack getItem(int slot) { return items.get(slot); }
        @Override public int size() { return items.size(); }
        @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty) && fluids.stream().allMatch(FluidStack::isEmpty); }
    }

    @Override protected int getMaxInputCount() { return MAX_ENTRIES; }
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
            items.forEach(input -> ingredients.add(input.ingredient()));
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
            MapCodec<Payload> raw = RecordCodecBuilder.mapCodec(i -> i.group(
                    ItemInput.CODEC.listOf(0, MAX_ENTRIES).optionalFieldOf("item_inputs", List.of()).forGetter(Payload::items),
                    FluidInput.CODEC.listOf(0, MAX_ENTRIES).optionalFieldOf("fluid_inputs", List.of()).forGetter(Payload::fluids),
                    ItemOutput.CODEC.listOf(0, MAX_ENTRIES).optionalFieldOf("item_outputs", List.of()).forGetter(Payload::outputs),
                    FluidOutput.CODEC.listOf(0, MAX_ENTRIES).optionalFieldOf("fluid_outputs", List.of()).forGetter(Payload::fluidOutputs),
                    POSITIVE.optionalFieldOf("processing_time", 100).forGetter(Payload::duration),
                    RPM.optionalFieldOf("minimum_rpm", 0f).forGetter(Payload::rpm),
                    Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("minimum_heat", 0).forGetter(Payload::heat),
                    HeatCondition.CODEC.optionalFieldOf("heat_requirement", HeatCondition.NONE).forGetter(Payload::condition),
                    IncubationDisplay.CODEC.optionalFieldOf("incubation_display").forGetter(Payload::incubationDisplay)
            ).apply(i, Payload::new));
            codec = raw.flatXmap(data -> {
                        if (data.items.isEmpty() && data.fluids.isEmpty()) return DataResult.error(() -> "Machine recipes need an input");
                        String validation = MachineRecipeValidation.error(entry, data.items, data.fluids, data.outputs, data.fluidOutputs, data.rpm, data.heat, data.condition);
                        if (validation != null) return DataResult.error(() -> validation);
                        if (data.incubationDisplay.isPresent() && entry != MachineRecipes.INCUBATING)
                            return DataResult.error(() -> "Only incubator recipes support incubation_display");
                        return DataResult.success(new MachineRecipe(entry, data.items, data.fluids, data.outputs, data.fluidOutputs, data.duration, data.rpm, data.heat, data.condition, data.incubationDisplay));
                    },
                    recipe -> DataResult.success(new Payload(recipe.itemInputs, recipe.fluidInputs, recipe.itemOutputs, recipe.fluidOutputs,
                            recipe.getProcessingDuration(), recipe.minimumRpm, recipe.minimumHeat, recipe.getRequiredHeat(), recipe.incubationDisplay)));
            streamCodec = ByteBufCodecs.fromCodecWithRegistries(codec.codec());
        }
        @Override public MapCodec<MachineRecipe> codec() { return codec; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> streamCodec() { return streamCodec; }
        private record Payload(List<ItemInput> items, List<FluidInput> fluids, List<ItemOutput> outputs, List<FluidOutput> fluidOutputs,
                               int duration, float rpm, int heat, HeatCondition condition, Optional<IncubationDisplay> incubationDisplay) {}
    }
}
