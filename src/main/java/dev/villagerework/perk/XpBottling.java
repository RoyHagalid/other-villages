package dev.villagerework.perk;

import dev.villagerework.config.VRConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Cleric's brewing stand, level III: XP bottling, from the action row of the panel docked to the brewing stand
 * (see StationActions). {@code perks.xpPerBottle} XP points (7) and a glass bottle become a bottle o' enchanting
 * (which gives back 3-11 XP when thrown, about 7). Creative players bottle for free.
 */
public final class XpBottling {
	private XpBottling() {
	}

	/** Total XP points a player has (vanilla's level formula plus progress). */
	public static int points(Player player) {
		int level = player.experienceLevel;
		long base;
		if (level <= 16) base = (long) level * level + 6L * level;
		else if (level <= 31) base = (long) (2.5 * level * level - 40.5 * level + 360);
		else base = (long) (4.5 * level * level - 162.5 * level + 2220);
		return (int) Math.min(Integer.MAX_VALUE, base + Math.round(player.experienceProgress * player.getXpNeededForNextLevel()));
	}
}
