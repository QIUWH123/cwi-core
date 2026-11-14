package net.quiwh.cwi.machine.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** One independent whole-stack weighted byproduct; visibility never changes gameplay. */
public record OutputPool(List<Entry> entries, boolean showOutputs) {
    private static final Codec<Double> WEIGHT = Codec.DOUBLE.validate(n -> Double.isFinite(n) && n > 0
            ? DataResult.success(n) : DataResult.error(() -> "Pool weights must be positive and finite"));
    public record Entry(ItemStack stack, double weight) {
        public static final Codec<Entry> CODEC = RecipeEntryCodecs.fields(RecordCodecBuilder.create(i -> i.group(
                ItemStack.CODEC.fieldOf("stack").forGetter(Entry::stack),
                WEIGHT.optionalFieldOf("weight", 1d).forGetter(Entry::weight)).apply(i, Entry::new)), java.util.Set.of("stack", "weight"));
        public Entry { if (stack.isEmpty() || !Double.isFinite(weight) || weight <= 0) throw new IllegalArgumentException("Invalid pool entry"); stack = stack.copy(); }
        @Override public ItemStack stack() { return stack.copy(); }
    }
    public static final Codec<OutputPool> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.listOf(1, 64).fieldOf("entries").forGetter(OutputPool::entries),
            Codec.BOOL.optionalFieldOf("show_outputs", false).forGetter(OutputPool::showOutputs)).apply(i, OutputPool::new));
    public OutputPool {
        entries = List.copyOf(entries);
        if (entries.isEmpty() || entries.size() > 64 || !Double.isFinite(entries.stream().mapToDouble(Entry::weight).sum()))
            throw new IllegalArgumentException("Pool requires 1..64 entries and a finite total weight");
    }
    public double totalWeight() { return entries.stream().mapToDouble(Entry::weight).sum(); }
    public ItemStack roll(net.minecraft.util.RandomSource random) {
        double value = random.nextDouble() * totalWeight();
        for (var entry : entries) { value -= entry.weight(); if (value < 0) return entry.stack(); }
        return entries.getLast().stack();
    }
}
