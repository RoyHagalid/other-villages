package dev.villagerework.perk;

import dev.villagerework.config.VRConfig;
import dev.villagerework.net.StoryPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.entity.LecternBlockEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Librarian's lectern: a signed book on it becomes a message, triggered by a redstone signal only
 * (pairs with a calibrated sculk sensor, which also reaches 16 blocks).
 *  - Level II, title card: page 1 shows as a title on the screens of players within {@code lectern.range}
 *    blocks (line 1 the title, line 2 the subtitle).
 *  - Level III, story scroll: a book with more than one page scrolls up the screen like the end credits
 *    (skippable with Esc). A one-page book still gives a title card.
 * Limits: a lectern triggers at most once per {@code lectern.cooldownSeconds}; the same scroll plays to the
 * same player at most once per {@code lectern.scrollRepeatMinutes} (2). Breaking the lectern resets both.
 * Only a rising redstone edge triggers.
 */
public final class LecternMessages {
	private LecternMessages() {
	}

	/** Redstone state per lectern, to react only when the signal turns on. */
	private static final Map<BlockPos, Boolean> POWERED = new HashMap<>();
	private static final Map<BlockPos, Long> LAST_TRIGGER = new HashMap<>();
	private static final Map<String, Long> LAST_SCROLL = new HashMap<>();

	public static void onNeighborChanged(ServerLevel level, BlockPos pos) {
		boolean powered = level.hasNeighborSignal(pos);
		Boolean was = POWERED.put(pos.immutable(), powered);
		if (!powered || Boolean.TRUE.equals(was)) return;
		int perk = Perks.level(level, pos, Perks.LIBRARIAN);
		if (perk < 2 || !(level.getBlockEntity(pos) instanceof LecternBlockEntity lectern)) return;
		ItemStack book = lectern.getBook();
		if (!book.is(Items.WRITTEN_BOOK)) return;
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (content == null || content.pages().isEmpty()) return;

		VRConfig.Lectern cfg = VRConfig.get().lectern;
		long now = level.getGameTime();
		Long last = LAST_TRIGGER.get(pos);
		if (last != null && now - last < cfg.cooldownSeconds * 20L) return;
		LAST_TRIGGER.put(pos.immutable(), now);

		List<Component> pages = content.pages().stream().map(Filterable::raw).toList();
		double range2 = (double) cfg.range * cfg.range;
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > range2) continue;
			if (perk >= 3 && pages.size() > 1) {
				String key = player.getUUID() + "@" + pos.asLong();
				Long played = LAST_SCROLL.get(key);
				if (played != null && now - played < cfg.scrollRepeatMinutes * 1200L) continue;
				LAST_SCROLL.put(key, now);
				ServerPlayNetworking.send(player, new StoryPayload(content.title().raw(), content.author(), pages));
			} else {
				titleCard(player, pages.getFirst());
			}
		}
	}

	private static void titleCard(ServerPlayer player, Component page) {
		String[] lines = page.getString().split("\n");
		String title = lines.length > 0 ? lines[0].trim() : "";
		String subtitle = lines.length > 1 ? lines[1].trim() : "";
		if (title.isEmpty() && subtitle.isEmpty()) return;
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
	}

	/** A lectern was broken: its cooldowns start over (for the lectern and for every player's scroll). */
	public static void forget(BlockPos pos) {
		POWERED.remove(pos);
		LAST_TRIGGER.remove(pos);
		String suffix = "@" + pos.asLong();
		LAST_SCROLL.keySet().removeIf(key -> key.endsWith(suffix));
	}

	/** Forgets lectern timers when the server stops. */
	public static void reset() {
		POWERED.clear();
		LAST_TRIGGER.clear();
		LAST_SCROLL.clear();
	}
}
