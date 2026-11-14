package net.quiwh.cwi.compat.jei;

import com.simibubi.create.compat.jei.category.ProcessingViaFanCategory;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.content.kinetics.base.IRotate.SpeedLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.crafting.Recipe;

/** Processing estimates at Create's configured maximum RPM; never changes machine behavior. */
public final class ProcessingTiming {
    private static final String CREATE = "com.simibubi.create.compat.jei.category.";
    private static final String VINTAGE = "com.negodya1.vintageimprovements.compat.jei.category.";
    private static final String DIESEL = "com.jesz.createdieselgenerators.compat.jei.";

    public enum Kind { MILL, CRUSH, SAW, PRESS, MIX, DEPLOY, FILL, DRAIN, FAN, VACUUM, TURN, FERMENT, DISTILL, ROLL }
    public record Profile(Kind kind, int defaultTime, int fixedTime, int x1, int x2, int y1, int y2) {
        boolean contains(double x, double y) { return x > x1 && x < x2 && y > y1 && y < y2; }
    }
    public record Estimate(long ticks, long overhead, int raw, int rpm, boolean kinetic, int batch, long bulkTicks, String detail) { }

    /** Exact names keep the superclass hook from changing unrelated addon categories. */
    public static Profile profile(Object category, Recipe<?> recipe) {
        String name = category.getClass().getName();
        if (category instanceof ProcessingViaFanCategory<?>) return p(Kind.FAN, 0, 0, 55, 106, 8, 48);
        if (name.startsWith(CREATE)) return switch (name.substring(CREATE.length())) {
            case "MillingCategory" -> p(Kind.MILL, 0, 0, 42, 71, 18, 50);
            case "CrushingCategory" -> p(Kind.CRUSH, 0, 0, 44, 132, 24, 71);
            case "SawingCategory" -> p(Kind.SAW, 0, 0, 63, 97, 31, 65);
            case "BlockCuttingCategory" -> p(Kind.SAW, 50, 50, 25, 58, 26, 60);
            case "PressingCategory" -> p(Kind.PRESS, 0, 0, 70, 103, -1, 48);
            case "PackingCategory" -> p(Kind.PRESS, 0, 0, 90, 121, 10, 76);
            case "MixingCategory" -> p(Kind.MIX, 100, 0, 90, 120, 9, 76);
            case "DeployingCategory" -> p(Kind.DEPLOY, 0, 0, 74, 102, 1, 65);
            case "SpoutCategory" -> p(Kind.FILL, 20, 20, 76, 100, 1, 64);
            case "ItemDrainCategory" -> p(Kind.DRAIN, 20, 20, 74, 102, 21, 45);
            default -> null;
        };
        if (name.startsWith(VINTAGE)) return switch (name.substring(VINTAGE.length())) {
            case "CentrifugationCategory" -> p(Kind.MILL, 100, 0, 23, 105, 47, 97);
            case "VibratingCategory" -> p(Kind.MILL, 100, 0, 39, 73, 19, 57);
            case "LeavesVibratingCategory", "UnpackingCategory" -> p(Kind.MILL, 100, 100, 39, 73, 19, 57);
            case "CoilingCategory", "GrinderPolishingCategory" -> p(Kind.SAW, 0, 0, 63, 97, 33, 64);
            case "GrinderSandpaperPolishingCategory" -> p(Kind.SAW, 50, 50, 63, 97, 33, 64);
            case "CurvingCategory", "AutoCurvingCategory" -> p(Kind.PRESS, 0, 0, 70, 103, -1, 48);
            case "TurningCategory" -> p(Kind.TURN, 200, 0, 54, 97, 24, 65);
            case "PressurizingCategory", "VacuumizingCategory" -> p(Kind.VACUUM, 100, 0, 90, 120, 9, 76);
            default -> null;
        };
        if (name.equals("com.mrh0.createaddition.compat.jei.RollingMillCategory")) return p(Kind.ROLL, 0, 0, 45, 75, 21, 52);
        if (name.equals(DIESEL + "BasinFermentingCategory")) return p(Kind.FERMENT, 0, 0, 90, 121, 41, 73);
        if (name.equals(DIESEL + "DistillationCategory") && recipe instanceof ProcessingRecipe<?, ?> processing)
            return p(Kind.DISTILL, 0, 0, 90, 121, 161 - 22 * (processing.getFluidResults().size() + 1), 169);
        return null;
    }

    private static Profile p(Kind kind, int fallback, int fixed, int x1, int x2, int y1, int y2) {
        return new Profile(kind, fallback, fixed, x1, x2, y1, y2);
    }

    public static List<Component> tooltip(Object category, Recipe<?> recipe, double x, double y) {
        Profile profile = profile(category, recipe);
        if (profile == null || !profile.contains(x, y)) return List.of();
        int raw = profile.fixedTime > 0 ? profile.fixedTime
                : recipe instanceof ProcessingRecipe<?, ?> processing ? processing.getProcessingDuration() : 0;
        if (raw == 0) raw = profile.defaultTime;
        if (profile.kind == Kind.PRESS || profile.kind == Kind.DEPLOY) raw = 0;
        if (profile.kind == Kind.ROLL) raw = RollingSettings.duration();
        return lines(estimate(profile.kind, raw, AllConfigs.server().kinetics.maxRotationSpeed.get(),
                maxBatch(recipe), AllConfigs.server().kinetics.fanProcessingTime.get()), Screen.hasShiftDown());
    }

