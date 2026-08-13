package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.SkillService;
import net.finnigan.tommemod.skill.SkillSync;
import net.finnigan.tommemod.skill.SkillTreeManager;
import net.finnigan.tommemod.skill.data.ModSkillCapabilities;
import net.finnigan.tommemod.skill.data.SkillsHandler;
import net.finnigan.tommemod.skill.data.SkillsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Everything that has to happen for the skill system to exist at all: attaching the capability,
 * loading the trees, and making sure a client is told about both before it can open the screen.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillLifecycleEvents {

    public static final ResourceLocation SKILLS_CAP_ID = new ResourceLocation(TommeMod.MOD_ID, "skills");

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof Player)) return;

        SkillsProvider provider = new SkillsProvider();
        // Queued rather than sent: this fires several times a second on a player who is simply
        // walking. Anything the player is actually waiting on sends immediately at its own call site.
        provider.getHandler().setChangeListener(() -> {
            if (event.getObject() instanceof ServerPlayer serverPlayer) {
                SkillSync.markDirty(serverPlayer);
            }
        });
        event.addCapability(SKILLS_CAP_ID, provider);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) SkillSync.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SkillSync.forget(player);
    }

    @SubscribeEvent
    public static void registerReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SkillTreeManager.ReloadListener(SkillTreeManager.CATEGORY_DIRECTORY));
        event.addListener(new SkillTreeManager.ReloadListener(SkillTreeManager.TREE_DIRECTORY));
    }

    /**
     * Fires on login and on every {@code /reload}, which is exactly when a client's copy of the trees
     * could be stale. Progress goes out alongside them because the screen needs both to draw anything,
     * and because a reload can change what a player's existing ranks are worth.
     */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            sendEverything(event.getPlayer());
            return;
        }
        event.getPlayerList().getPlayers().forEach(SkillLifecycleEvents::sendEverything);
    }

    private static void sendEverything(ServerPlayer player) {
        SkillSync.definitionsTo(player);
        // Ranks are read against the definitions that just landed, so the totals have to be rebuilt
        // after a reload even though the player's own numbers didn't change.
        SkillService.handler(player).ifPresent(SkillsHandler::recompute);
        SkillService.applyAttributes(player);
        SkillSync.toPlayer(player);
    }

    /**
     * Skills are a record of what the player has done, not something they were carrying, so death
     * never touches them - the same call the reputation capability already makes.
     */
    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(ModSkillCapabilities.SKILLS).ifPresent(oldHandler ->
                event.getEntity().getCapability(ModSkillCapabilities.SKILLS).ifPresent(newHandler ->
                        newHandler.deserializeNBT(oldHandler.serializeNBT())));
        event.getOriginal().invalidateCaps();
    }

    /** Attribute modifiers are transient, so a fresh body needs them put back on. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillService.applyAttributes(player);
            SkillSync.toPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillService.applyAttributes(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillService.applyAttributes(player);
        }
    }
}
