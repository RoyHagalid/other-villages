package dev.villagerework.client;

import dev.villagerework.ui.StationMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

/** Client side of the mod: the station screen. */
public class VillageReworkClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		StationMenus.all().forEach(type -> MenuScreens.register(type, StationScreen::new));
		MenuScreens.register(StationMenus.fletching(), FletchingScreen::new);
		// The bell screen (0.17.11): content from the server, laid out here.
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				dev.villagerework.net.BellScreenPayload.TYPE,
				(payload, context) -> context.client().gui.setScreen(new BellScreen(payload)));
		// Tier marks on upgraded station blocks (village stations and take-homes).
		dev.villagerework.client.looks.StationLooksClient.init();
		// Librarian's lectern, level III: a signed book plays as a story scroll.
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				dev.villagerework.net.StoryPayload.TYPE,
				(payload, context) -> {
					// Never interrupt another screen (inventory, chest, chat): then the scroll is skipped.
					if (context.client().gui.screen() == null) context.client().gui.setScreen(new StoryScreen(payload));
				});
	}
}
