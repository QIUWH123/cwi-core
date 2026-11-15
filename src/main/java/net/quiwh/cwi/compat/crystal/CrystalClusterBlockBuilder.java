package net.quiwh.cwi.compat.crystal;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latvian.mods.kubejs.block.BlockBuilder;
import dev.latvian.mods.kubejs.block.BlockRenderType;
import dev.latvian.mods.kubejs.block.KubeJSBlockProperties;
import dev.latvian.mods.kubejs.block.callback.BlockStateModifyCallback;
import dev.latvian.mods.kubejs.block.callback.BlockStateModifyPlacementCallback;
import dev.latvian.mods.kubejs.client.ModelGenerator;
import dev.latvian.mods.kubejs.client.VariantBlockStateGenerator;
import dev.latvian.mods.kubejs.generator.KubeAssetGenerator;
import dev.latvian.mods.kubejs.generator.KubeDataGenerator;
import dev.latvian.mods.kubejs.util.ID;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Script-owned crystal stages with vanilla placement, growth-state and harvesting conventions. */
public final class CrystalClusterBlockBuilder extends BlockBuilder {
    private Stage stage = Stage.CLUSTER;
    private ResourceLocation explicitModel;

    public CrystalClusterBlockBuilder(ResourceLocation id) {
        super(id);
        hardness = resistance = 1.5F;
        lightLevel = Float.NaN;
        mapColorFn = state -> MapColor.COLOR_PURPLE;
        notSolid = true;
        opaque = false;
        renderType = BlockRenderType.CUTOUT;
    }

    /** Supplies vanilla shape, sound, light and loot defaults for this growth stage. */
    public CrystalClusterBlockBuilder stage(String name) {
        try {
            stage = Stage.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException(id + ": stage must be small, medium, large or cluster", exception);
        }
        return this;
    }

    /** Uses an existing model; collision and outline boxes remain independent. */
    public CrystalClusterBlockBuilder model(ResourceLocation model) {
        explicitModel = java.util.Objects.requireNonNull(model, "model");
        return this;
    }

    @Override
    public BlockBehaviour.Properties createProperties() {
        var properties = super.createProperties().forceSolidOn().pushReaction(PushReaction.DESTROY);
        if (soundType == null) properties.sound(stage.sound);
        int light = Float.isNaN(lightLevel) ? stage.light : (int) (lightLevel * 15F);
        return properties.lightLevel(state -> light);
    }

    @Override
    public Block createObject() {
        if (blockEntityInfo != null) throw new IllegalArgumentException(id + ": crystal_cluster does not support block entities");
        return new CrystalClusterBlock(this);
    }

    @Override
    protected void generateBlockModels(KubeAssetGenerator generator) {
        if (explicitModel != null) return;
        generator.blockModel(id, model -> {
            if (modelGenerator != null) {
                modelGenerator.accept(model);
                return;
            }
            model.parent(parentModel == null ? ResourceLocation.withDefaultNamespace("block/cross") : parentModel);
            model.texture("cross", baseTexture);
            model.textures(textures);
        });
    }

    private ResourceLocation blockModel() {
        return explicitModel == null ? id.withPrefix("block/") : explicitModel;
    }

    @Override
    protected void generateItemModel(ModelGenerator model) {
        if (explicitModel != null || parentModel != null || modelGenerator != null) {
            model.parent(blockModel());
            return;
        }
        String nativeItem = stage == Stage.CLUSTER ? "amethyst_cluster"
                : stage.name().toLowerCase(Locale.ROOT) + "_amethyst_bud";
        model.parent(ResourceLocation.withDefaultNamespace("item/" + nativeItem));
        model.texture("layer0", textures.getOrDefault("cross", baseTexture));
    }

    @Override
    protected void generateBlockState(VariantBlockStateGenerator generator) {
        for (Direction direction : Direction.values()) {
            generator.variant("facing=" + direction.getSerializedName(), variant -> {
                var model = variant.model(blockModel());
                switch (direction) {
                    case DOWN -> model.x(180);
                    case NORTH -> model.x(90);
                    case EAST -> model.x(90).y(90);
                    case SOUTH -> model.x(90).y(180);
                    case WEST -> model.x(90).y(270);
                    default -> { }
                }
            });
        }
    }

