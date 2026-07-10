package net.finnigan.tommemod.entity.custom;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SoulSummoner {

    private static final UUID SOUL_BUFF_HEALTH_UUID = UUID.fromString("8e6b1b3a-1111-4a2e-9c3d-000000000001");
    private static final UUID SOUL_BUFF_SPEED_UUID  = UUID.fromString("8e6b1b3a-1111-4a2e-9c3d-000000000002");
    private static final UUID SOUL_BUFF_DAMAGE_UUID = UUID.fromString("8e6b1b3a-1111-4a2e-9c3d-000000000003");

    public static final String SOUL_ALLY_TAG = "tommemod_soul_ally";

    private static void applySoulBuffs(PathfinderMob mob) {
        AttributeInstance maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && maxHealth.getModifier(SOUL_BUFF_HEALTH_UUID) == null) {
            maxHealth.addPermanentModifier(new AttributeModifier(
                    SOUL_BUFF_HEALTH_UUID, "Soul buff health", 1.5, AttributeModifier.Operation.MULTIPLY_BASE));
            mob.setHealth(mob.getMaxHealth()); // heal to full including the new bonus
        }

        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SOUL_BUFF_SPEED_UUID) == null) {
            speed.addPermanentModifier(new AttributeModifier(
                    SOUL_BUFF_SPEED_UUID, "Soul buff speed", 0.15, AttributeModifier.Operation.MULTIPLY_BASE));
        }

        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null && damage.getModifier(SOUL_BUFF_DAMAGE_UUID) == null) {
            damage.addPermanentModifier(new AttributeModifier(
                    SOUL_BUFF_DAMAGE_UUID, "Soul buff damage", 2.0, AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    private static void applySoulEffects(PathfinderMob mob) {
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 999999, 0, false, false));
        mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 999999, 0, false, false));
    }

    public static List<PathfinderMob> summonAll(Level level, Player owner, ListTag souls) {
        List<PathfinderMob> summoned = new ArrayList<>();
        for (int i = 0; i < souls.size(); i++) {
            PathfinderMob mob = summonOne(level, owner, souls.getCompound(i));
            if (mob != null) summoned.add(mob);
        }
        return summoned;
    }

    private static PathfinderMob summonOne(Level level, Player owner, CompoundTag soulData) {
        ResourceLocation id = ResourceLocation.tryParse(soulData.getString("EntityType"));
        if (id == null) return null;

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        Entity spawned = type.create(level);
        if (!(spawned instanceof PathfinderMob mob)) return null;

        double offsetX = (level.random.nextDouble() - 0.5) * 3;
        double offsetZ = (level.random.nextDouble() - 0.5) * 3;
        mob.moveTo(owner.getX() + offsetX, owner.getY(), owner.getZ() + offsetZ, owner.getYRot(), 0);

        if (soulData.contains("CustomName")) {
            mob.setCustomName(Component.literal(soulData.getString("CustomName")));
        }

        CompoundTag equipment = soulData.getCompound("Equipment");
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (equipment.contains(slot.getName())) {
                ItemStack item = ItemStack.of(equipment.getCompound(slot.getName()));
                mob.setItemSlot(slot, item);
                mob.setDropChance(slot, 0f);
            }
        }

        mob.addTag(SOUL_ALLY_TAG);
        mob.getPersistentData().putUUID("tommemod_soul_owner", owner.getUUID());
        applySoulBuffs(mob);     // <-- gives the ally +health, +speed, +damage
        applySoulEffects(mob);   // <-- gives the ally Glowing + Damage Resistance
        rewireAI(mob, owner);

        level.addFreshEntity(mob);
        return mob;
    }

    private static void rewireAI(PathfinderMob mob, Player owner) {
        GoalSelector goalSelector = ObfuscationReflectionHelper.getPrivateValue(Mob.class, mob, "goalSelector");
        GoalSelector targetSelector = ObfuscationReflectionHelper.getPrivateValue(Mob.class, mob, "targetSelector");
        if (goalSelector == null || targetSelector == null) return;

        goalSelector.removeAllGoals(goal -> true);
        targetSelector.removeAllGoals(goal -> true);

        goalSelector.addGoal(0, new FloatGoal(mob));
        goalSelector.addGoal(1, new MeleeAttackGoal(mob, 1.2, true));
        goalSelector.addGoal(2, new FollowOwnerGoal(mob, owner, 2.0, 10.0f));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(mob, 1.0));

        targetSelector.addGoal(0, new DefendOwnerTargetGoal(mob, owner));
        targetSelector.addGoal(1, new AssistOwnerTargetGoal(mob, owner));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, LivingEntity.class, 10, true, false,
                entity -> entity instanceof Enemy && !entity.getTags().contains(SOUL_ALLY_TAG)));
    }
}