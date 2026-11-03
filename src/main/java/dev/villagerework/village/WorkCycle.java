package dev.villagerework.village;

import dev.villagerework.config.VRConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The villagers' day, with two moments:
 *
 *  - Sunrise: the station makes a new request (one item from its pool, sized by level).
 *  - Evening, when villagers start gathering at the bell (before anyone can sleep through the night):
 *    the work is done for each active station with workers, and the request is closed until sunrise.
 *
 * The evening work:
 *  1. Contribution: how much of the request the player put in, 0-100%. That much is used up.
 *  2. Progress toward the next level: contribution × workers × rate. No contribution, no progress.
 *  3. Goods: one bulk delivery into each unlocked reward slot, scaled by contribution. With nothing supplied,
 *     only a single item in the first slot (stations.minimumOutputFactor, idleRewardSlots).
 *     An empty slot gets a random good; a slot that already holds something gets more of the
 *     same, up to a full stack; a full slot gets nothing.
 */
public final class WorkCycle {
	/** Time of day when villagers switch to gathering (vanilla's "meet" activity). */
	public static final long GATHER_TIME = 9000;

	private WorkCycle() {
	}

	public static long today(ServerLevel level) {
		return Math.floorDiv(level.getOverworldClockTime(), 24000L);
	}

	/** The latest evening that has started: today after gathering time, otherwise yesterday. */
	public static long currentWorkDay(ServerLevel level) {
		long time = level.getOverworldClockTime();
		long day = Math.floorDiv(time, 24000L);
		return Math.floorMod(time, 24000L) >= GATHER_TIME ? day : day - 1;
	}

	/** Runs on every village update. */
	static void update(ServerLevel level, VillageRegistry registry, Village village) {
		long today = today(level);
		long workDay = currentWorkDay(level);
		// Tonight's emerald boosts (only once per evening, from the pool the villagers brought to the bell).
		boolean poolDue = village.poolDay() == ProfessionStation.NEVER || village.poolDay() < workDay;
		Map<String, Double> boosts = poolDue ? emeraldBoosts(registry, village, workDay) : Map.of();
		for (Map.Entry<String, ProfessionStation> e : List.copyOf(village.professions().entrySet())) {
			String key = e.getKey();
			ProfessionStation station = e.getValue();
			Optional<StationTypes.Definition> def = StationTypes.get(key);
			if (def.isEmpty() || !station.active()) continue;

			// The clock went backwards (e.g. /time set): count from now instead of waiting days to catch up.
			if (station.lastWorkDay() != ProfessionStation.NEVER && station.lastWorkDay() > workDay) {
				registry.setLastWorkDay(village, key, workDay);
			}
			if (station.requestDay() != ProfessionStation.NEVER && station.requestDay() > today) {
				registry.setRequestDay(village, key, today);
			}
			if (station.lastWorkDay() == ProfessionStation.NEVER) {
				registry.setLastWorkDay(village, key, workDay);
			}

			// Evening
			long missed = workDay - station.lastWorkDay();
			if (missed > 0) {
				// Evenings the area was unloaded get the minimal delivery; supplies count for the latest one only.
				int runs = (int) Math.min(missed, Math.max(1, VRConfig.get().stations.maxCatchUpDays));
				double contribution = 0;
				for (int i = 0; i < runs; i++) {
					boolean latest = i == runs - 1;
					contribution = work(level, registry, village, key, station, def.get(), latest,
							latest ? boosts.getOrDefault(key, 0.0) : 0.0);
				}
				registry.recordWork(village, key, workDay, contribution, station.progress());
				registry.closeRequest(village, key);
			}

			// Sunrise
			if (!station.hasOpenRequest() && station.requestDay() < today) {
				newRequest(level, registry, village, key);
				registry.resetRerolls(village, key);
			}
		}
		// The evening has passed: the day's emeralds are used up (or wasted, if no station could use them).
		if (poolDue) registry.usePool(village, workDay);
	}

	/**
	 * Splits tonight's emerald points evenly between the stations that will work tonight and got some of
	 * their request. Each point is a boost (10% by default), at most 10 points per station; the rest is wasted.
	 */
	private static Map<String, Double> emeraldBoosts(VillageRegistry registry, Village village, long workDay) {
		VRConfig cfg = VRConfig.get();
		int points = cfg.emeraldPoints(village.emeraldPool());
		if (points <= 0) return Map.of();
		List<String> eligible = new java.util.ArrayList<>();
		for (Map.Entry<String, ProfessionStation> e : village.professions().entrySet()) {
			ProfessionStation p = e.getValue();
			if (!p.active() || StationTypes.get(e.getKey()).isEmpty()) continue;
			if (p.lastWorkDay() != ProfessionStation.NEVER && p.lastWorkDay() >= workDay) continue; // not working tonight
			if (!p.hasOpenRequest() || village.workerCount(e.getKey()) == 0) continue;
			if (registry.inventory(village, e.getKey()).supplied() <= 0) continue; // no contribution, no boost
			eligible.add(e.getKey());
		}
		int each = cfg.pointsPerStation(points, eligible.size());
		Map<String, Double> boosts = new java.util.HashMap<>();
		for (String key : eligible) boosts.put(key, each * cfg.emeralds.boostPerPoint);
		return boosts;
	}

