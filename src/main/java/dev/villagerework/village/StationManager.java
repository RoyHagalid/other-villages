package dev.villagerework.village;

import dev.villagerework.VillageRework;
import dev.villagerework.config.VRConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Profession stations: which workstation acts for which profession in a village.
 *
 * A "profession" is identified by its workstation's point-of-interest id,
 * e.g. "minecraft:librarian" for a lectern. That covers all 13 vanilla
 * workstations, and modded ones that register a job-site POI.
 */
public final class StationManager {
	private StationManager() {
	}

	// ---------------------------------------------------------------- lookups

	/** The profession key of the workstation at this position, if it is one. */
	public static Optional<String> professionAt(ServerLevel level, BlockPos pos) {
		return level.getPoiManager().getType(pos)
				.filter(holder -> holder.is(PoiTypeTags.ACQUIRABLE_JOB_SITE))
				.flatMap(holder -> holder.unwrapKey())
				.map(key -> key.identifier().toString());
	}

	/** "minecraft:librarian" → "Librarian". */
	public static String displayName(String professionKey) {
		String path = professionKey.contains(":") ? professionKey.substring(professionKey.indexOf(':') + 1) : professionKey;
		if (path.equals("leatherworker")) return "Tanner"; // shorter, and fits the station panel's title
		String[] words = path.replace('_', ' ').split(" ");
		StringBuilder sb = new StringBuilder();
		for (String w : words) {
			if (w.isEmpty()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
		}
		return sb.toString();
	}

	/** Accepts "librarian" or "minecraft:librarian". */
	public static String normalizeKey(String input) {
		String s = input.toLowerCase(Locale.ROOT).trim();
		return s.contains(":") ? s : "minecraft:" + s;
	}

	/** Why a workstation can or can't be donated. */
	public enum DonateCheck {
		OK, NOT_A_WORKSTATION, TAKE_HOME, NO_VILLAGE, DORMANT, ALREADY_STATION, PROFESSION_TAKEN
	}

	public record Donation(DonateCheck check, Optional<Village> village, Optional<String> profession) {
	}

	public static Donation checkDonation(ServerLevel level, BlockPos pos) {
		Optional<String> profession = professionAt(level, pos);
		if (profession.isEmpty()) return new Donation(DonateCheck.NOT_A_WORKSTATION, Optional.empty(), Optional.empty());

		VillageRegistry registry = VillageRegistry.get(level);
		Optional<Village> village = registry.containing(pos, VRConfig.get().village.radius);
		if (village.isEmpty()) return new Donation(DonateCheck.NO_VILLAGE, Optional.empty(), profession);
		// A bought take-home keeps its own tier and can't become a village's station.
		if (dev.villagerework.perk.TakeHomes.isTakeHome(level, pos)) return new Donation(DonateCheck.TAKE_HOME, village, profession);
		Village v = village.get();
		if (v.dormant()) return new Donation(DonateCheck.DORMANT, village, profession);
		if (v.professionAtStation(pos).isPresent()) return new Donation(DonateCheck.ALREADY_STATION, village, profession);
		boolean taken = v.profession(profession.get()).map(ProfessionStation::active).orElse(false);
		if (taken) return new Donation(DonateCheck.PROFESSION_TAKEN, village, profession);
		return new Donation(DonateCheck.OK, village, profession);
	}

	// ---------------------------------------------------------------- changes

	/** Donates the workstation at {@code pos}. Returns true on success. */
	public static boolean donate(ServerLevel level, BlockPos pos, ServerPlayer donor) {
		Donation d = checkDonation(level, pos);
		if (d.check() != DonateCheck.OK) return false;
		Village village = d.village().orElseThrow();
		String profession = d.profession().orElseThrow();

		boolean resumed = village.profession(profession).map(ProfessionStation::hasProgress).orElse(false);
		VillageRegistry.get(level).activateStation(village, profession, pos);
		// Work starts at the next gathering; days without a station aren't caught up on.
		VillageRegistry.get(level).setLastWorkDay(village, profession, WorkCycle.currentWorkDay(level));
		// The villagers say what they need right away, so the first evening can count.
		if (!village.profession(profession).map(ProfessionStation::hasOpenRequest).orElse(false)) {
			WorkCycle.newRequest(level, VillageRegistry.get(level), village, profession);
		}
		celebrate(level, pos);

		if (donor != null) {
			ProfessionStation p = village.profession(profession).orElseThrow();
			String text = resumed
					? "The " + displayName(profession) + " station of " + village.name() + " is back, at level " + p.level() + "."
					: "You donated a " + displayName(profession) + " station to " + village.name() + ".";
			donor.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GOLD));
		}
		VillageRework.LOGGER.info("{} station donated to {} at {}", displayName(profession), village.name(), pos.toShortString());
		return true;
	}

	/** Called right after a player breaks a block. */
	public static void onBlockBroken(ServerLevel level, BlockPos pos, ServerPlayer player) {
		VillageRegistry registry = VillageRegistry.get(level);
		Optional<Village> village = registry.byStation(pos);
		if (village.isEmpty()) return;
		Village v = village.get();
		v.professionAtStation(pos).ifPresent(profession -> {
			dropInventory(level, registry, v, profession, pos);
			registry.deactivateStation(v, profession);
			if (player != null) {
				player.sendSystemMessage(Component.literal("You removed the " + displayName(profession) + " station of "
						+ v.name() + ". The village remembers its level; donate another one to continue.")
						.withStyle(ChatFormatting.YELLOW));
			}
		});
	}

	/**
	 * Clears stations whose block is gone or changed (explosions, pistons, commands).
	 * Only checks stations in loaded chunks.
	 */
	static void validate(ServerLevel level, VillageRegistry registry, Village village) {
		List<String> lost = new ArrayList<>();
		for (Map.Entry<String, ProfessionStation> e : village.professions().entrySet()) {
			Optional<BlockPos> pos = e.getValue().stationPos();
			if (pos.isEmpty() || !level.isLoaded(pos.get())) continue;
			if (!professionAt(level, pos.get()).map(e.getKey()::equals).orElse(false)) {
				lost.add(e.getKey());
			}
		}
		for (String profession : lost) {
			village.profession(profession).flatMap(ProfessionStation::stationPos).ifPresent(pos -> {
				dropInventory(level, registry, village, profession, pos);
				dev.villagerework.perk.LecternMessages.forget(pos); // e.g. a lectern blown up
			});
			registry.deactivateStation(village, profession);
			VillageRework.LOGGER.info("{} station of {} is gone", displayName(profession), village.name());
		}
	}

	/** Natural villages: donate the generated workstations once, one per profession, closest to the bell first. */
	static void seedNaturalStations(ServerLevel level, VillageRegistry registry, Village village) {
		if (village.stationsSeeded() || !village.natural()) return;
		if (!VRConfig.get().stations.autoDonateNaturalStations) {
			registry.markStationsSeeded(village);
			return;
		}
		int radius = VRConfig.get().village.radius;
		// Wait until the whole village is loaded (0.20.3). Workstations in chunks that aren't loaded yet have no
		// POI to find, and seeding only happens once: a player arriving at the edge (or by teleport) used to leave
		// the village with no stations at all.
		if (!wholeVillageLoaded(level, village.bellPos(), radius)) return;
		List<BlockPos> sites = VillageManager.columnPoi(level, holder -> holder.is(PoiTypeTags.ACQUIRABLE_JOB_SITE),
						village.bellPos(), radius)
				// Where two villages' columns overlap, a workstation belongs to the nearer bell.
				.filter(pos -> registry.containing(pos, radius).map(owner -> owner == village).orElse(false))
				.sorted(Comparator.comparingDouble(pos -> pos.distSqr(village.bellPos())))
				.toList();

		Set<String> done = new HashSet<>();
		for (BlockPos pos : sites) {
			if (!level.isLoaded(pos)) continue;
			Optional<String> profession = professionAt(level, pos);
			if (profession.isEmpty() || dev.villagerework.perk.TakeHomes.isTakeHome(level, pos)) continue;
			if (!done.add(profession.get())) continue;
			if (village.profession(profession.get()).map(ProfessionStation::active).orElse(false)) continue;
			registry.activateStation(village, profession.get(), pos);
			registry.setLastWorkDay(village, profession.get(), WorkCycle.currentWorkDay(level));
			WorkCycle.newRequest(level, registry, village, profession.get());
			// A natural village has been working for a while: start part of the way to level 2.
			VRConfig.Stations cfg = VRConfig.get().stations;
			double fraction = cfg.naturalStartProgressMin
					+ level.getRandom().nextDouble() * (cfg.naturalStartProgressMax - cfg.naturalStartProgressMin);
			registry.setProfessionLevel(village, profession.get(), 1,
					Math.floor(fraction * cfg.progressToFillByLevel[0]));
		}
		registry.markStationsSeeded(village);
		VillageRework.LOGGER.info("Village {} starts with {} station(s)", village.name(), done.size());
	}

	/** Whether every chunk within {@code radius} blocks of the bell is loaded. */
	private static boolean wholeVillageLoaded(ServerLevel level, BlockPos bell, int radius) {
		int minX = (bell.getX() - radius) >> 4, maxX = (bell.getX() + radius) >> 4;
		int minZ = (bell.getZ() - radius) >> 4, maxZ = (bell.getZ() + radius) >> 4;
		for (int cx = minX; cx <= maxX; cx++) {
			for (int cz = minZ; cz <= maxZ; cz++) {
				if (!level.isLoaded(new BlockPos(cx << 4, bell.getY(), cz << 4))) return false;
			}
		}
		return true;
	}

	/**
	 * Supplies and goods spill out where the station was, like any container being broken. So do the
	 * fisherman barrel's extra rows (0.14), so nothing stays hidden in the village.
	 */
	public static void dropInventory(ServerLevel level, VillageRegistry registry, Village village, String profession, BlockPos pos) {
		StationInventory inv = registry.inventory(village, profession);
		if (!inv.isEmpty()) {
			Containers.dropContents(level, pos, inv);
			inv.clearContent();
			inv.setChanged();
		}
		net.minecraft.world.Container extra = registry.extraStorage(village, profession);
		if (!extra.isEmpty()) {
			Containers.dropContents(level, pos, extra);
			extra.clearContent();
			extra.setChanged();
		}
	}

	// ---------------------------------------------------------------- visuals

	static void drawGlow(ServerLevel level, Village village) {
		for (ProfessionStation p : village.professions().values()) {
			p.stationPos().ifPresent(pos -> {
				if (!level.isLoaded(pos)) return;
				level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
						3, 0.35, 0.2, 0.35, 0.3);
			});
		}
	}

	private static void celebrate(ServerLevel level, BlockPos pos) {
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
				12, 0.5, 0.4, 0.5, 0.0);
	}
}
