package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.entity.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;

/**
 * The fireball a Totem of Fire Magic wearer hurls - see event.FireMagicTotemHandler for the throwing.
 *
 * Built on ThrowableItemProjectile rather than vanilla's SmallFireball on purpose. A fireball sets
 * itself alight and trails smoke every tick, and since it starts right at the wearer's eyes and only
 * accelerates from a standstill, all of that hangs in front of the camera for the first second of
 * flight. A throwable carries no such effects, so the shot leaves clean; the fire charge item is
 * still what gets rendered, so it reads the same at a distance.
 */
public class MagicFireballEntity extends ThrowableItemProjectile {

    /** Damage on a direct hit. A vanilla blaze fireball does 5. */
    private static final float DIRECT_DAMAGE = 6.0F;
    /** How long a directly-hit target burns for, in seconds. */
    private static final int DIRECT_BURN_SECONDS = 8;

    /** Blast size. Ghast fireballs are 1.0, TNT is 4.0. */
    private static final float EXPLOSION_POWER = 1.5F;
    /**
     * NONE hurts and knocks back entities but leaves terrain intact. Switch to MOB (obeys the
     * mobGriefing gamerule) or TNT (always) to let the blast break blocks - be aware the cooldown is
     * only half a second, so a wearer could take a base apart very quickly.
     */
    private static final Level.ExplosionInteraction EXPLOSION_INTERACTION = Level.ExplosionInteraction.NONE;

    /** How far from the impact point fire is laid down, in blocks. */
    private static final double FIRE_RADIUS = 3.5;
    /** How much less likely fire is to catch at the rim than at ground zero, so the burn looks ragged. */
    private static final double FIRE_EDGE_FALLOFF = 0.75;

    /** Flight is gravity-free, so it needs a hard stop or a miss travels until it leaves the world. */
    private static final int MAX_LIFETIME_TICKS = 60;

    public MagicFireballEntity(EntityType<? extends MagicFireballEntity> type, Level level) {
        super(type, level);
    }

    public MagicFireballEntity(Level level, LivingEntity thrower) {
        super(ModEntityTypes.MAGIC_FIREBALL.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.FIRE_CHARGE;
    }

    /** Fireballs fly flat - you aim them where you are looking, not on an arc. */
    @Override
    protected float getGravity() {
        return 0.0F;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide() && this.tickCount > MAX_LIFETIME_TICKS) {
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide()) return;

        Entity target = result.getEntity();
        Entity owner = this.getOwner();

        int burningBefore = target.getRemainingFireTicks();
        target.setSecondsOnFire(DIRECT_BURN_SECONDS);
        if (!target.hurt(fireballDamage(owner), DIRECT_DAMAGE)) {
            // The hit was refused (invulnerable, immune, still in hurt cooldown) - don't leave it lit either.
            target.setRemainingFireTicks(burningBefore);
        } else if (owner instanceof LivingEntity livingOwner) {
            this.doEnchantDamageEffects(livingOwner, target);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result); // resolves onHitEntity / onHitBlock first
        if (this.level().isClientSide()) return;

        this.level().explode(this, this.getX(), this.getY(), this.getZ(),
                EXPLOSION_POWER, false, EXPLOSION_INTERACTION);
        this.scorchArea();
        this.discard();
    }

    /** Lays fire across everything around the impact point that will hold a flame. */
    private void scorchArea() {
        Level level = this.level();
        BlockPos impact = this.blockPosition();
        int reach = Mth.ceil(FIRE_RADIUS);

        for (BlockPos pos : BlockPos.betweenClosed(impact.offset(-reach, -reach, -reach),
                impact.offset(reach, reach, reach))) {
            double distance = Math.sqrt(pos.distToCenterSqr(this.position()));
            if (distance > FIRE_RADIUS) continue;

            // Thin the fire out toward the rim so the burn reads as a spreading patch, not a stamped sphere.
            if (this.random.nextDouble() > 1.0 - (distance / FIRE_RADIUS) * FIRE_EDGE_FALLOFF) continue;

            if (!BaseFireBlock.canBePlacedAt(level, pos, Direction.UP)) continue;
            level.setBlockAndUpdate(pos.immutable(), BaseFireBlock.getState(level, pos));
        }
    }

    /**
     * Vanilla's DamageSources.fireball only accepts a Fireball entity, which this deliberately isn't,
     * so the same damage type is built by hand - it counts as both fire and a projectile, which is
     * what makes Fire Resistance and Projectile Protection behave as a player would expect.
     */
    private DamageSource fireballDamage(@Nullable Entity owner) {
        return new DamageSource(
                this.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(owner == null ? DamageTypes.UNATTRIBUTED_FIREBALL : DamageTypes.FIREBALL),
                this, owner);
    }
}
