import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;

/** Native world/pack selection calls; only window creation and screen submission are replaced. */
public final class PackWarningFixture {
    private static sun.misc.Unsafe unsafe;
    private static int checks;

    private static final Pack.ResourcesSupplier NO_CONTENT = new Pack.ResourcesSupplier() {
        @Override
        public PackResources openPrimary(PackLocationInfo location) {
            throw new AssertionError("Compatibility display must not open resource contents");
        }

        @Override
        public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
            throw new AssertionError("Compatibility display must not open resource contents");
        }
    };

    public static final class RecordingMinecraft extends Minecraft {
        private int submissions;

        private RecordingMinecraft() {
            super(null);
            throw new AssertionError("Fixture is allocated without starting Minecraft");
        }

        @Override
        public void setScreen(Screen screen) {
            this.screen = screen;
            submissions++;
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static RecordingMinecraft minecraft() throws Exception {
        var minecraft = (RecordingMinecraft) unsafe.allocateInstance(RecordingMinecraft.class);
        field(Minecraft.class, "instance").set(null, minecraft);
        return minecraft;
    }

    private static void world() throws Exception {
        RecordingMinecraft minecraft = minecraft();
        var previous = (CreateWorldScreen) unsafe.allocateInstance(CreateWorldScreen.class);
        var continued = new AtomicInteger();

        WorldOpenFlows.confirmWorldCreation(minecraft, previous, Lifecycle.experimental(), continued::incrementAndGet, false);
        check(continued.get() == 1, "Experimental world creation continues exactly once without confirmation");
        check(minecraft.submissions == 0, "Experimental creation submits no warning screen");

        WorldOpenFlows.confirmWorldCreation(minecraft, previous, Lifecycle.stable(), continued::incrementAndGet, false);
        check(continued.get() == 2 && minecraft.submissions == 0, "Stable creation retains its native immediate continuation");
        WorldOpenFlows.confirmWorldCreation(minecraft, previous, Lifecycle.experimental(), continued::incrementAndGet, true);
        check(continued.get() == 3 && minecraft.submissions == 0, "Already-confirmed experimental creation still runs once");

        Lifecycle deprecated = Lifecycle.deprecated(1);
        WorldOpenFlows.confirmWorldCreation(minecraft, previous, deprecated, continued::incrementAndGet, false);
        check(continued.get() == 3, "Deprecated world settings still require their separate confirmation");
        check(minecraft.screen instanceof ConfirmScreen && minecraft.submissions == 1,
                "Deprecated settings retain the native confirmation screen");
        check(minecraft.screen.getTitle().getContents() instanceof TranslatableContents title
                        && title.getKey().equals("selectWorld.warning.deprecated.title"),
                "The preserved screen is the deprecated-settings warning");
        BooleanConsumer callback = (BooleanConsumer) field(ConfirmScreen.class, "callback").get(minecraft.screen);
        callback.accept(false);
        check(minecraft.screen == previous && continued.get() == 3, "Rejecting deprecated settings returns to world creation");

        WorldOpenFlows.confirmWorldCreation(minecraft, previous, deprecated, continued::incrementAndGet, false);
        callback = (BooleanConsumer) field(ConfirmScreen.class, "callback").get(minecraft.screen);
        callback.accept(true);
        check(continued.get() == 4, "Accepting deprecated settings retains the native creation callback");

        int before = minecraft.submissions;
        var sentinel = new IllegalStateException("creation failure");
        try {
            WorldOpenFlows.confirmWorldCreation(minecraft, previous, Lifecycle.experimental(), () -> { throw sentinel; }, false);
            throw new AssertionError("Creation failure should propagate");
        } catch (IllegalStateException actual) {
            check(actual == sentinel && minecraft.submissions == before, "Warning suppression does not swallow creation failures");
        }
    }

    private static Pack pack(String id, PackCompatibility compatibility, String description) {
        return new Pack(new PackLocationInfo(id, Component.literal(id), PackSource.DEFAULT, Optional.empty()),
                NO_CONTENT, new Pack.Metadata(Component.literal(description), compatibility,
                        FeatureFlagSet.of(), List.of(), false),
                new PackSelectionConfig(false, Pack.Position.TOP, false));
    }

    private static PackSelectionModel model(PackRepository repository, AtomicInteger changed, AtomicInteger committed) {
        return new PackSelectionModel(changed::incrementAndGet,
                pack -> ResourceLocation.withDefaultNamespace("fixture/pack"), repository,
                ignored -> committed.incrementAndGet());
    }

    private static PackSelectionModel.Entry entry(PackSelectionModel model, String id, boolean selected) {
        return (selected ? model.getSelected() : model.getUnselected())
                .filter(candidate -> candidate.getId().equals(id)).findFirst().orElseThrow();
    }

    private static void packs() throws Exception {
        RecordingMinecraft minecraft = minecraft();
        Pack old = pack("old", PackCompatibility.TOO_OLD, "initial old metadata");
        Pack future = pack("future", PackCompatibility.TOO_NEW, "initial future metadata");
        Pack current = pack("current", PackCompatibility.COMPATIBLE, "current metadata");
        var content = new AtomicReference<>(List.of(old, future, current));
        PackRepository repository = new PackRepository(consumer -> content.get().forEach(consumer));
        repository.reload();
        field(Minecraft.class, "resourcePackRepository").set(minecraft, repository);
        var changed = new AtomicInteger();
        var committed = new AtomicInteger();
        PackSelectionModel model = model(repository, changed, committed);

        for (String id : List.of("old", "future", "current")) {
            check(entry(model, id, false).getCompatibility() == PackCompatibility.COMPATIBLE,
                    "Resource-pack UI suppresses format warnings for " + id);
        }
        check(old.getCompatibility() == PackCompatibility.TOO_OLD && future.getCompatibility() == PackCompatibility.TOO_NEW,
                "Resource-pack metadata retains its actual compatibility values");
        check(old.getDescription().getString().equals("initial old metadata"), "The pack description is unchanged");

        entry(model, "old", false).select();
        check(repository.getSelectedIds().contains("old") && changed.get() == 1,
                "Native selection still updates the repository and list callback");
        check(entry(model, "old", true).getCompatibility() == PackCompatibility.COMPATIBLE,
                "Selected resource packs also omit incompatibility warnings");
        model.commit();
        check(committed.get() == 1, "Native model commit still invokes its consumer");

        Pack refreshedOld = pack("old", PackCompatibility.TOO_OLD, "refreshed old metadata");
        Pack refreshedFuture = pack("future", PackCompatibility.TOO_NEW, "refreshed future metadata");
        content.set(List.of(refreshedOld, refreshedFuture, current));
        model.findNewPacks();
        check(repository.getPack("old") == refreshedOld && old != refreshedOld && old.equals(refreshedOld),
                "Refresh fixture replaces a pack with an equal distinct native instance");
        check(entry(model, "old", true).getDescription().getString().equals("initial old metadata"),
                "Native refresh retains the selected pack instance rather than replacing it");
        check(entry(model, "old", true).getCompatibility() == PackCompatibility.COMPATIBLE,
                "Selected resource-pack warnings stay suppressed after folder refresh");
        check(entry(model, "future", false).getCompatibility() == PackCompatibility.COMPATIBLE,
                "Refreshed unselected resource packs also omit warnings");
        check(refreshedOld.getCompatibility() == PackCompatibility.TOO_OLD
                        && refreshedFuture.getCompatibility() == PackCompatibility.TOO_NEW,
                "Refresh does not rewrite native pack metadata");

        Pack dataOld = pack("old", PackCompatibility.TOO_OLD, "old datapack metadata");
        Pack dataFuture = pack("future", PackCompatibility.TOO_NEW, "future datapack metadata");
        PackRepository datapacks = new PackRepository(consumer -> List.of(dataOld, dataFuture).forEach(consumer));
        datapacks.reload();
        PackSelectionModel dataModel = model(datapacks, new AtomicInteger(), new AtomicInteger());
        check(entry(dataModel, "old", false).getCompatibility() == PackCompatibility.TOO_OLD,
                "Datapack warnings survive even when an equal resource pack has the same ID");
        check(entry(dataModel, "future", false).getCompatibility() == PackCompatibility.TOO_NEW,
                "Future-format datapack warnings remain intact");
        entry(dataModel, "old", false).select();
        check(entry(dataModel, "old", true).getCompatibility() == PackCompatibility.TOO_OLD,
                "Selected datapack compatibility is unaffected");

        field(Minecraft.class, "instance").set(null, null);
        check(entry(model, "old", true).getCompatibility() == PackCompatibility.TOO_OLD,
                "Without a live client, the compatibility API keeps its native result");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !(args[0].equals("world") || args[0].equals("packs")))
            throw new IllegalArgumentException("Expected mode: world or packs");
        MinimalNativeBootstrap.initialize();
        field(FMLLoader.class, "dist").set(null, Dist.CLIENT);
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        unsafe = (sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null);
        if (args[0].equals("world")) world();
        else packs();
        System.out.println("PackWarningFixture " + args[0] + " PASS: " + checks + " native contracts");
    }
}
