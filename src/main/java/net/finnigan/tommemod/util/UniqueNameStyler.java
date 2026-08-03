package net.finnigan.tommemod.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Restyles a Unique-tagged item's name as bold text under a cyan-to-purple gradient that flows along
 * it: every letter takes the colour of the letter before it, one step per frame, so the whole ramp
 * marches through the name and returns to where it started every {@link #CYCLE_MILLIS}.
 * The gradient is laid out across the name once, and animation is purely an offset into that ramp -
 * so a name's colours are the same set at every moment, just rotated.
 */
public final class UniqueNameStyler {

    private static final int GRADIENT_START = 0x2ECEFF;
    private static final int GRADIENT_END = 0xBD2EFF;

    /** Time for a letter's colour to travel the whole name and return to its starting hue. */
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

        int length = text.length();
        // Shift by whole letters rather than smoothly: the spec is a discrete "each letter adopts the
        // previous letter's colour", so one full cycle is exactly `length` steps.
        long step = (System.currentTimeMillis() * length / CYCLE_MILLIS) % length;

        MutableComponent styled = Component.empty();
        for (int i = 0; i < length; i++) {
            int gradientIndex = (int) ((i - step + length) % length);
            float t = length == 1 ? 0F : (float) gradientIndex / (length - 1);

            styled.append(Component.literal(String.valueOf(text.charAt(i)))
                    .setStyle(Style.EMPTY
                            .withColor(TextColor.fromRgb(lerpColor(t)))
                            .applyFormat(ChatFormatting.BOLD)));
        }
        return styled;
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
