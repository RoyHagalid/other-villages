package dev.villagerework.ui;

import dev.villagerework.config.VRConfig;
import dev.villagerework.net.BellScreenPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * What one version of the bell screen shows (0.17.11). Sent to the mod's own bell screen; a client without the
 * mod gets the same content as a vanilla dialog instead.
 */
final class BellView {
	private final BlockPos bell;
	private final String title;
	private final ChatFormatting titleColor;
	private String subtitle = "";
	private final List<Component> lines = new ArrayList<>();
	private final List<BellScreenPayload.Station> stations = new ArrayList<>();
	private final List<BellScreenPayload.Action> buttons = new ArrayList<>();
	private int mood = -1;
	private List<Component> gossip = List.of();
	private int party = -1;
	private int renownRank = -1;
	private int renownFill;

	BellView(BlockPos bell, String title, ChatFormatting titleColor) {
		this.bell = bell;
		this.title = title;
		this.titleColor = titleColor;
	}

	/** The village's mood face and what the villagers talk about (0.18). */
	BellView mood(int mood, List<Component> gossip) {
		this.mood = mood;
		this.gossip = gossip;
		return this;
	}

	/** Shows the party switch (0.18.1). */
	BellView party(boolean allowed) {
		this.party = allowed ? 1 : 0;
		return this;
	}

	/** The renown bar (0.20.0): rank and how full the bar toward the next rank is (0..1). */
	BellView renown(int rank, double fill) {
		this.renownRank = rank;
		this.renownFill = (int) Math.round(Math.max(0, Math.min(1, fill)) * 1000);
		return this;
	}

	BellView subtitle(String text) {
		this.subtitle = text;
		return this;
	}

	/** A "Label: value" line. */
	BellView stat(String label, String value) {
		lines.add(Component.literal(label + ": " + value));
		return this;
	}

	BellView text(String text, ChatFormatting color) {
		lines.add(Component.literal(text).withStyle(color));
		return this;
	}

	/** A text line in any color (for the light panel: ChatFormatting's gold and yellow are too pale there). */
	BellView text(String text, int rgb) {
		lines.add(Component.literal(text).withStyle(style -> style.withColor(rgb)));
		return this;
	}

	/** Dark amber: warnings that read well on the light grey panel. */
	static final int WARNING = 0x8A4B00;

	/** A requirement line with a green ✔ or a red ✘. */
	BellView requirement(boolean met, String text) {
		MutableComponent c = Component.literal(met ? "✔ " : "✘ ")
				.withStyle(met ? ChatFormatting.DARK_GREEN : ChatFormatting.RED)
				.append(Component.literal(text).withStyle(met ? ChatFormatting.BLACK : ChatFormatting.DARK_GRAY));
		lines.add(c);
		return this;
	}

	BellView station(String name, String item, int level, int state) {
		stations.add(new BellScreenPayload.Station(name, item, level, state));
		return this;
	}

	BellView button(String label, String tooltip, String command) {
		buttons.add(new BellScreenPayload.Action(label, tooltip, command, false));
		return this;
	}

	BellView dangerButton(String label, String tooltip, String command) {
		buttons.add(new BellScreenPayload.Action(label, tooltip, command, true));
		return this;
	}

	void show(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, BellScreenPayload.TYPE)) {
			net.minecraft.network.chat.TextColor color = net.minecraft.network.chat.TextColor.fromLegacyFormat(titleColor);
			ServerPlayNetworking.send(player, new BellScreenPayload(bell, title,
					0xFF000000 | (color == null ? 0x404040 : color.getValue()), subtitle, lines, stations, buttons,
					VRConfig.get().emeralds.maxPerDay, mood, gossip, party, renownRank, renownFill));
			return;
		}
		// Without the mod: the same content as a vanilla dialog.
		List<DialogBody> body = new ArrayList<>();
		if (!subtitle.isEmpty()) body.add(Dialogs.text(subtitle, ChatFormatting.GRAY));
		if (mood >= 0) body.add(Dialogs.text("Mood: " + dev.villagerework.village.Prosperity.MOODS[mood], ChatFormatting.GRAY));
		for (Component line : lines) body.add(Dialogs.component(line));
		if (renownRank >= 0) body.add(Dialogs.text("Renown " + renownRank, ChatFormatting.GRAY));
		if (!stations.isEmpty()) {
			body.add(Dialogs.component(Component.literal("Stations").withStyle(ChatFormatting.GOLD)));
			for (BellScreenPayload.Station s : stations) {
				if (s.state() == BellScreenPayload.Station.ACTIVE) {
					body.add(Dialogs.text(s.name() + "  level " + s.level(), ChatFormatting.WHITE));
				} else if (s.state() == BellScreenPayload.Station.NEEDS_BLOCK) {
					body.add(Dialogs.text(s.name() + "  level " + s.level() + ", needs a new station", ChatFormatting.DARK_GRAY));
				}
			}
		}
		List<ActionButton> actions = new ArrayList<>();
		for (BellScreenPayload.Action a : buttons) actions.add(Dialogs.button(a.label(), a.tooltip(), a.command()));
		Dialogs.show(player, Component.literal(title).withStyle(titleColor), body, actions);
	}
}
