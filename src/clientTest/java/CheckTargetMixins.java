import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.container.*;
import org.spongepowered.asm.logging.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.service.*;

/** Runs the installed target Mixin injector against every target in the requested configs.
 * The host supplies bytecode only; this does not start Forge or execute transformed world logic.
 * Arguments: compiled classes directory, exact Create jar, output directory, configs... */
public class CheckTargetMixins {
    static Path root;
    static ZipFile create;
    public static void main(String[] args) throws Exception {
        root = Path.of(args[0]); create = new ZipFile(args[1]);
        var names = new ArrayList<String>();
        var targets = new TreeSet<String>();
        Path fixture = Path.of(args[2]);
        Files.createDirectories(fixture);
        var configs = new ArrayList<String>();
        for (int configIndex = 3; configIndex < args.length; configIndex++) {
            var central = com.google.gson.JsonParser.parseString(Files.readString(Path.of(args[configIndex]))).getAsJsonObject();
            String pkg = central.get("package").getAsString();
            for (String section : List.of("mixins", "client")) {
                if (!central.has(section)) continue;
                for (var configured : central.getAsJsonArray(section)) {
                    String name = pkg + "." + configured.getAsString();
                    names.add(name);
                    ClassNode node = read(Files.readAllBytes(root.resolve(name.replace('.', '/') + ".class")));
                    if (node.invisibleAnnotations == null) throw new AssertionError("No @Mixin: " + name);
                    for (AnnotationNode annotation : node.invisibleAnnotations) if (annotation.desc.endsWith("/Mixin;"))
                        for (int i=0; i<annotation.values.size(); i+=2) {
                            if (annotation.values.get(i).equals("targets")) for (Object target : (List<?>)annotation.values.get(i+1)) targets.add(target.toString());
                            if (annotation.values.get(i).equals("value")) for (Object target : (List<?>)annotation.values.get(i+1)) targets.add(((Type)target).getClassName());
                        }
                }
            }
            // Installed-integration mode: bypass only loader discovery, never configured mixin entries.
            central.remove("plugin");
            String config = "target-" + configIndex + ".mixins.json";
            Files.writeString(fixture.resolve(config), central.toString());
            configs.add(config);
        }
        if (names.isEmpty() || targets.isEmpty()) throw new AssertionError("No configured mixins/targets");
        Host.fixture = fixture;
        var constructor = MixinService.class.getDeclaredConstructor(); constructor.setAccessible(true);
        Object services = constructor.newInstance();
        set(MixinService.class, services, "service", new Host());
        set(MixinService.class, services, "propertyService", new Properties());
        set(MixinService.class, null, "instance", services);
        MixinBootstrap.init();
        Method phase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        phase.setAccessible(true); phase.invoke(null, MixinEnvironment.Phase.DEFAULT);
        MixinEnvironment environment = MixinEnvironment.getDefaultEnvironment();
        environment.setSide(MixinEnvironment.Side.CLIENT);
        for (String config : configs) Mixins.addConfiguration(config);
        Class<?> type = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer");
        var ctor = type.getDeclaredConstructor(); ctor.setAccessible(true);
        IMixinTransformer transformer = (IMixinTransformer)ctor.newInstance();
        environment.setActiveTransformer(transformer);
        com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
        int changed = 0;
        var appliedMixins = new TreeSet<String>();
        var syntheticArgs = new TreeSet<String>();
        var failures = new TreeMap<String,String>();
        for (String target : targets) {
            try {
            byte[] before = bytes(target);
            byte[] after = transformer.transformClass(environment, target, before);
            if (Arrays.equals(before, after)) throw new AssertionError("No mixins applied to " + target);
            ClassNode transformed = read(after);
            
            for (MethodNode method : transformed.methods) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode invoke && invoke.owner.startsWith("org/spongepowered/asm/synthetic/args/")) syntheticArgs.add(invoke.owner);
                }
                var annotations = new ArrayList<AnnotationNode>();
                if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
                if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
                for (AnnotationNode annotation : annotations)
                    if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;"))
                        for (int i = 0; i < annotation.values.size(); i += 2)
                            if (annotation.values.get(i).equals("mixin")) appliedMixins.add(annotation.values.get(i+1).toString());
            }
            Path output = fixture.resolve("transformed").resolve(target.replace('.', '/') + ".class");
            Files.createDirectories(output.getParent()); Files.write(output, after); changed++;
            System.out.println("Transformed " + target);
            } catch (Throwable failure) {
                failures.put(target, failure.toString());
                failure.printStackTrace(System.out);
            }
        }
        // Static accessor interfaces also need Mixin's postprocessor in a separate fixture JVM.
        for (String name : names) {
            byte[] before = bytes(name);
            ClassNode node = read(before);
            boolean staticAccessor = (node.access & Opcodes.ACC_INTERFACE) != 0 && node.methods.stream().anyMatch(method ->
                    (method.access & Opcodes.ACC_STATIC) != 0 && method.visibleAnnotations != null && method.visibleAnnotations.stream()
                    .anyMatch(annotation -> annotation.desc.endsWith("/Invoker;") || annotation.desc.endsWith("/Accessor;")));
            if (!staticAccessor) continue;
            byte[] after = transformer.transformClass(environment, name, before);
            Path output = fixture.resolve("transformed").resolve(name.replace('.', '/') + ".class");
            Files.createDirectories(output.getParent()); Files.write(output, after);
        }
        for (var name : syntheticArgs) {
            byte[] generated = transformer.generateClass(environment, name.replace('/', '.'));
            if (generated == null) throw new AssertionError("Missing generated class " + name);
            Path output = fixture.resolve("transformed").resolve(name + ".class");
            Files.createDirectories(output.getParent()); Files.write(output, generated);
        }
        // Persist sugar-local helper classes for the separate behavior-test JVM.
        for (var generated : com.llamalad7.mixinextras.utils.ClassGenUtils.getDefinitions().entrySet()) {
            Path output = fixture.resolve("transformed").resolve(generated.getKey().replace('.', '/') + ".class");
            Files.createDirectories(output.getParent()); Files.write(output, generated.getValue());
        }
        var report = new com.google.gson.JsonObject();
        report.addProperty("configured_mixins", names.size());
        report.addProperty("target_count", targets.size());
        report.addProperty("transformed_targets", changed);
        report.add("failures", new com.google.gson.Gson().toJsonTree(failures));
        report.add("merged_handlers", new com.google.gson.Gson().toJsonTree(appliedMixins));
        Files.writeString(fixture.resolve("report.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        System.out.println("CheckTargetMixins: " + names.size() + " configured mixins, " + changed + "/" + targets.size() + " targets transformed; failures=" + failures.size());
        if (!failures.isEmpty()) throw new AssertionError("Target transformation failures: " + failures.keySet());

    }
    static void set(Class<?> type, Object object, String name, Object value) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(object, value);
    }
    static ClassNode read(byte[] bytes) { var node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node; }
    static byte[] bytes(String name) throws IOException, ClassNotFoundException {
        String path = name.replace('.', '/') + ".class";
        var entry = create.getEntry(path);
        if (entry != null) return create.getInputStream(entry).readAllBytes();
        Path file = root.resolve(path);
        if (Files.exists(file)) return Files.readAllBytes(file);
        try (var in = CheckTargetMixins.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new ClassNotFoundException(name);
            return in.readAllBytes();
        }
    }
    public static class Host extends MixinServiceAbstract implements IClassProvider, IClassBytecodeProvider {
        static Path fixture;
        public String getName() { return "Offline combined native family fixture"; }
        public boolean isValid() { return true; }
        public IClassProvider getClassProvider() { return this; }
        public IClassBytecodeProvider getBytecodeProvider() { return this; }
        public ITransformerProvider getTransformerProvider() { return null; }
        public IClassTracker getClassTracker() { return null; }
        public IMixinAuditTrail getAuditTrail() { return null; }
        public Collection<String> getPlatformAgents() { return List.of(); }
        public IContainerHandle getPrimaryContainer() { return new ContainerHandleVirtual("tank-fixture"); }
        public InputStream getResourceAsStream(String name) {
            try {
                Path local = fixture.resolve(name);
                return Files.exists(local) ? Files.newInputStream(local)
                        : CheckTargetMixins.class.getClassLoader().getResourceAsStream(name);
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }
        public URL[] getClassPath() { return new URL[0]; }
        public Class<?> findClass(String name) throws ClassNotFoundException { return findClass(name, false); }
        public Class<?> findClass(String name, boolean init) throws ClassNotFoundException {
            return Class.forName(name, init, CheckTargetMixins.class.getClassLoader());
        }
        public Class<?> findAgentClass(String name, boolean init) throws ClassNotFoundException { return findClass(name, init); }
        public ClassNode getClassNode(String name) throws IOException, ClassNotFoundException { return read(bytes(name)); }
        public ClassNode getClassNode(String name, boolean transform) throws IOException, ClassNotFoundException { return getClassNode(name); }
        public ClassNode getClassNode(String name, boolean transform, int flags) throws IOException, ClassNotFoundException { return getClassNode(name); }
        protected ILogger createLogger(String name) { return new LoggerAdapterConsole(name); }
    }
    static class Properties implements IGlobalPropertyService {
        record Key(String name) implements IPropertyKey { }
        final Map<IPropertyKey,Object> values = new HashMap<>();
        public IPropertyKey resolveKey(String name) { return new Key(name); }
        @SuppressWarnings("unchecked") public <T>T getProperty(IPropertyKey key) { return (T)values.get(key); }
        public void setProperty(IPropertyKey key, Object value) { values.put(key, value); }
        @SuppressWarnings("unchecked") public <T>T getProperty(IPropertyKey key, T fallback) { return (T)values.getOrDefault(key, fallback); }
        public String getPropertyString(IPropertyKey key, String fallback) { return String.valueOf(values.getOrDefault(key, fallback)); }
    }
}
