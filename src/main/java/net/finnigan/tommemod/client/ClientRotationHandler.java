package net.finnigan.tommemod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public class ClientRotationHandler {
    public static void apply(float yaw, float pitch) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.setYRot(yaw);
            player.setXRot(pitch);
            player.setYHeadRot(yaw);
            player.yRotO = yaw;   // previous-tick rotation, prevents a visual snap-back/interpolation glitch
            player.xRotO = pitch;
        }
    }
}