package dev.villagerework.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.villagerework.VillageRework;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Every tunable number in the mod lives here, so balancing never needs a rebuild.
 * The file is config/villagerework.json. Missing fields are filled with defaults
 * and written back, so new options appear automatically after an update.
 * Use /vr config reload to apply changes without restarting.
 */
public final class VRConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("villagerework.json");

	private static VRConfig instance = new VRConfig();

	public static VRConfig get() {
		return instance;
	}

	// ---------------------------------------------------------------- sections

	public Features features = new Features();
	public Village village = new Village();
	public Stations stations = new Stations();
	public Emeralds emeralds = new Emeralds();
	public Prosperity prosperity = new Prosperity();
	public Rerolls rerolls = new Rerolls();
	public Librarian librarian = new Librarian();
	public Trader trader = new Trader();
	public Perks perks = new Perks();
	public ExplosiveArrows explosiveArrows = new ExplosiveArrows();
	public Lectern lectern = new Lectern();
	public TakeHomes takeHomes = new TakeHomes();

	/**
	 * Which defaults this file was written with. Used to move old default values forward once
	 * (e.g. the 0.11 output multipliers) without touching values someone changed on purpose.
	 * No initializer on purpose: a file without the field reads as 0 (an older file).
	 */
	public int configVersion;
	private static final int CURRENT_CONFIG_VERSION = 8;

	/** Switches for replacing vanilla behaviour. Used from Phase 2. */
	public static final class Features {
		/** Removes the vanilla trade screen. */
		public boolean disableVanillaTrading = true;
		/** Stops villagers claiming workstations on their own; only donated stations count. */
		public boolean disableVanillaJobClaiming = true;
	}

	/** Bell-anchored villages. Used from Phase 1. */
	public static final class Village {
		/** Radius in blocks around the center bell that belongs to the village. */
		public int radius = 68;
		/** Minimum distance between two center bells, in blocks (at least twice the radius). */
		public int minDistanceBetweenVillages = 140;
		/** Requirements for "Make Village" on a fresh bell. */
		public int foundingMinVillagers = 2;
		public int foundingMinBeds = 2;
		/** Give the center bell an enchantment-style glow (particles for now). */
		public boolean centerBellGlow = true;
		/** How often villages update (discovery, bell check, member sync), in ticks. 100 = 5 seconds. */
		public int updateIntervalTicks = 100;
		/** How far around each player the mod looks for natural village bells to convert. */
		public int discoveryRadius = 64;
		/** How close a player must be to a bell to use its panel buttons. */
		public int panelReach = 8;
		/** How long "Show border" draws the village edge, in seconds. */
		public int borderDisplaySeconds = 10;
		/**
		 * Iron golems a village allows (0.19.0): one per this many residents, at least golemMin and at most
		 * golemMax. At the cap, villagers inside the village don't spawn golems (panic or gossip).
		 */
		public int residentsPerGolem = 8;
		public int golemMin = 1;
		public int golemMax = 4;
		/** Natural village chests (0.20.0): chance of 1..chestEmeraldMax emeralds, chance of a level I book. */
		public double chestEmeraldChance = 0.4;
		public int chestEmeraldMax = 3;
		public double chestBookChance = 0.15;
	}

	/** Profession stations. Used from Phase 3. */
	public static final class Stations {
		/** Stations are capped at this level for now. */
		public int maxLevel = 3;
		/** Natural villages start with their generated workstations donated (one per profession). */
		public boolean autoDonateNaturalStations = true;
		/** Natural villages' stations start with random progress toward level 2, between these fractions. */
		public double naturalStartProgressMin = 0.10;
		public double naturalStartProgressMax = 0.30;
		/** Villagers that can work one station at the same time. */
		public int workerCapPerStation = 3;
		/**
		 * Request size by level (index 0 = level 1). Each request item has its own amount at level 1
		 * (e.g. 16 seeds, 8 bone meal); higher levels scale it by this table's ratio (8/16/24 = x1, x2, x3).
		 */
		public int[] dailyInputCapByLevel = {8, 16, 24};
		/**
		 * Reward slots the evening fills at each level (index 0 = level 1), 1 to 3. Each slot gets its
		 * own pick from the pool, so two slots can hold the same good. Locked slots show a barrier.
		 */
		public int[] rewardSlotsByLevel = {1, 2, 3};
		/** Multiplier on the size of each slot's daily delivery of goods, indexed by level. */
		public double[] outputMultiplierByLevel = {1.0, 1.5, 2.0};
		/** Progress points needed to fill the bar at each level (the milestone item then levels it up). */
		public int[] progressToFillByLevel = {80, 200, 500};
		/** Progress each worker adds per day when the request is fully met (scaled by how much was met). */
		public int progressPerWorkerPerDay = 4;
		/** The first worker counts as this many workers (0.19.0), so one villager alone makes real progress. */
		public double firstWorkerWeight = 2.0;
		/**
		 * Days a villager keeps a profession whose station the village doesn't have (lost or never donated)
		 * before it becomes unemployed and can take another job (0.19.0).
		 */
		public double jobReleaseDays = 1.0;
		/**
		 * In-game hours a station waits after taking a worker before it takes the next (0.20.0; 1 hour = 1000
		 * ticks = 50 seconds). Leaves time to donate more stations so new jobs spread out.
		 */
		public double hireDelayHours = 2.0;
		/**
		 * Share of the goods villagers still deliver when the player supplied nothing (0 = a single item: the
		 * least a delivery can be). Partial supplies scale between this and the full amount.
		 */
		public double minimumOutputFactor = 0.0;
		/**
		 * Reward slots that still get something on a day with nothing supplied (the rest stay empty).
		 * 0.20.0: 0, so a station with no contribution delivers nothing and it's clear it didn't work.
		 */
		public int idleRewardSlots = 0;
		/** Most in-game days a village catches up on when its area loads again. */
		public int maxCatchUpDays = 7;
		/** One-time XP dropped at the station on level-up (91 points = level 0 to 7). */
		public int levelUpXp = 91;
	}

	/**
	 * Emerald donations (Phase 7). Villagers pick up emeralds thrown inside the village and bring them
	 * to the bell. At the evening work, the day's emeralds turn into points that are shared evenly
	 * between the stations that got some of their request; each point is a boost to that evening.
	 */
	public static final class Emeralds {
		/** Most emeralds the bell keeps per day; any more are taken but wasted. */
		public int maxPerDay = 64;
		/** Points a full day's pool (maxPerDay emeralds) is worth. */
		public int pointsForFullPool = 100;
		/** Points one station can use; more are wasted. */
		public int maxPointsPerStation = 10;
		/** Boost per point (0.10 = +10% progress and goods that evening). */
		public double boostPerPoint = 0.10;
	}

	/**
	 * Village prosperity (0.18): a 0–100 score from what the village is (standing) plus what happened lately (memory,
	 * fading over in-game days). It sets the village's mood (five bands) and production; abundance celebrates.
	 */
	public static final class Prosperity {
		public boolean enabled = true;
		/** Standing: every village starts here. */
		public double base = 35;
		/** Standing: points per station level (all active stations together), up to stationMax. */
		public double perStationLevel = 1.5;
		public double stationMax = 36;
		/** Standing: points per iron golem inside the border, counting at most maxGolems. */
		public double perGolem = 4;
		public int maxGolems = 3;
		/** Memory: points an event is worth and how many days it takes to fade away completely. */
		public double cure = 10;
		public double cureDays = 5;
		/** A cure counts at most once per this many days per village (cure loops gain nothing). */
		public double cureCooldownDays = 1;
		public double raidDefended = 20;
		public double raidDays = 5;
		public double villagerDeath = -8;
		public double villagerDeathDays = 4;
		public double golemDeath = -6;
		public double golemDeathDays = 3;
		public double villagerHurt = -3;
		public double villagerHurtDays = 2;
		/** The same villager hurt again within this many seconds doesn't count again. */
		public int hurtCooldownSeconds = 30;
		public double zombified = -12;
		public double zombifiedDays = 4;
		/** All memories together stay within these bounds. */
		public double memoryMin = -40;
		public double memoryMax = 30;
		/** Score thresholds for unhappy, neutral, content and abundant (below the first: miserable). */
		public int[] bands = {20, 40, 60, 80};
		/** Progress and goods per mood: miserable, unhappy, neutral, content, abundant. */
		public double[] production = {0.7, 0.85, 1.0, 1.1, 1.25};
		/** Abundance: parties at the afternoon gathering on some days (dance, fireworks, Lava Chicken). */
		public boolean parties = true;
		/** Chance that an abundant village parties on a given day. */
		public double partyChancePerDay = 0.3;
		/** Party parts that can be switched off on their own. */
		public boolean fireworks = true;
		public boolean song = true;
		/** Food a villager needs before it wants to breed, per mood (vanilla 12); -1 = doesn't breed at all. */
		public int[] breedFood = {-1, 18, 12, 9, 6};
	}

	/**
	 * Paying emeralds at a station to swap one uncollected reward for a new pick from the same pool.
	 * The price doubles with every reroll at that station during the day and resets at sunrise.
	 * The emeralds are spent (they don't go to the bell).
	 */
	public static final class Rerolls {
		public boolean enabled = true;
		/** Price of the day's first reroll of an ordinary reward. */
		public int baseCost = 1;
		/** Price of the day's first reroll when the slot holds an enchanted book. */
		public int bookBaseCost = 3;
		/** The price never goes above this. */
		public int maxCost = 64;
	}

	/**
	 * Enchanted books from the librarian's station. Books always carry one level I enchantment
	 * (combine them, as in vanilla, to reach higher levels). Enchantments are grouped in tiers;
	 * a station picks a tier by its level's weights, then an enchantment from that tier.
	 * Ids that don't exist (e.g. from a mod that isn't installed) are skipped.
	 */
	public static final class Librarian {
		/** Weight of "an enchanted book" in the librarian's goods (books 4, bookshelves 2, lapis 2, ...). */
		public int bookWeight = 2;
		/** Tier weights per station level: {tier I, tier II, tier III}, index 0 = level 1. */
		public int[][] tierWeightsByLevel = {{1, 0, 0}, {3, 2, 0}, {2, 3, 1}};
		public String[] tier1 = {
				"minecraft:protection", "minecraft:fire_protection", "minecraft:blast_protection",
				"minecraft:projectile_protection", "minecraft:unbreaking", "minecraft:efficiency",
				"minecraft:power", "minecraft:sharpness", "minecraft:feather_falling", "minecraft:respiration",
				"minecraft:aqua_affinity", "minecraft:lure", "minecraft:quick_charge", "minecraft:piercing",
				"minecraft:loyalty", "minecraft:knockback", "minecraft:punch"};
		public String[] tier2 = {
				"minecraft:fortune", "minecraft:looting", "minecraft:silk_touch", "minecraft:depth_strider",
				"minecraft:thorns", "minecraft:infinity", "minecraft:flame", "minecraft:fire_aspect",
				"minecraft:multishot", "minecraft:sweeping_edge", "minecraft:riptide", "minecraft:channeling",
				"minecraft:luck_of_the_sea", "minecraft:density", "minecraft:breach", "minecraft:lunge"};
		public String[] tier3 = {
				"minecraft:mending", "minecraft:frost_walker", "minecraft:soul_speed", "minecraft:swift_sneak",
				"minecraft:wind_burst"};
	}

	/**
	 * The wandering trader (0.12). He buys village goods for emeralds and sells a couple of less common
	 * items; his trades are data files in data/villagerework/ (see the README). He also comes more often.
	 */
	public static final class Trader {
		/** Use the mod's trade sets instead of vanilla's. Applies to traders that arrive after the change. */
		public boolean replaceTrades = true;
		/** Replace vanilla's spawn odds (in practice every couple of weeks) with {@code visitChanceByDaysSince}. */
		public boolean guaranteedVisits = true;
		/**
		 * Chance he comes on a day, by days since his last visit (index 0 = the day after). From the last
		 * entry on, that chance applies every day; 1.0 means he is due and comes as soon as he finds a spot.
		 * Default: 25% on day 2, 50% on day 3, certain on day 4.
		 */
		public double[] visitChanceByDaysSince = {0.0, 0.25, 0.5, 1.0};
	}

	/** Take-home stations (0.17): workers sell their workstation with its tier, to keep the perks at home. */
	public static final class TakeHomes {
		public boolean enabled = true;
		/** Emeralds for tier II and tier III (plus a plain block of the same kind). */
		public int[] priceByTier = {12, 32};
		/**
		 * Renown (0.20.0): level III stations' daily contributions fill ranks. The first rank costs renownFirstRank,
		 * each next one renownRankStep more. Each rank cuts take-home prices by renownDiscountPerRank, compounding,
		 * never below minPrice.
		 */
		public double renownFirstRank = 10;
		public double renownRankStep = 5;
		public double renownDiscountPerRank = 0.10;
		public int minPrice = 1;
	}

	/**
	 * Station perks (0.13+): what an upgraded station block does better than a plain one. Perks start at
	 * level II and work on an active village station of that profession, or on a bought take-home block.
	 */
	public static final class Perks {
		public boolean enabled = true;
		/** Smoker and blast furnace, level II: fuel burns this much longer. */
		public double fuelMultiplier = 1.5;
		/** Smoker, level III: cooks this much faster. */
		public double smokerSpeedMultiplier = 1.5;
		/** Brewing stand, level II: brews this much faster. */
		public double brewingSpeedMultiplier = 1.5;
		/** Blast furnace, level III: share of an armor piece's or tool's ingots given back (rounded down, at least 1). */
		public double recycleShare = 0.5;
		/** Cauldron: items a full cauldron (3 water levels) soaks before it's empty. */
		public int itemsPerFullCauldron = 64;
		/** Loom: banner layers allowed by station level (vanilla 6). */
		public int[] loomLayersByLevel = {6, 8, 12};
		/** Barrel: extra rows of storage by station level (0 to 2; the extra rows belong to the village). */
		public int[] barrelExtraRowsByLevel = {0, 1, 2};
		/** Grindstone, level III: honing with flint. Swords and axes: attack bonus for a number of hits. */
		public double honedAttackBonus = 2.0;
		public int honedHits = 40;
		/**
		 * Pickaxes, shovels, hoes: mining efficiency added for a number of blocks (works like the Efficiency
		 * enchantment: 5 is about Efficiency II). Big on stone and iron tools, small next to diamond with Efficiency V.
		 */
		public double honedMiningBonus = 5.0;
		public int honedBlocks = 64;
		/** Brewing stand, level III: XP points per bottle o' enchanting (a bottle gives back 3-11, 7 on average, so 7 = no loss). */
		public int xpPerBottle = 7;
		/** Cartography table, level III: sonar maps. How far the sonar reaches, and the depth band around the feet. */
		public int sonarRadius = 16;
		public int sonarBelow = 32;
		public int sonarAbove = 4;
	}

	/** Explosive arrows (fletcher level III). Block damage and damage to creatures are separate. */
	public static final class ExplosiveArrows {
		/** Blow up blocks (/vr explosivearrows breakblocks on|off). Ancient debris always survives. */
		public boolean breakBlocks = true;
		/** Explosion strength for blocks: 3 = creeper, 4 = TNT, 5 = bed. */
		public double blockPower = 3.0;
		/** Share of the explosion's normal damage to creatures and players. */
		public double entityDamage = 0.5;
	}

	/** Librarian's lectern messages (title cards at II, story scrolls at III), triggered by redstone. */
	public static final class Lectern {
		/** Players within this many blocks see the message (16, like a calibrated sculk sensor). */
		public int range = 16;
		/** A lectern triggers at most once per this many seconds. */
		public int cooldownSeconds = 10;
		/** The same scroll plays to the same player at most once per this many minutes. */
		public int scrollRepeatMinutes = 2;
	}

	// ---------------------------------------------------------------- loading

	public static synchronized boolean load() {
		VRConfig loaded = null;
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, VRConfig.class);
			} catch (IOException | JsonParseException e) {
				VillageRework.LOGGER.error("Could not read {}, keeping previous values: {}", FILE, e.getMessage());
				return false;
			}
		}
		if (loaded == null) {
			loaded = new VRConfig();
		}
		loaded.fillMissingSections();
		loaded.migrate();
		loaded.validate();
		instance = loaded;
		save();
		return true;
	}

	public static synchronized void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			VillageRework.LOGGER.error("Could not write {}: {}", FILE, e.getMessage());
		}
	}

	/** Gson leaves a section null if the file is missing it entirely. */
	private void fillMissingSections() {
		if (features == null) features = new Features();
		if (village == null) village = new Village();
		if (stations == null) stations = new Stations();
		if (emeralds == null) emeralds = new Emeralds();
		if (prosperity == null) prosperity = new Prosperity();
		if (rerolls == null) rerolls = new Rerolls();
		if (takeHomes == null) takeHomes = new TakeHomes();
		if (librarian == null) librarian = new Librarian();
		if (trader == null) trader = new Trader();
		if (perks == null) perks = new Perks();
		if (explosiveArrows == null) explosiveArrows = new ExplosiveArrows();
		if (lectern == null) lectern = new Lectern();
	}

	/** Moves old default values to the new defaults, once. Values someone changed are kept. */
	private void migrate() {
		if (configVersion < 2) {
			// 0.11: three reward slots by level, so each slot gets a smaller multiplier.
			if (java.util.Arrays.equals(stations.outputMultiplierByLevel, new double[]{1.0, 2.0, 3.5})) {
				stations.outputMultiplierByLevel = new double[]{1.0, 1.5, 2.0};
				VillageRework.LOGGER.info("Updated stations.outputMultiplierByLevel to the 0.11 defaults (1, 1.5, 2).");
			}
		}
		if (configVersion < 3) {
			// 0.15.1: honing buffed so it's felt in the early game; XP bottling at no loss.
			if (perks.honedAttackBonus == 1.0) perks.honedAttackBonus = 2.0;
			if (perks.honedMiningBonus == 0.2) perks.honedMiningBonus = 5.0;
			if (perks.xpPerBottle == 10) perks.xpPerBottle = 7;
		}
		if (configVersion < 4 && lectern.scrollRepeatMinutes == 5) lectern.scrollRepeatMinutes = 2; // 0.15.4
		if (configVersion < 5 && perks.sonarBelow == 4 && perks.sonarAbove == 8) {
			// 0.15.7: sonar maps look far down and only a little up, so cliffs show the ground below.
			perks.sonarBelow = 32;
			perks.sonarAbove = 4;
		}
		if (configVersion < 6 && stations.minimumOutputFactor == 0.25) {
			// 0.17.2: a day without supplies gives barely anything (one item in the first reward slot).
			stations.minimumOutputFactor = 0.0;
		}
		if (configVersion < 7) {
			// 0.19.0: bigger village borders, faster early levels, more XP on level-up.
			if (village.radius == 48) village.radius = 68;
			if (village.minDistanceBetweenVillages == 128) village.minDistanceBetweenVillages = 140;
			if (java.util.Arrays.equals(stations.progressToFillByLevel, new int[]{100, 250, 500})) {
				stations.progressToFillByLevel = new int[]{80, 200, 500};
			}
			if (stations.levelUpXp == 30) stations.levelUpXp = 91;
			VillageRework.LOGGER.info("Config moved to the 0.19.0 defaults where they were unchanged.");
		}
		if (configVersion < 8 && stations.idleRewardSlots == 1) {
			// 0.20.0: no contribution, no rewards.
			stations.idleRewardSlots = 0;
		}
		configVersion = CURRENT_CONFIG_VERSION;
	}

	/** Clamp values into sane ranges and make per-level arrays match maxLevel. */
	private void validate() {
		village.radius = clamp(village.radius, 8, 256);
		// prosperity (0.18): arrays keep their length; days at least a little, so nothing divides by zero
		if (prosperity.bands == null || prosperity.bands.length != 4) prosperity.bands = new Prosperity().bands;
		if (prosperity.production == null || prosperity.production.length != 5) prosperity.production = new Prosperity().production;
		for (int i = 0; i < 5; i++) prosperity.production[i] = clamp(prosperity.production[i], 0.0, 5.0);
		prosperity.maxGolems = clamp(prosperity.maxGolems, 0, 64);
		takeHomes.renownFirstRank = clamp(takeHomes.renownFirstRank, 0.1, 100000.0);
		takeHomes.renownRankStep = clamp(takeHomes.renownRankStep, 0.0, 100000.0);
		takeHomes.renownDiscountPerRank = clamp(takeHomes.renownDiscountPerRank, 0.0, 0.99);
		takeHomes.minPrice = clamp(takeHomes.minPrice, 1, 64);
		prosperity.hurtCooldownSeconds = clamp(prosperity.hurtCooldownSeconds, 0, 3600);
		prosperity.cureDays = clamp(prosperity.cureDays, 0.05, 365.0);
		prosperity.raidDays = clamp(prosperity.raidDays, 0.05, 365.0);
		prosperity.villagerDeathDays = clamp(prosperity.villagerDeathDays, 0.05, 365.0);
		prosperity.golemDeathDays = clamp(prosperity.golemDeathDays, 0.05, 365.0);
		prosperity.villagerHurtDays = clamp(prosperity.villagerHurtDays, 0.05, 365.0);
		prosperity.zombifiedDays = clamp(prosperity.zombifiedDays, 0.05, 365.0);
		prosperity.partyChancePerDay = clamp(prosperity.partyChancePerDay, 0.0, 1.0);
		if (prosperity.breedFood == null || prosperity.breedFood.length != 5) prosperity.breedFood = new Prosperity().breedFood;
		for (int i = 0; i < 5; i++) prosperity.breedFood[i] = prosperity.breedFood[i] < 0 ? -1 : clamp(prosperity.breedFood[i], 1, 64);
		village.minDistanceBetweenVillages = Math.max(village.minDistanceBetweenVillages, village.radius * 2);
		village.foundingMinVillagers = clamp(village.foundingMinVillagers, 0, 64);
		village.foundingMinBeds = clamp(village.foundingMinBeds, 0, 64);
		village.updateIntervalTicks = clamp(village.updateIntervalTicks, 20, 1200);
		village.discoveryRadius = clamp(village.discoveryRadius, 16, 128);
		village.panelReach = clamp(village.panelReach, 2, 32);
		village.borderDisplaySeconds = clamp(village.borderDisplaySeconds, 1, 120);
		village.residentsPerGolem = clamp(village.residentsPerGolem, 1, 1000);
		village.golemMin = clamp(village.golemMin, 0, 64);
		village.golemMax = clamp(village.golemMax, village.golemMin, 64);
		village.chestEmeraldChance = clamp(village.chestEmeraldChance, 0.0, 1.0);
		village.chestEmeraldMax = clamp(village.chestEmeraldMax, 1, 64);
		village.chestBookChance = clamp(village.chestBookChance, 0.0, 1.0);

		Stations s = stations;
		s.maxLevel = clamp(s.maxLevel, 1, 10);
		s.workerCapPerStation = clamp(s.workerCapPerStation, 1, 32);
		s.dailyInputCapByLevel = fitInts(s.dailyInputCapByLevel, s.maxLevel, "stations.dailyInputCapByLevel");
		s.rewardSlotsByLevel = fitInts(s.rewardSlotsByLevel, s.maxLevel, "stations.rewardSlotsByLevel");
		for (int i = 0; i < s.rewardSlotsByLevel.length; i++) s.rewardSlotsByLevel[i] = clamp(s.rewardSlotsByLevel[i], 1, 3);
		s.outputMultiplierByLevel = fitDoubles(s.outputMultiplierByLevel, s.maxLevel, "stations.outputMultiplierByLevel");
		s.progressToFillByLevel = fitInts(s.progressToFillByLevel, s.maxLevel, "stations.progressToFillByLevel");
		s.progressPerWorkerPerDay = Math.max(0, s.progressPerWorkerPerDay);
		s.firstWorkerWeight = clamp(s.firstWorkerWeight, 0.0, 32.0);
		s.jobReleaseDays = clamp(s.jobReleaseDays, 0.0, 365.0);
		s.hireDelayHours = clamp(s.hireDelayHours, 0.0, 240.0);
		s.minimumOutputFactor = clamp(s.minimumOutputFactor, 0.0, 1.0);
		s.maxCatchUpDays = clamp(s.maxCatchUpDays, 0, 365);
		s.levelUpXp = Math.max(0, s.levelUpXp);
		s.naturalStartProgressMin = clamp(s.naturalStartProgressMin, 0.0, 0.99);
		s.naturalStartProgressMax = clamp(s.naturalStartProgressMax, s.naturalStartProgressMin, 0.99);

		Emeralds e = emeralds;
		e.maxPerDay = clamp(e.maxPerDay, 1, 64 * 27);
		e.pointsForFullPool = clamp(e.pointsForFullPool, 0, 10000);
		e.maxPointsPerStation = clamp(e.maxPointsPerStation, 1, 1000);
		e.boostPerPoint = clamp(e.boostPerPoint, 0.0, 10.0);

		rerolls.baseCost = clamp(rerolls.baseCost, 0, 64 * 36);
		rerolls.bookBaseCost = clamp(rerolls.bookBaseCost, 0, 64 * 36);
		rerolls.maxCost = clamp(rerolls.maxCost, 1, 64 * 36);

		Librarian l = librarian;
		l.bookWeight = clamp(l.bookWeight, 0, 1000);
		if (l.tier1 == null) l.tier1 = new String[0];
		if (l.tier2 == null) l.tier2 = new String[0];
		if (l.tier3 == null) l.tier3 = new String[0];
		if (l.tierWeightsByLevel == null || l.tierWeightsByLevel.length == 0) l.tierWeightsByLevel = new Librarian().tierWeightsByLevel;
		int[][] weights = new int[s.maxLevel][];
		for (int i = 0; i < s.maxLevel; i++) {
			int[] row = l.tierWeightsByLevel[Math.min(i, l.tierWeightsByLevel.length - 1)];
			int[] fixed = new int[3];
			for (int t = 0; t < 3; t++) fixed[t] = row != null && t < row.length ? Math.max(0, row[t]) : 0;
			weights[i] = fixed;
		}
		l.tierWeightsByLevel = weights;

		if (trader.visitChanceByDaysSince == null || trader.visitChanceByDaysSince.length == 0) {
			trader.visitChanceByDaysSince = new Trader().visitChanceByDaysSince;
		}
		for (int i = 0; i < trader.visitChanceByDaysSince.length; i++) {
			trader.visitChanceByDaysSince[i] = clamp(trader.visitChanceByDaysSince[i], 0.0, 1.0);
		}

		perks.fuelMultiplier = clamp(perks.fuelMultiplier, 1.0, 10.0);
		perks.smokerSpeedMultiplier = clamp(perks.smokerSpeedMultiplier, 1.0, 10.0);
		perks.brewingSpeedMultiplier = clamp(perks.brewingSpeedMultiplier, 1.0, 10.0);
		perks.recycleShare = clamp(perks.recycleShare, 0.0, 1.0);
		perks.loomLayersByLevel = fitInts(perks.loomLayersByLevel, s.maxLevel, "perks.loomLayersByLevel");
		for (int i = 0; i < perks.loomLayersByLevel.length; i++) perks.loomLayersByLevel[i] = clamp(perks.loomLayersByLevel[i], 6, 16);
		perks.barrelExtraRowsByLevel = fitInts(perks.barrelExtraRowsByLevel, s.maxLevel, "perks.barrelExtraRowsByLevel");
		perks.honedAttackBonus = clamp(perks.honedAttackBonus, 0.0, 10.0);
		perks.honedHits = clamp(perks.honedHits, 1, 10000);
		perks.honedMiningBonus = clamp(perks.honedMiningBonus, 0.0, 100.0);
		perks.honedBlocks = clamp(perks.honedBlocks, 1, 10000);
		perks.xpPerBottle = clamp(perks.xpPerBottle, 1, 1000);
		perks.sonarRadius = clamp(perks.sonarRadius, 4, 48);
		perks.sonarBelow = clamp(perks.sonarBelow, 0, 64);
		perks.sonarAbove = clamp(perks.sonarAbove, 1, 32);
		perks.itemsPerFullCauldron = clamp(perks.itemsPerFullCauldron, 1, 64 * 27);

		explosiveArrows.blockPower = clamp(explosiveArrows.blockPower, 0.5, 8.0);
		explosiveArrows.entityDamage = clamp(explosiveArrows.entityDamage, 0.0, 2.0);

		lectern.range = clamp(lectern.range, 1, 64);
		lectern.cooldownSeconds = clamp(lectern.cooldownSeconds, 0, 3600);
		lectern.scrollRepeatMinutes = clamp(lectern.scrollRepeatMinutes, 0, 1440);

	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	private static double clamp(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}

	/** Pads (repeating the last value) or trims an array to exactly {@code length} entries. */
	private static int[] fitInts(int[] values, int length, String name) {
		if (values == null || values.length == 0) values = new int[]{1};
		if (values.length != length) {
			VillageRework.LOGGER.warn("{} has {} entries but maxLevel is {}; adjusting.", name, values.length, length);
		}
		int[] out = new int[length];
		for (int i = 0; i < length; i++) out[i] = Math.max(0, values[Math.min(i, values.length - 1)]);
		return out;
	}

	private static double[] fitDoubles(double[] values, int length, String name) {
		if (values == null || values.length == 0) values = new double[]{1.0};
		if (values.length != length) {
			VillageRework.LOGGER.warn("{} has {} entries but maxLevel is {}; adjusting.", name, values.length, length);
		}
		double[] out = new double[length];
		for (int i = 0; i < length; i++) out[i] = Math.max(0.0, values[Math.min(i, values.length - 1)]);
		return out;
	}

	// ---------------------------------------------------------------- helpers

	/** Reward slots a station fills at {@code level} (1 to 3). */
	public int rewardSlots(int level) {
		int[] slots = stations.rewardSlotsByLevel;
		return slots[Math.max(0, Math.min(level, slots.length) - 1)];
	}

	/** Points a pool of this many emeralds is worth (rounded to a whole number). */
	public int emeraldPoints(int emeralds) {
		int capped = Math.min(Math.max(0, emeralds), this.emeralds.maxPerDay);
		return (int) Math.round((double) capped * this.emeralds.pointsForFullPool / this.emeralds.maxPerDay);
	}

	/** Points each of {@code stations} stations gets (rounded, capped). */
	public int pointsPerStation(int points, int stations) {
		if (stations <= 0) return 0;
		return Math.min(this.emeralds.maxPointsPerStation, (int) Math.round((double) points / stations));
	}
}
