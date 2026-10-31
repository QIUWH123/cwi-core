package net.quiwh.cwi.processing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;

/** Stable pack identifiers. The historical sterilzing recipe spelling is intentional. */
public enum FanMode {
    CORRODING("corroding", "corroding", 501, 0xABAC50, 0.75F),
    STERILIZING("sterilzing", "sterilizing", 502, 0x88CCFF, 0.6F);

    public static final Codec<FanMode> CODEC = Codec.STRING.comapFlatMap(value -> {
        for (FanMode mode : values()) {
            if (mode.recipeId.toString().equals(value)) return DataResult.success(mode);
        }
        return DataResult.error(() -> "Unknown CWI fan mode: " + value);
    }, mode -> mode.recipeId.toString());

    private final ResourceLocation recipeId;
    private final ResourceLocation catalystId;
    private final int priority;
    private final int color;
    private final float alpha;

    FanMode(String recipePath, String catalystPath, int priority, int color, float alpha) {
        this.recipeId = ResourceLocation.fromNamespaceAndPath("cwi", recipePath);
        this.catalystId = ResourceLocation.fromNamespaceAndPath("create", "fan_processing_catalysts/" + catalystPath);
        this.priority = priority;
        this.color = color;
        this.alpha = alpha;
    }

    public ResourceLocation recipeId() { return recipeId; }
    public ResourceLocation catalystId() { return catalystId; }
    public int priority() { return priority; }
    public int color() { return color; }
    public float alpha() { return alpha; }
}
