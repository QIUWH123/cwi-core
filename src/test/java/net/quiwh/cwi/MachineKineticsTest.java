package net.quiwh.cwi;

import net.quiwh.cwi.machine.MachineKinetics;

/** Standalone regression checks for stopped/overstressed and reverse-direction processing. */
public final class MachineKineticsTest {
    public static void main(String[] args) {
        check(!MachineKinetics.canWork(0, false, 0), "zero RPM never works");
        check(!MachineKinetics.canWork(128, true, 64), "overstress stops processing");
        check(!MachineKinetics.canWork(63.9f, false, 64), "incubation threshold");
        check(MachineKinetics.canWork(-64, false, 64), "reverse incubation");
        check(!MachineKinetics.canWork(257, false, 1, 256), "configured overspeed cannot complete a recipe");
        check(MachineKinetics.canWork(512, false, 1, 512), "configured high speed works");
        check(MachineKinetics.mixerDuration(100, 512) == 50, "high-speed duration keeps256RPM reference");
        check(!MachineKinetics.canWork(Float.NaN, false, 1), "invalid speed cannot process");
        check(MachineKinetics.mixerDuration(20, 256) == 20, "full speed duration");
        check(MachineKinetics.mixerDuration(20, -128) == 40, "reverse speed duration");
        check(MachineKinetics.mixerDuration(20, 32) == 160, "slow mixer duration");
        check(MachineKinetics.mixerDuration(20, 0) == 0, "stopped mixer has no batch duration");
        check(MachineKinetics.mixerDuration(Integer.MAX_VALUE, 1) == Integer.MAX_VALUE, "duration overflow saturates");
        check(MachineKinetics.lavaBurst(1, 128, false) == 250, "one pump half-speed burst");
        check(MachineKinetics.lavaBurst(3, -256, false) == 1500, "three reverse pumps full-speed burst");
        check(MachineKinetics.lavaBurst(3, 127, false) == 0, "extractor threshold");
        check(MachineKinetics.lavaBurst(4, 256, false) == 0, "invalid structure height");
        check(MachineKinetics.lavaBurst(2, 256, true) == 0, "overstressed extractor");
        System.out.println("MachineKineticsTest: 18 assertions passed");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
