package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.skill.SkillService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: "I clicked Unlock on this node."
 *
 * Carries only which node, never what it costs or whether it is affordable - the server re-derives
 * all of that from its own definitions and its own copy of the player's progress. A client that lies
 * about the node id names one that either doesn't exist or that it doesn't qualify for, and gets a
 * refusal either way.
 */
public class UnlockSkillNodePacket {

    private final ResourceLocation skillId;
    private final String nodeId;

    public UnlockSkillNodePacket(ResourceLocation skillId, String nodeId) {
        this.skillId = skillId;
        this.nodeId = nodeId;
    }

    public UnlockSkillNodePacket(FriendlyByteBuf buf) {
        this.skillId = buf.readResourceLocation();
        this.nodeId = buf.readUtf(128);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(skillId);
        buf.writeUtf(nodeId, 128);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            SkillService.PurchaseResult result = SkillService.unlock(player, skillId, nodeId);
            if (result == SkillService.PurchaseResult.BOUGHT) return;

            // The screen already greys out what it knows the player can't have, so a refusal here
            // means the two disagreed - worth saying out loud rather than swallowing.
            player.displayClientMessage(Component.literal(switch (result) {
                case UNKNOWN_NODE -> "That skill no longer exists.";
                case ALREADY_MAXED -> "That node is already fully upgraded.";
                case NOT_ENOUGH_POINTS -> "Not enough points in that skill.";
                case REQUIREMENTS_UNMET -> "You don't meet the requirements for that node yet.";
                case BOUGHT -> "";
            }).withStyle(ChatFormatting.GRAY), true);
        });
        ctx.setPacketHandled(true);
    }
}
