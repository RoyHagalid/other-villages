package dev.villagerework.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.villagerework.config.VRConfig;
import dev.villagerework.trader.TraderVisits;
import dev.villagerework.village.StationManager;
import dev.villagerework.village.StationTypes;
import dev.villagerework.village.Upgrades;
import dev.villagerework.village.ProfessionStation;
import dev.villagerework.village.WorkCycle;
import dev.villagerework.village.Village;
import dev.villagerework.village.VillageManager;
import dev.villagerework.village.VillageRegistry;
import dev.villagerework.village.WorkforceManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Debug commands, all under /vr (also /villagerework). Operator only.
 *
 * Phase 0:
 *   /vr config reload          re-read config/villagerework.json
 *   /vr config show            print the most important values
 *   /vr emeralds [count]       preview the diminishing-returns curve
 *   /vr scan [radius]          list village POIs and villagers around you (vanilla data)
 *
 * Phase 1:
 *   /vr village list           every village in this dimension
 *   /vr village info           details of the nearest village
 *   /vr village remove         delete the nearest village (the bell stays)
 *   /vr village sync           run the village update right now instead of waiting
 *
 * Phase 3:
 *   /vr station list                      professions of the nearest village
 *   /vr station setlevel <profession> <level>   set a profession's level (e.g. librarian 2)
 *   /vr station fill <profession>         fill the progress bar (to test upgrading)
 *
 * Phase 5:
 *   /vr day run                 run the evening work now for the nearest village (uses donations)
 *   /vr explosivearrows breakblocks [on|off]   whether explosive arrows blow up blocks (saved in the config)
 *   /vr trader status|due      days since the trader's last visit and today's chance; make him due now
 *   /vr day sunrise             give every working station in the nearest village a fresh request
 *   /vr day status              time of day, latest work day, each station's request
 */
