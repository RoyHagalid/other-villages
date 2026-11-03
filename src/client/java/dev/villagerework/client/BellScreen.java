package dev.villagerework.client;

import dev.villagerework.VillageRework;
import dev.villagerework.net.BellScreenPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The bell screen (0.17.11): the village at a glance. Its content comes from the server (BellScreenPayload); this
 * only lays it out like an inventory screen: title, a grey subtitle, text lines, the station grid, and buttons.
 * A "?" tab on the right opens BellHelpScreen.
 */
public final class BellScreen extends Screen {
	static final int WIDTH = 176;
	static final int TEXT_WIDTH = WIDTH - 16;
	static final int LABEL = 0xFF404040;
	static final int SUBTITLE = 0xFF707070;
	private static final int SLOT = 18;
	private static final int PER_ROW = 7;
	/** The tabs on the left (0.18.2): "?", the mood face, the party switch, stacked like a vertical menu. */
	private static final int TAB_W = 26;      // 22 showing, 4 tucked behind the panel
	private static final int TAB_SHOW = 22;
	private static final int TAB_H = 24;
	private static final int TAB_Y = 8;
	private static final int TAB_STEP = 26;
	private int helpTabY;
	private int moodTabY = -1;
	private int partyTabY = -1;

	private final BellScreenPayload view;
	private final List<FormattedCharSequence> lines = new ArrayList<>();
	private final List<Integer> lineGaps = new ArrayList<>();
	private int left;
	private int top;
	private int panelHeight;
	private int linesY;
	private int gridY;
	/** Where the renown label sits (0.20.0); -1 without one. */
	private int renownY = -1;
	private static final int BAR_W = WIDTH - 16;
	private static final int BAR_H = 5;

	public BellScreen(BellScreenPayload view) {
		super(Component.literal(view.title()));
		this.view = view;
	}

