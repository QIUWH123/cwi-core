import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.compat.jei.category.*;
import com.simibubi.create.content.processing.recipe.*;
import com.simibubi.create.content.processing.sequenced.*;
import com.simibubi.create.infrastructure.config.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import mezz.jei.api.gui.builder.*;
import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.GameData;
import net.quiwh.cwi.compat.AssemblyOutputVisibility;
import net.quiwh.cwi.compat.jei.*;
import net.quiwh.cwi.mixin.jei.ProcessingJeiMixinPlugin;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Real transformed serializers/categories. Only keyboard/GPU submissions are intercepted. */
public final class ProcessingJeiFixture {
    static int checks;
    static sun.misc.Unsafe unsafe;
    public static boolean shift;
    public static final com.mojang.blaze3d.vertex.PoseStack pose = new com.mojang.blaze3d.vertex.PoseStack();
    public static final List<String> rendered = new ArrayList<>();
    static SequencedAssemblyRecipeSerializer serializer;
    static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
    static void put(Object object, Class<?> owner, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    static Object allocate(String name) throws Exception { return unsafe.allocateInstance(Class.forName(name)); }
    public static void texture(com.simibubi.create.foundation.gui.AllGuiTextures texture) { rendered.add(texture.name()); }
    static void capture(Path output) throws Exception {
        for (String name : List.of("net/minecraft/client/gui/screens/Screen", "net/minecraft/client/gui/GuiGraphics",
                "net/minecraft/client/gui/Font", "com/simibubi/create/foundation/gui/AllGuiTextures")) {
            var node = new ClassNode();
            try (var input = ProcessingJeiFixture.class.getResourceAsStream("/" + name + ".class")) { new ClassReader(input).accept(node, 0); }
            for (var method : node.methods) {
                var instructions = new InsnList();
                if (name.endsWith("/Screen") && method.name.equals("hasShiftDown")) {
                    instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "ProcessingJeiFixture", "shift", "Z")); instructions.add(new InsnNode(Opcodes.IRETURN));
                } else if (name.endsWith("/GuiGraphics") && method.name.equals("pose")) {
                    instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "ProcessingJeiFixture", "pose", "Lcom/mojang/blaze3d/vertex/PoseStack;")); instructions.add(new InsnNode(Opcodes.ARETURN));
                } else if ((name.endsWith("/Font") && method.name.equals("width")) || (name.endsWith("/GuiGraphics") && method.name.equals("drawString"))) {
                    instructions.add(new InsnNode(Opcodes.ICONST_0)); instructions.add(new InsnNode(Opcodes.IRETURN));
                } else if (name.endsWith("/AllGuiTextures") && method.name.equals("render") && method.desc.equals("(Lnet/minecraft/client/gui/GuiGraphics;II)V")) {
                    instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "ProcessingJeiFixture", "texture", "(Lcom/simibubi/create/foundation/gui/AllGuiTextures;)V", false)); instructions.add(new InsnNode(Opcodes.RETURN));
                } else continue;
                method.instructions = instructions; method.tryCatchBlocks.clear(); method.localVariables = null;
            }
            var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES); node.accept(writer);
            var target = output.resolve(name + ".class"); Files.createDirectories(target.getParent()); Files.write(target, writer.toByteArray());
        }
    }
    static void config(net.neoforged.neoforge.common.ModConfigSpec spec) throws Exception {
        var values = com.electronwill.nightconfig.core.CommentedConfig.inMemory(); spec.correct(values);
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        spec.acceptConfig((net.neoforged.fml.config.IConfigSpec.ILoadedConfig) constructor.newInstance(values,null,null));
    }
    static void configure(boolean addons) throws Exception {
        var server = new CServer(); var builder = new net.neoforged.neoforge.common.ModConfigSpec.Builder();
        server.registerAll(builder); server.specification = builder.build(); config(server.specification);
        put(null, AllConfigs.class, "server", server);
        if (addons) config(com.mrh0.createaddition.config.CommonConfig.COMMON_CONFIG);
    }
    static void set(net.createmod.catnip.config.ConfigBase.ConfigInt entry, int number) throws Exception {
        var field = net.createmod.catnip.config.ConfigBase.CValue.class.getDeclaredField("value"); field.setAccessible(true);
        ((net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<Integer>)field.get(entry)).set(number);
    }
    static SequencedAssemblyRecipe assembly(String results, String visible) {
        return serializer.codec().codec().parse(JsonOps.INSTANCE, JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":{"id":"minecraft:iron_ingot"},
             "sequence":[],"loops":1,"results":%s%s}
            """.formatted(results, visible))).getOrThrow();
    }
    static void codecs() {
        for (String field : List.of("", ",\"show_outputs\":false", ",\"show_outputs\":true")) {
            var recipe = assembly("[{\"id\":\"minecraft:gold_ingot\",\"chance\":3},{\"id\":\"minecraft:flint\",\"chance\":1}]", field);
            boolean shown = field.endsWith("true");
            var encoded = serializer.codec().codec().encodeStart(JsonOps.INSTANCE, recipe).getOrThrow().getAsJsonObject();
            check((encoded.has("show_outputs") && encoded.get("show_outputs").getAsBoolean()) == shown, "native codec visibility");
            var copy = serializer.codec().codec().parse(JsonOps.INSTANCE, encoded).getOrThrow();
            check(((AssemblyOutputVisibility) copy).cwi$showOutputs() == shown, "JSON round trip");
            var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            try {
                serializer.streamCodec().encode(buffer, recipe); var network = serializer.streamCodec().decode(buffer);
                check(((AssemblyOutputVisibility) network).cwi$showOutputs() == shown && buffer.readableBytes() == 0, "network visibility round trip");
                check(network.resultPool.size() == 2 && network.resultPool.getFirst().getChance() == 3, "native weight payload retained");
            } finally { buffer.release(); }
        }
    }
    static IDrawable drawable() { return WorldSpoutingJeiFixture.proxy(IDrawable.class, (p,m,a) -> m.getName().equals("getWidth") ? 180 : m.getName().equals("getHeight") ? 180 : WorldSpoutingJeiFixture.def(m.getReturnType())); }
    static Object category(String name) throws Exception {
        var category = allocate(name); put(category, CreateRecipeCategory.class, "background", drawable()); return category;
    }
    static String chance(List<Component> tips) {
        return tips.stream().map(Component::getContents).filter(c -> c instanceof TranslatableContents t && t.getKey().equals("create.recipe.processing.chance"))
                .map(c -> ((TranslatableContents)c).getArgs()[0].toString()).findFirst().orElse("");
    }
    static void assemblyDisplay() throws Exception {
        var category = (SequencedAssemblyCategory) category("com.simibubi.create.compat.jei.category.SequencedAssemblyCategory");
        var minecraft = allocate("net.minecraft.client.Minecraft"); put(null, net.minecraft.client.Minecraft.class, "instance", minecraft);
        put(minecraft, net.minecraft.client.Minecraft.class, "font", allocate("net.minecraft.client.gui.Font"));
        var level = allocate("net.minecraft.client.multiplayer.ClientLevel");
        put(level, net.minecraft.world.level.Level.class, "registryAccess", RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        put(minecraft, net.minecraft.client.Minecraft.class, "level", level);
        var graphics = (net.minecraft.client.gui.GuiGraphics) allocate("net.minecraft.client.gui.GuiGraphics");
        for (boolean shown : new boolean[]{false,true}) {
            var recipe = assembly("[{\"id\":\"minecraft:gold_ingot\",\"chance\":6},{\"id\":\"minecraft:flint\",\"count\":2,\"chance\":1},{\"id\":\"minecraft:flint\",\"count\":2,\"chance\":1},{\"id\":\"minecraft:flint\",\"count\":3,\"chance\":2}]", ",\"show_outputs\":" + shown);
            WorldSpoutingJeiFixture.slots.clear(); category.setRecipe(WorldSpoutingJeiFixture.layout(), recipe, null);
            check(WorldSpoutingJeiFixture.slots.size() == (shown ? 3 : 2), "recipe-specific waste slot");
            if (shown) {
                var slot = WorldSpoutingJeiFixture.slots.getLast();
                check(slot.x == 144 && slot.y == 91 && slot.items.size() == 3, "native assembly waste geometry/cycling");
                check(slot.background == CreateRecipeCategory.getRenderedSlot(0f), "native chance frame");
                check(chance(WorldSpoutingJeiFixture.tooltip(slot,0,slot.callbacks)).equals("20"), "identical waste stacks aggregate weights");
                check(chance(WorldSpoutingJeiFixture.tooltip(slot,2,slot.callbacks)).equals("20"), "different counts remain separate outcomes");
            }
            check(category.getTooltipStrings(recipe,null,150,96).isEmpty() == shown, "generic salvage tooltip only when hidden");
            rendered.clear(); category.draw(recipe, null, graphics, 0, 0);
            check(rendered.contains("JEI_CHANCE_SLOT") != shown, "question-mark render follows recipe visibility");
        }
        var single = assembly("[{\"id\":\"minecraft:gold_ingot\"}]", ",\"show_outputs\":true");
        WorldSpoutingJeiFixture.slots.clear(); category.setRecipe(WorldSpoutingJeiFixture.layout(), single, null);
        check(WorldSpoutingJeiFixture.slots.size() == 2, "guaranteed assembly gains no empty waste slot");
    }
    static Object recipe(String name, float chance, boolean noItems) throws Exception {
        var recipe = allocate(name);
        put(recipe, ProcessingRecipe.class, "ingredients", NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT)));
        put(recipe, ProcessingRecipe.class, "fluidIngredients", NonNullList.create());
        put(recipe, ProcessingRecipe.class, "fluidResults", NonNullList.of(FluidStack.EMPTY, new FluidStack(Fluids.WATER,100)));
        put(recipe, ProcessingRecipe.class, "results", noItems ? NonNullList.create() : NonNullList.of(new ProcessingOutput(ItemStack.EMPTY,1), new ProcessingOutput(new ItemStack(Items.FLINT,4),chance)));
        put(recipe, ProcessingRecipe.class, "processingDuration", 100); return recipe;
    }
    static void drain() throws Exception {
        var category = (ItemDrainCategory) category("com.simibubi.create.compat.jei.category.ItemDrainCategory");
        for (float chance : new float[]{1,.25f,.001f,.999f}) for (boolean empty : new boolean[]{false,true}) {
            var recipe = (com.simibubi.create.content.fluids.transfer.EmptyingRecipe) recipe("com.simibubi.create.content.fluids.transfer.EmptyingRecipe", chance, empty);
            WorldSpoutingJeiFixture.slots.clear(); category.setRecipe(WorldSpoutingJeiFixture.layout(), recipe, null);
            var slot = WorldSpoutingJeiFixture.slots.getLast();
            check(slot.x == 132 && slot.y == 27, "drain output position retained");
            check(slot.background == CreateRecipeCategory.getRenderedSlot(empty ? 1f : chance), "drain chance frame only for probabilistic item");
            String expected = empty || chance == 1 ? "" : chance == .25f ? "25" : chance < .01f ? "<1" : ">99";
            check(chance(slot.tips).equals(expected), "drain ordinary whole-stack chance tooltip");
            check(empty || slot.items.getFirst().getCount() == 4, "drain output count retained");
        }
    }
    static void timings() throws Exception {
        String create = "com.simibubi.create.compat.jei.category.", vintage = "com.negodya1.vintageimprovements.compat.jei.category.";
        var names = new ArrayList<String>();
        for (String name : List.of("MillingCategory","CrushingCategory","SawingCategory","BlockCuttingCategory","PressingCategory","PackingCategory","MixingCategory","DeployingCategory","SpoutCategory","ItemDrainCategory","FanWashingCategory","FanSmokingCategory","FanHauntingCategory","FanBlastingCategory")) names.add(create+name);
        for (String name : List.of("CentrifugationCategory","CoilingCategory","GrinderPolishingCategory","GrinderSandpaperPolishingCategory","VibratingCategory","LeavesVibratingCategory","UnpackingCategory","TurningCategory","PressurizingCategory","VacuumizingCategory","CurvingCategory","AutoCurvingCategory")) names.add(vintage+name);
        names.add("com.mrh0.createaddition.compat.jei.RollingMillCategory");
        names.add("com.jesz.createdieselgenerators.compat.jei.BasinFermentingCategory"); names.add("com.jesz.createdieselgenerators.compat.jei.DistillationCategory");
        Recipe<?> recipe = (Recipe<?>) recipe("com.simibubi.create.content.kinetics.millstone.MillingRecipe",1,false);
        var holder = new RecipeHolder<>(ResourceLocation.parse("cwi:timing"),recipe);
        for (String name : names) {
            var category = (CreateRecipeCategory) category(name);
            var profile = ProcessingTiming.profile(category,recipe); check(profile != null,"supported timing category " + name);
            double x = (profile.x1()+profile.x2())/2d, y = (profile.y1()+profile.y2())/2d;
            for (boolean detailed : new boolean[]{false,true}) {
                shift = detailed;
                var tips = new ArrayList<Component>();
                var builder = WorldSpoutingJeiFixture.proxy(ITooltipBuilder.class,(p,m,a) -> {
                    if (m.getName().equals("addAll")) tips.addAll((Collection<Component>)a[0]);
                    if (m.getName().equals("add") && a[0] instanceof Component c) tips.add(c);
                    return m.getReturnType().isInstance(p) ? p : WorldSpoutingJeiFixture.def(m.getReturnType()); });
                category.getTooltip(builder,holder,null,x,y);
                check(tips.stream().filter(c->c.getContents() instanceof TranslatableContents t && t.getKey().equals("cwi.jei.time.estimate")).count()==1,"one scaled tooltip, no Vintage duplicate " + name);
                check(tips.stream().noneMatch(c->c.getContents() instanceof TranslatableContents t && t.getKey().startsWith("vintageimprovements.jei.text")),"no raw Vintage tooltip");
                check(tips.size() > (detailed?3:1),"Shift detail dispatch");
                tips.clear(); category.getTooltip(builder,holder,null,-100,-100); check(tips.isEmpty(),"no timing outside machine hover");
            }
        }
        shift = false;
        var mill = category(create+"MillingCategory");
        set(AllConfigs.server().kinetics.maxRotationSpeed,128);
        var slower = ProcessingTiming.tooltip(mill,recipe,55,25);
        set(AllConfigs.server().kinetics.maxRotationSpeed,256);
        var faster = ProcessingTiming.tooltip(mill,recipe,55,25);
        check(((TranslatableContents)slower.getFirst().getContents()).getArgs()[0].equals(13L) && ((TranslatableContents)faster.getFirst().getContents()).getArgs()[0].equals(7L),"runtime reference RPM config is read afresh");
        check(ProcessingTiming.estimate(ProcessingTiming.Kind.MIX,100,128,64,150).ticks()==31 && ProcessingTiming.estimate(ProcessingTiming.Kind.MIX,100,256,64,150).ticks()==16,"native mixer rate");
        check(ProcessingTiming.estimate(ProcessingTiming.Kind.MIX,10000,1,64,150).ticks()>512 && ProcessingTiming.estimate(ProcessingTiming.Kind.VACUUM,10000,1,64,150).ticks()==512,"Create versus Vintage mixer clamp");
        check(ProcessingTiming.estimate(ProcessingTiming.Kind.FAN,100,256,64,200).bulkTicks()==800,"fan configured duration and groups of sixteen");
        check(ProcessingTiming.estimate(ProcessingTiming.Kind.SAW,100,128,16,150).bulkTicks()==61,"saw fractional speed, floor batch multiplier and ejection");
    }
    static void selection() {
        String prefix="net.quiwh.cwi.mixin.jei.";
        for (String name:List.of("AssemblyRecipeMixin","AssemblySerializerMixin","AssemblyCategoryMixin","ItemDrainCategoryMixin","CreateTimingMixin","VintageTimingMixin")) {
            boolean common=name.equals("AssemblyRecipeMixin")||name.equals("AssemblySerializerMixin");
            check(!ProcessingJeiMixinPlugin.enabled(prefix+name,id->false),"absent Create");
            check(ProcessingJeiMixinPlugin.enabled(prefix+name,Set.of("create")::contains)==common,"headless client guard");
            check(ProcessingJeiMixinPlugin.enabled(prefix+name,Set.of("create","jei")::contains)==!name.equals("VintageTimingMixin"),"optional Vintage guard");
            check(ProcessingJeiMixinPlugin.enabled(prefix+name,Set.of("create","jei","vintageimprovements","createjustenoughtime")::contains)==common,"original JET owns UI while installed");
        }
    }
    public static void main(String[] args) throws Exception {
        if(args.length>0 && args[0].equals("capture")) {capture(Path.of(args[1]));return;}
        MinimalNativeBootstrap.initialize(); SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); GameData.unfreezeData();
        var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);unsafe=(sun.misc.Unsafe)field.get(null);
        // NeoForge marks built-in registries for ID sync during its real registry lifecycle.
        for (var registry : BuiltInRegistries.REGISTRY)
            if (registry instanceof net.neoforged.neoforge.registries.BaseMappedRegistry<?>)
                put(registry,net.neoforged.neoforge.registries.BaseMappedRegistry.class,"sync",true);
        serializer=new SequencedAssemblyRecipeSerializer();
        boolean addons = args.length == 0;
        configure(addons);
        if (addons) { codecs(); assemblyDisplay(); drain(); timings(); }
        else {
            var mill = category("com.simibubi.create.compat.jei.category.MillingCategory");
            Recipe<?> recipe = (Recipe<?>)recipe("com.simibubi.create.content.kinetics.millstone.MillingRecipe",1,false);
            check(!ProcessingTiming.tooltip(mill,recipe,55,25).isEmpty(), "Create timing without optional addons");
        }
        selection();
        System.out.println("ProcessingJeiFixture PASS: " + checks + " native codec/network/category/timing/selection contracts");
    }
}
