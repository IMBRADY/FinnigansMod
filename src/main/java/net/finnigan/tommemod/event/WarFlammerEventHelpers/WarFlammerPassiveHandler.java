package net.finnigan.tommemod.event.WarFlammerEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.WarFlammerItem;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * War Flammer passive.
 * <p>
 * Per spec, the two halves have deliberately different gating:
 * - 1.2x MOVEMENT_SPEED / 1.2x MAX_HEALTH apply while the player is simply in the Nether — dimension-gated
 *   only, not tied to holding the sword.
 * - Fire/lava damage immunity and the lava-swim speed boost are gated on actually holding War Flammer,
 *   not on dimension — lava immunity reads as more intuitive as an always-on trait of the weapon than as
 *   Nether-locked.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class WarFlammerPassiveHandler {

    private static final UUID NETHER_SPEED_UUID = UUID.fromString("9d3f1c20-5555-4b3f-8a1d-000000000050");
    private static final UUID NETHER_HEALTH_UUID = UUID.fromString("9d3f1c20-5555-4b3f-8a1d-000000000051");
    private static final double NETHER_BONUS = 0.2; // net 1.2x, MULTIPLY_TOTAL

    // Scoped-down "swim through lava like water": not full water-swim physics (would need mixin work),
    // just a strong forward push in the look direction plus zeroed fall damage while submerged in lava.
    private static final double LAVA_SWIM_SPEED = 0.5;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        Level level = player.level();
        if (level.isClientSide) return;

        boolean inNether = level.dimension() == Level.NETHER;
        applyOrRemove(player, Attributes.MOVEMENT_SPEED, NETHER_SPEED_UUID, NETHER_BONUS, inNether);
        applyOrRemove(player, Attributes.MAX_HEALTH, NETHER_HEALTH_UUID, NETHER_BONUS, inNether);

        if (!WarFlammerItem.isHeldBy(player)) return;

        if (player.isInLava()) {
            Vec3 look = player.getLookAngle();
            Vec3 desired = look.scale(LAVA_SWIM_SPEED);
            player.setDeltaMovement(desired.x, desired.y * 0.5 + 0.05, desired.z);
            player.hurtMarked = true;
            player.fallDistance = 0;
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!WarFlammerItem.isHeldBy(player)) return;
        if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
            event.setCanceled(true);
        }
    }

    private static void applyOrRemove(Player player, Attribute attribute,
                                       UUID uuid, double amount, boolean shouldHave) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;

        if (shouldHave) {
            if (instance.getModifier(uuid) == null) {
                instance.addTransientModifier(new AttributeModifier(uuid, "War Flammer nether buff",
                        amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        } else {
            if (instance.getModifier(uuid) != null) {
                instance.removeModifier(uuid);
            }
        }
    }
}
