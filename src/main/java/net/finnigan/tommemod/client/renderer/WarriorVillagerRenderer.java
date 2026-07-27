package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Reuses vanilla's humanoid rig (real posable arms) instead of this mod's usual GeckoLib approach or
 * the Elder Villager's crossed-arm VillagerModel, since the Warrior needs to visibly hold a weapon
 * like a player. Placeholder texture until real art exists.
 */
public class WarriorVillagerRenderer extends MobRenderer<WarriorVillagerEntity, HumanoidModel<WarriorVillagerEntity>> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TommeMod.MOD_ID, "textures/entity/warrior_villager/warrior_villager.png");

    public WarriorVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)),
                context.getModelManager()));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(WarriorVillagerEntity entity) {
        return TEXTURE;
    }
}
