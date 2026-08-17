package net.finnigan.tommemod.client.screen;

import net.finnigan.tommemod.network.ModNetwork;
import net.finnigan.tommemod.network.packet.UnlockSkillNodePacket;
import net.finnigan.tommemod.skill.Skill;
import net.finnigan.tommemod.skill.SkillCategory;
import net.finnigan.tommemod.skill.SkillNode;
import net.finnigan.tommemod.skill.SkillProgressView;
import net.finnigan.tommemod.skill.SkillTreeManager;
import net.finnigan.tommemod.skill.data.ModSkillCapabilities;
import net.finnigan.tommemod.skill.effect.SkillEffect;
import net.finnigan.tommemod.skill.requirement.SkillContext;
import net.finnigan.tommemod.skill.requirement.SkillRequirement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The skill trees, in the shape of a quest book: skills listed down the left under their category
 * headings, the selected skill's tree drawn as a connected graph in the middle, and whatever node is
 * selected explained at the bottom.
 *
 * Everything drawn here comes from the synced definitions and the player's own capability - the screen
 * holds no skill knowledge of its own and never hardcodes a node. A datapack that adds a fifteenth
 * skill gets a sidebar entry and a working tree without this class changing.
 *
 * The Unlock button only ever asks; the server decides (see UnlockSkillNodePacket). Greying it out is
 * a courtesy so the player can see at a glance what they qualify for, not a check.
 */
public class SkillTreeScreen extends Screen {

    // ---- Chrome. Hand-drawn, matching MonolithScreen - there is no art asset for this. ----

    private static final int BACKDROP = 0xE0100D14;
    private static final int PANEL = 0xFF1B1720;
    private static final int PANEL_DARK = 0xFF14111A;
    private static final int BORDER = 0xFF3C3448;
    private static final int BORDER_BRIGHT = 0xFF6E6280;
    private static final int TEXT = 0xFFE8E2F0;
    private static final int TEXT_DIM = 0xFF9A92A8;
    private static final int TEXT_HEAD = 0xFFB9A6E0;

    private static final int LINE_LOCKED = 0xFF332E3C;
    private static final int LINE_OPEN = 0xFF6E6280;
    private static final int LINE_OWNED = 0xFF6FBF73;

    private static final int NODE_LOCKED = 0xFF241F2C;
    private static final int NODE_AVAILABLE = 0xFF2E2838;
    private static final int NODE_OWNED = 0xFF20321F;
    private static final int NODE_MAXED = 0xFF3A3016;

    private static final int EDGE_LOCKED = 0xFF3C3448;
    private static final int EDGE_AVAILABLE = 0xFFB9A6E0;
    private static final int EDGE_OWNED = 0xFF6FBF73;
    private static final int EDGE_MAXED = 0xFFD9AE3A;

    private static final int XP_BAR_BACK = 0xFF14111A;
    private static final int XP_BAR_FILL = 0xFFB9A6E0;

    // ---- Layout ----

    private static final int SIDEBAR_WIDTH = 108;
    private static final int HEADER_HEIGHT = 30;
    /**
     * Deep enough for the worst case a tree can produce: a description, three effect lines and three
     * requirement lines under the heading. That is the capstone of a cross-tree skill, and a panel
     * that silently truncated its requirements would hide exactly the node hardest to work out.
     */
    private static final int DETAIL_HEIGHT = 98;
    private static final int MARGIN = 12;
    private static final int PADDING = 6;

    private static final int ROW_HEIGHT = 12;
    private static final int CATEGORY_HEIGHT = 15;

    /**
     * Node box size, and the grid pitch a node's x/y are multiplied by.
     *
     * The horizontal pitch is set so that the widest tree - seven columns, x from -3 to 3 - fits the
     * graph area without panning, since a branch the player cannot see is a branch they will not
     * consider. Depth is a different matter: six rows will not fit any Minecraft GUI, so the view is
     * anchored at the root and scrolls downward, which is how a tree is read anyway.
     */
    private static final int NODE_SIZE = 26;
    private static final int GRID_X = 50;
    private static final int GRID_Y = 40;

    private enum NodeState { LOCKED, AVAILABLE, OWNED, MAXED }

    /** One clickable row in the sidebar - either a heading or a skill. */
    private record SidebarRow(int y, int height, @Nullable Skill skill, @Nullable SkillCategory category) {
    }

    private final List<SidebarRow> sidebarRows = new ArrayList<>();

    @Nullable
    private ResourceLocation selectedSkillId;
    @Nullable
    private String selectedNodeId;

    private int panX;
    private int panY;
    private boolean dragging;

    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;

