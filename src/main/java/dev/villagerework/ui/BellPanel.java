package dev.villagerework.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.villagerework.VillageRework;
import dev.villagerework.config.VRConfig;
import dev.villagerework.net.BellScreenPayload;
import dev.villagerework.village.ProfessionStation;
import dev.villagerework.village.StationManager;
import dev.villagerework.village.Village;
import dev.villagerework.village.VillageManager;
import dev.villagerework.village.VillageRegistry;
import dev.villagerework.village.WorkforceManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.villagerework.ui.Dialogs.button;
import static dev.villagerework.ui.Dialogs.component;
import static dev.villagerework.ui.Dialogs.coords;
import static dev.villagerework.ui.Dialogs.line;
import static dev.villagerework.ui.Dialogs.requirement;
import static dev.villagerework.ui.Dialogs.show;
import static dev.villagerework.ui.Dialogs.text;

/**
 * The bell panel, opened by shift-right-clicking a bell with an empty hand.
 *
 * Since 0.17.11 it's the mod's own bell screen (BellView → BellScreenPayload → client BellScreen); a client
 * without the mod gets the same content as a vanilla dialog. Buttons run /village commands, which re-check
 * everything on the server.
 *
 * Three versions:
 *  - center bell of a village: village overview
 *  - another bell inside a village: points to the center bell
 *  - a bell outside any village: founding requirements and "Make village"
 */
public final class BellPanel {
	private BellPanel() {
	}

	public static void open(ServerPlayer player, ServerLevel level, BlockPos bell) {
		VillageRegistry registry = VillageRegistry.get(level);
		int radius = VRConfig.get().village.radius;

		Optional<Village> center = registry.byBell(bell);
		if (center.isPresent()) {
			openVillage(player, level, center.get());
			return;
		}
		Optional<Village> owner = registry.containing(bell, radius);
		if (owner.isPresent()) {
			openOtherBell(player, level, owner.get(), bell);
			return;
		}
		openFresh(player, level, bell);
	}

	// ---------------------------------------------------------------- versions

	private static void openVillage(ServerPlayer player, ServerLevel level, Village village) {
		VRConfig cfg = VRConfig.get();
		int radius = cfg.village.radius;
		BlockPos bell = village.bellPos();

		VillageManager.Census census = VillageManager.census(level, village);
		int beds = VillageManager.countPoi(level, bell, radius, PoiTypes.HOME);
		WorkforceManager.Workforce workforce = WorkforceManager.count(level, village);

		BellView view = new BellView(bell, village.name(), ChatFormatting.DARK_GRAY)
				.subtitle(origin(level, village))
				.stat("Residents", String.valueOf(census.residents()))
				.stat("Beds", String.valueOf(beds))
				.stat("Donated emeralds", String.valueOf(village.emeraldPool()))
				.stat("Raids defended", String.valueOf(dev.villagerework.village.Prosperity.defended(level, village)))
				.mood(dev.villagerework.village.Prosperity.mood(level, village),
						dev.villagerework.village.Prosperity.gossip(level, village))
				.party(dev.villagerework.village.Prosperity.partyAllowed(level, village));
		dev.villagerework.village.Renown.Standing renown = dev.villagerework.village.Renown.standing(level, village);
		view.renown(renown.rank(), renown.fill());
		addStations(view, village, workforce);
		view.button("Show border", "Mark the edge of the village for a few seconds", "/village border " + coords(bell))
				.button("Rename", "Give " + village.name() + " a new name", "/village rename " + coords(bell))
				.dangerButton("Disband", "Remove " + village.name() + " and all its records", "/village disband " + coords(bell))
				.show(player);
	}

