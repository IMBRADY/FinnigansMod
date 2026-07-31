package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.util.ModTags;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * "Only one Unique-tagged sword may be held in survival" (creative is unrestricted).
 * Scope = entire inventory (hotbar + storage + offhand). Extras are actively dropped, not destroyed.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class UniqueSwordEnforcementHandler {

    private static final int CHECK_INTERVAL_TICKS = 10;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        if (player.isCreative() || player.isSpectator()) return;
        if (player.tickCount % CHECK_INTERVAL_TICKS != 0) return;

        boolean keptOne = false;
        NonNullList<ItemStack> main = player.getInventory().items;
        for (int i = 0; i < main.size(); i++) {
            keptOne = dropIfExtra(player, main.get(i), keptOne);
        }
        NonNullList<ItemStack> offhand = player.getInventory().offhand;
        for (int i = 0; i < offhand.size(); i++) {
            keptOne = dropIfExtra(player, offhand.get(i), keptOne);
        }
    }

    private static boolean dropIfExtra(Player player, ItemStack stack, boolean alreadyKeptOne) {
        if (stack.isEmpty() || !stack.is(ModTags.Items.UNIQUE)) return alreadyKeptOne;
        if (!alreadyKeptOne) return true;

        ItemStack dropped = stack.copy();
        stack.setCount(0);
        player.drop(dropped, false, false);
        return true;
    }

    /** True if this player may activate a Unique-tagged sword's ability right now. */
    public static boolean canUseUniqueSword(Player player, Item item) {
        if (player.isCreative() || player.isSpectator()) return true;
        return countUniqueHeld(player) <= 1;
    }

    private static int countUniqueHeld(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(ModTags.Items.UNIQUE)) count++;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (!stack.isEmpty() && stack.is(ModTags.Items.UNIQUE)) count++;
        }
        return count;
    }
}
