package net.quiwh.cwi.machine.recipe;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Immutable entry behavior, shared by serialization, scripting, execution and JEI. */
public record EntrySettings(int interval, Clock clock, boolean catalyst, int damage, int energy) {
    public enum Clock { WORKING, RECIPE }
    public static final EntrySettings DEFAULT = new EntrySettings(0, Clock.WORKING, false, 0, 0);
    // NbtOps accepts any numeric tag as a boolean. Read positive integers first so
    // network/NBT round trips cannot collapse intervals greater than one to true.
    private static final Codec<Integer> INTERVAL = Codec.either(Codec.intRange(1, Integer.MAX_VALUE), Codec.BOOL)
            .xmap(v -> v.map(n -> n, b -> b ? 1 : 0), n -> n <= 1 ? Either.right(n == 1) : Either.left(n));
    public static final MapCodec<EntrySettings> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            INTERVAL.optionalFieldOf("per_tick", 0).forGetter(EntrySettings::interval),
            Codec.STRING.comapFlatMap(s -> switch (s) {
                case "working" -> com.mojang.serialization.DataResult.success(Clock.WORKING);
                case "recipe" -> com.mojang.serialization.DataResult.success(Clock.RECIPE);
                default -> com.mojang.serialization.DataResult.error(() -> "tick_mode must be working or recipe");
            }, c -> c.name().toLowerCase(java.util.Locale.ROOT)).optionalFieldOf("tick_mode", Clock.WORKING).forGetter(EntrySettings::clock),
            Codec.BOOL.optionalFieldOf("catalyst", false).forGetter(EntrySettings::catalyst),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("damage", 0).forGetter(EntrySettings::damage),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("energy", 0).forGetter(EntrySettings::energy)
    ).apply(i, EntrySettings::new));
    public EntrySettings {
        if (interval < 0 || clock == null || damage < 0 || energy < 0) throw new IllegalArgumentException("Invalid entry behavior");
        if (interval == 0 && clock != Clock.WORKING) throw new IllegalArgumentException("Recipe clock requires a positive per_tick interval");
        if (catalyst && (interval != 0 || damage != 0 || energy != 0) || damage != 0 && energy != 0)
            throw new IllegalArgumentException("Catalyst, damage and energy costs are mutually exclusive");
    }
    public static EntrySettings tick(boolean enabled) { return enabled ? new EntrySettings(1, Clock.WORKING, false, 0, 0) : DEFAULT; }
    public boolean periodic() { return interval > 0; }
    public boolean tool() { return damage > 0 || energy > 0; }
    public boolean advanced() { return interval > 1 || clock != Clock.WORKING || catalyst || tool(); }
    public void validate(float chance, boolean input, boolean fluid, int count) {
        if (catalyst && chance != 1) throw new IllegalArgumentException("Catalysts cannot have a consumption chance");
        if (!input && (catalyst || tool())) throw new IllegalArgumentException("Catalyst/tool costs are input-only");
        if (tool() && (fluid || count != 1)) throw new IllegalArgumentException("Tool costs require one item");
    }
}
