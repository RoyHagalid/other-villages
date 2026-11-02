package dev.villagerework.village;

import dev.villagerework.config.VRConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * What each station asks for and what it gives back (Phase 8: all thirteen professions).
 *
 * Requests: one stackable item per day, picked from {@code needs}. Each need has its own
 * amount at level I; higher levels scale it like {@code stations.dailyInputCapByLevel}
 * (8/16/24 by default = ×1, ×2, ×3), never more than one stack.
 *
 * Goods: a weighted table. Each good unlocks at a level, so the pool grows as the station
 * levels up. Single items (tools, armor, potions) arrive one at a time.
 */
public final class StationTypes {
	/**
	 * A request option: the item and how many are asked for at level I. {@code counts}, when set, gives the amount
	 * at each level instead of scaling ({@code stations.dailyInputCapByLevel}); 0.20.0 uses it for coal and
	 * charcoal (8 / 16 / 32). {@code minLevel}: the lowest station level that asks for it (0.20.0).
	 */
	public record Need(Item item, int baseCount, int[] counts, int minLevel) {
		/** How many to ask for at a station level (1-based), or -1 to scale baseCount the usual way. */
		public int countAt(int level) {
			if (counts == null || counts.length == 0) return -1;
			return counts[Math.max(0, Math.min(level, counts.length) - 1)];
		}
	}

	/** Builds one item of a good. {@code stationLevel} lets a good get better with the station (books). */
	@FunctionalInterface
	public interface Maker {
		ItemStack make(ServerLevel level, RandomSource random, int stationLevel);
	}

	/**
	 * One possible good.
	 * {@code make} builds one item of it (random colors, potions, enchantments);
	 * {@code matches} says whether a stack in a reward slot is this good, so more of it can be added.
	 * {@code varied}: each pick can come out different (colors, enchantments), so a reroll may land on it again.
	 */
	public record Good(Item item, int weight, int baseAmount, int minLevel, Maker make, Predicate<ItemStack> matches,
			boolean varied) {
		public ItemStack create(ServerLevel level, int stationLevel) {
			return make.make(level, level.getRandom(), stationLevel);
		}
	}

	/**
	 * {@code needs}: what the villagers can ask for (one per day).
	 * {@code goods}: weighted output table, unlocked by level.
	 * {@code milestones}: the item that unlocks each next level (index 0 = level 2, index 1 = level 3, ...).
	 */
	public record Definition(List<Need> needs, List<Good> goods, List<Item> milestones) {
		/** The item needed to reach {@code targetLevel}, if there is one. */
		public Optional<Item> milestoneFor(int targetLevel) {
			int index = targetLevel - 2;
			return index >= 0 && index < milestones.size() ? Optional.of(milestones.get(index)) : Optional.empty();
		}

		public boolean accepts(ItemStack stack) {
			for (Need need : needs) if (stack.is(need.item())) return true;
			return false;
		}

		/** A random need the station's level asks for, different from yesterday's when there is more than one. */
		public Need pickNeed(RandomSource random, Item previous, int level) {
			List<Need> open = new ArrayList<>();
			for (Need need : needs) if (need.minLevel() <= level) open.add(need);
			if (open.isEmpty()) open.add(needs.getFirst());
			if (open.size() > 1) open.removeIf(need -> need.item() == previous);
			return open.get(random.nextInt(open.size()));
		}

		/** The goods available at {@code level}. */
		public List<Good> pool(int level) {
			List<Good> out = new ArrayList<>();
			for (Good g : goods) if (g.minLevel() <= level) out.add(g);
			return out.isEmpty() ? goods : out;
		}

		/** A weighted random good from the level's pool, avoiding {@code exclude} when anything else is possible. */
		public Good pickGood(RandomSource random, int level, Good exclude) {
			List<Good> pool = new ArrayList<>(pool(level));
			if (exclude != null && pool.size() > 1) pool.remove(exclude);
			pool.removeIf(g -> g.weight() <= 0 && !g.equals(exclude));
			if (pool.isEmpty()) pool.add(exclude != null ? exclude : goods.getFirst());
			int total = 0;
			for (Good g : pool) total += g.weight();
			int roll = random.nextInt(Math.max(1, total));
			for (Good g : pool) {
				roll -= g.weight();
				if (roll < 0) return g;
			}
			return pool.getFirst();
		}

