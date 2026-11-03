package dev.villagerework.village;

import dev.villagerework.config.VRConfig;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Natural village chests hold a little extra (0.20.0): sometimes 1-3 emeralds, sometimes a level I enchanted book
 * (picked like the librarian's). Added on top of whatever the chest's loot table gives, so it works with datapacks
 * or other mods that replace those tables (Enchant Library's themed books, for one).
 */
public final class VillageLoot {
	private VillageLoot() {
	}

	public static void init() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			boolean villageChest = table.unwrapKey()
					.map(key -> key.identifier().getNamespace().equals("minecraft")
							&& key.identifier().getPath().startsWith("chests/village/"))
					.orElse(false);
			if (!villageChest) return;
			VRConfig.Village cfg = VRConfig.get().village;
			var random = context.getRandom();
			if (random.nextDouble() < cfg.chestEmeraldChance) {
				drops.add(new ItemStack(Items.EMERALD, 1 + random.nextInt(Math.max(1, cfg.chestEmeraldMax))));
			}
			if (random.nextDouble() < cfg.chestBookChance) {
				drops.add(StationTypes.enchantedBook(context.getLevel(), random, 1));
			}
		});
	}
}
