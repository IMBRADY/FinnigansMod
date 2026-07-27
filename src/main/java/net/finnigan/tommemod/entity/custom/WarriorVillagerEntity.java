package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers.DefendVillagersTargetGoal;
import net.finnigan.tommemod.item.ModItems;
import net.finnigan.tommemod.menu.WarriorVillagerMenu;
import net.finnigan.tommemod.village.VillageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * A village's combat defender: holds a Halberd, wears armor, picks up better armor dropped nearby,
 * defends villagers like an Iron Golem, and attacks hostiles at night. Its equipment is editable
 * only by that village's Chief, through a custom menu. Never spawns naturally - only ever placed by
 * the Target-block job-site trigger in WarriorVillagerSpawnEvents.
 */
public class WarriorVillagerEntity extends PathfinderMob implements MenuProvider {

    private static final EntityDataAccessor<Optional<UUID>> DATA_VILLAGE_ID =
            SynchedEntityData.defineId(WarriorVillagerEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public WarriorVillagerEntity(EntityType<? extends WarriorVillagerEntity> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(true);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setDropChance(slot, 0.0F);
        }
        equipStartingGear();
    }

    // Spawns bare except for the Halberd - the whole point of the conscription mechanic (see
    // WarriorVillagerSpawnEvents) is that YOU arm it; armor comes from what it picks up or the
    // Chief equips afterward.
    private void equipStartingGear() {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.HALBERD.get()));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_VILLAGE_ID, Optional.empty());
    }

    @Nullable
    public UUID getVillageId() {
        return this.entityData.get(DATA_VILLAGE_ID).orElse(null);
    }

    public void setVillageId(@Nullable UUID villageId) {
        this.entityData.set(DATA_VILLAGE_ID, Optional.ofNullable(villageId));
    }

    @Nullable
    private UUID resolveOrGetVillageId() {
        UUID cached = getVillageId();
        if (cached != null) return cached;
        if (!(this.level() instanceof ServerLevel serverLevel)) return null;

        UUID resolved = VillageManager.get(serverLevel).resolveVillage(serverLevel, this.blockPosition()).orElse(null);
        if (resolved != null) setVillageId(resolved);
        return resolved;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID villageId = getVillageId();
        if (villageId != null) tag.putUUID("VillageId", villageId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("VillageId")) setVillageId(tag.getUUID("VillageId"));
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, false)); // edit this num to change speed for which it sprints when attackGoal
        this.goalSelector.addGoal(2, new RandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new DefendVillagersTargetGoal(this));
        // Attacks hostiles at any time of day, not just at night.
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false, null));
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(this.level() instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.sidedSuccess(true);
        }

        UUID villageId = resolveOrGetVillageId();
        if (villageId == null) {
            Component message = Component.literal("This doesn't feel like an established village yet.")
                    .withStyle(ChatFormatting.GRAY);
            player.displayClientMessage(message, true);
            return InteractionResult.CONSUME;
        }

        UUID chief = VillageManager.get((ServerLevel) this.level()).getChief(villageId).orElse(null);
        if (chief == null || !chief.equals(player.getUUID())) {
            Component message = Component.literal("Only this village's Chief may handle this Warrior's equipment.")
                    .withStyle(ChatFormatting.GRAY);
            player.displayClientMessage(message, true);
            return InteractionResult.CONSUME;
        }

        NetworkHooks.openScreen(serverPlayer, this, buf -> buf.writeInt(this.getId()));
        return InteractionResult.CONSUME;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("entity.tommemod.warrior_villager");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory inventory, Player player) {
        return new WarriorVillagerMenu(windowId, inventory, this);
    }
}