    @Nullable
    private Button unlockButton;

    public SkillTreeScreen() {
        super(Component.translatable("screen.tommemod.skill_tree"));
    }

    @Override
    protected void init() {
        super.init();

        // Fills most of the window rather than a fixed box: a fourteen-skill sidebar and a tree wide
        // enough to branch both ways need the room, and the trees are authored on a grid that scales.
        panelWidth = Math.min(width - MARGIN * 2, 440);
        panelHeight = Math.min(height - MARGIN * 2, 320);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;

        if (selectedSkillId == null) {
            SkillTreeManager.orderedSkills().stream().findFirst()
                    .ifPresent(skill -> selectSkill(skill.id()));
        }

        unlockButton = addRenderableWidget(Button.builder(Component.literal("Unlock"), button -> requestUnlock())
                .bounds(left + panelWidth - 74, top + panelHeight - 24, 62, 16)
                .build());

        layoutSidebar();
    }

    private void layoutSidebar() {
        sidebarRows.clear();
        int y = top + PADDING;

        for (SkillCategory category : SkillTreeManager.orderedCategories()) {
            List<Skill> inCategory = SkillTreeManager.orderedSkills().stream()
                    .filter(skill -> skill.category().equals(category.id()))
                    .toList();
            if (inCategory.isEmpty()) continue;

            sidebarRows.add(new SidebarRow(y, CATEGORY_HEIGHT, null, category));
            y += CATEGORY_HEIGHT;

            for (Skill skill : inCategory) {
                sidebarRows.add(new SidebarRow(y, ROW_HEIGHT, skill, null));
                y += ROW_HEIGHT;
            }
            y += 3;
        }
    }

    private void selectSkill(ResourceLocation skillId) {
        selectedSkillId = skillId;
        selectedNodeId = null;
        // Every tree is authored around x = 0 with its root at y = 0, so resetting the pan puts the
        // root centred at the top of the view. Centring on the first node instead would open trees
        // that branch both ways lopsided, and trees the player has barely started halfway down.
        panX = 0;
        panY = 0;
    }

    // ---- Reading the player ----

    @Nullable
    private SkillProgressView progress() {
        return minecraft == null || minecraft.player == null ? null
                : minecraft.player.getCapability(ModSkillCapabilities.SKILLS).resolve().orElse(null);
    }

    @Nullable
    private Skill selectedSkill() {
        return selectedSkillId == null ? null : SkillTreeManager.skill(selectedSkillId);
    }

    @Nullable
    private SkillNode selectedNode() {
        Skill skill = selectedSkill();
        return skill == null || selectedNodeId == null ? null : skill.node(selectedNodeId);
    }

    // ---- Rendering ----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKDROP);

        panel(graphics, left, top, left + panelWidth, top + panelHeight, PANEL, BORDER);

        renderSidebar(graphics, mouseX, mouseY);

        Skill skill = selectedSkill();
        SkillProgressView progress = progress();
        if (skill == null || progress == null) {
            graphics.drawCenteredString(font, "No skills loaded", left + panelWidth / 2,
                    top + panelHeight / 2, TEXT_DIM);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        renderHeader(graphics, skill, progress);
        renderGraph(graphics, skill, progress, mouseX, mouseY);
        renderDetail(graphics, skill, progress);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        int barRight = left + SIDEBAR_WIDTH;
        graphics.fill(left + 1, top + 1, barRight, top + panelHeight - 1, PANEL_DARK);
        graphics.fill(barRight, top + 1, barRight + 1, top + panelHeight - 1, BORDER);

        for (SidebarRow row : sidebarRows) {
            if (row.category() != null) {
                graphics.drawString(font, row.category().displayName().toUpperCase(java.util.Locale.ROOT),
                        left + PADDING, row.y() + 4, TEXT_HEAD, false);
                continue;
            }

            Skill skill = row.skill();
            if (skill == null) continue;

            boolean selected = skill.id().equals(selectedSkillId);
            boolean hovered = mouseX >= left && mouseX < barRight
                    && mouseY >= row.y() && mouseY < row.y() + row.height();

            if (selected) graphics.fill(left + 1, row.y(), barRight, row.y() + row.height(), 0xFF2E2838);

            SkillProgressView progress = progress();
            int level = progress == null ? 1 : progress.level(skill.id());
            int points = progress == null ? 0 : progress.pointsAvailable(skill.id());

            graphics.drawString(font, (selected ? "▸ " : "  ") + skill.displayName(),
                    left + PADDING, row.y() + 2, selected || hovered ? TEXT : TEXT_DIM, false);

            // Unspent points are the one thing a player needs to spot without opening every tree, so
            // they sit on the row itself rather than only in the header.
            String badge = points > 0 ? String.valueOf(points) : String.valueOf(level);
            graphics.drawString(font, badge, barRight - PADDING - font.width(badge), row.y() + 2,
                    points > 0 ? EDGE_MAXED : TEXT_DIM, false);
        }
    }

