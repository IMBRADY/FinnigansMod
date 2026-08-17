package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.finnigan.tommemod.util.ModTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Defense tree: the shield, the armor, and what a fight costs somebody who expects to be hit.
 *
 * Most of these hang off blocking, which vanilla already does generously - a raised shield stops all
 * of the damage from in front of it. That is why none of these nodes is "block more": what a shield
 * costs you is your movement, your durability, and the fact that you are not attacking. Each node here
 * buys one of those back.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillDefenseBonuses {

    private static final UUID BLOCK_MOBILITY_MODIFIER = UUID.fromString("b7d3f240-91a6-4c85-8e12-5a04c76bd398");

    /** How long after a block the bash and the charge remain available. */
    private static final int BLOCK_WINDOW_TICKS = 40;
    /** How long a falloff stack survives without another hit from that same attacker. */
    private static final int FALLOFF_TIMEOUT_TICKS = 80;
    private static final int FALLOFF_CAP = 5;
    /** How long out of combat before armor starts mending and absorption grows back. */
    private static final int OUT_OF_COMBAT_TICKS = 300;
    private static final int ABSORPTION_IDLE_TICKS = 300;
    /** Absorption is granted in points; two per heart. */
    private static final int ABSORPTION_POINTS_PER_HEART = 2;
    /** How long Last Breath sits down for once it has saved somebody. */
    private static final int LAST_BREATH_COOLDOWN_TICKS = 1200;
    /** How far behind a guardian an ally may shelter. */
    private static final double GUARD_RADIUS = 3.0;
    /** Most of the knockback blocking may remove, so Unmoved reduces rather than negates. */
    private static final double BLOCK_KNOCKBACK_CAP = 0.8;

    private record Falloff(int stacks, long lastHitTick) {
    }

    /** Keyed victim+attacker, so each pairing keeps its own count. */
    private static final Map<String, Falloff> FALLOFF = new HashMap<>();
    private static final Map<UUID, Long> BLOCKED_AT = new HashMap<>();
    private static final Map<UUID, Long> LAST_COMBAT = new HashMap<>();
    private static final Map<UUID, Long> LAST_BREATH_READY_AT = new HashMap<>();

    // ---- Blocking ----

    @SubscribeEvent
    public static void onBlock(ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Noted for Shield Bash and Retaliation, both of which are about what the block sets up rather
        // than about the block itself.
        BLOCKED_AT.put(player.getUUID(), player.level().getGameTime());
    }

    /** Brace: a raised shield stops costing you your feet. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) return;
        Player player = event.player;

        applyBlockMobility(player);
        applyArmorRepair(player);
        applyIdleAbsorption(player);
    }

    /**
     * Vanilla halves a blocking player's speed through a movement multiplier rather than an attribute,
     * so it cannot simply be cancelled - what it can be is compensated for, by adding back roughly what
     * the block took while the block is up.
     */
    private static void applyBlockMobility(Player player) {
        boolean wanted = player.isBlocking()
                && SkillBonuses.has(player, ModSkillBonuses.BLOCK_MOBILITY);

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;

        AttributeModifier existing = speed.getModifier(BLOCK_MOBILITY_MODIFIER);
        if (!wanted) {
            if (existing != null) speed.removeModifier(BLOCK_MOBILITY_MODIFIER);
            return;
        }
        if (existing != null) return;

        speed.addTransientModifier(new AttributeModifier(BLOCK_MOBILITY_MODIFIER, "Skill block mobility",
                1.0, AttributeModifier.Operation.MULTIPLY_BASE));
    }

    /**
     * Shield Bash and Retaliation, both cashed in on the first swing after a block.
     *
     * Runs at LOW so the damage it multiplies is the finished figure. The block is consumed either way,
     * so a player cannot bank one block and spend it on a whole exchange.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDealDamage(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (event.getSource().getDirectEntity() != player) return;

        Long blockedAt = BLOCKED_AT.remove(player.getUUID());
        if (blockedAt == null || player.level().getGameTime() - blockedAt > BLOCK_WINDOW_TICKS) return;

        double charge = SkillBonuses.get(player, ModSkillBonuses.BLOCK_CHARGE);
        if (charge > 0.0) event.setAmount(event.getAmount() * (float) (1.0 + charge));

        double bash = SkillBonuses.get(player, ModSkillBonuses.SHIELD_BASH);
        LivingEntity target = event.getEntity();
        if (bash <= 0.0 || target.getType().is(ModTags.EntityTypes.BOSSES)) return;

        target.knockback(bash, player.getX() - target.getX(), player.getZ() - target.getZ());
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                (int) Math.round(bash * 20), 2, false, false, false));
    }

    // ---- Taking damage ----

    /** Everything that softens a blow this player is receiving. */
    @SubscribeEvent
    public static void onTakeDamage(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        markInCombat(player);
        if (event.getSource().getEntity() instanceof Player attacker) markInCombat(attacker);

        double reduction = 0.0;
        reduction += falloffFor(player, event);
        if (player.isShiftKeyDown()) {
            reduction += SkillBonuses.get(player, ModSkillBonuses.SNEAK_DEFENSE);
        }
        reduction += guardFor(player);

        if (reduction > 0.0) {
            event.setAmount(event.getAmount() * (float) (1.0 - Math.min(reduction, 0.85)));
        }
    }

    /** Resilience: the same attacker gets less out of you the longer it keeps at it. */
    private static double falloffFor(Player player, LivingHurtEvent event) {
        double perStack = SkillBonuses.get(player, ModSkillBonuses.DAMAGE_FALLOFF);
        if (perStack <= 0.0) return 0.0;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return 0.0;

        String key = player.getUUID() + "|" + attacker.getUUID();
        long now = player.level().getGameTime();
        Falloff current = FALLOFF.get(key);

        int stacks = current != null && now - current.lastHitTick() <= FALLOFF_TIMEOUT_TICKS
                ? Math.min(current.stacks() + 1, FALLOFF_CAP)
                : 1;
        FALLOFF.put(key, new Falloff(stacks, now));

        return perStack * (stacks - 1); // the first hit of a series is not yet a series
    }

    /**
     * Aegis: standing behind somebody with a shield up is worth something.
     *
     * The guardian has to be blocking and the sheltering player has to be behind them, worked out from
     * the guardian's own facing - a shield covers what it is pointed at, and somebody stood in front of
     * it is in the way rather than behind cover.
     */
    private static double guardFor(Player sheltered) {
        double best = 0.0;
        for (Player guardian : sheltered.level().getEntitiesOfClass(Player.class,
                sheltered.getBoundingBox().inflate(GUARD_RADIUS))) {
            if (guardian == sheltered || !guardian.isBlocking()) continue;

            double bonus = SkillBonuses.get(guardian, ModSkillBonuses.GUARD_ALLY);
            if (bonus <= 0.0) continue;

            Vec3 toSheltered = sheltered.position().subtract(guardian.position());
            if (toSheltered.lengthSqr() < 1.0E-4) continue;
            // Behind the guardian means opposite the way they are facing.
            if (guardian.getLookAngle().dot(toSheltered.normalize()) > -0.2) continue;

            best = Math.max(best, bonus);
        }
        return best;
    }

    // ---- Out of combat ----

    private static void markInCombat(Player player) {
        LAST_COMBAT.put(player.getUUID(), player.level().getGameTime());
    }

    private static boolean isOutOfCombat(Player player, int forTicks) {
        Long last = LAST_COMBAT.get(player.getUUID());
        return last == null || player.level().getGameTime() - last >= forTicks;
    }

    /** Field Repair: armor comes back together between fights, not during them. */
    private static void applyArmorRepair(Player player) {
        if (player.tickCount % 20 != 0) return;

        double perSecond = SkillBonuses.get(player, ModSkillBonuses.ARMOR_REPAIR);
        if (perSecond <= 0.0 || !isOutOfCombat(player, OUT_OF_COMBAT_TICKS)) return;

        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack piece = player.getItemBySlot(slot);
            if (!(piece.getItem() instanceof ArmorItem) || !piece.isDamaged()) continue;

            int mended = (int) perSecond;
            if (player.getRandom().nextDouble() < perSecond - mended) mended++;
            if (mended > 0) piece.setDamageValue(Math.max(0, piece.getDamageValue() - mended));
        }
    }

    /**
     * Unbreakable: a shield of absorption that grows back when nobody is swinging at you.
     *
     * Absorption is a fixed pool rather than a multiplier, so it cannot compound however many nodes
     * feed it - it simply returns once you disengage, and damage eats it as normal.
     */
    private static void applyIdleAbsorption(Player player) {
        if (player.tickCount % 20 != 0) return;

        double hearts = SkillBonuses.get(player, ModSkillBonuses.OUT_OF_COMBAT_ABSORPTION);
        if (hearts <= 0.0 || !isOutOfCombat(player, ABSORPTION_IDLE_TICKS)) return;
        if (player.getAbsorptionAmount() >= hearts * ABSORPTION_POINTS_PER_HEART) return;

        int amplifier = (int) Math.max(0, Math.round(hearts / 2.0) - 1);
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, amplifier, true, false, false));
    }

    // ---- Last Breath ----

    /**
     * Whether this player's Warded should refuse a killing blow.
     *
     * Called from {@link net.finnigan.tommemod.mixin.LastBreathMixin} at the point vanilla has finished
     * asking about totems and answered no. That ordering is the whole design: a totem in hand or in the
     * accessory slot is always spent first, so this and the totems can never both fire.
     */
    public static boolean tryLastBreath(Player player, net.minecraft.world.damagesource.DamageSource source) {
        if (!SkillBonuses.has(player, ModSkillBonuses.LAST_BREATH)) return false;
        if (source.getEntity() != null && source.getEntity().getType().is(ModTags.EntityTypes.BOSSES)) {
            return false;
        }

        long now = player.level().getGameTime();
        if (now < LAST_BREATH_READY_AT.getOrDefault(player.getUUID(), 0L)) return false;
        LAST_BREATH_READY_AT.put(player.getUUID(), now + LAST_BREATH_COOLDOWN_TICKS);

        player.setHealth(1.0F);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        return true;
    }

    /** Read by ThornsDurabilityMixin, where the wear is actually charged. */
    public static boolean thornsSparesArmor(LivingEntity wearer) {
        return wearer instanceof Player player
                && SkillBonuses.has(player, ModSkillBonuses.THORNS_NO_WEAR);
    }

    /** Read by KnockbackReductionMixin: how much of a shove a blocking player shrugs off. */
    public static double blockedKnockbackReduction(LivingEntity entity) {
        if (!(entity instanceof Player player) || !player.isBlocking()) return 0.0;

        double reduction = SkillBonuses.reduction(player, ModSkillBonuses.BLOCK_KNOCKBACK_REDUCTION);
        return Math.min(reduction, BLOCK_KNOCKBACK_CAP);
    }
}
