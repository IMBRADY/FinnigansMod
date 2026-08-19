package net.finnigan.tommemod.client.screen;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.finnigan.tommemod.menu.WarriorVillagerMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Chief's equipment screen for a Village Soldier.
 *
 * <p>Every coordinate below is measured off textures/gui/warrior_villager_gui.png rather than chosen,
 * so the widgets land in the wells the art already paints. The panel is 195x164 inside a 256x256
 * sheet, which is why the plain three-argument blit works: it assumes exactly that sheet size.
 *
 * <p>The title is painted into the art, so {@link #renderLabels} draws neither it nor the usual
 * "Inventory" heading - only the stat readout.
 */
public class WarriorVillagerScreen extends AbstractContainerScreen<WarriorVillagerMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TommeMod.MOD_ID, "textures/gui/warrior_villager_gui.png");

    /** The black well the Soldier is drawn in: x 54-102, y 6-75. */
    private static final int PREVIEW_CENTRE_X = 78;
    private static final int PREVIEW_BOTTOM_Y = 73;
    private static final int PREVIEW_SCALE = 32;

    /**
     * The two recessed panels - the wide one at x 109-146 takes the stat names, the narrow one at
     * x 149-168 the numbers, right-aligned. Both run y 29-75, and three rows of 11 sit centred in
     * that with room for a fourth. The names are 36px at their longest, so the wide panel is very
     * nearly full: anything longer than "Armour" needs a shorter word, not a bigger x.
     */
    private static final int STAT_NAME_X = 110;
    private static final int STAT_VALUE_RIGHT_X = 166;
    private static final int STAT_FIRST_Y = 37;
    private static final int STAT_LINE_HEIGHT = 11;

    private static final int CLOSE_BUTTON_X = 173;
    private static final int CLOSE_BUTTON_Y = 140;
    private static final int CLOSE_BUTTON_SIZE = 16;

    private static final int STAT_TEXT_COLOUR = 0x404040;

    public WarriorVillagerScreen(WarriorVillagerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 195;
        this.imageHeight = 164;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics,
                leftPos + PREVIEW_CENTRE_X, topPos + PREVIEW_BOTTOM_Y, PREVIEW_SCALE,
                (float) (leftPos + PREVIEW_CENTRE_X) - mouseX,
                (float) (topPos + PREVIEW_BOTTOM_Y - 50) - mouseY,
                menu.getEntity());

        if (isOverCloseButton(mouseX, mouseY)) {
            guiGraphics.fill(leftPos + CLOSE_BUTTON_X, topPos + CLOSE_BUTTON_Y,
                    leftPos + CLOSE_BUTTON_X + CLOSE_BUTTON_SIZE,
                    topPos + CLOSE_BUTTON_Y + CLOSE_BUTTON_SIZE, 0x40FFFFFF);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        WarriorVillagerEntity soldier = menu.getEntity();
        drawStat(guiGraphics, 0, "gui.tommemod.warrior_villager.health",
                Integer.toString(Math.round(soldier.getHealth())));
        drawStat(guiGraphics, 1, "gui.tommemod.warrior_villager.armour",
                Integer.toString(soldier.getArmorValue()));
        drawStat(guiGraphics, 2, "gui.tommemod.warrior_villager.damage",
                Integer.toString(Math.round(attackDamage(soldier))));
    }

    private void drawStat(GuiGraphics guiGraphics, int row, String nameKey, String value) {
        int y = STAT_FIRST_Y + row * STAT_LINE_HEIGHT;
        Component name = Component.translatable(nameKey);
        guiGraphics.drawString(font, name, STAT_NAME_X, y, STAT_TEXT_COLOUR, false);
        guiGraphics.drawString(font, value, STAT_VALUE_RIGHT_X - font.width(value), y,
                STAT_TEXT_COLOUR, false);
    }

    /**
     * What the Soldier actually hits for: its own attack damage attribute plus whatever the weapon in
     * its hand adds, which is the same sum an item tooltip shows.
     */
    private static float attackDamage(WarriorVillagerEntity soldier) {
        double damage = soldier.getAttributeValue(Attributes.ATTACK_DAMAGE);
        ItemStack weapon = soldier.getItemBySlot(EquipmentSlot.MAINHAND);
        for (AttributeModifier modifier : weapon.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE)) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                damage += modifier.getAmount();
            }
        }
        return (float) damage;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverCloseButton((int) mouseX, (int) mouseY)) {
            minecraft.getSoundManager()
                    .play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                            .forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isOverCloseButton(int mouseX, int mouseY) {
        int x = leftPos + CLOSE_BUTTON_X;
        int y = topPos + CLOSE_BUTTON_Y;
        return mouseX >= x && mouseX < x + CLOSE_BUTTON_SIZE
                && mouseY >= y && mouseY < y + CLOSE_BUTTON_SIZE;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (isOverCloseButton(mouseX, mouseY)) {
            guiGraphics.renderTooltip(font,
                    Component.translatable("gui.tommemod.warrior_villager.close")
                            .withStyle(ChatFormatting.WHITE),
                    mouseX, mouseY);
        }
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
