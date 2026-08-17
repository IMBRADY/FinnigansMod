package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.MusketItem;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.finnigan.tommemod.util.ModTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Marksmanship tree: crossbows, muskets and thrown tridents - the weapons you aim rather than draw.
 *
 * Where Archery is about the draw, this is about the shot placed at distance and what it takes with it.
 * Two of its nodes exist because of bugs found while building it: muskets and thrown tridents were both
 * being credited to the wrong tree entirely (see {@link SkillCombatTracker#classifyAttack}), so this is
 * the first tree that actually pays out for either.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillMarksmanshipBonuses {

    /** Beyond this a shot is a long one, for spread and for crits. */
    private static final double LONG_RANGE_BLOCKS = 30.0;
    private static final double CRIT_RANGE_BLOCKS = 40.0;
    /** How often holstered weapons are looked at. */
    private static final int HOLSTER_INTERVAL_TICKS = 20;
    /** Multiple of a normal reload that loading a holstered weapon takes. */
    private static final int HOLSTER_RELOAD_MULTIPLIER = 3;
    /** Where the holstered progress is kept, on the weapon itself so it survives everything. */
    private static final String HOLSTER_TAG = "tommemod:HolsterTicks";

    // ---- The projectile ----

    /** Pierce and spread are properties of the bolt, settled the tick it exists. */
    @SubscribeEvent
    public static void onProjectileSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getOwner() instanceof Player player)) return;

        boolean crossbow = player.getMainHandItem().getItem() instanceof CrossbowItem;
        boolean trident = arrow instanceof ThrownTrident;
        if (!crossbow && !trident) return;
        if (arrow.getPersistentData().getBoolean("tommemod:skill_extra")) return;

        if (crossbow) {
            int pierce = (int) Math.round(SkillBonuses.get(player, ModSkillBonuses.BOLT_PIERCE));
            if (pierce > 0) {
                arrow.setPierceLevel((byte) Math.min(127, arrow.getPierceLevel() + pierce));
            }
            fireExtraBolt(player, arrow);
        }

        // Harpooner: a trident thrown by a trained hand hits harder and comes home sooner. The return
        // itself is Loyalty's business; what this changes is the weight behind the throw.
        if (trident) {
            double mastery = SkillBonuses.get(player, ModSkillBonuses.TRIDENT_MASTERY);
            if (mastery > 0.0) arrow.setBaseDamage(arrow.getBaseDamage() * (1.0 + mastery));
        }
    }

    // ---- Impact ----

    /**
     * What a bolt, ball or trident does where it lands.
     *
     * LOW, so the figure being multiplied is the one the shared handler finished with. Distance is
     * measured from the shooter rather than from the projectile's flight, so a hitscan musket shot is
     * asked the same question as a bolt and gets the same answer.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHit(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.equals(event.getEntity())) return;

        ItemStack weapon = player.getMainHandItem();
        boolean musket = weapon.getItem() instanceof MusketItem
                && event.getSource().getDirectEntity() == player;
        boolean projectile = event.getSource().getDirectEntity() instanceof AbstractArrow;

        LivingEntity target = event.getEntity();

        // Bayonet: the answer to something that closed the distance while you were reloading.
        if (!musket && !projectile
                && event.getSource().getDirectEntity() == player
                && (weapon.getItem() instanceof CrossbowItem || weapon.getItem() instanceof MusketItem)) {
            double bayonet = SkillBonuses.get(player, ModSkillBonuses.BAYONET_DAMAGE);
            if (bayonet > 0.0) event.setAmount(event.getAmount() * (float) (1.0 + bayonet));
            return;
        }

        if (!musket && !projectile) return;

        double distance = player.distanceTo(target);

        if (distance >= CRIT_RANGE_BLOCKS && SkillBonuses.has(player, ModSkillBonuses.DISTANCE_CRIT)) {
            event.setAmount(event.getAmount() * 1.5F);
        }

        // Overwatch: a target already labouring under your suppression is an easier shot.
        double suppressed = SkillBonuses.get(player, ModSkillBonuses.SUPPRESSED_DAMAGE);
        if (suppressed > 0.0 && target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
            event.setAmount(event.getAmount() * (float) (1.0 + suppressed));
        }

        if (SkillBonuses.roll(player, ModSkillBonuses.SUPPRESS_SLOW)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, true, true));
        }

        applyAssassinate(player, target);
        applyImpactArea(player, target, event.getAmount());
    }

    /**
     * Assassin: a shot into something that never saw it.
     *
     * Bosses exempt through {@code tommemod:bosses}, and so is anything already fighting - a mob with a
     * target has been alerted, and this is meant to reward the shot taken before the fight starts rather
     * than to end one already in progress.
     */
    private static void applyAssassinate(Player player, LivingEntity target) {
        if (!SkillBonuses.has(player, ModSkillBonuses.ASSASSINATE)) return;
        if (target.getType().is(ModTags.EntityTypes.BOSSES)) return;
        if (target instanceof Player) return; // never an instant kill on another player
        if (target instanceof Mob mob && mob.getTarget() != null) return;

        target.hurt(player.damageSources().playerAttack(player), target.getMaxHealth() * 2.0F);
    }

    /** Siege Shot: the impact carries past what it hit. */
    private static void applyImpactArea(Player player, LivingEntity struck, float damage) {
        double radius = SkillBonuses.get(player, ModSkillBonuses.IMPACT_AREA);
        if (radius <= 0.0 || damage <= 0.0F) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        float splash = damage * 0.5F;
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
                struck.getBoundingBox().inflate(radius))) {
            if (nearby == struck || nearby == player) continue;
            if (nearby.isAlliedTo(player)) continue;
            nearby.hurt(player.damageSources().explosion(null, player), splash);
        }
    }

    // ---- Holstered weapons ----

    /**
     * Opening Shot: a weapon you are not holding loads itself.
     *
     * The progress lives in the stack's own NBT rather than in a map, because the thing being reloaded
     * moves - between hotbar slots, into a chest, onto the ground - and a map keyed by player would
     * either lose it or credit it to the wrong weapon. Held weapons are skipped and their counter reset,
     * so this only ever loads what is genuinely put away.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) return;

        Player player = event.player;
        if (player.tickCount % HOLSTER_INTERVAL_TICKS != 0) return;
        if (!SkillBonuses.has(player, ModSkillBonuses.HOLSTER_RELOAD)) return;

        int required = MusketItem.RELOAD_TICKS * HOLSTER_RELOAD_MULTIPLIER;
        ItemStack held = player.getMainHandItem();

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof MusketItem)) continue;

            if (stack == held) {
                if (stack.hasTag()) stack.getOrCreateTag().remove(HOLSTER_TAG);
                continue;
            }
            if (MusketItem.isLoaded(stack)) {
                stack.getOrCreateTag().remove(HOLSTER_TAG);
                continue;
            }

            int progress = stack.getOrCreateTag().getInt(HOLSTER_TAG) + HOLSTER_INTERVAL_TICKS;
            if (progress < required) {
                stack.getOrCreateTag().putInt(HOLSTER_TAG, progress);
                continue;
            }

            stack.getOrCreateTag().remove(HOLSTER_TAG);
            MusketItem.setLoaded(stack, true);
        }
    }

    /** Read by ItemCooldownsMixin: how much of a musket's reload a marksman skips. */
    public static int shortenReload(Player player, ItemStack weapon, int ticks) {
        if (!(weapon.getItem() instanceof MusketItem)) return ticks;

        double cut = SkillBonuses.reduction(player, ModSkillBonuses.MUSKET_RELOAD);
        return cut <= 0.0 ? ticks : Math.max(1, (int) Math.round(ticks * (1.0 - cut)));
    }

    /**
     * Volley Fire: a second bolt on the same line.
     *
     * Marked so the spawn handler lets it past, or each extra bolt would roll for an extra bolt of its
     * own and one pull of the trigger could empty the sky.
     */
    private static void fireExtraBolt(Player player, AbstractArrow bolt) {
        if (!SkillBonuses.roll(player, ModSkillBonuses.CROSSBOW_MULTISHOT)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        net.minecraft.world.entity.projectile.Arrow copy =
                new net.minecraft.world.entity.projectile.Arrow(level, player);
        copy.getPersistentData().putBoolean("tommemod:skill_extra", true);
        copy.setBaseDamage(bolt.getBaseDamage());
        copy.setPierceLevel(bolt.getPierceLevel());
        copy.pickup = AbstractArrow.Pickup.DISALLOWED;
        copy.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());

        var velocity = bolt.getDeltaMovement();
        copy.shoot(velocity.x, velocity.y, velocity.z, (float) velocity.length(), 2.0F);
        level.addFreshEntity(copy);
    }
}
