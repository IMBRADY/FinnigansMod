package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.capability.reputation.ModReputationCapabilities;
import net.finnigan.tommemod.capability.reputation.ReputationTier;
import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.VillageRegion;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * A village's sole elder: no trading, gives quests (future work) and, once a player is trusted
 * enough, the option to become that village's permanent Chief. Never spawns naturally - only ever
 * placed by the Enchanting-Table trigger in ElderVillagerSpawnEvents.
 */
public class ElderVillagerEntity extends PathfinderMob {

    private static final EntityDataAccessor<Optional<UUID>> DATA_VILLAGE_ID =
            SynchedEntityData.defineId(ElderVillagerEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public ElderVillagerEntity(EntityType<? extends ElderVillagerEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
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

    @Nullable
    public UUID getChiefUUID() {
        UUID villageId = getVillageId();
        if (villageId == null || !(this.level() instanceof ServerLevel serverLevel)) return null;
        return VillageManager.get(serverLevel).getChief(villageId).orElse(null);
    }

    /**
     * Chief status is permanent and lives in VillageManager (keyed by village, not by this entity),
     * so it survives this Elder dying or despawning. Returns false if the village already has a chief.
     */
    public boolean trySetChief(UUID playerUUID) {
        UUID villageId = getVillageId();
        if (villageId == null || !(this.level() instanceof ServerLevel serverLevel)) return false;
        return VillageManager.get(serverLevel).trySetChief(villageId, playerUUID);
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
        // Behave like a normal villager: never despawn just for being far from a player.
        return false;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        // Only free up "one Elder per village" on a permanent removal, not a chunk unload/dimension change.
        UUID villageId = getVillageId();
        if (reason.shouldDestroy() && villageId != null && this.level() instanceof ServerLevel serverLevel) {
            VillageManager.get(serverLevel).unregisterElder(villageId, this.getUUID());
        }
        super.remove(reason);
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
        UUID villageId = getVillageId();
        if (villageId == null || !(this.level() instanceof ServerLevel serverLevel)) return;

        long readyAtTick = serverLevel.getGameTime() + ModConfig.ELDER_SUCCESSION_DELAY_TICKS.get();
        VillageManager.get(serverLevel).schedulePendingSuccession(villageId, readyAtTick);
    }

    private int tetherCheckCooldown;

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (--tetherCheckCooldown > 0) return;
        tetherCheckCooldown = ModConfig.ELDER_TETHER_CHECK_INTERVAL_TICKS.get();
        if (this.level() instanceof ServerLevel serverLevel) tetherToVillage(serverLevel);
    }

    /**
     * Keeps the Elder from wandering off the edge of the map: once it strays past the configured
     * distance from its village's resolved anchor, snap it straight back rather than pathing home.
     */
    private void tetherToVillage(ServerLevel serverLevel) {
        UUID villageId = resolveOrGetVillageId();
        if (villageId == null) return;

        VillageRegion region = VillageManager.get(serverLevel).resolveVillageRegion(serverLevel, villageId);
        int maxWander = ModConfig.ELDER_MAX_WANDER_BLOCKS.get();
        if (this.blockPosition().distSqr(region.anchor()) <= (double) maxWander * maxWander) return;

        BlockPos anchor = region.anchor();
        this.teleportTo(anchor.getX() + 0.5, anchor.getY() + 1.0, anchor.getZ() + 0.5);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Zombie.class, 8.0F, 0.6D, 0.6D));
        this.goalSelector.addGoal(2, new SleepInNearbyBedGoal(this));
        this.goalSelector.addGoal(3, new RandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(4, new CongregateGoal(this));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return InteractionResult.sidedSuccess(true);
        }

        UUID villageId = resolveOrGetVillageId();
        if (villageId == null) {
            Component message = Component.literal("This doesn't feel like an established village yet.")
                    .withStyle(ChatFormatting.GRAY);
            player.displayClientMessage(message, true);
            return InteractionResult.CONSUME;
        }

        ReputationTier tier = player.getCapability(ModReputationCapabilities.REPUTATION_HANDLER)
                .map(handler -> handler.getTier(villageId))
                .orElse(ReputationTier.NOVICE);

