package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.client.SkillXpFeed;
import net.finnigan.tommemod.skill.data.ModSkillCapabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: one player's own skill progress.
 *
 * Sent whenever anything about it changes, which is often - every few blocks walked moves an
 * experience bar. It stays cheap because untouched skills are never written (see
 * SkillProgress#isUntouched), so an early-game player's whole payload is a handful of numbers.
 *
 * Only ever the receiving player's own data. Nobody else's progress is any of their business, and
 * nothing client-side has a use for it.
 */
public class SyncSkillDataPacket {

    private final CompoundTag data;

    public SyncSkillDataPacket(CompoundTag data) {
        this.data = data;
    }

    public SyncSkillDataPacket(FriendlyByteBuf buf) {
        this.data = buf.readNbt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(data);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::applyOnClient));
        ctx.setPacketHandled(true);
    }

    private void applyOnClient() {
        var player = Minecraft.getInstance().player;
        if (player == null || data == null) return;

        // deserializeNBT recomputes the derived totals, so the client's own bonus lookups - the ones
        // driving greyed-out nodes and the effect lines in tooltips - agree with the server's without
        // any of it being sent.
        player.getCapability(ModSkillCapabilities.SKILLS).ifPresent(handler -> {
            handler.deserializeNBT(data);

            // The corner readout works out what was earned by comparing this against the last one, so
            // it runs here, after the capability has been written and before anything else moves it.
            // Handed the handler rather than the player deliberately: passing LocalPlayer to anything
            // typed Player makes the verifier prove the two are related, which on a dedicated server
            // means loading a client-only class and losing mod construction to a RuntimeDistCleaner.
            SkillXpFeed.onProgressSynced(handler);
        });
    }
}
