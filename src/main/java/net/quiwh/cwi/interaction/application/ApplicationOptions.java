package net.quiwh.cwi.interaction.application;

import com.google.gson.*;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.quiwh.cwi.interaction.BlockSelector;

/** Validated, synchronized data; no commands or retained script closures. */
public final class ApplicationOptions {
    public static final Codec<ApplicationOptions> CODEC = new Codec<>() {
        public <T> DataResult<Pair<ApplicationOptions,T>> decode(DynamicOps<T> ops, T input) {
            try {
                var json = ops.convertTo(JsonOps.INSTANCE, input);
                return DataResult.success(Pair.of(new ApplicationOptions(new Reader(json, ops)), ops.empty()));
            } catch (RuntimeException ex) { return DataResult.error(ex::getMessage); }
        }
        public <T> DataResult<T> encode(ApplicationOptions value, DynamicOps<T> ops, T prefix) {
            return DataResult.success(JsonOps.INSTANCE.convertTo(ops, value.json));
        }
    };
    private final JsonObject json;
    public final boolean emptyHand;
    public final Boolean sneaking;
    public final int priority, cooldown;
    public final Integer damageHeld, consumeHeld;
    public final float damagePlayer;
    public final String blockMode;
    public final BlockSelector block;
    public final Map<String,String> state;
    public final List<Action> actions;
    public final List<Drop> drops;

