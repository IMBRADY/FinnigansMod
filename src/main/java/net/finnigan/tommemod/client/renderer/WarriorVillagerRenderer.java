package net.finnigan.tommemod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.client.ModModelLayers;
import net.finnigan.tommemod.client.model.WarriorVillagerModel;
import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;

/**
 * Stays on vanilla's humanoid rig (real posable arms) instead of this mod's usual GeckoLib approach or
 * the Elder Villager's crossed-arm VillagerModel, since the Warrior needs to visibly hold a weapon
 * like a player. The mesh itself is villager-shaped - see WarriorVillagerModel.
 *
 * Skinned by climate rather than by biome. There are three sheets - cold, temperate and warm - and
 * every vanilla villager type maps onto one of them, so a taiga Warrior and a snowy one share a coat
 * instead of each needing art of its own. Anything unrecognised (a villager type from another mod)
 * falls back to the plain warrior_villager texture.
 */
public class WarriorVillagerRenderer extends MobRenderer<WarriorVillagerEntity, WarriorVillagerModel> {

    private static final ResourceLocation DEFAULT_TEXTURE =
            new ResourceLocation(TommeMod.MOD_ID, "textures/entity/warrior_villager/warrior_villager.png");

    private static final Map<String, String> CLIMATE_BY_VILLAGER_TYPE = Map.of(
            "snow", "cold",
            "taiga", "cold",
            "plains", "temperate",
            "swamp", "temperate",
            "desert", "warm",
            "savanna", "warm",
            "jungle", "warm");

    /** Resolved per variant once - building a ResourceLocation every frame is wasted work. */
    private static final Map<String, ResourceLocation> RESOLVED = new HashMap<>();

    public WarriorVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new WarriorVillagerModel(context.bakeLayer(ModModelLayers.WARRIOR_VILLAGER)), 0.5F);
        this.addLayer(new VillagerArmorLayer(this,
                new VillagerArmorLayer.VillagerArmorModelPair(
                        new HumanoidModel<>(context.bakeLayer(ModModelLayers.VILLAGER_ARMOR_INNER)),
                        new HumanoidModel<>(context.bakeLayer(ModModelLayers.VILLAGER_ARMOR_OUTER))),
                context.getResourceManager()));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    /**
     * A helmet and the villager's own headwear cannot both be on. The headwear is the head's overlay
     * layer, inflated 0.51 clear of a 10-tall skull; VillagerArmorModel's helmet is a shorter box
     * inflated 0.75, which puts its crown 0.26 <em>inside</em> that - so drawing both leaves a
     * Warrior wearing its hood over the top of its helmet. Taking the hood off to put the helmet on
     * is the answer that needs no geometry to be fudged. ModelPart#copyFrom doesn't carry visibility,
     * so setting it here survives setupAnim.
     */
    @Override
    public void render(WarriorVillagerEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        this.getModel().hat.visible = entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(WarriorVillagerEntity entity) {
        return RESOLVED.computeIfAbsent(entity.getVillagerType(), WarriorVillagerRenderer::resolve);
    }

    private static ResourceLocation resolve(String villagerType) {
        String climate = CLIMATE_BY_VILLAGER_TYPE.get(villagerType);
        return climate == null
                ? DEFAULT_TEXTURE
                : new ResourceLocation(TommeMod.MOD_ID,
                        "textures/entity/warrior_villager/warrior_villager_" + climate + ".png");
    }
}
