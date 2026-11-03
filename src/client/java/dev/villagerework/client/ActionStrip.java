package dev.villagerework.client;

import dev.villagerework.ui.ActionRow;
import dev.villagerework.ui.StationActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * The perk action strip under a vanilla screen (see ActionRow): [input] ([extra]) [Button] [output], with faded
 * ghosts showing what goes in. Drawn by AbstractContainerScreenMixin; only while the block has the perk.
 */
public final class ActionStrip {
	private static final int BORDER = 0xFF373737, LIGHT = 0xFFFFFFFF, SHADE = 0xFF555555, FILL = 0xFFC6C6C6;

	private final ActionRow row;
	private final Button button;

	public ActionStrip(ActionRow row, int containerId, Consumer<Button> addWidget) {
		this.row = row;
		button = Button.builder(Component.empty(), b -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(containerId, ActionRow.BUTTON_ID);
		}).bounds(0, 0, 60, ActionRow.BUTTON_HEIGHT).build();
		button.visible = false;
		addWidget.accept(button);
	}

	public boolean owns(Object widget) {
		return widget == button;
	}

	public boolean contains(double x, double y, int leftPos, int topPos) {
		if (!row.shown()) return false;
		int sx = leftPos + ActionRow.STRIP_X, sy = topPos + ActionRow.STRIP_Y;
		return x >= sx && x < sx + ActionRow.STRIP_WIDTH && y >= sy && y < sy + ActionRow.STRIP_HEIGHT;
	}

	public void draw(GuiGraphicsExtractor graphics, int leftPos, int topPos) {
		int action = row.action();
		button.visible = row.shown();
		if (!button.visible) return;
		int x = leftPos + ActionRow.STRIP_X, y = topPos + ActionRow.STRIP_Y;
		int w = ActionRow.STRIP_WIDTH, h = ActionRow.STRIP_HEIGHT;
		graphics.fill(x, y, x + w, y + h, BORDER);
		graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, LIGHT);
		graphics.fill(x + 2, y + 2, x + w - 1, y + h - 1, SHADE);
		graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, FILL);

		Item extra = StationActions.extra(action);
		int buttonX = (extra != null ? ActionRow.EXTRA_X : ActionRow.INPUT_X) + 20;
		button.setPosition(leftPos + buttonX, topPos + ActionRow.BUTTON_Y);
		button.setWidth(ActionRow.BUTTON_RIGHT - buttonX);
		button.setMessage(Component.literal(StationActions.label(action)));
		Minecraft minecraft = Minecraft.getInstance();
		button.active = minecraft.player != null && StationActions.count(action, minecraft.player, row.io()) > 0;

		slot(graphics, row.slot(0), StationActions.input(action), leftPos, topPos);
		if (extra != null) slot(graphics, row.slot(1), extra, leftPos, topPos);
		slot(graphics, row.slot(2), null, leftPos, topPos);
	}

	private static void slot(GuiGraphicsExtractor graphics, Slot slot, Item ghost, int leftPos, int topPos) {
		int sx = leftPos + slot.x, sy = topPos + slot.y;
		StationPanelRenderer.slotBackground(graphics, sx, sy);
		if (ghost != null && !slot.hasItem()) StationPanelRenderer.ghost(graphics, new ItemStack(ghost), sx, sy);
	}
}
