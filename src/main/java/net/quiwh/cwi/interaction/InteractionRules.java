package net.quiwh.cwi.interaction;

import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.List;
import net.minecraft.world.entity.EquipmentSlot;
import net.quiwh.cwi.equipment.protection.HazardProtection;
import net.quiwh.cwi.equipment.protection.ProtectiveEnchantments.Type;

/** Typed immutable runtime definitions, constructed once by the native recipe codec. */
public final class InteractionRules {
    public sealed interface Rule permits Impact, InventoryHazard, FluidHazard, Toxin, Lock, Spouting, Budding, Drying {}
    public record Impact(Ingredient input, double minHeight, List<ImpactAction> actions) implements Rule {}
    public record Effect(ResourceLocation id, int duration, int amplifier) {}
    public record InventoryHazard(Ingredient input, int interval, float damage, ResourceLocation damageType,
                                  int fireTicks, List<Effect> effects, HazardProtection protection) implements Rule {}
    public record FluidHazard(FluidIngredient fluid, int interval, float damage, ResourceLocation damageType,
                              int fireTicks, List<Effect> effects, HazardProtection protection) implements Rule {}
    public record Toxin(Ingredient input, Ingredient additionalInput, double additionalWeight, int interval,
                        double retention, double scale, double threshold, String stateKey, ResourceLocation effect,
                        int duration, double meleeChance, int meleeDuration, double strongMeleeChance,
                        int strongMeleeDuration) implements Rule {}
    public record Lock(Ingredient tool, List<BlockSelector> blocks, double chance, int durability,
                       boolean lootOnly, double silverfishChance) implements Rule {}
    public record Spouting(BlockSelector input, SizedFluidIngredient fluid, List<Block> outputs) implements Rule {}
    public record Budding(BlockSelector input, SizedFluidIngredient fluid, List<Block> stages,
                          double chance, int attempts) implements Rule {}
    public record Drying(BlockSelector input, Block output, double chance) implements Rule {}

    static <T> Rule decode(InteractionRecipes.Kind kind, JsonObject json, DynamicOps<T> ops) {
        var r = new RuleReader<>(json, ops);
        Rule result = switch (kind) {
            case ITEM_IMPACT -> new Impact(r.value("input", Ingredient.CODEC_NONEMPTY),
                    r.number("min_height", 0, 0, 4096),
                    r.list("actions", 1, 64).stream().map(value -> ImpactAction.decode(r.child(value))).toList());
            case INVENTORY_HAZARD -> new InventoryHazard(r.value("input", Ingredient.CODEC_NONEMPTY), interval(r),
                    damage(r), damageType(r), fireTicks(r), effects(r), protection(r));
            case FLUID_HAZARD -> new FluidHazard(r.value("fluid", FluidIngredient.CODEC_NON_EMPTY), interval(r),
                    damage(r), damageType(r), fireTicks(r), effects(r), protection(r));
            case TOXIN_EXPOSURE -> new Toxin(r.value("input", Ingredient.CODEC_NONEMPTY),
                    r.has("additional_input") ? r.value("additional_input", Ingredient.CODEC_NONEMPTY) : Ingredient.EMPTY,
                    r.number("additional_weight", 9, 0, 1_000_000), interval(r),
                    r.number("retention", .85, 0, 1), r.number("scale", .02, 0, 1_000_000),
                    r.number("threshold", 1.5, 0, 1_000_000), stateKey(r), r.id("effect"),
                    r.integer("duration", 11, 1, 1_000_000), r.number("melee_chance", 0, 0, 1),
                    r.integer("melee_duration", 100, 0, 1_000_000), r.number("strong_melee_chance", 0, 0, 1),
                    r.integer("strong_melee_duration", 200, 0, 1_000_000));
            case LOCKPICKING -> new Lock(r.value("tool", Ingredient.CODEC_NONEMPTY),
                    r.list("blocks", 1, 128).stream().map(value -> BlockSelector.of(value.getAsString())).toList(),
                    r.number("chance", 0, 0, 1), r.integer("durability", 3, 0, 1_000_000),
                    r.bool("loot_only", true), r.number("silverfish_chance", .1, 0, 1));
            case BLOCK_SPOUTING -> new Spouting(BlockSelector.of(r.string("input")), fluid(r), blocks(r, "outputs", 1, 64));
            case BUDDING_CATALYSIS -> new Budding(BlockSelector.of(r.string("input")), fluid(r), blocks(r, "stages", 1, 16),
                    r.number("chance", 1, 0, 1), r.integer("attempts", 1, 1, 64));
            case DRYING -> new Drying(BlockSelector.of(r.string("input")),
                    r.registered("output", BuiltInRegistries.BLOCK), r.number("chance", 1, 0, 1));
        };
        r.finish();
        return result;
    }

