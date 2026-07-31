package net.finnigan.tommemod.village;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * No Bank building exists yet for villages to draw shared funds/resources from - once one does,
 * callers should try deducting from the village's bank inventory before falling back to the
 * player's own inventory, as this does. Shared by anything that charges a village-scoped cost
 * (Monolith upgrades, Builder Hub construction) so that seam only needs to be filled in once.
 */
public class VillageFunds {

    private VillageFunds() {
    }

    public static boolean tryDeductEmeralds(ServerPlayer player, int cost) {
        return tryDeductItem(player, Items.EMERALD, cost);
    }

    /** Read-only affordability check - use before a purchase that spends more than one item type,
     * so a failed second deduction can't leave the first one already spent. */
    public static boolean hasEnough(ServerPlayer player, Item item, int cost) {
        if (cost <= 0) return true;
        int available = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) available += stack.getCount();
        }
        return available >= cost;
    }

    public static boolean tryDeductItem(ServerPlayer player, Item item, int cost) {
        if (cost <= 0) return true;

        int available = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) available += stack.getCount();
        }
        if (available < cost) return false;

        int remaining = cost;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) break;
            if (!stack.is(item)) continue;
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
        }
        return true;
    }
}