public final class VRCommands {
	private VRCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralCommandNode<CommandSourceStack> root = dispatcher.register(literal("vr")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(literal("config")
						.then(literal("reload").executes(VRCommands::configReload))
						.then(literal("show").executes(VRCommands::configShow)))
				.then(literal("emeralds")
						.executes(ctx -> emeraldCurve(ctx, 40))
						.then(argument("count", IntegerArgumentType.integer(1, 512))
								.executes(ctx -> emeraldCurve(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
				.then(literal("scan")
						.executes(ctx -> scan(ctx, 48))
						.then(argument("radius", IntegerArgumentType.integer(4, 128))
								.executes(ctx -> scan(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
				.then(literal("village")
						.then(literal("list").executes(VRCommands::villageList))
						.then(literal("info").executes(VRCommands::villageInfo))
						.then(literal("remove").executes(VRCommands::villageRemove))
						.then(literal("sync").executes(VRCommands::villageSync)))
				.then(literal("explosivearrows")
						.then(literal("breakblocks")
								.executes(VRCommands::explosiveStatus)
								.then(literal("on").executes(ctx -> explosiveBreakBlocks(ctx, true)))
								.then(literal("off").executes(ctx -> explosiveBreakBlocks(ctx, false)))))
				.then(literal("prosperity")
						.executes(VRCommands::prosperityShow)
						.then(literal("clear").executes(VRCommands::prosperityClear))
						.then(literal("event")
								.then(argument("kind", StringArgumentType.word())
										.suggests((c, b) -> {
											for (String k : new String[] {"cure", "raid", "death", "golem_death", "hurt", "zombified"}) b.suggest(k);
											return b.buildFuture();
										})
										.executes(VRCommands::prosperityEvent))))
				.then(literal("renown")
						.executes(ctx -> renown(ctx, -1))
						.then(literal("set").then(argument("points", IntegerArgumentType.integer(0, 1000000))
								.executes(ctx -> renown(ctx, IntegerArgumentType.getInteger(ctx, "points"))))))
				.then(literal("trader")
						.then(literal("status").executes(VRCommands::traderStatus))
						.then(literal("due").executes(VRCommands::traderDue)))
				.then(literal("day")
						.then(literal("run").executes(VRCommands::dayRun))
						.then(literal("sunrise").executes(VRCommands::daySunrise))
						.then(literal("status").executes(VRCommands::dayStatus)))
				.then(literal("station")
						.then(literal("list").executes(VRCommands::stationList))
						.then(literal("fill")
								.then(argument("profession", StringArgumentType.word())
										.suggests(VRCommands::suggestProfessions)
										.executes(VRCommands::stationFill)))
						.then(literal("setlevel")
								.then(argument("profession", StringArgumentType.word())
										.suggests(VRCommands::suggestProfessions)
										.then(argument("level", IntegerArgumentType.integer(1, 10))
												.executes(VRCommands::stationSetLevel)))))
				.then(literal("takehome")
						.then(argument("profession", StringArgumentType.word())
								.suggests(VRCommands::suggestProfessions)
								.then(argument("tier", IntegerArgumentType.integer(2, 3))
										.executes(VRCommands::takeHome)))));

		dispatcher.register(literal("villagerework")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.redirect(root));
	}

	// ---------------------------------------------------------------- config

	private static int configReload(CommandContext<CommandSourceStack> ctx) {
		if (VRConfig.load()) {
			ctx.getSource().sendSuccess(() -> Component.literal("Other: Villages config reloaded."), true);
			warnVanillaSwitches(ctx);
			return 1;
		}
		ctx.getSource().sendFailure(Component.literal("Config has errors; kept the previous values. See the server log."));
		return 0;
	}

	private static int configShow(CommandContext<CommandSourceStack> ctx) {
		VRConfig c = VRConfig.get();
		send(ctx, "§6Other: Villages config");
		send(ctx, "§7Village: §fradius " + c.village.radius
				+ ", min distance " + c.village.minDistanceBetweenVillages
				+ ", founding needs " + c.village.foundingMinVillagers + " villagers / " + c.village.foundingMinBeds + " beds");
		send(ctx, "§7Stations: §fmax level " + c.stations.maxLevel
				+ ", worker cap " + c.stations.workerCapPerStation
				+ ", catch-up " + c.stations.maxCatchUpDays + " days");
		send(ctx, "§7  input cap per level: §f" + Arrays.toString(c.stations.dailyInputCapByLevel));
		send(ctx, "§7  reward slots per level: §f" + Arrays.toString(c.stations.rewardSlotsByLevel));
		send(ctx, "§7  output multiplier per level: §f" + Arrays.toString(c.stations.outputMultiplierByLevel));
		send(ctx, "§7  rerolls: §f" + (c.rerolls.enabled ? "on" : "off") + "§7, first §f" + c.rerolls.baseCost
				+ "§7 (book §f" + c.rerolls.bookBaseCost + "§7), doubling, max §f" + c.rerolls.maxCost);
		send(ctx, "§7  librarian books: weight §f" + c.librarian.bookWeight + "§7, tiers §f" + c.librarian.tier1.length
				+ "/" + c.librarian.tier2.length + "/" + c.librarian.tier3.length);
		send(ctx, "§7  progress to fill per level: §f" + Arrays.toString(c.stations.progressToFillByLevel));
		send(ctx, "§7Emeralds: §f" + c.emeralds.maxPerDay + " per day = " + c.emeralds.pointsForFullPool
				+ " points, max " + c.emeralds.maxPointsPerStation + " per station, " + Math.round(c.emeralds.boostPerPoint * 100) + "% per point");
		send(ctx, "§7Vanilla: §ftrading " + (c.features.disableVanillaTrading ? "disabled" : "enabled")
				+ ", job claiming " + (c.features.disableVanillaJobClaiming ? "disabled" : "enabled")
				+ " §8(applies immediately)");
		return 1;
	}

	// ---------------------------------------------------------------- emerald curve

	/** Previews what a day's emeralds do for 1 to 6 eligible stations. */
	private static int emeraldCurve(CommandContext<CommandSourceStack> ctx, int count) {
		VRConfig c = VRConfig.get();
		int points = c.emeraldPoints(count);
		send(ctx, "§6" + count + " emeralds in one day → " + points + " points"
				+ (count > c.emeralds.maxPerDay ? " §8(" + (count - c.emeralds.maxPerDay) + " wasted)" : ""));
		for (int n = 1; n <= 6; n++) {
			int each = c.pointsPerStation(points, n);
			send(ctx, String.format("§7  %d eligible station(s): §f%d points each = +%d%%", n, each,
					Math.round(each * c.emeralds.boostPerPoint * 100)));
		}
		return points;
	}

	// ---------------------------------------------------------------- vanilla village scan

	private static int scan(CommandContext<CommandSourceStack> ctx, int radius) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();
		BlockPos center = BlockPos.containing(source.getPosition());
		PoiManager poiManager = level.getPoiManager();
		Predicate<Holder<PoiType>> isVillagePoi = holder -> holder.is(PoiTypeTags.VILLAGE);

		Map<String, int[]> counts = new TreeMap<>(); // name -> {total, occupied}
		poiManager.getInRange(isVillagePoi, center, radius, PoiManager.Occupancy.ANY)
				.forEach(record -> counts.computeIfAbsent(name(record), k -> new int[2])[0]++);
		poiManager.getInRange(isVillagePoi, center, radius, PoiManager.Occupancy.IS_OCCUPIED)
				.forEach(record -> counts.computeIfAbsent(name(record), k -> new int[2])[1]++);

		int villagers = level.getEntitiesOfClass(Villager.class, new AABB(center).inflate(radius)).size();

		send(ctx, "§6Vanilla village scan §7(radius " + radius + " around " + center.toShortString() + ")");
		send(ctx, "§7Counts as a village here: §f" + (level.isVillage(center) ? "yes" : "no"));
		send(ctx, "§7Villagers: §f" + villagers);
		if (counts.isEmpty()) {
			send(ctx, "§7No village points of interest found.");
		} else {
			counts.forEach((poiName, n) -> send(ctx, "§7  " + poiName + ": §f" + n[0] + " §8(" + n[1] + " claimed)"));
		}
		return villagers;
	}

	private static String name(PoiRecord record) {
		return record.getPoiType().unwrapKey().map(key -> key.identifier().getPath()).orElse("unknown");
	}

	// ---------------------------------------------------------------- villages

	private static int villageList(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getLevel();
		BlockPos here = BlockPos.containing(ctx.getSource().getPosition());
		List<Village> villages = VillageRegistry.get(level).all();
		send(ctx, "§6" + villages.size() + " village(s) in " + level.dimension().identifier());
		villages.stream()
				.sorted(Comparator.comparingDouble(v -> v.horizontalDistance(here)))
				.forEach(v -> send(ctx, "§7  " + v.name() + " §8at " + v.bellPos().toShortString()
						+ ", " + (int) v.horizontalDistance(here) + " blocks away"
						+ (v.natural() ? ", natural" : ", founded")
						+ (v.dormant() ? " §e(dormant)" : "")));
		return villages.size();
	}

	private static int villageInfo(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getLevel();
		BlockPos here = BlockPos.containing(ctx.getSource().getPosition());
		Optional<Village> nearest = VillageRegistry.get(level).nearest(here);
		if (nearest.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("No villages in this dimension."));
			return 0;
		}
		Village v = nearest.get();
		int radius = VRConfig.get().village.radius;
		boolean loaded = level.isLoaded(v.bellPos());
		send(ctx, "§6" + v.name() + " §8(" + v.id() + ")");
		send(ctx, "§7Center bell: §f" + v.bellPos().toShortString() + " §8(" + (int) v.horizontalDistance(here) + " blocks away)");
		send(ctx, "§7Origin: §f" + (v.natural() ? "natural" : "founded")
				+ v.founder().map(id -> " by " + id).orElse("") + ", day " + v.foundedDay());
		send(ctx, "§7Emerald pool: §f" + v.emeraldPool() + " §8(last used on day " + v.poolDay() + ")"
				+ (v.donors().isEmpty() ? "" : "§7, donors: §f" + v.donors().size()));
		send(ctx, "§7State: §f" + (v.dormant() ? "dormant (bell missing)" : "active")
				+ (loaded ? "" : " §8(not loaded)"));
		if (loaded) {
			VillageManager.Census c = VillageManager.census(level, v);
			send(ctx, "§7Residents: §f" + c.residents() + " §8(" + c.withBed() + " with a bed inside)");
			send(ctx, "§7Gathering: §f" + (c.isGatheringTime()
					? c.gathering() + " in meet activity, " + c.atBell() + " within 8 blocks of the bell"
					: "not gathering time"));
			send(ctx, "§7Beds inside: §f" + VillageManager.countPoi(level, v.bellPos(), radius, PoiTypes.HOME)
					+ "§7, bells inside: §f" + VillageManager.countPoi(level, v.bellPos(), radius, PoiTypes.MEETING));
			send(ctx, "§7Inside a natural village structure: §f" + (VillageManager.isInNaturalVillage(level, v.bellPos()) ? "yes" : "no"));
		}
		return 1;
	}

	private static int villageRemove(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getLevel();
		BlockPos here = BlockPos.containing(ctx.getSource().getPosition());
		VillageRegistry registry = VillageRegistry.get(level);
		Optional<Village> nearest = registry.nearest(here);
		if (nearest.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("No villages in this dimension."));
			return 0;
		}
		registry.remove(nearest.get().id());
		ctx.getSource().sendSuccess(() -> Component.literal("Removed " + nearest.get().name()
				+ ". A natural village will be rediscovered on the next update; use the bell panel to found a player village again."), true);
		return 1;
	}

	private static int villageSync(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getLevel();
		VillageManager.forceUpdate(level);
		ctx.getSource().sendSuccess(() -> Component.literal("Village update done. "
				+ VillageRegistry.get(level).size() + " village(s) in this dimension."), false);
		return 1;
	}

	// ---------------------------------------------------------------- work day

	private static Optional<Village> nearestVillage(CommandContext<CommandSourceStack> ctx) {
		Optional<Village> nearest = VillageRegistry.get(ctx.getSource().getLevel())
				.nearest(BlockPos.containing(ctx.getSource().getPosition()));
		if (nearest.isEmpty()) ctx.getSource().sendFailure(Component.literal("No villages in this dimension."));
		return nearest;
	}

	private static int prosperityShow(CommandContext<CommandSourceStack> ctx) {
		Optional<Village> v = nearestVillage(ctx);
		if (v.isEmpty()) return 0;
		send(ctx, dev.villagerework.village.Prosperity.describe(ctx.getSource().getLevel(), v.get()));
		return 1;
	}

	/** /vr renown [set <points>] (0.20.0): the nearest village's renown points, rank and prices. */
	private static int renown(CommandContext<CommandSourceStack> ctx, int set) {
		Optional<Village> v = nearestVillage(ctx);
		if (v.isEmpty()) return 0;
		ServerLevel level = ctx.getSource().getLevel();
		if (set >= 0) dev.villagerework.village.Renown.set(level, v.get(), set);
		var standing = dev.villagerework.village.Renown.standing(level, v.get());
		int[] base = VRConfig.get().takeHomes.priceByTier;
		send(ctx, v.get().name() + ": renown " + String.format(java.util.Locale.ROOT, "%.1f",
				dev.villagerework.village.Renown.points(level, v.get())) + " points, rank " + standing.rank()
				+ " (" + Math.round(standing.fill() * 100) + "% to the next); take-home prices "
				+ dev.villagerework.village.Renown.price(level, v.get(), base[0]) + " / "
				+ dev.villagerework.village.Renown.price(level, v.get(), base[Math.min(1, base.length - 1)]) + " emeralds");
		return 1;
	}

	private static int prosperityClear(CommandContext<CommandSourceStack> ctx) {
		Optional<Village> v = nearestVillage(ctx);
		if (v.isEmpty()) return 0;
		dev.villagerework.village.Prosperity.clearMemories(ctx.getSource().getLevel(), v.get());
		send(ctx, v.get().name() + " forgot everything that happened.");
		return 1;
	}

	private static int prosperityEvent(CommandContext<CommandSourceStack> ctx) {
		Optional<Village> v = nearestVillage(ctx);
		if (v.isEmpty()) return 0;
		String kind = StringArgumentType.getString(ctx, "kind");
		if (!dev.villagerework.village.Prosperity.debugEvent(ctx.getSource().getLevel(), v.get(), kind)) {
			ctx.getSource().sendFailure(Component.literal("Unknown event: " + kind));
			return 0;
		}
		send(ctx, dev.villagerework.village.Prosperity.describe(ctx.getSource().getLevel(), v.get()));
		return 1;
	}

	private static int dayRun(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getLevel();
		Optional<Village> nearest = VillageRegistry.get(level).nearest(BlockPos.containing(ctx.getSource().getPosition()));
		if (nearest.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("No villages in this dimension."));
			return 0;
		}
		WorkCycle.runNow(level, nearest.get());
		ctx.getSource().sendSuccess(() -> Component.literal("Ran one work day for " + nearest.get().name() + "."), true);
		return 1;
	}

	private static int daySunrise(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getLevel();
		Optional<Village> nearest = VillageRegistry.get(level).nearest(BlockPos.containing(ctx.getSource().getPosition()));
		if (nearest.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("No villages in this dimension."));
			return 0;
		}
		WorkCycle.sunriseNow(level, nearest.get());
		ctx.getSource().sendSuccess(() -> Component.literal("New requests for " + nearest.get().name() + "."), true);
		return 1;
	}

	private static int dayStatus(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getLevel();
		long time = level.getOverworldClockTime();
		send(ctx, "§6Day " + Math.floorDiv(time, 24000L) + ", time of day " + Math.floorMod(time, 24000L)
				+ " §8(villagers gather at " + WorkCycle.GATHER_TIME + ")");
		send(ctx, "§7Latest work day: §f" + WorkCycle.currentWorkDay(level));
		Optional<Village> nearest = VillageRegistry.get(level).nearest(BlockPos.containing(ctx.getSource().getPosition()));
		nearest.ifPresent(v -> v.professions().forEach((key, p) -> {
			if (StationTypes.get(key).isEmpty() || !p.active()) return;
			send(ctx, "§7  " + StationManager.displayName(key) + ": §flast worked day " + p.lastWorkDay()
					+ (p.hasOpenRequest() ? ", wants " + p.requestCount() + " " + p.requestItem().orElse("?") : ", no request until sunrise")
					+ (p.lastContribution() >= 0 ? ", last " + Math.round(p.lastContribution() * 100) + "%" : ""));
		}));
		return 1;
	}

	// ---------------------------------------------------------------- stations

	private static int stationList(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getLevel();
		Optional<Village> nearest = VillageRegistry.get(level).nearest(BlockPos.containing(ctx.getSource().getPosition()));
		if (nearest.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("No villages in this dimension."));
			return 0;
		}
		Village v = nearest.get();
		send(ctx, "§6Professions of " + v.name() + " §8(" + v.activeStationCount() + " active"
				+ (v.natural() ? ", seeded: " + v.stationsSeeded() : "") + ")");
		if (v.professions().isEmpty()) send(ctx, "§7  none");
		WorkforceManager.Workforce wf = WorkforceManager.count(level, v);
		v.professions().forEach((key, p) -> send(ctx, "§7  " + StationManager.displayName(key) + " §8(" + key + ")§7: §flevel "
				+ p.level() + ", progress " + (int) p.progress() + ", " + wf.workers(key) + " worker(s) §7"
				+ p.stationPos().map(pos -> "at " + pos.toShortString()).orElse("§8no station")));
		send(ctx, "§7Unemployed adults: §f" + wf.unemployed() + "§7, nitwits: §f" + wf.nitwits());
		warnVanillaSwitches(ctx);
		return v.professions().size();
	}

	/** Tab completion: the short names of every station profession (farmer, shepherd, ...). */
	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestProfessions(
			CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
		String typed = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
		for (String key : StationTypes.keys()) {
			String name = key.substring(key.indexOf(':') + 1);
			if (name.startsWith(typed)) builder.suggest(name);
		}
		return builder.buildFuture();
	}

	/** The profession key typed in the command, or null (with an error listing the valid names). */
	private static String professionArg(CommandContext<CommandSourceStack> ctx) {
		String key = StationManager.normalizeKey(StringArgumentType.getString(ctx, "profession"));
		if (StationTypes.get(key).isPresent()) return key;
		List<String> names = StationTypes.keys().stream().map(k -> k.substring(k.indexOf(':') + 1)).sorted().toList();
		ctx.getSource().sendFailure(Component.literal("Unknown profession \"" + StringArgumentType.getString(ctx, "profession")
				+ "\". Use one of: " + String.join(", ", names)));
		return null;
	}

	/** Fills a profession's progress bar, so the Upgrade button can be tested right away. */
	private static int stationFill(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getLevel();
		VillageRegistry registry = VillageRegistry.get(level);
		Optional<Village> nearest = registry.nearest(BlockPos.containing(ctx.getSource().getPosition()));
		if (nearest.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("No villages in this dimension."));
			return 0;
		}
		String key = professionArg(ctx);
		if (key == null) return 0;
		if (nearest.get().profession(key).isEmpty()) registry.setProfessionLevel(nearest.get(), key, 1, 0);
		Optional<ProfessionStation> p = nearest.get().profession(key);
		registry.setProfessionLevel(nearest.get(), key, p.get().level(), Upgrades.fill(p.get()));
		ctx.getSource().sendSuccess(() -> Component.literal(StationManager.displayName(key) + " of "
				+ nearest.get().name() + " has a full bar."), true);
		return 1;
	}

