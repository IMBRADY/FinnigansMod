package net.finnigan.tommemod.item.custom;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.finnigan.tommemod.item.custom.totems.TotemUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class FeatherlightItem extends SwordItem {

    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("b2c1a6e4-5f3e-4a2d-9c1a-7e6f8d2b1a90");
    private static final int COOLDOWN_TICKS = 30;

    public FeatherlightItem(Tier tier, int attackDamageModifier, float attackSpeedModifier, Properties properties) {
        super(tier, attackDamageModifier, attackSpeedModifier, properties);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) { // CHANGED — return type is Multimap, not ImmutableMultimap
        if (slot == EquipmentSlot.MAINHAND) {
            Multimap<Attribute, AttributeModifier> modifiers = ImmutableMultimap.copyOf(super.getAttributeModifiers(slot, stack)); // CHANGED — declared as Multimap, built from ImmutableMultimap.copyOf(...)

            ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
            builder.putAll(modifiers);

            builder.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(
                    SPEED_MODIFIER_UUID,
                    "Featherlight speed boost",
                    0.20D,
                    AttributeModifier.Operation.MULTIPLY_TOTAL
            ));

            return builder.build();
        }
        return super.getAttributeModifiers(slot, stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide()) {
            Vec3 look = player.getLookAngle();

            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CLOUD,
                        player.getX(), player.getEyeY(), player.getZ(),
                        20,
                        0.6, 0.6, 0.6,
                        0.15);
            }

            double forwardStrength = 1.4D;
            Vec3 forward = new Vec3(look.x, 0, look.z).normalize().scale(forwardStrength);

            double jumpStrength = 0.6D;

            Vec3 boost = new Vec3(forward.x, jumpStrength, forward.z);

            player.setDeltaMovement(player.getDeltaMovement().add(boost));
            player.hurtMarked = true;

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.2F);
        }

        player.getCooldowns().addCooldown(this, TotemUtil.applyCooldownReduction(player, COOLDOWN_TICKS));

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}