		/** The good a reward stack belongs to, if any. */
		public Optional<Good> goodFor(ItemStack stack) {
			for (Good g : goods) if (g.matches().test(stack)) return Optional.of(g);
			return Optional.empty();
		}
	}

	// ---------------------------------------------------------------- building blocks

	private static Need need(Item item, int baseCount) {
		return new Need(item, baseCount, null, 1);
	}

	/** Asked for from {@code minLevel} on (0.20.0). */
	private static Need need(Item item, int baseCount, int minLevel) {
		return new Need(item, baseCount, null, minLevel);
	}

	/** Fuel (coal, charcoal): 8 / 16 / 32 by level (0.20.0). */
	private static Need fuel(Item item) {
		return new Need(item, 8, new int[]{8, 16, 32}, 1);
	}

	/** A plain item. */
	private static Good good(Item item, int weight, int amount, int minLevel) {
		return new Good(item, weight, amount, minLevel, (l, r, lv) -> new ItemStack(item), s -> s.is(item), false);
	}

	/** One of several items (colors, variants), chosen when the slot is empty; more of the same after that. */
	private static Good oneOf(List<Item> items, int weight, int amount, int minLevel) {
		return new Good(items.getFirst(), weight, amount, minLevel,
				(l, r, lv) -> new ItemStack(items.get(r.nextInt(items.size()))),
				s -> items.stream().anyMatch(s::is), true);
	}

	/** An item with extra data (potion, color, enchantment). Matches by item; the slot keeps its own data. */
	private static Good special(Item item, int weight, int amount, int minLevel,
			BiFunction<ServerLevel, RandomSource, ItemStack> make) {
		return new Good(item, weight, amount, minLevel, (l, r, lv) -> make.apply(l, r), s -> s.is(item), true);
	}

	/**
	 * Where the village is, while goods are being made (set around deliveries and rerolls): explorer maps search
	 * for their structure from here.
	 */
	public static final ThreadLocal<net.minecraft.core.BlockPos> ORIGIN = new ThreadLocal<>();

	/** Goods a reward slot holds only one of (explorer maps: more copies of the same map would be pointless). */
	private static final java.util.Set<Item> SINGLE = java.util.Set.of(
			Items.OCEAN_MONUMENT_MAP, Items.WOODLAND_MANSION_MAP, Items.BURIED_TRIAL_CHAMBERS_MAP,
			Items.PLAINS_VILLAGE_MAP, Items.DESERT_VILLAGE_MAP, Items.SAVANNA_VILLAGE_MAP, Items.SNOWY_VILLAGE_MAP,
			Items.TAIGA_VILLAGE_MAP, Items.JUNGLE_PYRAMID_MAP, Items.SWAMP_HUT_MAP, Items.DESERT_PYRAMID_MAP,
			Items.BURIED_MINESHAFT_MAP, Items.WARM_OCEAN_RUINS_MAP, Items.ABANDONED_CAMP_MAP,
			Items.BURIED_ANCIENT_CITY_MAP, Items.BURIED_TREASURE_MAP);

	/** How many of this stack a reward slot may hold. */
	public static int cap(ItemStack stack) {
		return SINGLE.contains(stack.getItem()) ? 1 : stack.getMaxStackSize();
	}

