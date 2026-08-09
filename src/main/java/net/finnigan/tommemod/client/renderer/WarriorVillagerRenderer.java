package net.finnigan.tommemod.client.renderer;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.client.ModModelLayers;
import net.finnigan.tommemod.client.model.WarriorVillagerModel;
import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Stays on vanilla's humanoid rig (real posable arms) instead of this mod's usual GeckoLib approach or
 * the Elder Villager's crossed-arm VillagerModel, since the Warrior needs to visibly hold a weapon
 * like a player. The mesh itself is villager-shaped - see WarriorVillagerModel.
 *
 * Skinned by the biome variant of the Villager it was conscripted from, mirroring how vanilla
 * villagers are skinned. Variants with no art of their own fall back to the plain warrior_villager
 * texture, so shipping (say) a jungle skin later needs nothing but the .png.
 */
public class WarriorVillagerRenderer extends MobRenderer<WarriorVillagerEntity, WarriorVillagerModel> {

    private static final ResourceLocation DEFAULT_TEXTURE =
            new ResourceLocation(TommeMod.MOD_ID, "textures/entity/warrior_villager/warrior_villager.png");

    /** Vanilla's registry name for the snowy variant is "snow", but the art is named for the biome. */
    private static final Map<String, String> TEXTURE_SUFFIX_ALIASES = Map.of("snow", "snowy");

    /** Resolved per variant once - the resource lookup below is far too expensive to redo every frame. */
    private static final Map<String, ResourceLocation> RESOLVED = new HashMap<>();

    public WarriorVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new WarriorVillagerModel(context.bakeLayer(ModModelLayers.WARRIOR_VILLAGER)), 0.5F);
        // ZOMBIE_VILLAGER armor layers, not ZOMBIE: those are the meshes vanilla builds for a 10-tall
        // villager head, so a helmet caps the top of the skull instead of sinking two pixels into it.
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE_VILLAGER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE_VILLAGER_OUTER_ARMOR)),
                context.getModelManager()));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(WarriorVillagerEntity entity) {
        return RESOLVED.computeIfAbsent(entity.getVillagerType(), WarriorVillagerRenderer::resolve);
    }

    private static ResourceLocation resolve(String villagerType) {
        String suffix = TEXTURE_SUFFIX_ALIASES.getOrDefault(villagerType, villagerType);
        ResourceLocation candidate = new ResourceLocation(TommeMod.MOD_ID,
                "textures/entity/warrior_villager/warrior_villager_" + suffix + ".png");
        return Minecraft.getInstance().getResourceManager().getResource(candidate).isPresent()
                ? candidate
                : DEFAULT_TEXTURE;
    }
}
