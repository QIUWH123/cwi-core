import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.texture.atlas.*;
import net.minecraft.client.resources.TextureAtlasHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Native atlas discovery, animation, GUI submissions and pack reloads; only GPU calls are captured. */
public final class AnimatedJeiFrameFixture {
    private static final String SELF = "AnimatedJeiFrameFixture";
    private static final ResourceLocation PERIODIC = ResourceLocation.parse("cwi:jei/per_tick_slot");
    private static final ResourceLocation CATALYST = ResourceLocation.parse("cwi:jei/catalyst_slot");
    private static final ResourceLocation GUI_ATLAS = ResourceLocation.withDefaultNamespace("textures/atlas/gui.png");
    private static final Path ROOT = Path.of("build/client-contracts/animated-frames");
    private static final List<SpriteContents> contents = new ArrayList<>();
    private static final List<Upload> uploads = new ArrayList<>();
    private static final List<Blit> blits = new ArrayList<>();
    private static final List<Integer> borders = new ArrayList<>();
    private static final List<String> fallbacks = new ArrayList<>();
    private static sun.misc.Unsafe unsafe;
    private static int checks;
    private record Upload(int x, int y, int sourceX, int sourceY, int pixel) {}
    private record Blit(ResourceLocation atlas, int left, int right, int top, int bottom, float u0, float u1, float v0, float v1) {}

    private static void check(boolean pass, String reason) {
        checks++;
        if (!pass) throw new AssertionError(reason);
    }
    public static void upload(int x, int y, int sourceX, int sourceY, NativeImage[] images) {
        uploads.add(new Upload(x, y, sourceX, sourceY, images[0].getPixelRGBA(sourceX, sourceY)));
    }
    public static void blit(ResourceLocation atlas, int left, int right, int top, int bottom, int z, float u0, float u1, float v0, float v1) {
        blits.add(new Blit(atlas, left, right, top, bottom, u0, u1, v0, v1));
    }
    public static void fill(int x1, int y1, int x2, int y2, int color) { borders.add(color); }
    public static void fallback(AllGuiTextures texture, GuiGraphics graphics, int x, int y) { fallbacks.add(texture.name() + ":" + x + "," + y); }

