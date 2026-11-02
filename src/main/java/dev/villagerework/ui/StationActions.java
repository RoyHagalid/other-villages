package dev.villagerework.ui;

import dev.villagerework.config.VRConfig;
import dev.villagerework.item.ModItems;
import dev.villagerework.perk.Perks;
import dev.villagerework.perk.XpBottling;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Perk actions, made in the action row under the block's own screen (see ActionRow):
 *  - Cleric III (brewing stand): [glass bottles] [Bottle XP] [output]: each bottle + {@code perks.xpPerBottle}
 *    XP points → a bottle o' enchanting
 *  - Cartographer III (cartography table): [empty maps] [amethyst shards] [Cave Map] [output]: each map + shard
 *    → an unused cave map
 * The input's count is how many get made; one click makes as many as it can. Creative players pay no XP or shards.
 *
 * Container slots of the row: 0 input, 1 extra input (shards), 2 output.
 */
public final class StationActions {
	public static final int NONE = StationMenu.ACTION_NONE, BOTTLE_XP = StationMenu.ACTION_BOTTLE_XP,
			CAVE_MAP = StationMenu.ACTION_SONAR_MAP;

	private StationActions() {
	}

	/** The action this block offers at its perk level (the block's profession is given by its screen). */
	public static int available(ServerLevel level, BlockPos pos, String profession) {
		if (Perks.CLERIC.equals(profession) && Perks.level(level, pos, Perks.CLERIC) >= 3) return BOTTLE_XP;
		if (Perks.CARTOGRAPHER.equals(profession) && Perks.level(level, pos, Perks.CARTOGRAPHER) >= 3) return CAVE_MAP;
		return NONE;
	}

	public static String label(int action) {
		return switch (action) {
			case BOTTLE_XP -> "Bottle XP";
			case CAVE_MAP -> "Cave Map";
			default -> "";
		};
	}

	/** What goes in the input slot. */
	public static Item input(int action) {
		return action == BOTTLE_XP ? Items.GLASS_BOTTLE : Items.MAP;
	}

	/** What goes in the extra input slot, or null when the action has none. */
	public static Item extra(int action) {
		return action == CAVE_MAP ? Items.AMETHYST_SHARD : null;
	}

	public static Item output(int action) {
		return action == BOTTLE_XP ? Items.EXPERIENCE_BOTTLE : ModItems.SONAR_MAP;
	}

	/** How many the action would make now (the client greys out the button at 0; the server makes them). */
	public static int count(int action, Player player, Container io) {
		if (action == NONE) return 0;
		ItemStack in = io.getItem(0);
		ItemStack out = io.getItem(2);
		if (!in.is(input(action))) return 0;
		Item result = output(action);
		if (!out.isEmpty() && !out.is(result)) return 0;
		int room = (out.isEmpty() ? new ItemStack(result).getMaxStackSize() : out.getMaxStackSize()) - out.getCount();
		int n = Math.min(in.getCount(), room);
		if (!player.hasInfiniteMaterials()) {
			if (action == BOTTLE_XP) {
				n = Math.min(n, XpBottling.points(player) / Math.max(1, VRConfig.get().perks.xpPerBottle));
			} else {
				ItemStack shards = io.getItem(1);
				n = Math.min(n, shards.is(extra(action)) ? shards.getCount() : 0);
			}
		}
		return Math.max(0, n);
	}

	/** Runs the action (server). Returns true if anything was made. */
	public static boolean run(int action, Player player, Container io) {
		int n = count(action, player, io);
		if (n <= 0) return false;
		if (!player.hasInfiniteMaterials()) {
			if (action == BOTTLE_XP) player.giveExperiencePoints(-n * VRConfig.get().perks.xpPerBottle);
			else shrink(io, 1, n);
		}
		shrink(io, 0, n);
		ItemStack out = io.getItem(2);
		io.setItem(2, out.isEmpty() ? new ItemStack(output(action), n) : out.copyWithCount(out.getCount() + n));
		io.setChanged();
		player.level().playSound(null, player.blockPosition(), action == BOTTLE_XP
						? SoundEvents.BOTTLE_FILL : SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
				SoundSource.PLAYERS, 0.8F, 1.0F);
		return true;
	}

	private static void shrink(Container io, int slot, int n) {
		ItemStack stack = io.getItem(slot).copy();
		stack.shrink(n);
		io.setItem(slot, stack);
	}
}
