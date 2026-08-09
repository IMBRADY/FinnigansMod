package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Left-clicking a mob standing in tall grass hits the mob, not the grass.
 *
 * Works off InputEvent.InteractionKeyMappingTriggered - the moment the attack key resolves, and the
 * one place a plain Forge event can redirect a click - rather than a mixin on the crosshair pick.
 * That keeps this off the collision/shape hot path that Embeddium, Canary and friends contend over.
 *
 * Only blocks with an empty collision shape qualify, which is exactly the set you can already walk
 * through: grass, ferns, flowers, saplings, crops, and any modded plant that behaves the same way.
 * Solid blocks are untouched, so this can never let you hit through a wall.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, value = Dist.CLIENT)
public class SwingThroughPlantsHandler {

    /** Vanilla's unmodified entity reach, used only if the reach attribute is somehow absent. */
    private static final double FALLBACK_REACH = 3.0D;

    @SubscribeEvent
    public static void onAttackInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        if (!ModConfig.SWING_THROUGH_PLANTS.get()) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null || minecraft.gameMode == null) return;
        if (player.isSpectator() || player.isUsingItem()) return;

        // Only step in when the crosshair actually landed on a pass-through plant. Anything else -
        // a real block, an entity already targeted, empty air - is left entirely to vanilla.
        if (!(minecraft.hitResult instanceof BlockHitResult blockHit)) return;
        if (blockHit.getType() != HitResult.Type.BLOCK) return;

        BlockState state = level.getBlockState(blockHit.getBlockPos());
        if (!state.getCollisionShape(level, blockHit.getBlockPos()).isEmpty()) return;

        Entity target = findEntityBehind(player);
        if (target == null) return;

        event.setCanceled(true);
        minecraft.gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND);
        event.setSwingHand(false); // swing() above already did it; leaving it on double-swings
    }

    private static Entity findEntityBehind(LocalPlayer player) {
        double reach = player.getAttribute(ForgeMod.ENTITY_REACH.get()) != null
                ? player.getAttributeValue(ForgeMod.ENTITY_REACH.get())
                : FALLBACK_REACH;

        Vec3 eyes = player.getEyePosition();
        Vec3 end = eyes.add(player.getLookAngle().scale(reach));
        AABB searchBox = player.getBoundingBox().expandTowards(player.getLookAngle().scale(reach)).inflate(1.0D);

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player, eyes, end, searchBox,
                entity -> !entity.isSpectator() && entity.isPickable(),
                reach * reach);

        return hit != null ? hit.getEntity() : null;
    }
}
