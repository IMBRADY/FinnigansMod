package net.finnigan.tommemod.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.world.WorldProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Armor on newly spawned zombies and skeletons, with the table swapping once the ender dragon is down.
 *
 * <p>Hooked on {@link EntityJoinLevelEvent} rather than on Forge's FinalizeSpawn event because vanilla
 * populates equipment inside {@code Mob#finalizeSpawn}, which {@code NaturalSpawner} calls immediately
 * before {@code addFreshEntityWithPassengers} - so joining the level is the first moment at which a
 * write here is guaranteed to survive. That hook also fires on every chunk reload, hence the one-shot
 * marker in the mob's persistent data.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public final class MobGearEvents {

    /** Marks a mob as already rolled, so a chunk reload doesn't re-arm it every time. */
    private static final String TAG_GEAR_ROLLED = "TommeModGearRolled";

    /** Helmets are deliberately absent: vanilla's own helmet roll is what keeps zombies out of the sun. */
    private static final EquipmentSlot[] ROLLED_SLOTS = {
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static final float CHANCE_PRE_DRAGON = 0.60F;
    private static final float CHANCE_POST_DRAGON = 0.70F;

    /** Of the post-dragon rolls that produce armour, the share that comes out diamond instead of iron. */
    private static final float DIAMOND_SHARE = 0.10F;

    private MobGearEvents() {
    }

    private enum ArmorSet {
        LEATHER  (Items.LEATHER_CHESTPLATE,   Items.LEATHER_LEGGINGS,   Items.LEATHER_BOOTS),
        CHAINMAIL(Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS),
        IRON     (Items.IRON_CHESTPLATE,      Items.IRON_LEGGINGS,      Items.IRON_BOOTS),
        DIAMOND  (Items.DIAMOND_CHESTPLATE,   Items.DIAMOND_LEGGINGS,   Items.DIAMOND_BOOTS);

        private final Item chest;
        private final Item legs;
        private final Item feet;

        ArmorSet(Item chest, Item legs, Item feet) {
            this.chest = chest;
            this.legs = legs;
            this.feet = feet;
        }

        Item forSlot(EquipmentSlot slot) {
            return switch (slot) {
                case CHEST -> this.chest;
                case LEGS  -> this.legs;
                case FEET  -> this.feet;
                default    -> null;
            };
        }
    }

    @SubscribeEvent
    public static void onDragonDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof EnderDragon)) return;

        MinecraftServer server = event.getEntity().getServer();
        if (server == null) return;

        WorldProgress.get(server).markDragonDefeated();
    }

    @SubscribeEvent
    public static void onMobJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Mob mob)) return;

        // Base types, so husks and drowned come along with zombies, and strays and wither skeletons
        // with skeletons.
        if (!(mob instanceof Zombie || mob instanceof AbstractSkeleton)) return;

        CompoundTag data = mob.getPersistentData();
        if (data.getBoolean(TAG_GEAR_ROLLED)) return;
        data.putBoolean(TAG_GEAR_ROLLED, true);

        boolean afterDragon = WorldProgress.isDragonDefeated(event.getLevel());
        float chance = afterDragon ? CHANCE_POST_DRAGON : CHANCE_PRE_DRAGON;
        RandomSource random = mob.getRandom();

        for (EquipmentSlot slot : ROLLED_SLOTS) {
            // Clear vanilla's difficulty-scaled roll first: this table governs these three slots
            // outright, so a miss here has to mean an empty slot rather than whatever vanilla left.
            mob.setItemSlot(slot, ItemStack.EMPTY);

            if (random.nextFloat() >= chance) continue;

            ArmorSet set = afterDragon
                    ? (random.nextFloat() < DIAMOND_SHARE ? ArmorSet.DIAMOND : ArmorSet.IRON)
                    : (random.nextBoolean() ? ArmorSet.LEATHER : ArmorSet.CHAINMAIL);

            mob.setItemSlot(slot, new ItemStack(set.forSlot(slot)));
        }
    }
}
