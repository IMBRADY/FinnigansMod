package net.finnigan.tommemod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.block.ModBlocks;
import net.finnigan.tommemod.client.ConstructionRotationClientState;
import net.minecraft.core.Direction;
import net.finnigan.tommemod.village.BuildingStructures;
import net.finnigan.tommemod.village.BuildingType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Renders a translucent "ghost" preview of the building footprint that would be placed while the
 * player is holding a construction-banner item, so they can see where it'll land before committing.
 * Purely a client-side visual aid - no gameplay/placement logic lives here, and this class never
 * touches world state.
 * <p>
 * The positioning math below (STRUCTURE_OFFSET, single-heightmap-sample baseY) intentionally
 * duplicates the real placement pipeline in {@code ConstructionSiteBlockEntity#initialize} rather
 * than exposing new public API on that shared class, so this feature stays a fully self-contained
 * addition (see class-level task scoping). If that pipeline's math ever changes, this preview will
 * need updating to match.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ConstructionPreviewRenderer {

    /** Mirrors {@code ConstructionSiteBlockEntity.STRUCTURE_OFFSET} - the real structure is placed
     * two blocks diagonally away from wherever the banner block itself ends up. */
    private static final BlockPos STRUCTURE_OFFSET = new BlockPos(2, 0, 2);

    private static final int TINT_R = 80;
    private static final int TINT_G = 220;
    private static final int TINT_B = 120;
    private static final int TINT_A = 90;

    private ConstructionPreviewRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return;

        ItemStack heldStack = heldBannerStack(player);
        if (heldStack == null) return;

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) return;

        BuildingType type = buildingTypeFrom(heldStack);
        Direction facing = ConstructionRotationClientState.getCurrentFacing();
        List<Map.Entry<BlockPos, BlockState>> structure = BuildingStructures.forType(type, facing);
        if (structure.isEmpty()) return;

        BlockPos placePos = wouldBePlacePos(level, blockHit);
        BlockPos structureOrigin = placePos.offset(STRUCTURE_OFFSET);

        // Single representative sample rather than ConstructionSiteBlockEntity#initialize's full
        // min/max variance scan across the whole footprint - fine for a preview.
        int groundHeight = level.getHeight(Heightmap.Types.WORLD_SURFACE, structureOrigin.getX(), structureOrigin.getZ()) - 1;
        int baseY = groundHeight + 1;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        for (Map.Entry<BlockPos, BlockState> entry : structure) {
            if (entry.getValue().isAir()) continue; // door gaps etc. - nothing to preview there
            BlockPos rel = entry.getKey();
            double x = structureOrigin.getX() + rel.getX();
            double y = baseY + rel.getY();
            double z = structureOrigin.getZ() + rel.getZ();
            addBox(poseStack, consumer, x, y, z);
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.debugQuads());
    }

    @Nullable
    private static ItemStack heldBannerStack(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (isBanner(main)) return main;
        ItemStack off = player.getOffhandItem();
        if (isBanner(off)) return off;
        return null;
    }

    private static boolean isBanner(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == ModBlocks.CONSTRUCTION_BANNER.get().asItem();
    }

    /** Reads the pending BuildingType off the held stack's NBT, same tag name ConstructionBannerBlock
     * reads at placement time. Falls back to HOUSE if unset (e.g. a stack given via /give that never
     * went through the normal RequestBuildingBannerPacket flow) so the preview never silently renders
     * nothing. */
    private static BuildingType buildingTypeFrom(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("BuildingType")) {
            try {
                return BuildingType.valueOf(tag.getString("BuildingType"));
            } catch (IllegalArgumentException ignored) {
                // unrecognized value - fall through to default
            }
        }
        return BuildingType.HOUSE;
    }

    /** Approximates vanilla's block-placement targeting: if the block being looked at can be
     * replaced (tall grass, snow layers, etc.), the structure would land on that same position;
     * otherwise it'd be offset one step along the clicked face - matching normal BlockItem placement
     * behavior closely enough for a preview. */
    private static BlockPos wouldBePlacePos(ClientLevel level, BlockHitResult hit) {
        BlockPos hitPos = hit.getBlockPos();
        BlockState hitState = level.getBlockState(hitPos);
        return hitState.canBeReplaced() ? hitPos : hitPos.relative(hit.getDirection());
    }

    /** A cheap solid-tinted cube spanning one full block - 5 faces (top + 4 sides, bottom skipped
     * since it's rarely visible for a ground-level preview), not the actual block model/texture. */
    private static void addBox(PoseStack poseStack, VertexConsumer consumer, double x, double y, double z) {
        float minX = (float) x;
        float minY = (float) y;
        float minZ = (float) z;
        float maxX = minX + 1F;
        float maxY = minY + 1F;
        float maxZ = minZ + 1F;

        Matrix4f matrix = poseStack.last().pose();

        // Top (+Y)
        quad(consumer, matrix,
                minX, maxY, minZ,
                minX, maxY, maxZ,
                maxX, maxY, maxZ,
                maxX, maxY, minZ);

        // North (-Z)
        quad(consumer, matrix,
                minX, minY, minZ,
                minX, maxY, minZ,
                maxX, maxY, minZ,
                maxX, minY, minZ);

        // South (+Z)
        quad(consumer, matrix,
                maxX, minY, maxZ,
                maxX, maxY, maxZ,
                minX, maxY, maxZ,
                minX, minY, maxZ);

        // West (-X)
        quad(consumer, matrix,
                minX, minY, maxZ,
                minX, maxY, maxZ,
                minX, maxY, minZ,
                minX, minY, minZ);

        // East (+X)
        quad(consumer, matrix,
                maxX, minY, minZ,
                maxX, maxY, minZ,
                maxX, maxY, maxZ,
                maxX, minY, maxZ);
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix,
                              float x0, float y0, float z0,
                              float x1, float y1, float z1,
                              float x2, float y2, float z2,
                              float x3, float y3, float z3) {
        consumer.vertex(matrix, x0, y0, z0).color(TINT_R, TINT_G, TINT_B, TINT_A).endVertex();
        consumer.vertex(matrix, x1, y1, z1).color(TINT_R, TINT_G, TINT_B, TINT_A).endVertex();
        consumer.vertex(matrix, x2, y2, z2).color(TINT_R, TINT_G, TINT_B, TINT_A).endVertex();
        consumer.vertex(matrix, x3, y3, z3).color(TINT_R, TINT_G, TINT_B, TINT_A).endVertex();
    }
}
