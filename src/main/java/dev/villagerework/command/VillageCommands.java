package dev.villagerework.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.villagerework.VillageRework;
import dev.villagerework.config.VRConfig;
import dev.villagerework.ui.BellPanel;
import dev.villagerework.village.StationManager;
import dev.villagerework.village.WorkforceManager;
import dev.villagerework.village.BorderDisplay;
import dev.villagerework.village.Village;
import dev.villagerework.village.VillageManager;
import dev.villagerework.village.VillageRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Player commands behind the bell panel buttons. Everyone can use them, but they only
 * work when standing near the bell they name, so the panel stays the real entry point.
 *
 *   /village                   where am I? (village you're standing in)
 *   /village make <x y z>      found a village at that bell
 *   /village border <x y z>    show the village edge around that bell
 *   /village rename <x y z> [name]   open the rename box, or rename directly
 *   /village panel <x y z>     open the bell panel (used by "Cancel" in the rename box)
 *   /village donate <x y z>    donate the workstation at that position to the village it's in
 *   /village disband <x y z> [confirm]   ask to disband, or disband, the village that bell belongs to
 *   /village party <x y z> on|off      allow or stop the village's parties (fireworks and music)
 */
public final class VillageCommands {
	private VillageCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("village")
				.executes(VillageCommands::whereAmI)
				.then(literal("make")
						.then(argument("bell", BlockPosArgument.blockPos()).executes(VillageCommands::make)))
				.then(literal("border")
						.then(argument("bell", BlockPosArgument.blockPos()).executes(VillageCommands::border)))
				.then(literal("rename")
						.then(argument("bell", BlockPosArgument.blockPos())
								.executes(VillageCommands::openRename)
								.then(argument("name", StringArgumentType.greedyString())
										.executes(VillageCommands::rename))))
				.then(literal("party")
						.then(argument("bell", BlockPosArgument.blockPos())
								.then(literal("on").executes(ctx -> party(ctx, true)))
								.then(literal("off").executes(ctx -> party(ctx, false)))))
				.then(literal("panel")
						.then(argument("bell", BlockPosArgument.blockPos()).executes(VillageCommands::panel)))
				.then(literal("donate")
						.then(argument("station", BlockPosArgument.blockPos()).executes(VillageCommands::donate)))
				.then(literal("disband")
						.then(argument("bell", BlockPosArgument.blockPos())
								.executes(ctx -> disband(ctx, false))
								.then(literal("confirm").executes(ctx -> disband(ctx, true))))));
	}

	private static int whereAmI(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = ctx.getSource().getLevel();
		Optional<Village> village = VillageRegistry.get(level).containing(player.blockPosition(), VRConfig.get().village.radius);
		if (village.isEmpty()) {
			player.sendSystemMessage(Component.literal("You're not inside a village.").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		Village v = village.get();
		player.sendSystemMessage(Component.literal("You're in " + v.name() + ". Its center bell is "
				+ (int) v.horizontalDistance(player.blockPosition()) + " blocks away at " + v.bellPos().toShortString()
				+ (v.dormant() ? " (missing, the village is dormant)." : "."))
				.withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static int make(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = ctx.getSource().getLevel();
		BlockPos bell = BlockPosArgument.getSpawnablePos(ctx, "bell");
		if (!nearBell(player, level, bell)) return 0;

		Optional<Village> village = VillageManager.found(level, bell, player.getUUID());
		if (village.isEmpty()) {
			player.sendSystemMessage(Component.literal("A village can't be founded here right now. Shift-click the bell to see what's missing.")
					.withStyle(ChatFormatting.RED));
			return 0;
		}
		player.sendSystemMessage(Component.literal("You founded " + village.get().name() + "!").withStyle(ChatFormatting.GOLD));
		BorderDisplay.show(player, bell, VRConfig.get().village.radius, VRConfig.get().village.borderDisplaySeconds);
		return 1;
	}

	private static int border(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = ctx.getSource().getLevel();
		BlockPos bell = BlockPosArgument.getSpawnablePos(ctx, "bell");
		VRConfig cfg = VRConfig.get();

		// Allowed near the named bell, or anywhere inside the village whose center it is.
		boolean insideThatVillage = VillageRegistry.get(level).byBell(bell)
				.map(v -> v.contains(player.blockPosition(), cfg.village.radius))
				.orElse(false);
		if (!insideThatVillage && !nearBell(player, level, bell)) return 0;

		BorderDisplay.show(player, bell, cfg.village.radius, cfg.village.borderDisplaySeconds);
		return 1;
	}

	/** The bell screen's party switch (0.18.1): fireworks and music at abundance, on or off. Anyone may flip it. */
	private static int party(CommandContext<CommandSourceStack> ctx, boolean allowed) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = ctx.getSource().getLevel();
		Optional<Village> village = centerVillage(player, level, BlockPosArgument.getSpawnablePos(ctx, "bell"));
		if (village.isEmpty()) return 0;
		dev.villagerework.village.Prosperity.setParty(level, village.get(), allowed);
		return 1;
	}

	private static int openRename(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = ctx.getSource().getLevel();
		Optional<Village> village = centerVillage(player, level, BlockPosArgument.getSpawnablePos(ctx, "bell"));
		if (village.isEmpty()) return 0;
		BellPanel.openRename(player, level, village.get());
		return 1;
	}

	private static int rename(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = ctx.getSource().getLevel();
		Optional<Village> village = centerVillage(player, level, BlockPosArgument.getSpawnablePos(ctx, "bell"));
		if (village.isEmpty()) return 0;

		String name = cleanName(StringArgumentType.getString(ctx, "name"));
		if (name.isEmpty()) {
			player.sendSystemMessage(Component.literal("The name can't be empty.").withStyle(ChatFormatting.RED));
			return 0;
		}
		String old = village.get().name();
		if (old.equals(name)) {
			BellPanel.open(player, level, village.get().bellPos());
			return 1;
		}
		VillageRegistry.get(level).rename(village.get(), name);
		player.sendSystemMessage(Component.literal(old + " is now called " + name + ".").withStyle(ChatFormatting.GOLD));
		BellPanel.open(player, level, village.get().bellPos());
		return 1;
	}

	private static int panel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = ctx.getSource().getLevel();
		BlockPos bell = BlockPosArgument.getSpawnablePos(ctx, "bell");
		if (!nearBell(player, level, bell)) return 0;
		BellPanel.open(player, level, bell);
		return 1;
	}

	/**
	 * Works from the center bell of an active village, or from any bell inside a dormant one
	 * (its center bell is gone, so that's the only way to reach it).
	 */
	private static int disband(CommandContext<CommandSourceStack> ctx, boolean confirmed) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = ctx.getSource().getLevel();
		BlockPos bell = BlockPosArgument.getSpawnablePos(ctx, "bell");
		if (!nearBell(player, level, bell)) return 0;

		VillageRegistry registry = VillageRegistry.get(level);
		Optional<Village> village = registry.byBell(bell);
		if (village.isEmpty()) {
			village = registry.containing(bell, VRConfig.get().village.radius).filter(Village::dormant);
		}
		if (village.isEmpty()) {
			player.sendSystemMessage(Component.literal("Use the center bell of the village to disband it.").withStyle(ChatFormatting.RED));
			return 0;
		}
		if (!confirmed) {
			BellPanel.openDisbandConfirm(player, village.get(), bell);
			return 1;
		}
		Village target = village.get();
		String name = target.name();
		// Supplies and goods still in the stations are dropped, not deleted.
		target.professions().forEach((key, p) -> p.stationPos().ifPresent(pos ->
				StationManager.dropInventory(level, registry, target, key, pos)));
		int released = WorkforceManager.releaseAll(level, target);
		registry.disband(target);
		player.sendSystemMessage(Component.literal(name + " has been disbanded."
				+ (released > 0 ? " Its " + released + " villager(s) with a profession are looking for work again." : ""))
				.withStyle(ChatFormatting.GOLD));
		VillageRework.LOGGER.info("Village {} disbanded by {}", name, player.getName().getString());
		return 1;
	}

	private static int donate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = ctx.getSource().getLevel();
		BlockPos pos = BlockPosArgument.getSpawnablePos(ctx, "station");
		int reach = VRConfig.get().village.panelReach;
		if (!level.isLoaded(pos) || player.blockPosition().distSqr(pos) > (double) reach * reach) {
			player.sendSystemMessage(Component.literal("You need to stand next to that workstation.").withStyle(ChatFormatting.RED));
			return 0;
		}
		StationManager.DonateCheck check = StationManager.checkDonation(level, pos).check();
		if (check != StationManager.DonateCheck.OK) {
			player.sendSystemMessage(Component.literal(switch (check) {
				case NOT_A_WORKSTATION -> "That isn't a workstation.";
				case NO_VILLAGE -> "That workstation isn't inside a village.";
				case DORMANT -> "That village is dormant. It needs its bell back first.";
				case ALREADY_STATION -> "That workstation is already a station.";
				case PROFESSION_TAKEN -> "The village already has a station for that profession.";
				default -> "That workstation can't be donated.";
			}).withStyle(ChatFormatting.RED));
			return 0;
		}
		return StationManager.donate(level, pos, player) ? 1 : 0;
	}

	/** The village whose center bell this is, if the player is standing next to it. */
	private static Optional<Village> centerVillage(ServerPlayer player, ServerLevel level, BlockPos bell) {
		if (!nearBell(player, level, bell)) return Optional.empty();
		Optional<Village> village = VillageRegistry.get(level).byBell(bell);
		if (village.isEmpty()) {
			player.sendSystemMessage(Component.literal("That bell isn't the center of a village.").withStyle(ChatFormatting.RED));
		}
		return village;
	}

	/** Removes formatting codes and extra spaces, and caps the length. */
	static String cleanName(String raw) {
		String name = raw.replaceAll("§.?", "").replaceAll("\\s+", " ").trim();
		if (name.length() > Village.MAX_NAME_LENGTH) name = name.substring(0, Village.MAX_NAME_LENGTH).trim();
		return name;
	}

	private static boolean nearBell(ServerPlayer player, ServerLevel level, BlockPos bell) {
		int reach = VRConfig.get().village.panelReach;
		if (!level.isLoaded(bell) || !level.getBlockState(bell).is(Blocks.BELL)
				|| player.blockPosition().distSqr(bell) > (double) reach * reach) {
			player.sendSystemMessage(Component.literal("You need to stand next to that bell.").withStyle(ChatFormatting.RED));
			return false;
		}
		return true;
	}
}
