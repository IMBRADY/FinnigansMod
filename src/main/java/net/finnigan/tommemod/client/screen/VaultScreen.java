package net.finnigan.tommemod.client.screen;

import net.finnigan.tommemod.network.ModNetwork;
import net.finnigan.tommemod.network.packet.VaultActionPacket;
import net.finnigan.tommemod.network.packet.VaultStatusPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * The Bank vault: the village's wealth in emeralds up top, everything else the bank holds below.
 * Anyone can pay in; the Chief clicks an item to take a stack out (shift-click for all of it).
 */
public class VaultScreen extends Screen {

    private static final int PANEL = 0xE0101824;
    private static final int EDGE = 0xFF3A6EA5;
    private static final int GOLD = 0xFFFFD27F;
    private static final int MUTED = 0xFF9AA7B4;
    private static final int W = 196;
    private static final int H = 200;
    private static final int COLS = 9;
    private static final int CELL = 20;
    private static final int ROWS = 5;

    private final BlockPos vaultPos;
    private VaultStatusPacket status;
    private int left;
    private int top;
    private int ticks;

    private VaultScreen(VaultStatusPacket status) {
        super(Component.literal("Village Vault"));
        this.vaultPos = status.vaultPos;
        this.status = status;
    }

    public static void onStatus(VaultStatusPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof VaultScreen open && open.vaultPos.equals(msg.vaultPos)) {
            open.status = msg;
        } else if (msg.open) {
            mc.setScreen(new VaultScreen(msg));
        }
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        addRenderableWidget(Button.builder(Component.literal("Deposit emeralds"), b -> send(VaultActionPacket.Action.DEPOSIT_EMERALDS, ""))
                .bounds(left + 8, top + H - 26, 88, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Deposit held item"), b -> send(VaultActionPacket.Action.DEPOSIT_HELD, ""))
                .bounds(left + W - 96, top + H - 26, 88, 18).build());
    }

    private void send(VaultActionPacket.Action action, String item) {
        ModNetwork.CHANNEL.sendToServer(new VaultActionPacket(vaultPos, action, item));
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(vaultPos.getCenter()) > 8 * 8) {
            onClose();
            return;
        }
        if (++ticks % 20 == 0) send(VaultActionPacket.Action.REFRESH, "");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Everything but emeralds, which are shown on their own as the village's wealth. */
    private List<VaultStatusPacket.Entry> goods() {
        List<VaultStatusPacket.Entry> out = new ArrayList<>();
        for (VaultStatusPacket.Entry e : status.entries) {
            if (!e.itemId().equals("minecraft:emerald")) out.add(e);
        }
        return out;
    }

    private long emeralds() {
        for (VaultStatusPacket.Entry e : status.entries) if (e.itemId().equals("minecraft:emerald")) return e.count();
        return 0;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && status.canWithdraw) {
            // The wealth line is clickable too: it withdraws emeralds.
            if (mx >= left + 8 && mx <= left + W - 8 && my >= top + 20 && my <= top + 44 && emeralds() > 0) {
                send(hasShiftDown() ? VaultActionPacket.Action.WITHDRAW_ALL : VaultActionPacket.Action.WITHDRAW, "minecraft:emerald");
                return true;
            }
            int idx = cellAt(mx, my);
            List<VaultStatusPacket.Entry> goods = goods();
            if (idx >= 0 && idx < goods.size()) {
                send(hasShiftDown() ? VaultActionPacket.Action.WITHDRAW_ALL : VaultActionPacket.Action.WITHDRAW, goods.get(idx).itemId());
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private int gridLeft() {
        return left + (W - COLS * CELL) / 2;
    }

    private int gridTop() {
        return top + 60;
    }

    private int cellAt(double mx, double my) {
        int col = (int) Math.floor((mx - gridLeft()) / CELL);
        int row = (int) Math.floor((my - gridTop()) / CELL);
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) return -1;
        return row * COLS + col;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(left, top, left + W, top + H, PANEL);
        g.renderOutline(left, top, W, H, EDGE);
        g.drawString(font, "Village Vault", left + 8, top + 7, GOLD);

        // Wealth
        g.fill(left + 8, top + 20, left + W - 8, top + 44, 0x60303030);
        g.renderItem(new ItemStack(Items.EMERALD), left + 13, top + 24);
        g.drawString(font, "Village wealth", left + 34, top + 24, MUTED);
        g.drawString(font, String.format("%,d", emeralds()), left + 34, top + 33, 0xFF6FE07A);

        g.drawString(font, "Stores", left + 8, top + 49, GOLD);
        List<VaultStatusPacket.Entry> goods = goods();
        VaultStatusPacket.Entry hovered = null;
        for (int i = 0; i < COLS * ROWS; i++) {
            int x = gridLeft() + (i % COLS) * CELL;
            int y = gridTop() + (i / COLS) * CELL;
            g.fill(x, y, x + CELL - 2, y + CELL - 2, 0x50000000);
            if (i >= goods.size()) continue;
            VaultStatusPacket.Entry e = goods.get(i);
            ItemStack stack = stackOf(e);
            g.renderItem(stack, x + 1, y + 1);
            g.renderItemDecorations(font, stack, x + 1, y + 1, shortCount(e.count()));
            if (mouseX >= x && mouseX < x + CELL - 2 && mouseY >= y && mouseY < y + CELL - 2) hovered = e;
        }
        if (goods.isEmpty()) g.drawCenteredString(font, "Empty", left + W / 2, gridTop() + ROWS * CELL / 2 - 4, 0xFF606060);
        if (goods.size() > COLS * ROWS) {
            g.drawString(font, "+" + (goods.size() - COLS * ROWS) + " more", left + 8, gridTop() + ROWS * CELL + 2, MUTED);
        }
        String hint = status.canWithdraw ? "Click to take a stack, shift-click for all" : "Only the Chief may take from the vault";
        g.drawCenteredString(font, hint, left + W / 2, top + H - 40, MUTED);

        super.render(g, mouseX, mouseY, partialTick);
        if (hovered != null) {
            g.renderTooltip(font, List.of(stackOf(hovered).getHoverName(), Component.literal(String.format("%,d stored", hovered.count()))),
                    java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    private static ItemStack stackOf(VaultStatusPacket.Entry e) {
        ResourceLocation id = ResourceLocation.tryParse(e.itemId());
        Item item = id != null ? ForgeRegistries.ITEMS.getValue(id) : null;
        return item != null ? new ItemStack(item) : ItemStack.EMPTY;
    }

    private static String shortCount(long n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1_000_000) return (n / 100) / 10.0 + "k";
        return (n / 100_000) / 10.0 + "m";
    }
}
