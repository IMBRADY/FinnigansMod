package net.finnigan.tommemod.client;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.totems.TotemOfStickinessItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Jumping while clung to a wall (Totem of Stickiness) pushes off it instead of climbing further up.
 *
 * Client-only on purpose: the server never learns that a walking player pressed jump - only the
 * resulting motion, which player movement is authoritative about anyway. The grace window opened
 * here keeps mixin.LivingEntityClimbableMixin from re-attaching on the next tick, and the server
 * needs no equivalent since by then the player is no longer touching the wall.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class StickinessInputHandler {

    private static final double PUSH_AWAY = 0.4;
    private static final double PUSH_UP = 0.42; // same as a normal jump, so it reads as one

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) return;
        if (!mc.options.keyJump.isDown()) return;
        if (player.onGround()) return; // on the ground, jump is just a jump
        if (!TotemOfStickinessItem.isClingingToWall(player)) return;

        TotemOfStickinessItem.startDetachGrace(player);

        // Away from the wall is away from where the player is looking - you have to face a wall to
        // be climbing it in the first place.
        Vec3 look = player.getLookAngle();
        Vec3 away = new Vec3(-look.x, 0.0, -look.z);
        if (away.lengthSqr() < 1.0E-4) {
            away = Vec3.ZERO;
        } else {
            away = away.normalize().scale(PUSH_AWAY);
        }
        player.setDeltaMovement(away.x, PUSH_UP, away.z);
    }
}
