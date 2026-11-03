package dev.villagerework.trader;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.villagerework.VillageRework;
import dev.villagerework.config.VRConfig;
import dev.villagerework.village.WorkCycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * When the wandering trader comes (0.12). Replaces vanilla's spawn odds (one roll a day at 25-75%, then
 * another 1 in 10, so in practice every couple of weeks).
 *
 * - Only one trader at a time: while the one the mod sent is still around, no new one comes.
 * - The count starts the day he leaves (despawns, dies, or has surely left). From then on the chance
 *   grows each day ({@code trader.visitChanceByDaysSince}); on the last entry's day (1.0 by default:
 *   day 4) he is due and the spawner tries every minute until he finds a spot near a player.
 * - "Still around": he is found alive, or he can't be found (area not loaded) and his two-day stay,
 *   counted in played ticks like vanilla's despawn timer, isn't over. A trader killed by a player
 *   therefore still "stays" until his time is up. /time add doesn't shorten a stay; only played time does.
 *
 * 0.20.0:
 * - He stays one day (was two).
 * - Vanilla only counts a trader's stay down while his area is loaded. A trader left behind in an unloaded
 *   village used to wait there, frozen, while the mod sent the next one elsewhere; coming home you'd find
 *   several. Now a trader that loads from disk and isn't the current one (or has overstayed) leaves a moment
 *   later, with his llamas. The current one also leaves once his day is over by the clock.
 *
 * Saved in the overworld.
 */
public final class TraderVisits extends SavedData {
	public static final long NEVER = Long.MIN_VALUE;
	/** How long he stays (0.20.0: one day; vanilla 48000), in loaded ticks for vanilla's own timer. */
	private static final int STAY = 24000;
	/** His stay by the clock, plus a minute of slack. */
	private static final long STAY_TICKS = STAY + 1200;
	/** Traders that loaded from disk and should leave, with the tick to send them away (after leashes restore). */
	private static final java.util.List<Object[]> LEAVING = new java.util.ArrayList<>();

