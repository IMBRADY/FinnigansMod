package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.AmethystCutlassHelpers.CrystalFragmentEntity;
import net.finnigan.tommemod.item.ModItems;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class AmethystCutlassEvents {

    private static final Set<UUID> pendingCritFragments = new HashSet<>();

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

    @SubscribeEvent
    public static void onCriticalHit(CriticalHitEvent event) {
        Player player = event.getEntity();
        if (player.getMainHandItem().getItem() == ModItems.AMETHYST_CUTLASS.get() && event.isVanillaCritical()) {
            pendingCritFragments.add(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof Player player && pendingCritFragments.remove(player.getUUID())) {
            spawnCrystalFragments(player, event.getEntity(), event.getAmount() * 0.25F);
        }
    }

    private static void spawnCrystalFragments(Player attacker, LivingEntity target, float damage) {
        if (attacker.level().isClientSide) return;
        for (int i = 0; i < 3; i++) {
            CrystalFragmentEntity fragment = new CrystalFragmentEntity(attacker.level(), attacker);
            fragment.setPos(target.getX(), target.getEyeY(), target.getZ());
            fragment.setDamage(damage);

            double dx = (attacker.getRandom().nextDouble() - 0.5D) * 2.0D;
            double dy = (attacker.getRandom().nextDouble() - 0.5D) + 0.3D;
            double dz = (attacker.getRandom().nextDouble() - 0.5D) * 2.0D;
            fragment.shoot(dx, dy, dz, 1.2F, 1.0F);

            attacker.level().addFreshEntity(fragment);
        }
    }
}