	/** Debug: run the evening work now (uses donations and the emerald pool), then close the request. */
	public static void runNow(ServerLevel level, Village village) {
		VillageRegistry registry = VillageRegistry.get(level);
		Map<String, Double> boosts = emeraldBoosts(registry, village, Long.MAX_VALUE);
		registry.usePool(village, currentWorkDay(level));
		for (Map.Entry<String, ProfessionStation> e : List.copyOf(village.professions().entrySet())) {
			Optional<StationTypes.Definition> def = StationTypes.get(e.getKey());
			if (def.isEmpty() || !e.getValue().active()) continue;
			double c = work(level, registry, village, e.getKey(), e.getValue(), def.get(), true,
					boosts.getOrDefault(e.getKey(), 0.0));
			registry.recordWork(village, e.getKey(), currentWorkDay(level), c, e.getValue().progress());
			registry.closeRequest(village, e.getKey());
		}
	}

	/** Debug: sunrise now, every working station gets a fresh request. */
	public static void sunriseNow(ServerLevel level, Village village) {
		VillageRegistry registry = VillageRegistry.get(level);
		for (Map.Entry<String, ProfessionStation> e : List.copyOf(village.professions().entrySet())) {
			if (StationTypes.get(e.getKey()).isEmpty() || !e.getValue().active()) continue;
			newRequest(level, registry, village, e.getKey(), true);
			registry.resetRerolls(village, e.getKey());
		}
	}

	/** One day of work. Returns the contribution (0..1). */
	private static double work(ServerLevel level, VillageRegistry registry, Village village, String key,
			ProfessionStation station, StationTypes.Definition def, boolean useSupplies, double boost) {
		VRConfig.Stations cfg = VRConfig.get().stations;
		int workers = village.workerCount(key);
		if (workers == 0) return 0; // nobody works here yet: no goods, no progress, supplies kept

		StationInventory inv = registry.inventory(village, key);
		int levelIndex = Math.min(station.level(), cfg.maxLevel) - 1;

		// The village's mood (0.18) makes everyone work harder or slacker: progress and goods.
		double mood = Prosperity.production(level, village);

		// 1. Contribution
		double contribution = 0;
		Optional<Item> requested = station.requestItem().flatMap(StationTypes::item);
		if (useSupplies && requested.isPresent() && station.hasOpenRequest()) {
			int used = take(inv, requested.get(), station.requestCount());
			contribution = (double) used / station.requestCount();
			// The armorer's lava is used up bucket and all (0.20.3): no empty buckets pop out of the station.
		}

		// 2. Progress (only from contribution), capped at the full bar until the level-up (Phase 6)
		if (contribution > 0 && station.level() < cfg.maxLevel) {
			// The first worker counts extra (0.19.0), so a lone villager still levels its station.
			double crew = workers - 1 + cfg.firstWorkerWeight;
			double gain = contribution * crew * cfg.progressPerWorkerPerDay * (1 + boost) * mood;
			double full = cfg.progressToFillByLevel[levelIndex];
			registry.setProfessionLevel(village, key, station.level(), Math.min(full, station.progress() + gain));
		}

		// 3. Goods
		double factor = cfg.minimumOutputFactor + (1 - cfg.minimumOutputFactor) * contribution;
		// Nothing supplied: barely anything (by default one item, in the first reward slot only).
		int slots = contribution > 0 ? VRConfig.get().rewardSlots(station.level())
				: Math.min(VRConfig.get().rewardSlots(station.level()), Math.max(0, cfg.idleRewardSlots));
		StationTypes.ORIGIN.set(village.bellPos());
		try {
			deliver(level, inv, def, station.level(), cfg.outputMultiplierByLevel[levelIndex] * factor * (contribution > 0 ? 1 + boost : 1) * mood, slots);
		} finally {
			StationTypes.ORIGIN.remove();
		}

		if (contribution > 0) {
			station.stationPos().ifPresent(pos -> celebrate(level, pos));
		}
		// Renown (0.20.0): top-level stations' work adds to the village's renown.
		if (station.level() >= cfg.maxLevel) Renown.addWork(level, village, contribution);
		return contribution;
	}

