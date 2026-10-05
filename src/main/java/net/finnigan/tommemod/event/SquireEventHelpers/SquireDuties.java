package net.finnigan.tommemod.event.SquireEventHelpers;

import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.finnigan.tommemod.village.SquireGear;
import net.finnigan.tommemod.village.VillageTier;
import net.finnigan.tommemod.village.WarriorKit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * What each kind of squire does for a Warrior once {@link SquireTickHandler} has established that it
 * is on shift and the village has Warriors.
 *
 * <h2>Kit goes in the bunk chest</h2>
 * Squires no longer put anything on a Warrior. Each Barracks Warrior has a chest beside its bed, and
 * the squires leave what they have for it there; the Warrior collects it and decides for itself what
 * to wear, using the same rules the squires used to apply (see WarriorKit). A Warrior without a bunk -
 * one from before Barracks existed - gets nothing from the squires.
 *
 * <h2>One piece a day</h2>
 * The three outfitters - Armorer, Weaponsmith, Fletcher - leave at most one piece per Minecraft day
 * each. The day they last gave something is written into the villager's own persistent data, so it
 * survives a save, a chunk unload and a server restart.
 *
 * <p>The Cleric and the Fletcher's arrows are exempt: they keep each chest stocked (two healing
 * potions, one strength potion from tier 2, a stack of arrows for a Warrior that shoots) rather than
 * handing out one thing a day, because those are consumed.
 */
public final class SquireDuties {

    /** Where a squire's daily allowance is tracked, on the squire itself. */
    private static final String LAST_ISSUE_DAY_KEY = "TommeModSquireLastIssueDay";
    private static final long TICKS_PER_DAY = 24000L;
    private static final int HEALING_STOCK = 2;
    private static final int STRENGTH_STOCK = 1;

    private SquireDuties() {
    }

    public static void serve(ServerLevel level, Villager squire, List<WarriorVillagerEntity> warriors, int tier) {
        List<Bunk> bunks = new ArrayList<>();
        for (WarriorVillagerEntity warrior : warriors) {
            Container chest = bunkChest(level, warrior);
            if (chest != null) bunks.add(new Bunk(warrior, chest));
        }
        if (bunks.isEmpty()) return;

        VillagerProfession profession = squire.getVillagerData().getProfession();
        if (profession == VillagerProfession.CLERIC) {
            for (Bunk bunk : bunks) stockPotions(level, squire, bunk, tier);
            return;
        }
        if (profession == VillagerProfession.FLETCHER) {
            for (Bunk bunk : bunks) stockArrows(bunk, tier);
        }

        if (!hasAllowanceToday(level, squire)) return;
        if (issueEquipment(level, squire, bunks, tier, profession)) spendAllowance(level, squire);
    }

    /** A Warrior and the chest by its bed. */
    private record Bunk(WarriorVillagerEntity warrior, Container chest) {
    }

    @Nullable
    private static Container bunkChest(ServerLevel level, WarriorVillagerEntity warrior) {
        BlockPos pos = warrior.getBunkChest();
        if (pos == null || !level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof ChestBlockEntity chest ? chest : null;
    }

    // ---- Armorer, Weaponsmith, Fletcher ----

    /** One squire's offer: what it wants to hand over, and which slot that goes in. */
    private record Offer(EquipmentSlot slot, ItemStack stack) {
    }

    /** Leaves today's piece in the chest of a Warrior that would take it, if there is one. */
    private static boolean issueEquipment(ServerLevel level, Villager squire, List<Bunk> bunks, int tier,
                                          VillagerProfession profession) {
        // Rolled once, not per Warrior: a Weaponsmith's work period produces one weapon, and which
        // Warrior ends up with it must not change what came off the anvil.
        ItemStack forged = profession == VillagerProfession.WEAPONSMITH
                ? forgeWeapon(tier, level.getRandom())
                : ItemStack.EMPTY;

        for (Bunk bunk : bunks) {
            Offer offer = offerFor(bunk.warrior(), tier, profession, forged);
            if (offer == null || alreadyWaiting(bunk.chest(), offer)) continue;
            if (!insert(bunk.chest(), offer.stack())) continue;
            celebrate(level, squire);
            return true;
        }
        return false;
    }

    /** What this squire would give this Warrior, or null if it has nothing this Warrior would take. */
    @Nullable
    private static Offer offerFor(WarriorVillagerEntity warrior, int tier,
                                  VillagerProfession profession, ItemStack forged) {
        Offer offer = null;
        if (profession == VillagerProfession.ARMORER) {
            EquipmentSlot slot = neediestArmorSlot(warrior, tier);
            Item item = slot == null ? null : VillageTier.armorFor(tier, slot);
            if (item != null) offer = new Offer(slot, new ItemStack(item));
        } else if (profession == VillagerProfession.WEAPONSMITH) {
            if (!forged.isEmpty()) offer = new Offer(EquipmentSlot.MAINHAND, forged.copy());
        } else if (profession == VillagerProfession.FLETCHER) {
            Item bow = VillageTier.bowFor(tier);
            if (bow != null) offer = new Offer(EquipmentSlot.MAINHAND, new ItemStack(bow));
        }
        // Only offer what the Warrior would actually pick up - anything else would sit in the chest.
        return offer != null && WarriorKit.wants(warrior, offer.stack()) ? offer : null;
    }

    /** Whether the chest already holds something at least as good for this slot, still uncollected. */
    private static boolean alreadyWaiting(Container chest, Offer offer) {
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack inChest = chest.getItem(i);
            if (inChest.isEmpty()) continue;
            if (inChest.is(offer.stack().getItem())) return true;
            boolean sameKind = offer.slot().isArmor()
                    ? LivingEntity.getEquipmentSlotForItem(inChest) == offer.slot()
                    : WarriorKit.isRanged(inChest) == WarriorKit.isRanged(offer.stack());
            if (sameKind && !SquireGear.isStronger(offer.stack(), inChest, offer.slot())) return true;
        }
        return false;
    }

