package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.WitherspineItem;
import net.finnigan.tommemod.network.ModNetwork;
import net.finnigan.tommemod.network.packet.SyncWitherspineChargeResetPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Clears Witherspine's banked partial charge whenever its owner actually takes damage. */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public final class WitherspineChargeHandler {
    private WitherspineChargeHandler() {
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getAmount() <= 0.0F) {
            return;
        }

        // Damage does not interrupt an already-completed firing pool.
        if (player.getPersistentData().getInt(WitherspineItem.FIRING_TICKS_LEFT_TAG) > 0) {
            return;
        }

        boolean activelyCharging = player.isUsingItem()
                && player.getUseItem().getItem() instanceof WitherspineItem;
        if (activelyCharging
                || player.getPersistentData().getInt(WitherspineItem.CHARGE_TICKS_TAG) > 0) {
            player.getPersistentData().remove(WitherspineItem.CHARGE_TICKS_TAG);
            if (activelyCharging) {
                player.stopUsingItem();
            }
            if (player instanceof ServerPlayer serverPlayer) {
                ModNetwork.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                        new SyncWitherspineChargeResetPacket());
            }
        }
    }
}
