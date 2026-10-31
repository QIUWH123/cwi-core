package net.quiwh.cwi;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.quiwh.cwi.kubejs.HammerItem;
import dev.latvian.mods.kubejs.core.EntityKJS;

import java.util.ArrayList;
import java.util.function.Consumer;

/** Catches absent/misordered callbacks, duplicate dispatch and failed-hit notifications. */
@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class HammerImpactGameTests {
    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void callbackRunsAfterSuccessfulHitWithOffhandContext(GameTestHelper h) {
        var fixture = setup(h, "impact_contract_hammer", InteractionHand.OFF_HAND);
        float health = fixture.target.getHealth();
        var sounds = new ArrayList<Float>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getLevel() == h.getLevel() && event.getSound() != null
                    && event.getSound().value().getLocation().equals(ResourceLocation.parse("cwi:steel_pipe")))
                sounds.add(event.getNewVolume());
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try { fixture.release(2); }
        finally { NeoForge.EVENT_BUS.unregister(listener); }
        var data = ((EntityKJS) fixture.target).kjs$getPersistentData();
        check(data.getInt("cwiImpactCalls") == 1 && data.getInt("cwiImpactStage") == 2, "callback runs once with charge stage");
        check(data.getFloat("cwiImpactDamage") > 0 && fixture.target.getHealth() < health, "callback reports the applied damage request");
        check(data.getString("cwiImpactHand").equals("OFF_HAND"), "offhand is preserved");
        check(data.getBoolean("cwiImpactPlayer") && data.getBoolean("cwiImpactLevel") && data.getBoolean("cwiImpactItem"),
                "Rhino exposes the same player, level and live held stack");
        check(data.getBoolean("cwiImpactAfterDamage"), "callback sees damage already applied");
        check(Math.abs(fixture.target.getDeltaMovement().y - Math.sqrt(2)) < .00001, "script changes target velocity");
        check(sounds.size() == 1 && Math.abs(sounds.getFirst() - .8 * Math.sqrt(2)) < .00001,
                "script plays the registered native sound once at the configured volume");
        check(fixture.stack.getDamageValue() == 2 && fixture.player.getCooldowns().isOnCooldown(fixture.hammer),
                "callback retains native durability and cooldown");
        check(!fixture.stack.has(DataComponents.CUSTOM_MODEL_DATA), "callback retains charge cleanup");
        fixture.target.discard();
        h.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void callbackRunsForMainhandFinishingBlow(GameTestHelper h) {
        var fixture = setup(h, "impact_contract_hammer", InteractionHand.MAIN_HAND);
        fixture.target.setHealth(1);
        fixture.release(3);
        var data = ((EntityKJS) fixture.target).kjs$getPersistentData();
        check(!fixture.target.isAlive() && data.getInt("cwiImpactCalls") == 1 && data.getInt("cwiImpactStage") == 3,
                "finishing blow still invokes the callback exactly once");
        check(data.getString("cwiImpactHand").equals("MAIN_HAND"), "mainhand context is preserved");
        check(fixture.stack.getDamageValue() == 3, "finishing blow retains native durability cost");
        fixture.target.discard();
        h.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void callbackSkipsUnchargedBlockedAndRejectedHits(GameTestHelper h) {
        var fixture = setup(h, "impact_contract_hammer", InteractionHand.MAIN_HAND);
        fixture.release(0);
        check(((EntityKJS) fixture.target).kjs$getPersistentData().getInt("cwiImpactCalls") == 0, "no callback for uncharged release");
        fixture.target.setInvulnerable(true);
        fixture.release(2);
        check(((EntityKJS) fixture.target).kjs$getPersistentData().getInt("cwiImpactCalls") == 0 && fixture.stack.getDamageValue() == 0,
                "rejected damage has no callback or wear");
        fixture.target.setInvulnerable(false);
        h.setBlock(new BlockPos(2, 3, 4), Blocks.OBSIDIAN);
        fixture.release(2);
        check(((EntityKJS) fixture.target).kjs$getPersistentData().getInt("cwiImpactCalls") == 0, "block conversion never dispatches entity callback");
        check(fixture.stack.getDamageValue() == 2, "block conversion still completes");
        h.setBlock(new BlockPos(2, 3, 4), Blocks.AIR);
        fixture.target.discard();
        fixture.release(2);
        check(fixture.stack.getDamageValue() == 2, "air miss has no wear");
        h.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void customReleaseStillOwnsTheAction(GameTestHelper h) {
        var fixture = setup(h, "impact_contract_hammer", InteractionHand.MAIN_HAND);
        var behavior = ((dev.latvian.mods.kubejs.core.ItemKJS) (Object) fixture.hammer).kjs$getItemBehavior();
        var original = behavior.releaseUsing;
        boolean[] called = {false};
        behavior.releaseUsing = (stack, level, entity, remaining) -> called[0] = true;
        try {
            fixture.release(2);
            check(called[0] && ((EntityKJS) fixture.target).kjs$getPersistentData().getInt("cwiImpactCalls") == 0,
                    "custom release does not also run native actions or afterImpact");
            check(fixture.stack.getDamageValue() == 0 && !fixture.stack.has(DataComponents.CUSTOM_MODEL_DATA),
                    "custom release keeps its own wear policy and native cleanup");
        } finally { behavior.releaseUsing = original; fixture.target.discard(); }
        h.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void ordinaryMeleeDoesNotDispatchChargedImpact(GameTestHelper h) {
        var fixture = setup(h, "impact_contract_hammer", InteractionHand.MAIN_HAND);
        fixture.hammer.hurtEnemy(fixture.stack, fixture.target, fixture.player);
        check(((EntityKJS) fixture.target).kjs$getPersistentData().getInt("cwiImpactCalls") == 0,
                "ordinary melee retains its separate KubeJS hurtEnemy callback");
        fixture.target.discard();
        h.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void callbackFailureKeepsNativeWearAndCleanup(GameTestHelper h) {
        var fixture = setup(h, "throwing_contract_hammer", InteractionHand.MAIN_HAND);
        fixture.release(1);
        check(((EntityKJS) fixture.target).kjs$getPersistentData().getBoolean("cwiThrowingImpactCalled"), "throwing callback was reached");
        check(fixture.stack.getDamageValue() == 1 && fixture.player.getCooldowns().isOnCooldown(fixture.hammer),
                "callback failure cannot bypass durability/cooldown after dealing damage");
        check(!fixture.stack.has(DataComponents.CUSTOM_MODEL_DATA), "failed callback clears charged model");
        fixture.target.discard();
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void customSoundsHaveNativeRegistryEntries(GameTestHelper h) {
        for (String name : new String[]{"steel_pipe", "player_spawn"}) {
            var id = ResourceLocation.fromNamespaceAndPath(CwiCore.ID, name);
            check(BuiltInRegistries.SOUND_EVENT.containsKey(id), "native sound registry entry missing: " + id);
            check(BuiltInRegistries.SOUND_EVENT.get(id).getLocation().equals(id), "sound registry maps to the matching resource");
        }
        h.succeed();
    }

    private static Fixture setup(GameTestHelper h, String id, InteractionHand hand) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(CwiCore.ID, id));
        check(item instanceof HammerItem, "startup .afterImpact registration failed: " + id);
        var hammer = (HammerItem) item;
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var origin = h.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        var target = h.spawn(EntityType.COW, new BlockPos(2, 2, 5));
        target.setNoAi(true);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        player.xRotO = player.getXRot();
        player.yRotO = player.getYRot();
        var stack = new ItemStack(hammer);
        player.setItemInHand(hand, stack);
        return new Fixture(h, player, target, hammer, stack, hand);
    }

    private record Fixture(GameTestHelper helper, Player player, Cow target, HammerItem hammer, ItemStack stack, InteractionHand hand) {
        void release(int stage) {
            player.getCooldowns().removeCooldown(hammer);
            hammer.use(helper.getLevel(), player, hand);
            int remaining = HammerItem.USE_DURATION - stage * hammer.chargeTime();
            hammer.onUseTick(helper.getLevel(), player, stack, remaining);
            hammer.releaseUsing(stack, helper.getLevel(), player, remaining);
        }
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
