package net.finnigan.tommemod.event.IxeEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.IxeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Ixe passive: Regeneration I in snowy biomes, 1.5x speed while snowing, never freezes. */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class IxePassiveHandler {

    private static final UUID SNOW_SPEED_UUID = UUID.fromString("9d3f1c20-5555-4b3f-8a1d-000000000040");
    private static final double SNOW_SPEED_BONUS = 0.5; // net 1.5x while snowing

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        Level level = player.level();
        if (level.isClientSide) return;
        if (!IxeItem.isHeldBy(player)) {
            removeSpeedBonus(player);
            return;
        }

        BlockPos pos = player.blockPosition();
        boolean snowyBiome = isSnowyBiome(level, pos);
        boolean snowing = snowyBiome && level.isRaining();

        if (snowyBiome) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 30, 0, false, false, false));
        }

        applyOrRemove(player, SNOW_SPEED_UUID, SNOW_SPEED_BONUS, snowing);

        // never freezes
        player.setTicksFrozen(0);
    }

    @SubscribeEvent
    public static void onLivingHurtCancelFreeze(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!IxeItem.isHeldBy(player)) return;
        if (event.getSource().is(DamageTypeTags.IS_FREEZING)) {
            event.setCanceled(true);
        }
    }

    private static boolean isSnowyBiome(Level level, BlockPos pos) {
        Biome biome = level.getBiome(pos).value();
        return biome.coldEnoughToSnow(pos);
    }

    private static void removeSpeedBonus(Player player) {
        applyOrRemove(player, SNOW_SPEED_UUID, SNOW_SPEED_BONUS, false);
    }

    private static void applyOrRemove(Player player, UUID uuid, double amount, boolean shouldHave) {
        AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (instance == null) return;

        if (shouldHave) {
            if (instance.getModifier(uuid) == null) {
                instance.addTransientModifier(new AttributeModifier(uuid, "Ixe snowing speed",
                        amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        } else {
            if (instance.getModifier(uuid) != null) {
                instance.removeModifier(uuid);
            }
        }
    }
}
