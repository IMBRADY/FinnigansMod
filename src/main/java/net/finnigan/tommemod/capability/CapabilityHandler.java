package net.finnigan.tommemod.capability;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.capability.accessory.AccessoryProvider;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.finnigan.tommemod.network.ModNetwork;
import net.finnigan.tommemod.network.packet.SyncAccessoryPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = "tommemod")
public class CapabilityHandler {

    public static final ResourceLocation ACCESSORY_CAP_ID =
            new ResourceLocation(TommeMod.MOD_ID, "accessories");

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<net.minecraft.world.entity.Entity> event) {
        if (event.getObject() instanceof Player) {
            AccessoryProvider provider = new AccessoryProvider();
            provider.getHandler().setChangeListener(() -> {
                if (event.getObject() instanceof net.minecraft.server.level.ServerPlayer sp) {
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                            new SyncAccessoryPacket(sp.getId(), provider.getHandler().serializeNBT()));
                }
            });
            event.addCapability(ACCESSORY_CAP_ID, provider);
        }
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;

        boolean keepInventory = event.getEntity().level().getGameRules()
                .getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY);

        if (keepInventory) {
            event.getOriginal().getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(oldHandler ->
                    event.getEntity().getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(newHandler ->
                            newHandler.deserializeNBT(oldHandler.serializeNBT())));
        }
    }
}