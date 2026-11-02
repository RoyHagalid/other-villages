package dev.villagerework.village;

import dev.villagerework.VillageRework;
import dev.villagerework.config.VRConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 4: who works where.
 *
 * - A villager's profession is locked once it has one. It counts toward that profession's
 *   worker cap in the village it lives in, whether the station is there right now or not.
 * - A worker is a villager whose profession has an active station. Workers get the station
 *   as their job site, so vanilla AI walks them there during work hours.
 * - Adults without a profession (not nitwits, not babies) take a job at an active station
 *   with free slots, preferring stations with no workers yet. Checked on every village update.
 * - Disbanding a village makes its residents unemployed again.
 * - 0.19.0: a villager whose profession has no active station in its village (the station was lost, or it
 *   never had one, e.g. a stray from the edge of a big generated village) becomes unemployed after
 *   stations.jobReleaseDays, and can then take any free job. Timers are memory only.
 */
public final class WorkforceManager {
	private WorkforceManager() {
	}

	/** Villager -> game time it was first seen without an active station for its profession (0.19.0). */
	private static final Map<UUID, Long> JOBLESS_SINCE = new HashMap<>();
	/** "village/profession" -> game time the station last took a worker (0.20.0: hire delay). Memory only. */
	private static final Map<String, Long> LAST_HIRE = new HashMap<>();

	/** Profession counts and who is unemployed, for one village. */
	public record Workforce(Map<String, Integer> byProfession, int unemployed, int nitwits) {
		public int workers(String profession) {
			return byProfession.getOrDefault(profession, 0);
		}
	}

	/** The workforce only runs while vanilla job claiming is switched off. */
	public static boolean isActive() {
		return VRConfig.get().features.disableVanillaJobClaiming;
	}

	// ---------------------------------------------------------------- reading

	public static Optional<String> professionKey(Villager villager) {
		var profession = villager.getVillagerData().profession();
		if (profession.is(VillagerProfession.NONE) || profession.is(VillagerProfession.NITWIT)) return Optional.empty();
		return profession.unwrapKey().map(key -> key.identifier().toString());
	}

	public static boolean isUnemployed(Villager villager) {
		return villager.getVillagerData().profession().is(VillagerProfession.NONE);
	}

	public static boolean isNitwit(Villager villager) {
		return villager.getVillagerData().profession().is(VillagerProfession.NITWIT);
	}

	/**
	 * Worker counts come from the village's roster (so absent workers keep their slots);
	 * unemployed adults and nitwits are counted from the residents loaded right now.
	 */
	public static Workforce count(Village village, List<Villager> residents) {
		int unemployed = 0, nitwits = 0;
		for (Villager v : residents) {
			if (isNitwit(v)) nitwits++;
			else if (isUnemployed(v) && !v.isBaby()) unemployed++;
		}
		return new Workforce(village.workerCounts(), unemployed, nitwits);
	}

	public static Workforce count(ServerLevel level, Village village) {
		return count(village, VillageManager.villagersInside(level, village));
	}

	// ---------------------------------------------------------------- update

	/** Runs on every village update, for active villages. */
	static void update(ServerLevel level, Village village, List<Villager> residents) {
		// With vanilla job claiming switched back on, vanilla hands out professions itself.
		// Running both makes villagers swap jobs constantly, so the workforce pauses.
		if (!isActive()) return;
		VillageRegistry registry = VillageRegistry.get(level);

		// 1. Keep the roster in line with the residents who are here right now.
		long now = level.getGameTime();
		long releaseTicks = (long) (VRConfig.get().stations.jobReleaseDays * 24000L);
		for (Villager villager : residents) {
			Optional<String> key = professionKey(villager);
			if (key.isPresent() && !village.profession(key.get()).map(ProfessionStation::active).orElse(false)) {
				// No station for this job here: after a while the villager gives it up (0.19.0).
				long since = JOBLESS_SINCE.computeIfAbsent(villager.getUUID(), id -> now);
				if (now - since >= releaseTicks && release(level, villager)) {
					JOBLESS_SINCE.remove(villager.getUUID());
					key = Optional.empty();
				}
			} else {
				JOBLESS_SINCE.remove(villager.getUUID());
			}
			if (key.isPresent()) {
				registry.setWorker(village, villager.getUUID(), key.get());
				pointAtStation(level, village, villager, key.get());
			} else {
				registry.removeWorker(village, villager.getUUID());
			}
		}

		// 2. Hand out free slots to unemployed adults.
		int cap = VRConfig.get().stations.workerCapPerStation;
		Map<String, Integer> counts = new HashMap<>(village.workerCounts());
		for (Villager villager : residents) {
			if (villager.isBaby() || villager.isSleeping() || !isUnemployed(villager)) continue;
			Optional<String> job = pickJob(village, counts, cap, level.getRandom(), now);
			if (job.isEmpty()) break; // every station is full, or just took someone
			if (assign(level, village, villager, job.get())) {
				registry.setWorker(village, villager.getUUID(), job.get());
				counts.merge(job.get(), 1, Integer::sum);
				LAST_HIRE.put(village.id() + "/" + job.get(), now);
			}
		}
	}

