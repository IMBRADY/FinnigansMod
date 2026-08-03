package net.finnigan.tommemod.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Restyles a Unique-tagged item's name as bold text under a cyan-to-purple gradient that flows along
 * it: every letter takes the colour of the letter before it, one step at a time, so the whole ramp
 * marches through the name and returns to where it started every {@link #CYCLE_MILLIS}.
 * The ramp goes out to the end colour and back again (see {@link #rampColor}) so it meets itself
 * where the rotation wraps.
 * <p>
 * The ramp is a fixed {@link #GRADIENT_PERIOD} letters long for every item rather than being
 * stretched to fit each name. Sizing it per name gave each unique its own step count and its own
 * per-letter speed - a three-letter name raced through three colours while a sixteen-letter one
 * crawled through sixteen. A fixed period makes every unique, present and future, show the same
 * colour spacing moving at the same rate; a name shorter than the period simply shows a window onto
 * that one shared gradient.
 */
public final class UniqueNameStyler {

    private static final int GRADIENT_START = 0x2ECEFF;
    private static final int GRADIENT_END = 0xBD2EFF;

    /**
     * Letters the ramp spans before repeating. Set to the length of "Custodire Gladio", the name this
     * look was tuned against - matching it is what keeps every other unique identical to that one.
     */
    private static final int GRADIENT_PERIOD = 16;

    /** Time for a letter's colour to travel the whole ramp and return to its starting hue. */
    private static final long CYCLE_MILLIS = 2000L;

    private UniqueNameStyler() {
    }

    /**
     * @param name the item's plain name; its own style (and any siblings) are discarded, since the
     *             point is to recolour every letter individually.
     */
    public static Component style(Component name) {
        String text = name.getString();
        if (text.isEmpty()) return name;

        // Shift by whole letters rather than smoothly: the spec is a discrete "each letter adopts the
        // previous letter's colour", so one full cycle is exactly GRADIENT_PERIOD steps.
        long step = (System.currentTimeMillis() * GRADIENT_PERIOD / CYCLE_MILLIS) % GRADIENT_PERIOD;

        MutableComponent styled = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            int gradientIndex = (int) Math.floorMod(i - step, GRADIENT_PERIOD);

            styled.append(Component.literal(String.valueOf(text.charAt(i)))
                    .setStyle(Style.EMPTY
                            .withColor(TextColor.fromRgb(rampColor(gradientIndex)))
                            .applyFormat(ChatFormatting.BOLD)));
        }
        return styled;
    }

    /**
     * The ramp runs start -> end -> start across the period rather than start -> end, so the colour at
     * the last position sits one ordinary step away from the colour at the first. That join is what
     * makes the rotation read as one continuous flow instead of snapping from purple back to cyan
     * every time it wraps. The turning point is a whole letter, so both named colours always land on
     * a real position.
     */
    private static int rampColor(int index) {
        int peak = GRADIENT_PERIOD / 2; // the position that shows GRADIENT_END
        float t = index <= peak
                ? (float) index / peak
                : (float) (GRADIENT_PERIOD - index) / (GRADIENT_PERIOD - peak);
        return lerpColor(t);
    }

    private static int lerpColor(float t) {
        int r = lerpChannel(t, 16);
        int g = lerpChannel(t, 8);
        int b = lerpChannel(t, 0);
        return (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(float t, int shift) {
        int from = (GRADIENT_START >> shift) & 0xFF;
        int to = (GRADIENT_END >> shift) & 0xFF;
        return Math.round(from + (to - from) * t);
    }
}
