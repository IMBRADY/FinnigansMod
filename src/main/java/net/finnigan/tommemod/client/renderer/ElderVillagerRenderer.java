package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.ElderVillagerEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Reuses vanilla's own villager rig/model instead of this mod's usual GeckoLib approach, since the
 * Elder Villager needs the exact humanoid-villager shape rather than a new creature.
 *
 * <p>Skinned the way vanilla skins a villager: a plain body here, with the biome variant and the
 * Elder's own robes painted over it by {@link ElderVillagerOverlayLayer}.
 */
public class ElderVillagerRenderer extends MobRenderer<ElderVillagerEntity, VillagerModel<ElderVillagerEntity>> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TommeMod.MOD_ID, "textures/entity/elder_villager/villager.png");

    public ElderVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
        this.addLayer(new ElderVillagerOverlayLayer(this, context.getResourceManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(ElderVillagerEntity entity) {
        return TEXTURE;
    }
}
