package net.quiwh.cwi.machine.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Preparation information for JEI; these feeds are not additional incubator inputs. */
public record IncubationDisplay(ItemStack microbe, List<IncubationDisplay.Step> steps, int yieldCount) {
    private static final Codec<Integer> POSITIVE = Codec.intRange(1, Integer.MAX_VALUE);
    public static final Codec<IncubationDisplay> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.CODEC.fieldOf("microbe").forGetter(IncubationDisplay::microbe),
            Step.CODEC.listOf(0, MachineRecipe.MAX_ENTRIES).fieldOf("steps").forGetter(IncubationDisplay::steps),
            POSITIVE.optionalFieldOf("yield_count", 1).forGetter(IncubationDisplay::yieldCount)
    ).apply(i, IncubationDisplay::new));

    public IncubationDisplay {
        if (microbe == null || microbe.isEmpty()) throw new IllegalArgumentException("Incubation display needs a microbe item");
        if (steps == null || steps.size() > MachineRecipe.MAX_ENTRIES) throw new IllegalArgumentException("Too many preparation steps");
        if (yieldCount <= 0) throw new IllegalArgumentException("Incubation yield must be positive");
        microbe = microbe.copy();
        steps = List.copyOf(steps);
    }

    @Override public ItemStack microbe() { return microbe.copy(); }

    public enum Type implements StringRepresentable {
        DEPLOYING("deploying"), FILLING("filling");
        public static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);
        private final String name;
        Type(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }

    public record Step(Type type, ItemStack item, FluidStack fluid, int count) {
        private static final Codec<Payload> PAYLOAD = RecordCodecBuilder.create(i -> i.group(
                Type.CODEC.fieldOf("type").forGetter(Payload::type),
                ItemStack.CODEC.optionalFieldOf("item").forGetter(Payload::item),
                FluidStack.CODEC.optionalFieldOf("fluid").forGetter(Payload::fluid),
                POSITIVE.optionalFieldOf("count", 1).forGetter(Payload::count)
        ).apply(i, Payload::new));
        public static final Codec<Step> CODEC = PAYLOAD.flatXmap(data -> checked(() -> new Step(data.type,
                        data.item.orElse(ItemStack.EMPTY), data.fluid.orElse(FluidStack.EMPTY), data.count)),
                step -> DataResult.success(new Payload(step.type,
                        step.item.isEmpty() ? Optional.empty() : Optional.of(step.item()),
                        step.fluid.isEmpty() ? Optional.empty() : Optional.of(step.fluid()), step.count)));

        public Step {
            if (type == null || item == null || fluid == null || count <= 0)
                throw new IllegalArgumentException("Preparation step needs a type and positive count");
            if (type == Type.DEPLOYING ? item.isEmpty() || !fluid.isEmpty() : fluid.isEmpty() || !item.isEmpty())
                throw new IllegalArgumentException("Deploying steps need only an item; filling steps need only a fluid");
            Math.multiplyExact(type == Type.DEPLOYING ? item.getCount() : fluid.getAmount(), count);
            item = item.copy(); fluid = fluid.copy();
        }

        @Override public ItemStack item() { return item.copy(); }
        @Override public FluidStack fluid() { return fluid.copy(); }
        public ItemStack displayItem() { return item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(Math.multiplyExact(item.getCount(), count)); }
        public FluidStack displayFluid() { return fluid.isEmpty() ? FluidStack.EMPTY : fluid.copyWithAmount(Math.multiplyExact(fluid.getAmount(), count)); }
        private record Payload(Type type, Optional<ItemStack> item, Optional<FluidStack> fluid, int count) {}
    }

    private static <T> DataResult<T> checked(Supplier<T> factory) {
        try { return DataResult.success(factory.get()); }
        catch (IllegalArgumentException | ArithmeticException error) { return DataResult.error(() -> error.getMessage()); }
    }
}
