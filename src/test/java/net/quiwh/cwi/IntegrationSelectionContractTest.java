package net.quiwh.cwi;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.quiwh.cwi.mixin.RecipeOutputMixinPlugin;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

/** Read annotations without loading optional integration classes. Every configured target needs an explicit rule. */
public final class IntegrationSelectionContractTest {
    public static void main(String[] args) throws Exception {
        int targets = 0;
        var loader = IntegrationSelectionContractTest.class.getClassLoader();
        try (var stream = loader.getResourceAsStream("cwi-recipe-outputs.mixins.json")) {
            var config = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var section : List.of("mixins", "client")) for (var name : config.getAsJsonArray(section)) {
                String mixin = config.get("package").getAsString() + "." + name.getAsString();
                var node = new ClassNode();
                try (var code = loader.getResourceAsStream(mixin.replace('.', '/') + ".class")) {
                    new ClassReader(code).accept(node, ClassReader.SKIP_CODE);
                }
                boolean found = false;
                for (var annotation : node.invisibleAnnotations) if (annotation.desc.endsWith("/Mixin;"))
                    for (int i = 0; i < annotation.values.size(); i += 2) {
                        String key = annotation.values.get(i).toString();
                        if (!key.equals("value") && !key.equals("targets")) continue;
                        for (Object target : (List<?>)annotation.values.get(i + 1)) {
                            String targetName = target instanceof Type type ? type.getClassName() : target.toString();
                            require(RecipeOutputMixinPlugin.enabled(targetName, mixin, id -> true), "available integration disabled: " + mixin);
                            require(!RecipeOutputMixinPlugin.enabled(targetName, mixin, id -> false), "absent mod enabled: " + mixin);
                            require(!RecipeOutputMixinPlugin.enabled(targetName, mixin, "jei"::equals), "JEI alone enabled integration: " + mixin);
                            require(RecipeOutputMixinPlugin.enabled(targetName, mixin, id -> !id.equals("jei")) == !name.getAsString().startsWith("client."),
                                    "explicit JEI requirement differs: " + mixin);
                            targets++; found = true;
                        }
                    }
                require(found, "No target checked: " + mixin);
                try {
                    RecipeOutputMixinPlugin.enabled("unexpected.Target", mixin, id -> true);
                    throw new AssertionError("Mismatched target accepted: " + mixin);
                } catch (IllegalArgumentException expected) { }
            }
        }
        require(targets >= 27, "Integration target count unexpectedly reduced");
        System.out.println("Integration selection: " + targets + " configured targets and absent/JEI-only/available profiles passed");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
