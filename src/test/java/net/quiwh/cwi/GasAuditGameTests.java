package net.quiwh.cwi;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.quiwh.cwi.pollution.*;

@net.neoforged.neoforge.gametest.GameTestHolder("cwi")
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class GasAuditGameTests {
    @GameTest(template="machine_empty", templateNamespace="cwi")
    public static void eyeIrritationChecksEachGasIndependently(GameTestHelper h) {
        // Each amount is mB in one free block. Three weak exposures must not
        // combine, while each gas still irritates eyes at its own threshold.
        for (var amounts : new int[][]{{60,60,200},{60,60,0},{60,0,200},{0,60,200},{99,99,399}}) {
            var player = eyeExposure(h, amounts, 0);
            h.assertTrue(!player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS),
                    "individually weak gases must not combine into eye irritation: " + java.util.Arrays.toString(amounts));
        }
        for (var amounts : new int[][]{{100,0,0},{0,100,0},{0,0,400}}) {
            var player = eyeExposure(h, amounts, 0);
            h.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS),
                    "each gas retains its individual eye irritation threshold: " + java.util.Arrays.toString(amounts));
        }
        h.succeed();
    }

    @GameTest(template="machine_empty", templateNamespace="cwi")
    public static void eyeProtectionUsesIndividualExposureThresholds(GameTestHelper h) {
        var weak = eyeExposure(h, new int[]{60,60,200}, 4);
        h.assertTrue(!weak.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS), "weak exposures do not irritate protected eyes");
        h.assertTrue(weak.getItemBySlot(EquipmentSlot.HEAD).getDamageValue() == 0,
                "individually weak gases must not charge eye-protection wear");
        var partial = eyeExposure(h, new int[]{160,160,640}, 2);
        h.assertTrue(!partial.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS),
                "separately reduced exposures must not recombine after protection");
        h.assertTrue(partial.getItemBySlot(EquipmentSlot.HEAD).getDamageValue() == 1,
                "contributing eye protection pays at most once per pulse");
        var strong = eyeExposure(h, new int[]{250,250,1000}, 4);
        h.assertTrue(!strong.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS), "full eye protection blocks each strong exposure");
        h.assertTrue(strong.getItemBySlot(EquipmentSlot.HEAD).getDamageValue() == 1,
                "several strong gases still share one protection wear pulse");
        h.succeed();
    }

    private static net.minecraft.world.entity.player.Player eyeExposure(GameTestHelper h, int[] amounts, int shielding) {
        var level = h.getLevel();
        var original = GasClouds.get(level);
        long oldTick = level.getGameTime();
        try {
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime((oldTick / 20 + 1) * 20);
            var clouds = new GasClouds();
            level.getDataStorage().set("cwi_gas_pollution", clouds);
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            var pos = h.absolutePos(new BlockPos(3,3,3));
            player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            if (shielding > 0) {
                var helmet = new ItemStack(Items.IRON_HELMET);
                helmet.enchant(ProtectiveEnchantmentGameTests.enchantment(h, "ocular_shielding"), shielding);
                player.setItemSlot(EquipmentSlot.HEAD, helmet);
            }
            var eye = BlockPos.containing(player.getEyePosition());
            var gases = new GasProfile[]{GasProfiles.byId("kubejs:chlorine"),GasProfiles.byId("kubejs:ammonia"),GasProfiles.byId("kubejs:hydrogen_sulfide")};
            for (int i = 0; i < gases.length; i++) if (amounts[i] > 0)
                h.assertTrue(clouds.emit(level, eye, gases[i], amounts[i], false) == amounts[i], "fixture emits exact eye exposure");
            GasExposure.tick(player);
            return player;
        } finally {
            level.getDataStorage().set("cwi_gas_pollution", original);
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(oldTick);
        }
    }

    @GameTest(template="machine_empty", templateNamespace="cwi")
    public static void absentGasesCannotWearEyeProtection(GameTestHelper h) throws Exception {
        // The startup registry is frozen in a running server. Temporarily install the
        // immutable policy under test, restoring it before any other server work runs.
        var field = GasProfiles.class.getDeclaredField("profiles");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") var profiles = (Map<String, GasProfile>)field.get(null);
        var original = GasProfiles.byId("kubejs:chlorine");
        var chlorine = new GasProfile.Builder(original.id())
                .route("eyes", route -> route.noProtection())
                .effect("minecraft:blindness",Map.of("route","eyes","threshold",.1,"duration",40)).build();
        var level = h.getLevel();
        long oldTick = level.getGameTime();
        try {
            profiles.put(chlorine.id(), chlorine);
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            var pos = h.absolutePos(new BlockPos(3, 3, 3));
            player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            var helmet = new ItemStack(Items.IRON_HELMET);
            helmet.enchant(ProtectiveEnchantmentGameTests.enchantment(h, "ocular_shielding"), 4);
            player.setItemSlot(EquipmentSlot.HEAD, helmet);
            var eye = BlockPos.containing(player.getEyePosition());
            h.assertTrue(GasClouds.get(level).emit(level, eye, chlorine, 500, false) == 500, "chlorine emitted");
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime((oldTick / 20 + 1) * 20);
            GasExposure.tick(player);
            h.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS), "noProtection leaves chlorine eye irritation active");
            h.assertTrue(helmet.getDamageValue() == 0, "absent ammonia and sulfide must not charge their eye protection policies");
        } finally {
            profiles.put(original.id(), original);
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(oldTick);
        }
        h.succeed();
    }
}
