package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.finnigan.tommemod.entity.custom.MagicFireballEntity;
import net.finnigan.tommemod.item.custom.totems.TotemOfFireMagicItem;
import net.finnigan.tommemod.item.custom.totems.TotemUtil;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Totem of Fire Magic: right-clicking a Fire Charge hurls it as an exploding fireball, consuming the
 * charge, where it would otherwise do nothing at all. The projectile itself is entity.custom
 * .MagicFireballEntity, which carries the blast and the fire it leaves behind.
 *
 * Deliberately only the air/entity interaction (RightClickItem), never RightClickBlock. Aiming at a
 * block in reach still places fire the vanilla way - and, more importantly, a wearer holding a fire
 * charge can still open chests and doors, which taking over the block interaction would break.
 * Mobs are entity hits rather than block hits, so throwing at something still works point-blank.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class FireMagicTotemHandler {

    private static final int COOLDOWN_TICKS = 10; // half a second between shots
    /** Launch speed, in blocks per tick. A snowball leaves the hand at 1.5, an arrow at 3.0. */
    private static final float SPEED = 2.0F;

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        InteractionHand hand = event.getHand();

        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(Items.FIRE_CHARGE)) return;
        if (!hasTotem(player)) return;
        if (player.getCooldowns().isOnCooldown(Items.FIRE_CHARGE)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        Level level = player.level();
        if (!level.isClientSide) {
            MagicFireballEntity fireball = new MagicFireballEntity(level, player);
            fireball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, SPEED, 0.0F);
            level.addFreshEntity(fireball);

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.getCooldowns().addCooldown(Items.FIRE_CHARGE,
                    TotemUtil.applyCooldownReduction(player, COOLDOWN_TICKS));
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F,
                1.0F / (level.getRandom().nextFloat() * 0.4F + 0.8F));

        player.swing(hand);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static boolean hasTotem(Player player) {
        return player.getCapability(ModCapabilities.ACCESSORY_HANDLER)
                .map(handler -> {
                    ItemStack totem = handler.getStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY);
                    return totem.getItem() instanceof TotemOfFireMagicItem;
                })
                .orElse(false);
    }
}
