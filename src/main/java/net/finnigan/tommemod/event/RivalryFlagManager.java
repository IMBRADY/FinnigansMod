package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class RivalryFlagManager {

    private static final String TEAM1_KEY = "Team1";
    private static final String TEAM2_KEY = "Team2";
    private static final String ID_KEY = "Id";
    private static final String NAME_KEY = "Name";

    // Rosters (pre-fight) live on the flag stack's NBT itself, so they follow the physical item and
    // show up in its tooltip. Once triggered, the live combatants are tracked here so that the first
    // casualty on either side can call the whole thing off.
    private static final Map<UUID, Set<UUID>> activeTeam1ByPlayer = new HashMap<>();
    private static final Map<UUID, Set<UUID>> activeTeam2ByPlayer = new HashMap<>();

    public static void addToTeam1(ItemStack stack, LivingEntity target) {
        removeFromTeam(stack, TEAM2_KEY, target.getUUID());
        addToTeam(stack, TEAM1_KEY, target);
    }

    public static void addToTeam2(ItemStack stack, LivingEntity target) {
        removeFromTeam(stack, TEAM1_KEY, target.getUUID());
        addToTeam(stack, TEAM2_KEY, target);
    }

    public static ListTag getTeam1(ItemStack stack) {
        return getTeamTag(stack, TEAM1_KEY);
    }

    public static ListTag getTeam2(ItemStack stack) {
        return getTeamTag(stack, TEAM2_KEY);
    }

    public static String entryName(CompoundTag entry) {
        return entry.getString(NAME_KEY);
    }

    /** Makes Team 1 and Team 2 mutually target each other, then clears both teams off the stack. */
    public static void triggerRivalry(ServerPlayer player, ItemStack stack) {
        Set<UUID> team1Ids = readIds(stack, TEAM1_KEY);
        Set<UUID> team2Ids = readIds(stack, TEAM2_KEY);
        if (team1Ids.isEmpty() || team2Ids.isEmpty()) return;

        ServerLevel level = player.serverLevel();
        List<Mob> team1 = resolve(level, team1Ids);
        List<Mob> team2 = resolve(level, team2Ids);

        for (Mob mob : team1) {
            Mob closest = findClosest(mob, team2);
            if (closest != null) {
                mob.setTarget(closest);
                mob.setAggressive(true);
            }
        }
        for (Mob mob : team2) {
            Mob closest = findClosest(mob, team1);
            if (closest != null) {
                mob.setTarget(closest);
                mob.setAggressive(true);
            }
        }

        activeTeam1ByPlayer.put(player.getUUID(), team1Ids);
        activeTeam2ByPlayer.put(player.getUUID(), team2Ids);

        CompoundTag tag = stack.getOrCreateTag();
        tag.remove(TEAM1_KEY);
        tag.remove(TEAM2_KEY);
    }

    /** As soon as either side takes a casualty, the survivors on both teams stand down. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        UUID victim = event.getEntity().getUUID();

        for (UUID playerId : new HashSet<>(activeTeam1ByPlayer.keySet())) {
            Set<UUID> team1 = activeTeam1ByPlayer.get(playerId);
            Set<UUID> team2 = activeTeam2ByPlayer.get(playerId);
            if (!team1.contains(victim) && !team2.contains(victim)) continue;

            ServerLevel level = (ServerLevel) event.getEntity().level();
            standDown(level, team1);
            standDown(level, team2);

            activeTeam1ByPlayer.remove(playerId);
            activeTeam2ByPlayer.remove(playerId);
        }
    }

    private static void standDown(ServerLevel level, Set<UUID> ids) {
        for (UUID id : ids) {
            Entity entity = level.getEntity(id);
            if (entity instanceof Mob mob && mob.isAlive()) {
                mob.setTarget(null);
                mob.setAggressive(false);
            }
        }
    }

    private static void addToTeam(ItemStack stack, String key, LivingEntity target) {
        CompoundTag tag = stack.getOrCreateTag();
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            if (list.getCompound(i).getUUID(ID_KEY).equals(target.getUUID())) return;
        }
        CompoundTag entry = new CompoundTag();
        entry.putUUID(ID_KEY, target.getUUID());
        entry.putString(NAME_KEY, target.getDisplayName().getString());
        list.add(entry);
        tag.put(key, list);
    }

    private static void removeFromTeam(ItemStack stack, String key, UUID id) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(key)) return;
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            if (list.getCompound(i).getUUID(ID_KEY).equals(id)) {
                list.remove(i);
                break;
            }
        }
        tag.put(key, list);
    }

    private static ListTag getTeamTag(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        return tag == null ? new ListTag() : tag.getList(key, Tag.TAG_COMPOUND);
    }

    private static Set<UUID> readIds(ItemStack stack, String key) {
        ListTag list = getTeamTag(stack, key);
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            ids.add(list.getCompound(i).getUUID(ID_KEY));
        }
        return ids;
    }

    private static List<Mob> resolve(ServerLevel level, Set<UUID> ids) {
        List<Mob> result = new ArrayList<>();
        for (UUID id : ids) {
            Entity entity = level.getEntity(id);
            // "generic attack function" - skip anything that isn't a Mob (players, armor stands, ...)
            // and mobs that don't even have an ATTACK_DAMAGE attribute (e.g. cows, chickens, sheep).
            if (entity instanceof Mob mob && mob.isAlive() && mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
                result.add(mob);
            }
        }
        return result;
    }

    private static Mob findClosest(Mob from, List<Mob> candidates) {
        Mob best = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (Mob candidate : candidates) {
            double distSqr = from.distanceToSqr(candidate);
            if (distSqr < bestDistSqr) {
                bestDistSqr = distSqr;
                best = candidate;
            }
        }
        return best;
    }
}
