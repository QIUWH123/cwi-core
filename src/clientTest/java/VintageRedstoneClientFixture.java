import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.createmod.ponder.api.element.InputElementBuilder;
import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.quiwh.cwi.mixin.RecipeOutputMixinPlugin;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Inspect actual transformed render branches and execute transformed Ponder callbacks. */
public final class VintageRedstoneClientFixture {
    static int checks;
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
    static ClassNode read(Path root, String name) throws Exception {
        var node = new ClassNode(); new ClassReader(Files.readAllBytes(root.resolve(name.replace('.', '/') + ".class"))).accept(node, 0); return node;
    }
    static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.contains(name)).findFirst().orElseThrow();
    }
    static List<AbstractInsnNode> code(MethodNode method) {
        return Arrays.stream(method.instructions.toArray()).filter(i -> i.getOpcode() >= 0).toList();
    }
    static void falseHandler(ClassNode node, String name) {
        var instructions = code(method(node, name));
        check(instructions.size() == 2 && instructions.get(0).getOpcode() == Opcodes.ICONST_0
                && instructions.get(1).getOpcode() == Opcodes.IRETURN, node.name + " forces obsolete flag false");
    }
    static void renderer(Path root, String family, String renderer, String module) throws Exception {
        var name = "com.negodya1.vintageimprovements.content.kinetics." + family + "." + renderer;
        var node = read(root, name); falseHandler(node, "cwi$normalModel");
        var render = node.methods.stream().filter(m -> m.name.equals("renderSafe") && m.desc.contains("/" + family + "/")).findFirst().orElseThrow();
        var instructions = render.instructions.toArray(); int guard = -1, partial = -1; JumpInsnNode branch = null;
        for (int i = 0; i < instructions.length; i++) {
            if (instructions[i] instanceof MethodInsnNode m && m.name.contains("cwi$normalModel")) guard = i;
            if (guard >= 0 && branch == null && instructions[i] instanceof JumpInsnNode j) branch = j;
            if (instructions[i] instanceof FieldInsnNode f && f.name.equals(module)) partial = i;
        }
        check(guard >= 0 && branch != null && branch.getOpcode() == Opcodes.IFEQ && partial > guard
                && render.instructions.indexOf(branch.label) > partial, renderer + " skips only the module partial");
        check(Arrays.stream(instructions).anyMatch(i -> i instanceof FieldInsnNode f && f.owner.endsWith("VintagePartialModels") && !f.name.contains("REDSTONE")), "ordinary machine partials preserved");
    }
    static Method callback(Class<?> type, String suffix) {
        var m = Arrays.stream(type.getDeclaredMethods()).filter(v -> v.getName().contains(suffix)).findFirst().orElseThrow();
        m.setAccessible(true); return m;
    }
    static void scene(String name) throws Exception {
        var type = Class.forName("com.negodya1.vintageimprovements.infrastructure.ponder.scenes." + name);
        var calls = new ArrayList<String>();
        TextElementBuilder text = (TextElementBuilder) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{TextElementBuilder.class}, (proxy, method, args) -> {
            if (method.getName().equals("sharedText")) calls.add(args[0].toString());
            return proxy;
        });
        Operation<TextElementBuilder> original = args -> { calls.add("numbered text consumed"); return text; };
        var result = callback(type, "cwi$explainBuiltInComparator").invoke(null, text, "obsolete installation", original);
        check(result == text && calls.equals(List.of("numbered text consumed", "cwi:built_in_comparator")), "scene text preserves native indices and uses new localization");
        var lang = (PonderLocalization) net.createmod.ponder.foundation.PonderIndex.getLangAccess();
        check(lang.shared.get(ResourceLocation.parse("cwi:built_in_comparator")).contains("built in"), "editing-mode text registered");
        Operation<InputElementBuilder> forbidden = args -> { throw new AssertionError("obsolete controls became visible"); };
        var controls = (InputElementBuilder) callback(type, "cwi$skipModuleInstallation").invoke(null, null, null, null, 30, forbidden);
        check(controls.rightClick().withItem(net.minecraft.world.item.ItemStack.EMPTY) == controls, "module hint creates no visible controls");
    }
    static void registrations() throws Exception {
        var module = net.neoforged.neoforge.registries.DeferredHolder.create(net.minecraft.core.registries.Registries.ITEM,
                ResourceLocation.parse("vintageimprovements:redstone_module"));
        var head = net.neoforged.neoforge.registries.DeferredHolder.create(net.minecraft.core.registries.Registries.ITEM,
                ResourceLocation.parse("vintageimprovements:concave_curving_head"));
        var type = Class.forName("com.negodya1.vintageimprovements.infrastructure.ponder.VintagePonderScene");
        var wrapper = callback(type, "cwi$skipRemovedModule"); int[] forwarded = {0};
        Operation<net.createmod.ponder.api.registration.StoryBoardEntry> original = args -> { forwarded[0]++; return null; };
        wrapper.invoke(null, null, module, "centrifuge/redstone", null, new ResourceLocation[0], original);
        check(forwarded[0] == 0, "removed module has no Ponder storyboard component");
        wrapper.invoke(null, null, head, "curving_press/processing", null, new ResourceLocation[0], original);
        check(forwarded[0] == 1, "normal component storyboards still register");
        var tagType = Class.forName("com.negodya1.vintageimprovements.infrastructure.ponder.VintagePonderTag");
        var tagWrapper = callback(tagType, "cwi$skipRemovedModule");
        var builder = (net.createmod.ponder.api.registration.MultiTagBuilder.Tag<?>) Proxy.newProxyInstance(type.getClassLoader(),
                new Class[]{net.createmod.ponder.api.registration.MultiTagBuilder.Tag.class}, (proxy, method, args) -> proxy);
        Operation<net.createmod.ponder.api.registration.MultiTagBuilder.Tag<?>> tagOriginal = args -> { forwarded[0]++; return builder; };
        check(tagWrapper.invoke(null, builder, module, tagOriginal) == builder && forwarded[0] == 1, "removed module has no Ponder tag icon");
        tagWrapper.invoke(null, builder, head, tagOriginal);
        check(forwarded[0] == 2, "normal Ponder tags still register");
    }
    static void optionalLoading() throws Exception {
        MinimalNativeBootstrap.initialize();
        var plugin = new RecipeOutputMixinPlugin();
        String target = "com.negodya1.vintageimprovements.content.kinetics.centrifuge.CentrifugeRenderer";
        check(!plugin.shouldApplyMixin(target, "net.quiwh.cwi.mixin.vintage.CentrifugeRedstoneRendererMixin"), "absent Vintage skips client integration");
        var field = LoadingModList.class.getDeclaredField("fileById"); field.setAccessible(true);
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        field.set(FMLLoader.getLoadingModList(), Map.of("vintageimprovements", unsafe.allocateInstance(ModFileInfo.class)));
        for (String mixin : List.of("CentrifugeRedstoneRendererMixin", "CurvingPressRedstoneRendererMixin", "VintageRedstoneScenesMixin", "VintageRedstoneSceneRegistrationMixin", "VintageRedstoneTagRegistrationMixin"))
            check(plugin.shouldApplyMixin(target, "net.quiwh.cwi.mixin.vintage." + mixin), "client integration works with Vintage and no JEI: " + mixin);
    }
    public static void main(String[] args) throws Exception {
        optionalLoading();
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        // No game window, OpenGL context or Vintage mod initialization is needed.
        var root = Path.of(args[0]);
        renderer(root, "centrifuge", "CentrifugeRenderer", "REDSTONE_MODULE_CENTRIFUGE");
        renderer(root, "curving_press", "CurvingPressRenderer", "REDSTONE_MODULE_CURVING_PRESS");
        for (String be : List.of("centrifuge.CentrifugeBlockEntity", "curving_press.CurvingPressBlockEntity")) {
            var node = read(root, "com.negodya1.vintageimprovements.content.kinetics." + be);
            falseHandler(node, "cwi$noInstalledModule");
            check(Arrays.stream(method(node, "addToGoggleTooltip").instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode m && m.name.contains("cwi$noInstalledModule")), "goggles use false installation flag");
        }
        scene("CentrifugeScenes"); scene("CurvingPressScenes");
        registrations();
        var keyMethod = PonderLocalization.class.getDeclaredMethod("langKeyForShared", ResourceLocation.class); keyMethod.setAccessible(true);
        var key = (String) keyMethod.invoke(null, ResourceLocation.parse("cwi:built_in_comparator"));
        for (String locale : List.of("en_us", "zh_cn")) {
            var json = com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/cwi/lang/" + locale + ".json"))).getAsJsonObject();
            check(json.has(key), "native shared translation key exists in " + locale);
        }
        System.out.println("PASS " + checks + " transformed renderer/tooltip/Ponder/optional-loading checks; no graphical client verification");
    }
}
