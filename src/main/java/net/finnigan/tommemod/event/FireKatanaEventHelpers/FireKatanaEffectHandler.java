package net.finnigan.tommemod.event.FireKatanaEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.FireKatanaItem;
import net.finnigan.tommemod.item.custom.FireKatanaHelpers.FireZoneManager;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class FireKatanaEffectHandler {

    private static final UUID ON_FIRE_DAMAGE_UUID = UUID.fromString("9d3f1c20-5555-4b3f-8a1d-000000000030");
    private static final UUID ON_FIRE_SPEED_UUID = UUID.fromString("9d3f1c20-5555-4b3f-8a1d-000000000031");

    private static final double ON_FIRE_DAMAGE_BONUS = 5;   // flat addition
    private static final double ON_FIRE_SPEED_BONUS = 1.5;   // +150% speed

    private static boolean hasFireKatana(Player player) {
        return player.getMainHandItem().getItem() instanceof FireKatanaItem
                || player.getOffhandItem().getItem() instanceof FireKatanaItem;
    }

    @SubscribeEvent
    public static void onLivingHurtFireZoneCheck(LivingHurtEvent event) {
        if (!event.getSource().is(DamageTypeTags.IS_FIRE)) return;

        LivingEntity victim = event.getEntity();
        if (victim instanceof Player) return; // fire zone shouldn't double-damage players standing near their own fire

        if (FireZoneManager.isInAnyFireZone(victim)) {
            event.setAmount(event.getAmount() * 2.0F);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        boolean hasSword = hasFireKatana(player);
        boolean onFire = player.isOnFire();

        applyOrRemove(player, Attributes.ATTACK_DAMAGE, ON_FIRE_DAMAGE_UUID, ON_FIRE_DAMAGE_BONUS,
                AttributeModifier.Operation.ADDITION, hasSword && onFire);
        applyOrRemove(player, Attributes.MOVEMENT_SPEED, ON_FIRE_SPEED_UUID, ON_FIRE_SPEED_BONUS,
                AttributeModifier.Operation.MULTIPLY_TOTAL, hasSword && onFire);
    }

    private static void applyOrRemove(Player player, Attribute attribute,
                                      UUID uuid, double amount, AttributeModifier.Operation op, boolean shouldHave) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;

        if (shouldHave) {
            if (instance.getModifier(uuid) == null) {
                instance.addTransientModifier(new AttributeModifier(uuid, "Fire katana on-fire buff", amount, op));
            }
        } else {
            if (instance.getModifier(uuid) != null) {
                instance.removeModifier(uuid);
            }
        }
    }
}