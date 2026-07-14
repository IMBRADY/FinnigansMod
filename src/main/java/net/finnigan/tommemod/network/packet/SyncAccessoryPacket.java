package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncAccessoryPacket {

    private final int entityId;
    private final CompoundTag data;

    public SyncAccessoryPacket(int entityId, CompoundTag data) {
        this.entityId = entityId;
        this.data = data;
    }

    public SyncAccessoryPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.data = buf.readNbt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeNbt(data);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Entity e = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getEntity(entityId) : null;
            if (e != null) {
                e.getCapability(ModCapabilities.ACCESSORY_HANDLER).ifPresent(h -> h.deserializeNBT(data));
            }
        });
        ctx.setPacketHandled(true);
    }
}