package dev.villagerework;

import dev.villagerework.command.VRCommands;
import dev.villagerework.command.VillageCommands;
import dev.villagerework.config.VRConfig;
import dev.villagerework.ui.BellPanel;
import dev.villagerework.ui.StationMenus;
import dev.villagerework.ui.StationPanel;
import dev.villagerework.village.StationManager;
import dev.villagerework.village.VillageManager;
import dev.villagerework.village.WorkforceManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillageRework implements ModInitializer {
	public static final String MOD_ID = "villagerework";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		VRConfig.load();
		dev.villagerework.item.ModItems.register();
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay()
				.register(dev.villagerework.net.StoryPayload.TYPE, dev.villagerework.net.StoryPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay()
				.register(dev.villagerework.net.StationLooksPayload.TYPE, dev.villagerework.net.StationLooksPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay()
				.register(dev.villagerework.net.BellScreenPayload.TYPE, dev.villagerework.net.BellScreenPayload.CODEC);

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
			VRCommands.register(dispatcher);
			VillageCommands.register(dispatcher);
		});

		StationMenus.register();
		VillageManager.register();
		dev.villagerework.perk.TakeHomes.init();
		dev.villagerework.perk.StationLooks.init();
		dev.villagerework.item.ExplosiveArrows.init();
		dev.villagerework.village.Prosperity.init();
		dev.villagerework.village.Forgiveness.init();
		dev.villagerework.village.BellRings.init();
		dev.villagerework.trader.TraderVisits.init();
		dev.villagerework.village.VillageLoot.init();
		dev.villagerework.village.Renown.init();
		// Deep emerald veins in every overworld biome (0.18.5). Mountain emeralds stay vanilla. Tune the vein in
		// data/villagerework/worldgen/feature/ore_emerald_deep.json and its rarity/depth in
		// data/villagerework/worldgen/placed_feature/ore_emerald_deep.json.
		net.fabricmc.fabric.api.biome.v1.BiomeModifications.addFeature(
				net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInOverworld(),
				net.minecraft.world.level.levelgen.GenerationStep.Decoration.UNDERGROUND_ORES,
				net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.PLACED_FEATURE,
						id("ore_emerald_deep")));
		// Everything kept in memory only is forgotten when the server stops, so a second singleplayer world
		// in the same session starts clean (0.18.4).
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			dev.villagerework.village.Prosperity.reset();
			dev.villagerework.perk.StationLooks.reset();
			dev.villagerework.perk.LecternMessages.reset();
			dev.villagerework.village.BorderDisplay.reset();
			dev.villagerework.item.ExplosiveArrows.reset();
			dev.villagerework.village.Forgiveness.reset();
			dev.villagerework.village.WorkforceManager.reset();
			dev.villagerework.village.BellRings.reset();
			dev.villagerework.trader.TraderVisits.reset();
		});
		registerBellInteraction();

		LOGGER.info("Other: Villages loaded.");
		if (!VRConfig.get().features.disableVanillaJobClaiming) {
			LOGGER.warn("features.disableVanillaJobClaiming is false: vanilla hands out professions and the workforce is paused.");
		}
		if (!VRConfig.get().features.disableVanillaTrading) {
			LOGGER.warn("features.disableVanillaTrading is false: villagers trade the vanilla way.");
		}
	}

	/**
	 * Bells: shift-right-click with an empty hand opens the bell panel.
	 * Working stations: right-click opens the station screen (see below).
	 * Other workstations in a village: shift-right-click with an empty hand for status or donating.
	 * Breaking a station removes it from the village.
	 */
	private static void registerBellInteraction() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
			BlockPos pos = hit.getBlockPos();
			boolean emptyHand = player.getMainHandItem().isEmpty();
			boolean sneaking = player.isShiftKeyDown();

			// Bells: shift-right-click with an empty hand opens the bell panel.
			if (level.getBlockState(pos).is(Blocks.BELL)) {
				if (!sneaking || !emptyHand) return InteractionResult.PASS;
				if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
					BellPanel.open(serverPlayer, (ServerLevel) level, pos);
				}
				return InteractionResult.SUCCESS;
			}

			// Workstations: only the server knows about villages. If it opens a screen,
			// the click is consumed there; otherwise vanilla behavior continues.
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
			ServerLevel serverLevel = (ServerLevel) level;

			if (StationPanel.isWorkingStation(serverLevel, pos)) {
				if (sneaking && !emptyHand) return InteractionResult.PASS; // placing blocks against it, as in vanilla
				BlockState state = level.getBlockState(pos);
				// The fletcher's table gets a screen of its own from level II (fletching), with the village panel
				// docked on the right.
				if (state.is(Blocks.FLETCHING_TABLE)
						&& dev.villagerework.perk.Perks.level(serverLevel, pos, dev.villagerework.perk.Perks.FLETCHER) >= 2) {
					serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, who) ->
							new dev.villagerework.ui.FletchingMenu(id, inventory,
									net.minecraft.world.inventory.ContainerLevelAccess.create(serverLevel, pos)),
							net.minecraft.network.chat.Component.literal("Fletching Table")));
					return InteractionResult.SUCCESS;
				}
				// Lectern: without a book it has nothing of its own to show, so it opens the village panel; with a
				// book, the book opens on a normal right-click and the panel on shift-right-click (empty hand).
				if (state.is(Blocks.LECTERN)) {
					boolean hasBook = state.getValue(net.minecraft.world.level.block.LecternBlock.HAS_BOOK);
					// 0.19.0: any item opens the panel too, except a book going onto an empty lectern.
					boolean placingBook = !hasBook && player.getMainHandItem().is(net.minecraft.tags.ItemTags.LECTERN_BOOKS);
					if (placingBook || (hasBook && !sneaking)) return InteractionResult.PASS;
					StationPanel.openScreen(serverPlayer, serverLevel, pos);
					return InteractionResult.SUCCESS;
				}
				// Blocks with their own screen (smoker, loom, ...) open it with the village panel docked on the
				// right, also on shift-right-click.
				if (StationPanel.hasOwnScreen(state)) return InteractionResult.PASS;
				// Stations with storage (barrel) always open storage + village panel.
				// Blocks without a screen (composter, cauldron, fletching table) open the panel with any item in hand
				// (0.19.0, like a chest), unless the item does something to the block in vanilla: composting, a full
				// composter giving its bone meal, buckets, bottles and washing in a cauldron.
				if (StationPanel.hasStorage(serverLevel, pos) || !usesBlock(state, player.getMainHandItem())) {
					StationPanel.openScreen(serverPlayer, serverLevel, pos);
					return InteractionResult.SUCCESS;
				}
				return InteractionResult.PASS;
			}

			// A bought barrel (take-home, tier II+): the barrel with its own extra rows, as a bigger chest screen.
			if (!StationPanel.isWorkingStation(serverLevel, pos) && level.getBlockState(pos).is(Blocks.BARREL)
					&& !(sneaking && !emptyHand) && level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel) {
				int rows = dev.villagerework.perk.BarrelPerks.extraRows(
						dev.villagerework.perk.TakeHomes.tier(serverLevel, pos, dev.villagerework.perk.Perks.FISHERMAN));
				if (rows > 0) {
					net.minecraft.world.Container storage = new net.minecraft.world.CompoundContainer(barrel,
							new dev.villagerework.village.ContainerSlice(dev.villagerework.perk.TakeHomes.extraStorage(serverLevel, pos), rows * 9));
					net.minecraft.world.inventory.MenuType<?> type = rows == 1
							? net.minecraft.world.inventory.MenuType.GENERIC_9x4 : net.minecraft.world.inventory.MenuType.GENERIC_9x5;
					serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, who) ->
							new net.minecraft.world.inventory.ChestMenu(type, id, inventory, storage, 3 + rows),
							barrel.getDisplayName()));
					return InteractionResult.SUCCESS;
				}
			}

			// A bought fletching table (take-home, tier II+) opens the fletching screen.
			if (level.getBlockState(pos).is(Blocks.FLETCHING_TABLE) && !(sneaking && !emptyHand)
					&& dev.villagerework.perk.TakeHomes.tier(serverLevel, pos, dev.villagerework.perk.Perks.FLETCHER) >= 2) {
				serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, who) ->
						new dev.villagerework.ui.FletchingMenu(id, inventory,
								net.minecraft.world.inventory.ContainerLevelAccess.create(serverLevel, pos)),
						net.minecraft.network.chat.Component.literal("Fletching Table")));
				return InteractionResult.SUCCESS;
			}

			// Any other workstation inside a village: shift-right-click with an empty hand
			// shows its status, or the donate prompt.
			if (sneaking && emptyHand && StationPanel.tryOpen(serverPlayer, serverLevel, pos)) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		// A worker that dies (or is turned into a zombie villager) frees its slot.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Villager villager && entity.level() instanceof ServerLevel serverLevel) {
				WorkforceManager.onVillagerGone(serverLevel, villager);
			}
		});
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, context) -> {
			if (previous instanceof Villager villager && previous.level() instanceof ServerLevel serverLevel) {
				WorkforceManager.onVillagerGone(serverLevel, villager);
			}
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel) {
				if (state.is(Blocks.LECTERN)) dev.villagerework.perk.LecternMessages.forget(pos);
				StationManager.onBlockBroken(serverLevel, pos, player instanceof ServerPlayer sp ? sp : null);
				dev.villagerework.perk.TakeHomes.brokenBy(serverLevel, pos, player, state);
			}
		});
	}

	/** Whether the held item has its own vanilla use on this station block (then vanilla handles the click). */
	private static boolean usesBlock(BlockState state, net.minecraft.world.item.ItemStack held) {
		if (held.isEmpty()) return false;
		if (state.hasProperty(ComposterBlock.LEVEL)) {
			return state.getValue(ComposterBlock.LEVEL) == 8
					|| held.has(net.minecraft.core.component.DataComponents.COMPOSTABLE)
					|| held.is(net.minecraft.world.item.Items.ROTTEN_FLESH) || held.is(net.minecraft.world.item.Items.SPIDER_EYE);
		}
		net.minecraft.core.cauldron.CauldronInteraction.Dispatcher cauldron =
				state.is(Blocks.CAULDRON) ? net.minecraft.core.cauldron.CauldronInteractions.EMPTY
				: state.is(Blocks.WATER_CAULDRON) ? net.minecraft.core.cauldron.CauldronInteractions.WATER
				: state.is(Blocks.LAVA_CAULDRON) ? net.minecraft.core.cauldron.CauldronInteractions.LAVA
				: state.is(Blocks.POWDER_SNOW_CAULDRON) ? net.minecraft.core.cauldron.CauldronInteractions.POWDER_SNOW
				: null;
		if (cauldron != null) return cauldron.get(held) != net.minecraft.core.cauldron.CauldronInteraction.DEFAULT;
		return false;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
