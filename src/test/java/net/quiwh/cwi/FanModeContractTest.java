package net.quiwh.cwi;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

/** Runs without a game bootstrap; catches accidental changes to existing pack IDs. */
public final class FanModeContractTest {
    public static void main(String[] args) throws Exception {
        Class<?> type;
        try {
            type = Class.forName("net.quiwh.cwi.processing.FanMode");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("CWI fan processing modes are not implemented", missing);
        }
        Object[] modes = type.getEnumConstants();
        require(modes.length == 2, "exactly two custom fan modes");
        check(type, modes[0], "cwi:corroding", "create:fan_processing_catalysts/corroding", 501, 0xABAC50, 0.75F);
        check(type, modes[1], "cwi:sterilzing", "create:fan_processing_catalysts/sterilizing", 502, 0x88CCFF, 0.6F);
        Codec<?> codec = (Codec<?>) type.getField("CODEC").get(null);
        require(codec.parse(JsonOps.INSTANCE, new JsonPrimitive("cwi:unknown")).error().isPresent(),
                "unknown modes must fail decoding");
        require(codec.parse(JsonOps.INSTANCE, new JsonPrimitive("cwi:sterilizing")).error().isPresent(),
                "the compatibility recipe spelling must not silently change");
        System.out.println("FanModeContractTest: 15 assertions passed");
    }

    @SuppressWarnings("unchecked")
    private static void check(Class<?> type, Object mode, String id, String catalyst, int priority, int color, float alpha)
            throws Exception {
        require(type.getMethod("recipeId").invoke(mode).toString().equals(id), "recipe ID " + id);
        require(type.getMethod("catalystId").invoke(mode).toString().equals(catalyst), "catalyst " + catalyst);
        require(type.getMethod("priority").invoke(mode).equals(priority), "fan priority " + priority);
        require(type.getMethod("color").invoke(mode).equals(color), "airflow color " + id);
        require(type.getMethod("alpha").invoke(mode).equals(alpha), "airflow alpha " + id);
        Codec<Object> codec = (Codec<Object>) type.getField("CODEC").get(null);
        Object decoded = codec.parse(JsonOps.INSTANCE, new JsonPrimitive(id)).getOrThrow();
        require(decoded == mode, "mode codec round trip " + id);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
