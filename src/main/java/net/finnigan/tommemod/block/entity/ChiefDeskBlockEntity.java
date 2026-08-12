package net.finnigan.tommemod.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Everything a {@link MonolithBlockEntity} is, minus the Elder job site, plus the GeckoLib hooks its
 * renderer needs. No animation controllers yet - the desk is static geometry for now, and the model
 * is loaded through GeckoLib purely because its shape can't be expressed as a vanilla block model.
 */
public class ChiefDeskBlockEntity extends MonolithBlockEntity implements GeoBlockEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ChiefDeskBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHIEF_DESK.get(), pos, state);
    }

    @Override
    protected boolean isElderJobSite() {
        return false;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.tommemod.chief_desk");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
