package net.quiwh.cwi.hammer;

import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/** Server-side actions shared by native hammer items and focused tests. */
public final class HammerActions {
    private HammerActions() {}

    public static boolean apply(Level level, Player player, int stage) {
        return apply(level, player, stage, null);
    }

    public static boolean apply(Level level, Player player, int stage, BiConsumer<LivingEntity, Float> afterImpact) {
        if (stage <= 0 || level.isClientSide()) return false;
        HitResult hit = player.pick(5, 1, false);
        var eye = player.getEyePosition();
        var reach = player.getViewVector(1).scale(5);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, eye.add(reach),
                player.getBoundingBox().expandTowards(reach).inflate(1),
                entity -> entity instanceof LivingEntity && !entity.isSpectator() && entity.isPickable(),
                eye.distanceToSqr(hit.getLocation()));
        if (entityHit != null) hit = entityHit;
        if (!level.isClientSide()) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW,
                    SoundSource.PLAYERS, 1F, 1F);
        }
        if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult entityResult) {
            Entity target = entityResult.getEntity();
            if (!(target instanceof LivingEntity living)) return false;
            float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * stage);
            boolean hurt = living.hurt(player.damageSources().playerAttack(player), damage);
            if (hurt) {
                living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 5 * stage, 255, true, true));
                level.playSound(null, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                        SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 1F, 1F);
                if (level instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() / 2,
                            target.getZ(), 16 * stage, 0.2, 0.2, 0.2, 0.6 * Math.sqrt(stage));
                }
                if (afterImpact != null) afterImpact.accept(living, damage);
            }
            return hurt;
        }
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) return false;
        BlockPos pos = blockHit.getBlockPos();
        if (!player.mayInteract(level, pos)) return false;
        if (blockHit.getDirection() == Direction.UP && applyDepotConversion(level, pos, stage)) {
            return true;
        }
        return applyBlockConversions(level, pos, stage) > 0;
    }

    /** Applies the old hammer's repeated-blow semantics, stopping at the first missing recipe. */
    public static int applyBlockConversions(Level level, BlockPos pos, int stage) {
        if (level == null || level.isClientSide() || pos == null || stage <= 0) return 0;
        int applied = 0;
        for (int blow = 0; blow < stage; blow++) {
            BlockState state = level.getBlockState(pos);
            ItemStack input = state.isAir() ? ItemStack.EMPTY : new ItemStack(state.getBlock().asItem());
            if (input.isEmpty()) break;
            Optional<net.minecraft.world.item.crafting.RecipeHolder<HammerRecipe>> match =
                    HammerRecipes.findBlock(level, input, stage);
            if (match.isEmpty()) break;
            HammerRecipe recipe = match.get().value();
            ItemStack target = recipe.target();
            BlockState targetState = target.isEmpty() ? Blocks.AIR.defaultBlockState()
                    : target.getItem() instanceof BlockItem blockItem
                    ? blockItem.getBlock().defaultBlockState() : null;
            if (targetState == null || (!state.equals(targetState) && !level.setBlock(pos, targetState, 3))) break;
            if (!level.isClientSide()) {
                level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 1F, 1F);
                if (level instanceof ServerLevel server) {
                    server.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST,
                                    Blocks.GRAVEL.defaultBlockState()), pos.getX() + 0.5, pos.getY() + 0.5,
                            pos.getZ() + 0.5, 16, 0.6, 0.6, 0.6, 0.05);
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5,
                            pos.getY() + 0.5, pos.getZ() + 0.5, 60, 0.5, 0.5, 0.5, 0.05);
                }
            }
            for (HammerRecipe.Drop drop : recipe.drops()) {
                ItemStack stack = drop.stack();
                int count = 0;
                for (int item = 0; item < stack.getCount(); item++) {
                    if (level.random.nextFloat() < drop.chance()) count++;
                }
                if (count > 0) Block.popResource(level, pos, stack.copyWithCount(count));
            }
            applied++;
        }
        return applied;
    }

    /** Converts one depot input and writes extra output to Create's processing buffer. */
    public static boolean applyDepotConversion(Level level, BlockPos pos, int stage) {
        if (level == null || level.isClientSide() || pos == null || stage <= 0) return false;
        if (!(level.getBlockEntity(pos) instanceof DepotBlockEntity depot)) return false;
        ItemStack held = depot.getHeldItem();
        if (held.isEmpty()) return false;
        Optional<net.minecraft.world.item.crafting.RecipeHolder<HammerRecipe>> match =
                HammerRecipes.findDepot(level, held, stage);
        if (match.isEmpty()) return false;
        ItemStack output = match.get().value().target();
        if (held.getCount() == 1) {
            depot.setHeldItem(output);
            depot.notifyUpdate();
        } else {
            var processing = depot.getBehaviour(TransportedItemStackHandlerBehaviour.TYPE);
            var inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (processing == null || !hasOutputSpace(inventory, output)) return false;
            boolean[] converted = {false};
            processing.handleProcessingOnAllItems(transported -> {
                var remainder = transported.copy();
                remainder.stack.shrink(1);
                var convertedItem = transported.getSimilar();
                convertedItem.stack = output.copy();
                converted[0] = true;
                return TransportedResult.convertToAndLeaveHeld(List.of(convertedItem), remainder);
            });
            if (!converted[0]) return false;
        }
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 1F, 1F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, output), pos.getX() + 0.5,
                    pos.getY() + 0.9, pos.getZ() + 0.5, 12, 0.1, 0.3, 0.1, 0.2);
        }
        return true;
    }

    /** Depot capability slot 0 is held input; all remaining slots are processing output. */
    private static boolean hasOutputSpace(IItemHandler inventory, ItemStack output) {
        if (inventory == null) return false;
        int remaining = output.getCount();
        for (int slot = 1; slot < inventory.getSlots(); slot++) {
            ItemStack existing = inventory.getStackInSlot(slot);
            if (existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, output)) {
                remaining -= Math.max(0, Math.min(inventory.getSlotLimit(slot), output.getMaxStackSize()) - existing.getCount());
                if (remaining <= 0) return true;
            }
        }
        return false;
    }
}
