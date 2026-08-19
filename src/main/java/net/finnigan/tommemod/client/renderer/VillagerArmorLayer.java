package net.finnigan.tommemod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.client.model.WarriorVillagerModel;
import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableArmorItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws the Warrior's armor on the villager-shaped armor meshes in {@link VillagerArmorModel},
 * using this mod's own {@code villager_*} armor sheets.
 *
 * <p>This is vanilla's HumanoidArmorLayer written out rather than subclassed: the one thing that
 * needs changing is where the texture comes from, and vanilla builds that path in a private method.
 * A material with no villager sheet of its own - a turtle helmet, or anything from another mod -
 * falls through to the vanilla texture, which still lines up because the uv layout is unchanged.
 *
 * <p>Armor trims are deliberately not drawn. Their art is cut for the player rig, so on this mesh a
 * trim lands in the wrong place; there is no villager trim atlas to point at instead.
 */
public class VillagerArmorLayer extends RenderLayer<WarriorVillagerEntity, WarriorVillagerModel> {

    private final VillagerArmorModelPair models;
    private final ResourceManager resourceManager;

    /** Resolved per material once - the resource lookup in {@link #armorTexture} is not frame work. */
    private final Map<String, ResourceLocation> textures = new HashMap<>();

    /** Just a holder, so the renderer can bake both meshes and hand them over together. */
    public record VillagerArmorModelPair(HumanoidModel<WarriorVillagerEntity> inner,
                                         HumanoidModel<WarriorVillagerEntity> outer) {
    }

    public VillagerArmorLayer(RenderLayerParent<WarriorVillagerEntity, WarriorVillagerModel> parent,
                              VillagerArmorModelPair models, ResourceManager resourceManager) {
        super(parent);
        this.models = models;
        this.resourceManager = resourceManager;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       WarriorVillagerEntity entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        renderPiece(poseStack, bufferSource, entity, EquipmentSlot.CHEST, packedLight);
        renderPiece(poseStack, bufferSource, entity, EquipmentSlot.LEGS, packedLight);
        renderPiece(poseStack, bufferSource, entity, EquipmentSlot.FEET, packedLight);
        renderPiece(poseStack, bufferSource, entity, EquipmentSlot.HEAD, packedLight);
    }

    private void renderPiece(PoseStack poseStack, MultiBufferSource bufferSource,
                             WarriorVillagerEntity entity, EquipmentSlot slot, int packedLight) {
        ItemStack stack = entity.getItemBySlot(slot);
        if (!(stack.getItem() instanceof ArmorItem armor) || armor.getEquipmentSlot() != slot) return;

        boolean inner = slot == EquipmentSlot.LEGS;
        HumanoidModel<WarriorVillagerEntity> model = inner ? models.inner() : models.outer();

        getParentModel().copyPropertiesTo(model);
        setPartVisibility(model, slot);

        boolean glint = stack.hasFoil();
        if (armor instanceof DyeableArmorItem dyeable) {
            int colour = dyeable.getColor(stack);
            float red = (colour >> 16 & 255) / 255.0F;
            float green = (colour >> 8 & 255) / 255.0F;
            float blue = (colour & 255) / 255.0F;
            renderModel(poseStack, bufferSource, packedLight, glint, model, red, green, blue,
                    armorTexture(armor, inner, null));
            // The dye tints the base sheet only; the overlay on top of it is the undyed stitching.
            renderModel(poseStack, bufferSource, packedLight, glint, model, 1.0F, 1.0F, 1.0F,
                    armorTexture(armor, inner, "overlay"));
        } else {
            renderModel(poseStack, bufferSource, packedLight, glint, model, 1.0F, 1.0F, 1.0F,
                    armorTexture(armor, inner, null));
        }
    }

    private void renderModel(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                             boolean glint, HumanoidModel<WarriorVillagerEntity> model,
                             float red, float green, float blue, ResourceLocation texture) {
        VertexConsumer buffer = ItemRenderer.getArmorFoilBuffer(bufferSource,
                RenderType.armorCutoutNoCull(texture), false, glint);
        model.renderToBuffer(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY,
                red, green, blue, 1.0F);
    }

    /** Vanilla's mapping of slot to visible parts, unchanged. */
    private static void setPartVisibility(HumanoidModel<WarriorVillagerEntity> model, EquipmentSlot slot) {
        model.setAllVisible(false);
        switch (slot) {
            case HEAD -> {
                model.head.visible = true;
                model.hat.visible = true;
            }
            case CHEST -> {
                model.body.visible = true;
                model.rightArm.visible = true;
                model.leftArm.visible = true;
            }
            case LEGS -> {
                model.body.visible = true;
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            case FEET -> {
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            default -> {
            }
        }
    }

    private ResourceLocation armorTexture(ArmorItem armor, boolean inner, @Nullable String suffix) {
        String material = armor.getMaterial().getName();
        // Modded materials are namespaced ("modid:foo"); only the name half belongs in a file name.
        String name = material.substring(material.indexOf(':') + 1);
        String file = name + "_layer_" + (inner ? 2 : 1) + (suffix == null ? "" : "_" + suffix) + ".png";

        return textures.computeIfAbsent(file, key -> {
            ResourceLocation villager =
                    new ResourceLocation(TommeMod.MOD_ID, "textures/models/armor/villager_" + key);
            return resourceManager.getResource(villager).isPresent()
                    ? villager
                    : new ResourceLocation("textures/models/armor/" + key);
        });
    }
}
