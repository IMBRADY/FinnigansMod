package net.finnigan.tommemod.event.ArackopeshEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.ArackopeshItem;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class WebImmunityHandler {

    private static final UUID WEB_SPEED_UUID = UUID.fromString("7a3c9a10-4444-4b3f-8a1d-000000000099");
    private static final double WEB_SPEED_BONUS = 3.0; // net ~4x speed while stuck, cancelling cobweb's 0.25x

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide) return;

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;

        boolean shouldBoost = ArackopeshItem.isHeldBy(player) && isOverlappingCobweb(player);

        if (shouldBoost) {
            if (speed.getModifier(WEB_SPEED_UUID) == null) {
                speed.addTransientModifier(new AttributeModifier(WEB_SPEED_UUID, "Web immunity",
                        WEB_SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        } else {
            if (speed.getModifier(WEB_SPEED_UUID) != null) {
                speed.removeModifier(WEB_SPEED_UUID);
            }
        }
    }

    private static boolean isOverlappingCobweb(Player player) {
        AABB box = player.getBoundingBox();
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(box.maxY);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    var pos = new net.minecraft.core.BlockPos(x, y, z);
                    if (player.level().getBlockState(pos).is(Blocks.COBWEB)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}