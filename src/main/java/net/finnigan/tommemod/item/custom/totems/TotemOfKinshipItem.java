package net.finnigan.tommemod.item.custom.totems;

import net.finnigan.tommemod.entity.custom.UndeadSwordHelpers.SoulSummoner;
import net.finnigan.tommemod.item.custom.ITotemEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class TotemOfKinshipItem extends Item implements ITotemEffect {

    private static final double RADIUS = 20.0;
    private static final double SPEED_MULT = 0.10;
    private static final double DAMAGE_MULT = 0.15;
    private static final double HEALTH_MULT = 0.15;
    private static final double ARMOR_MULT = 0.15;

    private static final UUID SPEED_ID = UUID.fromString("d1e2f3a4-0001-4a1a-9a1a-000000000010");
    private static final UUID DAMAGE_ID = UUID.fromString("d1e2f3a4-0002-4a1a-9a1a-000000000011");
    private static final UUID HEALTH_ID = UUID.fromString("d1e2f3a4-0003-4a1a-9a1a-000000000012");
    private static final UUID ARMOR_ID = UUID.fromString("d1e2f3a4-0004-4a1a-9a1a-000000000013");

    // Tracks which entities are currently buffed per player, so we can clean up when they leave range/unequip
    private static final Map<UUID, Set<UUID>> buffedEntitiesByPlayer = new HashMap<>();

    public TotemOfKinshipItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onPlayerTick(Player player, ItemStack totemStack) {
        Level level = player.level();
        if (level.isClientSide) return;

        Set<UUID> previouslyBuffed = buffedEntitiesByPlayer.computeIfAbsent(player.getUUID(), k -> new HashSet<>());

        AABB range = player.getBoundingBox().inflate(RADIUS);
        List<LivingEntity> currentTargets = level.getEntitiesOfClass(LivingEntity.class, range,
                TotemOfKinshipItem::isToyTarget);

        Set<UUID> currentIds = new HashSet<>();
        for (LivingEntity entity : currentTargets) {
            currentIds.add(entity.getUUID());
            applyBuffs(entity);
        }

        // Clear anyone who was buffed last tick but is no longer in range/valid
        previouslyBuffed.removeIf(id -> {
            if (!currentIds.contains(id)) {
                level.getEntitiesOfClass(LivingEntity.class, range, e -> e.getUUID().equals(id))
                        .forEach(TotemOfKinshipItem::clearBuffs);
                return true;
            }
            return false;
        });

        previouslyBuffed.addAll(currentIds);
    }

    /** Call this when the totem is unequipped entirely to strip all active buffs immediately. */
    public static void clearAllForPlayer(Player player, Level level) {
        Set<UUID> ids = buffedEntitiesByPlayer.remove(player.getUUID());
        if (ids == null || ids.isEmpty()) return;
        AABB wide = player.getBoundingBox().inflate(RADIUS + 5.0);
        level.getEntitiesOfClass(LivingEntity.class, wide, e -> ids.contains(e.getUUID()))
                .forEach(TotemOfKinshipItem::clearBuffs);
    }

    private static boolean isToyTarget(LivingEntity entity) {
        if (entity instanceof TamableAnimal tamable && tamable.isTame()) return true;
        return entity.getTags().contains(SoulSummoner.SOUL_ALLY_TAG);
    }

    private static void applyBuffs(LivingEntity entity) {
        apply(entity, Attributes.MOVEMENT_SPEED, SPEED_ID, SPEED_MULT);
        apply(entity, Attributes.ATTACK_DAMAGE, DAMAGE_ID, DAMAGE_MULT);
        apply(entity, Attributes.MAX_HEALTH, HEALTH_ID, HEALTH_MULT);
        apply(entity, Attributes.ARMOR, ARMOR_ID, ARMOR_MULT);
    }

    private static void clearBuffs(LivingEntity entity) {
        remove(entity, Attributes.MOVEMENT_SPEED, SPEED_ID);
        remove(entity, Attributes.ATTACK_DAMAGE, DAMAGE_ID);
        remove(entity, Attributes.MAX_HEALTH, HEALTH_ID);
        remove(entity, Attributes.ARMOR, ARMOR_ID);
    }

    private static void apply(LivingEntity entity, Attribute attribute, UUID id, double mult) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null || instance.getModifier(id) != null) return;
        instance.addTransientModifier(new AttributeModifier(id, "Totem of Toys", mult, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static void remove(LivingEntity entity, Attribute attribute, UUID id) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) instance.removeModifier(id);
    }
}