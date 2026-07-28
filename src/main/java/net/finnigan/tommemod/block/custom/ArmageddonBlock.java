package net.finnigan.tommemod.block.custom;

import net.finnigan.tommemod.block.entity.ArmageddonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;

public class ArmageddonBlock extends TntBlock {

    private final float blastRadius;

    public ArmageddonBlock(Properties properties, float blastRadius) {
        super(properties);
        this.blastRadius = blastRadius;
    }


    @Override
    public void onCaughtFire(BlockState state, Level level, BlockPos pos,
                             net.minecraft.core.Direction direction,
                             LivingEntity igniter) {

        if (!level.isClientSide) {
            ArmageddonBlockEntity tnt =
                    new ArmageddonBlockEntity(
                            level,
                            pos.getX() + 0.5,
                            pos.getY(),
                            pos.getZ() + 0.5,
                            igniter,
                            blastRadius
                    );

            level.addFreshEntity(tnt);
        }
    }


    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        if (!level.isClientSide) {

            ArmageddonBlockEntity tnt =
                    new ArmageddonBlockEntity(
                            level,
                            pos.getX(),
                            pos.getY(),
                            pos.getZ(),
                            null,
                            blastRadius
                    );

            tnt.setFuse(0);
            level.addFreshEntity(tnt);
        }
    }
}