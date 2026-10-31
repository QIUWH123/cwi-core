package net.quiwh.cwi;

import net.quiwh.cwi.shadow.ShadowDropRules;

/** A separate JVM checks the common render-state helper without either optional integration. */
public final class ShadowRulesWithoutModsTest {
    public static void main(String[] args) throws Exception {
        for (var absent : new String[]{"dev.latvian.mods.kubejs.KubeJS", "net.terrunic.shadowdrop.ShadowDrop"}) {
            try {
                Class.forName(absent);
                throw new AssertionError("Fixture unexpectedly contains " + absent);
            } catch (ClassNotFoundException expected) {}
        }
        if (ShadowDropRules.renderColor() != null) throw new AssertionError("Unconfigured rendering must inherit native color");
        ShadowDropRules.defaults(new ShadowDropRules.Style(128, 0xAABBCC));
        if (ShadowDropRules.renderColor() != 0xAABBCC || ShadowDropRules.alpha(0.5) != 128)
            throw new AssertionError("Common styles must work without either optional mod");
        ShadowDropRules.defaults(ShadowDropRules.Style.INHERIT);
        System.out.println("Shadow rules: class loading and defaults passed without KubeJS, Rhino or Shadow Drop");
    }
}
