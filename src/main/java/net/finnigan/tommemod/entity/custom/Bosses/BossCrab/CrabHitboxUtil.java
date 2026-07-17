package net.finnigan.tommemod.entity.custom.Bosses.BossCrab;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.stream.Collectors;

public final class CrabHitboxUtil {

    private CrabHitboxUtil() {}

    /**
     * True if target is within a forward-facing cone from the attacker.
     * angleDegrees is the FULL arc width (90 = 45 degrees to each side of facing).
     * verticalReach is how far above/below the crab's own Y the target can be and still count.
     */
    public static boolean isInCone(Mob attacker, LivingEntity target, double range, double angleDegrees, double verticalReach) {
        Vec3 toTarget = target.position().subtract(attacker.position());

        double horizontalDistSqr = toTarget.x * toTarget.x + toTarget.z * toTarget.z;
        if (horizontalDistSqr > range * range) {
            return false;
        }
        if (Math.abs(toTarget.y) > verticalReach) {
            return false;
        }

        Vec3 flatToTarget = new Vec3(toTarget.x, 0, toTarget.z);
        if (flatToTarget.lengthSqr() < 1.0E-4) {
            return true; // target is essentially on top of the crab
        }
        flatToTarget = flatToTarget.normalize();

        Vec3 look = attacker.getLookAngle().multiply(1, 0, 1).normalize();
        double dot = Mth.clamp(look.dot(flatToTarget), -1.0, 1.0);
        double angleBetween = Math.toDegrees(Math.acos(dot));

        return angleBetween <= (angleDegrees / 2.0);
    }

    /** True if target is within a vertical cylinder centered on the attacker. */
    public static boolean isInCylinder(Mob attacker, LivingEntity target, double horizontalRadius, double verticalReach) {
        Vec3 toTarget = target.position().subtract(attacker.position());
        double horizontalDistSqr = toTarget.x * toTarget.x + toTarget.z * toTarget.z;
        if (horizontalDistSqr > horizontalRadius * horizontalRadius) {
            return false;
        }
        return Math.abs(toTarget.y) <= verticalReach;
    }

    public static List<LivingEntity> getNearbyLivingExcludingSelf(Mob attacker, double maxRadius) {
        return attacker.level().getEntitiesOfClass(LivingEntity.class,
                attacker.getBoundingBox().inflate(maxRadius),
                e -> e != attacker && e.isAlive() && attacker.canAttack(e)
        ).stream().collect(Collectors.toList());
    }
}