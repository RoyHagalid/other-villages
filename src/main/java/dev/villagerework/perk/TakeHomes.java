package dev.villagerework.perk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.villagerework.VillageRework;
import dev.villagerework.village.StationManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Take-home stations (0.17): a workstation bought from a village's workers keeps its tier (II or III) wherever
 * it's placed, so its perks work at home. It's the ordinary vanilla block; the item carries the tier, and
 * placing it records the position here (per dimension). Perks.level() asks here after the village.
 *
 * - Breaking it (by hand or by an explosion) drops the take-home again, tier and all (0.17.1); pistons can't
 *   push it. Only a command replacing the block loses it. Village stations, by contrast, always drop a plain block.
 * - It can't be donated to a village (a bought tier would skip the village's upgrades).
 * - A green sparkle now and then shows which blocks are take-homes.
 * - The fisherman's barrel (0.17.3) has extra rows of its own, kept here; they spill out when it's broken.
 */
public final class TakeHomes extends SavedData {
	private static final String KEY = "villagerework_take_home";

	/** Workstation item of each profession sold as a take-home. */
	public static final Map<String, Item> BLOCKS = new LinkedHashMap<>();

	static {
		BLOCKS.put(Perks.FARMER, Items.COMPOSTER);
		BLOCKS.put(Perks.BUTCHER, Items.SMOKER);
		BLOCKS.put(Perks.ARMORER, Items.BLAST_FURNACE);
		BLOCKS.put(Perks.CLERIC, Items.BREWING_STAND);
		BLOCKS.put(Perks.LEATHERWORKER, Items.CAULDRON);
		BLOCKS.put(Perks.MASON, Items.STONECUTTER);
		BLOCKS.put(Perks.SHEPHERD, Items.LOOM);
		BLOCKS.put(Perks.WEAPONSMITH, Items.GRINDSTONE);
		BLOCKS.put(Perks.TOOLSMITH, Items.SMITHING_TABLE);
		BLOCKS.put(Perks.CARTOGRAPHER, Items.CARTOGRAPHY_TABLE);
		BLOCKS.put(Perks.FLETCHER, Items.FLETCHING_TABLE);
		BLOCKS.put(Perks.LIBRARIAN, Items.LECTERN);
		BLOCKS.put(Perks.FISHERMAN, Items.BARREL);
	}

