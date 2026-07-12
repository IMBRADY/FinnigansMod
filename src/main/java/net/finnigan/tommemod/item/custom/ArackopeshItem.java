package net.finnigan.tommemod.item.custom;

import net.finnigan.tommemod.entity.custom.ArackopeshHelpers.GrappleHookEntity;
import net.finnigan.tommemod.entity.custom.UndeadSwordHelpers.SoulSummoner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class ArackopeshItem extends SwordItem {

    private static final int SPIDER_LIFETIME_TICKS = 20*60;
    private static final Map<UUID, Long> SPIDER_EXPIRY = new HashMap<>();
    private static final Map<UUID, Integer> ACTIVE_HOOKS = new HashMap<>();

    private static final double GRAPPLE_RANGE = 20.0;
    private static final int GRAPPLE_COOLDOWN_TICKS = 20; // 1s

    private static final int WEB_RADIUS = 4;
    private static final int MAX_ALLY_SPIDERS = 2;
    private static final int WEB_SUMMON_COOLDOWN_TICKS = 20 * 30; // 30s

    private static final Random RANDOM = new Random();
    // Tracks which spiders belong to which player, so the 2-spider cap survives across casts
    private static final Map<UUID, List<UUID>> OWNED_SPIDERS = new HashMap<>();

    public ArackopeshItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    public static boolean isHeldBy(Player player) {
        return player.getMainHandItem().getItem() instanceof ArackopeshItem
                || player.getOffhandItem().getItem() instanceof ArackopeshItem;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 200; // 10s hard ceiling; real "release" is the player letting go, not reaching this
    }

    @Override
    public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) {
        return net.minecraft.world.item.UseAnim.SPEAR;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entityLiving, int timeLeft) {
        if (entityLiving instanceof Player player && !level.isClientSide) {
            detachHook(player);
        }
    }

    private InteractionResultHolder<ItemStack> performGrappleStart(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this) || ACTIVE_HOOKS.containsKey(player.getUUID())) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide) {
            GrappleHookEntity hook = new GrappleHookEntity(level, player);
            Vec3 look = player.getLookAngle();
            hook.setPos(player.getX() + look.x, player.getEyeY() - 0.2, player.getZ() + look.z);
            hook.shoot(look.x, look.y, look.z, 2.2F, 0.5F);
            level.addFreshEntity(hook);
            ACTIVE_HOOKS.put(player.getUUID(), hook.getId());

            player.startUsingItem(hand);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.0F, 0.8F);
        }

        player.swing(hand);
        return InteractionResultHolder.consume(stack);
    }

    private void detachHook(Player player) {
        Integer hookId = ACTIVE_HOOKS.get(player.getUUID());
        if (hookId != null && player.level() instanceof ServerLevel serverLevel) {
            var entity = serverLevel.getEntity(hookId);
            if (entity != null) entity.discard(); // triggers GrappleHookEntity.remove() -> cleans up the map
        }
        player.getCooldowns().addCooldown(this, GRAPPLE_COOLDOWN_TICKS);
    }

    public static void clearHookFor(UUID ownerUUID) {
        ACTIVE_HOOKS.remove(ownerUUID);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            return performWebNestAndSummon(context.getLevel(), player, context.getHand()).getResult();
        }
        return performGrappleStart(context.getLevel(), player, context.getHand()).getResult();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            return performWebNestAndSummon(level, player, hand);
        }
        return performGrappleStart(level, player, hand);
    }

    private InteractionResultHolder<ItemStack> performGrapple(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide) {
            GrappleHookEntity hook = new GrappleHookEntity(level, player);
            Vec3 look = player.getLookAngle();
            hook.setPos(player.getX() + look.x, player.getEyeY() - 0.2, player.getZ() + look.z);
            hook.shoot(look.x, look.y, look.z, 2.2F, 0.5F); // speed, inaccuracy
            level.addFreshEntity(hook);

            player.getCooldowns().addCooldown(this, GRAPPLE_COOLDOWN_TICKS);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.0F, 0.8F);
        }

        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private InteractionResultHolder<ItemStack> performWebNestAndSummon(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        ServerLevel serverLevel = (ServerLevel) level;
        BlockPos center = player.blockPosition();

        // Lay a single-layer web floor around the player's feet
        for (int dx = -WEB_RADIUS; dx <= WEB_RADIUS; dx++) {
            for (int dz = -WEB_RADIUS; dz <= WEB_RADIUS; dz++) {
                if (dx * dx + dz * dz > WEB_RADIUS * WEB_RADIUS) continue;
                BlockPos pos = center.offset(dx, 0, dz);
                if (level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir()) {
                    level.setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState());
                }
            }
        }

        // Summon ally spiders, capped at MAX_ALLY_SPIDERS currently alive per player
        List<UUID> owned = OWNED_SPIDERS.computeIfAbsent(player.getUUID(), id -> new ArrayList<>());
        owned.removeIf(uuid -> serverLevel.getEntity(uuid) == null);

        int toSummon = MAX_ALLY_SPIDERS - owned.size();
        for (int i = 0; i < toSummon; i++) {
            Spider spider = EntityType.SPIDER.create(serverLevel);
            if (spider == null) continue;

            BlockPos spawnPos = center.offset(RANDOM.nextInt(3) - 1, 0, RANDOM.nextInt(3) - 1);
            spider.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
            spider.addTag(SoulSummoner.SOUL_ALLY_TAG);

            serverLevel.addFreshEntity(spider);
            owned.add(spider.getUUID());
            SPIDER_EXPIRY.put(spider.getUUID(), serverLevel.getGameTime() + SPIDER_LIFETIME_TICKS);
        }

        player.getCooldowns().addCooldown(this, WEB_SUMMON_COOLDOWN_TICKS);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SPIDER_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.7F);

        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    public static Map<UUID, Long> getSpiderExpiryMap() {
        return SPIDER_EXPIRY;
    }

    public static Map<UUID, List<UUID>> getOwnedSpidersMap() {
        return OWNED_SPIDERS;
    }
}