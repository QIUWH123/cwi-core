import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import net.neoforged.accesstransformer.api.AccessTransformerEngine;
import org.antlr.v4.runtime.CharStreams;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

/** Applies installed mods' real AT configs to classpath bytecode for a standalone fixture. */
public final class ApplyInstalledAccessTransformers {
    public static void main(String[] args) throws Exception {
        var engine = AccessTransformerEngine.newEngine();
        var output = Path.of(args[0]);
        for (int i = 1; i < args.length; i++) {
            try (var jar = new ZipFile(args[i])) {
                var entry = jar.getEntry("META-INF/accesstransformer.cfg");
                if (entry == null) throw new AssertionError("Missing installed AT: " + args[i]);
                try (var input = jar.getInputStream(entry)) {
                    engine.loadAT(CharStreams.fromStream(input));
                }
            }
        }
        int transformed = 0;
        for (var target : engine.getTargets()) {
            String name = target.getInternalName() + ".class";
            try (var input = ApplyInstalledAccessTransformers.class.getClassLoader().getResourceAsStream(name)) {
                if (input == null) throw new AssertionError("Missing AT target: " + name);
                var node = new ClassNode();
                new ClassReader(input).accept(node, 0);
                if (!engine.transform(node, target)) continue;
                var writer = new ClassWriter(0);
                node.accept(writer);
                var file = output.resolve(name);
                Files.createDirectories(file.getParent());
                Files.write(file, writer.toByteArray());
                transformed++;
            }
        }
        System.out.println("Applied installed access transformers to " + transformed + " native classes");
    }
}
