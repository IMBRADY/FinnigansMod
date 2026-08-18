package net.finnigan.tommemod.skill.event;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.skill.SkillService;
import net.finnigan.tommemod.skill.xp.ModSkillActions;
import net.finnigan.tommemod.skill.xp.SkillAction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.finnigan.tommemod.item.custom.MusketItem;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Turns fighting into skill actions.
 *
 * Damage is reported at LOWEST priority, after everything - including this mod's own combat bonuses -
 * has finished adjusting it, so what a skill is paid for is what the target actually took rather than
 * what was originally swung.
 *
 * How a blow was delivered decides which action it becomes, and that is the only classification done
 * in Java: bare hands, a held weapon, an arrow, a bolt, or something thrown. Which of Melee, Archery,
 * Marksmanship or Unarmed cares about each is left to the tree files, which is what lets Archery and
 * Marksmanship overlap on some shots and diverge on others without either of them being hardcoded.
 */
@Mod.EventBusSubscriber(modid = TommeMod.MOD_ID)
public class SkillCombatTracker {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamageDealt(LivingHurtEvent event) {
        if (event.getAmount() <= 0.0F) return;

        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof ServerPlayer player && !player.equals(event.getEntity())) {
            ResourceLocation action = classifyAttack(player, event.getSource().getDirectEntity());
            if (action != null) {
                SkillService.award(player, SkillAction.of(action, event.getAmount())
                        .withEntity(event.getEntity())
                        .withTool(player.getItemBySlot(EquipmentSlot.MAINHAND)));
            }

            // A landed projectile is its own event as well as a damage one, so a precision tree can
            // pay for the shot itself - and for how far it flew - rather than for the damage.
            if (event.getSource().getDirectEntity() instanceof Projectile projectile) {
                SkillService.award(player, SkillAction.of(ModSkillActions.PROJECTILE_HIT,
                                projectile.distanceTo(player))
                        .withEntity(event.getEntity())
                        .withTool(player.getItemBySlot(EquipmentSlot.MAINHAND)));
            }
        }

        if (event.getEntity() instanceof ServerPlayer victim) {
            SkillService.award(victim, SkillAction.of(ModSkillActions.DAMAGE_TAKEN, event.getAmount())
                    .withEntity(attacker));
        }
    }

    /**
     * A kill, posted twice: once unqualified and once with the weapon that landed it.
     *
     * The unqualified action is what a tree uses when it means any kill at all - Uniques does, and
     * filters on the weapon's tag. A combat tree that took it directly was being paid for every kill
     * in the game, which is how Melee came to earn from crossbow bolts and thrown tridents.
     */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && !player.equals(event.getEntity())) {
            ItemStack weapon = player.getItemBySlot(EquipmentSlot.MAINHAND);

            SkillService.award(player, SkillAction.once(ModSkillActions.KILL)
                    .withEntity(event.getEntity())
                    .withTool(weapon));

            ResourceLocation classified = classifyKill(player, event.getSource().getDirectEntity());
            if (classified != null) {
                SkillService.award(player, SkillAction.once(classified)
                        .withEntity(event.getEntity())
                        .withTool(weapon));
            }
        }
    }

    /** The kill action matching the damage action the killing blow would have been classified as. */
    @Nullable
    private static ResourceLocation classifyKill(ServerPlayer player, @Nullable Entity directEntity) {
        ResourceLocation damage = classifyAttack(player, directEntity);
        if (damage == null) return null;

        if (damage.equals(ModSkillActions.DAMAGE_DEALT_MELEE)) return ModSkillActions.KILL_MELEE;
        if (damage.equals(ModSkillActions.DAMAGE_DEALT_UNARMED)) return ModSkillActions.KILL_UNARMED;
        if (damage.equals(ModSkillActions.DAMAGE_DEALT_BOW)) return ModSkillActions.KILL_BOW;
        if (damage.equals(ModSkillActions.DAMAGE_DEALT_CROSSBOW)) return ModSkillActions.KILL_CROSSBOW;
        if (damage.equals(ModSkillActions.DAMAGE_DEALT_THROWN)) return ModSkillActions.KILL_THROWN;
        if (damage.equals(ModSkillActions.DAMAGE_DEALT_MUSKET)) return ModSkillActions.KILL_MUSKET;
        if (damage.equals(ModSkillActions.DAMAGE_DEALT_TRIDENT)) return ModSkillActions.KILL_TRIDENT;
        return null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onShieldBlock(ShieldBlockEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillService.award(player, SkillAction.of(ModSkillActions.DAMAGE_BLOCKED, event.getBlockedDamage())
                    .withEntity(event.getDamageSource().getEntity()));
        }
    }

    /**
     * Which kind of blow this was, or null for damage a skill has no business being paid for -
     * environmental sources, and anything the player caused only indirectly.
     */
    @Nullable
    public static ResourceLocation classifyAttack(ServerPlayer player, @Nullable Entity directEntity) {
        // Checked ahead of the arrow branch: a thrown trident IS an AbstractArrow as far as the class
        // hierarchy is concerned, so left below it, it fell through to "has gravity, therefore a bow".
        if (directEntity instanceof ThrownTrident) return ModSkillActions.DAMAGE_DEALT_TRIDENT;

        if (directEntity instanceof AbstractArrow arrow) {
            // Fired from what? The arrow itself doesn't say, so the hand does. A player who has since
            // swapped weapons is credited by what they are holding now, which is close enough and far
            // simpler than stamping every arrow with its launcher.
            ItemStack held = player.getItemBySlot(EquipmentSlot.MAINHAND);
            if (held.getItem() instanceof CrossbowItem) return ModSkillActions.DAMAGE_DEALT_CROSSBOW;
            if (held.getItem() instanceof BowItem) return ModSkillActions.DAMAGE_DEALT_BOW;
            return arrow.isNoGravity() ? ModSkillActions.DAMAGE_DEALT_CROSSBOW : ModSkillActions.DAMAGE_DEALT_BOW;
        }
        if (directEntity instanceof Projectile) return ModSkillActions.DAMAGE_DEALT_THROWN;

        // Not a projectile, so it has to have been landed in person to count.
        if (!(directEntity instanceof LivingEntity) || !directEntity.equals(player)) return null;

        ItemStack weapon = player.getItemBySlot(EquipmentSlot.MAINHAND);
        if (weapon.isEmpty()) return ModSkillActions.DAMAGE_DEALT_UNARMED;
        // A musket shot arrives here rather than above, having no projectile to be recognised by. The
        // held item is the only thing distinguishing it from a swing, which is enough: firing is what a
        // musket does, and a player who clubs something with one can have the shot's credit for it.
        if (weapon.getItem() instanceof MusketItem) return ModSkillActions.DAMAGE_DEALT_MUSKET;
        return ModSkillActions.DAMAGE_DEALT_MELEE;
    }
}
