package net.finnigan.tommemod.capability.accessory;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

public class ModCapabilities {
    public static final Capability<AccessoryHandler> ACCESSORY_HANDLER =
            CapabilityManager.get(new CapabilityToken<>() {});
}