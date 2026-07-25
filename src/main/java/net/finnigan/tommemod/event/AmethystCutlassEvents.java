package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.ModItems;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class AmethystCutlassEvents {

    @SubscribeEvent
    public static void onRightClickAmethystShard(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();

        if (!stack.is(Items.AMETHYST_SHARD) || !hasCutlass(player)) return;

        FoodData food = player.getFoodData();
        if (food.getFoodLevel() >= 20 && food.getSaturationLevel() >= 20.0F) return;

        if (!player.level().isClientSide) {
            food.setFoodLevel(Math.min(20, food.getFoodLevel() + 10));
            food.setSaturation(Math.min(20.0F, food.getSaturationLevel() + 12.8F));
            stack.shrink(1);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static boolean hasCutlass(Player player) {
        for (ItemStack invStack : player.getInventory().items) {
            if (invStack.getItem() == ModItems.AMETHYST_CUTLASS.get()) return true;
        }
        return player.getOffhandItem().getItem() == ModItems.AMETHYST_CUTLASS.get();
    }
}