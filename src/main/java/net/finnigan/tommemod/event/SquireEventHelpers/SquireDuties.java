package net.finnigan.tommemod.event.SquireEventHelpers;

import net.finnigan.tommemod.config.ModConfig;
import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.finnigan.tommemod.village.SquireGear;
import net.finnigan.tommemod.village.VillageTier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * What each kind of squire does for a Warrior once {@link SquireTickHandler} has established that it
 * is on shift and has Warriors in reach.
 *
 * <h2>One piece a day</h2>
 * The three outfitters - Armorer, Weaponsmith, Fletcher - hand over at most one piece per Minecraft
 * day each. The day they last gave something is written into the villager's own persistent data
 * rather than held in a map here, so it survives a save, a chunk unload and a server restart; a
 * squire's daily allowance is a fact about that squire, not about this session.
 *
 * <p>The Cleric is exempt on purpose. Potions are consumed the moment they land, so a Cleric that
 * could only throw one a day would be a Cleric that does nothing; it throws for as long as there is a
 * Warrior in range that is hurt, or (from tier 2) unbuffed, and stops when there is not.
 *
 * <h2>Whose gear is whose</h2>
 * A squire may only take a piece back off a Warrior if the village put it there in the first place.
 * Anything a player equipped is left exactly where it is, and the piece the squire wanted to give is
 * dropped at the Warrior's feet instead - the village makes the offer, the player's choice stands.
 */
public final class SquireDuties {

    /** Where a squire's daily allowance is tracked, on the squire itself. */
    private static final String LAST_ISSUE_DAY_KEY = "TommeModSquireLastIssueDay";
    private static final long TICKS_PER_DAY = 24000L;

    private SquireDuties() {
    }

    public static void serve(ServerLevel level, Villager squire, List<WarriorVillagerEntity> warriors, int tier) {
        double range = ModConfig.SQUIRE_RANGE_BLOCKS.get();
        List<WarriorVillagerEntity> inReach = new ArrayList<>();
        for (WarriorVillagerEntity warrior : warriors) {
            if (squire.distanceToSqr(warrior) <= range * range) inReach.add(warrior);
        }
        if (inReach.isEmpty()) return;

        VillagerProfession profession = squire.getVillagerData().getProfession();
        if (profession == VillagerProfession.CLERIC) {
            tendWounded(level, squire, inReach, tier);
            return;
        }

        // Arrows are not a piece of kit and don't cost the Fletcher its day: a Warrior already
        // carrying a bow the village gave it should not run dry waiting for tomorrow's allowance.
        if (profession == VillagerProfession.FLETCHER) {
            for (WarriorVillagerEntity warrior : inReach) stockArrows(warrior, tier);
        }

        if (!hasAllowanceToday(level, squire)) return;
        if (issueEquipment(level, squire, inReach, tier, profession)) spendAllowance(level, squire);
    }

    // ---- Armorer, Weaponsmith, Fletcher ----

    /** One squire's offer: what it wants to hand over, and which slot that goes in. */
    private record Offer(EquipmentSlot slot, ItemStack stack) {
    }

    /**
     * Hands over today's piece, if there is a Warrior that wants it.
     *
     * <p>Two passes on purpose. The first looks for a Warrior the squire can actually equip - a bare
     * slot, or one holding something the village issued and may take back. Only when no Warrior in
     * reach qualifies does the second pass make the offer anyway, on the ground beside one whose kit
     * belongs to a player. Done in one pass the squire would find the player-equipped Warrior first
     * and spend the day dropping a sword in the dirt while a bare-handed Warrior stood behind it.
     */
    private static boolean issueEquipment(ServerLevel level, Villager squire,
                                          List<WarriorVillagerEntity> warriors, int tier,
                                          VillagerProfession profession) {
        // Rolled once, not per Warrior: a Weaponsmith's work period produces one weapon, and which
        // Warrior ends up with it must not change what came off the anvil.
        ItemStack forged = profession == VillagerProfession.WEAPONSMITH
                ? forgeWeapon(tier, level.getRandom())
                : ItemStack.EMPTY;

        for (WarriorVillagerEntity warrior : warriors) {
            Offer offer = offerFor(warrior, tier, profession, forged);
            if (offer == null) continue;
            if (!warrior.getItemBySlot(offer.slot()).isEmpty() && !warrior.isSquireIssued(offer.slot())) continue;

            warrior.setItemSlot(offer.slot(), offer.stack());
            warrior.markSquireIssued(offer.slot());
            if (profession == VillagerProfession.FLETCHER) stockArrows(warrior, tier);
            celebrate(level, squire);
            return true;
        }

        // Nobody's kit is the village's to replace. The offer still leaves the squire's hands and
        // lands at a Warrior's feet - the village makes its case, and the player's choice stands.
        for (WarriorVillagerEntity warrior : warriors) {
            Offer offer = offerFor(warrior, tier, profession, forged);
            if (offer == null) continue;
            dropAtFeet(level, warrior, offer.stack());
            celebrate(level, squire);
            return true;
        }
        return false;
    }

