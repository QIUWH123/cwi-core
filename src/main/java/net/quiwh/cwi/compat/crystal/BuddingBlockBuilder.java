package net.quiwh.cwi.compat.crystal;

import com.mojang.serialization.MapCodec;
import dev.latvian.mods.kubejs.block.BlockBuilder;
import dev.latvian.mods.kubejs.block.KubeJSBlockProperties;
import dev.latvian.mods.kubejs.block.callback.BlockStateModifyCallback;
import dev.latvian.mods.kubejs.block.callback.BlockStateModifyPlacementCallback;
import dev.latvian.mods.kubejs.block.callback.RandomTickCallback;
import dev.latvian.mods.kubejs.client.ModelGenerator;
import dev.latvian.mods.kubejs.client.VariantBlockStateGenerator;
import dev.latvian.mods.kubejs.core.LevelKJS;
import dev.latvian.mods.kubejs.generator.KubeAssetGenerator;
import dev.latvian.mods.kubejs.generator.KubeDataGenerator;
import dev.latvian.mods.kubejs.script.ScriptType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Script-owned crystal families using vanilla's single-face random-tick growth. */
public final class BuddingBlockBuilder extends BlockBuilder {
    private List<ResourceLocation> stages = List.of(
            ResourceLocation.withDefaultNamespace("small_amethyst_bud"),
            ResourceLocation.withDefaultNamespace("medium_amethyst_bud"),
            ResourceLocation.withDefaultNamespace("large_amethyst_bud"),
            ResourceLocation.withDefaultNamespace("amethyst_cluster"));
    private double growthChance = 0.2;
    private Direction[] growthFaces = Direction.values();
    private boolean growthInWater = true;
    private ResourceLocation growthSound;
    private float soundVolume = 1;
    private float soundPitch = 1;
    private ResourceLocation growthParticles;
    private int particleCount;
    private final List<EffectSpec> growthEffects = new ArrayList<>();
    private ResourceLocation explicitModel;

    public BuddingBlockBuilder(ResourceLocation id) {
        super(id);
        hardness = 1.5F;
        resistance = 1.5F;
        soundType = SoundType.AMETHYST;
        mapColorFn = state -> MapColor.COLOR_PURPLE;
        requiresTool = true;
    }

    public BuddingBlockBuilder stages(List<String> values) {
        if (values == null || values.isEmpty()) throw invalid("stages must not be empty");
        List<ResourceLocation> parsed = values.stream().map(ResourceLocation::parse).toList();
        if (new HashSet<>(parsed).size() != parsed.size() || parsed.contains(id))
            throw invalid("stages must be distinct cluster blocks and cannot include the source");
        stages = parsed;
        return this;
    }

