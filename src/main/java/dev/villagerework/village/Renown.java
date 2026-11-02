package dev.villagerework.village;

import com.mojang.serialization.Codec;
import dev.villagerework.VillageRework;
import dev.villagerework.config.VRConfig;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Renown (0.20.0): what a village works toward once its stations are at the top level.
 *
 * Every evening, each level III station adds its day's contribution (0 to 1: how much of what it needed it got)
 * to the village's renown. Renown fills ranks; each rank costs a little more than the one before
 * ({@code takeHomes.renownFirstRank}, then + {@code renownRankStep} per rank). Each rank lowers the price of the
 * stations the village's workers sell by {@code renownDiscountPerRank} (10%), compounding, never below
 * {@code minPrice}. So it keeps going forever but slows down: 32 emeralds down to 1 is possible, just very long.
 *
 * Players see a bar and the rank on the bell screen, not the numbers.
 */
public final class Renown extends SavedData {
	public static final Codec<Renown> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.DOUBLE)
			.xmap(Renown::new, r -> r.points);

	public static final SavedDataType<Renown> TYPE = new SavedDataType<>(
			VillageRework.id("renown"), Renown::new, CODEC, null);

	private final Map<UUID, Double> points;

	public Renown() {
		this(Map.of());
	}

	private Renown(Map<UUID, Double> points) {
		this.points = new HashMap<>(points);
	}

	public static Renown get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	public static void init() {
		// Nothing to hook: WorkCycle reports the evening work, the bell screen and the shop ask.
	}

	/** A rank and how far the bar toward the next one is filled (0..1). */
	public record Standing(int rank, double fill) {
	}

	/** The evening work of a station at the top level: its contribution (0..1) adds to the village's renown. */
	public static void addWork(ServerLevel level, Village village, double contribution) {
		if (contribution <= 0) return;
		Renown data = get(level);
		data.points.merge(village.id(), contribution, Double::sum);
		data.setDirty();
	}

	public static Standing standing(ServerLevel level, Village village) {
		return standing(get(level).points.getOrDefault(village.id(), 0.0));
	}

	static Standing standing(double total) {
		VRConfig.TakeHomes cfg = VRConfig.get().takeHomes;
		int rank = 0;
		double left = total;
		double cost = cfg.renownFirstRank;
		while (left >= cost && rank < 10000) {
			left -= cost;
			rank++;
			cost = cfg.renownFirstRank + cfg.renownRankStep * rank;
		}
		return new Standing(rank, Math.max(0, Math.min(1, left / cost)));
	}

	/** A take-home price after the village's renown discount. */
	public static int price(ServerLevel level, Village village, int base) {
		VRConfig.TakeHomes cfg = VRConfig.get().takeHomes;
		int rank = standing(level, village).rank();
		double price = base * Math.pow(1 - cfg.renownDiscountPerRank, rank);
		return Math.max(cfg.minPrice, (int) Math.round(price));
	}

	/** Debug: set a village's renown points. */
	public static void set(ServerLevel level, Village village, double value) {
		Renown data = get(level);
		data.points.put(village.id(), Math.max(0, value));
		data.setDirty();
	}

	public static double points(ServerLevel level, Village village) {
		return get(level).points.getOrDefault(village.id(), 0.0);
	}
}
