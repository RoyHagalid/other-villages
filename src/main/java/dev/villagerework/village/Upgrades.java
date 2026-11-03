package dev.villagerework.village;

import dev.villagerework.VillageRework;
import dev.villagerework.config.VRConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Phase 6: leveling a station up.
 *
 * A station can go up a level when its progress bar is full and the milestone item
 * for the next level is in the milestone slot. Pressing Upgrade uses the item, raises
 * the level, empties the bar, drops a little XP and celebrates. Requests and goods
 * grow with the level (see the per-level lists in the config).
 */
public final class Upgrades {
	private Upgrades() {
	}

	public enum Check {
		OK, MAX_LEVEL, BAR_NOT_FULL, NO_MILESTONE
	}

	public static Check check(Village village, String profession) {
		VRConfig.Stations cfg = VRConfig.get().stations;
		Optional<ProfessionStation> p = village.profession(profession);
		Optional<StationTypes.Definition> def = StationTypes.get(profession);
		if (p.isEmpty() || def.isEmpty()) return Check.NO_MILESTONE;
		if (p.get().level() >= cfg.maxLevel) return Check.MAX_LEVEL;
		if (p.get().progress() < fill(p.get())) return Check.BAR_NOT_FULL;
		Optional<Item> milestone = def.get().milestoneFor(p.get().level() + 1);
		if (milestone.isEmpty() || !p.get().inventory().getItem(StationInventory.MILESTONE_SLOT).is(milestone.get())) {
			return Check.NO_MILESTONE;
		}
		return Check.OK;
	}

	/** Progress needed to fill the bar at this station's current level. */
	public static int fill(ProfessionStation p) {
		VRConfig.Stations cfg = VRConfig.get().stations;
		return cfg.progressToFillByLevel[Math.min(p.level(), cfg.maxLevel) - 1];
	}

	/** Levels the station up if everything is in place. Returns true on success. */
	public static boolean tryUpgrade(ServerLevel level, Village village, String profession, ServerPlayer player) {
		if (check(village, profession) != Check.OK) return false;
		VillageRegistry registry = VillageRegistry.get(level);
		ProfessionStation p = village.profession(profession).orElseThrow();
		StationInventory inv = registry.inventory(village, profession);

		inv.removeItem(StationInventory.MILESTONE_SLOT, 1);
		int newLevel = p.level() + 1;
		registry.setProfessionLevel(village, profession, newLevel, 0);

		// A new level means a new request: give back anything already donated and ask again, sized for the new level.
		ItemStack donated = inv.removeItemNoUpdate(StationInventory.DONATE_SLOT);
		inv.setChanged();
		if (!donated.isEmpty()) {
			if (player != null) {
				// Into the inventory; whatever doesn't fit is dropped at the player's feet.
				if (!player.getInventory().add(donated) && !donated.isEmpty()) {
					Containers.dropItemStack(level, player.getX(), player.getY(), player.getZ(), donated);
				}
			} else p.stationPos().ifPresent(pos -> Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, donated));
		}
		WorkCycle.newRequest(level, registry, village, profession);

		p.stationPos().ifPresent(pos -> celebrate(level, pos));
		VillageRework.LOGGER.info("{} of {} reached level {} ({})", StationManager.displayName(profession),
				village.name(), newLevel, player == null ? "command" : player.getName().getString());
		return true;
	}

	private static void celebrate(ServerLevel level, BlockPos pos) {
		Vec3 top = Vec3.atCenterOf(pos).add(0, 0.7, 0);
		int xp = VRConfig.get().stations.levelUpXp;
		if (xp > 0) ExperienceOrb.award(level, top, xp);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, top.x, top.y, top.z, 20, 0.6, 0.5, 0.6, 0.0);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, top.x, top.y, top.z, 30, 0.4, 0.4, 0.4, 0.3);
		level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8F, 1.0F);
	}
}
