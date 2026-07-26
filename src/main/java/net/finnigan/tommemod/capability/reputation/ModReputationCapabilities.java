package net.finnigan.tommemod.capability.reputation;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

public class ModReputationCapabilities {
    public static final Capability<ReputationHandler> REPUTATION_HANDLER =
            CapabilityManager.get(new CapabilityToken<>() {});
}
