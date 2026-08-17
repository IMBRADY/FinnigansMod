package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Excavation tree: what a shovel does in trained hands.
 *
 * The tree's signature is the wide swing, and most of what is here is either that or a decision about
 * it - how far it reaches, what shape it takes, whether it costs the tool anything. The shape matters
 * more than the reach: a 7x7 that ignores where you aimed turns every dig into a crater, so Terracing
 * and Backfill exist to give the reach back its manners.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillExcavationBonuses {

    /** Most consecutive blocks that count toward momentum. */
    private static final int MOMENTUM_CAP = 10;
    /** A pause longer than this and the run of digging is over. */
    private static final int MOMENTUM_TIMEOUT_TICKS = 20;

    /** How often suspicious blocks are looked for. */
    private static final int SITE_SENSE_INTERVAL_TICKS = 40;

    /** Guards against a wide swing triggering itself through the break events it causes. */
    private static final java.util.Set<UUID> DIGGING = new java.util.HashSet<>();

    private record Momentum(int count, long lastBreakTick) {
    }

    private static final Map<UUID, Momentum> MOMENTUM = new HashMap<>();

    // ---- Digging speed ----

    /**
     * Dig Momentum, and Hardpan's answer to the blocks that are shovel-work in name only.
     *
     * Clay, mud, packed mud and rooted dirt are all dug with a shovel and all take several times as
     * long as the dirt around them, which is the one thing about excavating that no amount of plain
     * speed bonus makes pleasant. Hardpan is a flat multiplier on exactly those.
     */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        float multiplier = 1.0F;

        double momentum = SkillBonuses.get(player, ModSkillBonuses.DIG_MOMENTUM);
        if (momentum > 0.0) {
            Momentum current = MOMENTUM.get(player.getUUID());
            if (current != null && player.level().getGameTime() - current.lastBreakTick() <= MOMENTUM_TIMEOUT_TICKS) {
                multiplier *= (float) (1.0 + momentum * current.count());
            }
        }

        if (isHardpan(event.getState()) && SkillBonuses.has(player, ModSkillBonuses.HARDPAN)) {
            multiplier *= 3.0F;
        }

        if (multiplier != 1.0F) event.setNewSpeed(event.getNewSpeed() * multiplier);
    }

    private static boolean isHardpan(BlockState state) {
        return state.is(Blocks.CLAY) || state.is(Blocks.MUD) || state.is(Blocks.PACKED_MUD)
                || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.MUDDY_MANGROVE_ROOTS);
    }

    // ---- The swing ----

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (event.isCanceled() || player.isCreative()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!isShovelWork(event.getState())) return;

        noteMomentum(player);

        if (SkillBonuses.roll(player, ModSkillBonuses.BURIED_FIND)) {
            Block.popResource(level, event.getPos(), buriedFind(player));
        }

        // Re-entrancy guard: every block this clears fires its own BreakEvent, and without this the
        // first swing would recurse outward until it ran out of world.
        if (!DIGGING.add(player.getUUID())) return;
        try {
            widen(level, player, event.getPos(), event.getState());
        } finally {
            DIGGING.remove(player.getUUID());
        }
    }

    private static boolean isShovelWork(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_SHOVEL);
    }

    private static void noteMomentum(ServerPlayer player) {
        long now = player.level().getGameTime();
        Momentum current = MOMENTUM.get(player.getUUID());
        int count = current != null && now - current.lastBreakTick() <= MOMENTUM_TIMEOUT_TICKS
                ? Math.min(current.count() + 1, MOMENTUM_CAP)
                : 1;
        MOMENTUM.put(player.getUUID(), new Momentum(count, now));
    }

    /**
     * Clears the face around the struck block.
     *
     * The plane is chosen from the way the player is looking rather than from the hit face, which
     * BreakEvent does not carry - digging down clears a horizontal slab, digging into a wall clears a
     * vertical one. Only shovel-work blocks are taken, so a wide swing in a dirt bank stops dead at
     * the stone behind it rather than quietly mining it.
     */
    private static void widen(ServerLevel level, ServerPlayer player, BlockPos origin, BlockState struck) {
        int radius = (int) SkillBonuses.get(player, ModSkillBonuses.EXCAVATION_AREA);
        if (radius <= 0) return;

        ItemStack tool = player.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!(tool.getItem() instanceof ShovelItem)) return;

        // Backfill's other half: sneaking is how you ask for one block when the tree has given you
        // forty-nine. A wide swing with no way to turn it off is a liability in a build.
        if (player.isShiftKeyDown() && SkillBonuses.has(player, ModSkillBonuses.BACKFILL)) return;

        boolean terraced = SkillBonuses.has(player, ModSkillBonuses.TERRACING);
        boolean freeSwing = SkillBonuses.has(player, ModSkillBonuses.WIDE_SWING_FREE);
        boolean direct = SkillBonuses.has(player, ModSkillBonuses.EXCAVATION_DIRECT_HARVEST);

        for (BlockPos pos : face(origin, player.getDirection(), player.getXRot(), radius, terraced)) {
            if (pos.equals(origin)) continue;

            BlockState state = level.getBlockState(pos);
            if (!isShovelWork(state) || state.isAir()) continue;
            if (state.getDestroySpeed(level, pos) < 0) continue; // unbreakable

            for (ItemStack drop : Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool)) {
                if (direct && player.getInventory().add(drop)) continue;
                Block.popResource(level, pos, drop);
            }
            level.destroyBlock(pos, false, player);

            // Spade Care: the block you aimed at still costs the shovel; the other forty-eight do not.
            if (!freeSwing) tool.hurtAndBreak(1, player, broken -> {
            });
        }
    }

    /**
     * The square of positions a swing covers.
     *
     * Terracing is applied here rather than filtered afterwards: it means the square never extends
     * above the block that was aimed at, so a cut into a hillside leaves a step you can walk up
     * instead of an overhang you cannot see past.
     */
    private static List<BlockPos> face(BlockPos origin, Direction facing, float pitch, int radius,
                                       boolean terraced) {
        List<BlockPos> positions = new ArrayList<>();
        boolean vertical = Math.abs(pitch) > 60.0F; // looking mostly up or down

        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                BlockPos pos = vertical
                        ? origin.offset(a, 0, b)
                        : facing.getAxis() == Direction.Axis.X
                                ? origin.offset(0, b, a)
                                : origin.offset(a, b, 0);

                if (terraced && !vertical && pos.getY() > origin.getY()) continue;
                positions.add(pos);
            }
        }
        return positions;
    }

    /** What comes up out of the ground. The good table is Treasure Hunter's doing. */
    private static ItemStack buriedFind(ServerPlayer player) {
        boolean good = SkillBonuses.roll(player, ModSkillBonuses.TREASURE_QUALITY);
        var random = player.getRandom();

        if (good) {
            return new ItemStack(switch (random.nextInt(5)) {
                case 0 -> Items.EMERALD;
                case 1 -> Items.DIAMOND;
                case 2 -> Items.AMETHYST_SHARD;
                case 3 -> Items.GOLD_INGOT;
                default -> Items.LAPIS_LAZULI;
            });
        }
        return new ItemStack(switch (random.nextInt(5)) {
            case 0 -> Items.FLINT;
            case 1 -> Items.BONE;
            case 2 -> Items.CLAY_BALL;
            case 3 -> Items.IRON_NUGGET;
            default -> Items.STICK;
        });
    }

    // ---- Being in the hole ----

    /** Well Shored: the two ways an excavation kills the person doing it. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!event.getSource().is(DamageTypes.FALLING_BLOCK) && !event.getSource().is(DamageTypes.IN_WALL)) {
            return;
        }

        double reduction = SkillBonuses.reduction(player, ModSkillBonuses.CAVE_IN_IMMUNITY);
        if (reduction <= 0.0) return;

        event.setAmount(event.getAmount() * (float) (1.0 - reduction));
    }

    /**
     * Site Sense: suspicious sand and gravel worth walking over to.
     *
     * The particles are sent to one player rather than spawned in the world, which on a server is the
     * difference between a hint and a broadcast - a marker spawned normally would show every other
     * player where somebody else's Excavation had found something.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % SITE_SENSE_INTERVAL_TICKS != 0) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        int range = (int) SkillBonuses.get(player, ModSkillBonuses.DIG_SITE_SENSE);
        if (range <= 0) return;

        BlockPos centre = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-range, -range, -range),
                centre.offset(range, range, range))) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(Blocks.SUSPICIOUS_SAND) && !state.is(Blocks.SUSPICIOUS_GRAVEL)) continue;

            level.sendParticles(player, ParticleTypes.WAX_OFF,
                    true, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.0);
        }
    }
}
