package net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Vanilla's Mob#pickUpItem only grabs gear it happens to touch while wandering, and equips it via
 * setItemSlotAndDropWhenKilled - which would undo the zero drop-chance WarriorVillagerEntity sets on
 * every slot at spawn (see equipStartingGear's comment: gear should never drop, only the Chief edits
 * it). This actively walks toward any armor piece on the ground for a slot the Warrior doesn't already
 * have filled - same idea as a villager beelining for dropped bread - and equips it via plain
 * setItemSlot so the "never drops" policy holds.
 */
public class SeekArmorGoal extends Goal {

    private static final double SEARCH_RADIUS = 8.0;
    private static final double REACH_DISTANCE_SQR = 2.25;
    private static final double SPEED_MODIFIER = 1.0D;

    private final Mob mob;
    private ItemEntity wantedItem;

    public SeekArmorGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        this.wantedItem = findWantedArmor();
        return this.wantedItem != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.wantedItem != null && this.wantedItem.isAlive()
                && !this.wantedItem.hasPickUpDelay() && isWantedArmor(this.wantedItem.getItem());
    }

    @Override
    public void start() {
        this.mob.getNavigation().moveTo(this.wantedItem, SPEED_MODIFIER);
    }

    @Override
    public void stop() {
        this.wantedItem = null;
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.wantedItem == null) return;

        this.mob.getLookControl().setLookAt(this.wantedItem, 10.0F, this.mob.getMaxHeadXRot());
        if (this.mob.distanceToSqr(this.wantedItem) <= REACH_DISTANCE_SQR) {
            equip(this.wantedItem);
            this.wantedItem = null;
        } else if (this.mob.getNavigation().isDone()) {
            this.mob.getNavigation().moveTo(this.wantedItem, SPEED_MODIFIER);
        }
    }

    private ItemEntity findWantedArmor() {
        AABB box = this.mob.getBoundingBox().inflate(SEARCH_RADIUS);
        List<ItemEntity> candidates = this.mob.level().getEntitiesOfClass(ItemEntity.class, box,
                item -> item.isAlive() && !item.hasPickUpDelay() && isWantedArmor(item.getItem()));
        return candidates.stream().min(Comparator.comparingDouble(this.mob::distanceToSqr)).orElse(null);
    }

    private boolean isWantedArmor(ItemStack stack) {
        EquipmentSlot slot = LivingEntity.getEquipmentSlotForItem(stack);
        return slot.isArmor() && this.mob.getItemBySlot(slot).isEmpty();
    }

    private void equip(ItemEntity itemEntity) {
        ItemStack stackOnGround = itemEntity.getItem();
        EquipmentSlot slot = LivingEntity.getEquipmentSlotForItem(stackOnGround);
        if (!slot.isArmor() || !this.mob.getItemBySlot(slot).isEmpty()) return; // someone else got there first

        ItemStack toEquip = stackOnGround.split(1);
        this.mob.onItemPickup(itemEntity);
        this.mob.take(itemEntity, 1);
        this.mob.setItemSlot(slot, toEquip);
        if (stackOnGround.isEmpty()) {
            itemEntity.discard();
        } else {
            itemEntity.setItem(stackOnGround);
        }
    }
}
