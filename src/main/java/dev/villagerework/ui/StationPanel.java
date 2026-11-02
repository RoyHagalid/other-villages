package dev.villagerework.ui;

import dev.villagerework.config.VRConfig;
import dev.villagerework.perk.BarrelPerks;
import dev.villagerework.perk.Perks;
import dev.villagerework.village.ContainerSlice;
import dev.villagerework.village.ProfessionStation;
import dev.villagerework.village.Rerolls;
import dev.villagerework.village.StationManager;
import dev.villagerework.village.StationInventory;
import dev.villagerework.village.StationTypes;
import dev.villagerework.village.Upgrades;
import dev.villagerework.village.Village;
import dev.villagerework.village.VillageRegistry;
import dev.villagerework.village.WorkforceManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static dev.villagerework.ui.Dialogs.button;
import static dev.villagerework.ui.Dialogs.coords;
import static dev.villagerework.ui.Dialogs.line;
import static dev.villagerework.ui.Dialogs.show;
import static dev.villagerework.ui.Dialogs.text;

/**
 * Shift-right-click a workstation with an empty hand:
 *  - an active station: its station screen (supplies, today's request, status, goods),
 *    or a status panel for professions whose daily work isn't in yet
 *  - a workstation that can be donated: a donate prompt
 *  - anything else: nothing, so vanilla behavior happens (storage barrels open as normal)
 */
public final class StationPanel {
	private StationPanel() {
	}

	/** Returns true if a panel was opened (and the click should be consumed). */
	public static boolean tryOpen(ServerPlayer player, ServerLevel level, BlockPos pos) {
		StationManager.Donation d = StationManager.checkDonation(level, pos);
		switch (d.check()) {
			case ALREADY_STATION -> {
				openStation(player, level, d.village().orElseThrow(), d.profession().orElseThrow(), pos);
				return true;
			}
			case OK -> {
				openDonate(player, d.village().orElseThrow(), d.profession().orElseThrow(), pos, level);
				return true;
			}
			case PROFESSION_TAKEN -> {
				// One station per profession: say where the current one is, instead of silently doing nothing.
				Village v = d.village().orElseThrow();
				String profession = d.profession().orElseThrow();
				String where = v.profession(profession).flatMap(ProfessionStation::stationPos)
						.map(p -> " at " + p.getX() + " " + p.getY() + " " + p.getZ()).orElse("");
				player.sendOverlayMessage(Component.literal(v.name() + " already has a "
						+ StationManager.displayName(profession) + " station" + where).withStyle(ChatFormatting.GRAY));
				return true;
			}
			case TAKE_HOME -> {
				// Say why nothing is offered, then let the block's own screen open as usual.
				player.sendOverlayMessage(Component.literal("A bought workstation can't be donated").withStyle(ChatFormatting.GRAY));
				return false;
			}
			default -> {
				return false;
			}
		}
	}

	public static void open(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (!tryOpen(player, level, pos)) {
			player.sendSystemMessage(Component.literal("That workstation can't be donated right now.").withStyle(ChatFormatting.GRAY));
		}
	}

	private static void openStation(ServerPlayer player, ServerLevel level, Village village, String profession, BlockPos pos) {
		if (StationTypes.get(profession).isPresent()) {
			openScreen(player, level, village, profession, pos);
			return;
		}
		// Professions whose daily work isn't in yet get a status panel.
		ProfessionStation p = village.profession(profession).orElseThrow();
		VRConfig.Stations cfg = VRConfig.get().stations;
		int levelIndex = Math.min(p.level(), cfg.maxLevel) - 1;
		boolean maxed = p.level() >= cfg.maxLevel;

		List<DialogBody> body = new ArrayList<>();
		body.add(text("This workstation belongs to " + village.name() + ".", ChatFormatting.GRAY));
		body.add(line("Level", p.level() + " / " + cfg.maxLevel));
		body.add(line("Progress", maxed ? "fully upgraded"
				: (int) p.progress() + " / " + cfg.progressToFillByLevel[levelIndex]));
		body.add(line("Workers", WorkforceManager.count(level, village).workers(profession) + " / " + cfg.workerCapPerStation));
		body.add(text("This station's daily work arrives in a later update.", ChatFormatting.DARK_GRAY));
		body.add(text("Breaking it gives you an ordinary " + StationManager.displayName(profession).toLowerCase()
				+ " workstation. The village remembers the level for the next one you donate.", ChatFormatting.DARK_GRAY));

		List<ActionButton> buttons = List.of(
				button("Village", "Open the panel of " + village.name(), "/village panel " + coords(village.bellPos())));

		show(player, Component.literal(StationManager.displayName(profession) + " station")
				.withStyle(ChatFormatting.GOLD), body, buttons);
	}

