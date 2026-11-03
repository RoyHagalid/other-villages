package dev.villagerework.client;

import dev.villagerework.ui.StationMenu;
import dev.villagerework.ui.StationRules;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Draws the village station panel, for the station screen and for the panel docked beside vanilla screens.
 *
 *   Fisherman Station
 *   We need   [16 String]      faded once fully donated; a barrier after the evening work, until sunrise
 *   Donate    [ ]
 *   Rewards   [ ] [ ] [X]      one slot per level; locked slots marked with a barrier
 *             [  Reroll   ]    rerolls what's left in the slots (hover: price in emeralds)
 *   Level II
 *   [■■■■■■■□□□]           hover: progress
 *   [Upgrade] [ghost item]  the item for the next level, faded until you put one in
 */
public final class StationPanelRenderer {
	static final Identifier CHEST = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
	public static final int WIDTH = StationMenu.PANEL_WIDTH - 4;
	public static final int HEIGHT = StationMenu.PANEL_HEIGHT;
	static final int LABEL_X = 8;
	private static final int PANEL_BORDER = 0xFF373737;
	private static final int PANEL_FILL = 0xFFC6C6C6;
	private static final int PANEL_LIGHT = 0xFFFFFFFF;
	private static final int PANEL_SHADE = 0xFF555555;
	static final int LABEL = 0xFF404040;
	private static final int BAR_BACK = 0xFF1E1E1E;
	private static final int BAR_FILL = 0xFF80D020;
	private static final int BAR_FULL = 0xFFF0C030;
	static final int GHOST_OVERLAY = 0x9AC6C6C6;
	private static final int BAR_WIDTH = WIDTH - 16;
	private static final int BAR_HEIGHT = 5;

	private StationPanelRenderer() {
	}

	/**
	 * The panel box at (px, py) with its rows. {@code slots} are the five panel slots in menu order; they're
	 * placed relative to (slotLeft, slotTop) like every slot of the screen.
	 */
	public static void draw(GuiGraphicsExtractor graphics, Font font, StationRules rules, Slot[] slots, int px, int py,
			int slotLeft, int slotTop, int mouseX, int mouseY, String title) {
		graphics.fill(px, py, px + WIDTH, py + HEIGHT, PANEL_BORDER);
		graphics.fill(px + 1, py + 1, px + WIDTH - 1, py + HEIGHT - 1, PANEL_LIGHT);
		graphics.fill(px + 2, py + 2, px + WIDTH - 1, py + HEIGHT - 1, PANEL_SHADE);
		graphics.fill(px + 2, py + 2, px + WIDTH - 2, py + HEIGHT - 2, PANEL_FILL);

		if (title != null) graphics.text(font, Component.literal(title), px + LABEL_X, py + 7, LABEL, false);
		label(graphics, font, "We need", px, py + StationMenu.REQUEST_Y + 5);
		label(graphics, font, "Donate", px, py + StationMenu.DONATE_Y + 5);
		label(graphics, font, "Rewards", px, py + StationMenu.REWARD_Y + 5);
		int level = rules.data(StationMenu.D_LEVEL);
		label(graphics, font, "Level " + roman(level), px, py + StationMenu.LEVEL_TEXT_Y);

		for (Slot slot : slots) {
			if (slot.isActive()) slotBackground(graphics, slotLeft + slot.x, slotTop + slot.y);
		}
		drawRequest(graphics, font, rules, px + StationMenu.SLOT_X, py + StationMenu.REQUEST_Y);

		// Locked reward slots: a barrier, so the next level's slot is visible ahead of time.
		for (int i = 0; i < StationMenu.REWARD_SLOTS.length; i++) {
			if (slots[StationMenu.REWARD_SLOTS[i]].isActive()) continue;
			int x = px + StationMenu.REWARD_X[i];
			int y = py + StationMenu.REWARD_Y;
			slotBackground(graphics, x, y);
			graphics.fakeItem(new ItemStack(Items.BARRIER), x, y);
			graphics.fill(x, y, x + 16, y + 16, GHOST_OVERLAY);
		}

		// At max level, the bar, the Upgrade button and the milestone slot are hidden.
		if (rules.isMaxLevel()) return;

		int bx = px + LABEL_X;
		int by = py + StationMenu.BAR_Y;
		int progress = rules.data(StationMenu.D_PROGRESS);
		int fill = Math.max(1, rules.data(StationMenu.D_FILL));
		int filled = Math.min(BAR_WIDTH, BAR_WIDTH * progress / fill);
		graphics.fill(bx - 1, by - 1, bx + BAR_WIDTH + 1, by + BAR_HEIGHT + 1, PANEL_BORDER);
		graphics.fill(bx, by, bx + BAR_WIDTH, by + BAR_HEIGHT, BAR_BACK);
		if (filled > 0) graphics.fill(bx, by, bx + filled, by + BAR_HEIGHT, progress >= fill ? BAR_FULL : BAR_FILL);
		if (mouseX >= bx - 1 && mouseX <= bx + BAR_WIDTH && mouseY >= by - 2 && mouseY <= by + BAR_HEIGHT + 1) {
			// Worker counts stay hidden on purpose: players learn the villages by looking at the villagers.
			String tip = "Progress " + progress + " / " + fill + (progress >= fill ? " · ready to upgrade" : "");
			graphics.setTooltipForNextFrame(font, Component.literal(tip), mouseX, mouseY);
		}

		// The milestone item as a faded "ghost" until the player puts one in.
		Slot milestoneSlot = slots[StationMenu.MILESTONE];
		Item milestone = rules.milestoneItem();
		if (milestone != null && !milestoneSlot.hasItem()) {
			ghost(graphics, new ItemStack(milestone), slotLeft + milestoneSlot.x, slotTop + milestoneSlot.y);
		}
	}

