package net.finnigan.tommemod.village.buildings;

import net.finnigan.tommemod.village.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Optional;
import java.util.UUID;

/**
 * Trading on the village's account. When the Chief picks a trade with one of their own villagers and
 * the village has a Bank, whatever the trade costs that they aren't carrying is drawn from the bank
 * into the payment slots. What they buy still lands in their own inventory.
 *
 * <p>Only the Chief: anything moved into the payment slots goes back to the player's inventory if the
 * trade isn't made, which is no different from the Chief taking it from the vault themselves.
 */
public final class BankTrades {

    private BankTrades() {
    }

    /** Called after vanilla has filled the payment slots from the player's inventory for a selected trade. */
    public static void topUpFromBank(Merchant trader, MerchantContainer payment, int offerIndex) {
        if (!(trader instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) return;
        if (!(trader.getTradingPlayer() instanceof ServerPlayer player)) return;
        MerchantOffers offers = trader.getOffers();
        if (offerIndex < 0 || offerIndex >= offers.size()) return;

        VillageManager manager = VillageManager.get(level);
        Optional<UUID> village = manager.resolveVillage(level, villager.blockPosition());
        if (village.isEmpty()) return;
        if (!manager.getChief(village.get()).map(player.getUUID()::equals).orElse(false)) return;
        if (!VillageBuildings.get(level).hasBank(level, village.get())) return;

        MerchantOffer offer = offers.get(offerIndex);
        fill(manager, village.get(), payment, 0, offer.getCostA());
        fill(manager, village.get(), payment, 1, offer.getCostB());
    }

    private static void fill(VillageManager manager, UUID village, MerchantContainer payment, int slot, ItemStack cost) {
        if (cost.isEmpty() || cost.hasTag()) return; // the bank holds plain items only
        ItemStack current = payment.getItem(slot);
        if (!current.isEmpty() && !ItemStack.isSameItemSameTags(current, cost)) return;
        int have = current.isEmpty() ? 0 : current.getCount();
        int need = Math.min(cost.getCount(), cost.getMaxStackSize()) - have;
        if (need <= 0) return;
        long taken = manager.bankWithdraw(village, cost.getItem(), need);
        if (taken <= 0) return;
        payment.setItem(slot, new ItemStack(cost.getItem(), have + (int) taken));
    }
}
