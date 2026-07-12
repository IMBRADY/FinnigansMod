package net.finnigan.tommemod.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

public class FireRingParticle extends TextureSheetParticle {

    protected FireRingParticle(ClientLevel level, double x, double y, double z,
                               double xSpeed, double ySpeed, double zSpeed,
                               SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.setSprite(sprites.get(this.random));
        this.gravity = 0.0F;
        this.quadSize *= 2.0F + this.random.nextFloat() * 0.4F;
        this.lifetime = 15 + this.random.nextInt(10);
        this.hasPhysics = false;
        this.xd = xSpeed;
        this.yd = ySpeed * 0.5;
        this.zd = zSpeed;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        this.alpha = 1.0F - ((float) this.age / this.lifetime);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new FireRingParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}