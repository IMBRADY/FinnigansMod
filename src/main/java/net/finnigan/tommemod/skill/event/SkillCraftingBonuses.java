package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Husbandry and Smithing bonuses.
 *
 * Enchanting power is the one that reaches furthest: EnchantmentLevelSetEvent is what decides how
 * strong the table's three offers are, so raising the level there is exactly the effect a smith would
 * expect - better enchantments for the same bookshelves, rather than cheaper ones.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillCraftingBonuses {

    // ---- Husbandry ----

    @SubscribeEvent
    public static void onBred(BabyEntitySpawnEvent event) {
        Player player = event.getCausedByPlayer();
        if (player == null) return;

        double cooldownCut = SkillBonuses.reduction(player, ModSkillBonuses.BREEDING_COOLDOWN_REDUCTION);
        if (cooldownCut > 0.0) {
            reduceLoveCooldown(event.getParentA(), cooldownCut);
            reduceLoveCooldown(event.getParentB(), cooldownCut);
        }

        if (SkillBonuses.roll(player, ModSkillBonuses.TWIN_BIRTH_CHANCE)) spawnTwin(event);
    }

    /**
     * A second baby alongside the first.
     *
     * Bred properly through the parent rather than copied from the child, so variant-carrying animals
     * - a horse's colour and markings, a sheep's fleece - roll their inheritance again instead of the
     * twin being a clone of its sibling.
     */
    private static void spawnTwin(BabyEntitySpawnEvent event) {
        AgeableMob child = event.getChild();
        if (child == null) return;
        if (!(event.getParentA() instanceof Animal parent) || !(event.getParentB() instanceof AgeableMob other)) return;
        if (!(parent.level() instanceof ServerLevel level)) return;

        AgeableMob twin = parent.getBreedOffspring(level, other);
        if (twin == null) return;

        twin.setBaby(true);
        twin.moveTo(child.getX(), child.getY(), child.getZ(), child.getYRot(), 0.0F);
        level.addFreshEntity(twin);
    }

    /**
     * Animals are put on a two-minute cooldown after breeding, held as an age counter running back up
     * to zero. Cutting it means moving that counter closer to zero, not clearing it - a herd that can
     * be re-bred instantly is not a reward, it is an item duplicator.
     */
    private static void reduceLoveCooldown(LivingEntity parent, double fraction) {
        if (!(parent instanceof Animal animal)) return;

        int age = animal.getAge();
        if (age > 0) animal.setAge((int) Math.round(age * (1.0 - fraction)));
    }

    @SubscribeEvent
    public static void onAnimalDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Animal)) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (!SkillBonuses.roll(player, ModSkillBonuses.ANIMAL_DROP_BONUS)) return;

        // Copied rather than stacked up, so cooked drops, damaged items and NBT all come through
        // exactly as the original did.
        event.getDrops().addAll(event.getDrops().stream()
                .map(drop -> new ItemEntity(drop.level(), drop.getX(), drop.getY(), drop.getZ(),
                        drop.getItem().copy()))
                .toList());
    }

    // ---- Smithing ----

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        Player player = event.getPlayer();
        if (player == null || event.getOutput().isEmpty()) return;

        double costCut = SkillBonuses.reduction(player, ModSkillBonuses.ANVIL_COST_REDUCTION);
        if (costCut > 0.0) {
            event.setCost(Math.max(1, (int) Math.round(event.getCost() * (1.0 - costCut))));
        }

        double efficiency = SkillBonuses.get(player, ModSkillBonuses.REPAIR_EFFICIENCY);
        if (efficiency > 0.0 && event.getOutput().isDamaged()) {
            ItemStack output = event.getOutput().copy();
            int repairedBy = event.getLeft().getDamageValue() - output.getDamageValue();
            if (repairedBy > 0) {
                output.setDamageValue(Math.max(0,
                        output.getDamageValue() - (int) Math.round(repairedBy * efficiency)));
                event.setOutput(output);
            }
        }
    }

}
