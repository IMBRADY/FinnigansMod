package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Excavation tree: what a shovel does in trained hands.
 *
 * The tree used to be built around a wide swing that cleared a 3x3, 5x5 or 7x7 face at a time, with
 * Terracing and Backfill existing only to make that swing behave. All of it is gone: digging is one
 * block again, and the four nodes that served the swing now pay for the tool, the spoil and the
 * experience instead. What is left here is what the shovel is doing rather than how much of the world
 * it takes at once.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillExcavationBonuses {

    /** Most consecutive blocks that count toward momentum. */
    private static final int MOMENTUM_CAP = 10;
    /** A pause longer than this and the run of digging is over. */
    private static final int MOMENTUM_TIMEOUT_TICKS = 20;

    /** How often suspicious blocks are looked for. */
    private static final int SITE_SENSE_INTERVAL_TICKS = 40;

    /** How far from the broken block a drop may appear and still count as that block's. */
    private static final double HARVEST_CATCH_RADIUS_SQR = 4.0;

    private record Momentum(int count, long lastBreakTick) {
    }

    /** A block a direct harvester has just broken: where it was, and on which tick. */
    private record PendingHarvest(BlockPos pos, long tick) {
    }

    private static final Map<UUID, Momentum> MOMENTUM = new HashMap<>();
    private static final Map<UUID, PendingHarvest> PENDING_HARVEST = new HashMap<>();

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

    // ---- The dig ----

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

        noteDirectHarvest(player, event.getPos());
    }

    // ---- Spoil Heap ----

    /**
     * Marks a dig whose spoil is to be caught on the way to the floor.
     *
     * BreakEvent fires before the block is removed and therefore before anything has dropped, so the
     * position and tick are all that can be recorded here; the drops are intercepted as they appear.
     * Catching them that way rather than cancelling the break and re-implementing it keeps vanilla in
     * charge of the tool wear, the experience and the block state, and leaves the double-drop handler
     * next door working exactly as it did.
     */
    private static void noteDirectHarvest(ServerPlayer player, BlockPos pos) {
        if (!SkillBonuses.has(player, ModSkillBonuses.EXCAVATION_DIRECT_HARVEST)) return;
        PENDING_HARVEST.put(player.getUUID(), new PendingHarvest(pos.immutable(),
                player.level().getGameTime()));
    }

    /**
     * Spoil Heap: what a dig turns up goes into the pack instead of onto the floor.
     *
     * Matched on the same tick and within a couple of blocks of what was broken, so this only ever
     * takes the spoil of the dig that asked for it - a mob dying beside the hole keeps its drops.
     * Anything the pack has no room for is left to spawn as normal.
     */
    @SubscribeEvent
    public static void onDropSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) return;
        if (!(event.getEntity() instanceof ItemEntity drop)) return;
        if (PENDING_HARVEST.isEmpty()) return;

        long now = event.getLevel().getGameTime();
        for (Map.Entry<UUID, PendingHarvest> entry : PENDING_HARVEST.entrySet()) {
            PendingHarvest pending = entry.getValue();
            if (pending.tick() != now) continue;
            if (drop.position().distanceToSqr(Vec3.atCenterOf(pending.pos())) > HARVEST_CATCH_RADIUS_SQR) {
                continue;
            }

            Player player = event.getLevel().getPlayerByUUID(entry.getKey());
            if (player == null) continue;

            // add() mutates the stack, so a partial pickup leaves the remainder to spawn normally.
            if (player.getInventory().add(drop.getItem())) event.setCanceled(true);
            return;
        }
    }

    /** Yesterday's digs are not worth carrying; the map only ever needs the current tick. */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING_HARVEST.isEmpty()) return;

        long now = event.level.getGameTime();
        PENDING_HARVEST.values().removeIf(pending -> pending.tick() != now);
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
