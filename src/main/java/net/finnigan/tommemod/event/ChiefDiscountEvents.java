package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.entity.custom.ElderVillagerEntity;
import net.finnigan.tommemod.village.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;
import java.util.UUID;

/**
 * Grants the Village Chief a trade discount on every Villager within their own village. Applied
 * before vanilla's Villager#mobInteract runs its own reputation-based discount; both simply add to
 * the offer's special price diff (vanilla resets that diff to zero only when the trade menu closes),
 * so this is safely additive with no compounding across repeated trade sessions - no Mixin needed.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class ChiefDiscountEvents {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getTarget() instanceof AbstractVillager villager)) return;

        VillageManager manager = VillageManager.get(level);
        Optional<UUID> villageId = manager.resolveVillage(level, villager.blockPosition());
        if (villageId.isEmpty()) return;

        Optional<UUID> elderUUID = manager.getElder(villageId.get());
        if (elderUUID.isEmpty()) return;
        if (!(level.getEntity(elderUUID.get()) instanceof ElderVillagerEntity elder)) return;
        if (!player.getUUID().equals(elder.getChiefUUID())) return;

        for (MerchantOffer offer : villager.getOffers()) {
            // FLAT only makes sense when the offer's price is actually in emeralds; trades priced
            // in some other item (e.g. buying trades where costA is what the villager wants) fall
            // back to the percentage discount instead.
            boolean useFlat = ModConfig.CHIEF_DISCOUNT_TYPE.get() == ModConfig.DiscountType.FLAT
                    && offer.getBaseCostA().is(Items.EMERALD);
            int discount = useFlat
                    ? ModConfig.CHIEF_DISCOUNT_FLAT_EMERALDS.get()
                    : Mth.floor(offer.getBaseCostA().getCount() * ModConfig.CHIEF_DISCOUNT_PERCENT.get());
            if (discount > 0) offer.addToSpecialPriceDiff(-discount);
        }
    }
}
