package net.quiwh.cwi;

import dev.latvian.mods.kubejs.registry.BuilderBase;
import dev.latvian.mods.kubejs.registry.BuilderFactory;
import dev.latvian.mods.kubejs.registry.BuilderTypeRegistry;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.compat.CwiKubeJSPlugin;
import net.quiwh.cwi.kubejs.HammerBuilder;
import net.quiwh.cwi.kubejs.HammerItem;
import net.quiwh.cwi.hammer.HammerActions;
import net.quiwh.cwi.hammer.HammerRecipes;

import java.util.HashMap;
import java.util.function.Consumer;

/** Contract for the KubeJS event.create(id, "hammer") item type. */
@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class HammerBuilderContractTest {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void hammerBuilder(GameTestHelper helper) {
        var registrations = new HashMap<ResourceLocation, Registration>();
        BuilderTypeRegistry registry = new BuilderTypeRegistry() {
            @Override
            public <T> void of(ResourceKey<Registry<T>> key, Consumer<Callback<T>> consumer) {
                if (key.equals(Registries.ITEM)) {
                    consumer.accept(new Callback<>() {
                        @Override
                        public void addDefault(Class<? extends BuilderBase<? extends T>> type, BuilderFactory factory) {
                        }

                        @Override
                        public void add(ResourceLocation id, Class<? extends BuilderBase<? extends T>> type,
                                         BuilderFactory factory) {
                            registrations.put(id, new Registration(type, factory));
                        }
                    });
                }
            }
        };

        new CwiKubeJSPlugin().registerBuilderTypes(registry);
        Registration registration = registrations.get(ResourceLocation.fromNamespaceAndPath("kubejs", "hammer"));
        require(registration != null, "CWI registers the kubejs:hammer item builder type");
        require(registration.type() == HammerBuilder.class, "hammer uses the native HammerBuilder class");

        var builder = registration.factory().createBuilder(ResourceLocation.fromNamespaceAndPath("cwi", "golden_hammer"));
        require(builder instanceof HammerBuilder, "factory creates a HammerBuilder");
        var hammer = (HammerBuilder) builder;
        require(hammer.maxStackSize == 1, "hammers are unstackable by default");
        require(hammer.defaultTags.contains(ItemTags.SWORDS.location()), "hammers retain sword/tool compatibility");
        require(!hammer.defaultTags.contains(ResourceLocation.parse("minecraft:hammers"))
                        && !hammer.defaultTags.contains(ResourceLocation.parse("cwi:hammers")),
                "hammer recipe catalyst tags are opt-in, never supplied by the builder");
        hammer.tag(new ResourceLocation[]{ResourceLocation.parse("cwi:hammers")});
        require(hammer.defaultTags.contains(ResourceLocation.parse("cwi:hammers")),
                "item registration can explicitly opt into cwi:hammers with tag()");
        require(hammer.defaultTags.contains(ResourceLocation.fromNamespaceAndPath("forge", "tools")),
                "hammers have the forge:tools tag");

        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("kubejs", "golden_hammer"));
        require(item instanceof HammerItem, "startup registration creates the native hammer item");
        var registeredHammer = (HammerItem) item;
        require(registeredHammer.chargeTime() == 10 && registeredHammer.maxStage() == 3,
                "registered hammer receives its configured charge settings");
        require(item.getDefaultMaxStackSize() == 1, "registered hammer items are unstackable");
        require(item.getUseDuration(new ItemStack(item), null) == HammerItem.USE_DURATION,
                "hammer uses the long charge duration");
        require(item.getUseAnimation(new ItemStack(item)) == UseAnim.SPEAR,
                "hammer uses the spear charge animation");
        require(HammerItem.stageFor(12, 0, 12, 2) == 0 && HammerItem.stageFor(12, 12, 12, 2) == 1
                        && HammerItem.stageFor(12, 24, 12, 2) == 2 && HammerItem.stageFor(12, 100, 12, 2) == 2,
                "hammer charge duration maps to capped stages");

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void hammerCatalystMembershipRequiresAnExplicitScriptTag(GameTestHelper helper) {
        var selected = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("cwi:model_contract_hammer")));
        var unselected = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("cwi:manual_model_contract_hammer")));
        var pipe = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("kubejs:steel_pipe")));
        var tag = net.minecraft.tags.TagKey.create(Registries.ITEM, ResourceLocation.parse("cwi:hammers"));
        var legacy = net.minecraft.tags.TagKey.create(Registries.ITEM, ResourceLocation.parse("minecraft:hammers"));
        require(selected.getItem() instanceof HammerItem && unselected.getItem() instanceof HammerItem
                        && pipe.getItem() instanceof HammerItem, "all fixtures retain the native hammer behavior");
        require(selected.is(tag), "a real startup .tag('cwi:hammers') opts into catalyst membership");
        require(!unselected.is(tag) && !pipe.is(tag), "untagged hammer builders and manual-model pipe stay outside cwi:hammers");
        require(!selected.is(legacy) && !unselected.is(legacy) && !pipe.is(legacy),
                "the builder never supplies the legacy minecraft:hammers tag");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void hammerBlockConversion(GameTestHelper helper) {
        var level = helper.getLevel();
        var relativePos = new BlockPos(1, 1, 1);
        var pos = helper.absolutePos(relativePos);
        helper.setBlock(relativePos, net.minecraft.world.level.block.Blocks.SMOOTH_BASALT);
        int conversions = HammerActions.applyBlockConversions(level, pos, 2);
        require(conversions == 1, "hammer applies one matching block recipe");
        require(level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.STONE),
                "hammer block recipe replaces the targeted block");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void hammerDepotConversion(GameTestHelper helper) {
        var level = helper.getLevel();
        var relativePos = new BlockPos(1, 1, 1);
        var pos = helper.absolutePos(relativePos);
        helper.setBlock(relativePos, AllBlocks.DEPOT.getDefaultState());
        var depot = (DepotBlockEntity) level.getBlockEntity(pos);
        require(depot != null, "Create depot block entity is present");
        depot.setHeldItem(new ItemStack(Items.RAW_IRON, 2));
        require(!depot.getHeldItem().isEmpty(), "depot accepts the held input");
        require(HammerRecipes.findDepot(level, depot.getHeldItem(), 3).isPresent(),
                "hammer depot recipe is loaded and matches");
        require(HammerActions.applyDepotConversion(level, pos, 3), "hammer depot recipe applies");
        require(depot.getHeldItem().is(Items.RAW_IRON) && depot.getHeldItem().getCount() == 1,
                "depot conversion consumes exactly one held input");
        var inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        require(inventory != null && inventory.getStackInSlot(1).getCount() == 1
                        && inventory.getStackInSlot(1).is(BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("create", "crushed_raw_iron"))),
                "depot conversion uses Create's output buffer");
        require(HammerActions.applyDepotConversion(level, pos, 3), "last held input also converts");
        require(depot.getHeldItem().is(BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:crushed_raw_iron")))
                && depot.getHeldItem().getCount() == 1, "last input becomes the displayed depot output");
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void hammerHitsLivingEntities(GameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var origin = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        var target = helper.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(2, 2, 5));
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        player.xRotO = player.getXRot();
        player.yRotO = player.getYRot();
        var hammer = (HammerItem) BuiltInRegistries.ITEM.get(ResourceLocation.parse("kubejs:golden_hammer"));
        var stack = new ItemStack(hammer);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        hammer.use(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
        int remaining = HammerItem.USE_DURATION - 2 * hammer.chargeTime();
        hammer.onUseTick(helper.getLevel(), player, stack, remaining);
        float health = target.getHealth();
        hammer.releaseUsing(stack, helper.getLevel(), player, remaining);
        require(target.getHealth() < health && target.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),
                "hammer deals damage and slowness");
        require(stack.getDamageValue() == 2 && player.getCooldowns().isOnCooldown(hammer),
                "successful native release applies stage durability and cooldown");
        require(!stack.has(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA), "release clears the charged model");
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void hammerCannotHitThroughBlocks(GameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var origin = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        var target = helper.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(2, 2, 5));
        helper.setBlock(new BlockPos(2, 2, 4), net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(new BlockPos(2, 3, 4), net.minecraft.world.level.block.Blocks.STONE);
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        player.xRotO = player.getXRot();
        player.yRotO = player.getYRot();
        float health = target.getHealth();
        require(!HammerActions.apply(helper.getLevel(), player, 2) && target.getHealth() == health,
                "hammer respects the nearest solid block");
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void hammerSameBlockConversion(GameTestHelper helper) {
        var relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, net.minecraft.world.level.block.Blocks.OBSIDIAN);
        require(HammerActions.applyBlockConversions(helper.getLevel(), helper.absolutePos(relative), 3) == 3,
                "same-block recipes count every successful blow");
        int drops = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .filter(entity -> entity.getItem().is(Items.DIAMOND)).mapToInt(entity -> entity.getItem().getCount()).sum();
        require(drops == 3, "same-block conversion drops once per successful blow");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void hammerRollsEachDropSeparately(GameTestHelper helper) {
        var relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, net.minecraft.world.level.block.Blocks.END_STONE);
        helper.getLevel().random.setSeed(12345L);
        HammerActions.applyBlockConversions(helper.getLevel(), helper.absolutePos(relative), 1);
        int drops = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .filter(entity -> entity.getItem().is(Items.EMERALD)).mapToInt(entity -> entity.getItem().getCount()).sum();
        require(drops > 0 && drops < 64, "each of 64 drops rolls independently instead of the whole stack");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void hammerUsesWholeDepotBuffer(GameTestHelper helper) {
        var relative = new BlockPos(1, 1, 1);
        var pos = helper.absolutePos(relative);
        helper.setBlock(relative, AllBlocks.DEPOT.getDefaultState());
        var depot = (DepotBlockEntity) helper.getLevel().getBlockEntity(pos);
        depot.setHeldItem(new ItemStack(Items.RAW_COPPER, 10));
        var inventory = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        for (int slot = 1; slot < inventory.getSlots(); slot++) {
            require(HammerActions.applyDepotConversion(helper.getLevel(), pos, 1), "available depot output slot " + slot);
            require(inventory.getStackInSlot(slot).is(Items.COPPER_INGOT)
                    && inventory.getStackInSlot(slot).getCount() == 64, "converted output occupies buffer slot " + slot);
        }
        int remaining = depot.getHeldItem().getCount();
        require(!HammerActions.applyDepotConversion(helper.getLevel(), pos, 1), "full buffer refuses conversion");
        require(depot.getHeldItem().getCount() == remaining, "full buffer preserves input");
        require(helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).isEmpty(), "buffer overflow cannot spill items");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void hammerClearsStageWhenStopped(GameTestHelper helper) {
        var hammer = (HammerItem) BuiltInRegistries.ITEM.get(ResourceLocation.parse("kubejs:golden_hammer"));
        var stack = new ItemStack(hammer);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        hammer.onUseTick(helper.getLevel(), player, stack, HammerItem.USE_DURATION - hammer.chargeTime());
        require(stack.has(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA), "charging sets the stage model");
        hammer.onStopUsing(stack, player, hammer.chargeTime());
        require(!stack.has(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA), "interruption clears the stage model");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void hammerClearsStageWhenFinished(GameTestHelper helper) {
        var hammer = (HammerItem) BuiltInRegistries.ITEM.get(ResourceLocation.parse("kubejs:golden_hammer"));
        var stack = new ItemStack(hammer);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        hammer.onUseTick(helper.getLevel(), player, stack, 1);
        hammer.finishUsingItem(stack, helper.getLevel(), player);
        require(!stack.has(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA), "full use duration clears the stage model");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void hammerCustomReleaseCleanup(GameTestHelper helper) {
        var hammer = (HammerItem) BuiltInRegistries.ITEM.get(ResourceLocation.parse("kubejs:golden_hammer"));
        var behavior = ((dev.latvian.mods.kubejs.core.ItemKJS) (Object) hammer).kjs$getItemBehavior();
        var previous = behavior.releaseUsing;
        boolean[] called = {false};
        behavior.releaseUsing = (stack, level, user, remaining) -> {
            called[0] = true;
            require(stack.has(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA), "callback sees charged stage");
        };
        try {
            var stack = new ItemStack(hammer);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            hammer.onUseTick(helper.getLevel(), player, stack, 1);
            hammer.releaseUsing(stack, helper.getLevel(), player, 1);
            require(called[0] && !stack.has(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA),
                    "custom release dispatches and clears its stage");
        } finally { behavior.releaseUsing = previous; }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void hammerDslBreakAndChanceZero(GameTestHelper helper) {
        var pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, net.minecraft.world.level.block.Blocks.GLASS);
        var recipe = HammerRecipes.findBlock(helper.getLevel(), new ItemStack(Items.GLASS), 2).orElseThrow().value();
        require(recipe.target().isEmpty() && recipe.drops().size() == 3, "fluent aliases support an omitted block target");
        require(recipe.drops().getFirst().stack().getCount() == 2 && recipe.drops().get(1).chance() == 0,
                "native stack counts and chance zero survive the KubeJS wrapper");
        require(HammerActions.applyBlockConversions(helper.getLevel(), helper.absolutePos(pos), 2) == 1,
                "omitted target removes the block and stops further blows");
        require(helper.getLevel().getBlockState(helper.absolutePos(pos)).isAir(), "break conversion leaves air");
        var drops = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM);
        require(drops.stream().noneMatch(entity -> entity.getItem().is(Items.STICK)), "chance zero produces no drop");
        require(drops.stream().filter(entity -> entity.getItem().is(Items.DIAMOND))
                .mapToInt(entity -> entity.getItem().getCount()).sum() == 2, "chance one produces every counted item");
        helper.succeed();
    }

    private record Registration(Class<? extends BuilderBase<?>> type, BuilderFactory factory) {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