	/** Shows the Upgrade and Reroll buttons as the station's state allows. */
	public static void updateButtons(StationRules rules, LocalPlayer player, Button upgrade, Button reroll, int[] tooltipCost) {
		if (upgrade != null) {
			upgrade.visible = !rules.isMaxLevel();
			upgrade.active = rules.canUpgrade();
		}
		if (reroll != null) {
			int emeralds = player == null ? 0 : player.getInventory().countItem(Items.EMERALD);
			boolean free = player != null && player.hasInfiniteMaterials();
			int cost = rules.rerollCost();
			reroll.visible = cost >= 0;
			reroll.active = free || emeralds >= cost;
			if (cost != tooltipCost[0]) {
				tooltipCost[0] = cost;
				reroll.setTooltip(Tooltip.create(Component.literal(cost + (cost == 1 ? " emerald" : " emeralds"))));
			}
		}
	}

	static void ghost(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
		graphics.fakeItem(stack, x, y);
		graphics.fill(x, y, x + 16, y + 16, GHOST_OVERLAY);
	}

	static void slotBackground(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, CHEST, x - 1, y - 1, 7.0F, 17.0F, 18, 18, 256, 256);
	}

	/**
	 * Today's request as an item with its count. Faded like the milestone ghost once fully donated
	 * (it comes back if items are taken out). A barrier while nothing is asked: after the evening work, until sunrise.
	 */
	private static void drawRequest(GuiGraphicsExtractor graphics, Font font, StationRules rules, int x, int y) {
		slotBackground(graphics, x, y);
		Item item = rules.requestedItem();
		int count = rules.data(StationMenu.D_REQUEST_COUNT);
		if (item == null || count <= 0) {
			ghost(graphics, new ItemStack(Items.BARRIER), x, y);
			return;
		}
		graphics.fakeItem(new ItemStack(item), x, y);
		String n = String.valueOf(count);
		graphics.text(font, Component.literal(n), x + 17 - font.width(n), y + 9, 0xFFFFFFFF, true);
		if (rules.remaining() == 0) graphics.fill(x, y, x + 16, y + 16, GHOST_OVERLAY);
	}

	private static void label(GuiGraphicsExtractor graphics, Font font, String text, int px, int y) {
		graphics.text(font, Component.literal(text), px + LABEL_X, y, LABEL, false);
	}

	static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			default -> String.valueOf(n);
		};
	}
}
