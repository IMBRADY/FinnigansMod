package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.item.custom.totems.TotemUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

public class SanguisGladioItem extends SwordItem {

    private static final String TAG_KILLS = "SanguisKills";
    private final float attackSpeedValue;

    private static final float BASE_DAMAGE = 25.0F;
    private static final float MAX_DAMAGE = 40.0F;
    private static final int KILLS_PER_POINT = 5;
    private static final int MAX_TRACKED_KILLS = (int) ((MAX_DAMAGE - BASE_DAMAGE) * KILLS_PER_POINT); // 75

    private static final float HEAL_AMOUNT = 4.0F; // 2 hearts
    private static final int ABILITY_COST_KILLS = 5;
    private static final int ABILITY_COOLDOWN_TICKS = 0;

    private static final UUID BASE_ATTACK_SPEED_UUID = UUID.fromString("d8d8ff81-e2f6-4b2e-8b4b-000000000031");

    // Base vanilla attack-damage modifier UUID (subtracted since SwordItem already adds its own tier bonus)
    private static final UUID BASE_ATTACK_DAMAGE_UUID = UUID.fromString("d8d8ff81-e2f6-4b2e-8b4b-000000000030");

    @Override
    public float getDamage() {
        return BASE_DAMAGE; // or hook this to whatever stack context you have available, see note below
    }

    @Override
    public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, AttributeModifier> getAttributeModifiers(
            net.minecraft.world.entity.EquipmentSlot slot, ItemStack stack) {

        com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, AttributeModifier> modifiers =
                com.google.common.collect.HashMultimap.create(super.getAttributeModifiers(slot, stack));

        if (slot == net.minecraft.world.entity.EquipmentSlot.MAINHAND) {
            // Strip BOTH vanilla modifiers before replacing them
            modifiers.removeAll(Attributes.ATTACK_DAMAGE);
            modifiers.removeAll(Attributes.ATTACK_SPEED);

            float damage = getCurrentDamage(stack);
            modifiers.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                    BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", damage, AttributeModifier.Operation.ADDITION));

            modifiers.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                    BASE_ATTACK_SPEED_UUID, "Weapon modifier", this.attackSpeedValue, AttributeModifier.Operation.ADDITION));
        }

        return modifiers;
    }

    public SanguisGladioItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
        this.attackSpeedValue = attackSpeed;
    }

    public static int getKills(ItemStack stack) {
        return stack.getOrCreateTag().getInt(TAG_KILLS);
    }

    public static void addKill(ItemStack stack) {
        int kills = getKills(stack);
        if (kills < MAX_TRACKED_KILLS) {
            stack.getOrCreateTag().putInt(TAG_KILLS, kills + 1);
        }
    }

    public static float getCurrentDamage(ItemStack stack) {
        int kills = getKills(stack);
        int bonusPoints = kills / KILLS_PER_POINT;
        return BASE_DAMAGE + bonusPoints;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }

        int kills = getKills(stack);
        if (kills < ABILITY_COST_KILLS) {
            player.displayClientMessage(
                    Component.literal("Not enough blood spent — need " + ABILITY_COST_KILLS + " kills."), true);
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide) {
            stack.getOrCreateTag().putInt(TAG_KILLS, kills - ABILITY_COST_KILLS);
            player.heal(HEAL_AMOUNT);
            player.getCooldowns().addCooldown(this, TotemUtil.applyCooldownReduction(player, ABILITY_COOLDOWN_TICKS));
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.8F, 1.0F);
        DustParticleOptions blood = new DustParticleOptions(
                new Vector3f(0.8f, 0.0f, 0.0f), // dark red
                1.0f                            // size
        );
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    blood, // particle
                    player.getX(),
                    player.getY() + 1.0, // around chest/head height
                    player.getZ(),
                    20,                  // count
                    0.3,                 // x spread
                    0.5,                 // y spread
                    0.3,                 // z spread
                    0.01                 // speed
            );
        }



        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level,
                                List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {

        stack.hideTooltipPart(net.minecraft.world.item.ItemStack.TooltipPart.MODIFIERS);

        int kills = getKills(stack);
        float currentDamage = getCurrentDamage(stack);

        tooltip.add(Component.literal("Kills: " + kills + " / " + MAX_TRACKED_KILLS).withStyle(style -> style.withColor(0xAA0000)));
        tooltip.add(Component.literal("Damage: " + (int) currentDamage).withStyle(style -> style.withColor(0xCC3333)));

        // Mimics vanilla attack speed modifier, needed bc we modified attack speed in override func
        float effectiveSpeed = 4.0F + this.attackSpeedValue; // base 4.0 +
        tooltip.add(Component.literal(" " + trimTrailingZero(effectiveSpeed) + " Attack Speed")
                .withStyle(net.minecraft.ChatFormatting.DARK_GREEN));
    }

    private static String trimTrailingZero(float value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.valueOf(Math.round(value * 10) / 10.0F);
    }
}