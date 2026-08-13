package net.finnigan.tommemod.skill.data;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

public class SkillsProvider implements ICapabilitySerializable<CompoundTag> {

    private final SkillsHandler handler = new SkillsHandler();
    private final LazyOptional<SkillsHandler> optional = LazyOptional.of(() -> handler);

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        return cap == ModSkillCapabilities.SKILLS ? optional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return handler.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        handler.deserializeNBT(nbt);
    }

    public SkillsHandler getHandler() {
        return handler;
    }
}
