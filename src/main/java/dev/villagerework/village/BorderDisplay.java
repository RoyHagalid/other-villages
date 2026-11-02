package dev.villagerework.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Draws a village's edge as a stacked wall of red particles on the terrain for one player,
 * for a few seconds. Used by the bell panel's "Show border" button.
 */
public final class BorderDisplay {
	private record Entry(UUID player, ResourceKey<Level> dimension, BlockPos center, int radius, long expiresAt) {
	}

	private static final List<Entry> ACTIVE = new ArrayList<>();
	/** Red dust, a bit larger than redstone dust so it reads from a distance (0.18.5: was green). */
	private static final DustParticleOptions MARKER = new DustParticleOptions(0xFF2020, 2.0F);
	/** Layers stacked above the ground, one block apart, so the edge stands up like a fence. */
	private static final int LAYERS = 8;

	private BorderDisplay() {
	}

	public static void show(ServerPlayer player, BlockPos center, int radius, int seconds) {
		ServerLevel level = (ServerLevel) player.level();
		ACTIVE.removeIf(e -> e.player().equals(player.getUUID()));
		ACTIVE.add(new Entry(player.getUUID(), level.dimension(), center.immutable(), radius,
				level.getGameTime() + seconds * 20L));
	}

	static void tick(ServerLevel level) {
		if (ACTIVE.isEmpty() || level.getGameTime() % 10 != 0) return;
		Iterator<Entry> it = ACTIVE.iterator();
		while (it.hasNext()) {
			Entry entry = it.next();
			if (!entry.dimension().equals(level.dimension())) continue;
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.player());
			if (player == null || player.level() != level || level.getGameTime() > entry.expiresAt()) {
				it.remove();
				continue;
			}
			draw(level, player, entry.center(), entry.radius());
		}
	}

	private static void draw(ServerLevel level, ServerPlayer player, BlockPos center, int radius) {
		// About one marker every two blocks along the edge, capped for very large radii.
		int points = Math.min(240, Math.max(24, (int) (Math.PI * radius)));
		for (int i = 0; i < points; i++) {
			double angle = (Math.PI * 2 * i) / points;
			int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
			int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);
			BlockPos column = new BlockPos(x, center.getY(), z);
			if (!level.isLoaded(column)) continue;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			for (int dy = 0; dy < LAYERS; dy++) {
				level.sendParticles(player, MARKER, true, true,
						x + 0.5, y + 0.3 + dy, z + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}

	/** Forgets border displays when the server stops. */
	public static void reset() {
		ACTIVE.clear();
	}
}
