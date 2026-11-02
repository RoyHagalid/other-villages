package dev.villagerework.item;

import dev.villagerework.VillageRework;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.DispenserBlock;

/** The mod's own items. Registering items means the mod must be installed on the client too (it already is). */
public final class ModItems {
	private ModItems() {
	}

	public static Item EXPLOSIVE_ARROW;
	public static Item SONAR_MAP;

	public static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, VillageRework.id("explosive_arrow"));
		EXPLOSIVE_ARROW = Registry.register(BuiltInRegistries.ITEM, key, new ExplosiveArrowItem(new Item.Properties().setId(key)));
		DispenserBlock.registerProjectileBehavior(EXPLOSIVE_ARROW);
		ResourceKey<Item> sonar = ResourceKey.create(Registries.ITEM, VillageRework.id("sonar_map"));
		SONAR_MAP = Registry.register(BuiltInRegistries.ITEM, sonar, new SonarMapItem(new Item.Properties().setId(sonar)));
	}
}
