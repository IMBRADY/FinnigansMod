package net.finnigan.tommemod.client.light;

/** Legacy colour value helper retained for compatibility; registrations now use explicit parameters. */
public record DynamicLightColor(int rgb, boolean useVanillaLightmap) {
    public static final DynamicLightColor WHITE = rgb(0xFFFFFF);

    public static DynamicLightColor rgb(int rgb) {
        return new DynamicLightColor(rgb & 0xFFFFFF, false);
    }

    public static DynamicLightColor vanillaLightmap() {
        return new DynamicLightColor(0, true);
    }

    int shaderRgb() { return rgb; }
}
