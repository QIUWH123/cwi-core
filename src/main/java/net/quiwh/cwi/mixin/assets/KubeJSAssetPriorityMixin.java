package net.quiwh.cwi.mixin.assets;

import dev.latvian.mods.kubejs.script.data.GeneratedDataStage;
import dev.latvian.mods.kubejs.script.data.KubeFileResourcePack;
import dev.latvian.mods.kubejs.script.data.VirtualAssetPack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Promote completed KubeJS asset packs before Minecraft builds its resource indexes. */
@Mixin(MultiPackResourceManager.class)
public abstract class KubeJSAssetPriorityMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static List<PackResources> cwi$prioritizeAssets(List<PackResources> packs,
                                                          PackType type, List<PackResources> original) {
        if (type != PackType.CLIENT_RESOURCES || packs.size() < 2) return packs;
        var ordinary = new ArrayList<PackResources>(packs.size());
        var kubejs = new ArrayList<PackResources>();
        for (var pack : packs) {
            (cwi$isPriorityAssetPack(pack) ? kubejs : ordinary).add(pack);
        }
        if (kubejs.isEmpty() || ordinary.isEmpty()) return packs;
        // Later packs win. Preserve ordering within both groups, including KubeJS's LAST stage.
        ordinary.addAll(kubejs);
        return ordinary;
    }

    @Unique
    private static boolean cwi$isPriorityAssetPack(PackResources pack) {
        if (pack instanceof VirtualAssetPack virtual) {
            // Language files are merged into INTERNAL in current KubeJS, not its file pack.
            // Explicit before_mods generation must retain its intentionally low priority.
            return virtual.stage != GeneratedDataStage.BEFORE_MODS;
        }
        if (pack instanceof KubeFileResourcePack) return true;
        // KubeJS's own ZIP discovery marks packs this way; display names are not identifiers.
        return pack.location().knownPackInfo()
                .filter(info -> info.namespace().equals("kubejs") && info.id().startsWith("kubejs_file_"))
                .isPresent();
    }
}
