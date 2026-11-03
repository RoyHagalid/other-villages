package dev.villagerework.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * One village, anchored to its center bell. This is the "village identity"
 * vanilla doesn't have. Holds the village's professions (levels and active stations).
 * Later phases add donations and rumors here.
 */
public final class Village {
	public static final int MAX_NAME_LENGTH = 32;

	public static final Codec<Village> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(v -> v.id),
			Codec.STRING.fieldOf("name").forGetter(v -> v.name),
			BlockPos.CODEC.fieldOf("bell").forGetter(v -> v.bellPos),
			Codec.BOOL.fieldOf("natural").forGetter(v -> v.natural),
			Codec.LONG.fieldOf("founded").forGetter(v -> v.foundedGameTime),
			Codec.BOOL.optionalFieldOf("dormant", false).forGetter(v -> v.dormant),
			UUIDUtil.CODEC.optionalFieldOf("founder").forGetter(v -> Optional.ofNullable(v.founder)),
			Codec.unboundedMap(Codec.STRING, ProfessionStation.CODEC).optionalFieldOf("professions", Map.of())
					.forGetter(v -> v.professions),
			Codec.BOOL.optionalFieldOf("stations_seeded", false).forGetter(v -> v.stationsSeeded),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING).optionalFieldOf("workers", Map.of())
					.forGetter(v -> v.workers),
			Codec.INT.optionalFieldOf("emerald_pool", 0).forGetter(v -> v.emeraldPool),
			Codec.LONG.optionalFieldOf("pool_day", ProfessionStation.NEVER).forGetter(v -> v.poolDay),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).optionalFieldOf("donors", Map.of())
					.forGetter(v -> v.donors)
	).apply(instance, Village::new));

	private final UUID id;
	private String name;
	private BlockPos bellPos;
	private final boolean natural;
	private final long foundedGameTime;
	private boolean dormant;
	private final UUID founder;
	/** Keyed by workstation POI id, e.g. "minecraft:librarian". Sorted for stable display. */
	private final Map<String, ProfessionStation> professions;
	/** Natural villages get their generated workstations donated once. */
	private boolean stationsSeeded;
	/**
	 * Every villager with a profession who belongs to this village, by UUID → profession key.
	 * Worker counts come from here rather than from the villagers loaded right now, so a worker
	 * who wanders outside the border or into unloaded chunks still holds its slot.
	 */
	private final Map<UUID, String> workers;
	/** Emeralds the villagers brought to the bell today (used up at the evening work). */
	private int emeraldPool;
	/** The last evening the pool was used. */
	private long poolDay;
	/** Emeralds each player has given this village in total (kept for future rewards; not shown). */
	private final Map<UUID, Integer> donors;

	private Village(UUID id, String name, BlockPos bellPos, boolean natural, long foundedGameTime,
			boolean dormant, Optional<UUID> founder, Map<String, ProfessionStation> professions, boolean stationsSeeded,
			Map<UUID, String> workers, int emeraldPool, long poolDay, Map<UUID, Integer> donors) {
		this.id = id;
		this.name = name;
		this.bellPos = bellPos.immutable();
		this.natural = natural;
		this.foundedGameTime = foundedGameTime;
		this.dormant = dormant;
		this.founder = founder.orElse(null);
		this.professions = new TreeMap<>(professions);
		// Forget anything that isn't a real station profession (e.g. a typo from an older /vr station command).
		this.professions.keySet().removeIf(key -> StationTypes.get(key).isEmpty());
		this.stationsSeeded = stationsSeeded;
		this.workers = new HashMap<>(workers);
		this.emeraldPool = Math.max(0, emeraldPool);
		this.poolDay = poolDay;
		this.donors = new HashMap<>(donors);
	}

	public static Village create(String name, BlockPos bellPos, boolean natural, long gameTime, UUID founder) {
		return new Village(UUID.randomUUID(), name, bellPos, natural, gameTime, false, Optional.ofNullable(founder),
				Map.of(), false, Map.of(), 0, ProfessionStation.NEVER, Map.of());
	}

	/** Read-only view of every profession the village knows about. */
	public Map<String, ProfessionStation> professions() {
		return Collections.unmodifiableMap(professions);
	}

	public Optional<ProfessionStation> profession(String key) {
		return Optional.ofNullable(professions.get(key));
	}

	/** The profession whose active station is at this position. */
	public Optional<String> professionAtStation(BlockPos pos) {
		for (Map.Entry<String, ProfessionStation> e : professions.entrySet()) {
			if (e.getValue().stationPos().map(pos::equals).orElse(false)) return Optional.of(e.getKey());
		}
		return Optional.empty();
	}

	public long activeStationCount() {
		return professions.values().stream().filter(ProfessionStation::active).count();
	}

	public Map<UUID, String> workers() {
		return Collections.unmodifiableMap(workers);
	}

	/** Workers per profession key. */
	public Map<String, Integer> workerCounts() {
		Map<String, Integer> counts = new HashMap<>();
		for (String key : workers.values()) counts.merge(key, 1, Integer::sum);
		return counts;
	}

	public int workerCount(String professionKey) {
		int n = 0;
		for (String key : workers.values()) if (key.equals(professionKey)) n++;
		return n;
	}

	void putWorker(UUID villager, String professionKey) {
		workers.put(villager, professionKey);
	}

	boolean removeWorker(UUID villager) {
		return workers.remove(villager) != null;
	}

	public int emeraldPool() {
		return emeraldPool;
	}

	public long poolDay() {
		return poolDay;
	}

	public Map<UUID, Integer> donors() {
		return Collections.unmodifiableMap(donors);
	}

	void setEmeraldPool(int pool) {
		this.emeraldPool = Math.max(0, pool);
	}

	void setPoolDay(long day) {
		this.poolDay = day;
	}

	void addDonation(UUID donor, int count) {
		donors.merge(donor, count, Integer::sum);
	}

	public boolean stationsSeeded() {
		return stationsSeeded;
	}

	ProfessionStation professionOrCreate(String key) {
		return professions.computeIfAbsent(key, k -> ProfessionStation.fresh());
	}

	void setStationsSeeded(boolean seeded) {
		this.stationsSeeded = seeded;
	}

	public UUID id() {
		return id;
	}

	public String name() {
		return name;
	}

	public BlockPos bellPos() {
		return bellPos;
	}

	public boolean natural() {
		return natural;
	}

	public long foundedGameTime() {
		return foundedGameTime;
	}

	/** In-game day the village was founded or discovered (day 1 = first day of the world). */
	public long foundedDay() {
		return foundedGameTime / 24000L + 1;
	}

	public boolean dormant() {
		return dormant;
	}

	public Optional<UUID> founder() {
		return Optional.ofNullable(founder);
	}

	// Mutators are package-private: only the registry and manager change villages,
	// so they can mark the saved data dirty.

	void setName(String name) {
		this.name = name;
	}

	void setBellPos(BlockPos pos) {
		this.bellPos = pos.immutable();
	}

	void setDormant(boolean dormant) {
		this.dormant = dormant;
	}

	/** Horizontal distance check (villages are columns, height doesn't matter). */
	public boolean contains(BlockPos pos, int radius) {
		long dx = pos.getX() - bellPos.getX();
		long dz = pos.getZ() - bellPos.getZ();
		return dx * dx + dz * dz <= (long) radius * radius;
	}

	public double horizontalDistance(BlockPos pos) {
		double dx = pos.getX() - bellPos.getX();
		double dz = pos.getZ() - bellPos.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}
}
