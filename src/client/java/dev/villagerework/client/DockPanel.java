package dev.villagerework.client;

import dev.villagerework.ui.DockedStation;
import dev.villagerework.ui.StationMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Consumer;

/**
 * The village station panel docked to the right of a vanilla screen (see DockedStation). Drawn and placed by
 * AbstractContainerScreenMixin, which also moves the whole screen left when the panel would go past the window's
 * right edge. Next to an open recipe book there's no room to move, so the panel folds into a bell tab at the
 * screen's top-right corner; clicking the tab closes the recipe book and the panel comes back.
 */
public final class DockPanel {
	public static final int TAB_WIDTH = 22, TAB_HEIGHT = 22, TAB_Y = 4;

	private final DockedStation dock;
	private final int containerId;
	private final Button upgradeButton;
	private final Button rerollButton;
	private final Button tabButton;
	private final int[] rerollTooltipCost = {Integer.MIN_VALUE};

	public DockPanel(DockedStation dock, int containerId, Consumer<Button> addWidget, Runnable onTab) {
		this.dock = dock;
		this.containerId = containerId;
		upgradeButton = Button.builder(Component.literal("Upgrade"), b -> press(StationMenu.UPGRADE_BUTTON))
				.bounds(0, 0, 70, 20).build();
		int rerollX = StationMenu.REWARD_X[0] - 1;
		int rerollWidth = StationMenu.REWARD_X[StationMenu.REWARD_X.length - 1] + 17 - rerollX;
		rerollButton = Button.builder(Component.literal("Reroll"), b -> press(StationMenu.REROLL_BUTTON))
				.bounds(0, 0, rerollWidth, StationMenu.REROLL_HEIGHT).build();
		tabButton = Button.builder(Component.empty(), b -> onTab.run())
				.bounds(0, 0, TAB_WIDTH, TAB_HEIGHT).build();
		for (Button b : new Button[]{upgradeButton, rerollButton, tabButton}) {
			b.visible = false;
			addWidget.accept(b);
		}
	}

	private void press(int button) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(containerId, DockedStation.BUTTON_BASE + button);
		}
	}

	public boolean owns(Object widget) {
		return widget == upgradeButton || widget == rerollButton || widget == tabButton;
	}

	public boolean shown() {
		return dock.shown();
	}

	/** Width the panel needs to the right of the vanilla screen's left edge. */
	public static int reach() {
		return DockedStation.X + StationPanelRenderer.WIDTH + 2;
	}

	public void setCollapsed(boolean collapsed) {
		dock.setCollapsed(collapsed);
	}

	/** True if (x, y) is on the panel or its tab (so a click there doesn't count as outside the screen). */
	public boolean contains(double x, double y, int leftPos, int topPos, int imageWidth) {
		if (!dock.shown()) return false;
		if (!dock.visible()) {
			int tx = leftPos + imageWidth;
			return x >= tx && x < tx + TAB_WIDTH && y >= topPos + TAB_Y && y < topPos + TAB_Y + TAB_HEIGHT;
		}
		int px = leftPos + DockedStation.X;
		int py = topPos + DockedStation.Y;
		return x >= px && x < px + StationPanelRenderer.WIDTH && y >= py && y < py + StationPanelRenderer.HEIGHT;
	}

	/** Draws the panel behind the slots and places the buttons. Called every frame. */
	public void draw(GuiGraphicsExtractor graphics, Font font, int leftPos, int topPos, int imageWidth, int mouseX, int mouseY) {
		tabButton.visible = dock.shown() && !dock.visible();
		tabButton.setPosition(leftPos + imageWidth, topPos + TAB_Y);
		if (!dock.visible()) {
			upgradeButton.visible = rerollButton.visible = false;
			return;
		}
		int px = leftPos + DockedStation.X;
		int py = topPos + DockedStation.Y;
		Slot[] slots = new Slot[DockedStation.PANEL_SLOTS];
		for (int i = 0; i < slots.length; i++) slots[i] = dock.slot(i);
		StationPanelRenderer.draw(graphics, font, dock.rules(), slots, px, py, leftPos, topPos, mouseX, mouseY, dock.title());

		upgradeButton.setPosition(px + StationPanelRenderer.LABEL_X, py + StationMenu.UPGRADE_Y);
		rerollButton.setPosition(px + StationMenu.REWARD_X[0] - 1, py + StationMenu.REROLL_Y);
		StationPanelRenderer.updateButtons(dock.rules(), Minecraft.getInstance().player, upgradeButton, rerollButton,
				rerollTooltipCost);
	}

	/** The bell on the tab, drawn over the tab button. */
	public void drawTabIcon(GuiGraphicsExtractor graphics) {
		if (tabButton.visible) graphics.fakeItem(new ItemStack(Items.BELL), tabButton.getX() + 3, tabButton.getY() + 3);
	}

	/** Moves every widget of the screen except the panel's own by dx (used when the screen is moved left). */
	public void moveOthers(List<? extends Object> children, int dx) {
		for (Object child : children) {
			if (child instanceof AbstractWidget w && !owns(w)) w.setX(w.getX() + dx);
		}
	}
}
