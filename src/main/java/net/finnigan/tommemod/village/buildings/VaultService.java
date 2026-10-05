package net.finnigan.tommemod.village.buildings;

import net.finnigan.tommemod.network.ModNetwork;
import net.finnigan.tommemod.network.packet.VaultStatusPacket;
import net.finnigan.tommemod.village.VillageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The Bank's vault: deposits and withdrawals against the village's bank (kept on VillageManager).
 * Anyone may pay in; only the Chief may take out. The vault only works while the Bank around it
 * still stands - a vault block on its own is just a block.
 */
public final class VaultService {

    private VaultService() {
    }

    /** The village whose bank this vault opens, or null (with a message to the player) if it opens none. */
    @Nullable
    public static UUID villageFor(ServerPlayer player, BlockPos vault) {
        ServerLevel level = player.serverLevel();
        Optional<UUID> village = VillageManager.get(level).resolveVillage(level, vault);
        if (village.isEmpty()) {
            fail(player, "This vault isn't part of an established village");
            return null;
        }
        if (!VillageBuildings.get(level).hasBank(level, village.get())) {
            fail(player, "The vault only works inside a standing Bank");
            return null;
        }
        return village.get();
    }

    public static boolean mayWithdraw(ServerPlayer player, UUID village) {
        VillageManager manager = VillageManager.get(player.serverLevel());
        return manager.getChief(village).map(player.getUUID()::equals).orElse(false)
                || (player.isCreative() && player.hasPermissions(2));
    }

    public static void sendStatus(ServerPlayer player, BlockPos vault, UUID village, boolean open) {
        List<VaultStatusPacket.Entry> entries = new ArrayList<>();
        for (Map.Entry<Item, Long> e : VillageManager.get(player.serverLevel()).bankContents(village).entrySet()) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(e.getKey());
            if (id != null) entries.add(new VaultStatusPacket.Entry(id.toString(), e.getValue()));
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new VaultStatusPacket(vault, open, mayWithdraw(player, village), entries));
    }

    /** Pays in every emerald the player carries. */
    public static void depositEmeralds(ServerPlayer player, UUID village) {
        long moved = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.is(Items.EMERALD)) continue;
            moved += stack.getCount();
            stack.setCount(0);
        }
        VillageManager.get(player.serverLevel()).bankDeposit(village, Items.EMERALD, moved);
        if (moved > 0) clink(player);
    }

    /** Pays in whatever the player holds in their main hand, any item at all. */
    public static void depositHeld(ServerPlayer player, UUID village) {
        ItemStack held = player.getMainHandItem();
        // Only plain stacks: anything carrying data (enchantments, names, contents) would lose it in a
        // bank that counts items by type alone.
        if (held.isEmpty() || held.hasTag()) {
            if (held.hasTag()) fail(player, "The bank only takes plain items - this one carries data it would lose");
            return;
        }
        VillageManager.get(player.serverLevel()).bankDeposit(village, held.getItem(), held.getCount());
        held.setCount(0);
        clink(player);
    }

    /** Takes up to a stack of an item (or everything of it, when {@code all}) into the player's inventory. */
    public static void withdraw(ServerPlayer player, UUID village, Item item, boolean all) {
        if (!mayWithdraw(player, village)) {
            fail(player, "Only the Village Chief may take from the vault");
            return;
        }
        VillageManager manager = VillageManager.get(player.serverLevel());
        long want = all ? manager.bankCount(village, item) : item.getMaxStackSize();
        long taken = manager.bankWithdraw(village, item, want);
        while (taken > 0) {
            int n = (int) Math.min(taken, item.getMaxStackSize());
            ItemStack stack = new ItemStack(item, n);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
            taken -= n;
        }
        clink(player);
    }

    private static void clink(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6F, 1.4F);
    }

    private static void fail(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.RED), true);
    }
}
