package dev.villagerework.perk;

import dev.villagerework.config.VRConfig;
import dev.villagerework.village.ProfessionStation;
import dev.villagerework.village.StationManager;
import dev.villagerework.village.Village;
import dev.villagerework.village.VillageRegistry;
import dev.villagerework.village.WorkforceManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Optional;

/**
 * Workers sell take-home workstations (0.17) in the trade screen: a worker whose village has its profession's
 * station at level II sells tier II, at level III also tier III. Price: {@code takeHomes.priceByTier} emeralds
 * plus a plain block of the same kind. The villager's own trades aren't touched (vanilla trading stays off);
 * this is a separate little shop opened from the worker.
 */
public final class TakeHomeShop implements Merchant {
	private final Villager villager;
	private Player tradingPlayer;
	private MerchantOffers offers;

	private TakeHomeShop(Villager villager, MerchantOffers offers) {
		this.villager = villager;
		this.offers = offers;
	}

	/** Opens the shop if this villager is a worker with something to sell. Returns true if it opened. */
	public static boolean tryOpen(ServerPlayer player, Villager villager, ServerLevel level) {
		VRConfig.TakeHomes cfg = VRConfig.get().takeHomes;
		if (!cfg.enabled) return false;
		Optional<String> profession = WorkforceManager.professionKey(villager);
		if (profession.isEmpty()) return false;
		Item block = TakeHomes.BLOCKS.get(profession.get());
		if (block == null) return false;
		Optional<Village> village = VillageRegistry.get(level).containing(villager.blockPosition(), VRConfig.get().village.radius);
		if (village.isEmpty() || village.get().dormant()) return false;
		int stationLevel = village.get().profession(profession.get())
				.filter(ProfessionStation::active).map(ProfessionStation::level).orElse(0);
		if (stationLevel < 2) return false;

		MerchantOffers offers = new MerchantOffers();
		for (int tier = 2; tier <= Math.min(3, stationLevel); tier++) {
			// 0.20.0: the village's renown lowers the price.
			int price = dev.villagerework.village.Renown.price(level, village.get(),
					cfg.priceByTier[Math.min(tier - 2, cfg.priceByTier.length - 1)]);
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, price), Optional.of(new ItemCost(block)),
					TakeHomes.item(profession.get(), tier), 0, Integer.MAX_VALUE, 0, 0.0F));
		}
		TakeHomeShop shop = new TakeHomeShop(villager, offers);
		shop.setTradingPlayer(player);
		shop.openTradingScreen(player, Component.literal(StationManager.displayName(profession.get())), 0);
		return true;
	}

	@Override
	public void setTradingPlayer(Player player) {
		this.tradingPlayer = player;
	}

	@Override
	public Player getTradingPlayer() {
		return tradingPlayer;
	}

	@Override
	public MerchantOffers getOffers() {
		return offers;
	}

	@Override
	public void overrideOffers(MerchantOffers offers) {
		this.offers = offers;
	}

	@Override
	public void notifyTrade(MerchantOffer offer) {
		offer.increaseUses();
		villager.playSound(SoundEvents.VILLAGER_YES, 1.0F, villager.getVoicePitch());
	}

	@Override
	public void notifyTradeUpdated(ItemStack stack) {
	}

	@Override
	public int getVillagerXp() {
		return 0;
	}

	@Override
	public void overrideXp(int xp) {
	}

	@Override
	public boolean showProgressBar() {
		return false;
	}

	@Override
	public SoundEvent getNotifyTradeSound() {
		return SoundEvents.VILLAGER_YES;
	}

	@Override
	public boolean canRestock() {
		return false;
	}

	@Override
	public boolean isClientSide() {
		return false;
	}

	@Override
	public boolean stillValid(Player player) {
		return villager.isAlive() && tradingPlayer == player && player.distanceToSqr(villager) <= 64.0;
	}
}