	public static final Codec<TraderVisits> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.LONG.optionalFieldOf("last_visit_day", NEVER).forGetter(t -> t.lastLeftDay),
			Codec.LONG.optionalFieldOf("last_roll_day", NEVER).forGetter(t -> t.lastRollDay),
			Codec.BOOL.optionalFieldOf("pending", false).forGetter(t -> t.pending),
			UUIDUtil.CODEC.optionalFieldOf("trader").forGetter(t -> Optional.ofNullable(t.trader)),
			Codec.LONG.optionalFieldOf("arrived_tick", 0L).forGetter(t -> t.arrivedTick),
			BlockPos.CODEC.optionalFieldOf("last_seen").forGetter(t -> Optional.ofNullable(t.lastSeen))
	).apply(instance, TraderVisits::new));

	public static final SavedDataType<TraderVisits> TYPE = new SavedDataType<>(
			VillageRework.id("trader_visits"),
			TraderVisits::new,
			CODEC,
			null
	);

	/** The day the last trader left (the count starts here). */
	private long lastLeftDay;
	private long lastRollDay;
	private boolean pending;
	/** The trader the mod sent, while he's around. */
	private UUID trader;
	private long arrivedTick;
	private BlockPos lastSeen;

	public TraderVisits() {
		this(NEVER, NEVER, false, Optional.empty(), 0L, Optional.empty());
	}

	private TraderVisits(long lastLeftDay, long lastRollDay, boolean pending, Optional<UUID> trader, long arrivedTick,
			Optional<BlockPos> lastSeen) {
		this.lastLeftDay = lastLeftDay;
		this.lastRollDay = lastRollDay;
		this.pending = pending;
		this.trader = trader.orElse(null);
		this.arrivedTick = arrivedTick;
		this.lastSeen = lastSeen.orElse(null);
	}

	public static TraderVisits get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	/**
	 * True while the trader the mod sent is still around. Notices when he's gone and starts the count
	 * from that day.
	 */
	public boolean traderPresent(ServerLevel level) {
		if (trader == null) return false;
		Entity entity = level.getEntity(trader);
		boolean present;
		if (entity instanceof WanderingTrader t && t.isAlive()) {
			BlockPos pos = t.blockPosition();
			if (!pos.equals(lastSeen)) {
				lastSeen = pos.immutable();
				setDirty();
			}
			present = level.getGameTime() - arrivedTick < STAY_TICKS;
			if (!present) sendAway(level, t); // his day is over by the clock (part of it spent unloaded)
		} else if (entity == null) {
			// Not found: his area isn't loaded (or not yet), or he's gone. Either way he counts as
			// around until his stay is over; after that he has surely left.
			present = level.getGameTime() - arrivedTick < STAY_TICKS;
		} else {
			present = false; // found, but dead
		}
		if (!present) {
			trader = null;
			lastSeen = null;
			lastLeftDay = WorkCycle.today(level);
			lastRollDay = lastLeftDay;
			pending = false;
			setDirty();
		}
		return present;
	}

	/** Days since the last trader left (0 = today). Counting starts the first day the mod sees. */
	public long daysSinceLeft(ServerLevel level) {
		long today = WorkCycle.today(level);
		if (lastLeftDay == NEVER || lastLeftDay > today) {
			// First run, or the clock went backwards: count from today.
			lastLeftDay = today;
			setDirty();
		}
		return today - lastLeftDay;
	}

	/** Chance of a visit on a day {@code days} after the last trader left (0 the same day). */
	public static double chance(long days) {
		if (days <= 0) return 0;
		double[] table = VRConfig.get().trader.visitChanceByDaysSince;
		return table[(int) Math.min(days, table.length) - 1];
	}

	/** True if the trader should come today (rolled once per day; certain once the table reaches 1.0). */
	private boolean dueToday(ServerLevel level, RandomSource random) {
		long days = daysSinceLeft(level);
		long today = WorkCycle.today(level);
		if (days <= 0 && !pending) return false;
		if (lastRollDay != today) {
			lastRollDay = today;
			pending = random.nextDouble() < chance(days);
			setDirty();
		}
		return pending;
	}

	private void recordArrival(ServerLevel level, WanderingTrader arrived) {
		arrived.setDespawnDelay(STAY); // his llamas follow his timer while leashed
		trader = arrived.getUUID();
		arrivedTick = level.getGameTime();
		lastSeen = arrived.blockPosition().immutable();
		lastRollDay = WorkCycle.today(level);
		pending = false;
		setDirty();
	}

	/** Called every minute by the spawner mixin. {@code spawn} tries to place a trader near a random player. */
	public static void tick(ServerLevel level, RandomSource random, Supplier<WanderingTrader> spawn) {
		TraderVisits visits = get(level);
		if (visits.traderPresent(level)) return;
		if (!visits.dueToday(level, random)) return;
		WanderingTrader arrived = spawn.get();
		if (arrived != null) visits.recordArrival(level, arrived);
	}

	/** Debug: forget the current trader and make the next one due now; he arrives within a minute. */
	public void forceDue(ServerLevel level) {
		trader = null;
		lastSeen = null;
		lastLeftDay = WorkCycle.today(level) - 1;
		lastRollDay = WorkCycle.today(level);
		pending = true;
		setDirty();
	}

	/** 0.20.0: watches traders loading from disk; the stale ones leave shortly after. */
	public static void init() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, fromDisk) -> {
			if (fromDisk && entity instanceof WanderingTrader) {
				LEAVING.add(new Object[]{level, entity.getUUID(), level.getGameTime() + 40});
			}
			return true;
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (LEAVING.isEmpty()) return;
			long now = level.getGameTime();
			java.util.Iterator<Object[]> it = LEAVING.iterator();
			while (it.hasNext()) {
				Object[] e = it.next();
				if (e[0] != level || (long) e[2] > now) continue;
				it.remove();
				if (!(level.getEntity((UUID) e[1]) instanceof WanderingTrader t) || !t.isAlive()) continue;
				TraderVisits visits = get(level.getServer().overworld());
				boolean current = t.getUUID().equals(visits.trader)
						&& level.getGameTime() - visits.arrivedTick < STAY_TICKS;
				if (!current) sendAway(level, t);
			}
		});
	}

	/** The trader and the llamas leashed to him leave (vanilla's despawn, without drops). */
	private static void sendAway(ServerLevel level, WanderingTrader t) {
		for (net.minecraft.world.entity.animal.equine.TraderLlama llama : level.getEntitiesOfClass(
				net.minecraft.world.entity.animal.equine.TraderLlama.class, t.getBoundingBox().inflate(16),
				l -> l.getLeashHolder() == t)) {
			llama.discard();
		}
		t.discard();
	}

	/** Forgets traders waiting to leave when the server stops. */
	public static void reset() {
		LEAVING.clear();
	}

	public boolean hasTrader() {
		return trader != null;
	}

	public boolean pending() {
		return pending;
	}
}
