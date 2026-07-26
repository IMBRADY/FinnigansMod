package net.finnigan.tommemod.capability.reputation;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

public class ReputationProvider implements ICapabilitySerializable<CompoundTag> {

    private final ReputationHandler handler = new ReputationHandler();
    private final LazyOptional<ReputationHandler> optional = LazyOptional.of(() -> handler);

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        return cap == ModReputationCapabilities.REPUTATION_HANDLER ? optional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return handler.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        handler.deserializeNBT(nbt);
    }

    public ReputationHandler getHandler() {
        return handler;
    }
}
