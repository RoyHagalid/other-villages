package dev.villagerework.perk;

import dev.villagerework.village.ProfessionStation;
import dev.villagerework.village.Village;
import dev.villagerework.village.VillageRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Station perks (0.13+): what an upgraded workstation does better than a plain one.
 *
 * Every perk asks one question: "what perk level does the block at this position have?" Today the
 * answer comes from the village: an active station of the matching profession in a village that isn't
 * dormant has its station level (1 = plain vanilla, perks start at 2). Otherwise a bought take-home block
 * (TakeHomes, 0.17) answers with its own tier.
 *
 * Server side only; on the client (or anywhere else) the answer is 0, which means vanilla behavior.
 */
public final class Perks {
	public static final String FARMER = "minecraft:farmer";
	public static final String BUTCHER = "minecraft:butcher";
	public static final String ARMORER = "minecraft:armorer";
	public static final String CLERIC = "minecraft:cleric";
	public static final String LEATHERWORKER = "minecraft:leatherworker";
	public static final String MASON = "minecraft:mason";
	public static final String SHEPHERD = "minecraft:shepherd";
	public static final String WEAPONSMITH = "minecraft:weaponsmith";
	public static final String TOOLSMITH = "minecraft:toolsmith";
	public static final String CARTOGRAPHER = "minecraft:cartographer";
	public static final String FISHERMAN = "minecraft:fisherman";
	public static final String FLETCHER = "minecraft:fletcher";
	public static final String LIBRARIAN = "minecraft:librarian";

	private Perks() {
	}

	/** The perk level of the block at {@code pos} for {@code profession}'s station, or 0 if it isn't one. */
	public static int level(Level level, BlockPos pos, String profession) {
		if (!(level instanceof ServerLevel server) || pos == null) return 0;
		if (!dev.villagerework.config.VRConfig.get().perks.enabled) return 0;
		VillageRegistry registry = VillageRegistry.get(server);
		Optional<Village> village = registry.byStation(pos);
		if (village.isPresent() && !village.get().dormant()
				&& village.get().professionAtStation(pos).map(profession::equals).orElse(false)) {
			return village.get().profession(profession).map(ProfessionStation::level).orElse(0);
		}
		// Not a village station: a bought take-home block has its own tier.
		return TakeHomes.tier(server, pos, profession);
	}

	/** True if the block at {@code pos} is {@code profession}'s station at {@code minLevel} or higher. */
	public static boolean has(Level level, BlockPos pos, String profession, int minLevel) {
		return level(level, pos, profession) >= minLevel;
	}
}
