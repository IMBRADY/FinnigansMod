package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.SkillService;
import net.finnigan.tommemod.skill.xp.ModSkillActions;
import net.finnigan.tommemod.skill.xp.SkillAction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Turns a player's movement into skill actions.
 *
 * Distance is banked rather than posted every tick. A sprinting player covers about a fifth of a
 * block per tick, and posting that twenty times a second would mean twenty index lookups and twenty
 * floating-point crumbs of experience for every player on the server; banking until a whole block has
 * gone by cuts that by a factor of five and costs nothing in accuracy, because the leftover carries
 * forward rather than being dropped.
 *
 * Which kind of movement it was is decided once, per tick, in {@link #classify} - a player is doing
 * exactly one of these things at a time, and the order the checks are written in is the order of
 * precedence (a passenger is riding even if the vehicle is in water; an elytra beats sprinting).
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillMovementTracker {

    /** Below this, a tick's movement is treated as jitter rather than travel. */
    private static final double MOVEMENT_EPSILON = 0.001;
    /** Bank up to this much before posting, so an action is worth handling when it arrives. */
    private static final double POST_THRESHOLD_BLOCKS = 1.0;

    private record Banked(ResourceLocation action, double distance) {
    }

    private static final Map<UUID, Banked> BANKED = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.isSpectator()) return;

        double distance = Math.sqrt(
                square(player.getX() - player.xo) + square(player.getY() - player.yo) + square(player.getZ() - player.zo));
        if (distance < MOVEMENT_EPSILON) return;

        ResourceLocation action = classify(player);
        if (action == null) return;

        Banked banked = BANKED.get(player.getUUID());
        // Switching activity pays out what was banked under the old one rather than crediting it to
        // the new one - stepping off a horse should not turn the last half block of riding into
        // running.
        if (banked != null && !banked.action().equals(action)) {
            post(player, banked);
            banked = null;
        }

        double total = (banked == null ? 0.0 : banked.distance()) + distance;
        if (total >= POST_THRESHOLD_BLOCKS) {
            double whole = Math.floor(total);
            post(player, new Banked(action, whole));
            total -= whole;
        }
        BANKED.put(player.getUUID(), new Banked(action, total));
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillService.award(player, SkillAction.once(ModSkillActions.JUMP));
        }
    }

    /** A player who logs out mid-stride shouldn't leave their banked distance behind them. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BANKED.remove(event.getEntity().getUUID());
    }

    @Nullable
    private static ResourceLocation classify(Player player) {
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            if (vehicle instanceof Boat) return ModSkillActions.SAIL;
            if (vehicle instanceof AbstractMinecart) return ModSkillActions.MINECART;
            return vehicle instanceof LivingEntity ? ModSkillActions.RIDE : null;
        }

        if (player.isFallFlying()) return ModSkillActions.GLIDE;
        if (player.isInWater()) return ModSkillActions.SWIM;
        if (player.onClimbable()) return ModSkillActions.CLIMB;
        // Falling is only travel when it is a long way down - otherwise every step off a kerb counts.
        if (!player.onGround() && player.fallDistance > 3.0F) return ModSkillActions.FALL;
        if (player.isSprinting()) return ModSkillActions.SPRINT;
        return player.onGround() ? ModSkillActions.WALK : null;
    }

    private static void post(ServerPlayer player, Banked banked) {
        if (banked.distance() <= 0.0) return;
        SkillService.award(player, SkillAction.of(banked.action(), banked.distance()));
    }

    private static double square(double value) {
        return value * value;
    }
}
