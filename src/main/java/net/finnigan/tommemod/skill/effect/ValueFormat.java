package net.finnigan.tommemod.skill.effect;

import java.util.Locale;

/** How an effect's number is written in a tooltip. */
public enum ValueFormat {
    /** 0.05 reads as "+5%". For anything multiplicative. */
    PERCENT,
    /** 2.0 reads as "+2". For flat additions on whole-number scales. */
    FLAT,
    /** 0.25 reads as "+0.25". For flat additions too small for rounding to survive. */
    DECIMAL,
    /**
     * 0.15 reads as "-15%". For the bonuses whose whole job is to take something away.
     *
     * A key like {@code fall_damage_reduction} stores the fraction it removes, so the number is
     * positive while the effect is a subtraction, and PERCENT would write it up as "+15% fall damage"
     * - the exact opposite of what the node does. The value is left alone and only the sign it is
     * announced with is flipped, so nothing downstream of the tooltip changes.
     */
    PERCENT_REDUCTION;

    public String format(double value) {
        return switch (this) {
            case PERCENT -> sign(value) + trim(value * 100.0, 2) + "%";
            case FLAT -> sign(value) + trim(value, 1);
            case DECIMAL -> sign(value) + trim(value, 2);
            case PERCENT_REDUCTION -> sign(-value) + trim(value * 100.0, 2) + "%";
        };
    }

    private static String sign(double value) {
        return value >= 0 ? "+" : "-";
    }

    /** Absolute value with at most {@code decimals} places and no trailing zeros: 12.50 -> "12.5". */
    private static String trim(double value, int decimals) {
        String text = String.format(Locale.ROOT, "%." + decimals + "f", Math.abs(value));
        if (text.indexOf('.') < 0) return text;
        text = text.replaceAll("0+$", "");
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    public static ValueFormat byName(String name, ValueFormat fallback) {
        for (ValueFormat format : values()) {
            if (format.name().toLowerCase(Locale.ROOT).equals(name)) return format;
        }
        return fallback;
    }
}
