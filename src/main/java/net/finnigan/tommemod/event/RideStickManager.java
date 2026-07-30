package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Backing state + per-tick movement for RideStickItem. A player can have at most one "held" entity
// at a time; grabbing any member of an existing ride-stack picks up the whole stack (its root vehicle),
// since everything riding that root already follows it around for free via vanilla passenger syncing.
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class RideStickManager {

    private static final double DEFAULT_DISTANCE = 5.0;
    private static final double MIN_DISTANCE = 1.0;
    private static final double MAX_DISTANCE = 60.0;
    private static final double RAY_DISTANCE = 512.0;
    private static final double SCROLL_STEP = 1.0;

    private static final Map<UUID, Integer> heldEntityId = new HashMap<>();
    private static final Map<UUID, Double> holdDistance = new HashMap<>();

    public static void onClickEntity(ServerPlayer player, LivingEntity clicked) {
        Entity held = getHeld(player);

        if (held == null) {
            Entity root = clicked.getRootVehicle();
            if (root instanceof LivingEntity rootLiving) {
                grab(player, rootLiving);
            }
            return;
        }

        if (held == clicked) {
            release(player, (LivingEntity) held);
            return;
        }

        held.stopRiding();
        held.startRiding(clicked, true);
        stopHolding(player, (LivingEntity) held);
    }

    public static void onClickNothing(ServerPlayer player) {
        Entity held = getHeld(player);
        if (held instanceof LivingEntity livingHeld) {
            release(player, livingHeld);
        }
    }

    public static void adjustDistance(ServerPlayer player, double scrollDelta) {
        if (getHeld(player) == null) return;
        UUID id = player.getUUID();
        double current = holdDistance.getOrDefault(id, DEFAULT_DISTANCE);
        holdDistance.put(id, Mth.clamp(current + scrollDelta * SCROLL_STEP, MIN_DISTANCE, MAX_DISTANCE));
    }

    public static LivingEntity rayTraceEntity(ServerPlayer player) {
        Vec3 start = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        Vec3 end = start.add(look.scale(RAY_DISTANCE));
        AABB box = new AABB(start, end).inflate(1.0);

        Entity held = getHeld(player);

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, start, end, box,
                e -> e instanceof LivingEntity && e.isAlive() && e != player && e != held);

        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    // True if `entity` is the mob this player is currently holding - used so a direct click on it
    // (it's floating right in the player's crosshair) is treated like "clicked nothing" instead of
    // re-selecting itself, letting rayTraceEntity look past it for a real target.
    public static boolean isHeld(ServerPlayer player, Entity entity) {
        Integer id = heldEntityId.get(player.getUUID());
        return id != null && entity.getId() == id;
    }

    private static void grab(ServerPlayer player, LivingEntity entity) {
        UUID id = player.getUUID();
        heldEntityId.put(id, entity.getId());
        holdDistance.putIfAbsent(id, DEFAULT_DISTANCE);
        entity.setNoGravity(true);
        if (entity instanceof Mob mob) mob.setNoAi(true);
    }

    private static void release(ServerPlayer player, LivingEntity entity) {
        stopHolding(player, entity);
    }

    private static void stopHolding(ServerPlayer player, LivingEntity entity) {
        heldEntityId.remove(player.getUUID());
        entity.setNoGravity(false);
        if (entity instanceof Mob mob) mob.setNoAi(false);
    }

    private static Entity getHeld(ServerPlayer player) {
        Integer id = heldEntityId.get(player.getUUID());
        if (id == null) return null;
        if (!(player.level() instanceof ServerLevel serverLevel)) return null;

        Entity entity = serverLevel.getEntity(id);
        if (entity == null || !entity.isAlive()) {
            heldEntityId.remove(player.getUUID());
            return null;
        }
        return entity;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        Entity held = getHeld(player);
        if (held == null) return;

        double distance = holdDistance.getOrDefault(player.getUUID(), DEFAULT_DISTANCE);
        Vec3 targetPos = player.getEyePosition(1.0F).add(player.getLookAngle().scale(distance));

        held.teleportTo(targetPos.x, targetPos.y, targetPos.z);
        held.setDeltaMovement(Vec3.ZERO);
        held.fallDistance = 0;
        held.hurtMarked = true;
    }
}