    private static HazardProtection protection(RuleReader<?> r) {
        if (!r.has("protection")) return HazardProtection.NONE;
        var id = r.id("protection");
        Type type = switch (id.toString()) {
            case "cwi:acid", "cwi:chemical", "cwi:chemical_sealing" -> Type.CHEMICAL_SEALING;
            case "cwi:heat", "cwi:heat_shielding" -> Type.HEAT_SHIELDING;
            default -> throw new IllegalArgumentException("Unknown or inactive hazard protection: " + id);
        };
        var slots = r.has("protection_slots") ? r.list("protection_slots", 1, 4).stream().map(value -> {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("protection_slots must contain armor slot names");
            return switch (value.getAsString()) {
                case "head" -> EquipmentSlot.HEAD;
                case "chest" -> EquipmentSlot.CHEST;
                case "legs" -> EquipmentSlot.LEGS;
                case "feet" -> EquipmentSlot.FEET;
                default -> throw new IllegalArgumentException("Unknown armor protection slot: " + value);
            };
        }).toList() : List.<EquipmentSlot>of();
        if (slots.stream().distinct().count() != slots.size())
            throw new IllegalArgumentException("protection_slots cannot repeat a slot");
        return new HazardProtection(type, r.integer("required_resistance", 4, 1, 16),
                r.integer("durability_cost", 0, 0, 1_000_000), slots);
    }

    private static String stateKey(RuleReader<?> r) {
        String key = r.string("state_key", "neurotoxinAmount");
        if (key.isEmpty() || key.length() > 128) throw new IllegalArgumentException("Invalid toxin state_key");
        return key;
    }
    private static int interval(RuleReader<?> r) { return r.integer("interval", 10, 1, 72_000); }
    private static float damage(RuleReader<?> r) { return (float) r.number("damage", 0, 0, 1_000_000); }
    private static ResourceLocation damageType(RuleReader<?> r) { return ResourceLocation.parse(r.string("damage_type", "minecraft:magic")); }
    private static int fireTicks(RuleReader<?> r) { return r.integer("fire_ticks", 0, 0, 1_000_000); }
    private static List<Effect> effects(RuleReader<?> r) {
        if (!r.has("effects")) return List.of();
        return r.list("effects", 0, 64).stream().map(value -> {
            var e = r.child(value);
            var effect = new Effect(e.id("id"), e.integer("duration", 20, 1, 1_000_000), e.integer("amplifier", 0, 0, 255));
            e.finish();
            return effect;
        }).toList();
    }
    private static SizedFluidIngredient fluid(RuleReader<?> r) {
        var fluid = r.value("fluid", SizedFluidIngredient.FLAT_CODEC);
        if (fluid.amount() < 1 || fluid.amount() > 1_000_000 || fluid.ingredient().isEmpty())
            throw new IllegalArgumentException("fluid needs a nonempty ingredient and amount in [1,1000000]");
        return fluid;
    }
    private static List<Block> blocks(RuleReader<?> r, String key, int min, int max) {
        return r.list(key, min, max).stream().map(value -> {
            var id = ResourceLocation.parse(value.getAsString());
            var block = BuiltInRegistries.BLOCK.getOptional(id)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown " + key + " block " + id));
            if (block.asItem() == net.minecraft.world.item.Items.AIR)
                throw new IllegalArgumentException(key + " block must have an item: " + id);
            return block;
        }).toList();
    }
    private InteractionRules() {}
}
