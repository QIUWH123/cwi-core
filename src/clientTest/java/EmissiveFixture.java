import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.model.IQuadTransformer;

/** Exact native sprite loading and vertex output, without a window or graphics context. */
public final class EmissiveFixture {
    static int checks;
    static java.util.function.Supplier<Capture> captureFactory = Capture::new;
    static final List<String> failures = new ArrayList<>();
    static final List<SpriteContents> contents = new ArrayList<>();
    static final MetadataSectionSerializer<JsonObject> CWI = new MetadataSectionSerializer<>() {
        public String getMetadataSectionName() { return "cwi"; }
        public JsonObject fromJson(JsonObject json) { return json; }
    };
    static sun.misc.Unsafe unsafe;
    public static void main(String[] args) throws Exception {
        MinimalNativeBootstrap.initialize(); SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); unsafe = (sun.misc.Unsafe)f.get(null);
        var glow = load("{\"cwi\":{\"emissive\":true}}", 16);
        var plain = load("{}", 16);
        check(hasFlag(glow), "native atlas loader retains CWI metadata");
        var animated = load("{\"animation\":{\"frametime\":4},\"cwi\":{\"emissive\":true}}", 32);
        check(animated.height() == 16 && animated.getUniqueFrames().count() == 2, "animation and CWI metadata coexist");
        var gs = sprite(glow); var ps = sprite(plain);
        int[] shared = vertices(); int[] saved = shared.clone();
        var gq = new BakedQuad(shared, -1, Direction.DOWN, gs, true, true);
        var pq = new BakedQuad(shared, -1, Direction.DOWN, ps, true, true);
        check(!gq.isShade() && !gq.hasAmbientOcclusion(), "emissive quads disable directional shading and AO");
        check(pq.isShade() && pq.hasAmbientOcclusion(), "ordinary quads keep shading and AO");
        check(Arrays.equals(shared, saved) && pq.getVertices() == shared, "shared caller vertices and ordinary quads are unchanged");
        check(lights(gq, LightTexture.FULL_BRIGHT), "every emissive vertex has full bright baked light");
        check(lights(pq, 0), "ordinary baked light is unchanged");
        for (String json : List.of("{\"cwi\":{\"emissive\":false}}", "{\"cwi\":{\"emissive\":\"true\"}}", "{\"cwi\":{\"emissive\":1}}", "{\"cwi\":{}}", "{\"cwi\":false}")) {
            var q = new BakedQuad(vertices(), -1, Direction.DOWN, sprite(load(json, 16)), true);
            check(q.isShade() && q.hasAmbientOcclusion() && lights(q, 0), "false/missing/nonboolean flags retain ordinary rendering: " + json);
        }
        var reloaded = new BakedQuad(vertices(), -1, Direction.DOWN, sprite(load("{}", 16)), true);
        check(reloaded.isShade() && !gq.isShade(), "fresh sprite metadata replaces old state without changing the old sprite");
        resourceOverride();
        blockVertices(gq, pq);
        lighter(gq,pq);
        itemVertices(gq, pq);
        itemTintFoilAndShadow(gq, pq);
        reusedModelQuadList(gq,pq);
        staticModelCache(gq,pq);
        for (var c : contents) c.close();
        if (!failures.isEmpty()) throw new AssertionError(failures.size() + " of " + checks + " failed: " + failures);
        System.out.println("EmissiveFixture PASS: " + checks + " native checks");
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    static boolean hasFlag(SpriteContents sprite) throws Exception {
        try {
            var serializer = (MetadataSectionSerializer)Class.forName("net.quiwh.cwi.client.emissive.EmissiveMetadata").getField("SERIALIZER").get(null);
            return sprite.metadata().getSection(serializer).isPresent();
        } catch (ClassNotFoundException absent) { return sprite.metadata().getSection(CWI).isPresent(); }
    }
    static SpriteContents load(String json, int h) throws Exception {
        var output = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(16, h, BufferedImage.TYPE_INT_ARGB), "png", output);
        var pack = new PathPackResources(new PackLocationInfo("fixture", net.minecraft.network.chat.Component.literal("fixture"), PackSource.DEFAULT, Optional.empty()), java.nio.file.Path.of("."));
        var metadata = ResourceMetadata.fromJsonStream(new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var resource = new Resource(pack, () -> new ByteArrayInputStream(output.toByteArray()), () -> metadata);
        var sections = List.<MetadataSectionSerializer<?>>of(AnimationMetadataSection.SERIALIZER);
        var c = SpriteResourceLoader.create(sections).loadSprite(ResourceLocation.parse("fixture:shared"), resource);
        check(c != null, "valid PNG remains loadable: " + json);
        check(sections.size() == 1, "loader does not mutate caller metadata collection");
        contents.add(c); return c;
    }
    static TextureAtlasSprite sprite(SpriteContents c) throws Exception {
        var ctor = TextureAtlasSprite.class.getDeclaredConstructor(ResourceLocation.class, SpriteContents.class, int.class, int.class, int.class, int.class);
        ctor.setAccessible(true); return ctor.newInstance(TextureAtlas.LOCATION_BLOCKS, c, 16, 16, 0, 0);
    }
    static int[] vertices() {
        var v = new int[IQuadTransformer.STRIDE * 4];
        for (int i = 0; i < 4; i++) {
            v[i * IQuadTransformer.STRIDE] = Float.floatToRawIntBits(i == 1 || i == 2 ? 1 : 0);
            v[i * IQuadTransformer.STRIDE + 2] = Float.floatToRawIntBits(i >= 2 ? 1 : 0);
            v[i * IQuadTransformer.STRIDE + IQuadTransformer.COLOR] = 0xFF80C0FF;
        }
        return v;
    }
    static boolean lights(BakedQuad q, int expected) { for (int i = 0; i < 4; i++) if (q.getVertices()[i * IQuadTransformer.STRIDE + IQuadTransformer.UV2] != expected) return false; return true; }
    static void blockVertices(BakedQuad glow, BakedQuad plain) throws Exception {
        var renderer = new ModelBlockRenderer(new BlockColors());
        var method = Arrays.stream(ModelBlockRenderer.class.getDeclaredMethods()).filter(m -> m.getName().equals("putQuadData")).findFirst().orElseThrow(); method.setAccessible(true);
        var gc = new Capture(); var pc = new Capture();
        for (var pair : List.of(Map.entry(glow, gc), Map.entry(plain, pc))) method.invoke(renderer, null, Blocks.STONE.defaultBlockState(), BlockPos.ZERO, pair.getValue(), new PoseStack().last(), pair.getKey(), .1f,.2f,.3f,.4f,0,0,0,0,0);
        check(gc.light.equals(List.of(LightTexture.FULL_BRIGHT,LightTexture.FULL_BRIGHT,LightTexture.FULL_BRIGHT,LightTexture.FULL_BRIGHT)), "world block vertex output bypasses dark lightmaps");
        check(gc.red.equals(List.of(255,255,255,255)) && gc.green.equals(List.of(192,192,192,192)), "world block bypasses all AO and directional brightness while retaining vertex RGB");
        check(pc.red.getFirst() < 100 && pc.light.equals(List.of(0,0,0,0)), "ordinary block remains shaded and dark");
    }
    static void lighter(BakedQuad glow,BakedQuad plain) {
        var lighter=new net.neoforged.neoforge.client.model.lighting.FlatQuadLighter(new BlockColors());
        lighter.computeLightingForQuad(glow);
        check(Arrays.equals(lighter.getComputedBrightness(),new float[]{1,1,1,1}) && Arrays.equals(lighter.getComputedLightmap(),new int[]{LightTexture.FULL_BRIGHT,LightTexture.FULL_BRIGHT,LightTexture.FULL_BRIGHT,LightTexture.FULL_BRIGHT}),"native NeoForge lighter handles marked quads without world shading");
    }
    static void resourceOverride() throws Exception {
        var root=java.nio.file.Path.of("build/emissive/native/resource-packs");
        java.nio.file.Files.createDirectories(root);
        var image=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB),"png",image);
        var id=ResourceLocation.parse("fixture:textures/block/reload.png");
        var low=pack(root.resolve("low"),image.toByteArray(),"{\"cwi\":{\"emissive\":true}}");
        var high=pack(root.resolve("high"),image.toByteArray(),"{\"cwi\":{\"emissive\":false}}");
        try(var manager=new MultiPackResourceManager(PackType.CLIENT_RESOURCES,List.of(low,high))) {
            var sprite=SpriteResourceLoader.create(List.of(AnimationMetadataSection.SERIALIZER)).loadSprite(ResourceLocation.parse("fixture:block/reload"),manager.getResource(id).orElseThrow());
            contents.add(sprite);
            check(new BakedQuad(vertices(),-1,Direction.DOWN,sprite(sprite),true).isShade(),"higher resource pack can disable lower emissive metadata");
        }
        var highAgain=pack(root.resolve("high"),image.toByteArray(),"{\"cwi\":{\"emissive\":true}}");
        try(var manager=new MultiPackResourceManager(PackType.CLIENT_RESOURCES,List.of(highAgain))) {
            var sprite=SpriteResourceLoader.create(List.of(AnimationMetadataSection.SERIALIZER)).loadSprite(ResourceLocation.parse("fixture:block/reload"),manager.getResource(id).orElseThrow());
            contents.add(sprite);
            check(!new BakedQuad(vertices(),-1,Direction.DOWN,sprite(sprite),true).isShade(),"new resource manager reload applies edited metadata for the same texture ID");
        }
    }
    static PackResources pack(java.nio.file.Path root,byte[] image,String metadata) throws Exception {
        var dir=root.resolve("assets/fixture/textures/block");java.nio.file.Files.createDirectories(dir);
        java.nio.file.Files.write(dir.resolve("reload.png"),image);java.nio.file.Files.writeString(dir.resolve("reload.png.mcmeta"),metadata);
        return new PathPackResources(new PackLocationInfo(root.getFileName().toString(),net.minecraft.network.chat.Component.literal("fixture"),PackSource.DEFAULT,Optional.empty()),root);
    }
    static void itemVertices(BakedQuad glow, BakedQuad plain) throws Exception {
        var renderer = (ItemRenderer)unsafe.allocateInstance(ItemRenderer.class);
        try { Class.forName("net.terrunic.shadowdrop.ShadowDropConfig").getField("CLIENT").getType().getField("modEnabled").setBoolean(Class.forName("net.terrunic.shadowdrop.ShadowDropConfig").getField("CLIENT").get(null), false); } catch (ClassNotFoundException ignored) {}
        var field = ItemRenderer.class.getDeclaredField("itemColors"); field.setAccessible(true); field.set(renderer, new ItemColors());
        for (var rf : ItemRenderer.class.getDeclaredFields()) if (rf.getType() == net.minecraft.util.RandomSource.class) { rf.setAccessible(true); rf.set(renderer,net.minecraft.util.RandomSource.create()); }
        var buffers = new LinkedHashMap<RenderType, Capture>();
        MultiBufferSource source = type -> buffers.computeIfAbsent(type, ignored -> captureFactory.get());
        int[] queries = {0};
        BakedModel model = (BakedModel)Proxy.newProxyInstance(EmissiveFixture.class.getClassLoader(),new Class[]{BakedModel.class},(p,m,a) -> switch(m.getName()) {
            case "applyTransform" -> p;
            case "isCustomRenderer" -> false;
            case "isVanillaAdapter" -> true;
            case "getRenderPasses" -> List.of(p);
            case "getRenderTypes" -> List.of(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
            case "getQuads" -> { queries[0]++; yield a[1] == null ? List.of(plain,glow) : List.of(); }
            default -> throw new AssertionError(m.getName());
        });
        for (var display : List.of(ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
            buffers.clear(); queries[0]=0;
            renderer.render(new ItemStack(Items.IRON_INGOT),display,false,new PoseStack(),source,0,0,model);
            check(buffers.size() == 2, "mixed item has normal and unlit buffers: " + display);
            var normal = buffers.get(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
            check(normal != null && normal.light.equals(List.of(0,0,0,0)), "normal item part retains original render type/light: " + display);
            var unlit = buffers.entrySet().stream().filter(e -> e.getKey().toString().contains("unlit")).map(Map.Entry::getValue).findFirst();
            check(unlit.isPresent() && unlit.get().light.equals(List.of(LightTexture.FULL_BRIGHT,LightTexture.FULL_BRIGHT,LightTexture.FULL_BRIGHT,LightTexture.FULL_BRIGHT)), "glowing item part selects native unlit shader and fullbright: " + display);
            check(queries[0] == 7, "item model quad queries remain once per direction with seed 42: " + display);
        }
    }
    static void itemTintFoilAndShadow(BakedQuad glow, BakedQuad plain) throws Exception {
        var renderer = (ItemRenderer)unsafe.allocateInstance(ItemRenderer.class);
        var colors = new ItemColors(); colors.register((stack,index) -> 0x804080C0, Items.IRON_INGOT);
        var field = ItemRenderer.class.getDeclaredField("itemColors");field.setAccessible(true);field.set(renderer,colors);
        for(var rf:ItemRenderer.class.getDeclaredFields()) if(rf.getType()==net.minecraft.util.RandomSource.class){rf.setAccessible(true);rf.set(renderer,net.minecraft.util.RandomSource.create());}
        var tintedGlow = new BakedQuad(glow.getVertices(),0,Direction.DOWN,glow.getSprite(),true);
        var tintedPlain = new BakedQuad(plain.getVertices(),0,Direction.DOWN,plain.getSprite(),true);
        BakedModel model = (BakedModel)Proxy.newProxyInstance(EmissiveFixture.class.getClassLoader(),new Class[]{BakedModel.class},(p,m,a) -> switch(m.getName()) {
            case "applyTransform" -> p;
            case "isCustomRenderer" -> false;
            case "isVanillaAdapter" -> true;
            case "getRenderPasses" -> List.of(p);
            case "getRenderTypes" -> List.of(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
            case "getQuads" -> a[1]==null?List.of(tintedPlain,tintedGlow):List.of();
            default -> throw new AssertionError(m.getName());
        });
        var buffers = new LinkedHashMap<RenderType,Capture>();
        MultiBufferSource source = type -> buffers.computeIfAbsent(type,ignored -> captureFactory.get());
        var stack = new ItemStack(Items.IRON_INGOT);stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);
        renderer.render(stack,ItemDisplayContext.GUI,false,new PoseStack(),source,0,0,model);
        var glowOutput = buffers.entrySet().stream().filter(e->e.getKey().toString().contains("unlit")).findFirst().orElseThrow().getValue();
        check(glowOutput.red.equals(List.of(64,64,64,64)) && glowOutput.green.equals(List.of(96,96,96,96)) && glowOutput.alpha.equals(List.of(128,128,128,128)), "emissive items retain native tint, vertex RGB and alpha");
        var foil = buffers.entrySet().stream().filter(e->e.getKey().toString().contains("glint")).mapToInt(e->e.getValue().light.size()).sum();
        check(foil==8,"normal and emissive item faces each keep a single foil layer");
        try {
            var accessor = Class.forName("net.terrunic.shadowdrop.mixin.accessor.RenderStateShardAccessor");
            if(accessor.isInstance(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS))) {
                buffers.clear();
                var shadow = (MultiBufferSource)Class.forName("net.terrunic.shadowdrop.render.ShadowBufferSource").getConstructor(MultiBufferSource.class,float.class,float.class,float.class,float.class).newInstance(source,.1f,.2f,.3f,.5f);
                renderer.render(stack,ItemDisplayContext.GUI,false,new PoseStack(),shadow,0,0,model);
                check(buffers.size()==1 && buffers.values().iterator().next().light.size()==8,"actual Shadow Drop buffer receives both item parts without extra glint");
                var captured=buffers.values().iterator().next();
                check(captured.red.stream().allMatch(v->v==25) && captured.alpha.stream().allMatch(v->v==63),"emissive item faces retain actual Shadow Drop RGB and alpha");
            }
        } catch(ClassNotFoundException absent) {}
    }
    static void reusedModelQuadList(BakedQuad glow,BakedQuad plain) {
        var reused = new ArrayList<BakedQuad>();
        boolean[] changed = {false};
        BakedModel model = (BakedModel)Proxy.newProxyInstance(EmissiveFixture.class.getClassLoader(),new Class[]{BakedModel.class},(p,m,a) -> {
            if(m.getName().equals("getQuads")) {reused.clear();if(a[1]==Direction.DOWN)reused.add(plain);if(a[1]==null)reused.add(changed[0] ? plain : glow);return reused;}
            throw new AssertionError(m.getName());
        });
        var parts=net.quiwh.cwi.client.emissive.EmissiveItemRendering.split(model);
        check(parts.normal().getQuads(null,Direction.DOWN,net.minecraft.util.RandomSource.create()).equals(List.of(plain)),"dynamic models can reuse scratch quad lists without changing earlier faces");
        check(parts.emissive().getQuads(null,null,net.minecraft.util.RandomSource.create()).equals(List.of(glow)),"scratch-list model emissive face remains separate");
        changed[0] = true;
        check(net.quiwh.cwi.client.emissive.EmissiveItemRendering.split(model).emissive() == null,
                "dynamic models immediately reflect changed quad lists without caching");
    }
    static void staticModelCache(BakedQuad glow, BakedQuad plain) {
        var faces = new EnumMap<Direction,List<BakedQuad>>(Direction.class);
        for (var side : Direction.values()) faces.put(side,List.of());
        var model = new net.minecraft.client.resources.model.SimpleBakedModel(List.of(plain,glow),faces,true,true,false,
                plain.getSprite(),net.minecraft.client.renderer.block.model.ItemTransforms.NO_TRANSFORMS,
                net.minecraft.client.renderer.block.model.ItemOverrides.EMPTY);
        var first = net.quiwh.cwi.client.emissive.EmissiveItemRendering.split(model);
        check(first == net.quiwh.cwi.client.emissive.EmissiveItemRendering.split(model), "static model reuses the split without repeated allocations");
        net.quiwh.cwi.client.emissive.EmissiveItemRendering.clearCache();
        check(first != net.quiwh.cwi.client.emissive.EmissiveItemRendering.split(model), "resource reload discards cached models and sprite references");
    }
    static void check(boolean value,String message) { checks++; if (!value) { failures.add(message); System.out.println("FAIL: " + message); } }
    static class Capture implements VertexConsumer {
        final List<Integer> light = new ArrayList<>(),red = new ArrayList<>(),green = new ArrayList<>(),alpha = new ArrayList<>();
        public VertexConsumer addVertex(float x,float y,float z) { return this; }
        public VertexConsumer setColor(int r,int g,int b,int a) { red.add(r);green.add(g);alpha.add(a); return this; }
        public VertexConsumer setUv(float u,float v) { return this; }
        public VertexConsumer setUv1(int u,int v) { return this; }
        public VertexConsumer setUv2(int u,int v) { light.add(u | v << 16); return this; }
        public VertexConsumer setNormal(float x,float y,float z) { return this; }
    }
}