	/**
	 * An explorer map like the vanilla cartographer sold (0.17.15): it points to the nearest such structure from the
	 * village that no other map has claimed yet. If there's none within reach, the slot gets a plain empty map.
	 */
	private static Good explorerMap(Item item, net.minecraft.tags.TagKey<net.minecraft.world.level.levelgen.structure.Structure> target,
			net.minecraft.core.Holder<net.minecraft.world.level.saveddata.maps.MapDecorationType> decoration, int weight, int minLevel) {
		return new Good(item, weight, 1, minLevel, (level, random, lv) -> {
			net.minecraft.core.BlockPos origin = ORIGIN.get();
			if (origin == null) return new ItemStack(Items.MAP);
			net.minecraft.core.BlockPos found = level.findNearestMapStructure(target, origin, 100, true);
			if (found == null) return new ItemStack(Items.MAP);
			ItemStack map = new ItemStack(item);
			net.minecraft.world.item.MapItem.applyNewSavedData(level, map, found.getX(), found.getZ(), (byte) 2, true, true);
			net.minecraft.world.item.MapItem.renderBiomePreviewMap(level, map);
			net.minecraft.world.level.saveddata.maps.MapItemSavedData.addTargetDecoration(map, found, "+", decoration);
			// Claim it, so the next map points to a different one (as vanilla's maps do).
			level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).get(target).ifPresent(set -> {
				var start = level.structureManager().getStructureAt(found, set);
				if (start.isValid() && start.canBeReferenced()) level.structureManager().addReference(start);
			});
			return map;
		}, s -> s.is(item), true);
	}

	private static Definition def(List<Need> needs, List<Good> goods, Item milestone2, Item milestone3) {
		return new Definition(needs, goods, List.of(milestone2, milestone3));
	}

	// 26.x keeps colored blocks in color collections (Items.WOOL.white(), Items.BANNER.asList(), ...).
	private static final List<Item> COLORED_WOOL = Items.WOOL.asList().stream().filter(i -> i != Items.WOOL.white()).toList();
	private static final List<Item> CARPETS = Items.CARPET.asList();
	private static final List<Item> BANNERS = Items.BANNER.asList();
	private static final List<Item> GLAZED_TERRACOTTA = Items.GLAZED_TERRACOTTA.asList();

	private static Item dye(DyeColor color) {
		return Items.DYE.pick(color);
	}

	/** 0.20.1: dyes the shepherd mixes from two others (II) and from three or more, or rare sources (III). */
	private static final List<Item> MIXED_DYES = List.of(dye(DyeColor.ORANGE), dye(DyeColor.LIME), dye(DyeColor.PINK),
			dye(DyeColor.LIGHT_BLUE), dye(DyeColor.GRAY));
	private static final List<Item> RARE_DYES = List.of(dye(DyeColor.MAGENTA), dye(DyeColor.PURPLE), dye(DyeColor.CYAN),
			dye(DyeColor.LIGHT_GRAY), dye(DyeColor.BROWN));
	/** 0.19.0: caps and boots come from a level I tanner, chestplates and leggings from level II. */
	private static final List<Item> LEATHER_SMALL = List.of(Items.LEATHER_HELMET, Items.LEATHER_BOOTS);
	private static final List<Item> LEATHER_LARGE = List.of(Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS);

	private static ItemStack dyedLeather(RandomSource random, List<Item> pieces) {
		ItemStack stack = new ItemStack(pieces.get(random.nextInt(pieces.size())));
		DyeColor color = DyeColor.values()[random.nextInt(DyeColor.values().length)];
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color.getTextureDiffuseColor() & 0xFFFFFF));
		return stack;
	}

	/**
	 * A level I enchanted book for the librarian. The station level decides how likely each tier
	 * is ({@code librarian.tierWeightsByLevel}); then any enchantment in that tier, evenly.
	 * Falls back to a plain book if no listed enchantment exists.
	 */
	public static ItemStack enchantedBook(ServerLevel level, RandomSource random, int stationLevel) {
		VRConfig.Librarian cfg = VRConfig.get().librarian;
		var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		List<List<Holder<Enchantment>>> tiers = new ArrayList<>();
		for (String[] ids : new String[][]{cfg.tier1, cfg.tier2, cfg.tier3}) {
			List<Holder<Enchantment>> tier = new ArrayList<>();
			for (String id : ids) {
				Identifier key = Identifier.tryParse(id);
				if (key == null) continue;
				registry.get(ResourceKey.create(Registries.ENCHANTMENT, key)).ifPresent(tier::add);
			}
			tiers.add(tier);
		}
		int[] weights = cfg.tierWeightsByLevel[Math.max(0, Math.min(stationLevel, cfg.tierWeightsByLevel.length) - 1)];
		int total = 0;
		for (int t = 0; t < 3; t++) if (!tiers.get(t).isEmpty()) total += weights[t];
		if (total <= 0) {
			// Nothing to pick from at these weights: use any tier that has something.
			for (List<Holder<Enchantment>> tier : tiers) {
				if (!tier.isEmpty()) return EnchantmentHelper.createBook(new EnchantmentInstance(
						tier.get(random.nextInt(tier.size())), 1));
			}
			return new ItemStack(Items.BOOK);
		}
		int roll = random.nextInt(total);
		for (int t = 0; t < 3; t++) {
			if (tiers.get(t).isEmpty()) continue;
			roll -= weights[t];
			if (roll < 0) {
				List<Holder<Enchantment>> tier = tiers.get(t);
				return EnchantmentHelper.createBook(new EnchantmentInstance(tier.get(random.nextInt(tier.size())), 1));
			}
		}
		return new ItemStack(Items.BOOK);
	}

	/** An item with one enchantment at level I (smiths' level III rewards). */
	private static ItemStack enchanted(ServerLevel level, Item item, ResourceKey<Enchantment> enchantment) {
		ItemStack stack = new ItemStack(item);
		level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(enchantment)
				.ifPresent(e -> stack.enchant(e, 1));
		return stack;
	}

	/** A random potion of the given kinds (cleric rewards). */
	private static Good potions(int weight, int minLevel, List<Holder<net.minecraft.world.item.alchemy.Potion>> kinds) {
		return special(Items.POTION, weight, 1, minLevel,
				(l, r) -> PotionContents.createItemStack(Items.POTION, kinds.get(r.nextInt(kinds.size()))));
	}

	private static Good map(Item item, net.minecraft.tags.TagKey<net.minecraft.world.level.levelgen.structure.Structure> target,
			Holder<net.minecraft.world.level.saveddata.maps.MapDecorationType> decoration, int minLevel) {
		return explorerMap(item, target, decoration, 1, minLevel);
	}

	// ---------------------------------------------------------------- the stations

	private static final Map<String, Definition> DEFINITIONS = new LinkedHashMap<>();

	static {
		// 0.20.0: stations feed each other (a reward of one is a request of another, so hoppers can chain them),
		// and no station asks for what it gives. Links: farmer -> butcher, shepherd, librarian (cane), cleric
		// (honey, golden carrots, glistering melon); butcher -> tanner (leather, rabbit hide), fletcher and
		// librarian (feathers), farmer and shepherd (bone meal); librarian -> cartographer (paper); mason ->
		// cartographer (glass panes); toolsmith -> cartographer (compass), shepherd (shears); weaponsmith ->
		// fletcher, toolsmith (flint); shepherd -> fisherman, tanner, weaponsmith (string); fisherman -> librarian
		// (ink sacs); cleric -> librarian (lapis).
		DEFINITIONS.put("minecraft:farmer", def( // composter
				List.of(need(Items.WHEAT_SEEDS, 16), need(Items.BEETROOT_SEEDS, 16), need(Items.BONE_MEAL, 8)),
				List.of(good(Items.WHEAT, 4, 8, 1), good(Items.CARROT, 3, 6, 1), good(Items.POTATO, 3, 6, 1),
						good(Items.BEETROOT, 2, 6, 1), good(Items.SUGAR_CANE, 2, 6, 1),
						good(Items.BREAD, 3, 4, 2), good(Items.PUMPKIN_PIE, 2, 2, 2),
						good(Items.HONEY_BOTTLE, 2, 2, 2), good(Items.HONEYCOMB, 2, 3, 2),
						good(Items.GOLDEN_CARROT, 2, 3, 3), good(Items.GLISTERING_MELON_SLICE, 1, 2, 3)),
				Items.IRON_BLOCK, Items.NETHER_WART));

		DEFINITIONS.put("minecraft:fisherman", def( // barrel
				List.of(need(Items.STRING, 8), need(Items.STICK, 16), need(Items.KELP, 16), need(Items.FISHING_ROD, 1)),
				List.of(good(Items.COD, 6, 6, 1), good(Items.SALMON, 4, 4, 1),
						good(Items.INK_SAC, 3, 3, 2), good(Items.TROPICAL_FISH, 2, 2, 2), good(Items.PUFFERFISH, 2, 2, 2),
						good(Items.PRISMARINE_SHARD, 2, 4, 3), good(Items.PRISMARINE_CRYSTALS, 2, 3, 3),
						good(Items.NAUTILUS_SHELL, 1, 1, 3)),
				Items.TURTLE_SCUTE, Items.HEART_OF_THE_SEA));

		DEFINITIONS.put("minecraft:butcher", def( // smoker
				List.of(fuel(Items.COAL), need(Items.WHEAT, 16), need(Items.CARROT, 16),
						need(Items.POTATO, 16), need(Items.SWEET_BERRIES, 16)),
				List.of(good(Items.COOKED_PORKCHOP, 3, 4, 1), good(Items.COOKED_BEEF, 3, 4, 1),
						good(Items.COOKED_MUTTON, 2, 4, 1), good(Items.COOKED_CHICKEN, 3, 4, 1),
						good(Items.LEATHER, 2, 3, 1), good(Items.FEATHER, 2, 4, 1), good(Items.BONE_MEAL, 2, 6, 1),
						good(Items.COOKED_RABBIT, 2, 3, 2), good(Items.RABBIT_HIDE, 2, 3, 2), good(Items.DRIED_KELP_BLOCK, 2, 2, 2),
						good(Items.RABBIT_STEW, 2, 1, 3)),
				Items.LEAD, Items.HONEY_BLOCK));

		DEFINITIONS.put("minecraft:mason", def( // stonecutter
				List.of(need(Items.COBBLESTONE, 32), need(Items.CLAY_BALL, 16), fuel(Items.COAL),
						need(Items.ANDESITE, 16), need(Items.SAND, 16),
						need(Items.RESIN_CLUMP, 16, 2), need(Items.QUARTZ, 16, 3)),
				List.of(good(Items.BRICKS, 3, 8, 1), good(Items.STONE_BRICKS, 3, 8, 1),
						good(Items.POLISHED_GRANITE, 1, 8, 1), good(Items.POLISHED_DIORITE, 1, 8, 1),
						good(Items.POLISHED_ANDESITE, 1, 8, 1), good(Items.GLASS_PANE, 2, 8, 1),
						good(Items.TERRACOTTA, 3, 8, 2), oneOf(GLAZED_TERRACOTTA, 2, 4, 2), good(Items.RESIN_BRICKS, 2, 6, 2),
						good(Items.QUARTZ_BLOCK, 2, 4, 3), good(Items.TUFF_BRICKS, 2, 8, 3),
						good(Items.POLISHED_DEEPSLATE, 2, 8, 3)),
				Items.AMETHYST_BLOCK, Items.QUARTZ_BLOCK));

		DEFINITIONS.put("minecraft:shepherd", def( // loom
				List.of(need(Items.WHEAT, 16), need(Items.SHEARS, 1), need(Items.BONE_MEAL, 8),
						need(dye(DyeColor.RED), 8), need(dye(DyeColor.YELLOW), 8), need(dye(DyeColor.BLUE), 8),
						need(dye(DyeColor.BLACK), 8)),
				List.of(good(Items.WOOL.white(), 4, 6, 1), oneOf(COLORED_WOOL, 3, 4, 1), good(Items.STRING, 2, 6, 1),
						oneOf(CARPETS, 3, 6, 2), oneOf(BANNERS, 2, 1, 2), oneOf(MIXED_DYES, 3, 4, 2),
						oneOf(RARE_DYES, 3, 4, 3),
						good(Items.FLOWER_BANNER_PATTERN, 1, 1, 3), good(Items.FIELD_MASONED_BANNER_PATTERN, 1, 1, 3),
						good(Items.BORDURE_INDENTED_BANNER_PATTERN, 1, 1, 3)),
				Items.IRON_BLOCK, Items.PIGLIN_BANNER_PATTERN));

		DEFINITIONS.put("minecraft:armorer", def( // blast furnace
				List.of(fuel(Items.COAL), need(Items.RAW_IRON, 8), fuel(Items.CHARCOAL),
						need(Items.RAW_COPPER, 8), need(Items.LAVA_BUCKET, 1)),
				List.of(good(Items.IRON_NUGGET, 4, 9, 1), good(Items.IRON_CHAIN, 3, 2, 1),
						good(Items.GOLD_NUGGET, 3, 9, 1), good(Items.IRON_BARS, 2, 4, 1),
						good(Items.IRON_INGOT, 3, 3, 2), good(Items.CHAINMAIL_HELMET, 1, 1, 2),
						good(Items.CHAINMAIL_BOOTS, 1, 1, 2),
						good(Items.CHAINMAIL_CHESTPLATE, 1, 1, 3), good(Items.CHAINMAIL_LEGGINGS, 1, 1, 3)),
				Items.IRON_BLOCK, Items.NETHERITE_SCRAP));

		DEFINITIONS.put("minecraft:toolsmith", def( // smithing table
				List.of(fuel(Items.COAL), need(Items.STICK, 16), need(Items.FLINT, 8),
						need(Items.RAW_COPPER, 8), need(Items.COBBLESTONE, 32)),
				List.of(good(Items.IRON_NUGGET, 3, 9, 1), good(Items.COPPER_NUGGET, 2, 9, 1), good(Items.BUCKET, 2, 1, 1),
						good(Items.COPPER_PICKAXE, 1, 1, 1), good(Items.COPPER_AXE, 1, 1, 1), good(Items.COPPER_SHOVEL, 1, 1, 1),
						good(Items.SHEARS, 2, 1, 2), good(Items.FLINT_AND_STEEL, 2, 1, 2),
						good(Items.COMPASS, 2, 1, 2), good(Items.LANTERN, 2, 2, 2),
						good(Items.IRON_PICKAXE, 1, 1, 3), good(Items.IRON_SHOVEL, 1, 1, 3),
						good(Items.IRON_AXE, 1, 1, 3), good(Items.IRON_HOE, 1, 1, 3),
						special(Items.IRON_PICKAXE, 1, 1, 3, (l, r) -> enchanted(l, Items.IRON_PICKAXE, Enchantments.EFFICIENCY))),
				Items.ANVIL, Items.DIAMOND_BLOCK));

		DEFINITIONS.put("minecraft:weaponsmith", def( // grindstone
				List.of(need(Items.GRAVEL, 16), fuel(Items.COAL), need(Items.STICK, 16),
						need(Items.RAW_COPPER, 8), need(Items.STRING, 8)),
				List.of(good(Items.COPPER_SWORD, 2, 1, 1), good(Items.COPPER_AXE, 2, 1, 1), good(Items.FLINT, 3, 4, 1),
						good(Items.IRON_SWORD, 2, 1, 2), good(Items.IRON_AXE, 2, 1, 2), good(Items.SHIELD, 2, 1, 2),
						good(Items.BELL, 1, 1, 2),
						special(Items.IRON_SWORD, 2, 1, 3, (l, r) -> enchanted(l, Items.IRON_SWORD, Enchantments.SHARPNESS)),
						special(Items.IRON_AXE, 1, 1, 3, (l, r) -> enchanted(l, Items.IRON_AXE, Enchantments.SHARPNESS))),
				Items.IRON_BLOCK, Items.BLAZE_ROD));

		DEFINITIONS.put("minecraft:fletcher", def( // fletching table
				List.of(need(Items.STICK, 16), need(Items.FLINT, 8), need(Items.FEATHER, 8)),
				List.of(good(Items.ARROW, 4, 8, 1),
						good(Items.SPECTRAL_ARROW, 2, 4, 2),
						special(Items.TIPPED_ARROW, 2, 4, 3, (l, r) -> PotionContents.createItemStack(Items.TIPPED_ARROW,
								List.of(Potions.SLOWNESS, Potions.POISON, Potions.SWIFTNESS, Potions.HEALING)
										.get(r.nextInt(4))))),
				Items.TARGET, Items.GHAST_TEAR));

		DEFINITIONS.put("minecraft:leatherworker", def( // cauldron
				List.of(need(Items.LEATHER, 8), need(Items.RABBIT_HIDE, 8), need(Items.STRING, 8),
						need(Items.ARMADILLO_SCUTE, 4, 3)),
				List.of(good(Items.ITEM_FRAME, 3, 2, 1), good(Items.BUNDLE, 2, 1, 1),
						good(Items.LEATHER_HORSE_ARMOR, 2, 1, 1),
						new Good(Items.LEATHER_HELMET, 2, 1, 1, (l, r, lv) -> dyedLeather(r, LEATHER_SMALL),
								s -> LEATHER_SMALL.stream().anyMatch(s::is), true),
						new Good(Items.LEATHER_CHESTPLATE, 2, 1, 2, (l, r, lv) -> dyedLeather(r, LEATHER_LARGE),
								s -> LEATHER_LARGE.stream().anyMatch(s::is), true),
						good(Items.IRON_HORSE_ARMOR, 1, 1, 3), good(Items.GOLDEN_HORSE_ARMOR, 1, 1, 3),
						good(Items.WOLF_ARMOR, 1, 1, 3)),
				Items.SADDLE, Items.ARMADILLO_SCUTE));

		DEFINITIONS.put("minecraft:cleric", def( // brewing stand
				List.of(need(Items.GLASS_BOTTLE, 8), need(Items.NETHER_WART, 8), need(Items.ROTTEN_FLESH, 16),
						need(Items.SUGAR, 8),
						need(Items.SPIDER_EYE, 4, 2), need(Items.HONEY_BOTTLE, 4, 2), need(Items.RABBIT_FOOT, 1, 3),
						need(Items.BLAZE_POWDER, 4, 3), need(Items.GOLDEN_CARROT, 4, 3), need(Items.GLISTERING_MELON_SLICE, 4, 3)),
				List.of(good(Items.REDSTONE, 3, 6, 1), good(Items.GLOWSTONE_DUST, 3, 4, 1),
						potions(2, 1, List.of(Potions.NIGHT_VISION, Potions.SWIFTNESS, Potions.STRENGTH)),
						good(Items.EXPERIENCE_BOTTLE, 2, 3, 2), good(Items.LAPIS_LAZULI, 2, 4, 2),
						potions(2, 2, List.of(Potions.LONG_NIGHT_VISION, Potions.LONG_SWIFTNESS, Potions.LONG_STRENGTH)),
						potions(2, 3, List.of(Potions.STRONG_SWIFTNESS, Potions.STRONG_STRENGTH, Potions.HEALING,
								Potions.REGENERATION))),
				Items.GHAST_TEAR, Items.RABBIT_FOOT));

		DEFINITIONS.put("minecraft:librarian", def( // lectern
				List.of(need(Items.SUGAR_CANE, 16), need(Items.INK_SAC, 8), need(Items.FEATHER, 8),
						need(Items.LAPIS_LAZULI, 8, 2)),
				List.of(good(Items.BOOK, 4, 3, 1), good(Items.PAPER, 3, 8, 1),
						new Good(Items.ENCHANTED_BOOK, VRConfig.get().librarian.bookWeight, 1, 1,
								StationTypes::enchantedBook, s -> s.is(Items.ENCHANTED_BOOK), true),
						good(Items.BOOKSHELF, 2, 1, 2), good(Items.WRITABLE_BOOK, 1, 1, 2),
						good(Items.CHISELED_BOOKSHELF, 2, 1, 3)),
				Items.AMETHYST_CLUSTER, Items.ECHO_SHARD));

		DEFINITIONS.put("minecraft:cartographer", def( // cartography table
				List.of(need(Items.PAPER, 16), need(Items.GLASS_PANE, 8), need(Items.COMPASS, 4)),
				List.of(good(Items.MAP, 4, 2, 1), good(Items.GLOW_ITEM_FRAME, 2, 2, 1),
						map(Items.PLAINS_VILLAGE_MAP, net.minecraft.tags.StructureTags.ON_PLAINS_VILLAGE_MAPS, MapDecorationTypes.PLAINS_VILLAGE, 2),
						map(Items.DESERT_VILLAGE_MAP, net.minecraft.tags.StructureTags.ON_DESERT_VILLAGE_MAPS, MapDecorationTypes.DESERT_VILLAGE, 2),
						map(Items.SAVANNA_VILLAGE_MAP, net.minecraft.tags.StructureTags.ON_SAVANNA_VILLAGE_MAPS, MapDecorationTypes.SAVANNA_VILLAGE, 2),
						map(Items.SNOWY_VILLAGE_MAP, net.minecraft.tags.StructureTags.ON_SNOWY_VILLAGE_MAPS, MapDecorationTypes.SNOWY_VILLAGE, 2),
						map(Items.TAIGA_VILLAGE_MAP, net.minecraft.tags.StructureTags.ON_TAIGA_VILLAGE_MAPS, MapDecorationTypes.TAIGA_VILLAGE, 2),
						map(Items.BURIED_MINESHAFT_MAP, net.minecraft.tags.StructureTags.ON_MINESHAFT_MAPS, MapDecorationTypes.MINESHAFT, 2),
						map(Items.DESERT_PYRAMID_MAP, net.minecraft.tags.StructureTags.ON_DESERT_PYRAMID_MAPS, MapDecorationTypes.DESERT_PYRAMID, 2),
						map(Items.JUNGLE_PYRAMID_MAP, net.minecraft.tags.StructureTags.ON_JUNGLE_PYRAMID_MAPS, MapDecorationTypes.JUNGLE_TEMPLE, 2),
						map(Items.SWAMP_HUT_MAP, net.minecraft.tags.StructureTags.ON_SWAMP_HUT_MAPS, MapDecorationTypes.SWAMP_HUT, 2),
						map(Items.WARM_OCEAN_RUINS_MAP, net.minecraft.tags.StructureTags.ON_OCEAN_RUIN_WARM_MAPS, MapDecorationTypes.OCEAN_RUIN_WARM, 2),
						map(Items.OCEAN_MONUMENT_MAP, net.minecraft.tags.StructureTags.ON_OCEAN_MONUMENT_MAPS, MapDecorationTypes.OCEAN_MONUMENT, 2),
						good(Items.SPYGLASS, 1, 1, 3), good(Items.GLOBE_BANNER_PATTERN, 1, 1, 3),
						map(Items.BURIED_TRIAL_CHAMBERS_MAP, net.minecraft.tags.StructureTags.ON_BURIED_TRIAL_CHAMBERS_MAPS, MapDecorationTypes.TRIAL_CHAMBERS, 3),
						map(Items.WOODLAND_MANSION_MAP, net.minecraft.tags.StructureTags.ON_WOODLAND_MANSION_MAPS, MapDecorationTypes.WOODLAND_MANSION, 3),
						map(Items.BURIED_ANCIENT_CITY_MAP, net.minecraft.tags.StructureTags.ON_ANCIENT_CITY_MAPS, MapDecorationTypes.ANCIENT_CITY, 3),
						map(Items.BURIED_TREASURE_MAP, net.minecraft.tags.StructureTags.ON_TREASURE_MAPS, MapDecorationTypes.RED_X, 3)),
				Items.SPYGLASS, Items.LODESTONE));
	}

	private StationTypes() {
	}

	public static Optional<Definition> get(String professionKey) {
		return Optional.ofNullable(DEFINITIONS.get(professionKey));
	}

	/** Every profession with a station, e.g. "minecraft:shepherd". */
	public static java.util.Set<String> keys() {
		return java.util.Collections.unmodifiableSet(DEFINITIONS.keySet());
	}

	public static String itemId(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	public static Optional<Item> item(String id) {
		Identifier identifier = Identifier.tryParse(id);
		if (identifier == null) return Optional.empty();
		return BuiltInRegistries.ITEM.getOptional(identifier);
	}
}
