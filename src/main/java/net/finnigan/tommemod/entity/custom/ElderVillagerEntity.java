package net.finnigan.tommemod.entity.custom;

import net.finnigan.tommemod.capability.reputation.ModReputationCapabilities;
import net.finnigan.tommemod.capability.reputation.ReputationTier;
import net.finnigan.tommemod.village.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * A village's sole elder: no trading, gives quests (future work) and, once a player is trusted
 * enough, the option to become that village's permanent Chief. Never spawns naturally - only ever
 * placed by the Enchanting-Table trigger in ElderVillagerSpawnEvents.
 */
public class ElderVillagerEntity extends PathfinderMob {

    private static final EntityDataAccessor<Optional<UUID>> DATA_CHIEF_UUID =
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
        this.entityData.define(DATA_CHIEF_UUID, Optional.empty());
    }

    @Nullable
    public UUID getChiefUUID() {
        return this.entityData.get(DATA_CHIEF_UUID).orElse(null);
    }

    public void setChiefUUID(@Nullable UUID uuid) {
        this.entityData.set(DATA_CHIEF_UUID, Optional.ofNullable(uuid));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID chief = getChiefUUID();
        if (chief != null) tag.putUUID("ChiefUUID", chief);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("ChiefUUID")) setChiefUUID(tag.getUUID("ChiefUUID"));
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        // Only free up "one Elder per village" on a permanent removal, not a chunk unload/dimension change.
        if (reason.shouldDestroy() && this.level() instanceof ServerLevel serverLevel) {
            VillageManager manager = VillageManager.get(serverLevel);
            manager.resolveVillage(serverLevel, this.blockPosition())
                    .ifPresent(villageId -> manager.unregisterElder(villageId, this.getUUID()));
        }
        super.remove(reason);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new RandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return InteractionResult.sidedSuccess(true);
        }

        BlockPos pos = this.blockPosition();
        Optional<UUID> villageId = VillageManager.get(serverLevel).resolveVillage(serverLevel, pos);
        if (villageId.isEmpty()) {
            player.sendSystemMessage(Component.literal("This doesn't feel like an established village yet."));
            return InteractionResult.CONSUME;
        }

        UUID village = villageId.get();
        ReputationTier tier = player.getCapability(ModReputationCapabilities.REPUTATION_HANDLER)
                .map(handler -> handler.getTier(village))
                .orElse(ReputationTier.NOVICE);

        if (!tier.isAtLeast(ReputationTier.APPRENTICE)) {
            player.sendSystemMessage(Component.literal("The Elder doesn't yet trust you enough to speak of leadership."));
            return InteractionResult.CONSUME;
        }

        UUID existingChief = getChiefUUID();
        if (existingChief != null) {
            player.sendSystemMessage(Component.literal(existingChief.equals(player.getUUID())
                    ? "You are already the Chief of this village."
                    : "This village already has a Chief."));
            return InteractionResult.CONSUME;
        }

        MutableComponent offer = Component.literal("[Become Village Chief]")
                .withStyle(style -> style.withClickEvent(
                        new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tommemod chief confirm " + village)));
        player.sendSystemMessage(Component.literal("The Elder offers you leadership of this village. ").append(offer));
        return InteractionResult.CONSUME;
    }
}
