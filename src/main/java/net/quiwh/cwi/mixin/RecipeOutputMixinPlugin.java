package net.quiwh.cwi.mixin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Optional targets are selected before their mixins or classes can be loaded. */
public final class RecipeOutputMixinPlugin implements IMixinConfigPlugin {
    private record Integration(String mod, String targetPackage, boolean needsJei) {}
    private static final Map<String, Integration> INTEGRATIONS = integrations();
    private static Map<String, Integration> integrations() {
        var result = new java.util.HashMap<String, Integration>();
        add(result, "vintageimprovements", "com.negodya1.vintageimprovements.", false,
                "vintage.VibratingRecipeMixin", "vintage.CentrifugationRecipeMixin", "vintage.PressurizingRecipeParamsMixin",
                "vintage.VibratingTableBlockEntityMixin", "vintage.CentrifugeBlockEntityMixin", "vintage.CentrifugeRedstoneMixin",
                "vintage.CurvingPressRedstoneMixin", "vintage.CentrifugeRedstoneRendererMixin", "vintage.CurvingPressRedstoneRendererMixin",
                "vintage.VintageRedstoneScenesMixin", "vintage.VintageRedstoneSceneRegistrationMixin", "vintage.VintageRedstoneTagRegistrationMixin");
        add(result, "vintageimprovements", "com.negodya1.vintageimprovements.", true,
                "client.VibratingCategoryMixin", "client.CentrifugationCategoryMixin");
        add(result, "rubberworks", "io.github.hadron13.rubberworks.", false,
                "rubberworks.CompressingRecipeMixin", "rubberworks.CompressorBlockEntityMixin");
        add(result, "rubberworks", "io.github.hadron13.rubberworks.", true, "client.CompressingCategoryMixin");
        add(result, "tfmg", "com.drmangotea.tfmg.", false, "tfmg.VatMachineRecipeMixin", "tfmg.VatBlockEntityMixin");
        add(result, "tfmg", "com.drmangotea.tfmg.", true, "client.ChemicalVatCategoryMixin");
        add(result, "createmetallurgy", "fr.lucreeper74.createmetallurgy.", false,
                "metallurgy.FoundryBasinRecipeMixin", "metallurgy.FoundryBasinBlockEntityMixin");
        add(result, "createaddition", "com.mrh0.createaddition.", false,
                "createaddition.RollingRecipeMixin", "createaddition.RollingMillBlockEntityMixin");
        add(result, "createaddition", "com.mrh0.createaddition.", true,
                "client.RollingMillCategoryMixin", "client.AnimatedRollingMillMixin");
        return Map.copyOf(result);
    }
    private static void add(Map<String, Integration> rules, String mod, String target, boolean jei, String... mixins) {
        for (String mixin : mixins) if (rules.put("net.quiwh.cwi.mixin." + mixin, new Integration(mod, target, jei)) != null)
            throw new IllegalStateException("Duplicate integration rule: " + mixin);
    }

    public static boolean enabled(String target, String mixin, Predicate<String> loaded) {
        Integration rule = INTEGRATIONS.get(mixin);
        if (rule == null || !target.startsWith(rule.targetPackage()))
            throw new IllegalArgumentException("Missing or mismatched CWI integration rule: " + mixin + " -> " + target);
        return loaded.test(rule.mod()) && (!rule.needsJei() || loaded.test("jei"));
    }
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        var mods = FMLLoader.getLoadingModList();
        if (mods == null) return false;
        return enabled(targetClassName, mixinClassName, id -> mods.getModFileById(id) != null);
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) { }
}