	/** Removes up to {@code max} of {@code item} from the supply slots. Returns how many were taken. */
	private static int take(StationInventory inv, Item item, int max) {
		int taken = 0;
		for (int slot = 0; slot < StationInventory.SUPPLY_SLOTS && taken < max; slot++) {
			ItemStack stack = inv.getItem(slot);
			if (!stack.is(item)) continue;
			int n = Math.min(stack.getCount(), max - taken);
			inv.removeItem(slot, n);
			taken += n;
		}
		return taken;
	}

	/**
	 * Fills the reward slots the station's level unlocks ({@code stations.rewardSlotsByLevel}: 1, 2, 3).
	 * Each slot is independent: an empty slot gets its own random good from the level's pool (two slots
	 * can land on the same good), a slot already holding a good gets more of the same up to a full stack,
	 * a full slot nothing.
	 */
	private static void deliver(ServerLevel level, StationInventory inv, StationTypes.Definition def, int stationLevel,
			double multiplier, int slots) {
		for (int i = 0; i < slots && i < StationInventory.REWARD_SLOTS.length; i++) {
			deliverInto(level, inv, StationInventory.REWARD_SLOTS[i], def, stationLevel, multiplier);
		}
	}

	/** One reward slot's delivery. Returns the good it holds afterwards (null if none). */
	private static StationTypes.Good deliverInto(ServerLevel level, StationInventory inv, int slot,
			StationTypes.Definition def, int stationLevel, double multiplier) {
		ItemStack current = inv.getItem(slot);
		StationTypes.Good good;
		if (current.isEmpty()) {
			good = def.pickGood(level.getRandom(), stationLevel, null);
		} else {
			Optional<StationTypes.Good> same = def.goodFor(current);
			if (same.isEmpty()) return null; // something foreign: leave it alone
			good = same.get();
			if (current.getCount() >= StationTypes.cap(current)) return good; // full: skip
		}
		int amount = Math.max(1, (int) Math.round(good.baseAmount() * multiplier));
		if (current.isEmpty()) {
			ItemStack fresh = good.create(level, stationLevel);
			inv.setItem(slot, fresh.copyWithCount(Math.min(amount, StationTypes.cap(fresh))));
		} else {
			int room = StationTypes.cap(current) - current.getCount();
			inv.setItem(slot, current.copyWithCount(current.getCount() + Math.min(room, amount)));
		}
		return good;
	}

	/**
	 * A fresh request for today, different from the previous item, sized by the station's level.
	 * Used at sunrise, when a station is donated, and right after a level-up.
	 *
	 * 0.19.0: a request made after the station's evening work (a donation or level-up in the evening or at
	 * night) starts closed, so the station shows the barrier until sunrise like every other station.
	 * Before, it stayed open through the night and the whole next day.
	 */
	public static void newRequest(ServerLevel level, VillageRegistry registry, Village village, String key) {
		newRequest(level, registry, village, key, false);
	}

	/** {@code openAnyway}: the debug sunrise, which always opens a request. */
	public static void newRequest(ServerLevel level, VillageRegistry registry, Village village, String key,
			boolean openAnyway) {
		Optional<StationTypes.Definition> def = StationTypes.get(key);
		Optional<ProfessionStation> station = village.profession(key);
		if (def.isEmpty() || station.isEmpty()) return;
		VRConfig.Stations cfg = VRConfig.get().stations;
		int levelIndex = Math.min(station.get().level(), cfg.maxLevel) - 1;
		StationTypes.Need need = def.get().pickNeed(level.getRandom(),
				station.get().requestItem().flatMap(StationTypes::item).orElse(null), station.get().level());
		// The item's own amount at level 1, scaled like dailyInputCapByLevel, never more than one stack;
		// or its own amount per level (fuel, 0.20.0).
		double scale = (double) cfg.dailyInputCapByLevel[levelIndex] / Math.max(1, cfg.dailyInputCapByLevel[0]);
		int fixed = need.countAt(station.get().level());
		int count = fixed > 0 ? fixed : Math.max(1, (int) Math.round(need.baseCount() * scale));
		count = Math.min(count, new ItemStack(need.item()).getMaxStackSize());
		registry.setRequest(village, key, StationTypes.itemId(need.item()), count, today(level));
		if (!openAnyway && eveningWorked(level, station.get())) registry.closeRequest(village, key);
	}

	/** The evening work has run for this station today, and it's still before sunrise. */
	private static boolean eveningWorked(ServerLevel level, ProfessionStation station) {
		long timeOfDay = Math.floorMod(level.getOverworldClockTime(), 24000L);
		return timeOfDay >= GATHER_TIME && station.lastWorkDay() != ProfessionStation.NEVER
				&& station.lastWorkDay() >= today(level);
	}

	private static void celebrate(ServerLevel level, BlockPos pos) {
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
				6, 0.4, 0.3, 0.4, 0.0);
	}
}
