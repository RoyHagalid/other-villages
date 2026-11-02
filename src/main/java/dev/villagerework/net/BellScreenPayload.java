package dev.villagerework.net;

import dev.villagerework.VillageRework;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client (0.17.11): open the bell screen with this content. The server decides everything that's shown
 * (it knows the village); the client only lays it out. Buttons run /village commands, which re-check on the server.
 *
 * @param title       the village's name (or "Unclaimed bell" and so on)
 * @param titleColor  ARGB color of the title
 * @param subtitle    grey line under the title ("A natural village"), may be empty
 * @param lines       text lines (stats, explanations, requirements with ✔/✘)
 * @param stations    the station grid; empty for bell versions without one
 * @param buttons     the actions; the client adds Close itself
 * @param emeraldCap  the daily emerald limit, for the help text
 * @param mood        the village's mood, 0 miserable … 4 abundant (0.18); -1 shows no face
 * @param gossip      what the villagers talk about (recent memories), shown when hovering the face
 * @param party       the party switch (0.18.1): -1 not shown, 0 no party allowed, 1 party allowed
 * @param renownRank  the village's renown rank (0.20.0); -1 shows no renown bar
 * @param renownFill  how full the bar toward the next rank is, 0..1000
 */
public record BellScreenPayload(BlockPos bell, String title, int titleColor, String subtitle, List<Component> lines,
		List<Station> stations, List<Action> buttons, int emeraldCap, int mood, List<Component> gossip, int party,
		int renownRank, int renownFill)
		implements CustomPacketPayload {

	/** One cell of the station grid. {@code state}: 0 no station, 1 working, 2 lost its block (keeps its level). */
	public record Station(String name, String item, int level, int state) {
		public static final int NONE = 0;
		public static final int ACTIVE = 1;
		public static final int NEEDS_BLOCK = 2;
	}

	/** A button: what it says, its tooltip, the command it runs, and whether it's a dangerous one (red text). */
	public record Action(String label, String tooltip, String command, boolean danger) {
	}

	public static final Type<BellScreenPayload> TYPE = new Type<>(VillageRework.id("bell_screen"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BellScreenPayload> CODEC = StreamCodec.of(
			BellScreenPayload::write, BellScreenPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, BellScreenPayload p) {
		buf.writeBlockPos(p.bell);
		buf.writeUtf(p.title);
		buf.writeInt(p.titleColor);
		buf.writeUtf(p.subtitle);
		buf.writeVarInt(p.lines.size());
		for (Component line : p.lines) ComponentSerialization.STREAM_CODEC.encode(buf, line);
		buf.writeVarInt(p.stations.size());
		for (Station s : p.stations) {
			buf.writeUtf(s.name());
			buf.writeUtf(s.item());
			buf.writeVarInt(s.level());
			buf.writeVarInt(s.state());
		}
		buf.writeVarInt(p.buttons.size());
		for (Action a : p.buttons) {
			buf.writeUtf(a.label());
			buf.writeUtf(a.tooltip());
			buf.writeUtf(a.command());
			buf.writeBoolean(a.danger());
		}
		buf.writeVarInt(p.emeraldCap);
		buf.writeVarInt(p.mood + 1);
		buf.writeVarInt(p.gossip.size());
		for (Component line : p.gossip) ComponentSerialization.STREAM_CODEC.encode(buf, line);
		buf.writeVarInt(p.party + 1);
		buf.writeVarInt(p.renownRank + 1);
		buf.writeVarInt(p.renownFill);
	}

	private static BellScreenPayload read(RegistryFriendlyByteBuf buf) {
		BlockPos bell = buf.readBlockPos();
		String title = buf.readUtf();
		int titleColor = buf.readInt();
		String subtitle = buf.readUtf();
		List<Component> lines = new ArrayList<>();
		for (int i = buf.readVarInt(); i > 0; i--) lines.add(ComponentSerialization.STREAM_CODEC.decode(buf));
		List<Station> stations = new ArrayList<>();
		for (int i = buf.readVarInt(); i > 0; i--) {
			stations.add(new Station(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt()));
		}
		List<Action> buttons = new ArrayList<>();
		for (int i = buf.readVarInt(); i > 0; i--) {
			buttons.add(new Action(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readBoolean()));
		}
		int cap = buf.readVarInt();
		int mood = buf.readVarInt() - 1;
		List<Component> gossip = new ArrayList<>();
		for (int i = buf.readVarInt(); i > 0; i--) gossip.add(ComponentSerialization.STREAM_CODEC.decode(buf));
		int party = buf.readVarInt() - 1;
		int renownRank = buf.readVarInt() - 1;
		int renownFill = buf.readVarInt();
		return new BellScreenPayload(bell, title, titleColor, subtitle, lines, stations, buttons, cap, mood, gossip, party,
				renownRank, renownFill);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
