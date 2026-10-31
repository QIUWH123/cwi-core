package net.quiwh.cwi.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;

public final class ContainerLocks {
    private static final String KEY = "cwi:lock";

    public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        var state = level.getBlockState(pos);
        var entity = level.getBlockEntity(pos);
        if (!(entity instanceof RandomizableContainer container)) return;
        var containers = connected(level, pos, entity);
        boolean hasLoot = containers.stream().anyMatch(be -> be instanceof RandomizableContainer random && random.getLootTable() != null);
        var rules = InteractionRecipes.rules(level, InteractionRecipes.Kind.LOCKPICKING).stream()
                .map(holder -> (InteractionRules.Lock) holder.value().rule())
                .filter(rule -> rule.blocks().stream().anyMatch(block -> block.test(state)))
                .filter(rule -> !rule.lootOnly() || hasLoot).toList();
        if (rules.isEmpty()) return;
        boolean unlocked = containers.stream().anyMatch(be -> be.getPersistentData().getInt(KEY) == -1
                || be.getPersistentData().getInt("Lock") == -1
                || be.getPersistentData().getCompound("kubejs").getInt("Lock") == -1);
        if (unlocked) {
            set(containers, -1);
            if (event.getHand() == InteractionHand.MAIN_HAND) openFeedback(level, pos, rules.getFirst().silverfishChance());
            return;
        }
        // Both hands are blocked, but one click can roll/wear only once.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        set(containers, 1);
        Player player = event.getEntity();
        var item = player.getMainHandItem();
        var rule = rules.stream().filter(value -> value.tool().test(item)).findFirst().orElse(null);
        if (rule == null) {
            player.displayClientMessage(Component.translatable("message.cwi.no_lockpick"), true);
            level.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1, .8f);
            return;
        }
        boolean success = level.random.nextDouble() < rule.chance();
        if (success) {
            set(containers, -1);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 2, 1.2f);
        }
        player.displayClientMessage(Component.translatable(success ? "message.cwi.lockpick_success" : "message.cwi.lockpick_failure"), true);
        if (rule.durability() > 0 && !player.isCreative()) item.hurtAndBreak(rule.durability(), player, Player.getSlotForHand(InteractionHand.MAIN_HAND));
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.NEUTRAL, 2, 1.2f);
    }

    private static List<BlockEntity> connected(ServerLevel level, BlockPos pos, BlockEntity first) {
        var result = new ArrayList<BlockEntity>();
        result.add(first);
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos otherPos = pos.relative(ChestBlock.getConnectedDirection(state));
            if (level.hasChunkAt(otherPos) && level.getBlockEntity(otherPos) instanceof RandomizableContainer
                    && level.getBlockState(otherPos).is(state.getBlock())) result.add(level.getBlockEntity(otherPos));
        }
        return result;
    }
    private static void set(List<BlockEntity> containers, int value) {
        for (var entity : containers) { entity.getPersistentData().putInt(KEY, value); entity.setChanged(); }
    }
    private static void openFeedback(ServerLevel level, BlockPos pos, double silverfishChance) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GRAVEL.defaultBlockState()),
                pos.getX() + .5, pos.getY() + .8, pos.getZ() + .5, 12, .25, .1, .25, 0);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + .5, pos.getY() + .8, pos.getZ() + .5,
                3, .25, .1, .25, 0);
        if (level.random.nextDouble() < silverfishChance) {
            var silverfish = EntityType.SILVERFISH.create(level);
            if (silverfish != null) {
                silverfish.setPos(pos.getX() + .5 + level.random.nextDouble() / 4, pos.getY() + .9,
                        pos.getZ() + .5 + level.random.nextDouble() / 4);
                level.addFreshEntity(silverfish);
            }
        }
    }
    private ContainerLocks() {}
}