	/** Frees the slot of a villager that died or turned into something else. */
	public static void onVillagerGone(ServerLevel level, Villager villager) {
		JOBLESS_SINCE.remove(villager.getUUID());
		VillageRegistry.get(level).removeWorkerEverywhere(villager.getUUID());
	}

	/** Makes one villager unemployed (0.19.0: its job had no station for a day). */
	private static boolean release(ServerLevel level, Villager villager) {
		Optional<Holder.Reference<VillagerProfession>> none = level.registryAccess()
				.lookupOrThrow(Registries.VILLAGER_PROFESSION).get(VillagerProfession.NONE);
		if (none.isEmpty()) return false;
		villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
		villager.setVillagerData(villager.getVillagerData().withProfession(none.get()));
		villager.refreshBrain(level);
		return true;
	}

	/** Forgets the job-release timers when the server stops. */
	public static void reset() {
		JOBLESS_SINCE.clear();
		LAST_HIRE.clear();
	}

	/** An active station with free slots; empty stations first, then the least staffed. Ties are random. */
	private static Optional<String> pickJob(Village village, Map<String, Integer> counts, int cap, RandomSource random,
			long now) {
		long delay = (long) (VRConfig.get().stations.hireDelayHours * 1000);
		List<String> best = new ArrayList<>();
		int bestCount = Integer.MAX_VALUE;
		for (Map.Entry<String, ProfessionStation> e : village.professions().entrySet()) {
			if (!e.getValue().active()) continue;
			int n = counts.getOrDefault(e.getKey(), 0);
			if (n >= cap) continue;
			// 0.20.0: a station that just took a worker waits a while before the next one.
			Long last = LAST_HIRE.get(village.id() + "/" + e.getKey());
			if (last != null && now - last < delay) continue;
			if (n < bestCount) {
				best.clear();
				bestCount = n;
			}
			if (n == bestCount) best.add(e.getKey());
		}
		if (best.isEmpty()) return Optional.empty();
		return Optional.of(best.get(random.nextInt(best.size())));
	}

	private static boolean assign(ServerLevel level, Village village, Villager villager, String professionKey) {
		Optional<Holder.Reference<VillagerProfession>> profession = professionHolder(level, professionKey);
		if (profession.isEmpty()) {
			VillageRework.LOGGER.warn("No villager profession matches station {}; skipping", professionKey);
			return false;
		}
		villager.setVillagerData(villager.getVillagerData().withProfession(profession.get()));
		villager.refreshBrain(level);
		pointAtStation(level, village, villager, professionKey);
		level.broadcastEntityEvent(villager, (byte) 14); // happy-villager particles
		return true;
	}

	private static void pointAtStation(ServerLevel level, Village village, Villager villager, String professionKey) {
		Optional<BlockPos> station = village.profession(professionKey).flatMap(ProfessionStation::stationPos);
		if (station.isEmpty()) return;
		GlobalPos target = GlobalPos.of(level.dimension(), station.get());
		Optional<GlobalPos> current = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
		if (current.isEmpty() || !current.get().equals(target)) {
			villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, target);
		}
	}

	/** Station keys are workstation ids; vanilla professions use the same ids ("minecraft:librarian"). */
	private static Optional<Holder.Reference<VillagerProfession>> professionHolder(ServerLevel level, String key) {
		Identifier id = Identifier.tryParse(key);
		if (id == null) return Optional.empty();
		return level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION)
				.get(ResourceKey.create(Registries.VILLAGER_PROFESSION, id));
	}

	// ---------------------------------------------------------------- disbanding

	/** Every resident except nitwits loses its profession. Returns how many were reset. */
	public static int releaseAll(ServerLevel level, Village village) {
		Optional<Holder.Reference<VillagerProfession>> none = level.registryAccess()
				.lookupOrThrow(Registries.VILLAGER_PROFESSION).get(VillagerProfession.NONE);
		if (none.isEmpty()) return 0;
		int reset = 0;
		for (Villager villager : VillageManager.villagersInside(level, village)) {
			if (isNitwit(villager) || isUnemployed(villager)) continue;
			villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
			villager.setVillagerData(villager.getVillagerData().withProfession(none.get()));
			villager.refreshBrain(level);
			reset++;
		}
		// The village is about to be removed, but clear its roster anyway in case it isn't.
		for (UUID id : List.copyOf(village.workers().keySet())) {
			VillageRegistry.get(level).removeWorker(village, id);
		}
		return reset;
	}
}
