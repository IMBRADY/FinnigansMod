package net.finnigan.tommemod.event.AquatanaEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.AquatanaItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class WaveDashEffectHandler {

    private static final UUID WATER_SPEED_UUID = UUID.fromString("3f2c9a10-4444-4b3f-8a1d-000000000020");
    private static final UUID SHALLOW_SPEED_UUID = UUID.fromString("3f2c9a10-4444-4b3f-8a1d-000000000021");

    private static final double WATER_SPEED_BONUS = 0.75; // +x%
    private static final double SHALLOW_SPEED_BONUS = 0.2;  // +x% on top of above

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        boolean hasSword = player.getMainHandItem().getItem() instanceof AquatanaItem
                || player.getOffhandItem().getItem() instanceof AquatanaItem;

        if (!hasSword) {
            removeModifier(player, WATER_SPEED_UUID);
            removeModifier(player, SHALLOW_SPEED_UUID);
            return;
        }

        // No drowning
        if (player.isInWater() && player.getAirSupply() < player.getMaxAirSupply()) {
            player.setAirSupply(player.getMaxAirSupply());
        }

        // Increased swim speed
        if (player.isInWater()) {
            addModifier(player, WATER_SPEED_UUID, WATER_SPEED_BONUS);
        } else {
            removeModifier(player, WATER_SPEED_UUID);
        }

        // Extra speed boost in shallow (1-block-deep) water
        if (isInShallowWater(player)) {
            addModifier(player, SHALLOW_SPEED_UUID, SHALLOW_SPEED_BONUS);
        } else {
            removeModifier(player, SHALLOW_SPEED_UUID);
        }
    }

    /** "Shallow" = water at the player's feet, but not submerged (nothing but air above it). */
    private static boolean isInShallowWater(Player player) {
        BlockPos feetPos = player.blockPosition();
        FluidState feetFluid = player.level().getFluidState(feetPos);
        FluidState aboveFluid = player.level().getFluidState(feetPos.above());

        return feetFluid.is(net.minecraft.tags.FluidTags.WATER) && aboveFluid.isEmpty();
    }

    private static void addModifier(Player player, UUID uuid, double amount) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(uuid) == null) {
            speed.addTransientModifier(new AttributeModifier(uuid, "Wave dash water speed",
                    amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void removeModifier(Player player, UUID uuid) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(uuid) != null) {
            speed.removeModifier(uuid);
        }
    }
}