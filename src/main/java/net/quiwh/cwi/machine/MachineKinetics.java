package net.quiwh.cwi.machine;

/** The shared kinetic rules deliberately accept both rotation directions. */
public final class MachineKinetics {
    public static boolean canWork(float rpm, boolean overstressed, float minimum) {
        return Float.isFinite(rpm) && !overstressed && Math.abs(rpm) > 0 && Math.abs(rpm) >= minimum;
    }
    public static boolean canWork(float rpm, boolean overstressed, float minimum, int maximum) {
        return canWork(rpm, overstressed, minimum) && Math.abs(rpm) <= maximum;
    }
    public static int mixerDuration(int baseTicks, float rpm) {
        if (!canWork(rpm, false, 0) || baseTicks < 1) return 0;
        return (int)Math.min(Integer.MAX_VALUE, Math.max(1, Math.floor(baseTicks * 256d / Math.abs(rpm))));
    }
    public static int lavaBurst(int pumps, float rpm, boolean overstressed) {
        if (pumps < 1 || pumps > 3 || !canWork(rpm, overstressed, 128)) return 0;
        return Math.round(pumps * 500 * Math.abs(rpm) / 256);
    }
    private MachineKinetics() {}
}
