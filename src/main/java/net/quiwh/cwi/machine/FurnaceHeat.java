package net.quiwh.cwi.machine;

/** Temperature is the persisted source of truth; stage and speed are always derived. */
public final class FurnaceHeat {
    public static double target(int heaterPoints, int armor) {
        double heater = 2400 * Math.pow(Math.clamp(heaterPoints, 0, 15) / 15.0, .75);
        double ceiling = 1200 + 1200 * (1 - Math.exp(-Math.max(0, armor) / 40.0)) / (1 - Math.exp(-126 / 40.0));
        return Math.min(2400, Math.min(heater, ceiling));
    }

    public static double approach(double current, int heaterPoints, int armor) {
        current = sanitize(current);
        double target = target(heaterPoints, armor);
        return Math.abs(current - target) < 1.5 ? target : current + .05 * (target - current);
    }

    public static int stage(double temperature) {
        return temperature >= 1900 ? 3 : temperature >= 1200 ? 2 : temperature >= 300 ? 1 : 0;
    }

    public static double durationFactor(double temperature) { return 1 - .75 * sanitize(temperature) / 2400; }
    public static double sanitize(double temperature) { return Double.isFinite(temperature) ? Math.clamp(temperature, 0, 2400) : 0; }
    private FurnaceHeat() {}
}
