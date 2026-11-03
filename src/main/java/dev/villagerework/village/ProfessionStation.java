package dev.villagerework.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a village knows about one profession: its level and progress, which workstation
 * is currently its station, the station's supplies and output, and today's request.
 *
 * Everything lives here, in the village, not in the block. Breaking the station gives an
 * ordinary workstation item; donating another workstation of the same kind picks up
 * where the old one left off (including any supplies and goods still waiting).
 */
public final class ProfessionStation {
	/** One saved inventory slot. */
	private record SavedSlot(int slot, ItemStack stack) {
		static final Codec<SavedSlot> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("slot").forGetter(SavedSlot::slot),
				ItemStack.CODEC.fieldOf("item").forGetter(SavedSlot::stack)
		).apply(i, SavedSlot::new));
	}

	/** Slot layout version. 1 = four supply slots + goods (0.6–0.7), 2 = donate, reward, milestone. */
	private static final int CURRENT_LAYOUT = 2;
	/** "Never happened" for day counters. Not -1: day -1 is a real day in a fresh world. */
	public static final long NEVER = Long.MIN_VALUE;

	public static final Codec<ProfessionStation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("level", 1).forGetter(s -> s.level),
			Codec.DOUBLE.optionalFieldOf("progress", 0.0).forGetter(s -> s.progress),
			BlockPos.CODEC.optionalFieldOf("station").forGetter(s -> Optional.ofNullable(s.stationPos)),
			SavedSlot.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(ProfessionStation::savedSlots),
			Codec.STRING.optionalFieldOf("request_item").forGetter(s -> Optional.ofNullable(s.requestItem)),
			Codec.INT.optionalFieldOf("request_count", 0).forGetter(s -> s.requestCount),
			Codec.LONG.optionalFieldOf("last_work_day", NEVER).forGetter(s -> s.lastWorkDay),
			Codec.LONG.optionalFieldOf("request_day", NEVER).forGetter(s -> s.requestDay),
			Codec.DOUBLE.optionalFieldOf("last_contribution", -1.0).forGetter(s -> s.lastContribution),
			Codec.INT.optionalFieldOf("layout", 1).forGetter(s -> CURRENT_LAYOUT),
			Codec.INT.optionalFieldOf("rerolls", 0).forGetter(s -> s.rerolls),
			Codec.INT.optionalFieldOf("soak_used", 0).forGetter(s -> s.soakUsed),
			SavedSlot.CODEC.listOf().optionalFieldOf("extra_storage", List.of()).forGetter(ProfessionStation::savedExtra)
	).apply(instance, ProfessionStation::new));

	private int level;
	private double progress;
	private BlockPos stationPos;
	private final StationInventory inventory = new StationInventory();
	/** Item id the station asks for today, e.g. "minecraft:string". Null until the first request. */
	private String requestItem;
	private int requestCount;
	/** The last day the villagers did their evening work for this station. */
	private long lastWorkDay;
	/** The day the current (or last) request was made, at sunrise. */
	private long requestDay;
	/** How much of the request was met at the last work time, 0..1 (-1 = not yet). */
	private double lastContribution;
	/** Rerolls bought at this station since the last sunrise; the price doubles with each. */
	private int rerolls;
	/** Leatherworker's cauldron: how much of the top water level soaking has used up (see CauldronPerks). */
	private int soakUsed;
	/** Fisherman's barrel: the extra rows unlocked at level II and III. Kept in the village like the station slots. */
	private final ExtraStorage extraStorage = new ExtraStorage(EXTRA_STORAGE_SIZE);
	public static final int EXTRA_STORAGE_SIZE = 18;

	private ProfessionStation(int level, double progress, Optional<BlockPos> stationPos, List<SavedSlot> slots,
			Optional<String> requestItem, int requestCount, long lastWorkDay, long requestDay, double lastContribution,
			int layout, int rerolls, int soakUsed, List<SavedSlot> extra) {
		this.level = Math.max(1, level);
		this.progress = Math.max(0, progress);
		this.stationPos = stationPos.map(BlockPos::immutable).orElse(null);
		if (layout < 2) {
			loadOldLayout(slots);
		} else {
			for (SavedSlot s : slots) {
				if (s.slot() >= 0 && s.slot() < inventory.getContainerSize()) inventory.setItemSilently(s.slot(), s.stack());
			}
		}
		this.requestItem = requestItem.orElse(null);
		this.requestCount = Math.max(0, requestCount);
		this.lastWorkDay = lastWorkDay;
		this.requestDay = requestDay;
		this.lastContribution = lastContribution;
		this.rerolls = Math.max(0, rerolls);
		this.soakUsed = Math.max(0, soakUsed);
		for (SavedSlot s : extra) {
			if (s.slot() >= 0 && s.slot() < EXTRA_STORAGE_SIZE) extraStorage.setItem(s.slot(), s.stack());
		}
	}

	static ProfessionStation fresh() {
		return new ProfessionStation(1, 0, Optional.empty(), List.of(), Optional.empty(), 0, NEVER, NEVER, -1.0, CURRENT_LAYOUT,
				0, 0, List.of());
	}

	/** Old saves had four supply slots (0-3) and goods in slot 4: merge supplies into the one donate slot. */
	private void loadOldLayout(List<SavedSlot> slots) {
		for (SavedSlot s : slots) {
			if (s.slot() == 4) {
				inventory.setItemSilently(StationInventory.REWARD_SLOT, s.stack());
			} else if (s.slot() >= 0 && s.slot() < 4) {
				ItemStack current = inventory.getItem(StationInventory.DONATE_SLOT);
				if (current.isEmpty()) {
					inventory.setItemSilently(StationInventory.DONATE_SLOT, s.stack());
				} else if (ItemStack.isSameItemSameComponents(current, s.stack())) {
					int total = Math.min(current.getMaxStackSize(), current.getCount() + s.stack().getCount());
					inventory.setItemSilently(StationInventory.DONATE_SLOT, current.copyWithCount(total));
				}
			}
		}
	}

	private List<SavedSlot> savedSlots() {
		List<SavedSlot> out = new ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty()) out.add(new SavedSlot(i, stack.copy()));
		}
		return out;
	}

	private List<SavedSlot> savedExtra() {
		List<SavedSlot> out = new ArrayList<>();
		for (int i = 0; i < extraStorage.getContainerSize(); i++) {
			ItemStack stack = extraStorage.getItem(i);
			if (!stack.isEmpty()) out.add(new SavedSlot(i, stack.copy()));
		}
		return out;
	}

	public ExtraStorage extraStorage() {
		return extraStorage;
	}

	public int level() {
		return level;
	}

	public double progress() {
		return progress;
	}

	public Optional<BlockPos> stationPos() {
		return Optional.ofNullable(stationPos);
	}

	public boolean active() {
		return stationPos != null;
	}

	/** True if the village has anything worth remembering for this profession. */
	public boolean hasProgress() {
		return level > 1 || progress > 0;
	}

	public StationInventory inventory() {
		return inventory;
	}

	public Optional<String> requestItem() {
		return Optional.ofNullable(requestItem);
	}

	public int requestCount() {
		return requestCount;
	}

	public long lastWorkDay() {
		return lastWorkDay;
	}

	public long requestDay() {
		return requestDay;
	}

	/** True while the villagers are asking for something (between sunrise and the evening work). */
	public boolean hasOpenRequest() {
		return requestItem != null && requestCount > 0;
	}

	public double lastContribution() {
		return lastContribution;
	}

	/** Rerolls bought since the last sunrise. */
	public int rerolls() {
		return rerolls;
	}

	void addReroll() {
		rerolls++;
	}

	void resetRerolls() {
		rerolls = 0;
	}

	public int soakUsed() {
		return soakUsed;
	}

	void setSoakUsed(int used) {
		soakUsed = Math.max(0, used);
	}

	void setStationPos(BlockPos pos) {
		this.stationPos = pos == null ? null : pos.immutable();
	}

	void setLevel(int level) {
		this.level = Math.max(1, level);
	}

	void setProgress(double progress) {
		this.progress = Math.max(0, progress);
	}

	void setRequest(String item, int count) {
		this.requestItem = item;
		this.requestCount = Math.max(0, count);
	}

	void setLastWorkDay(long day) {
		this.lastWorkDay = day;
	}

	void setRequestDay(long day) {
		this.requestDay = day;
	}

	void setLastContribution(double fraction) {
		this.lastContribution = fraction;
	}
}
