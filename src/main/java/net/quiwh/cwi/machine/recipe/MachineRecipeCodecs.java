package net.quiwh.cwi.machine.recipe;

import static net.quiwh.cwi.machine.recipe.MachineRecipe.MAX_ENTRIES;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.quiwh.cwi.machine.recipe.MachineRecipe.FluidInput;
import net.quiwh.cwi.machine.recipe.MachineRecipe.FluidOutput;
import net.quiwh.cwi.machine.recipe.MachineRecipe.ItemInput;
import net.quiwh.cwi.machine.recipe.MachineRecipe.ItemOutput;

/** Native JSON entry validation and payload mapping, separate from recipe state. */
final class MachineRecipeCodecs {
    static final Codec<Float> CHANCE = Codec.FLOAT.validate(value -> Float.isFinite(value) && value >= 0 && value <= 1
            ? DataResult.success(value) : DataResult.error(() -> "Chance must be finite and in [0,1]"));
    private static final Codec<Float> RPM = Codec.FLOAT.validate(value -> Float.isFinite(value) && value >= 0
            ? DataResult.success(value) : DataResult.error(() -> "RPM must be finite and nonnegative"));
    static final Codec<Integer> POSITIVE = Codec.intRange(1, Integer.MAX_VALUE);

    static MapCodec<MachineRecipe> create(MachineRecipes.Entry entry) {
        Codec<Either<FluidInput, ItemInput>> input = Codec.either(FluidInput.CODEC, ItemInput.CODEC);
        Codec<Either<FluidOutput, ItemOutput>> output = Codec.either(FluidOutput.CODEC, ItemOutput.CODEC);
        MapCodec<Payload> raw = RecordCodecBuilder.mapCodec(i -> i.group(
                input.listOf(0, MAX_ENTRIES * MAX_ENTRIES + MAX_ENTRIES).fieldOf("ingredients").forGetter(Payload::ingredients),
                output.listOf(0, MAX_ENTRIES * 2).optionalFieldOf("results", List.of()).forGetter(Payload::results),
                POSITIVE.optionalFieldOf("processing_time", 100).forGetter(Payload::duration),
                RPM.optionalFieldOf("minimum_rpm", 0f).forGetter(Payload::rpm),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("minimum_heat", 0).forGetter(Payload::heat),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("maximum_heat", Integer.MAX_VALUE).forGetter(Payload::maximumHeat),
                HeatCondition.CODEC.optionalFieldOf("heat_requirement", HeatCondition.NONE).forGetter(Payload::condition),
                IncubationDisplay.CODEC.optionalFieldOf("incubation_display").forGetter(Payload::incubationDisplay),
                Codec.INT.optionalFieldOf("priority", 0).forGetter(Payload::priority),
                OutputPool.CODEC.listOf(0, MAX_ENTRIES).optionalFieldOf("output_pools", List.of()).forGetter(Payload::pools)
        ).apply(i, Payload::new));
        MapCodec<MachineRecipe> validated = raw.flatXmap(data -> {
                    var items = new ArrayList<ItemInput>();
                    var fluids = new ArrayList<FluidInput>();
                    var outputs = new ArrayList<ItemOutput>();
                    var fluidOutputs = new ArrayList<FluidOutput>();
                    try {
                        data.ingredients.forEach(v -> v.ifLeft(fluids::add).ifRight(item -> {
                            if (item.chance() == 1 && item.settings().equals(EntrySettings.DEFAULT)) {
                                for (int n = 0; n < items.size(); n++) {
                                    var previous = items.get(n);
                                    if (previous.chance() == 1 && previous.settings().equals(EntrySettings.DEFAULT) && previous.ingredient().equals(item.ingredient())) {
                                        items.set(n, new ItemInput(previous.ingredient(), Math.addExact(previous.count(), item.count()), 1, false));
                                        return;
                                    }
                                }
                            }
                            items.add(item);
                        }));
                        data.results.forEach(v -> v.ifLeft(fluidOutputs::add).ifRight(outputs::add));
                        return DataResult.success(new MachineRecipe(entry, items, fluids, outputs, fluidOutputs,
                                data.duration, data.rpm, data.heat, data.maximumHeat, data.condition, data.incubationDisplay, data.priority, data.pools));
                    } catch (IllegalArgumentException | ArithmeticException ex) { return DataResult.error(ex::getMessage); }
                }, recipe -> {
                    var ingredients = new ArrayList<Either<FluidInput, ItemInput>>();
                    recipe.itemInputs().forEach(v -> {
                        // Native Create represents counted ordinary item inputs as repeated ingredients.
                        if (v.chance() == 1 && v.settings().equals(EntrySettings.DEFAULT)) {
                            for (int n = 0; n < v.count(); n++) ingredients.add(Either.right(new ItemInput(v.ingredient(), 1, 1, false)));
                        } else ingredients.add(Either.right(v));
                    });
                    recipe.fluidInputs().forEach(v -> ingredients.add(Either.left(v)));
                    var results = new ArrayList<Either<FluidOutput, ItemOutput>>();
                    recipe.itemOutputs().forEach(v -> results.add(Either.right(v)));
                    recipe.fluidOutputs().forEach(v -> results.add(Either.left(v)));
                    return DataResult.success(new Payload(ingredients, results, recipe.getProcessingDuration(), recipe.minimumRpm(),
                            recipe.minimumHeat(), recipe.maximumHeat(), recipe.getRequiredHeat(), recipe.incubationDisplay(), recipe.priority(), recipe.outputPools()));
                });
        return new MapCodec<>() {
            @Override public <T> java.util.stream.Stream<T> keys(com.mojang.serialization.DynamicOps<T> ops) { return validated.keys(ops); }
            @Override public <T> DataResult<MachineRecipe> decode(com.mojang.serialization.DynamicOps<T> ops, com.mojang.serialization.MapLike<T> input) {
                try {
                    for (String field : List.of("ingredients", "results")) {
                        T entries = input.get(field); if (entries == null) continue;
                        var stream = ops.getStream(entries).result(); if (stream.isEmpty()) continue;
                        for (T value : stream.get().toList()) {
                            var object = ops.getMap(value).result(); if (object.isEmpty()) continue;
                            boolean special = List.of("per_tick", "tick_mode", "catalyst", "damage", "energy").stream()
                                    .anyMatch(key -> object.get().get(key) != null);
                            if (special && object.get().get(field.equals("ingredients") ? "ingredient" : "stack") == null)
                                return DataResult.error(() -> "Advanced " + field + " entries require a nested "
                                        + (field.equals("ingredients") ? "ingredient" : "stack"));
                        }
                    }
                    return validated.decode(ops, input);
                }
                catch (IllegalArgumentException | ArithmeticException failure) { return DataResult.error(failure::getMessage); }
            }
            @Override public <T> com.mojang.serialization.RecordBuilder<T> encode(MachineRecipe recipe, com.mojang.serialization.DynamicOps<T> ops,
                    com.mojang.serialization.RecordBuilder<T> prefix) { return validated.encode(recipe, ops, prefix); }
        };
    }

    private record Payload(List<Either<FluidInput, ItemInput>> ingredients, List<Either<FluidOutput, ItemOutput>> results,
                           int duration, float rpm, int heat, int maximumHeat, HeatCondition condition,
                           Optional<IncubationDisplay> incubationDisplay, int priority, List<OutputPool> pools) {}

    private MachineRecipeCodecs() {}
}
