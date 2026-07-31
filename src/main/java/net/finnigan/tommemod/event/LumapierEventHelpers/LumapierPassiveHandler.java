package net.finnigan.tommemod.event.LumapierEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.effect.ModMobEffects;
import net.finnigan.tommemod.item.custom.LumapierItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Lumapier passive: while held (main or offhand), continuously grants Purifying Light (poison/wither/
 * nausea immunity + torch-level light, see PurifyingLightImmunityHandler/LumapierLightHandler) and a
 * flat +20% MOVEMENT_SPEED AttributeModifier - explicitly NOT the vanilla Speed I MobEffect, per spec.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class LumapierPassiveHandler {

    private static final UUID LUMAPIER_SPEED_UUID = UUID.fromString("9d3f1c20-6666-4b3f-8a1d-000000000050");
    private static final double SPEED_BONUS = 0.20; // flat +20% passive movement speed

    // Refreshed every tick while held so it never visibly expires; short enough that unequipping lets
    // it fade out promptly (also explicitly removed below for a crisp cutoff).
    private static final int EFFECT_REFRESH_DURATION = 30;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        boolean held = LumapierItem.isHeldBy(player);

        if (held) {
            player.addEffect(new MobEffectInstance(ModMobEffects.PURIFYING_LIGHT.get(),
                    EFFECT_REFRESH_DURATION, 0, false, false, false));
        } else if (player.hasEffect(ModMobEffects.PURIFYING_LIGHT.get())) {
            player.removeEffect(ModMobEffects.PURIFYING_LIGHT.get());
        }

        applyOrRemove(player, held);
    }

    private static void applyOrRemove(Player player, boolean shouldHave) {
        AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (instance == null) return;

        if (shouldHave) {
            if (instance.getModifier(LUMAPIER_SPEED_UUID) == null) {
                instance.addTransientModifier(new AttributeModifier(LUMAPIER_SPEED_UUID, "Lumapier passive speed",
                        SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        } else {
            if (instance.getModifier(LUMAPIER_SPEED_UUID) != null) {
                instance.removeModifier(LUMAPIER_SPEED_UUID);
            }
        }
    }
}
