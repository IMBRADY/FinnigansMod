package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.finnigan.tommemod.network.ModNetwork;
import net.finnigan.tommemod.network.packet.SyncAccessoryPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class AccessorySyncEvents {

    private static void sync(net.minecraft.world.entity.player.Player player) {
        if (player instanceof ServerPlayer sp) {
            sp.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(h ->
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                            new SyncAccessoryPacket(sp.getId(), h.serializeNBT())));
        }
    }
}