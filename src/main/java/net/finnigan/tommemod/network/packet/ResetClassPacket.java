package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.skill.SkillService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: "I confirmed the reset."
 *
 * Carries nothing at all. Which class is being abandoned, what it was worth and what comes back are
 * all things the server already knows and none of them are things a client should get to assert - the
 * confirmation dialog is a courtesy to the player, not the authority for the wipe.
 */
public class ResetClassPacket {

    public ResetClassPacket() {
    }

    public ResetClassPacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            double credit = SkillService.resetClass(player);
            player.displayClientMessage(credit > 0.0
                    ? Component.literal("Class reset. " + Math.round(credit)
                            + " experience banked for your next class.").withStyle(ChatFormatting.AQUA)
                    : Component.literal("You have no class to reset.").withStyle(ChatFormatting.GRAY), false);
        });
        ctx.setPacketHandled(true);
    }
}
