package net.finnigan.tommemod.client.blueprint;

import net.finnigan.tommemod.network.ModNetwork;
import net.finnigan.tommemod.network.packet.BlueprintModeStatePacket;
import net.finnigan.tommemod.network.packet.CancelConstructionPacket;
import net.finnigan.tommemod.network.packet.ExitBlueprintModePacket;
import net.finnigan.tommemod.network.packet.PlaceBlueprintPacket;
import net.finnigan.tommemod.network.packet.SyncConstructionSitesPacket;
import net.finnigan.tommemod.network.packet.SyncConstructionSitesPacket.SiteInfo;
import net.finnigan.tommemod.village.blueprint.Blueprint;
import net.finnigan.tommemod.village.blueprint.BlueprintPlanner;
import net.finnigan.tommemod.village.blueprint.Blueprints;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Blueprint mode, client side: the camera glide in and out, what the cursor is pointing at, the
 * ghost building that follows it, and every planning action the input handler can trigger.
 *
 * <p>The server owns whether the mode is on (it flips the game mode); this class owns how it looks
 * and feels. Entering is a {@value #GLIDE_TICKS}-tick glide from the player's eyes up to a
 * bird's-eye view; leaving glides back down to exactly where they stood and only then asks the server
 * to put them back on their feet, so the hand-over is invisible.
 */
public final class BlueprintClient {

    public enum Phase { INACTIVE, ENTERING, ACTIVE, EXITING, AWAITING_EXIT }

    static final int GLIDE_TICKS = 22;
    private static final double CURSOR_RANGE = 192.0;
    private static final float FLY_SPEED = 0.1F;
    private static final int MIN_Y_OFFSET = -8;
    private static final int MAX_Y_OFFSET = 16;

    private static Phase phase = Phase.INACTIVE;
    private static int phaseTicks;

    // From the server
    private static BlockPos regionCentre = BlockPos.ZERO;
    private static double placeRadius;
    private static double flightRadius;
    private static Vec3 returnPos = Vec3.ZERO;
    private static float returnYaw;
    private static float returnPitch;
    private static int maxGroundGap;
    private static boolean free;
    private static int maxSites;
    private static int builderCount;
    private static List<SiteInfo> sites = List.of();

    // Glide
    private static Vec3 glideFrom = Vec3.ZERO;
    private static Vec3 glideTo = Vec3.ZERO;
    private static float yawFrom;
    private static float yawTo;
    private static float pitchFrom;
    private static float pitchTo;

    // Planning
    private static int selected;
    private static Rotation rotation = Rotation.NONE;
    private static int yOffset;
    @Nullable
    private static BlockPos lockedGround;
    @Nullable
    private static BlueprintPlanner.Plan plan;
    @Nullable
    private static Component extraProblem;
    private static String planKey = "";
    private static int planAge;
    private static int confirmCooldown;
    @Nullable
    private static SiteInfo hoveredSite;
    @Nullable
    private static UUID cancelArmed;
    private static int cancelArmedTicks;
    @Nullable
    private static Component flash;
    private static int flashTicks;
    private static boolean flashBad;
    private static final Map<UUID, List<Blueprint.Cell>> siteRemaining = new HashMap<>();

    private BlueprintClient() {
    }

    // ---- State queries ----

    public static Phase phase() {
        return phase;
    }

    public static boolean isActive() {
        return phase != Phase.INACTIVE;
    }

    public static boolean isPlanning() {
        return phase == Phase.ACTIVE;
    }

    /** While gliding, the glide owns the camera; mouse look and movement keys are held off. */
    public static boolean isCameraLocked() {
        return phase == Phase.ENTERING || phase == Phase.EXITING || phase == Phase.AWAITING_EXIT;
    }

    /** 0 at the start of a glide, 1 at its end, eased. */
    public static float glideProgress(float partialTick) {
        return ease(Mth.clamp((phaseTicks + partialTick) / GLIDE_TICKS, 0F, 1F));
    }

    public static int selectedIndex() {
        return selected;
    }

    @Nullable
    public static Blueprint selectedBlueprint() {
        List<Blueprint> all = Blueprints.all();
        return all.isEmpty() ? null : all.get(Mth.clamp(selected, 0, all.size() - 1));
    }

    @Nullable
    public static BlueprintPlanner.Plan plan() {
        return plan;
    }

    /** Why the current placement cannot be confirmed, or null if it can. */
    @Nullable
    public static Component problem() {
        if (plan == null) return Component.literal("Point at the ground to place");
        if (plan.problem() != null) return plan.problem();
        return extraProblem;
    }

    public static boolean isLocked() {
        return lockedGround != null;
    }

    public static Rotation rotation() {
        return rotation;
    }

    public static int yOffset() {
        return yOffset;
    }

    public static boolean isFree() {
        return free;
    }

    public static int builderCount() {
        return builderCount;
    }

    public static int maxSites() {
        return maxSites;
    }

    public static List<SiteInfo> sites() {
        return sites;
    }

    @Nullable
    public static SiteInfo hoveredSite() {
        return hoveredSite;
    }

    public static boolean isCancelArmed(UUID site) {
        return site.equals(cancelArmed) && cancelArmedTicks > 0;
    }

    public static List<Blueprint.Cell> remainingFor(SiteInfo site) {
        return siteRemaining.getOrDefault(site.id(), List.of());
    }

    @Nullable
    public static Component flash() {
        return flashTicks > 0 ? flash : null;
    }

    public static boolean flashBad() {
        return flashBad;
    }

    public static int flashTicks() {
        return flashTicks;
    }

    public static boolean canAfford(Blueprint bp) {
        if (free) return true;
        for (Blueprint.Cost c : bp.cost()) {
            if (countInInventory(c) < c.count()) return false;
        }
        return true;
    }

    public static int countInInventory(Blueprint.Cost cost) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0;
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(cost.item())) n += stack.getCount();
        }
        return n;
    }

    public static BoundingBox boundsOf(SiteInfo site) {
        Blueprint bp = Blueprints.get(site.blueprintId());
        if (bp == null) return new BoundingBox(site.origin());
        return BlueprintPlanner.boundsFor(bp, site.rotation(), site.origin());
    }

    // ---- Server messages ----

    public static void onState(BlueprintModeStatePacket msg) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!msg.active) {
            reset();
            return;
        }
        if (player == null) return;

        regionCentre = msg.regionCentre;
        placeRadius = msg.placeRadius;
        flightRadius = msg.flightRadius;
        returnPos = new Vec3(msg.returnX, msg.returnY, msg.returnZ);
        returnYaw = msg.returnYaw;
        returnPitch = msg.returnPitch;
        maxGroundGap = msg.maxGroundGap;
        free = msg.free;
        maxSites = msg.maxSites;
        int initial = Blueprints.indexOf(msg.initialBlueprint);
        if (initial >= 0) selected = initial;
        lockedGround = null;
        plan = null;
        planKey = "";

        // Up and back from where they stand, looking down at the spot they were facing.
        Vec3 facing = Vec3.directionFromRotation(0, player.getYRot());
        glideFrom = player.position();
        glideTo = glideFrom.add(0, 14, 0).subtract(facing.scale(9));
        yawFrom = player.getYRot();
        yawTo = yawFrom;
        pitchFrom = player.getXRot();
        pitchTo = 48F;
        phase = Phase.ENTERING;
        phaseTicks = 0;
        // Nothing held down on the way in (the right click on the stand) may stay held afterwards.
        KeyMapping.releaseAll();
        play(SoundEvents.BOOK_PAGE_TURN, 1.0F);
    }

    public static void onSites(SyncConstructionSitesPacket msg) {
        builderCount = msg.builderCount;
        sites = List.copyOf(msg.sites);
        refreshSiteGhosts();
    }

    private static void reset() {
        if (phase != Phase.INACTIVE) KeyMapping.releaseAll();
        phase = Phase.INACTIVE;
        phaseTicks = 0;
        plan = null;
        lockedGround = null;
        hoveredSite = null;
        sites = List.of();
        siteRemaining.clear();
        flashTicks = 0;
        cancelArmed = null;
    }

    // ---- Ticking ----

    /** Client tick, END phase. */
    public static void tick() {
        if (phase == Phase.INACTIVE) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            reset();
            return;
        }
        phaseTicks++;
        if (confirmCooldown > 0) confirmCooldown--;
        if (flashTicks > 0) flashTicks--;
        if (cancelArmedTicks > 0 && --cancelArmedTicks == 0) cancelArmed = null;

        switch (phase) {
            case ENTERING -> {
                applyGlide(player);
                if (phaseTicks >= GLIDE_TICKS) {
                    phase = Phase.ACTIVE;
                    phaseTicks = 0;
                    player.getAbilities().setFlyingSpeed(FLY_SPEED);
                    player.getAbilities().flying = true;
                }
            }
            case ACTIVE -> {
                keepInsideFlightArea(player);
                updatePlan(mc, player);
                hoveredSite = siteUnderCursor(player);
                if (mc.level.getGameTime() % 20 == 0) refreshSiteGhosts();
            }
            case EXITING -> {
                applyGlide(player);
                if (phaseTicks >= GLIDE_TICKS) {
                    phase = Phase.AWAITING_EXIT;
                    phaseTicks = 0;
                    ModNetwork.CHANNEL.sendToServer(new ExitBlueprintModePacket());
                }
            }
            case AWAITING_EXIT -> {
                player.setPos(returnPos);
                player.setDeltaMovement(Vec3.ZERO);
                // The server answers within a tick or two; never leave the camera frozen if it does not.
                if (phaseTicks > 100) reset();
            }
            default -> {
            }
        }
    }

    private static void applyGlide(LocalPlayer player) {
        float t = glideProgress(0F);
        player.setPos(glideFrom.lerp(glideTo, t));
        player.setDeltaMovement(Vec3.ZERO);
        float yaw = yawFrom + Mth.wrapDegrees(yawTo - yawFrom) * t;
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(Mth.lerp(t, pitchFrom, pitchTo));
    }

    private static float ease(float t) {
        return t * t * (3F - 2F * t);
    }

    private static void keepInsideFlightArea(LocalPlayer player) {
        double cx = regionCentre.getX() + 0.5;
        double cz = regionCentre.getZ() + 0.5;
        double dx = player.getX() - cx;
        double dz = player.getZ() - cz;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double x = player.getX();
        double z = player.getZ();
        boolean moved = false;
        if (dist > flightRadius && dist > 0) {
            x = cx + dx / dist * flightRadius;
            z = cz + dz / dist * flightRadius;
            moved = true;
        }
        double y = Mth.clamp(player.getY(), regionCentre.getY() - 32, regionCentre.getY() + 120);
        if (y != player.getY()) moved = true;
        if (moved) {
            player.setPos(x, y, z);
            player.setDeltaMovement(Vec3.ZERO);
        }
    }

    private static void updatePlan(Minecraft mc, LocalPlayer player) {
        Blueprint bp = selectedBlueprint();
        BlockPos ground = lockedGround != null ? lockedGround : cursorGround(mc, player);
        if (bp == null || ground == null) {
            plan = null;
            planKey = "";
            return;
        }
        BlockPos origin = BlueprintPlanner.originFor(bp, rotation, ground, yOffset);
        String key = bp.id() + '|' + rotation + '|' + origin.asLong();
        // Re-plan when the ghost moves, and every half second regardless so it notices the world changing.
        if (!key.equals(planKey) || ++planAge > 10 || plan == null) {
            plan = BlueprintPlanner.plan(mc.level, bp, rotation, origin, maxGroundGap);
            planKey = key;
            planAge = 0;
        }
        extraProblem = villageProblem(bp, plan.bounds());
    }

    /** The checks the planner cannot make - they need village knowledge the server sent us. */
    @Nullable
    private static Component villageProblem(Blueprint bp, BoundingBox box) {
        double dx = box.getCenter().getX() - regionCentre.getX();
        double dz = box.getCenter().getZ() - regionCentre.getZ();
        if (dx * dx + dz * dz > placeRadius * placeRadius) return Component.literal("Too far from the village");
        for (SiteInfo s : sites) {
            if (boundsOf(s).intersects(box)) return Component.literal("Overlaps another construction");
        }
        if (sites.size() >= maxSites) return Component.literal(maxSites + " buildings already under way - wait for one to finish");
        if (builderCount < bp.requiredBuilders()) {
            return Component.literal("Needs " + bp.requiredBuilders() + " Builder" + (bp.requiredBuilders() == 1 ? "" : "s") + " (village has " + builderCount + ")");
        }
        if (!canAfford(bp)) {
            for (Blueprint.Cost c : bp.cost()) {
                if (countInInventory(c) < c.count()) {
                    return Component.literal("Not enough ").append(c.item().getDescription()).append(" (" + countInInventory(c) + "/" + c.count() + ")");
                }
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos cursorGround(Minecraft mc, LocalPlayer player) {
        HitResult hit = cursorHit(mc, player);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return null;
        return BlueprintPlanner.groundBelow(mc.level, blockHit.getBlockPos());
    }

    private static HitResult cursorHit(Minecraft mc, LocalPlayer player) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(CURSOR_RANGE));
        return mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
    }

    @Nullable
    private static SiteInfo siteUnderCursor(LocalPlayer player) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(CURSOR_RANGE));
        SiteInfo best = null;
        double bestDist = Double.MAX_VALUE;
        for (SiteInfo s : sites) {
            AABB box = AABB.of(boundsOf(s));
            Optional<Vec3> at = box.contains(eye) ? Optional.of(eye) : box.clip(eye, end);
            if (at.isPresent() && at.get().distanceToSqr(eye) < bestDist) {
                bestDist = at.get().distanceToSqr(eye);
                best = s;
            }
        }
        return best;
    }

    /** Recomputes which blocks of each site are still unbuilt, for the faint in-progress ghosts. */
    private static void refreshSiteGhosts() {
        ClientLevel level = Minecraft.getInstance().level;
        siteRemaining.clear();
        if (level == null) return;
        for (SiteInfo s : sites) {
            Blueprint bp = Blueprints.get(s.blueprintId());
            if (bp == null) continue;
            List<Blueprint.Cell> remaining = new ArrayList<>();
            for (Blueprint.Cell c : bp.cells(s.rotation())) {
                if (c.state().isAir()) continue;
                BlockPos pos = s.origin().offset(c.pos());
                BlockState current = level.getBlockState(pos);
                // Same block is good enough: fences and stairs reshape themselves to their neighbours.
                if (!current.is(c.state().getBlock())) remaining.add(new Blueprint.Cell(pos, c.state()));
            }
            siteRemaining.put(s.id(), remaining);
        }
    }

    // ---- Actions (called by BlueprintInput) ----

    public static void select(int index) {
        List<Blueprint> all = Blueprints.all();
        if (all.isEmpty()) return;
        int next = Math.floorMod(index, all.size());
        if (next != selected) {
            selected = next;
            yOffset = 0;
            play(SoundEvents.UI_BUTTON_CLICK.value(), 1.4F);
        }
    }

    public static void cycle(int delta) {
        select(selected + delta);
    }

    public static void rotate(int quarterTurns) {
        for (int i = 0; i < Math.floorMod(quarterTurns, 4); i++) rotation = rotation.getRotated(Rotation.CLOCKWISE_90);
        play(SoundEvents.ITEM_FRAME_ROTATE_ITEM, 1.0F);
    }

    public static void raise(int delta) {
        int next = Mth.clamp(yOffset + delta, MIN_Y_OFFSET, MAX_Y_OFFSET);
        if (next != yOffset) {
            yOffset = next;
            play(SoundEvents.UI_BUTTON_CLICK.value(), delta > 0 ? 1.6F : 1.2F);
        }
    }

    public static void toggleLock() {
        Minecraft mc = Minecraft.getInstance();
        if (lockedGround != null) {
            lockedGround = null;
            play(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F);
            return;
        }
        if (mc.player == null) return;
        BlockPos ground = cursorGround(mc, mc.player);
        if (ground == null) return;
        lockedGround = ground;
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F);
    }

    /** Moves the ghost a block relative to the camera: forward is wherever the player is looking. */
    public static void nudge(int forward, int right) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (lockedGround == null) {
            lockedGround = cursorGround(mc, player);
            if (lockedGround == null) return;
        }
        Direction ahead = player.getDirection();
        Direction side = ahead.getClockWise();
        lockedGround = lockedGround.relative(ahead, forward).relative(side, right);
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.8F);
    }

    public static void confirm() {
        if (confirmCooldown > 0 || !isPlanning()) return;
        Component problem = problem();
        if (problem != null || plan == null) {
            showFlash(problem != null ? problem : Component.literal("Nothing to place"), true);
            play(SoundEvents.VILLAGER_NO, 1.0F);
            return;
        }
        ModNetwork.CHANNEL.sendToServer(new PlaceBlueprintPacket(plan.blueprint().id(), plan.rotation(), plan.origin()));
        confirmCooldown = 10;
        lockedGround = null;
        play(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1.0F);
    }

    /** First press arms, second press (within three seconds, on the same site) cancels it. */
    public static void cancelHoveredSite() {
        SiteInfo site = hoveredSite;
        if (site == null) {
            showFlash(Component.literal("Look at a construction to cancel it"), true);
            return;
        }
        if (isCancelArmed(site.id())) {
            ModNetwork.CHANNEL.sendToServer(new CancelConstructionPacket(site.id()));
            cancelArmed = null;
            cancelArmedTicks = 0;
            play(SoundEvents.VILLAGER_NO, 0.9F);
            return;
        }
        Blueprint bp = Blueprints.get(site.blueprintId());
        cancelArmed = site.id();
        cancelArmedTicks = 60;
        showFlash(Component.literal("Press again to cancel the " + (bp != null ? bp.name() : "construction") + " (materials refunded)"), true);
        play(SoundEvents.NOTE_BLOCK_PLING.value(), 0.6F);
    }

    /** Escape, or the exit key: drop the lock first if there is one, otherwise glide home. */
    public static void back() {
        if (lockedGround != null) {
            toggleLock();
            return;
        }
        beginExit();
    }

    public static void beginExit() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || (phase != Phase.ACTIVE && phase != Phase.ENTERING)) return;
        glideFrom = player.position();
        glideTo = returnPos;
        yawFrom = player.getYRot();
        yawTo = returnYaw;
        pitchFrom = player.getXRot();
        pitchTo = returnPitch;
        phase = Phase.EXITING;
        phaseTicks = 0;
        plan = null;
        play(SoundEvents.BOOK_PAGE_TURN, 0.8F);
    }

    private static void showFlash(Component message, boolean bad) {
        flash = message;
        flashBad = bad;
        flashTicks = 50;
    }

    private static void play(SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
    }
}
