package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers.DefendVillagersTargetGoal;
import net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers.GenericRangedBowAttackGoal;
import net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers.GenericRangedCrossbowAttackGoal;
import net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers.MeleeUnlessRangedAttackGoal;
import net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers.MusketAttackGoal;
import net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers.SeekArmorGoal;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Items;
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
public class WarriorVillagerEntity extends PathfinderMob implements MenuProvider, RangedAttackMob, CrossbowAttackMob {

    private static final EntityDataAccessor<Optional<UUID>> DATA_VILLAGE_ID =
            SynchedEntityData.defineId(WarriorVillagerEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_CHARGING_CROSSBOW =
            SynchedEntityData.defineId(WarriorVillagerEntity.class, EntityDataSerializers.BOOLEAN);

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
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_VILLAGE_ID, Optional.empty());
        this.entityData.define(DATA_CHARGING_CROSSBOW, false);
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

    /**
     * Normally villageId is stable for this Warrior's whole life. But VillageManager can silently
     * merge two villages together as a village grows, without ever touching an already-spawned
     * Warrior's cached id - leaving it pointing at a village id VillageManager no longer considers
     * established. Self-heals by re-resolving from this Warrior's current position (best-effort - if
     * it's also wandered away from the village, this can't recover, but that's strictly no worse than
     * staying stuck on an id that no longer exists at all).
     */
    @Nullable
    private UUID reconcileVillageId(ServerLevel serverLevel, VillageManager manager) {
        UUID cached = resolveOrGetVillageId();
        if (cached == null) return null;
        if (manager.isEstablished(cached)) return cached;

        UUID resolved = manager.resolveVillage(serverLevel, this.blockPosition()).orElse(null);
        if (resolved != null) setVillageId(resolved);
        return resolved != null ? resolved : cached;
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

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return this.isSleeping() ? null : SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 1.0F;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new GenericRangedCrossbowAttackGoal<>(this, 1.0D, 8.0F));
        this.goalSelector.addGoal(1, new GenericRangedBowAttackGoal<>(this, 1.0D, 20, 15.0F));
        this.goalSelector.addGoal(1, new MusketAttackGoal(this, 20.0D, 40));
        this.goalSelector.addGoal(2, new MeleeUnlessRangedAttackGoal(this, 1.0D, false)); // edit this num to change speed for which it sprints when attackGoal
        this.goalSelector.addGoal(3, new SeekArmorGoal(this));
        this.goalSelector.addGoal(4, new RandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this,
                Villager.class, ElderVillagerEntity.class, WarriorVillagerEntity.class, IronGolem.class));
        this.targetSelector.addGoal(2, new DefendVillagersTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false, null));
    }
    @Override
    public ItemStack getProjectile(ItemStack weapon) {
        if (weapon.getItem() instanceof BowItem || weapon.getItem() instanceof CrossbowItem) {
            return new ItemStack(Items.ARROW);
        }
        return super.getProjectile(weapon);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        if (this.isHolding(is -> is.getItem() instanceof CrossbowItem)) {
            this.performCrossbowAttack(this, 1.6F);
            return;
        }

        ItemStack bowStack = this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, item -> item instanceof BowItem));
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, bowStack, velocity);
        if (bowStack.getItem() instanceof BowItem bowItem) {
            arrow = bowItem.customArrow(arrow);
        }

        double dx = target.getX() - this.getX();
        double dy = target.getY(0.3333333333333333D) - arrow.getY();
        double dz = target.getZ() - this.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontalDist * 0.2F, dz, 1.6F, (float) (14 - this.level().getDifficulty().getId() * 4));

        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        this.level().addFreshEntity(arrow);
    }

    @Override
    public void shootCrossbowProjectile(LivingEntity target, ItemStack stack, Projectile projectile, float velocity) {
        this.shootCrossbowProjectile(this, target, projectile, velocity, 1.6F);
    }

    // Not part of CrossbowAttackMob's contract (that interface only ever calls the setter below) -
    // kept as a plain getter in case rendering wants an arm/charge pose later.
    public boolean isChargingCrossbow() {
        return this.entityData.get(DATA_CHARGING_CROSSBOW);
    }

    @Override
    public void setChargingCrossbow(boolean charging) {
        this.entityData.set(DATA_CHARGING_CROSSBOW, charging);
    }

    @Override
    public void onCrossbowAttackPerformed() {
        this.noActionTime = 0;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(this.level() instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.sidedSuccess(true);
        }

        // Authorization is based on this Warrior's own (persisted) village and that village's Chief -
        // deliberately not on how far the Warrior itself has wandered, so its Chief can still manage
        // its equipment even while it's out chasing a raid or defending a player far from the village.
        VillageManager manager = VillageManager.get(serverLevel);
        UUID villageId = reconcileVillageId(serverLevel, manager);
        if (villageId == null || !manager.isEstablished(villageId)) {
            Component message = Component.literal("This doesn't feel like an established village yet.")
                    .withStyle(ChatFormatting.GRAY);
            player.displayClientMessage(message, true);
            return InteractionResult.CONSUME;
        }

        UUID chief = manager.getChief(villageId).orElse(null);
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
