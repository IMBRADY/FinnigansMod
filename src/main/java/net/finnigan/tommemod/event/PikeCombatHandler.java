package net.finnigan.tommemod.event;

import net.finnigan.tommemod.item.custom.PikeItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = "tommemod", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PikeCombatHandler {

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        var source = event.getSource();
        var direct = source.getEntity();

        if (direct instanceof Player player
                && player.getMainHandItem().getItem() instanceof PikeItem) {

            double distance = player.distanceTo(event.getEntity());
            if (distance < PikeItem.CLOSE_RANGE_THRESHOLD) {
                event.setAmount(event.getAmount() * 0.5F);
            }
        }
    }

    // Manually re-implements anvil book merging for pikes, since PikeItem no longer is a sword item
    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        if (!(left.getItem() instanceof PikeItem)) return;
        if (!(right.getItem() instanceof EnchantedBookItem)) return;

        Map<Enchantment, Integer> rightEnchants = EnchantmentHelper.getEnchantments(right);
        Map<Enchantment, Integer> leftEnchants = new HashMap<>(EnchantmentHelper.getEnchantments(left));

        boolean anyApplied = false;
        int cost = 0;

        for (Map.Entry<Enchantment, Integer> entry : rightEnchants.entrySet()) {
            Enchantment enchantment = entry.getKey();
            if (!PikeItem.ALLOWED_ENCHANTMENTS.contains(enchantment)) continue; // silently skips Sweeping Edge

            int rightLevel = entry.getValue();
            int existingLevel = leftEnchants.getOrDefault(enchantment, 0);
            int newLevel = (existingLevel == rightLevel)
                    ? Math.min(existingLevel + 1, enchantment.getMaxLevel())
                    : Math.max(existingLevel, rightLevel);

            if (newLevel != existingLevel) {
                leftEnchants.put(enchantment, newLevel);
                anyApplied = true;
                cost += enchantment.getRarity().getWeight() * newLevel;
            }
        }

        if (!anyApplied) return;

        ItemStack result = left.copy();
        EnchantmentHelper.setEnchantments(leftEnchants, result);

        event.setOutput(result);
        event.setCost(Math.max(1, cost));
        event.setMaterialCost(1);
    }
}