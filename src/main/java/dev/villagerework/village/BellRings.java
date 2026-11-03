package dev.villagerework.village;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The center bell rings twice a day (0.20.0): at sunrise, when the stations make their new requests, and when the
 * villagers gather in the evening, when the day's work is done and the stations close. Content and abundant
 * villages ring three times at the gathering (this replaces the 0.18.3 chime).
 *
 * Only the swing and the sound are sent to players. A real ring makes vanilla villagers run indoors for a while,
 * which would break up the gathering. Not saved: a ring missed while the area was unloaded doesn't matter.
 */
public final class BellRings {
	/** Ring windows: the first village update after these times of day rings (updates run every 5 seconds). */
	private static final long MORNING = 0, MORNING_END = 1000;
	private static final long GATHERING_END = WorkCycle.GATHER_TIME + 1000;
	/** Ticks between rings when a bell rings more than once. */
	private static final int RING_GAP = 24;

	private record Pending(ServerLevel level, BlockPos bell, long dueTick) {
	}

	/** Village -> "day:moment" of the last ring, so each moment rings once. */
	private static final Map<UUID, String> RUNG = new HashMap<>();
	private static final List<Pending> PENDING = new ArrayList<>();

	private BellRings() {
	}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (PENDING.isEmpty()) return;
			long now = level.getGameTime();
			Iterator<Pending> it = PENDING.iterator();
			while (it.hasNext()) {
				Pending p = it.next();
				if (p.level() != level || p.dueTick() > now) continue;
				it.remove();
				ring(level, p.bell());
			}
		});
	}

	/** Called on every village update (active villages with their bell loaded). */
	static void update(ServerLevel level, Village village) {
		long timeOfDay = Math.floorMod(level.getOverworldClockTime(), 24000L);
		String moment;
		int times;
		if (timeOfDay >= MORNING && timeOfDay < MORNING_END) {
			moment = "morning";
			times = 1;
		} else if (timeOfDay >= WorkCycle.GATHER_TIME && timeOfDay < GATHERING_END) {
			moment = "gathering";
			times = Prosperity.mood(level, village) >= 3 ? 3 : 1;
		} else {
			return;
		}
		String key = WorkCycle.today(level) + ":" + moment;
		if (key.equals(RUNG.get(village.id()))) return;
		RUNG.put(village.id(), key);
		long now = level.getGameTime();
		for (int i = 0; i < times; i++) PENDING.add(new Pending(level, village.bellPos(), now + (long) i * RING_GAP));
	}

	/** Swings the bell for nearby players and plays its sound. */
	private static void ring(ServerLevel level, BlockPos bell) {
		BlockState state = level.getBlockState(bell);
		if (!state.is(Blocks.BELL)) return;
		Direction facing = state.hasProperty(BellBlock.FACING) ? state.getValue(BellBlock.FACING) : Direction.NORTH;
		ClientboundBlockEventPacket swing = new ClientboundBlockEventPacket(bell, Blocks.BELL, 1, facing.get3DDataValue());
		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().distSqr(bell) <= 96 * 96) player.connection.send(swing);
		}
		level.playSound(null, bell, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.0F);
	}

	/** Forgets rings when the server stops. */
	public static void reset() {
		RUNG.clear();
		PENDING.clear();
	}
}