	/**
	 * All thirteen professions in a fixed order (0.17.11): working stations, ones that lost their block (they keep
	 * their level), and ones the village doesn't have yet.
	 */
	private static void addStations(BellView view, Village village, WorkforceManager.Workforce workforce) {
		for (Map.Entry<String, net.minecraft.world.item.Item> e : dev.villagerework.perk.TakeHomes.BLOCKS.entrySet()) {
			String key = e.getKey();
			String item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getValue()).toString();
			Optional<ProfessionStation> p = village.profession(key);
			int state = BellScreenPayload.Station.NONE;
			int stationLevel = 0;
			if (p.isPresent() && p.get().active()) {
				state = BellScreenPayload.Station.ACTIVE;
				stationLevel = p.get().level();
			} else if (p.isPresent() && waitingForStation(p.get(), workforce.workers(key))) {
				state = BellScreenPayload.Station.NEEDS_BLOCK;
				stationLevel = p.get().level();
			}
			view.station(StationManager.displayName(key), item, stationLevel, state);
		}
	}

	/** A profession without a station is listed if it has progress to keep, or workers waiting for it. */
	private static boolean waitingForStation(ProfessionStation p, int waiting) {
		return !p.active() && (p.hasProgress() || waiting > 0);
	}

	private static void openOtherBell(ServerPlayer player, ServerLevel level, Village village, BlockPos bell) {
		BlockPos center = village.bellPos();
		int distance = (int) Math.round(Math.sqrt(bell.distSqr(center)));
		BellView view = new BellView(bell, "A bell in " + village.name(), ChatFormatting.DARK_GRAY);

		if (!village.dormant()) {
			view.text("This bell is inside " + village.name() + ".", ChatFormatting.BLACK)
					.text("Villagers gather at the village's center bell, " + distance + " blocks away at "
							+ center.toShortString() + ". This bell still rings as normal.", ChatFormatting.DARK_GRAY)
					.button("Show border", "Mark the edge of " + village.name(), "/village border " + coords(center))
					.show(player);
			return;
		}

		// The village has lost its center bell. Explain whether this bell can take over.
		int minDistance = VRConfig.get().village.minDistanceBetweenVillages;
		Optional<Village> blocker = VillageManager.tooCloseTo(VillageRegistry.get(level), village, bell);
		view.text(village.name() + " has lost its center bell and is dormant.", BellView.WARNING);
		if (blocker.isPresent()) {
			view.requirement(false, "This bell can't become the new center: it's "
					+ (int) blocker.get().horizontalDistance(bell) + " blocks from " + blocker.get().name()
					+ ", and villages need " + minDistance + " blocks between them.")
					.text("Place a bell further from " + blocker.get().name() + ", or disband " + village.name()
							+ " if you want to start over.", ChatFormatting.DARK_GRAY);
		} else {
			view.requirement(true, "This bell will become the new center of " + village.name() + " in a moment.");
		}
		view.dangerButton("Disband", "Remove " + village.name() + " and all its records", "/village disband " + coords(bell))
				.show(player);
	}

	/** Asks before a village is removed for good. */
	public static void openDisbandConfirm(ServerPlayer player, Village village, BlockPos bell) {
		BellView view = new BellView(bell, "Disband " + village.name() + "?", ChatFormatting.DARK_RED)
				.text("The village and all its records are removed: its name, its stations, and every profession "
						+ "level it has reached. Blocks and villagers stay where they are.", ChatFormatting.DARK_GRAY)
				.text("Items left in the stations (donations, rewards, upgrade items, extra barrel rows) are dropped at "
						+ "each station. Stations in areas nobody is near right now may lose them: empty the stations "
						+ "first to be safe.", BellView.WARNING)
				.text("This can't be undone.", ChatFormatting.DARK_RED);
		if (village.natural()) {
			view.text("It's a natural village, so it won't come back by itself. You can found a new village "
					+ "from a bell afterwards.", ChatFormatting.DARK_GRAY);
		}
		view.dangerButton("Yes, disband", "Remove " + village.name() + " for good",
						"/village disband " + coords(bell) + " confirm")
				.button("Back", "Back to " + village.name(), "/village panel " + coords(village.bellPos()))
				.show(player);
	}

	private static void openFresh(ServerPlayer player, ServerLevel level, BlockPos bell) {
		VRConfig cfg = VRConfig.get();
		VillageManager.FoundingCheck check = VillageManager.checkFounding(level, bell);

		BellView view = new BellView(bell, "Unclaimed bell", ChatFormatting.DARK_GRAY)
				.text("This bell doesn't belong to a village yet. Villagers who live here can make it their center.",
						ChatFormatting.DARK_GRAY)
				.requirement(check.enoughVillagers(),
						"Villagers nearby: " + check.villagers() + " / " + cfg.village.foundingMinVillagers)
				.requirement(check.enoughBeds(),
						"Beds nearby: " + check.beds() + " / " + cfg.village.foundingMinBeds)
				.requirement(check.tooClose().isEmpty(), check.tooClose()
						.map(v -> "Too close to " + v.name() + " (" + (int) v.horizontalDistance(bell) + " blocks, needs "
								+ cfg.village.minDistanceBetweenVillages + ")")
						.orElse("No other village within " + cfg.village.minDistanceBetweenVillages + " blocks"));
		if (check.ok()) {
			view.button("Make village", "Found a village with this bell as its center", "/village make " + coords(bell));
		}
		view.button("Preview size", "Show how far the village would reach", "/village border " + coords(bell))
				.show(player);
	}

	/**
	 * A small dialog with a text field. Built from JSON (the same format datapacks use
	 * for dialogs), because input fields are easiest to describe that way.
	 * If it can't be built, the player gets a clickable chat suggestion instead.
	 */
	public static void openRename(ServerPlayer player, ServerLevel level, Village village) {
		String command = "/village rename " + coords(village.bellPos()) + " ";

		JsonObject json = new JsonObject();
		json.addProperty("type", "minecraft:confirmation");
		json.add("title", textJson("Rename " + village.name(), "gold"));
		json.addProperty("can_close_with_escape", true);
		json.addProperty("pause", false);

		JsonObject input = new JsonObject();
		input.addProperty("type", "minecraft:text");
		input.addProperty("key", "name");
		input.add("label", textJson("New name", null));
		input.addProperty("initial", village.name());
		input.addProperty("max_length", Village.MAX_NAME_LENGTH);
		input.addProperty("width", 200);
		JsonArray inputs = new JsonArray();
		inputs.add(input);
		json.add("inputs", inputs);

		JsonObject yesAction = new JsonObject();
		yesAction.addProperty("type", "minecraft:dynamic/run_command");
		yesAction.addProperty("template", command + "$(name)");
		JsonObject yes = new JsonObject();
		yes.add("label", textJson("Rename", null));
		yes.add("action", yesAction);
		json.add("yes", yes);

		JsonObject noAction = new JsonObject();
		noAction.addProperty("type", "minecraft:run_command");
		noAction.addProperty("command", "/village panel " + coords(village.bellPos()));
		JsonObject no = new JsonObject();
		no.add("label", textJson("Cancel", null));
		no.add("action", noAction);
		json.add("no", no);

		DataResult<Dialog> result = Dialog.DIRECT_CODEC.parse(
				level.registryAccess().createSerializationContext(JsonOps.INSTANCE), json);
		Optional<Dialog> dialog = result.resultOrPartial(error -> VillageRework.LOGGER.error("Rename dialog failed: {}", error));
		if (dialog.isPresent()) {
			player.openDialog(Holder.direct(dialog.get()));
			return;
		}
		player.sendSystemMessage(Component.literal("[Click to rename " + village.name() + "]")
				.withStyle(style -> style.withColor(ChatFormatting.GOLD)
						.withClickEvent(new ClickEvent.SuggestCommand(command))));
	}

	private static JsonObject textJson(String text, String color) {
		JsonObject o = new JsonObject();
		o.addProperty("text", text);
		if (color != null) o.addProperty("color", color);
		return o;
	}

	private static String origin(ServerLevel level, Village village) {
		String how;
		if (village.natural()) {
			how = "A natural village";
		} else {
			how = village.founder()
					.map(id -> level.getServer().getPlayerList().getPlayer(id))
					.map(p -> "Founded by " + p.getName().getString())
					.orElse("Founded by a player");
		}
		return how;
	}

}
