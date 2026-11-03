package dev.villagerework.ui;

import dev.villagerework.VillageRework;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;

/**
 * The two kinds of station screen:
 *  - storage: the panel next to a 27-slot block storage (barrel)
 *  - panel: the panel next to the player inventory only (composter and other blocks without storage)
 *
 * Registering menu types means the mod must be installed on the client too.
 */
public final class StationMenus {
	private static MenuType<StationMenu> storage;
	private static MenuType<StationMenu> storage4;
	private static MenuType<StationMenu> storage5;
	private static MenuType<StationMenu> panel;
	private static MenuType<FletchingMenu> fletching;

	private StationMenus() {
	}

	public static void register() {
		storage = Registry.register(BuiltInRegistries.MENU, VillageRework.id("station_storage"),
				new MenuType<>((id, inventory) -> new StationMenu(storage, id, inventory), FeatureFlagSet.of()));
		storage4 = Registry.register(BuiltInRegistries.MENU, VillageRework.id("station_storage_4"),
				new MenuType<>((id, inventory) -> new StationMenu(storage4, id, inventory), FeatureFlagSet.of()));
		storage5 = Registry.register(BuiltInRegistries.MENU, VillageRework.id("station_storage_5"),
				new MenuType<>((id, inventory) -> new StationMenu(storage5, id, inventory), FeatureFlagSet.of()));
		fletching = Registry.register(BuiltInRegistries.MENU, VillageRework.id("fletching"),
				new MenuType<>(FletchingMenu::new, FeatureFlagSet.of()));
		panel = Registry.register(BuiltInRegistries.MENU, VillageRework.id("station_panel"),
				new MenuType<>((id, inventory) -> new StationMenu(panel, id, inventory), FeatureFlagSet.of()));
	}

	public static MenuType<StationMenu> storage() {
		return storage;
	}

	/** The storage screen type for {@code rows} rows of block storage (3 to 5; the barrel's extra rows). */
	public static MenuType<StationMenu> storage(int rows) {
		return rows >= 5 ? storage5 : rows == 4 ? storage4 : storage;
	}

	/** How many storage slots a screen of this type shows (the client needs it before any data arrives). */
	public static int storageSize(MenuType<?> type) {
		if (type == storage5) return 45;
		if (type == storage4) return 36;
		if (type == storage) return 27;
		return 0;
	}

	public static java.util.List<MenuType<StationMenu>> all() {
		return java.util.List.of(storage, storage4, storage5, panel);
	}

	public static MenuType<FletchingMenu> fletching() {
		return fletching;
	}

	public static MenuType<StationMenu> panel() {
		return panel;
	}
}
