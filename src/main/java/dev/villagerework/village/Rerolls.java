package dev.villagerework.village;

import dev.villagerework.config.VRConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * Buying different rewards: pay emeralds at the station and the villagers swap everything still waiting
 * in the unlocked reward slots for new picks from the same level's pool. Take out what you want to keep
 * first; only what's left in the slots is rerolled.
 *
 * - The price is per station and doubles with each reroll there (1, 2, 4, 8 ... by default). It starts
 *   over at the station's sunrise (also /vr day sunrise). If an enchanted book is among the rewards, the
 *   book price applies (3, 6, 12 ...). The emeralds are spent, not donated.
 * - Each slot is rerolled on its own and keeps its size relative to a full day's delivery: a slot holding
 *   two days' worth of bread becomes two days' worth of the new good (never more than one stack).
 * - Plain goods never reroll into themselves; varied goods (books, colors) can come back as a different variant.
 */
public final class Rerolls {
	private Rerolls() {
	}

	/** Emeralds the next reroll costs, or -1 if there is nothing to reroll. */
	public static int cost(Village village, String profession) {
		VRConfig cfg = VRConfig.get();
		if (!cfg.rerolls.enabled) return -1;
		Optional<ProfessionStation> p = village.profession(profession);
		Optional<StationTypes.Definition> def = StationTypes.get(profession);
		if (p.isEmpty() || def.isEmpty()) return -1;
		boolean any = false;
		boolean book = false;
		for (int i = 0; i < unlocked(p.get()); i++) {
			ItemStack stack = p.get().inventory().getItem(StationInventory.REWARD_SLOTS[i]);
			if (stack.isEmpty() || def.get().goodFor(stack).isEmpty()) continue;
			any = true;
			book |= stack.is(Items.ENCHANTED_BOOK);
		}
		if (!any) return -1;
		int base = book ? cfg.rerolls.bookBaseCost : cfg.rerolls.baseCost;
		long price = (long) base << Math.min(30, p.get().rerolls());
		return (int) Math.min(cfg.rerolls.maxCost, price);
	}

	/** Pays and rerolls. Returns false (and changes nothing) if it isn't possible or the player can't pay. */
	public static boolean tryReroll(ServerLevel level, Village village, String profession, ServerPlayer player) {
		int price = cost(village, profession);
		if (price < 0) return false;
		boolean free = player.hasInfiniteMaterials();
		Inventory inventory = player.getInventory();
		if (!free && inventory.countItem(Items.EMERALD) < price) return false;

		ProfessionStation p = village.profession(profession).orElseThrow();
		StationTypes.Definition def = StationTypes.get(profession).orElseThrow();
		VillageRegistry registry = VillageRegistry.get(level);
		StationInventory inv = registry.inventory(village, profession);
		int stationLevel = p.level();

		if (!free) take(inventory, price);
		for (int i = 0; i < unlocked(p); i++) {
			int slot = StationInventory.REWARD_SLOTS[i];
			ItemStack current = inv.getItem(slot);
			if (current.isEmpty()) continue;
			Optional<StationTypes.Good> old = def.goodFor(current);
			if (old.isEmpty()) continue; // something foreign: leave it alone
			StationTypes.Good picked = def.pickGood(level.getRandom(), stationLevel, old.get().varied() ? null : old.get());
			double days = (double) current.getCount() / fullAmount(old.get(), stationLevel);
			StationTypes.ORIGIN.set(village.bellPos());
			ItemStack fresh;
			try {
				fresh = picked.create(level, stationLevel);
			} finally {
				StationTypes.ORIGIN.remove();
			}
			int count = (int) Math.max(1, Math.min(StationTypes.cap(fresh),
					Math.round(fullAmount(picked, stationLevel) * days)));
			inv.setItem(slot, fresh.copyWithCount(count));
		}
		registry.recordReroll(village, profession);

		p.stationPos().ifPresent(pos -> effects(level, pos));
		return true;
	}

	private static int unlocked(ProfessionStation p) {
		return Math.min(StationInventory.REWARD_SLOTS.length, VRConfig.get().rewardSlots(p.level()));
	}

	/** One day's delivery of {@code good} at full contribution, no boost. */
	private static int fullAmount(StationTypes.Good good, int stationLevel) {
		VRConfig.Stations cfg = VRConfig.get().stations;
		int levelIndex = Math.max(0, Math.min(stationLevel, cfg.maxLevel) - 1);
		return Math.max(1, (int) Math.round(good.baseAmount() * cfg.outputMultiplierByLevel[levelIndex]));
	}

	private static void take(Inventory inventory, int count) {
		for (int i = 0; i < inventory.getContainerSize() && count > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.is(Items.EMERALD)) continue;
			int n = Math.min(count, stack.getCount());
			inventory.removeItem(i, n);
			count -= n;
		}
		inventory.setChanged();
	}

	private static void effects(ServerLevel level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.6F, 1.0F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
				4, 0.3, 0.2, 0.3, 0.0);
	}
}
