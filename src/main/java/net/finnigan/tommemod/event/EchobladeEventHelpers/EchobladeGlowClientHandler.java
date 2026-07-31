package net.finnigan.tommemod.event.EchobladeEventHelpers;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.item.custom.EchobladeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Echoblade passive (client-only half): while holding Echoblade, nearby MOVING hostile mobs get a
 * subtle glow marker only the holder can see, so they can spot approaching threats.
 *
 * DEVIATION FROM SPEC (documented, per the plan's explicitly sanctioned fallback): a true per-entity
 * render outline was investigated first. Vanilla's glowing render pass (RenderType.outline() /
 * OutlineBufferSource) is only populated during the dedicated "entity outline" stage inside
 * LevelRenderer.renderLevel — reaching it per-entity from a Forge event (e.g. RenderLivingEvent.Pre)
 * would require either (a) directly poking the entity's synced DATA_SHARED_FLAGS_ID byte client-side
 * (a protected vanilla field, fragile against server resyncs, and not a clean Forge-exposed API), or
 * (b) manually redirecting that single entity's render call into the outline buffer source instead of
 * the normal buffer source, which isn't exposed as a per-entity toggle by any current Forge event —
 * genuine engine-internals territory, not a bounded one-file addition. Since the plan explicitly
 * sanctions "spawn a subtle client-side particle effect... hovering above tracked moving hostile mobs"
 * as an acceptable fallback when the real outline is clearly fragile, that's what ships here:
 * ParticleTypes.GLOW spawned above each tracked mob, throttled so it reads as a subtle marker rather
 * than a particle spam trail.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID, value = Dist.CLIENT)
public class EchobladeGlowClientHandler {

    private static final double SCAN_RADIUS = 24.0;
    private static final double MOVING_THRESHOLD_SQ = 0.0025; // ignore near-stationary jitter
    private static final int PARTICLE_INTERVAL_TICKS = 4;

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (!EchobladeItem.isHeldBy(player)) return;

        tickCounter++;
        if (tickCounter % PARTICLE_INTERVAL_TICKS != 0) return;

        List<Monster> nearby = mc.level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(SCAN_RADIUS));
        for (Monster monster : nearby) {
            if (monster.getDeltaMovement().horizontalDistanceSqr() <= MOVING_THRESHOLD_SQ) continue;

            Vec3 pos = monster.position();
            double jitter = monster.getBbWidth() * 0.5;
            mc.level.addParticle(ParticleTypes.GLOW,
                    pos.x + (mc.level.random.nextDouble() - 0.5) * jitter,
                    pos.y + monster.getBbHeight() + 0.1,
                    pos.z + (mc.level.random.nextDouble() - 0.5) * jitter,
                    0.0, 0.02, 0.0);
        }
    }
}
