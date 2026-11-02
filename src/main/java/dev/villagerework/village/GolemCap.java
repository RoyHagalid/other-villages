package dev.villagerework.village;

import dev.villagerework.config.VRConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.Optional;

/**
 * A ceiling on iron golems per village (0.19.0).
 *
 * Vanilla villagers summon a golem when three of them panic together (hurt by anyone, or a hostile mob nearby)
 * or five gossip together, as long as they slept within the last day and saw no golem in the last 30 seconds.
 * In a big village most golems are out of sight of a given group, so they kept summoning more. Inside a
 * village, villagers now only summon one while the village has fewer golems than it allows:
 * one per {@code village.residentsPerGolem} residents, between {@code golemMin} and {@code golemMax}.
 * Golems players build don't count against anything; outside villages vanilla rules apply.
 */
public final class GolemCap {
	private GolemCap() {
	}

	/** Whether this villager may summon a golem now (checked only when it otherwise would). */
	public static boolean allows(ServerLevel level, Villager villager) {
		VRConfig.Village cfg = VRConfig.get().village;
		Optional<Village> found = VillageRegistry.get(level).containing(villager.blockPosition(), cfg.radius);
		if (found.isEmpty()) return true;
		Village village = found.get();
		int residents = VillageManager.villagersInside(level, village).size();
		int allowed = Math.max(cfg.golemMin, Math.min(cfg.golemMax, residents / Math.max(1, cfg.residentsPerGolem)));
		int golems = level.getEntitiesOfClass(IronGolem.class, VillageManager.column(level, village.bellPos(), cfg.radius),
				g -> g.isAlive() && village.contains(g.blockPosition(), cfg.radius)).size();
		return golems < allowed;
	}
}