	@Override
	protected void init() {
		// Wrap the text lines; a small gap separates one line of the server's from the next.
		lines.clear();
		lineGaps.clear();
		for (Component line : view.lines()) {
			List<FormattedCharSequence> wrapped = font.split(line, TEXT_WIDTH);
			for (int i = 0; i < wrapped.size(); i++) {
				lines.add(wrapped.get(i));
				lineGaps.add(i == wrapped.size() - 1 ? 1 : 0);
			}
		}
		int y = view.subtitle().isEmpty() ? 22 : 34;
		linesY = y;
		for (int gap : lineGaps) y += 10 + gap;
		renownY = -1;
		if (view.renownRank() >= 0) {
			y += 3;
			renownY = y;
			y += 10 + BAR_H + 4;
		}
		if (!view.stations().isEmpty()) {
			y += 5;
			gridY = y + 11;
			int rows = (view.stations().size() + PER_ROW - 1) / PER_ROW;
			y = gridY + rows * SLOT + 7;
		} else {
			y += 6;
		}
		List<BellScreenPayload.Action> actions = new ArrayList<>(view.buttons());
		int rows = (actions.size() + 1 + 1) / 2;    // + Close
		panelHeight = y + rows * 22 + 4;
		left = (width - WIDTH) / 2;
		top = Math.max(4, (height - panelHeight) / 2);

		// The tabs on the left, top to bottom: "?", the mood, the party switch (each only when it applies).
		int tabY = top + TAB_Y;
		helpTabY = tabY;
		tabY += TAB_STEP;
		if (view.mood() >= 0) {
			moodTabY = tabY;
			tabY += TAB_STEP;
		}
		if (view.party() >= 0) partyTabY = tabY;
		// Real widgets, so clicks take the same path as every other button.
		addRenderableWidget(new HelpTab(left - TAB_SHOW, helpTabY, TAB_SHOW, TAB_H,
				() -> minecraft.gui.setScreen(new BellHelpScreen(this, view.emeraldCap()))));
		// The party switch flips at once; the server is told quietly.
		if (partyTabY >= 0) {
			addRenderableWidget(new PartySwitch(left - TAB_SHOW, partyTabY, TAB_SHOW, TAB_H, view.party() == 1, allowed -> {
				if (minecraft.player == null) return;
				var bell = view.bell();
				minecraft.player.connection.sendCommand("village party " + bell.getX() + " " + bell.getY() + " " + bell.getZ()
						+ (allowed ? " on" : " off"));
			}));
		}

		// Buttons two to a row; an odd last one gets the full width.
		int count = actions.size() + 1;
		for (int i = 0; i < count; i++) {
			int row = i / 2, col = i % 2;
			boolean alone = i == count - 1 && count % 2 == 1;
			int bx = left + 7 + (alone ? 0 : col * 83);
			int by = top + y + row * 22;
			int bw = alone ? WIDTH - 14 : 79;
			if (i < actions.size()) {
				BellScreenPayload.Action a = actions.get(i);
				Component label = a.danger()
						? Component.literal(a.label()).withStyle(ChatFormatting.RED)
						: Component.literal(a.label());
				addRenderableWidget(Button.builder(label, b -> run(a.command()))
						.bounds(bx, by, bw, 20)
						.tooltip(a.tooltip().isEmpty() ? null : Tooltip.create(Component.literal(a.tooltip())))
						.build());
			} else {
				addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(bx, by, bw, 20).build());
			}
		}
	}

	/** Runs a /village command (the server re-checks it) and closes; the server opens what comes next, if anything. */
	private void run(String command) {
		if (minecraft.player != null) {
			minecraft.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
		}
		onClose();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		// The tabs sit behind the panel's left edge, like creative inventory tabs.
		for (int ty : new int[] {helpTabY, moodTabY, partyTabY}) {
			if (ty >= 0) panel(graphics, left - TAB_SHOW, ty, TAB_W, TAB_H);
		}
		panel(graphics, left, top, WIDTH, panelHeight);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		// A name too long for the panel is cut short with "...".
		String title = view.title();
		int room = WIDTH - 16;
		if (font.width(title) > room) title = font.plainSubstrByWidth(title, room - font.width("...")) + "...";
		graphics.text(font, Component.literal(title), left + 8, top + 7, view.titleColor(), false);
		if (!view.subtitle().isEmpty()) {
			graphics.text(font, Component.literal(view.subtitle()), left + 8, top + 19, SUBTITLE, false);
		}
		int y = top + linesY;
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(font, lines.get(i), left + 8, y, LABEL, false);
			y += 10 + lineGaps.get(i);
		}
		if (renownY >= 0) drawRenown(graphics, mouseX, mouseY);
		if (!view.stations().isEmpty()) drawStations(graphics, mouseX, mouseY);
		if (view.mood() >= 0) drawMood(graphics, mouseX, mouseY);
	}

	/**
	 * Renown (0.20.0): the rank and a bar toward the next one, drawn like a station's progress bar. No numbers;
	 * hovering says what fills it and what it does.
	 */
	private void drawRenown(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = left + 8, y = top + renownY;
		graphics.text(font, Component.literal("Renown"), x, y, LABEL, false);
		if (view.renownRank() > 0) {
			String rank = roman(view.renownRank());
			graphics.text(font, Component.literal(rank), x + BAR_W - font.width(rank), y, LABEL, false);
		}
		int by = y + 10;
		int filled = BAR_W * Math.max(0, Math.min(1000, view.renownFill())) / 1000;
		graphics.fill(x - 1, by - 1, x + BAR_W + 1, by + BAR_H + 1, 0xFF8B8B8B);
		graphics.fill(x, by, x + BAR_W, by + BAR_H, 0xFF1E1E1E);
		if (filled > 0) graphics.fill(x, by, x + filled, by + BAR_H, 0xFFF0C030);
		if (mouseX >= x - 1 && mouseX <= x + BAR_W && mouseY >= y && mouseY <= by + BAR_H + 1) {
			List<Component> tip = List.of(
					Component.literal("Renown" + (view.renownRank() > 0 ? " " + roman(view.renownRank()) : "")),
					Component.literal("Grows each day a level III station gets what it needs.").withStyle(ChatFormatting.GRAY),
					Component.literal("The more renown, the cheaper the stations workers sell.").withStyle(ChatFormatting.GRAY));
			graphics.setTooltipForNextFrame(font, tip, Optional.empty(), mouseX, mouseY);
		}
	}

	private static final String[] MOOD_NAMES = {"Miserable", "Unhappy", "Neutral", "Content", "Abundant"};
	private static final String[] MOOD_FILES = {"miserable", "unhappy", "neutral", "content", "abundant"};

	/** The village's face on its tab (0.18); hovering the tab tells the mood and the latest gossip. */
	private void drawMood(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int mood = Math.max(0, Math.min(4, view.mood()));
		int x = left - TAB_SHOW + (TAB_SHOW - 16) / 2 + 1, y = moodTabY + (TAB_H - 16) / 2;
		graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
				VillageRework.id("textures/gui/mood/" + MOOD_FILES[mood] + ".png"), x, y, 0.0F, 0.0F, 16, 16, 16, 16);
		if (mouseX >= left - TAB_SHOW && mouseX < left && mouseY >= moodTabY && mouseY < moodTabY + TAB_H) {
			List<Component> tip = new ArrayList<>();
			tip.add(Component.literal(MOOD_NAMES[mood]));
			if (view.gossip().isEmpty()) {
				tip.add(Component.literal("Nothing much has happened lately.").withStyle(ChatFormatting.GRAY));
			} else {
				for (Component line : view.gossip()) tip.add(Component.empty().append(line).withStyle(ChatFormatting.GRAY));
			}
			graphics.setTooltipForNextFrame(font, tip, Optional.empty(), mouseX, mouseY);
		}
	}

	/** Seven to a row, a shorter last row centered. Missing stations are greyed out; hover tells name and level. */
	private void drawStations(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, Component.literal("Stations"), left + 8, top + gridY - 11, LABEL, false);
		List<BellScreenPayload.Station> stations = view.stations();
		int rows = (stations.size() + PER_ROW - 1) / PER_ROW;
		for (int i = 0; i < stations.size(); i++) {
			int row = i / PER_ROW, col = i % PER_ROW;
			int inRow = row == rows - 1 ? stations.size() - row * PER_ROW : PER_ROW;
			int x = left + (WIDTH - inRow * SLOT) / 2 + col * SLOT;
			int y = top + gridY + row * SLOT;
			StationPanelRenderer.slotBackground(graphics, x + 1, y + 1);
			BellScreenPayload.Station s = stations.get(i);
			ItemStack stack = icon(s);
			boolean active = s.state() == BellScreenPayload.Station.ACTIVE;
			if (active) graphics.fakeItem(stack, x + 1, y + 1);
			else StationPanelRenderer.ghost(graphics, stack, x + 1, y + 1);
			if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) {
				graphics.fill(x + 1, y + 1, x + 17, y + 17, 0x80FFFFFF);
				graphics.setTooltipForNextFrame(font, tooltip(s, stack), Optional.empty(), mouseX, mouseY);
			}
		}
	}

	/** The station's block, with the tier marks of its level (the same look as the placed block). */
	private static ItemStack icon(BellScreenPayload.Station s) {
		ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(s.item())));
		if (s.state() == BellScreenPayload.Station.ACTIVE && s.level() >= 2) {
			stack.set(DataComponents.ITEM_MODEL, VillageRework.id("take_home/"
					+ Identifier.parse(s.item()).getPath() + "_" + Math.min(3, s.level())));
		}
		return stack;
	}

	private static List<Component> tooltip(BellScreenPayload.Station s, ItemStack stack) {
		Component name = Component.literal(s.name());
		Component block = stack.getHoverName();
		return switch (s.state()) {
			case BellScreenPayload.Station.ACTIVE -> List.of(name,
					Component.empty().append(block).append(", level " + roman(s.level())).withStyle(ChatFormatting.GRAY));
			case BellScreenPayload.Station.NEEDS_BLOCK -> List.of(name,
					Component.literal("Level " + roman(s.level()) + ", needs a new ").withStyle(ChatFormatting.GRAY)
							.append(Component.empty().append(block).withStyle(ChatFormatting.GRAY)));
			default -> List.of(name, Component.literal("No station yet").withStyle(ChatFormatting.GRAY));
		};
	}

	static String roman(int n) {
		if (n <= 0 || n >= 4000) return String.valueOf(n);
		int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
		String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			while (n >= values[i]) {
				sb.append(symbols[i]);
				n -= values[i];
			}
		}
		return sb.toString();
	}

	/** The party switch: fireworks and music at abundance allowed or not. Its picture shows which. */
	private static final class PartySwitch extends net.minecraft.client.gui.components.AbstractButton {
		private static final Identifier ON = VillageRework.id("textures/gui/party/party.png");
		private static final Identifier OFF = VillageRework.id("textures/gui/party/party_off.png");
		private final java.util.function.Consumer<Boolean> changed;
		private boolean allowed;

		PartySwitch(int x, int y, int w, int h, boolean allowed, java.util.function.Consumer<Boolean> changed) {
			super(x, y, w, h, Component.literal("Party"));
			this.allowed = allowed;
			this.changed = changed;
			updateTooltip();
		}

		private void updateTooltip() {
			setTooltip(Tooltip.create(Component.literal(allowed ? "Party allowed" : "No party allowed")));
		}

		@Override
		public void onPress(net.minecraft.client.input.InputWithModifiers input) {
			allowed = !allowed;
			updateTooltip();
			changed.accept(allowed);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
			// Just the picture: party.png or party_off.png (already greyed with the red sign), no extra overlay.
			graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, allowed ? ON : OFF,
					getX() + (getWidth() - 16) / 2 + 1, getY() + (getHeight() - 16) / 2, 0.0F, 0.0F, 16, 16, 16, 16);
		}

		@Override
		protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

	/** The "?" tab's clickable part: draws the "?" (highlighted on hover); the tab's shape is drawn behind the panel. */
	private static final class HelpTab extends net.minecraft.client.gui.components.AbstractButton {
		private final Runnable open;

		HelpTab(int x, int y, int w, int h, Runnable open) {
			super(x, y, w, h, Component.literal("How villages work"));
			this.open = open;
			setTooltip(Tooltip.create(Component.literal("How villages work")));
		}

		@Override
		public void onPress(net.minecraft.client.input.InputWithModifiers input) {
			open.run();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
			var font = net.minecraft.client.Minecraft.getInstance().font;
			boolean hover = isHoveredOrFocused();
			graphics.text(font, Component.literal("?"), getX() + (getWidth() - font.width("?")) / 2, getY() + 8,
					hover ? 0xFFFFFFA0 : LABEL, hover);
		}

		@Override
		protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

	/** An inventory-style panel: dark outline, white top-left edge, grey bottom-right edge, light grey inside. */
	static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		graphics.fill(x + 1, y, x + w - 1, y + h, 0xFF000000);
		graphics.fill(x, y + 1, x + w, y + h - 1, 0xFF000000);
		graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFFFFFFF);
		graphics.fill(x + 3, y + 3, x + w - 1, y + h - 1, 0xFF555555);
		graphics.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFFC6C6C6);
		graphics.fill(x + w - 3, y + 1, x + w - 1, y + 3, 0xFFC6C6C6);
		graphics.fill(x + 1, y + h - 3, x + 3, y + h - 1, 0xFFC6C6C6);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