    /**
     * The armor slot this Warrior would gain most from, or null if this tier can't improve on any of
     * them. Bare slots come first, then upgrades to pieces the village issued.
     */
    @Nullable
    private static EquipmentSlot neediestArmorSlot(WarriorVillagerEntity warrior, int tier) {
        EquipmentSlot upgradable = null;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmor()) continue;
            Item item = VillageTier.armorFor(tier, slot);
            if (item == null) continue;

            ItemStack current = warrior.getItemBySlot(slot);
            if (!SquireGear.isStronger(new ItemStack(item), current, slot)) continue;
            if (current.isEmpty()) return slot;
            if (upgradable == null && warrior.isSquireIssued(slot)) upgradable = slot;
        }
        return upgradable;
    }

    /**
     * One weapon from this tier's rack. A tier 3 Weaponsmith rolls its diamond chance once per work
     * period - it either has the stock for something better today or it does not.
     */
    private static ItemStack forgeWeapon(int tier, RandomSource random) {
        List<Item> diamond = VillageTier.diamondWeaponsFor(tier);
        if (!diamond.isEmpty() && random.nextDouble() < ModConfig.SQUIRE_DIAMOND_ROLL_CHANCE.get()) {
            return new ItemStack(diamond.get(random.nextInt(diamond.size())));
        }

        List<Item> iron = VillageTier.weaponsFor(tier);
        if (iron.isEmpty()) return ItemStack.EMPTY;
        return new ItemStack(iron.get(random.nextInt(iron.size())));
    }

    /** Keeps a stack of arrows waiting for a Warrior that shoots (or has a bow waiting for it). */
    private static void stockArrows(Bunk bunk, int tier) {
        Item arrow = VillageTier.arrowFor(tier);
        if (arrow == null) return;
        boolean shoots = WarriorKit.isRanged(bunk.warrior().getMainHandItem()) || count(bunk.chest(), WarriorKit::isRanged) > 0;
        if (!shoots || count(bunk.chest(), s -> s.is(arrow)) > 0) return;
        insert(bunk.chest(), new ItemStack(arrow, 16));
    }

    // ---- Cleric ----

    /** Keeps healing (and from tier 2, strength) potions in each bunk chest for the Warrior to drink. */
    private static void stockPotions(ServerLevel level, Villager squire, Bunk bunk, int tier) {
        Potion healing = VillageTier.healingFor(tier);
        Potion strength = VillageTier.strengthFor(tier);
        boolean stocked = false;
        if (healing != null && count(bunk.chest(), s -> s.is(Items.POTION) && PotionUtils.getPotion(s) == healing) < HEALING_STOCK) {
            stocked = insert(bunk.chest(), PotionUtils.setPotion(new ItemStack(Items.POTION), healing));
        }
        if (strength != null && count(bunk.chest(), s -> s.is(Items.POTION) && PotionUtils.getPotion(s) == strength) < STRENGTH_STOCK) {
            stocked |= insert(bunk.chest(), PotionUtils.setPotion(new ItemStack(Items.POTION), strength));
        }
        if (stocked) {
            level.playSound(null, squire.getX(), squire.getY(), squire.getZ(), SoundEvents.BREWING_STAND_BREW,
                    squire.getSoundSource(), 0.6F, 1.0F);
        }
    }

    // ---- Chest helpers ----

    private static boolean insert(Container chest, ItemStack stack) {
        for (int i = 0; i < chest.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack existing = chest.getItem(i);
            if (existing.isEmpty()) {
                chest.setItem(i, stack.copy());
                stack.setCount(0);
            } else if (ItemStack.isSameItemSameTags(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                int moved = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(moved);
                stack.shrink(moved);
            }
        }
        chest.setChanged();
        return stack.isEmpty();
    }

    private static int count(Container chest, Predicate<ItemStack> test) {
        int n = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            if (!s.isEmpty() && test.test(s)) n += s.getCount();
        }
        return n;
    }

    // ---- Daily allowance ----

    private static boolean hasAllowanceToday(ServerLevel level, Villager squire) {
        // Tested for presence rather than compared against a default: an absent key reads as 0, which
        // is a real day number, and on day 0 of a fresh world that would read as "already gave today".
        if (!squire.getPersistentData().contains(LAST_ISSUE_DAY_KEY)) return true;
        return squire.getPersistentData().getLong(LAST_ISSUE_DAY_KEY) != level.getDayTime() / TICKS_PER_DAY;
    }

    private static void spendAllowance(ServerLevel level, Villager squire) {
        squire.getPersistentData().putLong(LAST_ISSUE_DAY_KEY, level.getDayTime() / TICKS_PER_DAY);
    }

    /** The same noise a villager makes over a completed trade - this is the same kind of moment. */
    private static void celebrate(ServerLevel level, Villager squire) {
        BlockPos pos = squire.blockPosition();
        level.playSound(null, pos, SoundEvents.VILLAGER_YES, squire.getSoundSource(), 1.0F, 1.0F);
    }
}
