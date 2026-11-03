package dev.villagerework.perk;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.TrimMaterial;

import java.util.HashMap;
import java.util.Map;

/** Toolsmith's smithing table: finding the item a trim material comes from (for stripping a trim). */
public final class SmithingPerks {
	private SmithingPerks() {
	}

	private static Map<TrimMaterial, Item> materials;

	/** One of the item that provides {@code material} (e.g. a gold ingot), or EMPTY if none does. */
	public static ItemStack materialItem(Holder<TrimMaterial> material) {
		if (materials == null) {
			Map<TrimMaterial, Item> found = new HashMap<>();
			for (Item item : BuiltInRegistries.ITEM) {
				Holder<TrimMaterial> provides = new ItemStack(item).get(DataComponents.PROVIDES_TRIM_MATERIAL);
				if (provides != null) found.putIfAbsent(provides.value(), item);
			}
			materials = found;
		}
		Item item = materials.get(material.value());
		return item == null ? ItemStack.EMPTY : new ItemStack(item);
	}
}
