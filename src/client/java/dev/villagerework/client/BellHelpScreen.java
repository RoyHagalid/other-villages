package dev.villagerework.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The bell screen's "?" window (0.17.11): how villages work. Back (or Esc) returns to the bell.
 *
 * 0.20.0: gaps between paragraphs, scrolls (mouse wheel or dragging the bar) when taller than the window.
 * 0.20.2: Roy's text, and a list of the station perks with each station's block as an icon.
 *
 * To change the text, edit {@link #sections()} (heading + paragraph) and {@link #PERKS} (block, level II, level III).
 */
public final class BellHelpScreen extends Screen {
	private static final int WIDTH = 248;
	private static final int HEADING = 0xFF404040;
	private static final int BODY = 0xFF606060;
	private static final int HEADING_H = 11;
	private static final int LINE_H = 9;
	private static final int PARAGRAPH_GAP = 8;
	private static final int TEXT_TOP = 22;
	private static final int FOOTER = 30;
	private static final int SCROLL_W = 4;
	/** Perk rows: the icon sits left, the text is indented past it. */
	private static final int ICON_INDENT = 20;
	private static final int PERK_GAP = 4;

	/** One station perk row: its block, what level II adds, what level III adds. */
	private record Perk(Item block, String name, String two, String three) {
	}

	private static final List<Perk> PERKS = List.of(
			new Perk(Items.COMPOSTER, "Composter", "Composts more efficiently.",
					"More items can be composted, even more efficiently."),
			new Perk(Items.SMOKER, "Smoker", "Fuel lasts longer.", "Cooks faster."),
			new Perk(Items.BLAST_FURNACE, "Blast furnace", "Fuel lasts longer.",
					"Smelting iron and gold tools and armor gives back ingots."),
			new Perk(Items.BREWING_STAND, "Brewing stand", "Brews faster.", "Lets you bottle your XP."),
			new Perk(Items.CAULDRON, "Cauldron", "Turns dirt into mud.", "Turns concrete powder into concrete."),
			new Perk(Items.BARREL, "Barrel", "More storage.", "Even more storage."),
			new Perk(Items.STONECUTTER, "Stonecutter", "Cuts wood.", "Cuts stairs back into blocks."),
			new Perk(Items.SMITHING_TABLE, "Smithing table", "Removes a trim.",
					"Changes a trim's material without a new template."),
			new Perk(Items.GRINDSTONE, "Grindstone", "Mills cobblestone, gravel, sand and bones.",
					"Hones tools and weapons with flint."),
			new Perk(Items.LOOM, "Loom", "8 banner layers.", "12 banner layers."),
			new Perk(Items.CARTOGRAPHY_TABLE, "Cartography table", "One empty map makes two copies.",
					"Makes Cave Maps."),
			new Perk(Items.FLETCHING_TABLE, "Fletching table", "Makes arrows more efficiently.",
					"Tips arrows with lingering potions and makes explosive arrows."),
			new Perk(Items.LECTERN, "Lectern", "A signed book and a redstone signal show its title on screen.",
					"The book's text plays as a scrolling story on screen."));

	private final Screen parent;
	private final int emeraldCap;

	/** One laid-out line: its text, color (0 = gap), height, x indent, and an icon drawn at its left (or null). */
	private record Line(FormattedCharSequence text, int color, int height, int indent, ItemStack icon) {
	}

	private final List<Line> lines = new ArrayList<>();
	private int left;
	private int top;
	private int panelHeight;
	private int textHeight;
	private int viewHeight;
	private double scroll;
	private boolean dragging;

	BellHelpScreen(Screen parent, int emeraldCap) {
		super(Component.literal("How villages work"));
		this.parent = parent;
		this.emeraldCap = emeraldCap;
	}

	/** Heading and paragraph; the perk list follows the section whose heading is "Helping out". */
	private String[][] sections() {
		return new String[][] {
				{"The bell", "The bell is the heart of the village. Here you can find information about the village "
						+ "and its stations."},
				{"Active stations", "Donate a workstation by shift-clicking it with an empty hand inside the village "
						+ "border to make it an active station. Villagers without a job look for active stations to "
						+ "work at, up to three villagers per station.\n\nA village can only have one active station of "
						+ "each kind at a time. Villagers who lose their station look for a new job the next day, "
						+ "unless they get a new active station for their profession."},
				{"Needs and rewards", "Every morning each active station shows what it needs that day. Supply it by "
						+ "hand or with hoppers. If the station has workers, they use it and leave rewards at the end "
						+ "of the day, which a hopper underneath can collect.\n\nAn active station without workers "
						+ "doesn't use anything or give any rewards."},
				{"Helping out", "If you keep helping the villagers, their stations level up and give better rewards. "
						+ "Drop emeralds near villagers to boost their work that day, up to " + emeraldCap
						+ " emeralds a day. A happy village, higher station levels and safety also boost "
						+ "production.\n\nAt levels II and III, stations also give extra perks:"},
				{"Your own stations", "A station in a village gives these perks, but you can also buy your own "
						+ "version from a villager who works at an upgraded station. A village with more renown sells "
						+ "them for less.\n\nKeep helping them out and build a thriving community!"},
		};
	}

	@Override
	protected void init() {
		lines.clear();
		int textWidth = WIDTH - 16 - SCROLL_W - 2;
		String[][] sections = sections();
		for (int s = 0; s < sections.length; s++) {
			lines.add(new Line(Component.literal(sections[s][0]).getVisualOrderText(), HEADING, HEADING_H, 0, null));
			String[] paragraphs = sections[s][1].split("\n\n");
			for (int p = 0; p < paragraphs.length; p++) {
				if (p > 0) gap(PARAGRAPH_GAP / 2);
				for (FormattedCharSequence line : font.split(Component.literal(paragraphs[p]), textWidth)) {
					lines.add(new Line(line, BODY, LINE_H, 0, null));
				}
			}
			if (sections[s][0].equals("Helping out")) addPerks(textWidth);
			if (s < sections.length - 1) gap(PARAGRAPH_GAP);
		}
		textHeight = 0;
		for (Line line : lines) textHeight += line.height();
		int maxPanel = Math.max(120, height - 8);
		panelHeight = Math.min(TEXT_TOP + textHeight + 4 + FOOTER, maxPanel);
		viewHeight = panelHeight - TEXT_TOP - FOOTER;
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
		left = (width - WIDTH) / 2;
		top = Math.max(4, (height - panelHeight) / 2);
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
				.bounds(left + (WIDTH - 80) / 2, top + panelHeight - 26, 80, 20).build());
	}

	/** Each perk: the block icon, its name, then "II" and "III" lines indented past the icon. */
	private void addPerks(int textWidth) {
		int width = textWidth - ICON_INDENT;
		for (Perk perk : PERKS) {
			gap(PERK_GAP);
			lines.add(new Line(Component.literal(perk.name()).getVisualOrderText(), HEADING, HEADING_H, ICON_INDENT,
					new ItemStack(perk.block())));
			addLevel("II", perk.two(), width);
			addLevel("III", perk.three(), width);
		}
	}

	private void addLevel(String level, String text, int width) {
		for (FormattedCharSequence line : font.split(Component.literal(level + "  " + text), width)) {
			lines.add(new Line(line, BODY, LINE_H, ICON_INDENT, null));
		}
	}

	private void gap(int height) {
		lines.add(new Line(FormattedCharSequence.EMPTY, 0, height, 0, null));
	}

	private int maxScroll() {
		return Math.max(0, textHeight + 4 - viewHeight);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		BellScreen.panel(graphics, left, top, WIDTH, panelHeight);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(font, title, left + 8, top + 7, HEADING, false);
		int viewTop = top + TEXT_TOP;
		graphics.enableScissor(left + 4, viewTop, left + WIDTH - 4, viewTop + viewHeight);
		int y = viewTop - (int) scroll;
		for (Line line : lines) {
			if (line.color() != 0 && y + 18 > viewTop && y < viewTop + viewHeight) {
				// The icon covers the name and the first level line (16 px), the name at its middle.
				if (line.icon() != null) graphics.fakeItem(line.icon(), left + 8, y - 1);
				graphics.text(font, line.text(), left + 8 + line.indent(), y, line.color(), false);
			}
			y += line.height();
		}
		graphics.disableScissor();

		// The scroll bar, only when the text doesn't fit.
		if (maxScroll() > 0) {
			int x = left + WIDTH - 8 - SCROLL_W;
			graphics.fill(x, viewTop, x + SCROLL_W, viewTop + viewHeight, 0xFF8B8B8B);
			int thumb = Math.max(12, viewHeight * viewHeight / (textHeight + 4));
			int thumbY = viewTop + (int) ((viewHeight - thumb) * (scroll / maxScroll()));
			graphics.fill(x, thumbY, x + SCROLL_W, thumbY + thumb, 0xFF404040);
		}
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (maxScroll() > 0) {
			scroll = Math.max(0, Math.min(maxScroll(), scroll - scrollY * 18));
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		int x = left + WIDTH - 8 - SCROLL_W;
		int viewTop = top + TEXT_TOP;
		if (maxScroll() > 0 && event.x() >= x - 2 && event.x() <= x + SCROLL_W + 2
				&& event.y() >= viewTop && event.y() <= viewTop + viewHeight) {
			dragging = true;
			scrollTo(event.y());
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			scrollTo(event.y());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	private void scrollTo(double mouseY) {
		int viewTop = top + TEXT_TOP;
		double f = (mouseY - viewTop) / Math.max(1, viewHeight);
		scroll = Math.max(0, Math.min(maxScroll(), f * maxScroll()));
	}

	/** Escape and Back both return to the bell screen. */
	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
