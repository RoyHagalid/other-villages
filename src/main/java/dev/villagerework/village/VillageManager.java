package dev.villagerework.village;

import dev.villagerework.VillageRework;
import dev.villagerework.config.VRConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps villages alive while the game runs:
 *  - converts natural village bells into center bells when a player comes near
 *  - notices when a center bell is gone (village goes dormant) or replaced (village wakes up)
 *  - makes every villager inside a village gather at its center bell
 *  - draws the center bell glow and the border display
 */
public final class VillageManager {
	private VillageManager() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(VillageManager::onLevelTick);
	}

	private static void onLevelTick(ServerLevel level) {
		BorderDisplay.tick(level);

		VRConfig cfg = VRConfig.get();
		long time = level.getGameTime();
		if (cfg.village.centerBellGlow && time % 20 == 0) {
			drawGlow(level);
		}
		if (time % cfg.village.updateIntervalTicks != 0) return;

		VillageRegistry registry = VillageRegistry.get(level);
		discoverNaturalVillages(level, registry);
		for (Village village : registry.all()) {
			updateVillage(level, registry, village);
		}
	}

	private static void updateVillage(ServerLevel level, VillageRegistry registry, Village village) {
		StationManager.validate(level, registry, village);
		if (!level.isLoaded(village.bellPos())) return;
		checkBell(level, registry, village);
		if (!village.dormant()) {
			List<Villager> residents = villagersInside(level, village);
			syncMembers(level, village, residents);
			StationManager.seedNaturalStations(level, registry, village);
			WorkforceManager.update(level, village, residents);
			WorkCycle.update(level, registry, village);
			BellRings.update(level, village);
		}
	}

	/** Runs one update immediately (used by /vr village sync). */
	public static void forceUpdate(ServerLevel level) {
		VillageRegistry registry = VillageRegistry.get(level);
		discoverNaturalVillages(level, registry);
		for (Village village : registry.all()) {
			updateVillage(level, registry, village);
		}
	}

	// ---------------------------------------------------------------- natural villages

	private static void discoverNaturalVillages(ServerLevel level, VillageRegistry registry) {
		VRConfig cfg = VRConfig.get();
		PoiManager poi = level.getPoiManager();
		for (ServerPlayer player : level.players()) {
			List<BlockPos> bells = poi.getInRange(holder -> holder.is(PoiTypes.MEETING),
							player.blockPosition(), cfg.village.discoveryRadius, PoiManager.Occupancy.ANY)
					.map(PoiRecord::getPos)
					.toList();
			for (BlockPos found : bells) {
				if (registry.byBell(found).isPresent()) continue;
				StructureStart structure = level.structureManager().getStructureWithPieceAt(found, StructureTags.VILLAGE);
				if (!structure.isValid()) continue;

				// One village per generated structure (0.18.5). Big villages (e.g. from a size datapack) can hold
				// several bells far enough apart to pass the distance check, which used to found two villages in
				// one structure. Skip the structure if any village already sits inside its footprint.
				BoundingBox box = structure.getBoundingBox();
				if (registry.all().stream().anyMatch(v -> insideFootprint(box, v.bellPos()))) continue;

				// Anchor on the bell nearest the town center (the structure's first piece), not on whichever
				// bell a player happened to walk up to first. Wait until the town center is loaded.
				BlockPos town = structure.getPieces().getFirst().getBoundingBox().getCenter();
				if (!level.isLoaded(town)) continue;
				BlockPos bell = townBell(level, poi, box, town).orElse(found);

				if (registry.byBell(bell).isPresent() || registry.wasDisbanded(bell)) continue;
				if (registry.nearestWithin(bell, cfg.village.minDistanceBetweenVillages).isPresent()) continue;

				Village village = Village.create(VillageNames.generate(bell), bell, true, level.getGameTime(), null);
				registry.add(village);
				VillageRework.LOGGER.info("Discovered natural village {} at {}", village.name(), bell.toShortString());
			}
		}
	}

	/** Whether pos lies inside a structure's footprint (horizontally, with a small margin). */
	private static boolean insideFootprint(BoundingBox box, BlockPos pos) {
		int margin = 8;
		return pos.getX() >= box.minX() - margin && pos.getX() <= box.maxX() + margin
				&& pos.getZ() >= box.minZ() - margin && pos.getZ() <= box.maxZ() + margin;
	}

	/** The bell inside the structure that is closest to its town center, if one exists. */
	private static Optional<BlockPos> townBell(ServerLevel level, PoiManager poi, BoundingBox box, BlockPos town) {
		int reach = Math.max(box.getXSpan(), box.getZSpan()) / 2 + 8;
		return poi.getInRange(holder -> holder.is(PoiTypes.MEETING), town, reach, PoiManager.Occupancy.ANY)
				.map(PoiRecord::getPos)
				.filter(pos -> insideFootprint(box, pos))
				.filter(pos -> level.getBlockState(pos).is(Blocks.BELL))
				.min(Comparator.comparingDouble(pos -> pos.distSqr(town)));
	}

	public static boolean isInNaturalVillage(ServerLevel level, BlockPos pos) {
		return level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE).isValid();
	}

	// ---------------------------------------------------------------- bell presence

	private static void checkBell(ServerLevel level, VillageRegistry registry, Village village) {
		if (level.getBlockState(village.bellPos()).is(Blocks.BELL)) {
			if (village.dormant()) {
				registry.setDormant(village, false);
				announce(level, village, village.name() + " has its bell back.", ChatFormatting.GREEN);
			}
			return;
		}

		// The center bell is gone. Another bell inside the village can take over, which also lets
		// players move their village on purpose. The new center must keep the usual distance to
		// other villages, so a village can't be moved into (or right next to) another one.
		int radius = VRConfig.get().village.radius;
		Optional<BlockPos> replacement = columnPoi(level, holder -> holder.is(PoiTypes.MEETING), village.bellPos(), radius)
				.filter(pos -> registry.byBell(pos).isEmpty())
				.filter(pos -> tooCloseTo(registry, village, pos).isEmpty())
				.filter(pos -> level.getBlockState(pos).is(Blocks.BELL))
				.min(Comparator.comparingDouble(pos -> pos.distSqr(village.bellPos())));

		if (replacement.isPresent()) {
			registry.moveBell(village, replacement.get());
			if (village.dormant()) {
				registry.setDormant(village, false);
				announce(level, village, village.name() + " has a new center bell.", ChatFormatting.GREEN);
			}
			VillageRework.LOGGER.info("Village {} moved its center bell to {}", village.name(), replacement.get().toShortString());
		} else if (!village.dormant()) {
			registry.setDormant(village, true);
			announce(level, village, "The bell of " + village.name()
					+ " is gone. The village is dormant until a bell is placed inside it again.", ChatFormatting.YELLOW);
			VillageRework.LOGGER.info("Village {} went dormant (bell missing)", village.name());
		}
	}

	/**
	 * Another village that would be closer than the minimum distance if {@code village}
	 * moved its center bell to {@code pos}.
	 */
	public static Optional<Village> tooCloseTo(VillageRegistry registry, Village village, BlockPos pos) {
		int minDistance = VRConfig.get().village.minDistanceBetweenVillages;
		return registry.all().stream()
				.filter(other -> other != village && other.horizontalDistance(pos) < minDistance)
				.min(Comparator.comparingDouble(other -> other.horizontalDistance(pos)));
	}

	private static void announce(ServerLevel level, Village village, String text, ChatFormatting color) {
		int radius = VRConfig.get().village.radius + 16;
		for (ServerPlayer player : level.players()) {
			if (village.contains(player.blockPosition(), radius)) {
				player.sendSystemMessage(Component.literal(text).withStyle(color));
			}
		}
	}

	// ---------------------------------------------------------------- members

	/** Villagers inside the village are pointed at the center bell as their meeting point. */
	private static void syncMembers(ServerLevel level, Village village) {
		syncMembers(level, village, villagersInside(level, village));
	}

	private static void syncMembers(ServerLevel level, Village village, List<Villager> residents) {
		GlobalPos center = GlobalPos.of(level.dimension(), village.bellPos());
		PoiManager poi = level.getPoiManager();
		for (Villager villager : residents) {
			Brain<Villager> brain = villager.getBrain();
			Optional<GlobalPos> current = brain.getMemory(MemoryModuleType.MEETING_POINT);
			if (current.isPresent() && current.get().equals(center)) continue;

			// Give back the claim on any other bell so the vanilla system stays consistent.
			current.ifPresent(old -> {
				if (old.dimension().equals(level.dimension())
						&& poi.exists(old.pos(), holder -> holder.is(PoiTypes.MEETING))) {
					poi.release(old.pos());
				}
			});
			brain.setMemory(MemoryModuleType.MEETING_POINT, center);
		}
	}

	/**
	 * Villagers who belong to this village: inside its border, and closer to this village
	 * than to any other (so overlapping villages never fight over the same villager).
	 */
	public static List<Villager> villagersInside(ServerLevel level, Village village) {
		int radius = VRConfig.get().village.radius;
		VillageRegistry registry = VillageRegistry.get(level);
		return level.getEntitiesOfClass(Villager.class, column(level, village.bellPos(), radius), v -> village.contains(v.blockPosition(), radius)
				&& registry.containing(v.blockPosition(), radius).map(owner -> owner == village).orElse(false));
	}

	/** Villagers whose meeting point is this village's center bell (only counts loaded villagers). */
	public static int countMembers(ServerLevel level, Village village) {
		GlobalPos center = GlobalPos.of(level.dimension(), village.bellPos());
		int count = 0;
		for (Villager villager : villagersInside(level, village)) {
			if (villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT).map(center::equals).orElse(false)) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Who lives in the village and what they're doing right now.
	 *
	 * @param residents     villagers inside the border (all of them belong to this village's bell)
	 * @param withBed       residents whose bed is inside the border
	 * @param gathering     residents currently in their "meet" activity (heading to or standing at the bell)
	 * @param atBell        residents within a few blocks of the bell right now
	 */
	public record Census(int residents, int withBed, int gathering, int atBell) {
		public boolean isGatheringTime() {
			return gathering > 0;
		}
	}

	private static final int AT_BELL_DISTANCE = 8;

	public static Census census(ServerLevel level, Village village) {
		int radius = VRConfig.get().village.radius;
		int residents = 0, withBed = 0, gathering = 0, atBell = 0;
		for (Villager villager : villagersInside(level, village)) {
			residents++;
			Brain<Villager> brain = villager.getBrain();
			boolean bedInside = brain.getMemory(MemoryModuleType.HOME)
					.filter(home -> home.dimension().equals(level.dimension()))
					.map(home -> village.contains(home.pos(), radius))
					.orElse(false);
			if (bedInside) withBed++;
			if (brain.isActive(Activity.MEET)) gathering++;
			if (villager.blockPosition().distSqr(village.bellPos()) <= AT_BELL_DISTANCE * AT_BELL_DISTANCE) atBell++;
		}
		return new Census(residents, withBed, gathering, atBell);
	}

	public static int countPoi(ServerLevel level, BlockPos center, int radius, ResourceKey<PoiType> type) {
		return (int) columnPoi(level, holder -> holder.is(type), center, radius).count();
	}

	// ---------------------------------------------------------------- columns

	/**
	 * Villages are columns: everything within {@code radius} blocks horizontally of the bell belongs to it,
	 * at any height (a bell deep in a ravine still owns the houses on the hill above it).
	 */
	public static AABB column(ServerLevel level, BlockPos center, int radius) {
		return new AABB(center.getX() - radius, level.getMinY(), center.getZ() - radius,
				center.getX() + radius + 1, level.getMaxY() + 1, center.getZ() + radius + 1);
	}

	/** Points of interest (bells, beds, workstations) in the village column around {@code center}. */
	public static java.util.stream.Stream<BlockPos> columnPoi(ServerLevel level,
			java.util.function.Predicate<net.minecraft.core.Holder<PoiType>> type, BlockPos center, int radius) {
		long r2 = (long) radius * radius;
		return level.getPoiManager().getInSquare(type, center, radius, PoiManager.Occupancy.ANY)
				.map(PoiRecord::getPos)
				.filter(pos -> {
					long dx = pos.getX() - center.getX();
					long dz = pos.getZ() - center.getZ();
					return dx * dx + dz * dz <= r2;
				});
	}

	// ---------------------------------------------------------------- founding

	public record FoundingCheck(int villagers, int beds, Optional<Village> tooClose, boolean natural) {
		public boolean enoughVillagers() {
			return villagers >= VRConfig.get().village.foundingMinVillagers;
		}

		public boolean enoughBeds() {
			return beds >= VRConfig.get().village.foundingMinBeds;
		}

		public boolean ok() {
			return enoughVillagers() && enoughBeds() && tooClose.isEmpty();
		}
	}

	public static FoundingCheck checkFounding(ServerLevel level, BlockPos bell) {
		VRConfig cfg = VRConfig.get();
		int radius = cfg.village.radius;
		int villagers = level.getEntitiesOfClass(Villager.class, column(level, bell, radius), v -> {
			long dx = v.blockPosition().getX() - bell.getX();
			long dz = v.blockPosition().getZ() - bell.getZ();
			return dx * dx + dz * dz <= (long) radius * radius;
		}).size();
		int beds = countPoi(level, bell, radius, PoiTypes.HOME);
		Optional<Village> tooClose = VillageRegistry.get(level).nearestWithin(bell, cfg.village.minDistanceBetweenVillages);
		return new FoundingCheck(villagers, beds, tooClose, isInNaturalVillage(level, bell));
	}

	/** Founds a village at this bell. Returns the new village, or empty if the requirements aren't met. */
	public static Optional<Village> found(ServerLevel level, BlockPos bell, UUID founder) {
		if (!level.getBlockState(bell).is(Blocks.BELL)) return Optional.empty();
		VillageRegistry registry = VillageRegistry.get(level);
		if (registry.byBell(bell).isPresent()) return Optional.empty();
		FoundingCheck check = checkFounding(level, bell);
		if (!check.ok()) return Optional.empty();

		Village village = Village.create(VillageNames.generate(bell), bell, check.natural(), level.getGameTime(), founder);
		registry.add(village);
		registry.forgetDisbanded(bell);
		// A player-founded village starts empty, even inside a generated village: the player
		// chooses which stations to donate (this is what makes disband-and-refound a clean restart).
		registry.markStationsSeeded(village);
		syncMembers(level, village);
		VillageRework.LOGGER.info("Village {} founded at {}", village.name(), bell.toShortString());
		return Optional.of(village);
	}

	// ---------------------------------------------------------------- glow

	private static void drawGlow(ServerLevel level) {
		VillageRegistry registry = VillageRegistry.get(level);
		if (registry.size() == 0) return;
		for (Village village : registry.all()) {
			if (village.dormant() || !level.isLoaded(village.bellPos())) continue;
			BlockPos bell = village.bellPos();
			boolean playerNear = false;
			for (ServerPlayer player : level.players()) {
				if (player.blockPosition().distSqr(bell) < 48 * 48) {
					playerNear = true;
					break;
				}
			}
			if (!playerNear) continue;
			StationManager.drawGlow(level, village);
			level.sendParticles(ParticleTypes.ENCHANT, bell.getX() + 0.5, bell.getY() + 0.6, bell.getZ() + 0.5,
					8, 0.45, 0.45, 0.45, 0.4);
		}
	}
}
