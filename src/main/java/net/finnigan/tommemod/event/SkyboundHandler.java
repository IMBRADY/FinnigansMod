package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.finnigan.tommemod.enchantment.ModEnchantments;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Skybound: holding sneak while gliding on an enchanted elytra propels the player forward exactly as
 * a firework rocket would.
 *
 * The delta-movement maths is lifted straight from FireworkRocketEntity#tick so the feel matches
 * vanilla boosting, and like the rocket it runs on both sides - server for authority, local client
 * for smooth prediction - because elytra movement is client-driven. Remote players on a client are
 * skipped; their motion is interpolated from the server.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkyboundHandler {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide && !player.isLocalPlayer()) return;
        if (!player.isFallFlying() || !player.isShiftKeyDown()) return;

        if (!hasSkybound(player)) return;

        Vec3 look = player.getLookAngle();
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.add(
                look.x * 0.1D + (look.x * 1.5D - motion.x) * 0.5D,
                look.y * 0.1D + (look.y * 1.5D - motion.y) * 0.5D,
                look.z * 0.1D + (look.z * 1.5D - motion.z) * 0.5D));
    }

    /** The elytra doing the gliding may sit in the chest slot or in the mod's accessory elytra slot. */
    private static boolean hasSkybound(Player player) {
        if (EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.SKYBOUND.get(), player.getItemBySlot(EquipmentSlot.CHEST)) > 0) {
            return true;
        }
        return player.getCapability(ModCapabilities.ACCESSORY_HANDLER)
                .map(h -> EnchantmentHelper.getItemEnchantmentLevel(
                        ModEnchantments.SKYBOUND.get(), h.getStackInSlot(AccessoryHandler.SLOT_ELYTRA)) > 0)
                .orElse(false);
    }
}
