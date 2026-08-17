package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.bonus.ModSkillBonuses;
import net.finnigan.tommemod.skill.bonus.SkillBonuses;
import net.finnigan.tommemod.util.ModTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Uniques tree, which replaced Unarmed.
 *
 * Everything here has to be general, because a player only ever carries one unique and the tree has to
 * be worth taking whichever one that is. So there is no node about souls, or about freezing, or about
 * sonic beams - only the two things every unique in the mod has in common: a heavy left-click swing,
 * and a right-click ability that puts itself on cooldown. Both are read off the
 * {@code tommemod:unique} tag rather than off any class, since the uniques share no common superclass.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillUniqueBonuses {

    /**
     * Extra damage on a unique's own swing.
     *
     * LOW, so the shared melee keys have already landed and this multiplies the real figure. Scoped to
     * the swing rather than to any damage the holder deals: a unique's ability damage is dealt by
     * projectiles and handlers of its own, and rolling those in here would pay twice for one node.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDealDamage(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (event.getSource().getDirectEntity() != player) return;

        ItemStack weapon = player.getMainHandItem();
        if (!weapon.is(ModTags.Items.UNIQUE)) return;

        double bonus = SkillBonuses.get(player, ModSkillBonuses.UNIQUE_DAMAGE);
        if (bonus > 0.0) event.setAmount(event.getAmount() * (float) (1.0 + bonus));
    }

    /**
     * How much of a cooldown a unique's ability should actually serve.
     *
     * Read by {@link net.finnigan.tommemod.mixin.ItemCooldownsMixin}, which is the only place the
     * question can be asked: a cooldown is set by each weapon calling
     * {@code player.getCooldowns().addCooldown(...)} with its own number, and there is no event around
     * it. Shortening it at the point it is written means every unique in the mod is covered, including
     * ones added later, without touching any of them.
     */
    public static int shortenCooldown(Player player, ItemStack item, int ticks) {
        if (!item.is(ModTags.Items.UNIQUE)) return ticks;

        double cut = SkillBonuses.reduction(player, ModSkillBonuses.ABILITY_COOLDOWN);
        if (cut <= 0.0) return ticks;

        return Math.max(1, (int) Math.round(ticks * (1.0 - cut)));
    }
}
