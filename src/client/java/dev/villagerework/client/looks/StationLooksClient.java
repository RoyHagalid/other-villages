package dev.villagerework.client.looks;

import dev.villagerework.net.StationLooksPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client half of the upgraded looks (0.17.5): which station blocks in the current dimension are tier II or III,
 * as sent by the server (StationLooks). Read from chunk-building threads, hence the concurrent map.
 */
public final class StationLooksClient {
	private static final Map<Long, Integer> TIERS = new ConcurrentHashMap<>();
	/** Take-homes this client just placed: shown at once, until the server's word arrives (or 3 s pass). */
	private static final Map<Long, Prediction> PREDICTED = new ConcurrentHashMap<>();

	private record Prediction(int tier, long until) {
	}

	private StationLooksClient() {
	}

	/** The tier the block at {@code pos} should look like (0 = plain). */
	public static int tier(BlockPos pos) {
		Integer tier = TIERS.get(pos.asLong());
		if (tier != null) return tier;
		Prediction guess = PREDICTED.get(pos.asLong());
		return guess != null && System.currentTimeMillis() < guess.until() ? guess.tier() : 0;
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(StationLooksPayload.TYPE,
				(payload, context) -> apply(context.client(), payload));
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			TIERS.clear();
			PREDICTED.clear();
		});
		dev.villagerework.perk.StationLooks.clientPrediction = (pos, tier) -> {
			PREDICTED.put(pos.asLong(), new Prediction(tier, System.currentTimeMillis() + 3000));
			markDirty(Minecraft.getInstance(), Set.of(pos.asLong()));
		};
		StationOverlays.register();
	}

	private static void apply(Minecraft client, StationLooksPayload payload) {
		Set<Long> changed = new HashSet<>();
		if (payload.reset()) {
			changed.addAll(TIERS.keySet());
			TIERS.clear();
			PREDICTED.clear();
		}
		for (int i = 0; i < payload.positions().size() && i < payload.tiers().size(); i++) {
			long pos = payload.positions().get(i);
			int tier = payload.tiers().get(i);
			if (tier >= 2) TIERS.put(pos, tier);
			else TIERS.remove(pos);
			changed.add(pos);
			PREDICTED.remove(pos);
		}
		markDirty(client, changed);
	}

	private static void markDirty(Minecraft client, Set<Long> changed) {
		// Rebuild the chunk sections those blocks sit in, so the marks appear or vanish right away.
		ClientLevel level = client.level;
		if (level == null) return;
		Set<Long> sections = new HashSet<>();
		for (long packed : changed) {
			BlockPos pos = BlockPos.of(packed);
			int sx = SectionPos.blockToSectionCoord(pos.getX());
			int sy = SectionPos.blockToSectionCoord(pos.getY());
			int sz = SectionPos.blockToSectionCoord(pos.getZ());
			if (sections.add(SectionPos.asLong(sx, sy, sz))) level.setSectionDirtyWithNeighbors(sx, sy, sz);
		}
	}
}
