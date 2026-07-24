package net.finnigan.tommemod.client;

import net.finnigan.tommemod.item.custom.AmethystCutlassItem;
import net.finnigan.tommemod.sound.ModSounds;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public class AmethystBeamSoundInstance extends AbstractTickableSoundInstance {
    private final LocalPlayer player;

    public AmethystBeamSoundInstance(LocalPlayer player) {
        super(ModSounds.AMETHYST_BEAM.get(), SoundSource.PLAYERS, player.getRandom());
        this.player = player;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0F;
        this.pitch = 1.0F;
        this.relative = false;
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }

    @Override
    public void tick() {
        if (isUsingBeam()) {
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
        }
    }

    @Override
    public boolean isStopped() {
        return !player.isAlive() || !isUsingBeam();
    }


    private boolean isUsingBeam() {
        ItemStack stack = player.getUseItem();
        return player.isUsingItem() && stack.getItem() instanceof AmethystCutlassItem;
    }
}