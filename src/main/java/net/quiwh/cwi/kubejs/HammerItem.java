package net.quiwh.cwi.kubejs;

import dev.latvian.mods.kubejs.item.ItemBehavior;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.quiwh.cwi.hammer.HammerActions;

import java.util.function.Consumer;

/** Native charged hammer item created by the CWI KubeJS hammer builder. */
public final class HammerItem extends SwordItem {
    public static final int USE_DURATION = 100_000;
    private final int chargeTime;
    private final int maxStage;
    private final ItemBehavior behavior;
    private final Consumer<HammerImpactContext> afterImpact;

    public HammerItem(Tier tier, Item.Properties properties, int chargeTime, int maxStage) {
        this(tier, properties, chargeTime, maxStage, null);
    }

    public HammerItem(Tier tier, Item.Properties properties, int chargeTime, int maxStage, ItemBehavior behavior) {
        this(tier, properties, chargeTime, maxStage, behavior, null);
    }

    public HammerItem(Tier tier, Item.Properties properties, int chargeTime, int maxStage, ItemBehavior behavior,
                      Consumer<HammerImpactContext> afterImpact) {
        super(tier, properties);
        if (chargeTime <= 0 || maxStage <= 0) throw new IllegalArgumentException("Invalid hammer charge settings");
        this.chargeTime = chargeTime;
        this.maxStage = maxStage;
        this.behavior = behavior;
        this.afterImpact = afterImpact;
    }

    public int chargeTime() { return chargeTime; }
    public int maxStage() { return maxStage; }

    public static int stageFor(int duration, int elapsed, int chargeTime, int maxStage) {
        if (duration <= 0 || chargeTime <= 0 || maxStage <= 0) return 0;
        return Math.min(Math.max(0, elapsed) / chargeTime, maxStage);
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ItemBehavior behavior = behavior();
        if (behavior != null && behavior.use != null) {
            return behavior.use.use(level, player, hand)
                    ? startUsing(player, hand, stack) : InteractionResultHolder.fail(stack);
        }
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        return startUsing(player, hand, stack);
    }

    @Override public UseAnim getUseAnimation(ItemStack stack) {
        ItemBehavior behavior = behavior();
        return behavior != null && behavior.anim != null ? behavior.anim : UseAnim.SPEAR;
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) {
        ItemBehavior behavior = behavior();
        return behavior != null && behavior.useDuration != null
                ? Math.max(1, behavior.useDuration.applyAsInt(stack, entity)) : USE_DURATION;
    }

    @Override public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        int elapsed = Math.max(0, getUseDuration(stack, entity) - remainingUseDuration);
        int stage = stageFor(getUseDuration(stack, entity), elapsed, chargeTime, maxStage);
        var previous = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (!level.isClientSide() && stage > (previous == null ? 0 : previous.value())) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.TRIDENT_RETURN,
                    SoundSource.PLAYERS, 1F, 1.5F);
        }
        setStage(stack, stage);
    }

    @Override public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        setStage(stack, 0);
    }

    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        try {
            return super.finishUsingItem(stack, level, entity);
        } finally {
            setStage(stack, 0);
        }
    }

    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingUseDuration) {
        ItemBehavior behavior = behavior();
        if (behavior != null && behavior.releaseUsing != null) {
            try {
                behavior.releaseUsing.releaseUsing(stack, level, entity, remainingUseDuration);
            } finally {
                setStage(stack, 0);
            }
            return;
        }
        int duration = getUseDuration(stack, entity);
        int stage = stageFor(duration, duration - remainingUseDuration, chargeTime, maxStage);
        setStage(stack, 0);
        if (stage == 0 || level.isClientSide() || !(entity instanceof Player player)) return;
        InteractionHand hand = player.getUsedItemHand();
        if (HammerActions.apply(level, player, stage, afterImpact == null ? null : (target, damage) -> {
            try {
                afterImpact.accept(new HammerImpactContext(stack, level, player, target, hand, stage, damage));
            } catch (Exception error) {
                // Damage has already happened; script errors must not bypass native wear/cooldown.
                dev.latvian.mods.kubejs.script.ConsoleJS.STARTUP.error("Error in hammer .afterImpact for " + this, error);
            }
        })) {
            long cooldown = (long) (chargeTime * Math.sqrt(stage));
            player.getCooldowns().addCooldown(this, (int) Math.min(Integer.MAX_VALUE, Math.max(1L, cooldown)));
            stack.hurtAndBreak(stage, player, hand == InteractionHand.MAIN_HAND
                    ? net.minecraft.world.entity.EquipmentSlot.MAINHAND
                    : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        }
    }

    private InteractionResultHolder<ItemStack> startUsing(Player player, InteractionHand hand, ItemStack stack) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    private ItemBehavior behavior() {
        return behavior;
    }

    private static void setStage(ItemStack stack, int stage) {
        if (stage <= 0) stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        else stack.set(DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(stage));
    }
}
