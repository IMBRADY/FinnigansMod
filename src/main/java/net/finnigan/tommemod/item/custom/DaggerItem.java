package net.finnigan.tommemod.item.custom;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.finnigan.tommemod.entity.custom.DaggerEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.UUID;

// Deliberately extends Item (not SwordItem/TieredItem): SwordItem's sweep-attack is gated by a hard
// `instanceof SwordItem` check in Player#attack, so daggers only lose it by not being one, even though
// their melee stats (attack damage/speed, durability, enchantability, repairing) are reproduced by hand below.
public class DaggerItem extends Item {

    // Vanilla's tooltip renderer (ItemStack#getTooltipLines) only adds the player's base attack
    // damage/speed (1.0 / 4.0) on top of a modifier's raw amount when that modifier's UUID exactly
    // equals Item.BASE_ATTACK_DAMAGE_UUID / BASE_ATTACK_SPEED_UUID - using our own UUIDs here (as a
    // prior version of this file did) meant the tooltip showed the raw modifier instead of the total,
    // e.g. attackSpeedModifier=-1.0F rendering as a literal "-1 Attack Speed" instead of "3".
    private static final UUID BASE_ATTACK_DAMAGE_UUID = Item.BASE_ATTACK_DAMAGE_UUID;
    private static final UUID BASE_ATTACK_SPEED_UUID = Item.BASE_ATTACK_SPEED_UUID;

    private static final int MIN_THROW_CHARGE_TICKS = 5;   // taps shorter than this are treated as "didn't mean to throw"
    private static final int MAX_THROW_CHARGE_TICKS = 20;  // ticks of holding to reach max throw velocity
    private static final float MIN_THROW_VELOCITY = 1.2F;
    private static final float MAX_THROW_VELOCITY = 2.6F;

    private final Tier tier;
    private final float attackDamage;
    private final Multimap<Attribute, AttributeModifier> defaultModifiers;

    public static final Set<Enchantment> ALLOWED_ENCHANTMENTS = Set.of(
            Enchantments.SHARPNESS,
            Enchantments.SMITE,
            Enchantments.BANE_OF_ARTHROPODS,
            Enchantments.KNOCKBACK,
            Enchantments.FIRE_ASPECT,
            Enchantments.MOB_LOOTING,
            Enchantments.UNBREAKING,
            Enchantments.MENDING,
            Enchantments.VANISHING_CURSE
    );

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return ALLOWED_ENCHANTMENTS.contains(enchantment);
    }

    public DaggerItem(Tier tier, int attackDamageModifier, float attackSpeedModifier, Properties properties) {
        super(properties.durability(tier.getUses()));
        this.tier = tier;
        this.attackDamage = attackDamageModifier + tier.getAttackDamageBonus();

        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", this.attackDamage, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                BASE_ATTACK_SPEED_UUID, "Weapon modifier", attackSpeedModifier, AttributeModifier.Operation.ADDITION));
        this.defaultModifiers = builder.build();
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? this.defaultModifiers : super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // SwordItem normally does this; without it a melee-only dagger would never lose durability.
        stack.hurtAndBreak(1, attacker, p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return tier.getEnchantmentValue();
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return tier.getRepairIngredient().test(repair) || super.isValidRepairItem(toRepair, repair);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72; // generous hold window; actual charge is clamped at MAX_THROW_CHARGE_TICKS
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;

        int chargeTicks = Math.min(this.getUseDuration(stack) - timeLeft, MAX_THROW_CHARGE_TICKS);
        if (chargeTicks < MIN_THROW_CHARGE_TICKS) return; // too short a hold - treat as a cancelled throw

        float chargeFraction = Mth.clamp(chargeTicks / (float) MAX_THROW_CHARGE_TICKS, 0.0F, 1.0F);
        float velocity = Mth.lerp(chargeFraction, MIN_THROW_VELOCITY, MAX_THROW_VELOCITY);

        if (!level.isClientSide) {
            ItemStack thrown = stack.copy();
            thrown.setCount(1);

            DaggerEntity dagger = new DaggerEntity(level, player, thrown, this.attackDamage);
            dagger.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, velocity, 0.0F);
            level.addFreshEntity(dagger);

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        if (!player.getAbilities().instabuild) {
            // Throwing costs durability like a melee hit, rather than consuming the dagger outright -
            // the thrown copy is a plain visual/damage projectile, not a duplicate of the one in hand.
            stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
    }

    public Tier getTier() {
        return tier;
    }
}
