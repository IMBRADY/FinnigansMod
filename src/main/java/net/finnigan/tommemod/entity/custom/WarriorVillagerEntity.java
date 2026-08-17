package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers.AidAllyTargetGoal;
import net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers.AnswerDistressCallGoal;
import net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers.DefendVillagersTargetGoal;
import net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers.HoldRaidLineGoal;
import net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers.ManBallistaGoal;
import net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers.GenericRangedBowAttackGoal;
import net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers.GenericRangedCrossbowAttackGoal;
import net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers.MeleeUnlessRangedAttackGoal;
import net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers.MusketAttackGoal;
import net.finnigan.tommemod.entity.custom.WarriorVillagerHelpers.SeekArmorGoal;
import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.item.ModItems;
import net.finnigan.tommemod.menu.WarriorVillagerMenu;
import net.finnigan.tommemod.village.VillageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
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
    // Biome variant carried over from the Villager this one was conscripted from, so a desert
    // villager stays a desert villager once armed. Synched because only the renderer consumes it.
    private static final EntityDataAccessor<String> DATA_VILLAGER_TYPE =
            SynchedEntityData.defineId(WarriorVillagerEntity.class, EntityDataSerializers.STRING);

    public static final String DEFAULT_VILLAGER_TYPE = "plains";

    /** Identifies the Healthy Warriors bonus so it can be replaced wholesale as the level changes. */
    private static final UUID HEALTHY_WARRIORS_MODIFIER =
            UUID.fromString("2b7f4c91-6d3a-4e58-9c02-1f8a5d3b7e64");
    /** How often a Warrior re-reads its village's Healthy Warriors level, in ticks. */
    private static final int HEALTHY_WARRIORS_REFRESH_INTERVAL_TICKS = 40;

    private int healthyWarriorsCooldown = 0;

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
                .add(Attributes.MOVEMENT_SPEED, 0.4D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 36.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_VILLAGE_ID, Optional.empty());
        this.entityData.define(DATA_CHARGING_CROSSBOW, false);
        this.entityData.define(DATA_VILLAGER_TYPE, DEFAULT_VILLAGER_TYPE);
    }

    /** The villager biome variant's registry path ("desert", "snow", "taiga", ...). */
    public String getVillagerType() {
        return this.entityData.get(DATA_VILLAGER_TYPE);
    }

    public void setVillagerType(VillagerType type) {
        ResourceLocation id = BuiltInRegistries.VILLAGER_TYPE.getKey(type);
        this.entityData.set(DATA_VILLAGER_TYPE, id != null ? id.getPath() : DEFAULT_VILLAGER_TYPE);
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
     *
     * Public because it is the only answer worth asking for: anything outside this class that wants to
     * know which village a Warrior belongs to wants the reconciled id, not the raw cached
     * {@link #getVillageId()}.
     */
    @Nullable
    public UUID reconcileVillageId(ServerLevel serverLevel, VillageManager manager) {
        UUID cached = resolveOrGetVillageId();
        if (cached == null) return null;
        if (manager.isEstablished(cached)) return cached;

        UUID resolved = manager.resolveVillage(serverLevel, this.blockPosition()).orElse(null);
        if (resolved != null) setVillageId(resolved);
        return resolved != null ? resolved : cached;
    }

    /**
     * Creepers are left alone. A Warrior that picks one up as a target charges it down and sets it off
     * inside the village - whatever it was defending eats the blast either way, so the fight is never
     * worth having. Refusing the target here rather than filtering each goal separately catches every
     * route to one that runs through TargetingConditions: hurting-back, nearest-hostile, and answering
     * a distress call all ask this first.
     */
    @Override
    public boolean canAttack(LivingEntity target) {
        if (target instanceof Creeper) return false;
        // A Ballista is village defence, not a mob - one reads as a hostile entity standing in the
        // village only because it happens to be built as one.
        if (target instanceof BallistaEntity) return false;
        return super.canAttack(target);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();

        if (--healthyWarriorsCooldown > 0) return;
        healthyWarriorsCooldown = HEALTHY_WARRIORS_REFRESH_INTERVAL_TICKS;
        refreshHealthyWarriorsBonus();
    }

    /**
     * Applies this village's Healthy Warriors level as a max-health bonus.
     *
     * Polled from the village rather than handed out at the moment of purchase, which is what makes a
     * Warrior conscripted (or summoned) after the upgrade arrive already strong, and what lets the
     * bonus follow a Warrior whose village merges into a better-upgraded neighbour. Village membership
     * comes from where the Warrior is standing when it has no conscription record of its own, so a
     * /summon'd Warrior inside the village counts as one of its defenders.
     */
    private void refreshHealthyWarriorsBonus() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;

        VillageManager manager = VillageManager.get(serverLevel);
        UUID villageId = reconcileVillageId(serverLevel, manager);
        double bonus = villageId == null ? 0.0
                : manager.getHealthyWarriorsLevel(villageId) * ModConfig.HEALTHY_WARRIORS_PERCENT_PER_LEVEL.get();

        AttributeModifier existing = maxHealth.getModifier(HEALTHY_WARRIORS_MODIFIER);
        if (existing != null && existing.getAmount() == bonus) return;
        if (existing == null && bonus <= 0.0) return;

        float previousMax = this.getMaxHealth();
        if (existing != null) maxHealth.removeModifier(HEALTHY_WARRIORS_MODIFIER);
        if (bonus > 0.0) {
            // Transient, so it is never written into this Warrior's NBT: a saved modifier would be
            // re-applied on top of itself every load, and a village losing the upgrade could never
            // take it back off again.
            maxHealth.addTransientModifier(new AttributeModifier(HEALTHY_WARRIORS_MODIFIER,
                    "Healthy Warriors", bonus, AttributeModifier.Operation.MULTIPLY_BASE));
        }

        // Hand over the new headroom as real health rather than leaving a gap for the Chief to heal by
        // hand - buying the upgrade should visibly do something to the Warriors standing around them.
        float gained = this.getMaxHealth() - previousMax;
        if (gained > 0.0F) this.setHealth(this.getHealth() + gained);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID villageId = getVillageId();
        if (villageId != null) tag.putUUID("VillageId", villageId);
        tag.putString("VillagerType", getVillagerType());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("VillageId")) setVillageId(tag.getUUID("VillageId"));
        // Warriors saved before biome variants existed have no tag - they read as the default.
        if (tag.contains("VillagerType", CompoundTag.TAG_STRING)) {
            this.entityData.set(DATA_VILLAGER_TYPE, tag.getString("VillagerType"));
        }
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
        // Above every attack goal: a Ballista outreaches and outhits anything a Warrior can carry, so
        // when one is available for a target too far to charge, crewing it is the right answer. It
        // stands itself down as soon as the fight closes, handing the flags back to the melee goal.
        this.goalSelector.addGoal(0, new ManBallistaGoal(this));
        this.goalSelector.addGoal(1, new GenericRangedCrossbowAttackGoal<>(this, 1.0D, 8.0F));
        this.goalSelector.addGoal(1, new GenericRangedBowAttackGoal<>(this, 1.0D, 20, 15.0F));
        this.goalSelector.addGoal(1, new MusketAttackGoal(this, 20.0D, 40));
        // followingTargetEvenIfNotSeen: without it the goal ends the moment the path does - which
        // happens every time a Warrior catches up to what it is hitting - and vanilla then makes it
        // wait a second before it may start again. A defender should stay on its target until the
        // target is dead.
        this.goalSelector.addGoal(2, new MeleeUnlessRangedAttackGoal(this, 1.0D, true)); // edit this num to change speed for which it sprints when attackGoal
        this.goalSelector.addGoal(3, new AnswerDistressCallGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new HoldRaidLineGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new SeekArmorGoal(this));
        this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this,
                Villager.class, ElderVillagerEntity.class, WarriorVillagerEntity.class, IronGolem.class));
        this.targetSelector.addGoal(2, new AidAllyTargetGoal(this));
        this.targetSelector.addGoal(3, new DefendVillagersTargetGoal(this));
        // randomInterval 0 rather than vanilla's 10: at 10 a Warrior that has just killed something
        // only re-scans on about one goal tick in five, which on its own is most of a second of
        // standing about between fights. There are few enough Warriors to afford scanning every tick.
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Monster.class, 0, true, false, null));
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
