package dev.villagerework.perk;

import dev.villagerework.net.StationLooksPayload;
import dev.villagerework.village.ProfessionStation;
import dev.villagerework.village.Village;
import dev.villagerework.village.VillageRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Upgraded looks (0.17.5): tells each player's client which station blocks around them are tier II or III,
 * so the client can draw the tier marks on them. Two kinds of blocks count:
 * - village stations at level II/III (a dormant village shows nothing, like its perks), so players can
 *   see what a village's workers would sell before buying;
 * - placed take-homes, at their own tier.
 *
 * When a village or the take-homes change (and once a second as a safety net), the wanted state of each player's
 * dimension is compared with what that player was last sent, and only the changes go out on the next tick. A
 * player in a new dimension (or just joined) gets the full list. Placing a take-home is also shown at once by the
 * placing player's own client (see {@link #clientPrediction}), so the marks appear together with the block.
 */
public final class StationLooks {
	/** Safety net: check this often even without a change. */
	private static final int INTERVAL = 20;
	private static volatile boolean changed = true;

	/**
	 * Set by the client: a take-home was just placed at this position with this tier, show it right away
	 * (BlockItemMixin calls it on the client side; the server's own message confirms it a moment later).
	 */
	public static java.util.function.BiConsumer<net.minecraft.core.BlockPos, Integer> clientPrediction = (pos, tier) -> {
	};

	/** What each player's client knows: its dimension and position → tier. */
	private record Sent(ResourceKey<Level> dimension, Map<Long, Integer> tiers) {
	}

	private static final Map<UUID, Sent> SENT = new HashMap<>();

	private StationLooks() {
	}

	/** A village or take-home changed: compare on the next tick. */
	public static void changed() {
		changed = true;
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(StationLooks::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.getPlayer().getUUID()));
	}

	/** Every station block in {@code level} that should look upgraded, as packed position → tier. */
	public static Map<Long, Integer> wanted(ServerLevel level) {
		Map<Long, Integer> out = new HashMap<>();
		for (Village village : VillageRegistry.get(level).all()) {
			if (village.dormant()) continue;
			for (ProfessionStation station : village.professions().values()) {
				if (!station.active() || station.level() < 2) continue;
				station.stationPos().ifPresent(pos -> out.put(pos.asLong(), Math.min(3, station.level())));
			}
		}
		TakeHomes.get(level).tiers().forEach((pos, tier) -> out.putIfAbsent(pos.asLong(), tier));
		return out;
	}

	private static void tick(MinecraftServer server) {
		if (!changed && server.getTickCount() % INTERVAL != 0) return;
		changed = false;
		Map<ServerLevel, Map<Long, Integer>> byLevel = new IdentityHashMap<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!ServerPlayNetworking.canSend(player, StationLooksPayload.TYPE)) continue;
			ServerLevel level = (ServerLevel) player.level();
			Map<Long, Integer> wanted = byLevel.computeIfAbsent(level, StationLooks::wanted);
			Sent sent = SENT.get(player.getUUID());
			boolean reset = sent == null || !sent.dimension().equals(level.dimension());
			Map<Long, Integer> known = reset ? Map.of() : sent.tiers();

			List<Long> positions = new ArrayList<>();
			List<Integer> tiers = new ArrayList<>();
			wanted.forEach((pos, tier) -> {
				if (!tier.equals(known.get(pos))) {
					positions.add(pos);
					tiers.add(tier);
				}
			});
			for (Long pos : known.keySet()) {
				if (!wanted.containsKey(pos)) {
					positions.add(pos);
					tiers.add(0);
				}
			}
			if (!reset && positions.isEmpty()) continue;
			ServerPlayNetworking.send(player, new StationLooksPayload(reset, positions, tiers));
			SENT.put(player.getUUID(), new Sent(level.dimension(), new HashMap<>(wanted)));
		}
	}

	/** Forgets what clients were told when the server stops. */
	public static void reset() {
		SENT.clear();
		changed = true;
	}
}
