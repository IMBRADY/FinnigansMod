package net.finnigan.tommemod.client.sound;

import net.finnigan.tommemod.entity.custom.LumapierHelpers.LightBoltProjectileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One continuous Elytra rush that charges, flies, then fades smoothly with its Lumapier rod. */
public final class LumapierRodSoundInstance extends AbstractTickableSoundInstance {
    private static final int CHARGE_TICKS = 40;
    private static final int IMPACT_FADE_TICKS = 20;
    private static final Map<UUID, LumapierRodSoundInstance> ACTIVE = new HashMap<>();

    private final LightBoltProjectileEntity rod;
    private int impactFadeTicks;
    private int unseenFadeTicks;
    private long lastRenderedGameTime = Long.MIN_VALUE;

    private LumapierRodSoundInstance(LightBoltProjectileEntity rod) {
        super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, RandomSource.create());
        this.rod = rod;
        this.looping = true;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.volume = 0.02F;
        this.pitch = 1.15F;
        updatePosition();
    }

    public static void ensurePlaying(LightBoltProjectileEntity rod) {
        LumapierRodSoundInstance current = ACTIVE.get(rod.getUUID());
        if (current == null) {
            current = new LumapierRodSoundInstance(rod);
            ACTIVE.put(rod.getUUID(), current);
            Minecraft.getInstance().getSoundManager().play(current);
        }
        current.markRendered();
    }

    @Override
    public void tick() {
        updatePosition();
        if (rod.isStuck()) {
            impactFadeTicks++;
            this.volume = 0.8F * Math.max(0.0F, 1.0F - impactFadeTicks / (float) IMPACT_FADE_TICKS);
            if (impactFadeTicks >= IMPACT_FADE_TICKS) stop();
        } else if (rod.isRemoved() || !wasRenderedRecently()) {
            unseenFadeTicks++;
            this.volume = 0.8F * Math.max(0.0F, 1.0F - unseenFadeTicks / (float) IMPACT_FADE_TICKS);
            if (unseenFadeTicks >= IMPACT_FADE_TICKS) {
                ACTIVE.remove(rod.getUUID());
                stop();
            }
        } else if (rod.isLaunched()) {
            unseenFadeTicks = 0;
            this.volume = 0.8F;
        } else {
            unseenFadeTicks = 0;
            this.volume = 0.02F + Math.min(1.0F, rod.tickCount / (float) CHARGE_TICKS) * 0.78F;
        }
    }

    private void markRendered() {
        if (Minecraft.getInstance().level != null) {
            lastRenderedGameTime = Minecraft.getInstance().level.getGameTime();
        }
    }

    private boolean wasRenderedRecently() {
        return Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getGameTime() - lastRenderedGameTime <= 2L;
    }

    private void updatePosition() {
        this.x = rod.getX();
        this.y = rod.getY();
        this.z = rod.getZ();
    }
}