    /** What this squire would give this Warrior, or null if it has nothing this Warrior needs. */
    @Nullable
    private static Offer offerFor(WarriorVillagerEntity warrior, int tier,
                                  VillagerProfession profession, ItemStack forged) {
        if (profession == VillagerProfession.ARMORER) {
            EquipmentSlot slot = neediestArmorSlot(warrior, tier);
            if (slot == null) return null;
            Item item = VillageTier.armorFor(tier, slot);
            return item == null ? null : new Offer(slot, new ItemStack(item));
        }

        if (profession == VillagerProfession.WEAPONSMITH) {
            if (forged.isEmpty()) return null;
            if (!SquireGear.isStronger(forged, warrior.getItemBySlot(EquipmentSlot.MAINHAND), EquipmentSlot.MAINHAND)) {
                return null;
            }
            return new Offer(EquipmentSlot.MAINHAND, forged.copy());
        }

        if (profession == VillagerProfession.FLETCHER) {
            Item bow = VillageTier.bowFor(tier);
            if (bow == null) return null;
            // A bow is not judged against a halberd. Ranged and melee are different jobs, and asking
            // which does more damage per swing would mean the Fletcher never armed anyone, since a
            // bow's swing damage is nothing. What it offers is a capability the Warrior lacks, so the
            // only question worth asking is whether it already shoots.
            if (isRanged(warrior.getItemBySlot(EquipmentSlot.MAINHAND))) return null;
            return new Offer(EquipmentSlot.MAINHAND, new ItemStack(bow));
        }

        return null;
    }

    private static boolean isRanged(ItemStack stack) {
        return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
    }

    /**
     * The armor slot this Warrior would gain most from, or null if this tier can't improve on any of
     * them. Bare slots come first - a Warrior with no boots at all wants boots more than it wants a
     * better helmet - and only once it is fully covered does the Armorer start upgrading pieces.
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
            if (upgradable == null) upgradable = slot;
        }
        return upgradable;
    }

    /**
     * One weapon from this tier's rack. A tier 3 Weaponsmith rolls its diamond chance once per work
     * period - it either has the stock for something better today or it does not, and which of the
     * three shapes it reaches for is a separate roll from that.
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

    /**
     * Tops up a Warrior's quiver. Only for Warriors that actually shoot, and only into an empty
     * supply slot - the slot is the Chief's stash as well, and a Fletcher should not be turning
     * whatever they left there into arrows.
     */
    private static void stockArrows(WarriorVillagerEntity warrior, int tier) {
        Item arrow = VillageTier.arrowFor(tier);
        if (arrow == null) return;
        if (!isRanged(warrior.getItemBySlot(EquipmentSlot.MAINHAND))) return;
        if (!warrior.getSupplyContainer().getItem(0).isEmpty()) return;
        warrior.getSupplyContainer().setItem(0, new ItemStack(arrow, 16));
    }

    // ---- Cleric ----

    /**
     * Heals first, buffs second, and does nothing at all when there is neither to do. A Cleric that
     * finds every Warrior in range at full health and already strong has finished its round.
     */
    private static void tendWounded(ServerLevel level, Villager squire,
                                    List<WarriorVillagerEntity> warriors, int tier) {
        WarriorVillagerEntity hurt = null;
        WarriorVillagerEntity unbuffed = null;
        for (WarriorVillagerEntity warrior : warriors) {
            if (warrior.getHealth() < warrior.getMaxHealth()) {
                if (hurt == null || warrior.getHealth() / warrior.getMaxHealth()
                        < hurt.getHealth() / hurt.getMaxHealth()) {
                    hurt = warrior;
                }
            } else if (unbuffed == null && !warrior.hasEffect(MobEffects.DAMAGE_BOOST)) {
                unbuffed = warrior;
            }
        }

        if (hurt != null) {
            Potion healing = VillageTier.healingFor(tier);
            if (healing != null) {
                throwPotion(level, squire, hurt, healing);
                return;
            }
        }

        if (unbuffed != null) {
            Potion strength = VillageTier.strengthFor(tier);
            if (strength != null) throwPotion(level, squire, unbuffed, strength);
        }
    }

    /** Lobbed the way a witch lobs one, which is the arc these projectiles are tuned for. */
    private static void throwPotion(ServerLevel level, Villager squire, WarriorVillagerEntity target, Potion potion) {
        double dx = target.getX() - squire.getX();
        double dy = target.getEyeY() - 1.1D - squire.getY();
        double dz = target.getZ() - squire.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        ThrownPotion thrown = new ThrownPotion(level, squire);
        thrown.setItem(PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), potion));
        thrown.setXRot(thrown.getXRot() + 20.0F);
        thrown.shoot(dx, dy + horizontal * 0.2D, dz, 0.75F, 8.0F);

        level.playSound(null, squire.getX(), squire.getY(), squire.getZ(),
                SoundEvents.SPLASH_POTION_THROW, squire.getSoundSource(), 1.0F,
                0.8F + level.getRandom().nextFloat() * 0.4F);
        level.addFreshEntity(thrown);
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

    private static void dropAtFeet(ServerLevel level, WarriorVillagerEntity warrior, ItemStack stack) {
        ItemEntity dropped = new ItemEntity(level, warrior.getX(), warrior.getY() + 0.5D, warrior.getZ(), stack);
        dropped.setDefaultPickUpDelay();
        level.addFreshEntity(dropped);
    }

    /** The same noise a villager makes over a completed trade - this is the same kind of moment. */
    private static void celebrate(ServerLevel level, Villager squire) {
        BlockPos pos = squire.blockPosition();
        level.playSound(null, pos, SoundEvents.VILLAGER_YES, squire.getSoundSource(), 1.0F, 1.0F);
    }
}
