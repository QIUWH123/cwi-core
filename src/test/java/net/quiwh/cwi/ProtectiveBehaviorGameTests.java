package net.quiwh.cwi;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.equipment.protection.HazardProtection;
import net.quiwh.cwi.equipment.protection.ProtectiveEnchantments;
import net.quiwh.cwi.equipment.protection.ProtectiveEnchantments.Type;
import net.quiwh.cwi.interaction.InteractionRecipes;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class ProtectiveBehaviorGameTests {
    private static ItemStack gear(GameTestHelper h, net.minecraft.world.item.Item item, String enchantment, int level) {
        var stack = new ItemStack(item);
        stack.enchant(ProtectiveEnchantmentGameTests.enchantment(h, enchantment), level);
        return stack;
    }
    private static Pig pig(GameTestHelper h) {
        var pig = new Pig(EntityType.PIG, h.getLevel());
        var pos = h.absolutePos(new BlockPos(2, 3, 2));
        pig.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        return pig;
    }
    @GameTest(template="empty", templateNamespace=CwiCore.ID)
    public static void reservedEnchantmentsRemainCompletelyInactive(GameTestHelper h) {
        var pig = pig(h);
        var helmet = new ItemStack(Items.DIAMOND_HELMET);
        for (String id : ProtectiveEnchantmentGameTests.IDS.subList(6, 11).stream().filter(id -> !id.equals("gas_filtration") && !id.equals("ocular_shielding")).toList())
            helmet.enchant(ProtectiveEnchantmentGameTests.enchantment(h, id), 4);
        pig.setItemSlot(EquipmentSlot.HEAD, helmet);
        for (var type : Type.values()) {
            h.assertTrue(ProtectiveEnchantments.level(pig, type) == 0, "reserved enchantments supply no active resistance: " + type);
            if (!type.active) h.assertTrue(new HazardProtection(type, 4, 100, List.of()).remaining(pig, 0, true) == 1,
                    "even a forced inactive policy has no effect");
        }
        pig.setOnGround(true); pig.setTicksFrozen(12);
        h.assertTrue(ProtectiveEnchantments.freezing(pig, 13) == 13 && ProtectiveEnchantments.friction(pig, .98F) == .98F,
                "reserved equipment cannot change cold or traction");
        h.assertTrue(helmet.getDamageValue() == 0, "inactive equipment has no durability costs");
        h.succeed();
    }
    @GameTest(template="empty", templateNamespace=CwiCore.ID)
    public static void wornSlotControlsEffectsWithoutEquipmentTags(GameTestHelper h) {
        var pig = pig(h);
        pig.setItemSlot(EquipmentSlot.HEAD, gear(h, Items.DIAMOND_BOOTS, "sure_footing", 4));
        pig.setOnGround(true);
        h.assertTrue(ProtectiveEnchantments.level(pig, Type.SURE_FOOTING) == 0, "boots must be worn on feet");
        pig.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        pig.setItemSlot(EquipmentSlot.FEET, gear(h, Items.IRON_BOOTS, "sure_footing", 4));
        h.assertTrue(Math.abs(ProtectiveEnchantments.friction(pig, .98F)-.6F)<1e-6,
                "enchanted boots work in the right slot without an eligibility tag");
        pig.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        pig.setItemSlot(EquipmentSlot.MAINHAND, gear(h, Items.DIAMOND_BOOTS, "sure_footing", 4));
        h.assertTrue(ProtectiveEnchantments.level(pig, Type.SURE_FOOTING)==0,"holding footwear does not activate a worn effect");
        h.succeed();
    }
    @GameTest(template="empty", templateNamespace=CwiCore.ID)
    public static void nativeFreezingGrowthAndThawingRespectInsulation(GameTestHelper h) {
        var pig = pig(h); pig.setNoAi(true);
        pig.setItemSlot(EquipmentSlot.FEET, gear(h, Items.GOLDEN_BOOTS, "cold_insulation", 2));
        for (int tick = 0; tick < 8; tick++) {
            pig.tickCount = tick; pig.isInPowderSnow = true; pig.aiStep();
        }
        h.assertTrue(pig.getTicksFrozen() == 4, "native aiStep grows freezing on half the ticks at level II");
        pig.setItemSlot(EquipmentSlot.CHEST, gear(h, Items.GOLDEN_CHESTPLATE, "cold_insulation", 2));
        for (int tick = 8; tick < 12; tick++) { pig.tickCount = tick; pig.isInPowderSnow = true; pig.aiStep(); }
        h.assertTrue(pig.getTicksFrozen() == 4, "aggregate resistance IV prevents further buildup");
        pig.isInPowderSnow = false; pig.aiStep();
        h.assertTrue(pig.getTicksFrozen() == 2, "native thawing is unchanged");
        h.succeed();
    }
    @GameTest(template="empty", templateNamespace=CwiCore.ID)
    public static void nativeFreezeAndLightningDamageAreProtectedOnlyByTheirOwnEnchantments(GameTestHelper h) {
        var pig = pig(h);
        pig.setItemSlot(EquipmentSlot.FEET, gear(h, Items.GOLDEN_BOOTS, "cold_insulation", 4));
        pig.hurt(pig.damageSources().freeze(), 8);
        h.assertTrue(pig.getHealth() == pig.getMaxHealth(), "cold IV prevents native freeze damage");
        pig.hurt(pig.damageSources().lightningBolt(), 8);
        h.assertTrue(pig.getHealth() < pig.getMaxHealth(), "cold protection does not absorb lightning");
        pig.setHealth(pig.getMaxHealth()); pig.invulnerableTime = 0;
        pig.setItemSlot(EquipmentSlot.FEET, gear(h, Items.CHAINMAIL_BOOTS, "electrical_insulation", 4));
        pig.hurt(pig.damageSources().lightningBolt(), 8);
        h.assertTrue(pig.getHealth() == pig.getMaxHealth(), "electrical IV prevents native lightning damage");
        pig.hurt(pig.damageSources().magic(), 4);
        h.assertTrue(pig.getHealth() < pig.getMaxHealth(), "unrelated damage remains native");
        h.succeed();
    }
    @GameTest(template="empty", templateNamespace=CwiCore.ID)
    public static void sureFootingChangesNativeKnockbackOnlyOnGround(GameTestHelper h) {
        var pig = pig(h); pig.setItemSlot(EquipmentSlot.FEET, gear(h, Items.DIAMOND_BOOTS, "sure_footing", 4));
        pig.setOnGround(true); pig.knockback(1, 1, 0);
        h.assertTrue(Math.abs(pig.getDeltaMovement().x + .4) < 1e-6, "grounded native knockback reduced 60 percent");
        pig.setDeltaMovement(Vec3.ZERO); pig.setOnGround(false); pig.knockback(1, 1, 0);
        h.assertTrue(Math.abs(pig.getDeltaMovement().x + 1) < 1e-6, "airborne knockback is unchanged");
        h.succeed();
    }
    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void sureFootingChangesNativeIceTravelButNotOrdinaryFriction(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 1, 2));
        h.setBlock(new BlockPos(2, 1, 2), Blocks.ICE);
        var pig = pig(h); pig.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        pig.setItemSlot(EquipmentSlot.FEET, gear(h, Items.DIAMOND_BOOTS, "sure_footing", 4));
        pig.setOnGround(true); pig.setDeltaMovement(.1, 0, 0); pig.travel(Vec3.ZERO);
        h.assertTrue(Math.abs(pig.getDeltaMovement().x - .1 * .6 * .91) < 1e-6, "native travel uses ordinary ground friction on ice");
        h.assertTrue(ProtectiveEnchantments.friction(pig, .8F) == .8F && ProtectiveEnchantments.friction(pig, .6F) == .6F,
                "ordinary ground and slime remain unchanged");
        h.succeed();
    }
    private static final class SwimPig extends Pig {
        SwimPig(GameTestHelper h) { super(EntityType.PIG, h.getLevel()); }
        void water(boolean value) { wasTouchingWater = value; }
    }
    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void swimmingBonusRequiresActiveWaterSwimmingInNativeTravel(GameTestHelper h) {
        var normal = new SwimPig(h); var protectedPig = new SwimPig(h);
        var pos = h.absolutePos(new BlockPos(2, 4, 2));
        for (var pig : List.of(normal, protectedPig)) {
            pig.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            pig.setYRot(0); pig.water(true); pig.setSwimming(true);
        }
        protectedPig.setItemSlot(EquipmentSlot.FEET, gear(h, Items.DIAMOND_BOOTS, "swimming_efficiency", 4));
        normal.travel(new Vec3(0, 0, 1)); protectedPig.travel(new Vec3(0, 0, 1));
        h.assertTrue(normal.getDeltaMovement().z > 0 && Math.abs(protectedPig.getDeltaMovement().z / normal.getDeltaMovement().z - 1.4) < 1e-5,
                "native swimming acceleration is forty percent faster: normal=" + normal.getDeltaMovement() + " protected=" + protectedPig.getDeltaMovement() + " swimming=" + protectedPig.isSwimming() + " water=" + protectedPig.isInWater() + " level=" + ProtectiveEnchantments.level(protectedPig, Type.SWIMMING_EFFICIENCY));
        protectedPig.setSwimming(false); protectedPig.setDeltaMovement(Vec3.ZERO); protectedPig.travel(new Vec3(0, 0, 1));
        h.assertTrue(Math.abs(protectedPig.getDeltaMovement().z - normal.getDeltaMovement().z) < 1e-6,
                "standing in water has no swimming bonus");
        protectedPig.water(false); protectedPig.setSwimming(true);
        h.assertTrue(ProtectiveEnchantments.swimming(protectedPig, 1) == 1, "no dry swimming bonus");
        h.succeed();
    }
    @GameTest(template="empty", templateNamespace=CwiCore.ID)
    public static void hazardCoverageWearDedupAndBreakageUseOnePulse(GameTestHelper h) {
        var pig = pig(h); var boots = gear(h, Items.IRON_BOOTS, "chemical_sealing", 2);
        pig.setItemSlot(EquipmentSlot.FEET, boots);
        var full = new HazardProtection(Type.CHEMICAL_SEALING, 4, 1, List.of(EquipmentSlot.FEET, EquipmentSlot.CHEST));
        h.assertTrue(full.remaining(pig, 10, true) == 1 && boots.getDamageValue() == 0, "missing required chest leaves a coverage gap without wear");
        var chest = gear(h, Items.IRON_CHESTPLATE, "chemical_sealing", 2); pig.setItemSlot(EquipmentSlot.CHEST, chest);
        h.assertTrue(full.remaining(pig, 11, true) == 0, "complete selected set supplies full resistance");
        var higher = new HazardProtection(Type.CHEMICAL_SEALING, 4, 3, List.of(EquipmentSlot.FEET, EquipmentSlot.CHEST));
        h.assertTrue(higher.remaining(pig, 11, true) == 0 && boots.getDamageValue() == 3 && chest.getDamageValue() == 3,
                "same-type rules charge maximum wear once per equipped piece and tick");
        boots.setDamageValue(boots.getMaxDamage() - 1); chest.setDamageValue(chest.getMaxDamage() - 1);
        h.assertTrue(full.remaining(pig, 12, true) == 0 && full.remaining(pig, 12, true) == 0,
                "both same-tick exposures retain the protection that just broke");
        h.assertTrue(full.remaining(pig, 13, true) == 1, "next exposure loses broken protection");
        h.succeed();
    }
    @GameTest(template="empty", templateNamespace=CwiCore.ID)
    public static void hazardWearHonorsNativeUnbreakingAndCreativeExemption(GameTestHelper h) {
        var pig = pig(h); var control = pig(h);
        var boots = gear(h, Items.IRON_BOOTS, "heat_shielding", 4);
        var unbreaking = h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
        boots.enchant(unbreaking, 3); var expected = boots.copy();
        pig.setItemSlot(EquipmentSlot.FEET, boots); control.setItemSlot(EquipmentSlot.FEET, expected);
        h.getLevel().getRandom().setSeed(41);
        var policy = new HazardProtection(Type.HEAT_SHIELDING, 4, 20, List.of());
        policy.remaining(pig, 0, true); h.getLevel().getRandom().setSeed(41); expected.hurtAndBreak(20, control, EquipmentSlot.FEET);
        h.assertTrue(boots.getDamageValue() == expected.getDamageValue(), "protection wear uses native Unbreaking");
        var player = h.makeMockPlayer(GameType.CREATIVE); player.getAbilities().instabuild = true;
        var creative = gear(h, Items.IRON_BOOTS, "heat_shielding", 4); player.setItemSlot(EquipmentSlot.FEET, creative);
        h.assertTrue(policy.remaining(player, 0, true) == 0 && creative.getDamageValue() == 0, "creative protection is free of durability costs");
        h.succeed();
    }
    @GameTest(template="empty", templateNamespace=CwiCore.ID)
    public static void malformedAndInactiveHazardPoliciesAreRejected(GameTestHelper h) {
        var ops = h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var codec = InteractionRecipes.Kind.FLUID_HAZARD.serializer().codec().codec();
        String base = "{\"fluid\":{\"fluid\":\"minecraft:water\"},";
        for (String fields : List.of("\"required_resistance\":4", "\"durability_cost\":1", "\"protection_slots\":[\"feet\"]",
                "\"protection\":\"cwi:gas_filtration\"", "\"protection\":\"cwi:cold\"", "\"protection\":\"cwi:electricity\"", "\"protection\":\"cwi:acid\",\"required_resistance\":0",
                "\"protection\":\"cwi:acid\",\"required_resistance\":1.5", "\"protection\":\"cwi:acid\",\"durability_cost\":-1",
                "\"protection\":\"cwi:acid\",\"protection_slots\":[\"head\",\"head\"]",
                "\"protection\":\"cwi:acid\",\"protection_slots\":[\"mainhand\"]"))
            h.assertTrue(codec.parse(ops, JsonParser.parseString(base + fields + "}")).error().isPresent(), "reject invalid policy " + fields);
        h.succeed();
    }
}
