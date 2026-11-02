package dev.villagerework.village;

import dev.villagerework.config.VRConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * Phase 7: emeralds.
 *
 * Throw emeralds on the ground inside a village and its villagers pick them up for the bell.
 * The bell keeps up to 64 a day (more are taken but wasted, and none can be taken back).
 * At the evening work the pool becomes points, shared evenly by the stations that got some of
 * their request; each point boosts that evening's progress and goods.
 */
public final class EmeraldDonations {
	private EmeraldDonations() {
	}

	/** Villagers want emeralds lying inside an active village. */
	public static boolean wants(ServerLevel level, Villager villager, ItemStack stack) {
		return stack.is(Items.EMERALD) && village(level, villager).isPresent();
	}

	/**
	 * A villager reached an emerald: it goes to the village's bell pool instead of the villager's pockets.
	 * Returns true if handled.
	 */
	public static boolean pickUp(ServerLevel level, Villager villager, ItemEntity item) {
		ItemStack stack = item.getItem();
		if (!stack.is(Items.EMERALD)) return false;
		Optional<Village> village = village(level, villager);
		if (village.isEmpty()) return false;

		int count = stack.getCount();
		Entity thrower = item.getOwner();
		VillageRegistry.get(level).addEmeralds(village.get(), count,
				thrower instanceof ServerPlayer player ? player.getUUID() : null, VRConfig.get().emeralds.maxPerDay);

		villager.take(item, count);
		item.discard();
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.8, villager.getZ(),
				5, 0.3, 0.3, 0.3, 0.0);
		level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
		return true;
	}

	private static Optional<Village> village(ServerLevel level, Villager villager) {
		return VillageRegistry.get(level)
				.containing(villager.blockPosition(), VRConfig.get().village.radius)
				.filter(v -> !v.dormant());
	}
}
