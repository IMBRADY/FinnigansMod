package net.finnigan.tommemod.village.blueprint;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.network.ModNetwork;
import net.finnigan.tommemod.network.packet.BlueprintModeStatePacket;
import net.finnigan.tommemod.network.packet.BlueprintStandStatusPacket;
import net.finnigan.tommemod.network.packet.SyncConstructionSitesPacket;
import net.finnigan.tommemod.village.VillageManager;
import net.finnigan.tommemod.village.VillageRegion;
import net.finnigan.tommemod.village.construction.BuilderWorkHandler;
import net.finnigan.tommemod.village.construction.ConstructionManager;
import net.finnigan.tommemod.village.construction.ConstructionSite;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Blueprint mode, server side: the Chief opens a Blueprint Stand, becomes a free-flying
 * spectator camera over their village, plans buildings, and drops back where they were standing.
 *
 * <p>Everything needed to undo the mode - previous game mode, where the player stood, which way they
 * faced - is written into the player's own persistent data the moment they enter, so it is saved with
 * the player. Logging out, a crash, or a dimension change all restore from that record; nobody can be
 * left stranded as a spectator. If something else changes their game mode while they are planning (a
 * command, another mod), that wins and the mode simply ends.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public final class BlueprintModeManager {

    private static final String TAG = "TommeBlueprintMode";

    private BlueprintModeManager() {
    }

    public static boolean isActive(ServerPlayer player) {
        return player.getPersistentData().contains(TAG);
    }

    @Nullable
    public static UUID villageOf(ServerPlayer player) {
        CompoundTag t = player.getPersistentData().getCompound(TAG);
        return t.hasUUID("Village") ? t.getUUID("Village") : null;
    }

    /** Whether this player came in from Creative - their buildings cost nothing. */
    public static boolean isFree(ServerPlayer player) {
        return previousMode(player) == GameType.CREATIVE;
    }

    private static GameType previousMode(ServerPlayer player) {
        return GameType.byName(player.getPersistentData().getCompound(TAG).getString("PrevMode"), GameType.SURVIVAL);
    }

    /** The Chief plans their own village. An operator in Creative may plan any village, for testing. */
    public static boolean mayPlan(ServerPlayer player, VillageManager manager, UUID villageId, boolean creative) {
        if (manager.getChief(villageId).map(player.getUUID()::equals).orElse(false)) return true;
        return creative && player.hasPermissions(2);
    }

    public static void tryEnter(ServerPlayer player, BlockPos at, @Nullable String initialBlueprint) {
        if (isActive(player)) return;
        ServerLevel level = player.serverLevel();
        VillageManager manager = VillageManager.get(level);
        Optional<UUID> village = manager.resolveVillage(level, at);
        if (village.isEmpty() || !manager.isEstablished(village.get())) {
            fail(player, "This isn't part of an established village");
            return;
        }
        if (!mayPlan(player, manager, village.get(), player.isCreative())) {
            fail(player, "Only the Village Chief may plan buildings here");
            return;
        }

        if (player.isPassenger()) player.stopRiding();
        CompoundTag t = new CompoundTag();
        t.putString("PrevMode", player.gameMode.getGameModeForPlayer().getName());
        t.putDouble("X", player.getX());
        t.putDouble("Y", player.getY());
        t.putDouble("Z", player.getZ());
        t.putFloat("YRot", player.getYRot());
        t.putFloat("XRot", player.getXRot());
        t.putString("Dim", level.dimension().location().toString());
        t.putUUID("Village", village.get());
        // Planning at dusk is miserable without it; only take it away again if we were the ones to give it.
        boolean giveNightVision = !player.hasEffect(MobEffects.NIGHT_VISION);
        t.putBoolean("GaveNightVision", giveNightVision);
        player.getPersistentData().put(TAG, t);

        player.setGameMode(GameType.SPECTATOR);
        if (giveNightVision) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
        }

        VillageRegion region = manager.resolveVillageRegion(level, village.get());
        double placeRadius = region.radius() + ModConfig.BUILDER_HUB_REGION_PADDING_BLOCKS.get();
        double flightRadius = region.radius() + ModConfig.BLUEPRINT_FLIGHT_MARGIN_BLOCKS.get();
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BlueprintModeStatePacket(
                true, village.get(), region.anchor(), placeRadius, flightRadius,
                t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"), t.getFloat("YRot"), t.getFloat("XRot"),
                initialBlueprint != null ? initialBlueprint : "", ModConfig.BLUEPRINT_MAX_GROUND_GAP.get(),
                previousMode(player) == GameType.CREATIVE, ModConfig.BLUEPRINT_MAX_ACTIVE_SITES.get()));
        syncSites(player);
    }

    /** Leaves blueprint mode: back to the old game mode, back to where they stood. */
    public static void exit(ServerPlayer player) {
        if (!isActive(player)) return;
        restore(player, true);
        sendInactive(player);
    }

    private static void restore(ServerPlayer player, boolean teleport) {
        CompoundTag t = player.getPersistentData().getCompound(TAG);
        player.getPersistentData().remove(TAG);

        if (t.getBoolean("GaveNightVision")) player.removeEffect(MobEffects.NIGHT_VISION);
        if (player.isSpectator()) {
            player.setGameMode(GameType.byName(t.getString("PrevMode"), GameType.SURVIVAL));
        }
        if (!teleport) return;

        ServerLevel target = player.serverLevel();
        ResourceLocation dimId = ResourceLocation.tryParse(t.getString("Dim"));
        if (dimId != null) {
            ServerLevel saved = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
            if (saved != null) target = saved;
        }
        player.teleportTo(target, t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"), t.getFloat("YRot"), t.getFloat("XRot"));
        player.fallDistance = 0;
    }

    private static void sendInactive(ServerPlayer player) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), BlueprintModeStatePacket.inactive());
    }

    private static void fail(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.RED), true);
    }

    // ---- Blueprint Stand screen ----

    /**
     * Sends a player the progress screen for the village a Blueprint Stand belongs to - opening it,
     * or refreshing one already open. Anyone may look; only the Chief gets a working Enter button.
     */
    public static void sendStandStatus(ServerPlayer player, BlockPos standPos, boolean open) {
        ServerLevel level = player.serverLevel();
        VillageManager manager = VillageManager.get(level);
        Optional<UUID> village = manager.resolveVillage(level, standPos);
        List<BlueprintStandStatusPacket.Site> sites = new ArrayList<>();
        boolean hasVillage = village.isPresent() && manager.isEstablished(village.get());
        boolean canPlan = false;
        int builders = 0;
        if (hasVillage) {
            canPlan = mayPlan(player, manager, village.get(), player.isCreative());
            builders = BuilderWorkHandler.countBuilders(level, manager.resolveVillageRegion(level, village.get()));
            for (ConstructionSite s : ConstructionManager.get(level).inVillage(village.get())) {
                sites.add(new BlueprintStandStatusPacket.Site(s.displayName(), s.completedCount(), s.total(),
                        BuilderWorkHandler.workersOn(level, s.id())));
            }
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BlueprintStandStatusPacket(
                standPos, open, hasVillage, canPlan, builders, ModConfig.BLUEPRINT_MAX_ACTIVE_SITES.get(), sites));
    }

    // ---- Construction site sync ----

    /** Re-sends the site list to everyone planning in this level. Called whenever sites change. */
    public static void syncSites(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (isActive(p)) syncSites(p);
        }
    }

    public static void syncSites(ServerPlayer player) {
        UUID village = villageOf(player);
        if (village == null) return;
        ServerLevel level = player.serverLevel();
        List<SyncConstructionSitesPacket.SiteInfo> infos = new ArrayList<>();
        for (ConstructionSite s : ConstructionManager.get(level).inVillage(village)) {
            infos.add(new SyncConstructionSitesPacket.SiteInfo(s.id(), s.blueprintId(), s.rotation(), s.origin(),
                    s.completedCount(), s.total(), BuilderWorkHandler.workersOn(level, s.id())));
        }
        VillageRegion region = VillageManager.get(level).resolveVillageRegion(level, village);
        int builders = BuilderWorkHandler.countBuilders(level, region);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncConstructionSitesPacket(builders, infos));
    }

    // ---- Safety nets ----

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && isActive(p)) restore(p, true);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // Only reachable if the server went down without a logout - put them back as they were.
        if (event.getEntity() instanceof ServerPlayer p && isActive(p)) {
            restore(p, true);
            sendInactive(p);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && isActive(p)) {
            restore(p, true);
            sendInactive(p);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null || server.getTickCount() % 10 != 0) return;
        boolean syncNow = server.getTickCount() % 20 == 0;

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!isActive(p)) continue;
            if (!p.isSpectator()) {
                // Something else changed their game mode; respect it and just end the mode.
                restore(p, false);
                sendInactive(p);
                continue;
            }
            keepNearVillage(p);
            if (syncNow) syncSites(p);
        }
    }

    /** The client keeps the camera inside the flight radius itself; this only catches a client that
     * does not, well past the point where the client would have stopped. */
    private static void keepNearVillage(ServerPlayer p) {
        UUID village = villageOf(p);
        if (village == null) return;
        ServerLevel level = p.serverLevel();
        VillageRegion region = VillageManager.get(level).resolveVillageRegion(level, village);
        double limit = region.radius() + ModConfig.BLUEPRINT_FLIGHT_MARGIN_BLOCKS.get();
        double dx = p.getX() - (region.anchor().getX() + 0.5);
        double dz = p.getZ() - (region.anchor().getZ() + 0.5);
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist <= limit + 24) return;
        double scale = limit / dist;
        p.teleportTo(level, region.anchor().getX() + 0.5 + dx * scale, p.getY(), region.anchor().getZ() + 0.5 + dz * scale, p.getYRot(), p.getXRot());
    }
}
