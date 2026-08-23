package net.finnigan.tommemod.client.light;

/** Shared client-side configuration for the dynamic-light system. */
public final class DynamicLightManagerInit {
    /**
     * Maximum number of nearby light sources submitted to each render pass.
     * Keep this synchronized with {@code MAX_DYNAMIC_LIGHTS} in
     * {@code assets/minecraft/shaders/include/dynamic_light.glsl}.
     */
    public static final int MAX_LIGHT_SOURCES = 20;

    private DynamicLightManagerInit() {}
}
