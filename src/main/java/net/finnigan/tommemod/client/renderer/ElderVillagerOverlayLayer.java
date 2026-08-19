package net.finnigan.tommemod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.ElderVillagerEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.metadata.animation.VillagerMetaDataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The Elder's biome skin and robes, painted over the plain body in
 * {@code textures/entity/elder_villager/villager.png}.
 *
 * <p>This is vanilla's VillagerProfessionLayer minus the profession-level badge, rather than that
 * class itself: vanilla derives every path from a registry key, so the biome layer would resolve
 * under {@code minecraft:} (villager types are vanilla objects) and the badge under a
 * {@code profession_level/} folder this mod has no art for, which renders as missing-texture pink.
 * Written out here, all three paths stay in {@code tommemod:} and there is no badge to go missing.
 *
 * <p>The hat rule is vanilla's and matters: a type whose {@code .mcmeta} declares a full hat (desert
 * and snow both do) already covers the head, so the robe's own hat is suppressed over it rather than
 * drawn twice.
 */
public class ElderVillagerOverlayLayer
        extends RenderLayer<ElderVillagerEntity, VillagerModel<ElderVillagerEntity>> {

    private static final ResourceLocation OVERLAY =
            new ResourceLocation(TommeMod.MOD_ID, "textures/entity/elder_villager/overlay.png");

    /** Resolved per variant once - the resource lookup below is far too expensive to redo every frame. */
    private final Map<String, ResourceLocation> typeTextures = new HashMap<>();
    private final Map<ResourceLocation, VillagerMetaDataSection.Hat> hats = new HashMap<>();

    private final ResourceManager resourceManager;

    public ElderVillagerOverlayLayer(
            RenderLayerParent<ElderVillagerEntity, VillagerModel<ElderVillagerEntity>> parent,
            ResourceManager resourceManager) {
        super(parent);
        this.resourceManager = resourceManager;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       ElderVillagerEntity entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) return;

        ResourceLocation type = typeTextures.computeIfAbsent(entity.getVillagerType(),
                path -> new ResourceLocation(TommeMod.MOD_ID,
                        "textures/entity/elder_villager/type/" + path + ".png"));

        // Vanilla's rule, unchanged: the hat is only suppressed when the robes' own hat is a partial
        // one that would clash with a type already covering the whole head (desert and snow both do).
        VillagerMetaDataSection.Hat overlayHat = hat(OVERLAY);
        VillagerModel<ElderVillagerEntity> model = getParentModel();
        model.hatVisible(overlayHat == VillagerMetaDataSection.Hat.NONE
                || (overlayHat == VillagerMetaDataSection.Hat.PARTIAL
                        && hat(type) != VillagerMetaDataSection.Hat.FULL));
        renderColoredCutoutModel(model, type, poseStack, bufferSource, packedLight, entity, 1.0F, 1.0F, 1.0F);

        model.hatVisible(true);
        renderColoredCutoutModel(model, OVERLAY, poseStack, bufferSource, packedLight, entity, 1.0F, 1.0F, 1.0F);
    }

    /** Missing or unreadable metadata means no hat, which is also what vanilla assumes. */
    private VillagerMetaDataSection.Hat hat(ResourceLocation texture) {
        return hats.computeIfAbsent(texture, location -> resourceManager.getResource(location)
                .flatMap(ElderVillagerOverlayLayer::readHatSection)
                .map(VillagerMetaDataSection::getHat)
                .orElse(VillagerMetaDataSection.Hat.NONE));
    }

    private static Optional<VillagerMetaDataSection> readHatSection(Resource resource) {
        try {
            return resource.metadata().getSection(VillagerMetaDataSection.SERIALIZER);
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
