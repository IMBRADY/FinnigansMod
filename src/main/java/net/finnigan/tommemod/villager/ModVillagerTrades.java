package net.finnigan.tommemod.villager;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.ModItems;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class ModVillagerTrades {

    @SubscribeEvent
    public static void addTrades(VillagerTradesEvent event) {
        if (event.getType() == ModVillagers.BEEKEEPER.get()) {
            var trades = event.getTrades();

            // Level 1 (novice) — buys flowers, sells honey
            trades.get(1).add((trader, random) -> new MerchantOffer(
                    new ItemStack(Items.POPPY, 6), new ItemStack(Items.EMERALD, 1), 16, 1, 0.05F));
            trades.get(1).add((trader, random) -> new MerchantOffer(
                    new ItemStack(Items.DANDELION, 6), new ItemStack(Items.EMERALD, 1), 16, 1, 0.05F));
            trades.get(1).add((trader, random) -> new MerchantOffer(
                    new ItemStack(Items.EMERALD, 1), new ItemStack(Items.HONEY_BOTTLE, 1), 16, 1, 0.05F));

            // Level 2 (apprentice) — buys bee nests + more flowers, sells honeycomb
            trades.get(2).add((trader, random) -> new MerchantOffer(
                    new ItemStack(Items.BEE_NEST, 1), new ItemStack(Items.EMERALD, 3), 8, 5, 0.05F));
            trades.get(2).add((trader, random) -> new MerchantOffer(
                    new ItemStack(Items.CORNFLOWER, 6), new ItemStack(Items.EMERALD, 1), 16, 5, 0.05F));
            trades.get(2).add((trader, random) -> new MerchantOffer(
                    new ItemStack(Items.EMERALD, 2), new ItemStack(Items.HONEYCOMB, 1), 12, 5, 0.05F));

            // Level 3 (journeyman) — sells stingers and bee wings
            trades.get(3).add((trader, random) -> new MerchantOffer(
                    new ItemStack(Items.EMERALD, 3), new ItemStack(ModItems.STINGER.get(), 1), 12, 10, 0.05F));
            trades.get(3).add((trader, random) -> new MerchantOffer(
                    new ItemStack(Items.EMERALD, 4), new ItemStack(ModItems.BEE_WINGS.get(), 2), 12, 10, 0.05F));

            // Level 4 (expert) — sells bee-nade and better buzz (poison antidote)
            trades.get(4).add((trader, random) -> new MerchantOffer(
                    new ItemStack(Items.EMERALD, 6), new ItemStack(ModItems.BEE_NADE.get(), 1), 8, 15, 0.05F));
            trades.get(4).add((trader, random) -> new MerchantOffer(
                    new ItemStack(Items.EMERALD, 5), new ItemStack(ModItems.BETTER_BUZZ.get(), 1), 8, 15, 0.05F));

            // Level 5 (master) — trades honey + emeralds up into Premium Honey
            trades.get(5).add((trader, random) -> new MerchantOffer(
                    new ItemStack(Items.HONEY_BOTTLE, 1), new ItemStack(Items.EMERALD, 4),
                    new ItemStack(ModItems.PREMIUM_HONEY.get(), 1), 12, 30, 0.05F));
        }

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