	/** One saved item of a take-home barrel's extra rows. */
	public record SavedSlot(int slot, ItemStack stack) {
		static final Codec<SavedSlot> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("slot").forGetter(SavedSlot::slot),
				ItemStack.CODEC.fieldOf("item").forGetter(SavedSlot::stack)
		).apply(i, SavedSlot::new));
	}

	/** Size of a take-home barrel's own extra storage (two rows; the tier decides how many show). */
	public static final int EXTRA_SIZE = 18;

	/**
	 * One placed take-home. {@code soakUsed}: the tanner's cauldron's own water budget (see CauldronPerks);
	 * {@code extra}: a fisherman's barrel's extra rows (only used for saving; live in {@link #extra}).
	 */
	public record Entry(BlockPos pos, String profession, int tier, int soakUsed, List<SavedSlot> extra) {
		Entry(BlockPos pos, String profession, int tier, int soakUsed) {
			this(pos, profession, tier, soakUsed, List.of());
		}

		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
				Codec.STRING.fieldOf("profession").forGetter(Entry::profession),
				Codec.INT.fieldOf("tier").forGetter(Entry::tier),
				Codec.INT.optionalFieldOf("soak_used", 0).forGetter(Entry::soakUsed),
				SavedSlot.CODEC.listOf().optionalFieldOf("extra_storage", List.of()).forGetter(Entry::extra)
		).apply(i, Entry::new));
	}

	public static final Codec<TakeHomes> CODEC = Entry.CODEC.listOf()
			.xmap(TakeHomes::new, TakeHomes::save);

	public static final SavedDataType<TakeHomes> TYPE = new SavedDataType<>(
			VillageRework.id("take_homes"), TakeHomes::new, CODEC, null);

	private final Map<BlockPos, Entry> entries = new HashMap<>();
	/** Fisherman barrels' extra rows, by position. */
	private final Map<BlockPos, net.minecraft.world.SimpleContainer> extra = new HashMap<>();

	public TakeHomes() {
	}

	private TakeHomes(List<Entry> list) {
		for (Entry e : list) {
			entries.put(e.pos(), new Entry(e.pos(), e.profession(), e.tier(), e.soakUsed()));
			if (!e.extra().isEmpty()) {
				net.minecraft.world.SimpleContainer c = newExtra();
				for (SavedSlot slot : e.extra()) {
					if (slot.slot() >= 0 && slot.slot() < EXTRA_SIZE) c.setItem(slot.slot(), slot.stack());
				}
				extra.put(e.pos(), c);
			}
		}
	}

	private List<Entry> save() {
		List<Entry> out = new ArrayList<>();
		for (Entry e : entries.values()) {
			List<SavedSlot> slots = new ArrayList<>();
			net.minecraft.world.SimpleContainer c = extra.get(e.pos());
			if (c != null) {
				for (int i = 0; i < c.getContainerSize(); i++) {
					if (!c.getItem(i).isEmpty()) slots.add(new SavedSlot(i, c.getItem(i).copy()));
				}
			}
			out.add(new Entry(e.pos(), e.profession(), e.tier(), e.soakUsed(), slots));
		}
		return out;
	}

	private net.minecraft.world.SimpleContainer newExtra() {
		return new net.minecraft.world.SimpleContainer(EXTRA_SIZE) {
			@Override
			public void setChanged() {
				super.setChanged();
				TakeHomes.this.setDirty();
			}
		};
	}

	/** The extra rows of the take-home barrel at {@code pos} (created empty on first use). */
	public static net.minecraft.world.Container extraStorage(ServerLevel level, BlockPos pos) {
		TakeHomes data = get(level);
		return data.extra.computeIfAbsent(pos.immutable(), p -> data.newExtra());
	}

	/** Any change may change which blocks look upgraded: let StationLooks check on the next tick. */
	@Override
	public void setDirty() {
		super.setDirty();
		dev.villagerework.perk.StationLooks.changed();
	}

	public static TakeHomes get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(TakeHomes::tick);
	}

	// ---------------------------------------------------------------- items

	/** "Butcher's Smoker II": the vanilla block with the tier on it (0.17.8: the painted tier marks replace the glint). */
	public static ItemStack item(String profession, int tier) {
		Item block = BLOCKS.get(profession);
		if (block == null) return ItemStack.EMPTY;
		ItemStack stack = new ItemStack(block);
		CompoundTag data = new CompoundTag();
		data.putString("profession", profession);
		data.putInt("tier", tier);
		CompoundTag root = new CompoundTag();
		root.put(KEY, data);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
		stack.set(DataComponents.ITEM_NAME, Component.literal(StationManager.displayName(profession) + "'s ")
				.append(block.getName(stack)).append(" " + roman(tier)));
		// 0.17.5: the tier marks show on the item too (items/take_home/<block>_<tier>.json, see tools/station_models.py).
		stack.set(DataComponents.ITEM_MODEL, VillageRework.id("take_home/"
				+ net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(block).getPath() + "_" + tier));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
				Component.literal("Level " + roman(tier)).withStyle(s ->
						s.withItalic(false).withColor(ChatFormatting.GRAY)))));
		return stack;
	}

	/** The profession and tier on a take-home item, if it is one. */
	public static Optional<Entry> fromItem(ItemStack stack) {
		CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
		if (custom == null) return Optional.empty();
		CompoundTag root = custom.copyTag();
		Optional<CompoundTag> data = root.getCompound(KEY);
		if (data.isEmpty()) return Optional.empty();
		String profession = data.get().getStringOr("profession", "");
		int tier = data.get().getIntOr("tier", 0);
		if (!BLOCKS.containsKey(profession) || tier < 2) return Optional.empty();
		return Optional.of(new Entry(BlockPos.ZERO, profession, tier, 0));
	}

	private static String roman(int n) {
		return switch (n) {
			case 2 -> "II";
			case 3 -> "III";
			default -> String.valueOf(n);
		};
	}

	// ---------------------------------------------------------------- placed blocks

	/** A take-home item was just placed at {@code pos}. */
	public static void placed(ServerLevel level, BlockPos pos, ItemStack stack) {
		fromItem(stack).ifPresent(e -> {
			TakeHomes data = get(level);
			data.entries.put(pos.immutable(), new Entry(pos.immutable(), e.profession(), e.tier(), 0));
			data.setDirty();
		});
	}

	/** The block at {@code pos} was broken by a player (or replaced): its tier is gone. */
	public static void removed(ServerLevel level, BlockPos pos) {
		TakeHomes data = get(level);
		if (data.entries.remove(pos) != null) data.setDirty();
		// A barrel's extra rows spill out where it stood, like its own slots.
		net.minecraft.world.SimpleContainer c = data.extra.remove(pos);
		if (c != null && !c.isEmpty()) {
			net.minecraft.world.Containers.dropContents(level, pos, c);
			data.setDirty();
		}
	}

	/** True if this block state is {@code profession}'s workstation. */
	public static boolean isWorkstation(net.minecraft.world.level.block.state.BlockState state, String profession) {
		return net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(state)
				.flatMap(holder -> holder.unwrapKey())
				.map(key -> key.identifier().toString().equals(profession))
				.orElse(false);
	}

	/**
	 * A player just broke the block at {@code pos}. Fabric reports this before vanilla works out the drops, so
	 * when the drops will run (the right tool, not creative), getDrops swaps in the take-home and nothing
	 * happens here. Without a fitting tool (a smoker broken by hand) vanilla drops nothing, so the take-home
	 * drops here instead. In creative mode it's just gone, like any block.
	 */
	public static void brokenBy(ServerLevel level, BlockPos pos, net.minecraft.world.entity.player.Player player,
			net.minecraft.world.level.block.state.BlockState state) {
		Optional<Entry> entry = raw(level, pos);
		if (entry.isEmpty()) return;
		if (player != null && player.hasInfiniteMaterials()) {
			removed(level, pos);
			return;
		}
		boolean vanillaDrops = player instanceof net.minecraft.server.level.ServerPlayer sp
				&& !sp.preventsBlockDrops() && sp.hasCorrectToolForDrops(state);
		if (vanillaDrops) return;
		removed(level, pos);
		net.minecraft.world.level.block.Block.popResource(level, pos, item(entry.get().profession(), entry.get().tier()));
	}

	/** The record at {@code pos}, without checking the block (it may just have been broken). */
	public static Optional<Entry> raw(ServerLevel level, BlockPos pos) {
		return Optional.ofNullable(get(level).entries.get(pos));
	}

	/**
	 * The take-home at {@code pos} was broken: its drops get the tiered item instead of the plain block (added
	 * if the loot had none, e.g. a creeper's blast), and the record is forgotten. Empty if it isn't a take-home.
	 */
	public static Optional<List<ItemStack>> drops(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
			List<ItemStack> drops) {
		Optional<Entry> entry = raw(level, pos);
		if (entry.isEmpty()) return Optional.empty();
		// Only if the broken block is that profession's workstation (not something put there by a command).
		if (!isWorkstation(state, entry.get().profession())) {
			removed(level, pos);
			return Optional.empty();
		}
		Item block = BLOCKS.get(entry.get().profession());
		List<ItemStack> result = new ArrayList<>(drops);
		boolean replaced = false;
		for (int i = 0; i < result.size() && !replaced; i++) {
			ItemStack stack = result.get(i);
			if (!stack.is(block)) continue;
			ItemStack plainLeft = stack.copyWithCount(stack.getCount() - 1);
			result.set(i, item(entry.get().profession(), entry.get().tier()));
			if (!plainLeft.isEmpty()) result.add(plainLeft);
			replaced = true;
		}
		if (!replaced) result.add(item(entry.get().profession(), entry.get().tier()));
		removed(level, pos);
		return Optional.of(result);
	}

	/** The take-home at {@code pos}, if its block is still there (checked when loaded; a missing block forgets it). */
	public static Optional<Entry> at(ServerLevel level, BlockPos pos) {
		TakeHomes data = get(level);
		Entry e = data.entries.get(pos);
		if (e == null) return Optional.empty();
		// Checked by block state: the POI record of a just-placed block only appears a moment later.
		if (level.isLoaded(pos) && !isWorkstation(level.getBlockState(pos), e.profession())) {
			removed(level, pos);
			return Optional.empty();
		}
		return Optional.of(e);
	}

	/** The tier this position has for {@code profession} as a take-home, or 0. */
	public static int tier(ServerLevel level, BlockPos pos, String profession) {
		return at(level, pos).filter(e -> e.profession().equals(profession)).map(Entry::tier).orElse(0);
	}

	/** Every recorded take-home in this level and its tier (for the upgraded looks, see StationLooks). */
	public Map<BlockPos, Integer> tiers() {
		Map<BlockPos, Integer> out = new HashMap<>();
		entries.values().forEach(e -> out.put(e.pos(), e.tier()));
		return out;
	}

	public static boolean isTakeHome(ServerLevel level, BlockPos pos) {
		return at(level, pos).isPresent();
	}

	public static int soakUsed(ServerLevel level, BlockPos pos) {
		return at(level, pos).map(Entry::soakUsed).orElse(0);
	}

	public static void setSoakUsed(ServerLevel level, BlockPos pos, int used) {
		TakeHomes data = get(level);
		Entry e = data.entries.get(pos);
		if (e == null || e.soakUsed() == used) return;
		data.entries.put(pos, new Entry(e.pos(), e.profession(), e.tier(), used));
		data.setDirty();
	}

	// ---------------------------------------------------------------- the sparkle

	/** Every 2 seconds: forget take-homes whose block is gone, and sparkle the ones a player is near. */
	private static void tick(ServerLevel level) {
		if (level.getGameTime() % 40 != 0) return;
		TakeHomes data = get(level);
		if (data.entries.isEmpty()) return;
		for (BlockPos pos : new ArrayList<>(data.entries.keySet())) {
			if (!level.isLoaded(pos) || at(level, pos).isEmpty()) continue;
			if (level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 16, false) == null) continue;
			if (level.getRandom().nextInt(3) != 0) continue;
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
					1 + level.getRandom().nextInt(2), 0.3, 0.1, 0.3, 0.0);
		}
	}
}