    private void renderHeader(GuiGraphics graphics, Skill skill, SkillProgressView progress) {
        int x = left + SIDEBAR_WIDTH + PADDING;
        int y = top + PADDING;
        int right = left + panelWidth - PADDING;

        int level = progress.level(skill.id());
        int points = progress.pointsAvailable(skill.id());

        graphics.drawString(font, skill.displayName(), x, y, TEXT, false);
        String levelText = "Lv " + level;
        graphics.drawString(font, levelText, x + font.width(skill.displayName()) + 8, y, TEXT_HEAD, false);

        String pointsText = points + (points == 1 ? " point" : " points");
        graphics.drawString(font, pointsText, right - font.width(pointsText), y,
                points > 0 ? EDGE_MAXED : TEXT_DIM, false);

        // Experience bar. At the level cap there is nothing left to fill, so it is drawn full rather
        // than empty - an empty bar on a maxed skill reads as lost progress.
        double needed = skill.xpToAdvance(level);
        double have = progress.xp(skill.id());
        double fraction = needed <= 0.0 ? 1.0 : Mth.clamp(have / needed, 0.0, 1.0);

        int barY = y + 12;
        int barWidth = right - x;
        graphics.fill(x, barY, right, barY + 4, XP_BAR_BACK);
        graphics.fill(x, barY, x + (int) (barWidth * fraction), barY + 4, XP_BAR_FILL);

        String xpText = needed <= 0.0 ? "MAX" : format(have) + " / " + format(needed);
        graphics.drawString(font, xpText, right - font.width(xpText), barY + 6, TEXT_DIM, false);

        graphics.fill(x, top + HEADER_HEIGHT, right, top + HEADER_HEIGHT + 1, BORDER);
    }

    private void renderGraph(GuiGraphics graphics, Skill skill, SkillProgressView progress, int mouseX, int mouseY) {
        int x0 = left + SIDEBAR_WIDTH + 1;
        int y0 = top + HEADER_HEIGHT + 1;
        int x1 = left + panelWidth - 1;
        int y1 = top + panelHeight - DETAIL_HEIGHT;

        graphics.enableScissor(x0, y0, x1, y1);

        int originX = (x0 + x1) / 2 + panX;
        int originY = y0 + 30 + panY;

        // Connections first, so nodes sit on top of their own lines rather than being cut by them.
        for (SkillNode node : skill.nodes().values()) {
            for (String parentId : node.parents()) {
                SkillNode parent = skill.node(parentId);
                if (parent == null) continue;
                drawConnection(graphics, skill, progress, parent, node, originX, originY);
            }
        }

        for (SkillNode node : skill.nodes().values()) {
            drawNode(graphics, skill, progress, node, originX, originY, mouseX, mouseY);
        }

        graphics.disableScissor();
    }

    private void drawConnection(GuiGraphics graphics, Skill skill, SkillProgressView progress,
                                SkillNode parent, SkillNode child, int originX, int originY) {
        int px = originX + parent.x() * GRID_X;
        int py = originY + parent.y() * GRID_Y;
        int cx = originX + child.x() * GRID_X;
        int cy = originY + child.y() * GRID_Y;

        // Lit only once the parent is actually owned, which is what the connection means: parents are
        // an implicit rank-1 requirement on their children (see SkillNode#allRequirements).
        boolean parentOwned = progress.nodeRank(skill.id(), parent.id()) > 0;
        boolean childOwned = progress.nodeRank(skill.id(), child.id()) > 0;
        int colour = childOwned ? LINE_OWNED : parentOwned ? LINE_OPEN : LINE_LOCKED;

        int fromY = py + NODE_SIZE / 2;
        int toY = cy - NODE_SIZE / 2;
        int midY = (fromY + toY) / 2;

        vLine(graphics, px, fromY, midY, colour);
        hLine(graphics, Math.min(px, cx), Math.max(px, cx), midY, colour);
        vLine(graphics, cx, midY, toY, colour);
    }

