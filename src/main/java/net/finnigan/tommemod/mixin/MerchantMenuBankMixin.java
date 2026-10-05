package net.finnigan.tommemod.mixin;

import net.finnigan.tommemod.village.buildings.BankTrades;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After vanilla fills a selected trade's payment slots from the player's inventory, lets the village
 * bank make up the rest for the Chief (see BankTrades). Server-side only in effect: BankTrades ignores
 * any trader that isn't a real villager being traded with by a server player.
 */
@Mixin(MerchantMenu.class)
public abstract class MerchantMenuBankMixin {

    @Shadow
    @Final
    private Merchant trader;

    @Shadow
    @Final
    private MerchantContainer tradeContainer;

    @Inject(method = "tryMoveItems", at = @At("TAIL"))
    private void tommemod$payFromVillageBank(int offerIndex, CallbackInfo ci) {
        BankTrades.topUpFromBank(trader, tradeContainer, offerIndex);
    }
}
