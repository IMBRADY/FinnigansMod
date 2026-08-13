package net.finnigan.tommemod.item.custom.totems;

import net.finnigan.tommemod.capability.accessory.AccessoryHandler;
import net.finnigan.tommemod.capability.accessory.ModCapabilities;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes every wall the wearer walks into behave like a ladder. The climbing itself is free: mixin
 * .LivingEntityClimbableMixin answers "yes, climbable" for the wearer, and vanilla's own ladder
 * handling then supplies the whole feel of it - walk into the wall to go up, sneak to hold position,
 * slow slide otherwise.
 *
 * Jumping off is the one part vanilla has no equivalent for, so it lives in
 * client.StickinessInputHandler, which pushes the player off the wall and opens the grace window
 * tracked here. Without that window the player would re-stick on the very next tick, since they are
 * still touching the wall they just jumped away from.
 */
public class TotemOfStickinessItem extends Item {

    /** How long after jumping off a wall the wearer stays non-sticky, in ticks. */
    private static final int DETACH_GRACE_TICKS = 10;

    /** How far past their own hitbox a clinging wearer may be from a wall and still hold on, in blocks. */
    private static final double WALL_REACH = 0.1;

    /** Player UUID -> the tickCount at which they may stick to walls again. */
    private static final Map<UUID, Integer> detachUntil = new ConcurrentHashMap<>();

    public TotemOfStickinessItem(Properties properties) {
        super(properties);
    }

    public static boolean isWornBy(Player player) {
        return player.getCapability(ModCapabilities.ACCESSORY_HANDLER)
                .map(handler -> {
                    ItemStack totem = handler.getStackInSlot(AccessoryHandler.SLOT_TOTEM_ACCESSORY);
                    return totem.getItem() instanceof TotemOfStickinessItem;
                })
                .orElse(false);
    }

    /**
     * Whether the wearer counts as clung to a wall right now - the single answer shared by
     * mixin.LivingEntityClimbableMixin and client.StickinessInputHandler, so that letting go of a wall
     * and jumping off one never disagree about whether there was a wall in the first place.
     *
     * Walking into something is what grabs it, which is why horizontalCollision alone can do the job on
     * the ground. It cannot keep the job, though: it is only true on ticks the player is actively
     * pushing into the wall, so on its own it drops anyone who stops holding forward - including
     * someone sneaking specifically to hold position. Once airborne, merely being within reach of a
     * wall is therefore enough to stay on it. That half is deliberately airborne-only, since a wearer
     * running along a wall on the ground would otherwise be clamped to climbing speed the whole way.
     */
    public static boolean isClingingToWall(Player player) {
        if (player.isSpectator() || player.getAbilities().flying) return false;
        if (isDetaching(player)) return false; // just jumped off - don't re-grab it
        if (!isWornBy(player)) return false;

        return player.horizontalCollision || (!player.onGround() && isWithinReachOfWall(player));
    }

    private static boolean isWithinReachOfWall(Player player) {
        // Widened sideways to feel for a wall, and pulled in vertically so the floor and ceiling
        // - which every standing player touches - are never mistaken for one.
        AABB reach = player.getBoundingBox().inflate(WALL_REACH, -WALL_REACH, WALL_REACH);
        for (VoxelShape shape : player.level().getBlockCollisions(player, reach)) {
            if (!shape.isEmpty()) return true;
        }
        return false;
    }

    public static void startDetachGrace(Player player) {
        detachUntil.put(player.getUUID(), player.tickCount + DETACH_GRACE_TICKS);
    }

    public static boolean isDetaching(Player player) {
        Integer until = detachUntil.get(player.getUUID());
        if (until == null) return false;
        if (player.tickCount >= until) {
            detachUntil.remove(player.getUUID());
            return false;
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Cling to walls and climb them like ladders").withStyle(style -> style.withColor(0x9422AB)));
        tooltip.add(Component.literal("Sneak to hold still, jump to let go").withStyle(style -> style.withColor(0x9422AB)));
        tooltip.add(Component.literal("Accessory Item").withStyle(style -> style.withColor(0x5D156B)));
        // literal displays exactly as is, translatable grabs from json
    }
}