    private void drawNode(GuiGraphics graphics, Skill skill, SkillProgressView progress, SkillNode node,
                          int originX, int originY, int mouseX, int mouseY) {
        int cx = originX + node.x() * GRID_X;
        int cy = originY + node.y() * GRID_Y;
        int x = cx - NODE_SIZE / 2;
        int y = cy - NODE_SIZE / 2;

        NodeState state = stateOf(skill, progress, node);
        boolean selected = node.id().equals(selectedNodeId);
        boolean hovered = mouseX >= x && mouseX < x + NODE_SIZE && mouseY >= y && mouseY < y + NODE_SIZE;

        int fill = switch (state) {
            case LOCKED -> NODE_LOCKED;
            case AVAILABLE -> NODE_AVAILABLE;
            case OWNED -> NODE_OWNED;
            case MAXED -> NODE_MAXED;
        };
        int edge = switch (state) {
            case LOCKED -> EDGE_LOCKED;
            case AVAILABLE -> EDGE_AVAILABLE;
            case OWNED -> EDGE_OWNED;
            case MAXED -> EDGE_MAXED;
        };

        panel(graphics, x, y, x + NODE_SIZE, y + NODE_SIZE, fill, selected || hovered ? BORDER_BRIGHT : edge);
        graphics.renderItem(node.icon(), x + (NODE_SIZE - 16) / 2, y + (NODE_SIZE - 16) / 2);

        // A locked node is dimmed by drawing over it rather than by tinting the icon, which keeps the
        // item recognisable while still reading as out of reach.
        if (state == NodeState.LOCKED) {
            graphics.fill(x + 1, y + 1, x + NODE_SIZE - 1, y + NODE_SIZE - 1, 0x99100D14);
        }

        // Single-rank nodes are counted too. Hiding the line on them saved a little clutter and cost
        // more than it saved: a node with nothing under it reads as a node whose state the screen is
        // not telling you, when in fact 0/1 and 1/1 are the whole of what there is to say about it.
        String rankText = progress.nodeRank(skill.id(), node.id()) + "/" + node.maxRank();
        graphics.drawString(font, rankText, cx - font.width(rankText) / 2, y + NODE_SIZE + 1,
                state == NodeState.LOCKED ? TEXT_DIM : TEXT, false);

        // Only the node under the cursor, or the one being read about, is labelled. Every node
        // labelled at once turns the graph into overlapping text at any grid pitch tight enough to
        // fit a whole tree on screen - and the detail panel already names what is selected.
        if (hovered || selected) {
            String title = node.title();
            int labelX = cx - font.width(title) / 2;
            graphics.fill(labelX - 2, y - 12, labelX + font.width(title) + 2, y - 1, PANEL_DARK);
            graphics.drawString(font, title, labelX, y - 11, TEXT, false);
        }
    }

    private void renderDetail(GuiGraphics graphics, Skill skill, SkillProgressView progress) {
        int y0 = top + panelHeight - DETAIL_HEIGHT;
        int x = left + SIDEBAR_WIDTH + PADDING;
        int right = left + panelWidth - PADDING;

        graphics.fill(left + SIDEBAR_WIDTH + 1, y0, left + panelWidth - 1, y0 + 1, BORDER);

        SkillNode node = selectedNode();
        if (node == null) {
            graphics.drawString(font, skill.description().isEmpty()
                            ? "Select a node to see what it does."
                            : skill.description(),
                    x, y0 + 8, TEXT_DIM, false);
            if (unlockButton != null) unlockButton.visible = false;
            return;
        }

        int rank = progress.nodeRank(skill.id(), node.id());
        boolean maxed = rank >= node.maxRank();
        int nextRank = Math.min(rank + 1, node.maxRank());

        String heading = node.title().toUpperCase(java.util.Locale.ROOT)
                + "  " + rank + "/" + node.maxRank();
        graphics.drawString(font, heading, x, y0 + 5, TEXT, false);

        String cost = maxed ? "Fully upgraded" : "Cost: " + node.costOf(nextRank)
                + (node.costOf(nextRank) == 1 ? " point" : " points");
        graphics.drawString(font, cost, right - font.width(cost), y0 + 5, maxed ? EDGE_MAXED : TEXT_DIM, false);

        int lineY = y0 + 17;
        if (!node.description().isEmpty()) {
            graphics.drawString(font, node.description(), x, lineY, TEXT_DIM, false);
            lineY += 10;
        }

        // What the next rank is worth, not what the current one already gave - the question in front
        // of a player looking at this panel is whether to spend the point.
        for (SkillEffect effect : node.effects()) {
            if (lineY > top + panelHeight - 20) break;
            graphics.drawString(font, effect.describe(nextRank), x, lineY, TEXT_DIM, false);
            lineY += 10;
        }

        SkillContext context = new SkillContext(progress, skill.id(), node.id());
        for (SkillRequirement requirement : node.allRequirements()) {
            if (lineY > top + panelHeight - 20) break;

            boolean met = requirement.test(context);
            graphics.drawString(font, Component.literal(met ? "✔ " : "✘ ")
                            .append(requirement.describe(context))
                            .withStyle(met ? ChatFormatting.GREEN : ChatFormatting.RED),
                    x, lineY, 0xFFFFFF, false);
            lineY += 10;
        }

        if (unlockButton != null) {
            unlockButton.visible = true;
            unlockButton.active = !maxed
                    && progress.pointsAvailable(skill.id()) >= node.costOf(nextRank)
                    && node.allRequirements().stream().allMatch(requirement -> requirement.test(context));
        }
    }

