package net.finnigan.tommemod.network.packet;

import net.finnigan.tommemod.skill.event.SkillGuardianBonuses;
import net.finnigan.tommemod.skill.event.SkillRangerBonuses;
import net.finnigan.tommemod.skill.event.SkillVanguardBonuses;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: "I pressed the class ability key."
 *
 * One key for three abilities, dispatched by what the player owns rather than by what the client
 * claims. A Swashbuckler dashes, a Hexblade fires their ultimate, an Aegis bonds - and because the
 * classes are mutually exclusive, no player can ever own two of them at once. Sending which ability
 * was meant would be a client asserting something the server already knows better.
 *
 * Carries nothing, so a client that spams it gets nothing a cooldown check does not already refuse.
 */
public class ClassAbilityPacket {

    public ClassAbilityPacket() {
    }

    public ClassAbilityPacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            boolean used = SkillVanguardBonuses.tryDash(player)
                    || SkillRangerBonuses.tryUltimate(player)
                    || SkillGuardianBonuses.tryBond(player);

            // Only said when nothing at all fired. A refusal from a cooldown is its own answer and the
            // player already knows why - saying it every press would be noise on a key held down.
            if (!used) {
                player.displayClientMessage(Component.literal("No class ability ready.")
                        .withStyle(ChatFormatting.DARK_GRAY), true);
            }
        });
        ctx.setPacketHandled(true);
    }
}
