package net.finnigan.tommemod.village;

import net.finnigan.tommemod.village.buildings.VillageBuildings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Paying for anything village-scoped (blueprints, Chief Desk upgrades, trades the Chief makes).
 *
 * <p>Once the village has a Bank standing, its stores are drawn on first and the player's own
 * inventory only makes up any shortfall - so a Chief with a stocked bank needs nothing in hand.
 * Without a Bank, everything comes out of the player's inventory as it always did.
 */
public class VillageFunds {

    private VillageFunds() {
    }

    /** Whether this village's bank can be drawn on right now: it needs a Bank building standing. */
    public static boolean bankActive(ServerPlayer player, @Nullable UUID villageId) {
        return villageId != null && VillageBuildings.get(player.serverLevel()).hasBank(player.serverLevel(), villageId);
    }

    public static long available(ServerPlayer player, @Nullable UUID villageId, Item item) {
        long total = inventoryCount(player, item);
        if (bankActive(player, villageId)) total += VillageManager.get(player.serverLevel()).bankCount(villageId, item);
        return total;
    }

    /** Read-only affordability check - use before a purchase that spends more than one item type,
     * so a failed second deduction can't leave the first one already spent. */
    public static boolean hasEnough(ServerPlayer player, @Nullable UUID villageId, Item item, int cost) {
        return cost <= 0 || available(player, villageId, item) >= cost;
    }

    /** Takes {@code cost} of an item - bank first, then inventory - or nothing at all if there isn't enough. */
    public static boolean tryDeductItem(ServerPlayer player, @Nullable UUID villageId, Item item, int cost) {
        if (cost <= 0) return true;
        if (!hasEnough(player, villageId, item, cost)) return false;
        long remaining = cost;
        if (bankActive(player, villageId)) {
            remaining -= VillageManager.get(player.serverLevel()).bankWithdraw(villageId, item, remaining);
        }
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) break;
            if (!stack.is(item)) continue;
            int taken = (int) Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
        }
        return true;
    }

    public static boolean hasEnough(ServerPlayer player, Item item, int cost) {
        return hasEnough(player, null, item, cost);
    }

    public static boolean tryDeductItem(ServerPlayer player, Item item, int cost) {
        return tryDeductItem(player, null, item, cost);
    }

    public static boolean tryDeductEmeralds(ServerPlayer player, int cost) {
        return tryDeductItem(player, null, Items.EMERALD, cost);
    }

    public static long inventoryCount(ServerPlayer player, Item item) {
        long available = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) available += stack.getCount();
        }
        return available;
    }
}
