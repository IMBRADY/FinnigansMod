package net.finnigan.tommemod.villager;

import net.finnigan.tommemod.TommeMod;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class ModVillagerTrades {

    @SubscribeEvent
    public static void addTrades(VillagerTradesEvent event) {
        if (event.getType() == ModVillagers.BAKER.get()) {
            var trades = event.getTrades();

            trades.get(1).add((trader, random) ->
                    new net.minecraft.world.item.trading.MerchantOffer(
                            new net.minecraft.world.item.ItemStack(Items.EGG, 6), // cost item
                            new net.minecraft.world.item.ItemStack(Items.EMERALD, 1), // result item
                            16, 2, 0.05F)); // max uses, xp, price multiplier

            trades.get(1).add((trader, random) ->
                    new net.minecraft.world.item.trading.MerchantOffer(
                            new net.minecraft.world.item.ItemStack(Items.SUGAR, 4), // cost item
                            new net.minecraft.world.item.ItemStack(Items.EMERALD, 1), // result item
                            16, 2, 0.05F)); // max uses, xp, price multiplier

            trades.get(2).add((trader, random) ->
                    new net.minecraft.world.item.trading.MerchantOffer(
                            new net.minecraft.world.item.ItemStack(Items.COCOA_BEANS, 2),
                            new net.minecraft.world.item.ItemStack(Items.COOKIE, 12),
                            16, 2, 0.05F));

            trades.get(2).add((trader, random) ->
                    new net.minecraft.world.item.trading.MerchantOffer(
                            new net.minecraft.world.item.ItemStack(Items.EMERALD, 1),
                            new net.minecraft.world.item.ItemStack(Items.CHARCOAL, 12),
                            12, 5, 0.05F));

            trades.get(3).add((trader, random) ->
                    new net.minecraft.world.item.trading.MerchantOffer(
                            new net.minecraft.world.item.ItemStack(Items.COAL, 1),
                            new net.minecraft.world.item.ItemStack(Items.BREAD, 4), // PLACEHOLDER
                            12, 5, 0.05F));

            trades.get(4).add((trader, random) ->
                    new net.minecraft.world.item.trading.MerchantOffer(
                            new net.minecraft.world.item.ItemStack(Items.EMERALD, 1),
                            new net.minecraft.world.item.ItemStack(Items.BREAD, 4), // PLACEHOLDER
                            12, 5, 0.05F));

            trades.get(5).add((trader, random) ->
                    new net.minecraft.world.item.trading.MerchantOffer(
                            new net.minecraft.world.item.ItemStack(Items.EMERALD, 1),
                            new net.minecraft.world.item.ItemStack(Items.BREAD, 4), // PLACEHOLDER
                            12, 5, 0.05F));
        }
    }
}