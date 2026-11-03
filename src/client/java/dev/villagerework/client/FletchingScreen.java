package dev.villagerework.client;

import dev.villagerework.ui.FletchingMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The fletcher's fletching table screen: three inputs (shaft, head, fletching), an arrow, the result,
 * and the player inventory. Drawn from vanilla pieces (the chest frame and slots, the furnace arrow),
 * so it needs no textures of its own.
 */
public final class FletchingScreen extends AbstractContainerScreen<FletchingMenu> {
	private static final Identifier CHEST = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
	private static final Identifier ARROW = Identifier.withDefaultNamespace("container/furnace/burn_progress");
	private static final int FILL = 0xFFC6C6C6;
	private static final int TOP = 3 * 18 + 17;

	public FletchingScreen(FletchingMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, TOP + 96);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		int x = leftPos;
		int y = topPos;
		graphics.blit(RenderPipelines.GUI_TEXTURED, CHEST, x, y, 0.0F, 0.0F, 176, TOP, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, CHEST, x, y + TOP, 0.0F, 126.0F, 176, 96, 256, 256);
		graphics.fill(x + 7, y + 17, x + 169, y + TOP, FILL); // hide the chest's slot rows
		slot(graphics, FletchingMenu.SHAFT_X, FletchingMenu.INPUT_Y);
		slot(graphics, FletchingMenu.HEAD_X, FletchingMenu.INPUT_Y);
		slot(graphics, FletchingMenu.FLETCHING_X, FletchingMenu.INPUT_Y);
		slot(graphics, FletchingMenu.RESULT_X, FletchingMenu.INPUT_Y);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW, x + FletchingMenu.ARROW_X, y + FletchingMenu.INPUT_Y, 24, 16);
	}

	private void slot(GuiGraphicsExtractor graphics, int slotX, int slotY) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, CHEST, leftPos + slotX - 1, topPos + slotY - 1, 7.0F, 17.0F, 18, 18, 256, 256);
	}
}
