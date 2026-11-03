package dev.villagerework.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.villagerework.VillageRework;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * All villages in one dimension, saved with the world.
 * Get it with {@link #get(ServerLevel)}; call {@link #setDirty()} after changes.
 */
public final class VillageRegistry extends SavedData {
	public static final Codec<VillageRegistry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Village.CODEC.listOf().fieldOf("villages").forGetter(r -> List.copyOf(r.villages.values())),
			BlockPos.CODEC.listOf().optionalFieldOf("disbanded_natural_bells", List.of())
					.forGetter(r -> List.copyOf(r.disbandedNaturalBells))
	).apply(instance, VillageRegistry::new));

	public static final SavedDataType<VillageRegistry> TYPE = new SavedDataType<>(
			VillageRework.id("villages"),
			VillageRegistry::new,
			CODEC,
			null // no data fixer; Fabric API makes this safe
	);

	private final Map<UUID, Village> villages = new LinkedHashMap<>();
	/** Bells of natural villages a player disbanded, so they aren't rediscovered automatically. */
	private final Set<BlockPos> disbandedNaturalBells = new HashSet<>();

	public VillageRegistry() {
	}

	private VillageRegistry(List<Village> loaded, List<BlockPos> disbanded) {
		for (Village v : loaded) villages.put(v.id(), v);
		disbandedNaturalBells.addAll(disbanded);
	}

	/** Removes a village and all its records. Natural villages won't be rediscovered at this bell. */
	public void disband(Village village) {
		if (villages.remove(village.id()) == null) return;
		if (village.natural()) disbandedNaturalBells.add(village.bellPos().immutable());
		setDirty();
	}

	public boolean wasDisbanded(BlockPos naturalBell) {
		return disbandedNaturalBells.contains(naturalBell);
	}

	/** Lets a disbanded natural bell be rediscovered again (used when a player founds a village there). */
	public void forgetDisbanded(BlockPos bell) {
		if (disbandedNaturalBells.remove(bell)) setDirty();
	}

	/** Any change may change which blocks look upgraded: let StationLooks check on the next tick. */
	@Override
	public void setDirty() {
		super.setDirty();
		dev.villagerework.perk.StationLooks.changed();
	}

	public static VillageRegistry get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	/** A copy, safe to iterate while villages are added or removed. */
	public List<Village> all() {
		return new ArrayList<>(villages.values());
	}

	public int size() {
		return villages.size();
	}

	public Optional<Village> byId(UUID id) {
		return Optional.ofNullable(villages.get(id));
	}

	public Optional<Village> byBell(BlockPos pos) {
		for (Village v : villages.values()) {
			if (v.bellPos().equals(pos)) return Optional.of(v);
		}
		return Optional.empty();
	}

	/** The village whose area contains this position, if any. */
	public Optional<Village> containing(BlockPos pos, int radius) {
		// Nearest center wins; on an exact tie, the older village wins (stable, not list order).
		return villages.values().stream()
				.filter(v -> v.contains(pos, radius))
				.min(Comparator.comparingDouble((Village v) -> v.horizontalDistance(pos))
						.thenComparingLong(Village::foundedGameTime));
	}

	/** Closest village center within {@code maxDistance} blocks (horizontal). */
	public Optional<Village> nearestWithin(BlockPos pos, int maxDistance) {
		return villages.values().stream()
				.filter(v -> v.horizontalDistance(pos) <= maxDistance)
				.min(Comparator.comparingDouble(v -> v.horizontalDistance(pos)));
	}

	public Optional<Village> nearest(BlockPos pos) {
		return villages.values().stream().min(Comparator.comparingDouble(v -> v.horizontalDistance(pos)));
	}

	public void add(Village village) {
		villages.put(village.id(), village);
		setDirty();
	}

	public boolean remove(UUID id) {
		boolean removed = villages.remove(id) != null;
		if (removed) setDirty();
		return removed;
	}

	public void moveBell(Village village, BlockPos newPos) {
		village.setBellPos(newPos);
		setDirty();
	}

	public void setDormant(Village village, boolean dormant) {
		if (village.dormant() != dormant) {
			village.setDormant(dormant);
			setDirty();
		}
	}

	// ---------------------------------------------------------------- professions

	/** Makes {@code pos} the active station for {@code profession}. Keeps any stored level and progress. */
	public void activateStation(Village village, String profession, BlockPos pos) {
		village.professionOrCreate(profession).setStationPos(pos);
		setDirty();
	}

	/** The station block is gone; the village keeps the profession's level and progress. */
	public void deactivateStation(Village village, String profession) {
		village.profession(profession).ifPresent(p -> {
			p.setStationPos(null);
			setDirty();
		});
	}

	public void setProfessionLevel(Village village, String profession, int level, double progress) {
		ProfessionStation p = village.professionOrCreate(profession);
		p.setLevel(level);
		p.setProgress(progress);
		p.inventory().setMilestone(StationTypes.get(profession).flatMap(d -> d.milestoneFor(p.level() + 1)).orElse(null));
		setDirty();
	}

	// ---------------------------------------------------------------- station inventories and work

	/** The station's supplies/goods, wired so changes save the village and only fitting supplies go in. */
	public StationInventory inventory(Village village, String profession) {
		StationInventory inv = village.professionOrCreate(profession).inventory();
		inv.setOnChange(this::setDirty);
		ProfessionStation p = village.professionOrCreate(profession);
		inv.setRequest(p.requestItem().flatMap(StationTypes::item).orElse(null), p.requestCount());
		inv.setMilestone(StationTypes.get(profession).flatMap(d -> d.milestoneFor(p.level() + 1)).orElse(null));
		return inv;
	}

	/** A new request, made on {@code day}. */
	public void setRequest(Village village, String profession, String item, int count, long day) {
		ProfessionStation p = village.professionOrCreate(profession);
		p.setRequest(item, count);
		p.setRequestDay(day);
		p.inventory().setRequest(StationTypes.item(item).orElse(null), count);
		setDirty();
	}

	/** The evening work used the request: nothing is asked for until sunrise. The item is kept, so tomorrow differs. */
	public void closeRequest(Village village, String profession) {
		ProfessionStation p = village.professionOrCreate(profession);
		p.setRequest(p.requestItem().orElse(null), 0);
		p.inventory().setRequest(null, 0);
		setDirty();
	}

	public void recordReroll(Village village, String profession) {
		village.professionOrCreate(profession).addReroll();
		setDirty();
	}

	/** The fisherman's extra barrel rows; changes mark the village as changed so they're saved. */
	public net.minecraft.world.Container extraStorage(Village village, String profession) {
		ExtraStorage c = village.professionOrCreate(profession).extraStorage();
		c.setOnChange(this::setDirty);
		return c;
	}

	public void setSoakUsed(Village village, String profession, int used) {
		ProfessionStation p = village.professionOrCreate(profession);
		if (p.soakUsed() == used) return;
		p.setSoakUsed(used);
		setDirty();
	}

	/** Sunrise: the reroll price starts over. */
	public void resetRerolls(Village village, String profession) {
		ProfessionStation p = village.professionOrCreate(profession);
		if (p.rerolls() == 0) return;
		p.resetRerolls();
		setDirty();
	}

	public void setRequestDay(Village village, String profession, long day) {
		village.professionOrCreate(profession).setRequestDay(day);
		setDirty();
	}

	public void recordWork(Village village, String profession, long day, double contribution, double progress) {
		ProfessionStation p = village.professionOrCreate(profession);
		p.setLastWorkDay(day);
		p.setLastContribution(contribution);
		p.setProgress(progress);
		setDirty();
	}

	public void setLastWorkDay(Village village, String profession, long day) {
		village.professionOrCreate(profession).setLastWorkDay(day);
		setDirty();
	}

	// ---------------------------------------------------------------- emeralds

	/**
	 * Emeralds brought to the bell. The pool keeps at most {@code max}; the rest is taken anyway (wasted).
	 * {@code donor} is the player who threw them, or null (dispensers, unknown).
	 */
	public void addEmeralds(Village village, int count, UUID donor, int max) {
		village.setEmeraldPool(Math.min(max, village.emeraldPool() + count));
		if (donor != null) village.addDonation(donor, count);
		setDirty();
	}

	/** The evening used the pool. */
	public void usePool(Village village, long day) {
		village.setEmeraldPool(0);
		village.setPoolDay(day);
		setDirty();
	}

	// ---------------------------------------------------------------- workers

	/** Records {@code villager} as a worker of {@code village}, removing it from any other village. */
	public void setWorker(Village village, UUID villager, String professionKey) {
		if (professionKey.equals(village.workers().get(villager))) return;
		removeWorkerEverywhere(villager);
		village.putWorker(villager, professionKey);
		setDirty();
	}

	public void removeWorker(Village village, UUID villager) {
		if (village.removeWorker(villager)) setDirty();
	}

	/** A villager died or converted: free its slot wherever it was. */
	public boolean removeWorkerEverywhere(UUID villager) {
		boolean removed = false;
		for (Village v : villages.values()) removed |= v.removeWorker(villager);
		if (removed) setDirty();
		return removed;
	}

	public void markStationsSeeded(Village village) {
		village.setStationsSeeded(true);
		setDirty();
	}

	/** The village (if any) that has an active station at this position. */
	public Optional<Village> byStation(BlockPos pos) {
		for (Village v : villages.values()) {
			if (v.professionAtStation(pos).isPresent()) return Optional.of(v);
		}
		return Optional.empty();
	}

	public void rename(Village village, String name) {
		village.setName(name);
		setDirty();
	}
}