    @Override
    public void generateData(KubeDataGenerator generator) {
        if (drops != null) {
            super.generateData(generator);
            return;
        }
        // KubeJS emits startup data before dynamic enchantments exist. Keep references
        // symbolic so the native datapack loader resolves them at the correct lifecycle.
        JsonObject table = new JsonObject();
        table.addProperty("type", "minecraft:block");
        table.addProperty("random_sequence", id.withPrefix("blocks/").toString());
        JsonArray pools = new JsonArray();
        table.add("pools", pools);
        if (stage == Stage.CLUSTER || itemBuilder != null) {
            JsonObject pool = new JsonObject();
            pool.addProperty("rolls", 1.0);
            pool.addProperty("bonus_rolls", 0.0);
            JsonArray entries = new JsonArray();
            pool.add("entries", entries);
            JsonObject self = JsonParser.parseString("""
                    {"type":"minecraft:item","conditions":[{"condition":"minecraft:match_tool",
                     "predicate":{"predicates":{"minecraft:enchantments":[
                      {"enchantments":"minecraft:silk_touch","levels":{"min":1}}]}}}]}
                    """).getAsJsonObject();
            self.addProperty("name", itemBuilder == null ? id.toString() : itemBuilder.id.toString());
            if (stage == Stage.CLUSTER) {
                JsonObject shards = JsonParser.parseString("""
                        {"type":"minecraft:alternatives","children":[
                         {"type":"minecraft:item","name":"minecraft:amethyst_shard",
                          "conditions":[{"condition":"minecraft:match_tool",
                           "predicate":{"items":"#minecraft:cluster_max_harvestables"}}],
                          "functions":[{"function":"minecraft:set_count","count":4.0,"add":false},
                           {"function":"minecraft:apply_bonus","enchantment":"minecraft:fortune","formula":"minecraft:ore_drops"}]},
                         {"type":"minecraft:item","name":"minecraft:amethyst_shard",
                          "functions":[{"function":"minecraft:set_count","count":2.0,"add":false},
                           {"function":"minecraft:explosion_decay"}]}]}
                        """).getAsJsonObject();
                if (itemBuilder == null) entries.add(shards);
                else {
                    JsonObject alternatives = new JsonObject();
                    alternatives.addProperty("type", "minecraft:alternatives");
                    JsonArray children = new JsonArray();
                    children.add(self);
                    children.add(shards);
                    alternatives.add("children", children);
                    entries.add(alternatives);
                }
            } else {
                pool.add("conditions", self.remove("conditions"));
                entries.add(self);
            }
            pools.add(pool);
        }
        generator.json(id.withPath(ID.BLOCK_LOOT_TABLE), table);
    }

    private enum Stage {
        SMALL(3, 4, 1, SoundType.SMALL_AMETHYST_BUD),
        MEDIUM(4, 3, 2, SoundType.LARGE_AMETHYST_BUD),
        LARGE(5, 3, 4, SoundType.MEDIUM_AMETHYST_BUD),
        CLUSTER(7, 3, 5, SoundType.AMETHYST_CLUSTER);

        final float height;
        final float offset;
        final int light;
        final SoundType sound;

        Stage(float height, float offset, int light, SoundType sound) {
            this.height = height;
            this.offset = offset;
            this.light = light;
            this.sound = sound;
        }
    }

    /** Native inheritance retains water ticks, support loss, rotations and projectile sounds. */
    public static final class CrystalClusterBlock extends AmethystClusterBlock {
        private final CrystalClusterBlockBuilder builder;
        private final VoxelShape[] shapes;

        CrystalClusterBlock(CrystalClusterBlockBuilder builder) {
            super(builder.stage.height, builder.stage.offset, builder.createProperties());
            this.builder = builder;
            shapes = builder.customShape.isEmpty() ? null : rotateShapes(List.copyOf(builder.customShape));
            if (builder.defaultStateModification != null) {
                var callback = new BlockStateModifyCallback(defaultBlockState());
                builder.defaultStateModification.accept(callback);
                registerDefaultState(callback.getState());
            }
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> states) {
            super.createBlockStateDefinition(states);
            if (properties instanceof KubeJSBlockProperties kube) {
                for (var property : kube.blockBuilder.blockStateProperties) {
                    if (property != FACING && property != WATERLOGGED) states.add(property);
                }
            }
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return shapes == null ? super.getShape(state, level, pos, context) : shapes[state.getValue(FACING).ordinal()];
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            var state = super.getStateForPlacement(context);
            if (builder.placementStateModification == null) return state;
            var callback = new BlockStateModifyPlacementCallback(context, this);
            for (var entry : state.getValues().entrySet()) copyProperty(callback, entry.getKey(), entry.getValue());
            builder.placementStateModification.accept(callback);
            return callback.getState();
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static void copyProperty(BlockStateModifyCallback callback,
                net.minecraft.world.level.block.state.properties.Property property, Comparable value) {
            callback.setValue(property, value);
        }

        private static VoxelShape[] rotateShapes(List<AABB> boxes) {
            var rotated = new VoxelShape[Direction.values().length];
            for (Direction direction : Direction.values()) {
                VoxelShape shape = Shapes.empty();
                for (AABB box : boxes) shape = Shapes.or(shape, Shapes.create(rotate(box, direction)));
                rotated[direction.ordinal()] = shape.optimize();
            }
            return rotated;
        }

        private static AABB rotate(AABB box, Direction direction) {
            return switch (direction) {
                case UP -> box;
                case DOWN -> new AABB(box.minX, 1 - box.maxY, 1 - box.maxZ, box.maxX, 1 - box.minY, 1 - box.minZ);
                case NORTH -> new AABB(box.minX, box.minZ, 1 - box.maxY, box.maxX, box.maxZ, 1 - box.minY);
                case EAST -> new AABB(box.minY, box.minZ, box.minX, box.maxY, box.maxZ, box.maxX);
                case SOUTH -> new AABB(1 - box.maxX, box.minZ, box.minY, 1 - box.minX, box.maxZ, box.maxY);
                case WEST -> new AABB(1 - box.maxY, box.minZ, 1 - box.maxX, 1 - box.minY, box.maxZ, 1 - box.minX);
            };
        }
    }
}
