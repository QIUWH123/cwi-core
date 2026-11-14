package net.quiwh.cwi.machine;

import net.minecraft.nbt.CompoundTag;
import net.quiwh.cwi.machine.recipe.EntrySettings;

/** Persisted operation clocks and decisions. A failed step retains its target and random seeds. */
public final class VesselOperation {
    private final long seed;
    private double completed, pending = -1;
    private int ticks;
    private boolean paid;
    final CompoundTag toolRolls = new CompoundTag();
    public VesselOperation(long seed) { this.seed = seed; }
    public double completed() { return completed; }
    public int ticks() { return ticks; }
    public boolean paid() { return paid; }
    public double target() { return pending; }
    public void prepare(int actualDuration) {
        if (pending < 0) { pending = Math.min(1, completed + 1d / Math.max(1, actualDuration)); if (1 - pending < 1e-10) pending = 1; }
    }
    public void prepareAdvance(double advance) {
        if (!Double.isFinite(advance) || advance <= 0) return;
        if (pending < 0) { pending = Math.min(1, completed + advance); if (1-pending < 1e-10) pending=1; }
    }
    public boolean finishing() { return pending == 1; }
    public int events(EntrySettings settings, int baseDuration) {
        if (!settings.periodic()) return 0;
        if (settings.clock() == EntrySettings.Clock.WORKING) return (ticks + 1) % settings.interval() == 0 ? 1 : 0;
        return thresholds(pending, baseDuration, settings.interval()) - thresholds(completed, baseDuration, settings.interval());
    }
    private static int thresholds(double fraction, int duration, int interval) {
        return (int) Math.floor(Math.min(duration, fraction * duration + 1e-8) / interval);
    }
    public net.minecraft.util.RandomSource random(long channel) {
        // A completion channel is independent from the working clock; pending periodic decisions use the saved tick index.
        long value = seed ^ channel * 0x9E3779B97F4A7C15L;
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        return net.minecraft.util.RandomSource.create(value ^ value >>> 27);
    }
    public void commit(boolean upfront) { completed = pending; pending = -1; ticks++; paid |= upfront; toolRolls.getAllKeys().clear(); }
    public CompoundTag save() {
        var tag = new CompoundTag(); tag.putLong("Seed", seed); tag.putDouble("Completed", completed);
        tag.putDouble("Pending", pending); tag.putInt("Ticks", ticks); tag.putBoolean("Paid", paid); tag.put("ToolRolls", toolRolls.copy()); return tag;
    }
    public static VesselOperation load(CompoundTag tag) {
        var value = new VesselOperation(tag.getLong("Seed"));
        double completed = tag.getDouble("Completed");
        value.completed = Double.isFinite(completed) ? Math.clamp(completed, 0, 1) : 0;
        double pending = tag.getDouble("Pending");
        value.pending = Double.isFinite(pending) && pending >= value.completed && pending <= 1 ? pending : -1;
        value.ticks = Math.max(0, tag.getInt("Ticks")); value.paid = tag.getBoolean("Paid");
        value.toolRolls.merge(tag.getCompound("ToolRolls")); return value;
    }
}