        if (!tier.isAtLeast(ReputationTier.APPRENTICE)) {
            Component message = Component.literal("The Elder does not trust you enough to speak")
                    .withStyle(ChatFormatting.GRAY);
            player.displayClientMessage(message, true);
            return InteractionResult.CONSUME;
        }

        UUID existingChief = getChiefUUID();
        if (existingChief != null) {
            Component message = Component.literal(existingChief.equals(player.getUUID()) ? "You are already the Chief of this village."
                    : "This village already has a Chief.")
                    .withStyle(ChatFormatting.GRAY);
            player.displayClientMessage(message, true);
            return InteractionResult.CONSUME;
        }

        MutableComponent offer = Component.literal("[Become Village Chief]")
                .withStyle(ChatFormatting.GREEN)
                .withStyle(style -> style.withClickEvent(
                        new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tommemod chief confirm " + villageId)));
        player.sendSystemMessage(Component.literal("The Elder offers you leadership of this village. ").append(offer));
        return InteractionResult.CONSUME;
    }

    /**
     * Approximates vanilla villagers' Brain-based sleep behavior using the same underlying
     * LivingEntity#startSleeping/stopSleeping primitives that behavior ultimately calls into.
     */
    private static class SleepInNearbyBedGoal extends Goal {
        private static final Predicate<Holder<PoiType>> HOME_POI = holder -> holder.is(PoiTypeTags.VILLAGE) && holder.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME);
        private static final int SEARCH_RADIUS = 32;

        private final ElderVillagerEntity mob;
        @Nullable
        private BlockPos bedPos;

        SleepInNearbyBedGoal(ElderVillagerEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mob.level().isNight() && findBed().isPresent();
        }

        @Override
        public boolean canContinueToUse() {
            return mob.level().isNight();
        }

        @Override
        public void start() {
            bedPos = findBed().orElse(null);
        }

        @Override
        public void stop() {
            if (mob.isSleeping()) mob.stopSleeping();
            bedPos = null;
        }

        @Override
        public void tick() {
            if (bedPos == null || mob.isSleeping()) return;
            if (mob.blockPosition().closerThan(bedPos, 1.5)) {
                mob.getNavigation().stop();
                mob.startSleeping(bedPos);
            } else {
                mob.getNavigation().moveTo(bedPos.getX() + 0.5, bedPos.getY(), bedPos.getZ() + 0.5, 0.5);
            }
        }

        private Optional<BlockPos> findBed() {
            if (!(mob.level() instanceof ServerLevel level)) return Optional.empty();
            return level.getPoiManager()
                    .getInRange(HOME_POI, mob.blockPosition(), SEARCH_RADIUS, PoiManager.Occupancy.ANY)
                    .map(PoiRecord::getPos)
                    .min(Comparator.comparingDouble(p -> p.distSqr(mob.blockPosition())));
        }
    }

    /**
     * Loose approximation of vanilla villagers "congregating": occasionally wanders toward other
     * nearby villagers instead of purely random strolling.
     */
    private static class CongregateGoal extends Goal {
        private static final double NEAR_RADIUS = 32.0;
        private static final double CLOSE_ENOUGH = 10.0;

        private final ElderVillagerEntity mob;
        private int cooldown;

        CongregateGoal(ElderVillagerEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mob.isSleeping()) return false;
            if (cooldown-- > 0) return false;
            cooldown = 100 + mob.getRandom().nextInt(200);
            return findDistantVillager().isPresent();
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            findDistantVillager().ifPresent(target ->
                    mob.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 0.5));
        }

        private Optional<Villager> findDistantVillager() {
            return mob.level().getEntitiesOfClass(Villager.class, mob.getBoundingBox().inflate(NEAR_RADIUS)).stream()
                    .filter(v -> v.distanceToSqr(mob) > CLOSE_ENOUGH * CLOSE_ENOUGH)
                    .min(Comparator.comparingDouble(v -> v.distanceToSqr(mob)));
        }
    }
}
