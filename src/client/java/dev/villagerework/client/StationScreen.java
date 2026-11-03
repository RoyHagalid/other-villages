package dev.villagerework.client;

import dev.villagerework.ui.DockedStation;
import dev.villagerework.ui.StationMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The station screen for stations without a screen of their own (barrel, composter, cauldron, fletching table
 * at I, lectern): the vanilla chest look on the left (the block's storage rows, if any, and the player
 * inventory) and the village panel on the right (see StationPanelRenderer), where DockPanel docks it on
 * vanilla screens too.
 */
public final class StationScreen extends AbstractContainerScreen<StationMenu> {
	private Button upgradeButton;
	private Button rerollButton;
	private final int[] rerollTooltipCost = {Integer.MIN_VALUE};

	public StationScreen(StationMenu menu, Inventory inventory, Component title) {
		// In 26.x the screen size is passed to the constructor (imageWidth/imageHeight are final).
		super(menu, inventory, title, DockedStation.X + StationPanelRenderer.WIDTH, StationMenu.screenHeight(menu.storageRows()));
		this.titleLabelX = DockedStation.X + StationPanelRenderer.LABEL_X; // the station's name heads the panel
		this.titleLabelY = 7;
		this.inventoryLabelX = 8;
		// Just above the inventory slots, as on a chest (the screen can be taller than the vanilla part).
		this.inventoryLabelY = StationMenu.vanillaTop(menu.storageRows()) + menu.storageRows() * 18 + 20;
	}

	@Override
	protected void init() {
		super.init();
		upgradeButton = Button.builder(Component.literal("Upgrade"), button ->
						minecraft.gameMode.handleInventoryButtonClick(menu.containerId, StationMenu.UPGRADE_BUTTON))
				.bounds(leftPos + DockedStation.X + StationPanelRenderer.LABEL_X, topPos + StationMenu.UPGRADE_Y, 70, 20)
				.build();
		upgradeButton.active = false;
		addRenderableWidget(upgradeButton);

		int rerollX = StationMenu.REWARD_X[0] - 1;
		int rerollWidth = StationMenu.REWARD_X[StationMenu.REWARD_X.length - 1] + 17 - rerollX;
		rerollButton = Button.builder(Component.literal("Reroll"), button ->
						minecraft.gameMode.handleInventoryButtonClick(menu.containerId, StationMenu.REROLL_BUTTON))
				.bounds(leftPos + DockedStation.X + rerollX, topPos + StationMenu.REROLL_Y, rerollWidth, StationMenu.REROLL_HEIGHT)
				.build();
		rerollButton.visible = false;
		rerollTooltipCost[0] = Integer.MIN_VALUE;
		addRenderableWidget(rerollButton);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		drawVanillaPart(graphics);
		Slot[] panelSlots = new Slot[StationMenu.PANEL_SLOTS];
		for (int i = 0; i < panelSlots.length; i++) panelSlots[i] = menu.slots.get(i);
		StationPanelRenderer.draw(graphics, font, menu.rules(), panelSlots, leftPos + DockedStation.X, topPos, leftPos, topPos,
				mouseX, mouseY, null);
		StationPanelRenderer.updateButtons(menu.rules(), minecraft.player, upgradeButton, rerollButton, rerollTooltipCost);
	}

	private void drawVanillaPart(GuiGraphicsExtractor graphics) {
		int x = leftPos;
		int y = topPos + StationMenu.vanillaTop(menu.storageRows());
		int top = menu.storageRows() * 18 + 17;
		graphics.blit(RenderPipelines.GUI_TEXTURED, StationPanelRenderer.CHEST, x, y, 0.0F, 0.0F, 176, top, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, StationPanelRenderer.CHEST, x, y + top, 0.0F, 126.0F, 176, 96, 256, 256);
	}
}
