package net.quiwh.cwi.kubejs;

import dev.latvian.mods.kubejs.item.custom.HandheldItemBuilder;
import dev.latvian.mods.kubejs.generator.KubeAssetGenerator;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;

import java.util.Objects;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * KubeJS handheld hammer item type.
 *
 * <p>The builder deliberately follows KubeJS's sword builder surface: tier,
 * attackDamageBaseline, speedBaseline, modifyTier, attackDamageBonus and
 * speed are inherited, while ordinary item behavior callbacks continue to be
 * supplied by KubeJS. This keeps existing hammer scripts compatible.</p>
 */
public final class HammerBuilder extends HandheldItemBuilder {
    public transient int chargeTime = 12;
    public transient int maxStage = 3;
    private transient Consumer<HammerImpactContext> afterImpact;
    private transient boolean chargeModels = true;
    private final transient Map<Integer, String> chargedTextures = new HashMap<>();
    private static final ResourceLocation TOOL_TAG =
            ResourceLocation.fromNamespaceAndPath("forge", "tools");
    private static final ResourceLocation DECORATED_POT_TAG =
            ResourceLocation.fromNamespaceAndPath("minecraft", "breaks_decorated_pots");

    public HammerBuilder(ResourceLocation id) {
        super(id, 3.0F, -2.4F);
        // Preserve the sword compatibility inherited by the original KubeJS hammers.
        tag(new ResourceLocation[]{ItemTags.SWORDS.location(), TOOL_TAG, DECORATED_POT_TAG});
        parentModel = ResourceLocation.withDefaultNamespace("item/handheld");
    }

    /** Number of held-use ticks needed to gain one hammer stage. */
    public HammerBuilder chargeTime(int ticks) {
        if (ticks <= 0) throw new IllegalArgumentException("Hammer charge time must be positive");
        chargeTime = ticks;
        return this;
    }

    /** Maximum stage this hammer can charge to. */
    public HammerBuilder maxStage(int stage) {
        if (stage <= 0) throw new IllegalArgumentException("Hammer maximum stage must be positive");
        maxStage = stage;
        return this;
    }

    /** Leave charge visuals to a custom asset model, such as the original 3D steel pipe. */
    @dev.latvian.mods.kubejs.typings.Info("Generate the three ordinary hammer charge models automatically (default true). Explicit modelGenerator callbacks always own the complete model.")
    public HammerBuilder chargeModels(boolean enabled) {
        chargeModels = enabled;
        return this;
    }

    /** Replaces one stage's complete texture instead of adding the ordinary charge overlay. */
    @dev.latvian.mods.kubejs.typings.Info("Use a complete replacement texture for charge stage 1, 2 or 3, without the shared overlay. Useful for the original stone hammer.")
    public HammerBuilder chargedTexture(int stage, String texture) {
        if (stage < 1 || stage > 3) throw new IllegalArgumentException("Hammer visual stage must be 1, 2 or 3");
        chargedTextures.put(stage, ResourceLocation.parse(Objects.requireNonNull(texture, "charged texture")).toString());
        return this;
    }

    @Override
    protected void generateItemModels(KubeAssetGenerator generator) {
        if (!chargeModels || modelGenerator != null) {
            super.generateItemModels(generator);
            return;
        }
        JsonObject base = new JsonObject();
        base.addProperty("parent", parentModel == null ? "minecraft:item/handheld" : parentModel.toString());
        JsonObject layers = new JsonObject();
        layers.addProperty("layer0", baseTexture);
        textures.forEach(layers::addProperty);
        base.add("textures", layers);
        JsonArray overrides = new JsonArray();
        for (int stage = 1; stage <= 3; stage++) {
            ResourceLocation chargedId = id.withPath(path -> "item/" + path + "/charged_")
                    .withSuffix(Integer.toString(stage));
            JsonObject charged = base.deepCopy();
            JsonObject chargedLayers = charged.getAsJsonObject("textures");
            if (chargedTextures.containsKey(stage)) {
                chargedLayers.addProperty("layer0", chargedTextures.get(stage));
                chargedLayers.remove("layer1");
            } else chargedLayers.addProperty("layer1", "kubejs:item/tools/charged_overlay_" + stage);
            generator.json(chargedId.withPath(path -> "models/" + path + ".json"), charged);
            JsonObject predicate = new JsonObject();
            predicate.addProperty("minecraft:custom_model_data", stage);
            JsonObject override = new JsonObject();
            override.add("predicate", predicate);
            override.addProperty("model", chargedId.toString());
            overrides.add(override);
        }
        base.add("overrides", overrides);
        generator.json(id.withPath(path -> "models/item/" + path + ".json"), base);
    }

    /** Runs on the server after a charged entity hit successfully deals damage. */
    @dev.latvian.mods.kubejs.typings.Info("Runs once after a successful charged entity hit on the server. Context: item, level, player, target, hand, stage, damage. Does not replace native damage, effects, cooldown or durability.")
    public HammerBuilder afterImpact(Consumer<HammerImpactContext> callback) {
        afterImpact = Objects.requireNonNull(callback, "afterImpact callback");
        return this;
    }

    @Override
    public Item createObject() {
        itemAttributeModifiers = SwordItem.createAttributes(toolTier, attackDamageBaseline, speedBaseline);
        return new HammerItem(toolTier, createItemProperties(), chargeTime, maxStage, behavior, afterImpact);
    }
}
