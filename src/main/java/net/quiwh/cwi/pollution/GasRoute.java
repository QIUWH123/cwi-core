package net.quiwh.cwi.pollution;

import java.util.Locale;

/** Where a cloud contacts an entity; effects are configured separately. */
public enum GasRoute {
    INHALATION, EYES, SKIN;
    public static GasRoute parse(String value) {
        try { return valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Gas route must be inhalation, eyes or skin: " + value); }
    }
}
