package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.enchantment.ModEnchantments;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class EnchantmentEvents {

    // Heal the attacker a % of the damage they deal, based on Lifesteal level
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            int level = EnchantmentHelper.getEnchantmentLevel(ModEnchantments.LIFE_STEAL.get(), attacker);
            if (level > 0) {
                float healAmount = event.getAmount() * (0.03F * level); // 3% per level, 30% at max
                attacker.heal(healAmount);
            }
        }
    }
    @SubscribeEvent
    public static void onLivingDamagePoison(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            int level = EnchantmentHelper.getEnchantmentLevel(ModEnchantments.POISON.get(), attacker);
            if (level > 0) {
                float chance = 0.25F * level;
                if (attacker.getRandom().nextFloat() < chance) {
                    target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0)); // 5 seconds poison I
                }
            }
        }
    }
}