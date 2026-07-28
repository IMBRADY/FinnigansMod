package net.finnigan.tommemod.block.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class ArmageddonBlockEntity extends PrimedTnt {

    private final float blastRadius;

    public ArmageddonBlockEntity(Level level, double x, double y, double z, @Nullable LivingEntity owner, float blastRadius) {
        super(level, x, y, z, owner);
        this.blastRadius = blastRadius;
    }

    @Override
    protected void explode() {
        this.level().explode(
                this,
                this.getX(),
                this.getY(),
                this.getZ(),
                blastRadius,
                Level.ExplosionInteraction.BLOCK
        );
    }
}
