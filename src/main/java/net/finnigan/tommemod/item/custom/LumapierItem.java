package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.event.UniqueSwordEnforcementHandler;
import net.finnigan.tommemod.entity.custom.LumapierHelpers.LightBoltProjectileEntity;
import net.finnigan.tommemod.item.custom.totems.TotemUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Hold use to charge. Charging levitates the wielder; a fully charged release fires
 * one enormous rod of light in the direction they are aiming.
 */
public class LumapierItem extends SwordItem {

    public static final int CHARGE_TICKS = 40;
    private static final int COOLDOWN_TICKS = 100;
    private static final String TAG_CHARGING = "LumapierCharging";
    private static final String TAG_GRANTED_LEVITATION = "LumapierGrantedLevitation";
    private static final String TAG_CHARGING_BEAM = "LumapierChargingBeam";

    public LumapierItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    public static boolean isHeldBy(Player player) {
        return player.getMainHandItem().getItem() instanceof LumapierItem
                || player.getOffhandItem().getItem() instanceof LumapierItem;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!UniqueSwordEnforcementHandler.canUseUniqueSword(player, this)) {
            return InteractionResultHolder.pass(stack);
        }

        player.startUsingItem(hand);
        if (!level.isClientSide) {
            stack.getOrCreateTag().putBoolean(TAG_CHARGING, true);
            if (player instanceof ServerPlayer serverPlayer) {
                applyChargeLevitation(serverPlayer, stack);
                LightBoltProjectileEntity beam = new LightBoltProjectileEntity(level, serverPlayer);
                beam.setLumapierOwner(serverPlayer);
                beam.setPos(serverPlayer.getX(), serverPlayer.getEyeY() + 5.0D, serverPlayer.getZ());
                level.addFreshEntity(beam);
                stack.getOrCreateTag().putUUID(TAG_CHARGING_BEAM, beam.getUUID());
            }
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override public int getUseDuration(ItemStack stack) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingUseDuration) {
        if (!level.isClientSide && user instanceof ServerPlayer player) {
            applyChargeLevitation(player, stack);
            spawnAngelWings((ServerLevel) level, player);
        }
    }

    /** Refreshes a brisk shulker-style lift for the full charging period. */
    private static void applyChargeLevitation(ServerPlayer player, ItemStack stack) {
        player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 5, 2, false, false, false));
        stack.getOrCreateTag().putBoolean(TAG_GRANTED_LEVITATION, true);
    }

    /** Draws two fanned arcs of white motes behind the player: a pair of temporary angel wings. */
    private static void spawnAngelWings(ServerLevel level, ServerPlayer player) {
        double yaw = Math.toRadians(player.getYRot());
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        double backX = Math.sin(yaw) * 0.3D;
        double backZ = -Math.cos(yaw) * 0.3D;
        // Rotate the complete pair as it rises, turning the two opposed wings into a double helix.
        double helixAngle = player.tickCount * 0.24D;
        double helixCos = Math.cos(helixAngle);
        double helixSin = Math.sin(helixAngle);

        for (int side : new int[] {-1, 1}) {
            for (int feather = 0; feather < 7; feather++) {
                double spread = 0.25D + feather * 0.27D;
                double lift = 0.25D + Math.sin(feather * Math.PI / 6.0D) * 0.75D;
                double localX = rightX * side * spread + backX;
                double localZ = rightZ * side * spread + backZ;
                double rotatedX = localX * helixCos - localZ * helixSin;
                double rotatedZ = localX * helixSin + localZ * helixCos;
                double x = player.getX() + rotatedX;
                double y = player.getY() + 1.0D + lift;
                double z = player.getZ() + rotatedZ;
                level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.015D, 0.015D, 0.015D, 0.0D);
                // White ash has gravity, leaving a short falling trail beneath each bright wing mote.
                level.sendParticles(ParticleTypes.WHITE_ASH, x, y, z, 2, 0.06D, 0.04D, 0.06D, 0.015D);
            }
        }
    }

    /** Confirms that a hovering rod still belongs to the stack the owner is actively charging. */
    public static boolean isActivelyChargingRod(LivingEntity owner, LightBoltProjectileEntity rod) {
        if (!(owner instanceof Player player) || !player.isUsingItem()) return false;
        ItemStack stack = player.getUseItem();
        return stack.getItem() instanceof LumapierItem && stack.hasTag()
                && stack.getOrCreateTag().getBoolean(TAG_CHARGING)
                && stack.getOrCreateTag().hasUUID(TAG_CHARGING_BEAM)
                && stack.getOrCreateTag().getUUID(TAG_CHARGING_BEAM).equals(rod.getUUID());
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack stack, Player player) {
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            cancelInterruptedCharge(stack, serverPlayer);
        }
        return true;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!level.isClientSide && entity instanceof ServerPlayer player && stack.hasTag()
                && stack.getOrCreateTag().getBoolean(TAG_CHARGING)
                && (!player.isUsingItem() || player.getUseItem() != stack)) {
            cancelInterruptedCharge(stack, player);
        }
    }

    private static void cancelInterruptedCharge(ItemStack stack, ServerPlayer player) {
        if (stack.getOrCreateTag().hasUUID(TAG_CHARGING_BEAM)
                && player.serverLevel().getEntity(stack.getOrCreateTag().getUUID(TAG_CHARGING_BEAM))
                instanceof LightBoltProjectileEntity beam) {
            beam.discard();
        }
        stack.getOrCreateTag().remove(TAG_CHARGING_BEAM);
        stack.getOrCreateTag().remove(TAG_CHARGING);
        if (stack.getOrCreateTag().getBoolean(TAG_GRANTED_LEVITATION)) {
            stack.getOrCreateTag().remove(TAG_GRANTED_LEVITATION);
            player.removeEffect(MobEffects.LEVITATION);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int remainingUseDuration) {
        if (!level.isClientSide && user instanceof ServerPlayer player) {
            int charge = getUseDuration(stack) - remainingUseDuration;
            // Clean up a charge that began before the levitation change, without touching normal
            // creative-mode flight or another source of flight.
            if (stack.getOrCreateTag().getBoolean("LumapierGrantedFlight")) {
                stack.getOrCreateTag().remove("LumapierGrantedFlight");
                if (!player.getAbilities().instabuild) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
            }
            if (stack.getOrCreateTag().getBoolean(TAG_GRANTED_LEVITATION)) {
                stack.getOrCreateTag().remove(TAG_GRANTED_LEVITATION);
                player.removeEffect(MobEffects.LEVITATION);
            }
            stack.getOrCreateTag().remove(TAG_CHARGING);
            LightBoltProjectileEntity beam = null;
            if (stack.getOrCreateTag().hasUUID(TAG_CHARGING_BEAM)) {
                beam = ((ServerLevel) level).getEntity(stack.getOrCreateTag().getUUID(TAG_CHARGING_BEAM))
                        instanceof LightBoltProjectileEntity chargingBeam ? chargingBeam : null;
                stack.getOrCreateTag().remove(TAG_CHARGING_BEAM);
            }
            if (charge >= CHARGE_TICKS) {
                if (beam != null) beam.launch(player);
                player.getCooldowns().addCooldown(this, TotemUtil.applyCooldownReduction(player, COOLDOWN_TICKS));
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0F, 0.65F);
            } else if (beam != null) {
                beam.discard();
            }
        }
    }

    /** True while the weapon is being charged - drives the lumapier_0/lumapier_1 item-model swap. */
    public static boolean isFiring(ItemStack stack, Level level) {
        return stack.hasTag() && stack.getOrCreateTag().getBoolean(TAG_CHARGING);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