    private NodeState stateOf(Skill skill, SkillProgressView progress, SkillNode node) {
        int rank = progress.nodeRank(skill.id(), node.id());
        if (rank >= node.maxRank()) return NodeState.MAXED;
        if (rank > 0) return NodeState.OWNED;

        SkillContext context = new SkillContext(progress, skill.id(), node.id());
        return node.allRequirements().stream().allMatch(requirement -> requirement.test(context))
                ? NodeState.AVAILABLE : NodeState.LOCKED;
    }

    // ---- Input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        if (mouseX < left + SIDEBAR_WIDTH) {
            for (SidebarRow row : sidebarRows) {
                if (row.skill() == null) continue;
                if (mouseY >= row.y() && mouseY < row.y() + row.height()) {
                    selectSkill(row.skill().id());
                    return true;
                }
            }
            return false;
        }

        Skill skill = selectedSkill();
        if (skill == null) return false;

        int x0 = left + SIDEBAR_WIDTH + 1;
        int y0 = top + HEADER_HEIGHT + 1;
        int y1 = top + panelHeight - DETAIL_HEIGHT;
        if (mouseY < y0 || mouseY >= y1) return false;

        int originX = (x0 + left + panelWidth - 1) / 2 + panX;
        int originY = y0 + 30 + panY;

        for (SkillNode node : skill.nodes().values()) {
            int nx = originX + node.x() * GRID_X - NODE_SIZE / 2;
            int ny = originY + node.y() * GRID_Y - NODE_SIZE / 2;
            if (mouseX >= nx && mouseX < nx + NODE_SIZE && mouseY >= ny && mouseY < ny + NODE_SIZE) {
                selectedNodeId = node.id();
                return true;
            }
        }

        dragging = true;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            panX += (int) dragX;
            panY += (int) dragY;
            clampPan();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /**
     * Keeps the tree in the window.
     *
     * Without this a stray drag scrolls the graph into empty space, and since the nodes are the only
     * thing drawn in that area there is no landmark left to drag back by - the tree simply appears to
     * have vanished.
     */
    private void clampPan() {
        Skill skill = selectedSkill();
        if (skill == null) return;

        int deepest = skill.nodes().values().stream().mapToInt(SkillNode::y).max().orElse(0);
        int widest = skill.nodes().values().stream().mapToInt(node -> Math.abs(node.x())).max().orElse(0);

        panY = Mth.clamp(panY, -deepest * GRID_Y, 0);
        panX = Mth.clamp(panX, -widest * GRID_X, widest * GRID_X);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= left + SIDEBAR_WIDTH) {
            panY += (int) (delta * 12);
            clampPan();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void requestUnlock() {
        Skill skill = selectedSkill();
        SkillNode node = selectedNode();
        if (skill == null || node == null) return;

        ModNetwork.CHANNEL.sendToServer(new UnlockSkillNodePacket(skill.id(), node.id()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- Drawing helpers ----

    private static void panel(GuiGraphics graphics, int x0, int y0, int x1, int y1, int fill, int border) {
        graphics.fill(x0, y0, x1, y1, fill);
        graphics.fill(x0, y0, x1, y0 + 1, border);
        graphics.fill(x0, y1 - 1, x1, y1, border);
        graphics.fill(x0, y0, x0 + 1, y1, border);
        graphics.fill(x1 - 1, y0, x1, y1, border);
    }

    private static void hLine(GuiGraphics graphics, int x0, int x1, int y, int colour) {
        graphics.fill(x0, y, x1 + 1, y + 1, colour);
    }

    private static void vLine(GuiGraphics graphics, int x, int y0, int y1, int colour) {
        graphics.fill(x, Math.min(y0, y1), x + 1, Math.max(y0, y1), colour);
    }

    /** Experience runs to five figures fast; whole numbers with a thousands mark stay readable. */
    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%,d", Math.round(value));
    }
}