    private ApplicationOptions(Reader r) {
        emptyHand = r.bool("empty_hand", false);
        sneaking = r.has("sneaking") ? r.bool("sneaking", false) : null;
        priority = r.integer("priority", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
        cooldown = r.integer("cooldown", 0, 0, 72000);
        damageHeld = r.has("damage_held") ? r.integer("damage_held", 0, 0, 1000000) : null;
        consumeHeld = r.has("consume_held") ? r.integer("consume_held", 1, 1, 64) : null;
        damagePlayer = (float)r.number("damage_player", 0, 0, 1000000);
        blockMode = r.string("block_mode", "replace");
        if (!Set.of("replace", "keep", "remove").contains(blockMode)) throw new IllegalArgumentException("Unknown block_mode: " + blockMode);
        if (damageHeld != null && consumeHeld != null) throw new IllegalArgumentException("Choose damage_held or consume_held");
        if (emptyHand && (damageHeld != null || consumeHeld != null)) throw new IllegalArgumentException("Empty hand cannot pay an item cost");
        block = r.has("block") ? BlockSelector.of(r.string("block", "")) : null;
        var properties = new LinkedHashMap<String,String>();
        if (r.has("state")) {
            var obj = r.take("state").getAsJsonObject();
            obj.entrySet().forEach(e -> {
                if (!e.getValue().isJsonPrimitive()) throw new IllegalArgumentException("State values must be strings");
                properties.put(e.getKey(), e.getValue().getAsString());
            });
            if (block != null && !block.tag()) {
                var sample = BuiltInRegistries.BLOCK.get(block.id()).defaultBlockState();
                for (var entry : properties.entrySet()) {
                    var property = sample.getBlock().getStateDefinition().getProperty(entry.getKey());
                    if (property == null || property.getValue(entry.getValue()).isEmpty()) throw new IllegalArgumentException("Invalid block state " + entry);
                }
            }
        }
        state = Map.copyOf(properties);
        var budget = new int[]{256};
        actions = readActions(r, "actions", 0, budget);
        var dropList = new ArrayList<Drop>();
        for (var e : r.list("drops", 128)) dropList.add(drop(r.child(e)));
        drops = List.copyOf(dropList);
        r.finish();
        json = r.json.deepCopy();
    }
    public JsonObject json() { return json.deepCopy(); }
    public boolean matchesState(BlockState actual) {
        if (block != null && !block.test(actual)) return false;
        for (var entry : state.entrySet()) {
            var p = actual.getBlock().getStateDefinition().getProperty(entry.getKey());
            if (p == null || !actual.getValue(p).toString().equals(entry.getValue())) return false;
        }
        return true;
    }
    public record Drop(ItemStack stack, float chance) {
        public Drop { stack = stack.copy(); }
        @Override public ItemStack stack() { return stack.copy(); }
        public void apply(Context c) {
            if (chance >= 1 || c.level.random.nextFloat() < chance) Block.popResource(c.level, c.pos, stack.copy());
        }
    }
    public record Context(ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player) {}
    public sealed interface Action permits Place, DropAction, Sound, Particle, DamagePlayer, Conditional { void apply(Context c); }
    public record Place(BlockState state, boolean copyProperties, Set<Property<?>> explicitProperties) implements Action {
        public Place { explicitProperties = Set.copyOf(explicitProperties); }
        public void apply(Context c) {
            var old = c.level.getBlockState(c.pos);
            var next = copyProperties ? com.simibubi.create.foundation.utility.BlockHelper.copyProperties(old, state) : state;
            for (var property : explicitProperties) next = copyExplicit(next, state, property);
            if (old != next) c.level.setBlockAndUpdate(c.pos, next);
        }
        private static <T extends Comparable<T>> BlockState copyExplicit(BlockState target, BlockState source, Property<T> property) {
            return target.setValue(property, source.getValue(property));
        }
    }
    public record DropAction(Drop drop) implements Action { public void apply(Context c) { drop.apply(c); } }
    public record Sound(SoundEvent sound, float volume, float pitch) implements Action {
        public void apply(Context c) { c.level.playSound(null, c.pos, sound, SoundSource.BLOCKS, volume, pitch); }
    }
    public record Particle(ParticleOptions particle, int count, double dx, double dy, double dz, double speed) implements Action {
        public void apply(Context c) { c.level.sendParticles(particle, c.pos.getX()+.5, c.pos.getY()+.5, c.pos.getZ()+.5, count, dx, dy, dz, speed); }
    }
    public record DamagePlayer(float amount) implements Action {
        public void apply(Context c) { if (!c.player.isCreative()) c.player.hurt(c.level.damageSources().generic(), amount); }
    }
    public record Conditional(float chance, Boolean sneaking, List<Action> thenActions, List<Action> elseActions) implements Action {
        public void apply(Context c) {
            boolean pass = (sneaking == null || sneaking == c.player.isShiftKeyDown()) && (chance >= 1 || c.level.random.nextFloat() < chance);
            (pass ? thenActions : elseActions).forEach(a -> a.apply(c));
        }
    }
    private static Drop drop(Reader r) {
        ItemStack stack = r.value("stack", ItemStack.STRICT_CODEC);
        float chance = (float)r.number("chance", 1, 0, 1);
        r.finish(); return new Drop(stack, chance);
    }
    private static List<Action> readActions(Reader r, String key, int depth, int[] budget) {
        if (depth > 8) throw new IllegalArgumentException("Action groups exceed depth 8");
        var result = new ArrayList<Action>();
        for (var element : r.list(key, 128)) {
            if (--budget[0] < 0) throw new IllegalArgumentException("Too many interaction actions");
            var a = r.child(element);
            Action action = switch (a.string("type", "")) {
                case "set_block" -> place(a.string("block", "minecraft:air"), a.bool("copy_properties", true));
                case "drop" -> new DropAction(drop(a));
                case "sound" -> new Sound(BuiltInRegistries.SOUND_EVENT.getOptional(ResourceLocation.parse(a.string("id", ""))).orElseThrow(() -> new IllegalArgumentException("Unknown sound")),
                        (float)a.number("volume", 1, 0, 64), (float)a.number("pitch", 1, .001, 64));
                case "particle" -> new Particle(particle(a), a.integer("count", 1, 0, 4096), a.number("dx", 0, 0, 64), a.number("dy", 0, 0, 64),
                        a.number("dz", 0, 0, 64), a.number("speed", 0, 0, 64));
                case "damage_player" -> new DamagePlayer((float)a.number("amount", 1, 0, 1000000));
                case "if" -> new Conditional((float)a.number("chance", 1, 0, 1), a.has("sneaking") ? a.bool("sneaking", false) : null,
                        readActions(a, "then", depth+1, budget), readActions(a, "else", depth+1, budget));
                default -> throw new IllegalArgumentException("Unknown item application action");
            };
            a.finish(); result.add(action);
        }
        return List.copyOf(result);
    }
    public static BlockState blockState(String value) {
        try { return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), value, false).blockState(); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) { throw new IllegalArgumentException("Invalid block state: " + value, ex); }
    }
    private static Place place(String value, boolean copyProperties) {
        try {
            var parsed = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), value, false);
            return new Place(parsed.blockState(), copyProperties, parsed.properties().keySet());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
            throw new IllegalArgumentException("Invalid block state: " + value, ex);
        }
    }
    private static ParticleOptions particle(Reader r) {
        String id = r.string("id", "");
        var type = BuiltInRegistries.PARTICLE_TYPE.getOptional(ResourceLocation.parse(id)).orElseThrow(() -> new IllegalArgumentException("Unknown particle " + id));
        if (type instanceof SimpleParticleType simple) return simple;
        if (type == ParticleTypes.BLOCK || type == ParticleTypes.FALLING_DUST)
            return new BlockParticleOption((ParticleType<BlockParticleOption>) type, blockState(r.string("block", "minecraft:stone")));
        throw new IllegalArgumentException("Use a simple, block or falling_dust particle");
    }
    static final class Reader {
        final JsonObject json;
        final DynamicOps<?> ops;
        final Set<String> used = new HashSet<>();
        Reader(JsonElement json, DynamicOps<?> ops) {
            if (!json.isJsonObject()) throw new IllegalArgumentException("Expected interaction object");
            this.json = json.getAsJsonObject(); this.ops = ops;
        }
        Reader child(JsonElement json) { return new Reader(json, ops); }
        boolean has(String key) { return json.has(key); }
        JsonElement take(String key) { used.add(key); return json.get(key); }
        String string(String key, String fallback) {
            if (!has(key)) return fallback;
            var e = take(key);
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key + " must be a string");
            return e.getAsString();
        }
        double number(String key, double fallback, double min, double max) {
            if (!has(key)) return fallback;
            var e = take(key);
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key + " must be numeric");
            double n = e.getAsDouble();
            if (!Double.isFinite(n) || n < min || n > max) throw new IllegalArgumentException(key + " outside ["+min+","+max+"]");
            return n;
        }
        int integer(String key, int fallback, int min, int max) {
            double n = number(key, fallback, min, max);
            if (n != (int)n) throw new IllegalArgumentException(key + " must be an integer");
            return (int)n;
        }
        @SuppressWarnings({"unchecked", "rawtypes"}) <V> V value(String key, Codec<V> codec) {
            return (V)codec.parse((DynamicOps)ops, JsonOps.INSTANCE.convertTo((DynamicOps)ops, take(key))).getOrThrow();
        }
        boolean bool(String key, boolean fallback) {
            if (!has(key)) return fallback;
            boolean b=value(key,Codec.BOOL);json.addProperty(key,b);return b;
        }
        List<JsonElement> list(String key, int max) {
            if (!has(key)) return List.of();
            var e = take(key);
            if (!e.isJsonArray() || e.getAsJsonArray().size() > max) throw new IllegalArgumentException(key + " must be a bounded list");
            return e.getAsJsonArray().asList();
        }
        void finish() { for (String key : json.keySet()) if (!used.contains(key)) throw new IllegalArgumentException("Unknown interaction field: " + key); }
    }
}
