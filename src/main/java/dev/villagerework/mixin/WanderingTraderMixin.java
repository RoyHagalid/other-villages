package dev.villagerework.mixin;

import dev.villagerework.config.VRConfig;
import dev.villagerework.trader.TraderTrades;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.TradeSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 0.12: the wandering trader's stock comes from the mod's trade sets (buy orders for village goods,
 * plus a couple of uncommon items for sale) instead of vanilla's three sets. The sets and trades are
 * plain data in data/villagerework/, so they can be edited or overridden with a datapack.
 */
@Mixin(WanderingTrader.class)
public abstract class WanderingTraderMixin {
	@Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
	private void villagerework$trades(ServerLevel level, CallbackInfo ci) {
		if (!VRConfig.get().trader.replaceTrades) return;
		ci.cancel();
		WanderingTrader self = (WanderingTrader) (Object) this;
		MerchantOffers offers = self.getOffers();
		for (ResourceKey<TradeSet> set : TraderTrades.SETS) {
			((AbstractVillagerInvoker) self).villagerework$addOffersFromTradeSet(level, offers, set);
		}
	}
}