    private static void body(MethodNode method, AbstractInsnNode... instructions) {
        method.instructions.clear(); method.tryCatchBlocks.clear(); method.localVariables = null;
        for (var instruction : instructions) method.instructions.add(instruction);
    }
    private static void capture(Path output) throws Exception {
        for (String name : List.of("net/minecraft/client/renderer/texture/SpriteContents",
                "net/minecraft/client/gui/GuiGraphics", "com/simibubi/create/foundation/gui/AllGuiTextures")) {
            var node = new ClassNode();
            try (var input = AnimatedJeiFrameFixture.class.getResourceAsStream("/" + name + ".class")) { new ClassReader(input).accept(node, 0); }
            int replaced = 0;
            for (var method : node.methods) {
                if (name.endsWith("SpriteContents") && method.name.equals("upload") && method.desc.equals("(IIII[Lcom/mojang/blaze3d/platform/NativeImage;)V")) {
                    body(method, new VarInsnNode(Opcodes.ILOAD, 1), new VarInsnNode(Opcodes.ILOAD, 2),
                            new VarInsnNode(Opcodes.ILOAD, 3), new VarInsnNode(Opcodes.ILOAD, 4), new VarInsnNode(Opcodes.ALOAD, 5),
                            new MethodInsnNode(Opcodes.INVOKESTATIC, SELF, "upload", method.desc, false), new InsnNode(Opcodes.RETURN));
                    replaced++;
                } else if (name.endsWith("GuiGraphics") && method.name.equals("innerBlit")
                        && method.desc.equals("(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFF)V")) {
                    body(method, new VarInsnNode(Opcodes.ALOAD, 1), new VarInsnNode(Opcodes.ILOAD, 2), new VarInsnNode(Opcodes.ILOAD, 3),
                            new VarInsnNode(Opcodes.ILOAD, 4), new VarInsnNode(Opcodes.ILOAD, 5), new VarInsnNode(Opcodes.ILOAD, 6),
                            new VarInsnNode(Opcodes.FLOAD, 7), new VarInsnNode(Opcodes.FLOAD, 8), new VarInsnNode(Opcodes.FLOAD, 9), new VarInsnNode(Opcodes.FLOAD, 10),
                            new MethodInsnNode(Opcodes.INVOKESTATIC, SELF, "blit", method.desc, false), new InsnNode(Opcodes.RETURN));
                    replaced++;
                } else if (name.endsWith("GuiGraphics") && method.name.equals("fill") && method.desc.equals("(IIIII)V")) {
                    body(method, new VarInsnNode(Opcodes.ILOAD, 1), new VarInsnNode(Opcodes.ILOAD, 2), new VarInsnNode(Opcodes.ILOAD, 3),
                            new VarInsnNode(Opcodes.ILOAD, 4), new VarInsnNode(Opcodes.ILOAD, 5),
                            new MethodInsnNode(Opcodes.INVOKESTATIC, SELF, "fill", method.desc, false), new InsnNode(Opcodes.RETURN));
                    replaced++;
                } else if (name.endsWith("AllGuiTextures") && method.name.equals("render") && method.desc.equals("(Lnet/minecraft/client/gui/GuiGraphics;II)V")) {
                    body(method, new VarInsnNode(Opcodes.ALOAD, 0), new VarInsnNode(Opcodes.ALOAD, 1), new VarInsnNode(Opcodes.ILOAD, 2), new VarInsnNode(Opcodes.ILOAD, 3),
                            new MethodInsnNode(Opcodes.INVOKESTATIC, SELF, "fallback", "(L" + name + ";Lnet/minecraft/client/gui/GuiGraphics;II)V", false), new InsnNode(Opcodes.RETURN));
                    replaced++;
                }
            }
            check(replaced == (name.endsWith("GuiGraphics") ? 2 : 1), "exact native capture methods: " + name);
            var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer);
            var target = output.resolve(name + ".class"); Files.createDirectories(target.getParent()); Files.write(target, writer.toByteArray());
        }
    }

    private static void image(Path path, int frames) throws Exception {
        Files.createDirectories(path.getParent());
        var image = new BufferedImage(18, 18 * frames, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < 18; x++) image.setRGB(x, y, 0xFF000000 | (y / 18) * 0x0E0E0E);
        ImageIO.write(image, "png", path.toFile());
    }
    private static PackResources pack(Path path) {
        return new PathPackResources(new PackLocationInfo(path.toString(), Component.literal("fixture"), PackSource.DEFAULT, Optional.empty()), path);
    }
    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, SpriteContents> load(Path... paths) throws Exception {
        var sections = GuiSpriteManager.class.getDeclaredField("METADATA_SECTIONS"); sections.setAccessible(true);
        var loader = SpriteResourceLoader.create((Set<MetadataSectionSerializer<?>>) sections.get(null));
        var result = new HashMap<ResourceLocation, SpriteContents>();
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, Arrays.stream(paths).map(AnimatedJeiFrameFixture::pack).toList())) {
            for (var source : SpriteSourceList.load(resources, ResourceLocation.withDefaultNamespace("gui")).list(resources)) {
                var sprite = source.apply(loader);
                check(sprite != null, "GUI sprite is valid"); result.put(sprite.name(), sprite); contents.add(sprite);
            }
        }
        return result;
    }
    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void install(TextureAtlas atlas, Map<ResourceLocation, SpriteContents> sprites) throws Exception {
        var constructor = TextureAtlasSprite.class.getDeclaredConstructor(ResourceLocation.class, SpriteContents.class, int.class, int.class, int.class, int.class);
        constructor.setAccessible(true);
        var regions = new HashMap<ResourceLocation, TextureAtlasSprite>(); int x = 0;
        for (var entry : new TreeMap<>(sprites).entrySet()) {
            regions.put(entry.getKey(), constructor.newInstance(GUI_ATLAS, entry.getValue(), 256, 256, x, 0)); x += 32;
        }
        set(atlas, TextureAtlas.class, "texturesByName", regions);
        set(atlas, TextureAtlas.class, "missingSprite", regions.get(MissingTextureAtlasSprite.getLocation()));
    }
    private static IDrawable drawable(String name) throws Exception {
        var field = Class.forName("net.quiwh.cwi.compat.jei.MachineJeiSlots").getDeclaredField(name); field.setAccessible(true);
        return (IDrawable)field.get(null);
    }
    private static void draw(IDrawable frame, GuiGraphics graphics) {
        blits.clear(); borders.clear(); fallbacks.clear(); frame.draw(graphics, 23, 41);
    }
    private static void spriteDraw(IDrawable frame, GuiGraphics graphics, TextureAtlasSprite sprite) {
        draw(frame, graphics);
        check(blits.size() == 1 && borders.isEmpty() && fallbacks.isEmpty(), "present custom frame uses one sprite draw");
        var b = blits.getFirst();
        check(b.atlas.equals(GUI_ATLAS) && b.left == 23 && b.right == 41 && b.top == 41 && b.bottom == 59, "GUI atlas frame is drawn at 18x18 at the requested position");
        check(b.u0 == sprite.getU0() && b.u1 == sprite.getU1() && b.v0 == sprite.getV0() && b.v1 == sprite.getV1(), "UVs select one atlas frame, not the complete strip");
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("capture")) { capture(Path.of(args[1])); return; }
        MinimalNativeBootstrap.initialize(); SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); RenderSystem.initRenderThread();
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true); unsafe = (sun.misc.Unsafe)field.get(null);
        var base = ROOT.resolve("base"); var external = ROOT.resolve("kubejs"); var override = ROOT.resolve("override");
        var definition = base.resolve("assets/minecraft/atlases/gui.json"); Files.createDirectories(definition.getParent());
        Files.writeString(definition, "{\"sources\":[{\"type\":\"minecraft:directory\",\"source\":\"gui/sprites\",\"prefix\":\"\"}]}");
        image(base.resolve("assets/fixture/textures/gui/sprites/retained.png"), 1);
        var periodic = external.resolve("assets/cwi/textures/gui/jei/per_tick_slot.png");
        var catalyst = external.resolve("assets/cwi/textures/gui/jei/catalyst_slot.png");
        image(periodic, 17); image(catalyst, 1); Files.writeString(Path.of(periodic + ".mcmeta"), "{\"animation\":{\"frametime\":1}}");
        var core = Path.of("src/main/resources");
        var loaded = load(base, core, external);
        check(loaded.containsKey(PERIODIC) && loaded.containsKey(CATALYST), "Core GUI atlas discovers both existing KubeJS frame paths");
        check(loaded.containsKey(ResourceLocation.parse("fixture:retained")), "atlas definitions merge with existing GUI sprites");
        var strip = loaded.get(PERIODIC); var plain = loaded.get(CATALYST);
        check(strip.width() == 18 && strip.height() == 18 && strip.getUniqueFrames().count() == 17, "18x306 strip becomes seventeen 18x18 animation frames");
        check(plain.width() == 18 && plain.height() == 18 && plain.createTicker() == null, "static catalyst texture stays static");
        uploads.clear(); strip.uploadFirstFrame(5, 7);
        try (var ticker = strip.createTicker()) {
            check(ticker != null && uploads.getLast().sourceY == 0, "native animation starts at frame zero");
            for (int tick = 1; tick <= 34; tick++) {
                ticker.tickAndUpload(5, 7);
                var upload = uploads.getLast();
                check(uploads.size() == tick + 1 && upload.x == 5 && upload.y == 7 && upload.sourceX == 0 && upload.sourceY == tick % 17 * 18,
                        "native frametime1 advances and loops without squeezing: tick " + tick);
            }
        }
        var atlas = (TextureAtlas)unsafe.allocateInstance(TextureAtlas.class); install(atlas, loaded);
        var manager = (GuiSpriteManager)unsafe.allocateInstance(GuiSpriteManager.class);
        set(manager, TextureAtlasHolder.class, "textureAtlas", atlas);
        var minecraft = (Minecraft)unsafe.allocateInstance(Minecraft.class);
        set(minecraft, Minecraft.class, "guiSprites", manager); set(null, Minecraft.class, "instance", minecraft);
        var graphics = new GuiGraphics(minecraft, null);
        var periodicFrame = drawable("PERIODIC_FRAME"); var catalystFrame = drawable("CATALYST_FRAME");
        check(periodicFrame.getWidth() == 18 && periodicFrame.getHeight() == 18 && catalystFrame.getWidth() == 18 && catalystFrame.getHeight() == 18, "JEI layout dimensions are unchanged");
        spriteDraw(periodicFrame, graphics, manager.getSprite(PERIODIC)); spriteDraw(catalystFrame, graphics, manager.getSprite(CATALYST));

        // A higher pack changes the same sprite's frame count, explicit order and per-frame timing.
        var replacement = override.resolve("assets/cwi/textures/gui/jei/per_tick_slot.png"); image(replacement, 3);
        Files.writeString(Path.of(replacement + ".mcmeta"), "{\"animation\":{\"frametime\":2,\"frames\":[2,{\"index\":0,\"time\":3},1]}}");
        var reloaded = load(base, core, external, override); var ordered = reloaded.get(PERIODIC); install(atlas, reloaded);
        check(ordered != strip && ordered.getUniqueFrames().count() == 3, "resource pack reload replaces old animation metadata and pixels");
        uploads.clear(); ordered.uploadFirstFrame(0, 0);
        try (var ticker = ordered.createTicker()) {
            check(uploads.getLast().sourceY == 36, "explicit frame order controls the first frame");
            for (int y : new int[]{36, 0, 0, 0, 18, 18, 36}) { ticker.tickAndUpload(0, 0); check(uploads.getLast().sourceY == y, "explicit frame durations and order are honored"); }
        }
        spriteDraw(periodicFrame, graphics, manager.getSprite(PERIODIC));
        Files.writeString(Path.of(replacement + ".mcmeta"), "{\"animation\":{\"frametime\":2,\"interpolate\":true}}");
        var interpolated = load(base, core, external, override).get(PERIODIC);
        uploads.clear(); interpolated.uploadFirstFrame(0, 0);
        try (var ticker = interpolated.createTicker()) {
            ticker.tickAndUpload(0, 0);
            check(uploads.size() == 2 && uploads.getLast().pixel != uploads.getFirst().pixel, "native interpolation uploads a blended intermediate frame");
        }
        install(atlas, load(base, core)); // Both optional external textures have been removed on reload.
        for (var entry : List.of(Map.entry(periodicFrame, 0xFF60BED5), Map.entry(catalystFrame, 0xFFDCB64A))) {
            draw(entry.getKey(), graphics);
            check(blits.isEmpty() && fallbacks.equals(List.of("JEI_SLOT:23,41")) && borders.equals(Collections.nCopies(4, entry.getValue())), "removed/absent frame uses its distinct fallback after reload");
        }
        install(atlas, loaded); spriteDraw(periodicFrame, graphics, manager.getSprite(PERIODIC));
        for (var sprite : contents) sprite.close();
        System.out.println("AnimatedJeiFrameFixture PASS: " + checks + " native animation/GUI/reload checks (no window or GPU)");
    }
}
