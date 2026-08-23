package net.finnigan.tommemod.client.light;

import net.finnigan.tommemod.TommeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;

/** Uploads dynamic-light source data immediately before terrain is rendered. */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, value = Dist.CLIENT)
public final class DynamicLightShaderHandler {
    private DynamicLightShaderHandler() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        Vec3 cameraPosition = minecraft.gameRenderer.getMainCamera().getPosition();
        List<DynamicLightManager.Source> sources = DynamicLightManager.getSources(event.getPartialTick()).stream()
                .sorted(Comparator.comparingDouble(source -> source.position().distanceToSqr(cameraPosition)))
                .limit(DynamicLightManagerInit.MAX_LIGHT_SOURCES)
                .toList();

        upload(GameRenderer.getRendertypeSolidShader(), sources, cameraPosition);
        upload(GameRenderer.getRendertypeCutoutMippedShader(), sources, cameraPosition);
        upload(GameRenderer.getRendertypeCutoutShader(), sources, cameraPosition);
        upload(GameRenderer.getRendertypeTranslucentShader(), sources, cameraPosition);
        upload(GameRenderer.getRendertypeEntityCutoutNoCullShader(), sources, cameraPosition);
    }

    private static void upload(ShaderInstance shader, List<DynamicLightManager.Source> sources, Vec3 cameraPosition) {
        if (shader == null) return;
        for (int index = 0; index < DynamicLightManagerInit.MAX_LIGHT_SOURCES; index++) {
            DynamicLightManager.Source source = index < sources.size() ? sources.get(index) : null;
            var uniform = shader.getUniform("DynamicLight" + index);
            var colorUniform = shader.getUniform("DynamicLightColor" + index);
            if (uniform == null) continue;
            if (source == null) {
                uniform.set(0.0F, 0.0F, 0.0F, 0.0F);
                if (colorUniform != null) colorUniform.set(1.0F, 1.0F, 1.0F);
            } else {
                // Chunk vertices use coordinates relative to the active camera. Uploading absolute
                // world positions makes every source appear far outside the shader's light radius.
                Vec3 position = source.position().subtract(cameraPosition);
                // The sign is available to the shader because range uses abs(w): negative selects
                // the optional fixed-RGB bleaching mode, while colour.y selects the vanilla map.
                float signedLevel = source.preserveMaterialHue() ? source.level() : -source.level();
                uniform.set((float) position.x, (float) position.y, (float) position.z, signedLevel);
                if (colorUniform != null) {
                    Vec3 color = source.color();
                    colorUniform.set((float) color.x, source.useVanillaLightmap() ? -1.0F : (float) color.y, (float) color.z);
                }
            }
        }
    }
}
