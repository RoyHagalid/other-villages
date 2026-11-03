package dev.villagerework.client;

import dev.villagerework.net.StoryPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * A signed book scrolling up the screen like the end credits (librarian's lectern, level III).
 * Esc skips it; holding space or a mouse button scrolls faster. It closes by itself after the last line.
 */
public final class StoryScreen extends Screen {
	private static final float SPEED = 0.5F;      // pixels per tick
	private static final float FAST = 4.0F;
	private static final int LINE = 12;

	private final StoryPayload story;
	private final List<FormattedCharSequence> lines = new ArrayList<>();
	private final List<Integer> colors = new ArrayList<>();
	private float scroll;
	private float lastTickScroll;
	private boolean mouseHeld;

	public StoryScreen(StoryPayload story) {
		super(Component.literal(story.title()));
		this.story = story;
	}

	@Override
	protected void init() {
		lines.clear();
		colors.clear();
		int wrap = Math.min(256, width - 40);
		add(Component.literal(story.title()).withStyle(ChatFormatting.BOLD), wrap, 0xFFFFD700);
		add(Component.literal("by " + story.author()), wrap, 0xFFAAAAAA);
		blank();
		blank();
		for (Component page : story.pages()) {
			for (String paragraph : page.getString().split("\n", -1)) {
				if (paragraph.isBlank()) blank();
				else add(Component.literal(paragraph), wrap, 0xFFFFFFFF);
			}
			blank();
		}
	}

	private void add(Component text, int wrap, int color) {
		for (FormattedCharSequence line : font.split(text, wrap)) {
			lines.add(line);
			colors.add(color);
		}
	}

	private void blank() {
		lines.add(FormattedCharSequence.EMPTY);
		colors.add(0);
	}

	@Override
	public void tick() {
		lastTickScroll = scroll;
		// Space is read directly (key events for it don't reliably reach this screen in 26.x).
		boolean fast = mouseHeld || InputConstants.isKeyDown(InputConstants.KEY_SPACE);
		scroll += fast ? FAST : SPEED;
		if (scroll > height + lines.size() * LINE + 20) onClose();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xFF000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		float offset = lastTickScroll + (scroll - lastTickScroll) * delta;
		int y = (int) (height - offset);
		for (int i = 0; i < lines.size(); i++, y += LINE) {
			if (y < -LINE || y > height) continue;
			FormattedCharSequence line = lines.get(i);
			graphics.text(font, line, (width - font.width(line)) / 2, y, colors.get(i), true);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		mouseHeld = true;
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		mouseHeld = false;
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
