package net.quiwh.cwi.interaction.application;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.deployer.ManualApplicationRecipe;
import com.simibubi.create.foundation.utility.BlockHelper;
import java.util.Comparator;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Server authority, one selected recipe and one set of rolls for each accepted click. */
public final class ManualApplications {
    private ManualApplications() {}
    public static boolean apply(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getLevel().getBlockState(event.getPos()).isAir()) return false;
        var level = event.getLevel();
        var player = event.getEntity();
        var state = level.getBlockState(event.getPos());
        var held = event.getItemStack();
        var candidates = level.getRecipeManager().getAllRecipesFor(AllRecipeTypes.ITEM_APPLICATION.<net.neoforged.neoforge.items.wrapper.RecipeWrapper, ManualApplicationRecipe>getType());
        var match = candidates.stream().filter(h -> {
            var recipe = h.value();
            var options = ((ApplicationExtension)recipe).cwi$interaction();
            if (options == null) return false;
            if (!(options.block != null ? options.matchesState(state) : recipe.testBlock(state) && options.matchesState(state))) return false;
            if (options.sneaking != null && options.sneaking != player.isShiftKeyDown()) return false;
            return options.emptyHand ? held.isEmpty() : !held.isEmpty() && recipe.getRequiredHeldItem().test(held);
        }).sorted(Comparator.<net.minecraft.world.item.crafting.RecipeHolder<ManualApplicationRecipe>>comparingInt(h ->
                ((ApplicationExtension)h.value()).cwi$interaction().priority).reversed().thenComparing(h -> h.id().toString())).findFirst();
        if (match.isEmpty()) return false;
        // Consume matching attempts, including a cooldown or insufficient-cost attempt, so vanilla
        // stripping/block use and the other hand cannot run the same interaction a second time.
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if (!(level instanceof ServerLevel server)) return true;
        var holder = match.get();
        var recipe = holder.value();
        var options = ((ApplicationExtension)recipe).cwi$interaction();
        var timers = player.getPersistentData().getCompound("cwi:item_application_cooldowns");
        if (timers.getLong(holder.id().toString()) > level.getGameTime() || !held.isEmpty() && player.getCooldowns().isOnCooldown(held.getItem())) return true;
        if (!player.isCreative() && options.consumeHeld != null && held.getCount() < options.consumeHeld) return true;
        if (options.damageHeld != null && options.damageHeld > 0 && held.getMaxDamage() <= 0) return true;

        if (options.cooldown > 0) {
            timers.putLong(holder.id().toString(), level.getGameTime() + options.cooldown);
            player.getPersistentData().put("cwi:item_application_cooldowns", timers);
            if (!held.isEmpty()) player.getCooldowns().addCooldown(held.getItem(), options.cooldown);
        }
        var next = state;
        if (options.blockMode.equals("remove")) next = Blocks.AIR.defaultBlockState();
        if (options.blockMode.equals("replace") && !recipe.getRollableResults().isEmpty()) {
            var output = recipe.getRollableResults().getFirst();
            // A failed transformation retains the original; its supplemental drops still roll.
            if (output.getStack().getItem() instanceof BlockItem blockItem) {
                if (output.getChance() >= 1 || level.random.nextFloat() < output.getChance())
                    next = BlockHelper.copyProperties(state, blockItem.getBlock().defaultBlockState());
            } else next = Blocks.AIR.defaultBlockState();
        }
        if (next != state) level.setBlockAndUpdate(event.getPos(), next);
        var results = recipe.getRollableResults();
        int firstDrop = !results.isEmpty() && results.getFirst().getStack().getItem() instanceof BlockItem ? 1 : 0;
        for (var drop : results.subList(firstDrop, results.size())) {
            if (drop.getChance() >= 1 || level.random.nextFloat() < drop.getChance()) Block.popResource(level, event.getPos(), drop.getStack().copy());
        }
        var context = new ApplicationOptions.Context(server, event.getPos(), player);
        options.drops.forEach(drop -> drop.apply(context));
        if (!player.isCreative()) {
            if (options.damageHeld != null) held.hurtAndBreak(options.damageHeld, player,
                    event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            else if (options.consumeHeld != null) consume(event, options.consumeHeld);
            else if (!options.emptyHand && !recipe.shouldKeepHeldItem() && !held.has(net.minecraft.core.component.DataComponents.UNBREAKABLE)) {
                if (held.isDamageableItem()) held.hurtAndBreak(1, player,
                        event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                else consume(event, 1);
            }
            if (options.damagePlayer > 0) player.hurt(level.damageSources().generic(), options.damagePlayer);
        }
        options.actions.forEach(action -> action.apply(context));
        return true;
    }
    private static void consume(PlayerInteractEvent.RightClickBlock event, int count) {
        var held = event.getItemStack();
        var remainder = held.getCraftingRemainingItem();
        held.shrink(count);
        for (int n = 0; n < count && !remainder.isEmpty(); n++) {
            var copy = remainder.copy();
            if (held.isEmpty() && n == 0) event.getEntity().setItemInHand(event.getHand(), copy);
            else if (!event.getEntity().getInventory().add(copy)) event.getEntity().drop(copy, false);
        }
    }
}
