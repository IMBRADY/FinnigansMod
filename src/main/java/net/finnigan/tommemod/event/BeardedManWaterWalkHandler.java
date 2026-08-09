package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.finnigan.tommemod.item.custom.totems.TotemOfTheBeardedManItem;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Totem of the Bearded Man: stand on top of water instead of falling into it.
 *
 * Runs unconditionally on both sides - the accessory capability is synced to its owner (see
 * CapabilityHandler), so the client reaches the same verdict as the server and the player doesn't
 * rubber-band while walking across a lake.
 *
 * Only ever catches a player who is already at the surface: their feet block must be non-water with
 * water directly beneath it. That is what makes lava safe by construction, keeps sneaking (and
 * diving in from a height) working as the way down, and stops the totem from levitating anyone off
 * the bottom of an ocean.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class BeardedManWaterWalkHandler {

    /** How far above the water's top face the player may still be and get caught, in blocks. */
    private static final double CATCH_MARGIN = 0.3D;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.isSpectator() || player.getAbilities().flying) return;
        if (player.isPassenger() || player.isFallFlying()) return;
        if (player.isShiftKeyDown()) return; // sneaking is how you get in the water
        if (player.getDeltaMovement().y > 0.0D) return; // don't fight a jump
        if (!hasTotem(player)) return;

        Level level = player.level();
        BlockPos feet = player.blockPosition();
        if (level.getFluidState(feet).is(FluidTags.WATER)) return; // already submerged - stay submerged

        BlockPos below = feet.below();
        FluidState fluid = level.getFluidState(below);
        if (!fluid.is(FluidTags.WATER)) return;

        double surfaceY = below.getY() + 1.0D;
        if (player.getY() > surfaceY + CATCH_MARGIN) return;

        player.setPos(player.getX(), surfaceY, player.getZ());
        player.setDeltaMovement(player.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
        player.setOnGround(true);
        player.resetFallDistance();
    }

    private static boolean hasTotem(Player player) {
        return player.getCapability(ModCapabilities.ACCESSORY_HANDLER)
                .map(handler -> {
                    ItemStack totem = handler.getStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY);
                    return totem.getItem() instanceof TotemOfTheBeardedManItem;
                })
                .orElse(false);
    }
}
