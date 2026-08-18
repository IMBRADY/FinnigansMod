package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Vanguard tree's two ends: the Swashbuckler who is never where the blow landed, and the
 * Dreadnought who makes each blow worth more than the last.
 *
 * The subclass split is a split in what a fight is. Swashbuckler pays for a crowd - stacks off kills,
 * a roll out of trouble, blows that miss - so its numbers are small and arrive often. Dreadnought pays
 * for one large thing, in health scaling and in what it opens up for everybody else swinging at the
 * same target. Neither is a bigger version of the shared trunk above them.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillVanguardBonuses {

    private static final UUID KILL_STACK_SPEED_MODIFIER = UUID.fromString("2a7c1e34-8b95-4f60-a1d2-6c3e8b0947f1");

    /** How long a kill's stack survives, and how many may be held at once. */
    private static final int KILL_STACK_TICKS = 120;
    private static final int KILL_STACK_CAP = 5;

    /** Most of a blow that Giant Slayer may ever be: it scales a weapon, it does not replace one. */
    private static final double MAX_HEALTH_DAMAGE_CAP = 1.0;

    /** How long a Breach mark lasts, how far it reaches, and how deep it stacks. */
    private static final int MARK_TICKS = 100;
    private static final double MARK_RADIUS = 12.0;
    private static final int MARK_CAP = 4;

    /** Blocks per tick a dash carries, and how long it leaves the roller untouchable. */
    private static final double DASH_SPEED = 1.1;
    private static final int DASH_INVULNERABLE_TICKS = 8;
    private static final int DASH_COOLDOWN_TICKS = 60;

    private record Stacks(int count, long expiresAtTick) {
    }

    /** One player's Breach mark on one target: how deep, and until when. */
    private record Mark(UUID owner, int stacks, long expiresAtTick) {
    }

    private static final Map<UUID, Stacks> KILL_STACKS = new HashMap<>();
    private static final Map<UUID, Mark> MARKS = new HashMap<>();
    private static final Map<UUID, Long> DASH_READY_AT = new HashMap<>();
    private static final Map<UUID, Long> DASH_INVULNERABLE_UNTIL = new HashMap<>();

    // ---- Dealing damage ----

    /**
     * Runs at LOWEST, after {@link SkillMeleeBonuses}, because Giant Slayer is a flat addition rather
     * than a multiplier. Added before the tree's percentages, it would be scaled by all of them and a
     * boss would take several times what the node advertises.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDealDamage(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.equals(event.getEntity())) return;
        if (event.getSource().getDirectEntity() != player) return;

        LivingEntity target = event.getEntity();

        double stacks = killStackBonus(player);
        if (stacks > 0.0) event.setAmount(event.getAmount() * (float) (1.0 + stacks));

        applyGiantSlayer(player, target, event);
        applyBreachMark(player, target);
    }

    /** Bloodrush: what the last few kills are still worth. */
    private static double killStackBonus(Player player) {
        double perStack = SkillBonuses.get(player, ModSkillBonuses.KILL_STACKS);
        return perStack <= 0.0 ? 0.0 : perStack * heldStacks(player);
    }

    private static int heldStacks(Player player) {
        Stacks held = KILL_STACKS.get(player.getUUID());
        if (held == null) return 0;
        if (player.level().getGameTime() >= held.expiresAtTick()) {
            KILL_STACKS.remove(player.getUUID());
            return 0;
        }
        return held.count();
    }

    /**
     * Giant Slayer: a share of what the target has, not of what you hit it with.
     *
     * Capped at the blow's own size, so it can at most double a hit. Uncapped, a percentage of a boss
     * health pool is a flat number large enough that the weapon in hand stops mattering, and the node
     * is meant to make a Dreadnought good against large things rather than to make everything else
     * they carry irrelevant.
     */
    private static void applyGiantSlayer(Player player, LivingEntity target, LivingHurtEvent event) {
        double share = SkillBonuses.get(player, ModSkillBonuses.MAX_HEALTH_DAMAGE);
        if (share <= 0.0) return;

        float added = (float) Math.min(target.getMaxHealth() * share, event.getAmount() * MAX_HEALTH_DAMAGE_CAP);
        if (added > 0.0F) event.setAmount(event.getAmount() + added);
    }

    /**
     * Breach: what this Dreadnought has opened up, everybody else can reach.
     *
     * The mark is stored against the target and remembers whose it is, so two Dreadnoughts working the
     * same boss do not stack their marks into a party-wide guaranteed critical - the later mark
     * replaces the earlier one at its own owner's strength.
     */
    private static void applyBreachMark(Player player, LivingEntity target) {
        double perStack = SkillBonuses.get(player, ModSkillBonuses.ALLY_CRIT_MARK);
        if (perStack <= 0.0) return;

        long now = player.level().getGameTime();
        Mark current = MARKS.get(target.getUUID());
        boolean continues = current != null
                && current.owner().equals(player.getUUID())
                && now < current.expiresAtTick();

        int stacks = continues ? Math.min(current.stacks() + 1, MARK_CAP) : 1;
        MARKS.put(target.getUUID(), new Mark(player.getUUID(), stacks, now + MARK_TICKS));
    }

    /**
     * The critical chance a marked target hands to everybody except the one who marked it.
     *
     * Read by {@link SkillCombatBonuses} where crits are decided. The marker is excluded because the
     * node's whole subject is what a Dreadnought does for a party; paying themselves would make it a
     * personal damage node with extra steps.
     */
    public static double critChanceAgainst(Player attacker, LivingEntity target) {
        Mark mark = MARKS.get(target.getUUID());
        if (mark == null || mark.owner().equals(attacker.getUUID())) return 0.0;
        if (attacker.level().getGameTime() >= mark.expiresAtTick()) {
            MARKS.remove(target.getUUID());
            return 0.0;
        }

        Player marker = attacker.level().getPlayerByUUID(mark.owner());
        if (marker == null) return 0.0;
        if (marker.distanceToSqr(target) > MARK_RADIUS * MARK_RADIUS) return 0.0;

        return SkillBonuses.get(marker, ModSkillBonuses.ALLY_CRIT_MARK) * mark.stacks();
    }

    // ---- Taking damage ----

    /**
     * Evasion and the dash's own moment of grace, both on LivingAttackEvent.
     *
     * Cancelling here rather than zeroing the amount in LivingHurtEvent means the blow never lands at
     * all - no knockback, no hurt animation, no durability off the armor - which is what dodging one
     * is. Zeroing the damage instead produces a hit that staggers you and takes nothing, which reads
     * as a bug rather than as a miss.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        Long until = DASH_INVULNERABLE_UNTIL.get(player.getUUID());
        if (until != null && player.level().getGameTime() < until) {
            event.setCanceled(true);
            return;
        }

        // Nothing dodges the void or a /kill: those sources exist precisely to be undodgeable, and a
        // player standing in the void who rolled a dodge would simply never die.
        double dodge = SkillBonuses.get(player, ModSkillBonuses.DODGE_CHANCE);
        if (dodge <= 0.0 || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        if (player.getRandom().nextDouble() < Math.min(dodge, 0.5)) {
            event.setCanceled(true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS, 0.3F, 1.8F);
        }
    }

    // ---- The dash ----

    /**
     * Rolls the player the way they are looking, briefly untouchable.
     *
     * Horizontal only. A dash that took the vertical component of the look angle would be a way to fly
     * up cliffs and to dive into the ground, neither of which is the node.
     *
     * Returns false when the node is unowned or still cooling down, so the caller can tell the player
     * why nothing happened.
     */
    public static boolean tryDash(Player player) {
        if (!SkillBonuses.has(player, ModSkillBonuses.DASH)) return false;

        long now = player.level().getGameTime();
        Long readyAt = DASH_READY_AT.get(player.getUUID());
        if (readyAt != null && now < readyAt) return false;

        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(0.0, 0.0, 1.0);

        player.push(flat.normalize().scale(DASH_SPEED).x, 0.15, flat.normalize().scale(DASH_SPEED).z);
        player.hurtMarked = true; // without this the server's push is never sent to the client

        DASH_READY_AT.put(player.getUUID(), now + DASH_COOLDOWN_TICKS);
        DASH_INVULNERABLE_UNTIL.put(player.getUUID(), now + DASH_INVULNERABLE_TICKS);

        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.6F, 1.4F);
        return true;
    }

    // ---- Over time ----

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (SkillBonuses.get(player, ModSkillBonuses.KILL_STACKS) <= 0.0) return;

        long now = player.level().getGameTime();
        int held = heldStacks(player);
        KILL_STACKS.put(player.getUUID(),
                new Stacks(Math.min(held + 1, KILL_STACK_CAP), now + KILL_STACK_TICKS));
    }

    /** The speed half of Bloodrush, which has to be an attribute rather than a number read at hit time. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) return;

        Player player = event.player;
        double perStack = SkillBonuses.get(player, ModSkillBonuses.KILL_STACKS);
        applySpeed(player, perStack <= 0.0 ? 0.0 : perStack * heldStacks(player));
    }

    private static void applySpeed(Player player, double amount) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;

        AttributeModifier existing = speed.getModifier(KILL_STACK_SPEED_MODIFIER);
        if (amount <= 0.0) {
            if (existing != null) speed.removeModifier(KILL_STACK_SPEED_MODIFIER);
            return;
        }
        if (existing != null && existing.getAmount() == amount) return;

        if (existing != null) speed.removeModifier(KILL_STACK_SPEED_MODIFIER);
        speed.addTransientModifier(new AttributeModifier(KILL_STACK_SPEED_MODIFIER, "Skill kill stacks",
                amount, AttributeModifier.Operation.MULTIPLY_BASE));
    }

    /**
     * Sweeps expired marks.
     *
     * Marks are keyed by target rather than by player, so nothing removes them when their subject dies
     * - the map would otherwise grow by one entry per marked mob for as long as the server runs.
     */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level) || MARKS.isEmpty()) return;
        if (level.getGameTime() % 100 != 0) return;

        long now = level.getGameTime();
        MARKS.entrySet().removeIf(entry -> now >= entry.getValue().expiresAtTick());
    }
}