	/** Gives a take-home workstation item (for testing). */
	private static int takeHome(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		String key = StationManager.normalizeKey(StringArgumentType.getString(ctx, "profession"));
		net.minecraft.world.item.ItemStack stack = dev.villagerework.perk.TakeHomes.item(key, IntegerArgumentType.getInteger(ctx, "tier"));
		if (stack.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("No take-home for " + StationManager.displayName(key) + "."));
			return 0;
		}
		net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
		Component name = stack.getHoverName();
		if (!player.getInventory().add(stack)) net.minecraft.world.Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), stack);
		ctx.getSource().sendSuccess(() -> Component.literal("Gave ").append(name), false);
		return 1;
	}

	private static int stationSetLevel(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getLevel();
		VillageRegistry registry = VillageRegistry.get(level);
		Optional<Village> nearest = registry.nearest(BlockPos.containing(ctx.getSource().getPosition()));
		if (nearest.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("No villages in this dimension."));
			return 0;
		}
		String key = professionArg(ctx);
		if (key == null) return 0;
		int lvl = Math.min(IntegerArgumentType.getInteger(ctx, "level"), VRConfig.get().stations.maxLevel);
		registry.setProfessionLevel(nearest.get(), key, lvl, 0);
		ctx.getSource().sendSuccess(() -> Component.literal(StationManager.displayName(key) + " of "
				+ nearest.get().name() + " is now level " + lvl + "."), true);
		return 1;
	}

	/** Reminds the operator when a vanilla switch is back on, since that changes how villagers behave. */
	private static void warnVanillaSwitches(CommandContext<CommandSourceStack> ctx) {
		VRConfig.Features f = VRConfig.get().features;
		if (!f.disableVanillaJobClaiming) {
			send(ctx, "§eVanilla job claiming is ON (features.disableVanillaJobClaiming = false). "
					+ "The workforce is paused and villagers take jobs the vanilla way.");
		}
		if (!f.disableVanillaTrading) {
			send(ctx, "§eVanilla trading is ON (features.disableVanillaTrading = false).");
		}
	}

	private static int explosiveStatus(CommandContext<CommandSourceStack> ctx) {
		send(ctx, "§7Explosive arrows break blocks: §f" + (VRConfig.get().explosiveArrows.breakBlocks ? "on" : "off"));
		return 1;
	}

	private static int explosiveBreakBlocks(CommandContext<CommandSourceStack> ctx, boolean on) {
		VRConfig.get().explosiveArrows.breakBlocks = on;
		VRConfig.save();
		ctx.getSource().sendSuccess(() -> Component.literal("Explosive arrows " + (on ? "now break blocks." : "no longer break blocks.")), true);
		return 1;
	}

	private static int traderStatus(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getServer().overworld();
		TraderVisits visits = TraderVisits.get(level);
		boolean present = visits.traderPresent(level);
		long days = visits.daysSinceLeft(level);
		VRConfig.Trader cfg = VRConfig.get().trader;
		send(ctx, "§6Wandering trader §8(" + (cfg.guaranteedVisits ? "mod visits" : "vanilla visits")
				+ ", " + (cfg.replaceTrades ? "mod trades" : "vanilla trades") + ")");
		if (present) {
			send(ctx, "§7  the trader is here; the count starts when he leaves");
		} else {
			send(ctx, "§7  days since the last one left: §f" + days + "§7, today's chance: §f"
					+ Math.round(TraderVisits.chance(days) * 100) + "%" + (visits.pending() ? " §a(due, looking for a spot)" : ""));
		}
		return 1;
	}

	private static int traderDue(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getServer().overworld();
		TraderVisits.get(level).forceDue(level);
		ctx.getSource().sendSuccess(() -> Component.literal("The wandering trader is due; he arrives within a minute near a random player."), true);
		return 1;
	}

	private static void send(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
	}
}
