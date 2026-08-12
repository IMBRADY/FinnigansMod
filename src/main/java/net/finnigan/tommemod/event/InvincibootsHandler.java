package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.caelus.api.CaelusApi;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything the Invinciboots do once they're on your feet.
 *
 * <p>Flight is granted through Caelus' fall-flying attribute (the same route the mod's accessory
 * elytra slot uses), so the boots glide on vanilla's own elytra code rather than an imitation of it
 * - identical physics, identical firework boosting, identical landing rules.
 *
 * <p>Sneaking in mid-air starts the glide and goes straight to full speed - twice a firework
 * rocket's - with no ordinary-glide lead-in, carving a tunnel through whatever is in the way. The
 * boost ends either by releasing sneak or by hitting something the carve couldn't clear, and either
 * way it ends the same: a TNT blast, a dead stop, and hovering in creative-style flight.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class InvincibootsHandler {

    private static final UUID FLIGHT_MODIFIER_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000002");

    /**
     * Twice a firework rocket's 1.5. Set outright rather than eased into the way a rocket does it -
     * a rocket spends several ticks winding up, and sneak here is meant to be full speed on the
     * first tick with no ordinary-glide lead-in.
     */
    private static final double BOOST_SPEED = 3.0D;

    private static final float BLAST_POWER = 6.0F; // TNT is 4.0F
    private static final double CARVE_RADIUS = 2.0D;
    /** Begin the tunnel just behind the player, so the space they currently occupy is cleared too. */
    private static final double CARVE_BACKSTEP = 0.5D;
    /** Clear a little past this tick's travel, so the next one doesn't begin against a wall. */
    private static final double CARVE_LOOKAHEAD = 1.5D;
    /** A boost runs at 3 blocks/tick; enough sweep to cover it without unbounded scans. */
    private static final double CARVE_MAX_SWEEP = 6.0D;

    /** Players mid-boost last tick, server-side only - releasing sneak is what triggers the blast. */
    private static final Set<UUID> BOOSTING = ConcurrentHashMap.newKeySet();

    /**
     * Players whose current sneak press has already been used on a boost. One press buys one boost:
     * without that, crashing into something unbreakable would detonate, re-launch off the still-held
     * key two ticks later, and detonate again - a TNT machine-gun against any bedrock wall.
     *
     * <p>Spent when a boost is actually running rather than on the tick the key goes down, so that
     * holding sneak on the way up to a jump still launches you the moment you leave the ground.
     *
     * <p>Two sets, one per logical side: in single-player the client and the integrated server run
     * in one JVM against the same player UUID, so a single shared set would have each side
     * overwriting the other's state.
     */
    private static final Set<UUID> SNEAK_SPENT_CLIENT = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SNEAK_SPENT_SERVER = ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        boolean wearing = player.getItemBySlot(EquipmentSlot.FEET).is(ModItems.INVINCIBOOTS.get());
        updateFlightAttribute(player, wearing);

        // Elytra movement is client-driven, so the boost runs on the owning client for smooth
        // prediction and on the server for authority - exactly as SkyboundHandler does it. Remote
        // players on a client are skipped; their motion arrives interpolated from the server.
        if (player.level().isClientSide && !player.isLocalPlayer()) return;

        Set<UUID> spent = player.level().isClientSide ? SNEAK_SPENT_CLIENT : SNEAK_SPENT_SERVER;
        boolean sneaking = wearing && player.isShiftKeyDown();
        if (!player.isShiftKeyDown()) spent.remove(player.getUUID());

        // No slow-glide lead-in. Sneaking in mid-air starts the glide on the spot and boost() puts
        // the player at full speed the same tick, so there is never a stretch of ordinary elytra
        // flight first. A hover left over from the last detonation counts as mid-air, which is what
        // lets one press lead straight into the next boost.
        if (sneaking && !spent.contains(player.getUUID())
                && !player.isFallFlying() && !player.onGround() && !player.isInWater()) {
            if (player.getAbilities().flying) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
            player.startFallFlying();
        }

        boolean boosting = sneaking && player.isFallFlying();
        if (boosting) {
            spent.add(player.getUUID());
            boost(player);
        }

        if (player.level().isClientSide) return;

        updateFlightAbility(player, wearing);

        if (boosting) {
            boolean sustained = !BOOSTING.add(player.getUUID());
            carveThrough(player);

            // Hit something the carve couldn't clear - bedrock, a chest, the ground. Ends the boost
            // the same way releasing sneak does. Gated on the boost having already run for a tick,
            // so the ground contact the player launched from doesn't register as a crash.
            if (sustained && (player.horizontalCollision || player.verticalCollision)) {
                BOOSTING.remove(player.getUUID());
                detonate(player);
            }
        } else if (BOOSTING.remove(player.getUUID()) && wearing && !player.isShiftKeyDown()) {
            detonate(player);
        }
    }

    /** Clear stale entries rather than leak a UUID per player who logs out mid-boost. */
    @SubscribeEvent
    public static void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        BOOSTING.remove(id);
        SNEAK_SPENT_CLIENT.remove(id);
        SNEAK_SPENT_SERVER.remove(id);
    }

    private static void updateFlightAttribute(Player player, boolean wearing) {
        AttributeInstance flight = player.getAttribute(CaelusApi.getInstance().getFlightAttribute());
        if (flight == null) return;

        boolean granted = flight.getModifier(FLIGHT_MODIFIER_ID) != null;
        if (wearing == granted) return;

        if (wearing) {
            flight.addTransientModifier(new AttributeModifier(
                    FLIGHT_MODIFIER_ID, "tommemod invinciboots flight", 1.0D, AttributeModifier.Operation.ADDITION));
        } else {
            flight.removeModifier(FLIGHT_MODIFIER_ID);
        }
    }

    /**
     * Keeps permission-to-fly in step with wearing the boots, so that {@link #detonate} can switch
     * flight straight on at the end of a boost. It has to be permission and not just the flying flag:
     * the server clamps a client's reported {@code flying} to {@code mayfly} on every abilities
     * packet, so flight granted without it is revoked within the tick.
     *
     * <p>Derived from scratch each tick rather than remembered, because abilities are saved in the
     * player's data - a player who logs out wearing these and loses them offline would otherwise
     * keep survival flight forever. Creative and spectator are left alone; the game owns those.
     */
    private static void updateFlightAbility(Player player, boolean wearing) {
        if (player.isCreative() || player.isSpectator()) return;
        if (player.getAbilities().mayfly == wearing) return;

        player.getAbilities().mayfly = wearing;
        if (!wearing) player.getAbilities().flying = false;
        player.onUpdateAbilities();
    }

    private static void boost(Player player) {
        player.setDeltaMovement(player.getLookAngle().scale(BOOST_SPEED));
    }

    /**
     * Clears the stretch the player is about to cover. Swept rather than a single sphere at their
     * feet, because a boost crosses several blocks a tick and a point check would punch through thin
     * walls without touching them.
     *
     * <p>Two overlapping volumes, because either alone leaves a way to clip a wall. The tube is
     * anchored on the player's own body and starts slightly <em>behind</em> them: an earlier version
     * started it a couple of blocks out in front, and the pocket of untouched blocks that left
     * beside the player is exactly what a glancing approach caught on - the wall you scrape is the
     * one at your shoulder, not the one ahead of you. On top of that, everything intersecting the
     * box the player physically sweeps through is cleared outright, since that box is by definition
     * everything they can collide with this tick.
     */
    private static void carveThrough(Player player) {
        if (!player.mayBuild()) return; // adventure mode / spawn protection

        Level level = player.level();
        Vec3 motion = player.getDeltaMovement();
        double speed = motion.length();
        Vec3 heading = speed > 1.0E-4D ? motion.scale(1.0D / speed) : player.getLookAngle();
        Vec3 reach = heading.scale(Math.min(speed, CARVE_MAX_SWEEP));

        Vec3 start = player.position().add(0.0D, player.getBbHeight() * 0.5D, 0.0D)
                .subtract(heading.scale(CARVE_BACKSTEP));
        Vec3 end = start.add(reach).add(heading.scale(CARVE_LOOKAHEAD));

        AABB collides = player.getBoundingBox().expandTowards(reach).inflate(0.35D);
        AABB scan = new AABB(start, end).inflate(CARVE_RADIUS).minmax(collides);

        // Deliberately removeBlock() rather than destroyBlock(): the latter fires a break-particle
        // and sound event per block, and a boost clears dozens of blocks a tick. One break effect is
        // played below for the whole sweep instead. Drops are skipped for the same reason - at this
        // speed they would be hundreds of item entities a second.
        BlockState broken = null;
        BlockPos brokenAt = null;

        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(scan.minX), Mth.floor(scan.minY), Mth.floor(scan.minZ),
                Mth.floor(scan.maxX), Mth.floor(scan.maxY), Mth.floor(scan.maxZ))) {
            if (distanceToSegmentSqr(Vec3.atCenterOf(pos), start, end) > CARVE_RADIUS * CARVE_RADIUS
                    && !collides.intersects(new AABB(pos))) continue;

            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (state.getDestroySpeed(level, pos) < 0.0F) continue;         // bedrock, barriers, portals
            if (state.getBlock() instanceof LiquidBlock) continue;
            if (state.hasBlockEntity()) continue;                           // don't void chest contents

            if (broken == null) {
                broken = state;
                brokenAt = pos.immutable();
            }
            level.removeBlock(pos, false);
        }

        if (broken != null) {
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, brokenAt, Block.getId(broken));
        }
    }

    /**
     * The end of a boost, whether from releasing sneak or from hitting something: drop out of the
     * glide, blow up, and come to a dead stop in mid-air under creative-style flight.
     *
     * <p>Order matters. The explosion applies its own knockback, so momentum is zeroed <em>after</em>
     * it rather than before, otherwise the blast would fling the player off at the exact moment they
     * are supposed to stop dead. {@code hurtMarked} is what makes that stick: player movement is
     * client-authoritative, and this is the same flag vanilla knockback uses to push a corrected
     * velocity down to the client.
     *
     * <p>Flight is switched on in every game mode. Guarding this on survival was wrong: these are a
     * creative-only item, so the guard skipped the one case it was most often used in, and the
     * player just fell. Only the {@code mayfly} permission needs a game-mode guard, and that lives
     * in {@link #updateFlightAbility}; creative and spectator already carry it.
     */
    private static void detonate(Player player) {
        player.stopFallFlying();
        player.level().explode(player, player.getX(), player.getY(), player.getZ(),
                BLAST_POWER, Level.ExplosionInteraction.TNT);

        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;

        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }

    /** Squared distance from a point to the line segment start..end. */
    private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-8D) return point.distanceToSqr(start);

        double t = Mth.clamp(point.subtract(start).dot(segment) / lengthSqr, 0.0D, 1.0D);
        return point.distanceToSqr(start.add(segment.scale(t)));
    }
}
