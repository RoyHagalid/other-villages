package dev.villagerework.trader;

import dev.villagerework.VillageRework;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.trading.TradeSet;

import java.util.List;

/**
 * The wandering trader's trade sets (0.12), in the order they show in his screen. Each is a data file in
 * data/villagerework/trade_set/wandering_trader/ that picks a number of trades from a tag in
 * data/villagerework/tags/villager_trade/wandering_trader/; the trades are in
 * data/villagerework/villager_trade/wandering_trader/.
 *
 *   buy_common    5 orders for level I goods,   1 emerald a trade,  4 trades each  (up to 20; 3 before 0.19.0)
 *   buy_uncommon  3 orders for level II goods,  2 emeralds a trade, 3 trades each  (up to 18)
 *   buy_rare      1 order for level III goods,  5 emeralds a trade, 2 trades       (up to 10)
 *   sell          4 less common items for emeralds (2 before 0.19.0)
 *
 * 0.19.0: he only buys empty bundles (the trade asks for empty bundle contents), so items inside aren't lost.
 */
public final class TraderTrades {
	private TraderTrades() {
	}

	public static final List<ResourceKey<TradeSet>> SETS = List.of(
			set("buy_common"), set("buy_uncommon"), set("buy_rare"), set("sell"));

	private static ResourceKey<TradeSet> set(String name) {
		return ResourceKey.create(Registries.TRADE_SET, VillageRework.id("wandering_trader/" + name));
	}
}
