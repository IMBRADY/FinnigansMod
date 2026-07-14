package net.finnigan.tommemod.capability.accessory;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

public class AccessoryProvider implements ICapabilitySerializable<CompoundTag> {

    private final AccessoryHandler handler = new AccessoryHandler();
    private final LazyOptional<AccessoryHandler> optional = LazyOptional.of(() -> handler);

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        return cap == ModCapabilities.ACCESSORY_HANDLER ? optional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return handler.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        handler.deserializeNBT(nbt);
    }

    public AccessoryHandler getHandler() {
        return handler;
    }
}