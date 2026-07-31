package net.finnigan.tommemod.item.custom.LumapierHelpers;

import net.finnigan.tommemod.entity.custom.LumapierHelpers.LightBoltProjectileEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Static pending-spray manager for Lumapier's "Light Spray" ability, ticked externally by
 * LightBoltTickHandler - same "static list ticked by a separate handler" idiom as War Flammer's
 * FireWaveManager/FireWaveTickHandler. Fires 5 light-bolt projectiles over ~1 second (4-tick gaps)
 * in a spray pattern, rather than all inline in LumapierItem#use.
 */
public class LightBoltManager {

    private static final List<PendingSpray> activeSprays = new ArrayList<>();

    private static final int SHOT_COUNT = 5;
    private static final int TICKS_BETWEEN_SHOTS = 4; // 5 shots * 4 ticks = 20 ticks, ~1 second
    private static final float BOLT_VELOCITY = 2.5F;
    private static final float SPRAY_SPREAD_DEGREES = 6.0F; // per-shot random spray offset

    private static class PendingSpray {
        final Level level;
        final Player owner;
        int shotIndex = 0;
        int ticksUntilNextShot = 0;

        PendingSpray(Level level, Player owner) {
            this.level = level;
            this.owner = owner;
        }
    }

    /** Kicks off a new 5-shot light spray from the player's current look direction. */
    public static void startSpray(Level level, Player owner) {
        activeSprays.add(new PendingSpray(level, owner));
    }

    /** Called once per server tick from LightBoltTickHandler. */
    public static void tick() {
        if (activeSprays.isEmpty()) return;

        List<PendingSpray> finished = new ArrayList<>();
        for (PendingSpray spray : activeSprays) {
            if (spray.owner == null || !spray.owner.isAlive()) {
                finished.add(spray);
                continue;
            }

            if (spray.ticksUntilNextShot > 0) {
                spray.ticksUntilNextShot--;
                continue;
            }

            fireShot(spray);
            spray.shotIndex++;
            spray.ticksUntilNextShot = TICKS_BETWEEN_SHOTS;

            if (spray.shotIndex >= SHOT_COUNT) {
                finished.add(spray);
            }
        }
        activeSprays.removeAll(finished);
    }

    private static void fireShot(PendingSpray spray) {
        Player owner = spray.owner;
        Level level = spray.level;

        LightBoltProjectileEntity bolt = new LightBoltProjectileEntity(level, owner);

        float spreadYaw = (level.getRandom().nextFloat() * 2.0F - 1.0F) * SPRAY_SPREAD_DEGREES;
        float spreadPitch = (level.getRandom().nextFloat() * 2.0F - 1.0F) * SPRAY_SPREAD_DEGREES;

        bolt.shootFromRotation(owner, owner.getXRot() + spreadPitch, owner.getYRot() + spreadYaw,
                0.0F, BOLT_VELOCITY, 0.0F);
        bolt.setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());

        level.addFreshEntity(bolt);

        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.4F, 1.8F + spray.shotIndex * 0.05F);
    }
}
