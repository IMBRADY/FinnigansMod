package net.finnigan.tommemod.skill.data;

import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.Capability;

public class ModSkillCapabilities {
    public static final Capability<SkillsHandler> SKILLS =
            CapabilityManager.get(new CapabilityToken<>() {
            });
}
