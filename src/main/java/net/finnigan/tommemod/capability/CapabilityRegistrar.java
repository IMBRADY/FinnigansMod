package net.finnigan.tommemod.capability;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CapabilityRegistrar {

    @SubscribeEvent
    public static void registerCap(RegisterCapabilitiesEvent event) {
        event.register(AccessoryHandler.class);
    }
}