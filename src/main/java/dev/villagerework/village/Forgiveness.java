package dev.villagerework.village;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Villages forgive a player when the player dies or sleeps through the night (0.18.5).
 *
 * Why: iron golems attack anyone whom nearby villagers dislike. Witnessed kills give vanilla "major negative"
 * gossip, villagers spread it among themselves and it only fades about 10 points a day, so after a killing
 * spree golems stayed hostile for many days, through deaths and nights. Forgiving clears the bad gossip about
 * that player (major and minor negative) and calms golems that are after them. The lasting consequence is the
 * village mood: Prosperity's death memories are left alone.
 *
 * Villagers that are unloaded at that moment are forgiven when they next load, for a few days afterwards.
 * Kept in memory only; a server restart forgets pending forgiveness (those villagers then fade the vanilla way).
 */
public final class Forgiveness {
	/** How long villagers loading later are still forgiven for a player (in game ticks, 3 days). */
	private static final long PENDING_TICKS = 3 * 24000L;

	/** Player -> game time until which villagers that load are forgiven for them. */
	private static final Map<UUID, Long> PENDING = new HashMap<>();
	/** Players who just woke up; checked at the end of the tick, once the sky has updated. */
	private static final Set<UUID> WOKE = new HashSet<>();

	private Forgiveness() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) forgive(player);
		});

		// Sleeping through the night: STOP_SLEEPING fires for the night skip as well as for leaving the bed
		// early. The sky brightness only updates later in the tick, so check at the end of the tick.
		EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> {
			if (entity instanceof ServerPlayer player) WOKE.add(player.getUUID());
		});
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (WOKE.isEmpty()) return;
			Iterator<UUID> it = WOKE.iterator();
			while (it.hasNext()) {
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(it.next());
				if (player == null) {
					it.remove();
					continue;
				}
				if (player.level() != level) continue;
				it.remove();
				if (level.isBrightOutside()) forgive(player);
			}
		});

		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (PENDING.isEmpty() || !(entity instanceof Villager villager)) return;
			long now = level.getGameTime();
			PENDING.values().removeIf(until -> until < now);
			for (UUID player : PENDING.keySet()) clearGossip(villager, player);
		});
	}

	/** Clears bad gossip about the player in every loaded villager and calms golems that are after them. */
	public static void forgive(ServerPlayer player) {
		UUID id = player.getUUID();
		MinecraftServer server = player.level().getServer();
		for (ServerLevel level : server.getAllLevels()) {
			for (Villager villager : level.getEntities(EntityTypes.VILLAGER, v -> true)) {
				clearGossip(villager, id);
			}
			for (IronGolem golem : level.getEntities(EntityTypes.IRON_GOLEM, g -> true)) {
				boolean targeting = golem.getTarget() != null && golem.getTarget().getUUID().equals(id);
				boolean hurtBy = golem.getLastHurtByMob() != null && golem.getLastHurtByMob().getUUID().equals(id);
				if (targeting || hurtBy || golem.isAngryAt(player, level)) golem.stopBeingAngry();
			}
		}
		PENDING.put(id, player.level().getGameTime() + PENDING_TICKS);
	}

	private static void clearGossip(Villager villager, UUID player) {
		GossipContainer gossips = villager.getGossips();
		gossips.remove(player, GossipType.MAJOR_NEGATIVE);
		gossips.remove(player, GossipType.MINOR_NEGATIVE);
	}

	/** Forgets pending forgiveness when the server stops. */
	public static void reset() {
		PENDING.clear();
		WOKE.clear();
	}
}