	/** True if this position is an active station whose daily work exists (so it has a station screen). */
	public static boolean isWorkingStation(ServerLevel level, BlockPos pos) {
		Optional<Village> village = VillageRegistry.get(level).byStation(pos);
		return village.isPresent() && !village.get().dormant()
				&& village.get().professionAtStation(pos).flatMap(StationTypes::get).isPresent();
	}

	/**
	 * Workstations with a vanilla screen of their own. These keep it on a normal right-click;
	 * the village panel opens with shift-right-click and an empty hand.
	 */
	public static boolean hasOwnScreen(BlockState state) {
		return state.is(Blocks.SMOKER) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.LOOM)
				|| state.is(Blocks.STONECUTTER) || state.is(Blocks.SMITHING_TABLE) || state.is(Blocks.GRINDSTONE)
				|| state.is(Blocks.CARTOGRAPHY_TABLE) || state.is(Blocks.BREWING_STAND) || state.is(Blocks.LECTERN);
	}

	/** True if the station block has its own 27-slot storage (barrel). */
	public static boolean hasStorage(ServerLevel level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof Container c && c.getContainerSize() == 27;
	}

	/** Opens the station screen for an active working station. Returns false if there isn't one here. */
	public static boolean openScreen(ServerPlayer player, ServerLevel level, BlockPos pos) {
		Optional<Village> village = VillageRegistry.get(level).byStation(pos);
		if (village.isEmpty() || village.get().dormant()) return false;
		Optional<String> profession = village.get().professionAtStation(pos);
		if (profession.isEmpty() || StationTypes.get(profession.get()).isEmpty()) return false;
		openScreen(player, level, village.get(), profession.get(), pos);
		return true;
	}

	/**
	 * Everything a station panel needs on the server: the village's slots, the synced values, and the
	 * Upgrade and Reroll buttons. Used by the station screen and by the panel docked beside vanilla screens.
	 */
	public record Link(ServerLevel level, BlockPos pos, String profession, StationInventory inventory, ContainerData data,
			BooleanSupplier stillActive, Predicate<Player> upgrade, Predicate<Player> reroll) {
	}

	/** The link for an active working station at this position, if there is one. */
	public static Optional<Link> link(ServerLevel level, BlockPos pos) {
		Optional<Village> village = VillageRegistry.get(level).byStation(pos);
		if (village.isEmpty() || village.get().dormant()) return Optional.empty();
		Optional<String> profession = village.get().professionAtStation(pos);
		if (profession.isEmpty() || StationTypes.get(profession.get()).isEmpty()) return Optional.empty();
		return Optional.of(link(level, village.get(), profession.get(), pos));
	}

	private static Link link(ServerLevel level, Village village, String profession, BlockPos pos) {
		VillageRegistry registry = VillageRegistry.get(level);
		StationInventory inv = registry.inventory(village, profession);
		UUID villageId = village.id();

		ContainerData data = new ContainerData() {
			@Override
			public int get(int index) {
				Optional<Village> v = VillageRegistry.get(level).byId(villageId);
				Optional<ProfessionStation> p = v.flatMap(x -> x.profession(profession));
				if (p.isEmpty()) return index == StationMenu.D_REQUEST_ITEM || index == StationMenu.D_MILESTONE_ITEM ? -1 : 0;
				VRConfig.Stations cfg = VRConfig.get().stations;
				return switch (index) {
					case StationMenu.D_REQUEST_ITEM -> !p.get().hasOpenRequest() ? -1 : p.get().requestItem()
							.flatMap(StationTypes::item).map(BuiltInRegistries.ITEM::getId).orElse(-1);
					case StationMenu.D_REQUEST_COUNT -> p.get().hasOpenRequest() ? p.get().requestCount() : 0;
					case StationMenu.D_LEVEL -> p.get().level();
					case StationMenu.D_MAX_LEVEL -> cfg.maxLevel;
					case StationMenu.D_PROGRESS -> (int) p.get().progress();
					case StationMenu.D_FILL -> Upgrades.fill(p.get());
					case StationMenu.D_MILESTONE_ITEM -> StationTypes.get(profession)
							.flatMap(d -> d.milestoneFor(p.get().level() + 1))
							.filter(i -> p.get().level() < cfg.maxLevel)
							.map(BuiltInRegistries.ITEM::getId).orElse(-1);
					case StationMenu.D_WORKERS -> v.get().workerCount(profession);
					case StationMenu.D_WORKER_CAP -> cfg.workerCapPerStation;
					case StationMenu.D_REWARD_SLOTS -> VRConfig.get().rewardSlots(p.get().level());
					case StationMenu.D_REROLL_COST -> Rerolls.cost(v.get(), profession);
					case StationMenu.D_ACTION -> StationActions.available(level, pos, profession);
					case StationMenu.D_LAST_PERCENT -> p.get().lastContribution() < 0 ? -1
							: (int) Math.round(p.get().lastContribution() * 100);
					default -> 0;
				};
			}

			@Override
			public void set(int index, int value) {
			}

			@Override
			public int getCount() {
				return StationMenu.DATA_COUNT;
			}
		};

		BooleanSupplier stillActive = () -> currentStation(level, villageId, profession)
				.flatMap(ProfessionStation::stationPos).map(pos::equals).orElse(false);

		Predicate<Player> upgrade = who -> who instanceof ServerPlayer sp
				&& VillageRegistry.get(level).byId(villageId)
						.map(v -> Upgrades.tryUpgrade(level, v, profession, sp)).orElse(false);

		Predicate<Player> reroll = who -> who instanceof ServerPlayer sp
				&& VillageRegistry.get(level).byId(villageId)
						.map(v -> Rerolls.tryReroll(level, v, profession, sp)).orElse(false);

		return new Link(level, pos, profession, inv, data, stillActive, upgrade, reroll);
	}

	/** The station screen: village panel on the left, the block's own storage (if any) on the right. */
	private static void openScreen(ServerPlayer player, ServerLevel level, Village village, String profession, BlockPos pos) {
		VillageRegistry registry = VillageRegistry.get(level);
		Link link = link(level, village, profession, pos);

		Container blockStorage = hasStorage(level, pos) ? (Container) level.getBlockEntity(pos) : new SimpleContainer(0);
		int extraRows = blockStorage.getContainerSize() > 0 && Perks.FISHERMAN.equals(profession)
				? BarrelPerks.extraRows(Perks.level(level, pos, Perks.FISHERMAN)) : 0;
		// Fisherman's barrel, level II/III: the village's extra rows below the barrel's own 27 slots.
		Container storage = extraRows > 0
				? new CompoundContainer(blockStorage, new ContainerSlice(registry.extraStorage(village, profession), extraRows * 9))
				: blockStorage;
		MenuType<StationMenu> type = storage.getContainerSize() > 0
				? StationMenus.storage(storage.getContainerSize() / 9) : StationMenus.panel();

		Component title = Component.literal(StationManager.displayName(profession) + " Station");
		player.openMenu(new SimpleMenuProvider((id, playerInventory, who) -> new StationMenu(type, id, playerInventory,
				link.inventory(), storage, link.data(), pos, link.stillActive(), link.upgrade(), link.reroll()), title));
	}

	private static Optional<ProfessionStation> currentStation(ServerLevel level, UUID villageId, String profession) {
		return VillageRegistry.get(level).byId(villageId).flatMap(v -> v.profession(profession));
	}

	private static Component plain(String text, ChatFormatting color) {
		return Component.literal(text).withStyle(style -> style.withItalic(false).withColor(color));
	}

	private static void openDonate(ServerPlayer player, Village village, String profession, BlockPos pos, ServerLevel level) {
		Optional<ProfessionStation> remembered = village.profession(profession).filter(ProfessionStation::hasProgress);
		String name = StationManager.displayName(profession);

		List<DialogBody> body = new ArrayList<>();
		body.add(text(village.name() + " has no " + name + " station yet. Donate this workstation to the village?",
				ChatFormatting.WHITE));
		remembered.ifPresent(p -> body.add(text("The village remembers its " + name + " at level " + p.level()
				+ ". This workstation will take over from there.", ChatFormatting.GREEN)));
		body.add(text("A village has one station per profession. You can take it back by breaking it.",
				ChatFormatting.DARK_GRAY));

		List<ActionButton> buttons = List.of(
				button("Donate", "Give this workstation to " + village.name(), "/village donate " + coords(pos)));

		show(player, Component.literal("Donate to " + village.name()).withStyle(ChatFormatting.GOLD), body, buttons);
	}
}