    public static List<Component> lines(Estimate estimate, boolean detailed) {
        var result = new ArrayList<Component>();
        result.add(Component.translatable("cwi.jei.time.estimate", estimate.ticks, seconds(estimate.ticks)));
        if (estimate.overhead > 0) result.add(Component.translatable("cwi.jei.time.overhead", estimate.overhead).withStyle(ChatFormatting.GRAY));
        if (!detailed) result.add(Component.translatable("cwi.jei.time.shift").withStyle(ChatFormatting.DARK_GRAY));
        else {
            if (estimate.kinetic) result.add(Component.translatable("cwi.jei.time.rpm", estimate.rpm).withStyle(ChatFormatting.AQUA));
            if (estimate.raw > 0) result.add(Component.translatable("cwi.jei.time.raw", estimate.raw).withStyle(ChatFormatting.GRAY));
            if (estimate.batch > 1) result.add(Component.translatable("cwi.jei.time.batch", estimate.batch,
                    estimate.bulkTicks, seconds(estimate.bulkTicks)).withStyle(ChatFormatting.GRAY));
            result.add(Component.translatable("cwi.jei.time.detail." + estimate.detail).withStyle(ChatFormatting.YELLOW));
            result.add(Component.translatable("cwi.jei.time.unblocked").withStyle(ChatFormatting.GRAY));
        }
        return result;
    }

    /** Tick thresholds follow installed Create 6 and its addons, including fractional saw rates. */
    public static Estimate estimate(Kind kind, int raw, int rpm, int batch, int fanTime) {
        rpm = Math.max(1, Math.abs(rpm)); batch = Math.max(1, batch); raw = Math.max(0, raw);
        int millRate = Mth.clamp(rpm / 16, 1, 512);
        float sawRate = Mth.clamp(rpm / 24f, 1, 128);
        long ticks, overhead = 0, bulk = 0;
        boolean kinetic = true;
        String detail = "processing";
        switch (kind) {
            case MILL, ROLL -> { ticks = ceil(raw, millRate); overhead = 1; }
            case CRUSH -> {
                ticks = crushing(raw, rpm, 1); bulk = crushing(raw, rpm, batch); overhead = 1; detail = "crushing";
            }
            case SAW -> {
                ticks = below(raw, 5, sawRate);
                overhead = 1 + ceil(20, sawRate);
                bulk = below((long) raw * Math.max(1, batch / 5), 5, sawRate) + overhead;
                detail = "saw";
            }
            case PRESS -> {
                int rate = (int) Mth.lerp(Mth.clamp(rpm / 512f, 0, 1), 1, 60);
                ticks = ceil(120, rate) + 120 / rate + 2; overhead = 1; detail = "press";
            }
            case DEPLOY -> {
                int rate = (int) Mth.clamp(rpm * 2f, 8, 512);
                ticks = 2 * ceil(1000, rate) + ceil(500, rate) + 3; detail = "deployer";
            }
            case MIX, VACUUM -> {
                long duration = (long) Mth.log2((int) (512f / rpm)) * Mth.ceil(raw / 100f * 15) + 1;
                ticks = kind == Kind.VACUUM ? Math.clamp(duration, 1, 512) : Math.max(1, duration);
                overhead = 1; detail = "mixer";
            }
            case FILL -> { ticks = 20; overhead = 1; kinetic = false; detail = "filling"; }
            case DRAIN -> { ticks = 20; kinetic = false; detail = "draining"; }
            case FAN -> {
                ticks = fanTime; raw = fanTime; bulk = (long) fanTime * ((batch - 1) / 16 + 1);
                overhead = 1; kinetic = false; detail = "fan";
            }
            case TURN -> {
                float fast = SpeedLevel.FAST.getSpeedValue(), medium = SpeedLevel.MEDIUM.getSpeedValue();
                float rate = 1 + Mth.clamp((int) ((rpm - fast) / (256f - fast)) / 2f, 0, .5f)
                        + Mth.clamp((int) ((rpm - medium) / (256f - medium)) / 2f, 0, .5f);
                ticks = ceil(raw, rate); overhead = 1; detail = "turning";
            }
            case FERMENT -> { ticks = raw; overhead = 2; kinetic = false; detail = "fermenting"; }
            case DISTILL -> { ticks = raw; kinetic = false; detail = "distillation"; }
            default -> throw new IllegalArgumentException(kind.toString());
        }
        return new Estimate(ticks, overhead, raw, rpm, kinetic, bulk > 0 ? batch : 1, bulk, detail);
    }

    private static long crushing(int raw, int rpm, int count) {
        // The wheel passes RPM/50 to the controller; after application the stack divisor is removed.
        float speed = rpm * 4f / 50;
        float divisor = (float) Math.log(count) / (float) Math.log(2);
        float rate = Mth.clamp(speed / divisor, .25f, 20f);
        long processing = below(raw, 20, rate);
        float remaining = raw - processing * rate;
        return processing + Math.max(1, ceil(remaining, Mth.clamp(speed, .25f, 20f)));
    }
    private static long below(long raw, int threshold, float rate) {
        return Math.max(1, (long) Math.floor((raw - threshold) / (double) rate) + 1);
    }
    private static long ceil(double value, double rate) { return Math.max(0, (long) Math.ceil(value / rate)); }
    private static String seconds(long ticks) {
        return String.format(Locale.ROOT, "%.2f", ticks / 20d).replaceFirst("\\.?0+$", "");
    }
    private static int maxBatch(Recipe<?> recipe) {
        return recipe.getIngredients().stream().flatMap(ingredient -> java.util.Arrays.stream(ingredient.getItems()))
                .mapToInt(stack -> stack.getMaxStackSize()).max().orElse(64);
    }
    /** Optional addon is linked only when its category is actually queried. */
    private static final class RollingSettings {
        static int duration() { return com.mrh0.createaddition.config.CommonConfig.ROLLING_MILL_PROCESSING_DURATION.get(); }
    }
    private ProcessingTiming() { }
}
