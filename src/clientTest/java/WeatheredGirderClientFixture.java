import com.simibubi.create.content.decoration.girder.GirderBlock;
import com.simibubi.create.content.decoration.girder.GirderCTBehaviour;
import com.simibubi.create.content.decoration.girder.GirderEncasedShaftBlock;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.quiwh.cwi.content.weathered.WeatheredConnectedGirderModel;
import net.quiwh.cwi.content.weathered.WeatheredGirderBlock;
import net.quiwh.cwi.content.weathered.WeatheredGirderCTBehaviour;
import net.quiwh.cwi.content.weathered.WeatheredGirderClient;
import net.quiwh.cwi.content.weathered.WeatheredGirderEncasedShaftBlock;

/** Native CT calls and baked-quad selection; no client, window, world or graphics context is started. */
public final class WeatheredGirderClientFixture {
    private static int checks;
    private static sun.misc.Unsafe unsafe;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    public static void main(String[] args) throws Exception {
        MinimalNativeBootstrap.initialize();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        unsafe = (sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null);
        FixtureBlocks blocks = fixtureBlocks();
        var ordinary = blocks.ordinary();
        var weathered = blocks.weathered();
        var shaft = blocks.shaft();
        for (Block block : List.of(ordinary, weathered, shaft)) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) state.initCache();
        }
        var nativeCT = new GirderCTBehaviour();
        var weatheredCT = new WeatheredGirderCTBehaviour();
        BlockState ordinaryPole = ordinary.defaultBlockState();
        BlockState weatheredPole = weathered.defaultBlockState();
        BlockState ordinaryBeam = ordinaryPole.setValue(GirderBlock.X, true);
        BlockState weatheredBeam = weatheredPole.setValue(GirderBlock.Z, true);
        BlockState cross = weatheredBeam.setValue(GirderBlock.X, true);
        for (Direction face : Direction.values()) {
            BlockPos otherPos = BlockPos.ZERO.above();
            check(nativeCT.connectsTo(ordinaryPole, weatheredPole, null, BlockPos.ZERO, otherPos, face),
                    "transformed native CT connects ordinary pole to weathered pole: " + face);
            check(weatheredCT.connectsTo(weatheredPole, ordinaryPole, null, BlockPos.ZERO, otherPos, face),
                    "weathered CT connects back to ordinary pole: " + face);
            check(nativeCT.connectsTo(ordinaryPole, ordinaryPole, null, BlockPos.ZERO, otherPos, face),
                    "native same-material pole connection remains: " + face);
            check(weatheredCT.connectsTo(weatheredPole, weatheredPole, null, BlockPos.ZERO, otherPos, face),
                    "weathered same-material pole connection remains: " + face);
            for (BlockState other : List.of(ordinaryBeam, weatheredBeam, cross, shaft.defaultBlockState(),
                    Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState())) {
                check(!nativeCT.connectsTo(ordinaryPole, other, null, BlockPos.ZERO, otherPos, face),
                        "native pole rejects beams, shafts and unrelated blocks: " + face);
                check(!weatheredCT.connectsTo(weatheredPole, other, null, BlockPos.ZERO, otherPos, face),
                        "weathered pole rejects beams, shafts and unrelated blocks: " + face);
            }
            check(weatheredCT.getShift(weatheredPole, face, null) != null == face.getAxis().isHorizontal(),
                    "pole CT applies only to horizontal faces: " + face);
            check(weatheredCT.getShift(weatheredBeam, face, null) == null,
                    "horizontal beams retain their original texture: " + face);
            check(weatheredCT.getShift(shaft.defaultBlockState(), face, null) == null,
                    "shaft composite retains its original texture: " + face);
        }
        modelConnections(weathered, shaft);
        System.out.println("Weathered girder client: " + checks + " native checks passed");
    }

    private record FixtureBlocks(GirderBlock ordinary, WeatheredGirderBlock weathered,
                                 WeatheredGirderEncasedShaftBlock shaft) {}

    private static FixtureBlocks fixtureBlocks() throws Exception {
        // Native Block constructors need intrusive holders even when the fixture never registers them.
        // Isolate those transient holders, then restore the bootstrapped registry without adding entries.
        var registry = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        Field frozen = field(MappedRegistry.class, "frozen");
        Field intrusive = field(MappedRegistry.class, "unregisteredIntrusiveHolders");
        boolean previouslyFrozen = frozen.getBoolean(registry);
        Object previousHolders = intrusive.get(registry);
        int previousSize = registry.size();
        FixtureBlocks blocks;
        try {
            registry.unfreeze();
            intrusive.set(registry, new IdentityHashMap<>());
            blocks = new FixtureBlocks(new GirderBlock(BlockBehaviour.Properties.of()),
                    new WeatheredGirderBlock(BlockBehaviour.Properties.of()),
                    new WeatheredGirderEncasedShaftBlock(BlockBehaviour.Properties.of()));
        } finally {
            intrusive.set(registry, previousHolders);
            frozen.setBoolean(registry, previouslyFrozen);
        }
        check(registry.size() == previousSize && frozen.getBoolean(registry) == previouslyFrozen
                        && intrusive.get(registry) == previousHolders,
                "transient fixture blocks preserve the native registry and its frozen state");
        return blocks;
    }

    private static BakedModel model(List<BakedQuad> quads) {
        return (BakedModel) Proxy.newProxyInstance(WeatheredGirderClientFixture.class.getClassLoader(),
                new Class<?>[]{BakedModel.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getQuads" -> quads;
                    case "toString" -> "weathered girder fixture model";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new AssertionError("Unexpected model call: " + method.getName());
                });
    }

    private static BlockAndTintGetter world(Map<BlockPos, BlockState> states) {
        return (BlockAndTintGetter) Proxy.newProxyInstance(WeatheredGirderClientFixture.class.getClassLoader(),
                new Class<?>[]{BlockAndTintGetter.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getBlockState" -> states.getOrDefault(args[0], Blocks.AIR.defaultBlockState());
                    case "getFluidState" -> states.getOrDefault(args[0], Blocks.AIR.defaultBlockState()).getFluidState();
                    case "getBlockEntity" -> null;
                    case "getShade" -> 1F;
                    case "getBlockTint", "getBrightness", "getRawBrightness" -> 0;
                    case "getHeight" -> 384;
                    case "getMinBuildHeight" -> -64;
                    case "toString" -> "weathered girder fixture block view";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new AssertionError("Unexpected block-view call: " + method.getName());
                });
    }

    @SuppressWarnings("unchecked")
    private static void modelConnections(WeatheredGirderBlock girder, WeatheredGirderEncasedShaftBlock shaft)
            throws Exception {
        WeatheredGirderClient.init();
        Map<Direction, PartialModel> partials = (Map<Direction, PartialModel>)
                field(WeatheredGirderClient.class, "BRACKETS").get(null);
        Map<Direction, BakedQuad> markers = new EnumMap<>(Direction.class);
        Map<Direction, Object> previousModels = new EnumMap<>(Direction.class);
        Field bakedModel = field(PartialModel.class, "bakedModel");
        try {
            for (var entry : partials.entrySet()) {
                Direction direction = entry.getKey();
                PartialModel partial = entry.getValue();
                check(partial.modelLocation().toString().equals(
                                "cwi:block/weathered_metal_girder/bracket_" + direction.getSerializedName()),
                        "native partial uses the weathered model resource: " + direction);
                BakedQuad marker = (BakedQuad) unsafe.allocateInstance(BakedQuad.class);
                markers.put(direction, marker);
                previousModels.put(direction, bakedModel.get(partial));
                bakedModel.set(partial, model(List.of(marker)));
            }
            var wrapped = new WeatheredConnectedGirderModel(model(List.of()));
            Map<BlockPos, BlockState> states = new HashMap<>();
            BlockAndTintGetter world = world(states);
            BlockState beam = girder.defaultBlockState().setValue(GirderBlock.X, true);
            states.put(BlockPos.ZERO, beam);
            states.put(BlockPos.ZERO.east(), Blocks.STONE.defaultBlockState());
            states.put(BlockPos.ZERO.north(), Blocks.STONE.defaultBlockState());
            ModelData data = wrapped.getModelData(world, BlockPos.ZERO, beam, ModelData.EMPTY);
            check(wrapped.getQuads(beam, null, RandomSource.create(1), data, null).equals(List.of(markers.get(Direction.EAST))),
                    "native model gathering adds only the beam's connected horizontal bracket");
            check(wrapped.getQuads(beam, Direction.EAST, RandomSource.create(1), data, null).isEmpty(),
                    "bracket geometry is submitted once through the unculled query");
            check(wrapped.getQuads(beam, null, RandomSource.create(1), ModelData.EMPTY, null).isEmpty(),
                    "item/no-world model data cannot retain another block's brackets");

            BlockState composite = shaft.defaultBlockState().setValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS, Axis.X);
            states.put(BlockPos.ZERO, composite);
            data = wrapped.getModelData(world, BlockPos.ZERO, composite, ModelData.EMPTY);
            check(wrapped.getQuads(composite, null, RandomSource.create(1), data, null)
                            .equals(List.of(markers.get(Direction.NORTH))),
                    "shaft-axis faces reject brackets while the perpendicular girder accepts them");

            BlockState pole = girder.defaultBlockState();
            states.put(BlockPos.ZERO, pole);
            data = wrapped.getModelData(world, BlockPos.ZERO, pole, ModelData.EMPTY);
            check(wrapped.getQuads(pole, null, RandomSource.create(1), data, null).isEmpty(),
                    "a vertical pole cannot inherit horizontal beam bracket geometry");
        } finally {
            for (var entry : previousModels.entrySet()) bakedModel.set(partials.get(entry.getKey()), entry.getValue());
        }
    }
}