    public BuddingBlockBuilder growthChance(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1)
            throw invalid("growthChance must be between 0 and 1");
        growthChance = value;
        return this;
    }

    public BuddingBlockBuilder growthFaces(List<String> values) {
        if (values == null || values.isEmpty()) throw invalid("growthFaces must not be empty");
        Direction[] parsed = new Direction[values.size()];
        var used = new HashSet<Direction>();
        for (int i = 0; i < parsed.length; i++) {
            String name = values.get(i);
            Direction direction = name == null ? null : Direction.byName(name.toLowerCase(Locale.ROOT));
            if (direction == null || !used.add(direction))
                throw invalid("growthFaces must contain distinct direction names");
            parsed[i] = direction;
        }
        growthFaces = parsed;
        return this;
    }

    public BuddingBlockBuilder growthInWater(boolean value) {
        growthInWater = value;
        return this;
    }

    public BuddingBlockBuilder growthSound(String sound) {
        return growthSound(sound, 1, 1);
    }

    public BuddingBlockBuilder growthSound(String sound, float volume, float pitch) {
        if (!Float.isFinite(volume) || volume < 0 || !Float.isFinite(pitch) || pitch <= 0)
            throw invalid("growthSound requires a nonnegative volume and positive pitch");
        growthSound = ResourceLocation.parse(sound);
        soundVolume = volume;
        soundPitch = pitch;
        return this;
    }

    public BuddingBlockBuilder growthParticles(String particle, int count) {
        if (count <= 0) throw invalid("growthParticles count must be positive");
        growthParticles = ResourceLocation.parse(particle);
        particleCount = count;
        return this;
    }

    public BuddingBlockBuilder growthEffect(String effect, int duration, double radius) {
        return growthEffect(effect, duration, radius, 1);
    }

    public BuddingBlockBuilder growthEffect(String effect, int duration, double radius, int level) {
        if (duration <= 0 || !Double.isFinite(radius) || radius <= 0 || level < 1 || level > 256)
            throw invalid("growthEffect requires positive ticks/radius and a level from 1 to 256");
        growthEffects.add(new EffectSpec(ResourceLocation.parse(effect), duration, radius, level - 1));
        return this;
    }

    /** Reference an existing block model instead of generating the default cube. */
    public BuddingBlockBuilder model(ResourceLocation model) {
        explicitModel = java.util.Objects.requireNonNull(model, "model");
        return this;
    }

    @Override
    public BlockBehaviour.Properties createProperties() {
        var properties = super.createProperties().randomTicks().pushReaction(PushReaction.DESTROY);
        if (!customShape.isEmpty()) properties.noOcclusion();
        return properties;
    }

    @Override
    public Block createObject() {
        if (blockEntityInfo != null) throw invalid("budding does not support block entities");
        return new BuddingBlock(this, createProperties());
    }

    @Override
    public LootTable generateLootTable(KubeDataGenerator generator) {
        return drops == null ? LootTable.lootTable().setParamSet(LootContextParamSets.BLOCK).build()
                : super.generateLootTable(generator);
    }

    @Override
    protected void generateBlockModels(KubeAssetGenerator generator) {
        if (explicitModel != null) return;
        generator.blockModel(id, model -> {
            if (modelGenerator != null) {
                modelGenerator.accept(model);
            } else if (parentModel != null) {
                model.parent(parentModel);
                model.textures(textures);
            } else if (textures.isEmpty()) {
                model.parent(KubeAssetGenerator.CUBE_ALL_BLOCK_MODEL);
                model.texture("all", baseTexture);
            } else {
                model.parent(KubeAssetGenerator.CUBE_BLOCK_MODEL);
                model.textures(textures);
            }
        });
    }

    @Override
    protected void generateBlockState(VariantBlockStateGenerator generator) {
        generator.simpleVariant("", blockModel());
    }

    @Override
    protected void generateItemModel(ModelGenerator generator) {
        generator.parent(blockModel());
    }

    private ResourceLocation blockModel() {
        return explicitModel != null ? explicitModel : id.withPrefix("block/");
    }

    /** Called once after native registries have finished, including script registrations. */
    public static void validateAll() {
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof BuddingBlock budding) budding.resolve();
        }
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Budding block " + id + ": " + message);
    }

    private record EffectSpec(ResourceLocation id, int duration, double radius, int amplifier) { }
    private record GrowthEffect(Holder<MobEffect> effect, int duration, double radius, int amplifier) { }

    public static final class BuddingBlock extends AmethystBlock implements SimpleWaterloggedBlock {
        private final BuddingBlockBuilder builder;
        private final VoxelShape shape;
        private final double chance;
        private final Direction[] faces;
        private final boolean inWater;
        private Block[] resolvedStages;
        private SoundEvent sound;
        private SimpleParticleType particle;
        private GrowthEffect[] effects;

        private BuddingBlock(BuddingBlockBuilder builder, Properties properties) {
            super(properties);
            this.builder = builder;
            shape = BlockBuilder.createShape(builder.customShape);
            chance = builder.growthChance;
            faces = builder.growthFaces.clone();
            inWater = builder.growthInWater;
            if (defaultBlockState().hasProperty(BlockStateProperties.WATERLOGGED))
                registerDefaultState(defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
            if (builder.defaultStateModification != null) {
                var callback = new BlockStateModifyCallback(defaultBlockState());
                if (callback(builder.defaultStateModification, callback, "default state"))
                    registerDefaultState(callback.getState());
            }
        }

        @Override
        public MapCodec<? extends AmethystBlock> codec() {
            return simpleCodec(properties -> new BuddingBlock(builder, properties));
        }

        private void resolve() {
            resolvedStages = new Block[builder.stages.size()];
            for (int i = 0; i < resolvedStages.length; i++) {
                ResourceLocation stageId = builder.stages.get(i);
                Block stage = BuiltInRegistries.BLOCK.getOptional(stageId)
                        .orElseThrow(() -> builder.invalid("unknown growth stage " + stageId));
                if (!(stage instanceof AmethystClusterBlock)
                        || !stage.defaultBlockState().hasProperty(AmethystClusterBlock.FACING)
                        || !stage.defaultBlockState().hasProperty(AmethystClusterBlock.WATERLOGGED))
                    throw builder.invalid("growth stage " + stageId + " must be an amethyst-style cluster");
                resolvedStages[i] = stage;
            }
            if (builder.growthSound != null)
                sound = BuiltInRegistries.SOUND_EVENT.getOptional(builder.growthSound)
                        .orElseThrow(() -> builder.invalid("unknown growth sound " + builder.growthSound));
            if (builder.growthParticles != null) {
                var type = BuiltInRegistries.PARTICLE_TYPE.getOptional(builder.growthParticles)
                        .orElseThrow(() -> builder.invalid("unknown growth particle " + builder.growthParticles));
                if (!(type instanceof SimpleParticleType simple))
                    throw builder.invalid("growthParticles requires a particle without additional parameters");
                particle = simple;
            }
            effects = new GrowthEffect[builder.growthEffects.size()];
            for (int i = 0; i < effects.length; i++) {
                EffectSpec spec = builder.growthEffects.get(i);
                MobEffect effect = BuiltInRegistries.MOB_EFFECT.getOptional(spec.id())
                        .orElseThrow(() -> builder.invalid("unknown growth effect " + spec.id()));
                effects[i] = new GrowthEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect),
                        spec.duration(), spec.radius(), spec.amplifier());
            }
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            grow(level, pos, random);
            if (builder.randomTickCallback != null)
                callback(builder.randomTickCallback, new RandomTickCallback(((LevelKJS) level).kjs$getBlock(pos), random), "random tick");
        }

        private void grow(ServerLevel level, BlockPos pos, RandomSource random) {
            if (chance == 0 || (chance == 0.2 ? random.nextInt(5) != 0 : random.nextDouble() >= chance)) return;
            Direction face = faces[random.nextInt(faces.length)];
            BlockPos target = pos.relative(face);
            if (!level.isInWorldBounds(target) || !level.hasChunkAt(target)) return;
            BlockState current = level.getBlockState(target);
            Block next = null;
            if (current.isAir() || inWater && BuddingAmethystBlock.canClusterGrowAtState(current)) {
                next = resolvedStages[0];
            } else if (current.hasProperty(AmethystClusterBlock.FACING)
                    && current.getValue(AmethystClusterBlock.FACING) == face
                    && (inWater || current.getFluidState().isEmpty())) {
                for (int i = 0; i < resolvedStages.length - 1; i++) {
                    if (current.is(resolvedStages[i])) {
                        next = resolvedStages[i + 1];
                        break;
                    }
                }
            }
            if (next == null) return;
            BlockState result = next.defaultBlockState().setValue(AmethystClusterBlock.FACING, face)
                    .setValue(AmethystClusterBlock.WATERLOGGED, current.getFluidState().getType() == Fluids.WATER);
            if (result.canSurvive(level, target) && level.setBlockAndUpdate(target, result)) growthFeedback(level, target);
        }

        private void growthFeedback(ServerLevel level, BlockPos pos) {
            if (sound != null) level.playSound(null, pos, sound, SoundSource.BLOCKS, builder.soundVolume, builder.soundPitch);
            if (particle != null) level.sendParticles(particle, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    builder.particleCount, 0.2, 0.2, 0.2, 0);
            if (effects.length == 0) return;
            Vec3 center = Vec3.atCenterOf(pos);
            for (GrowthEffect effect : effects) {
                double radius = effect.radius();
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(radius),
                        entity -> entity.isAlive() && entity.distanceToSqr(center) <= radius * radius))
                    entity.addEffect(new MobEffectInstance(effect.effect(), effect.duration(), effect.amplifier()));
            }
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return shape;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> definition) {
            if (properties instanceof KubeJSBlockProperties kubeProperties)
                kubeProperties.blockBuilder.blockStateProperties.forEach(property -> definition.add(property));
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            if (builder.placementStateModification != null) {
                var callback = new BlockStateModifyPlacementCallback(context, this);
                if (callback(builder.placementStateModification, callback, "placement state")) return callback.getState();
            }
            BlockState state = defaultBlockState();
            return state.hasProperty(BlockStateProperties.WATERLOGGED)
                    ? state.setValue(BlockStateProperties.WATERLOGGED,
                            context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER) : state;
        }

        @Override
        protected FluidState getFluidState(BlockState state) {
            return state.getOptionalValue(BlockStateProperties.WATERLOGGED).orElse(false)
                    ? Fluids.WATER.getSource(false) : super.getFluidState(state);
        }

        @Override
        protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                BlockPos pos, BlockPos neighborPos) {
            if (state.getOptionalValue(BlockStateProperties.WATERLOGGED).orElse(false))
                level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
        }

        @Override
        public boolean canPlaceLiquid(Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
            return builder.canBeWaterlogged() && SimpleWaterloggedBlock.super.canPlaceLiquid(player, level, pos, state, fluid);
        }

        @Override
        public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
            return builder.canBeWaterlogged() && SimpleWaterloggedBlock.super.placeLiquid(level, pos, state, fluid);
        }

        @Override
        public ItemStack pickupBlock(Player player, LevelAccessor level, BlockPos pos, BlockState state) {
            return builder.canBeWaterlogged() ? SimpleWaterloggedBlock.super.pickupBlock(player, level, pos, state) : ItemStack.EMPTY;
        }

        @Override
        public Optional<SoundEvent> getPickupSound() {
            return builder.canBeWaterlogged() ? SimpleWaterloggedBlock.super.getPickupSound() : Optional.empty();
        }

        private <T> boolean callback(Consumer<T> consumer, T value, String action) {
            try {
                consumer.accept(value);
                return true;
            } catch (Throwable error) {
                ScriptType.STARTUP.console.error("Budding block " + builder.id + " " + action, error);
                return false;
            }
        }
    }
}
