package dev.villagerework.mixin;

import dev.villagerework.perk.Perks;
import dev.villagerework.perk.SmithingPerks;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Toolsmith's smithing table perks (cosmetic; trim templates are always used up when trimming, as in vanilla).
 *  - Level II, strip trim: trimmed armor alone in the middle slot gives the armor without its trim, and the
 *    trim's material comes back. The template doesn't.
 *  - Level III, re-trim: trimmed armor + a trim material, template slot empty, gives the same pattern in the
 *    new material. Uses up the material, no template.
 * Results are worked out on the server, like vanilla smithing.
 */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin extends ItemCombinerMenu {
	protected SmithingMenuMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access,
			ItemCombinerMenuSlotDefinition slots) {
		super(type, id, inventory, access, slots);
	}

	@Unique
	private int villagerework$perk() {
		return access.evaluate((level, pos) -> Perks.level(level, pos, Perks.TOOLSMITH)).orElse(0);
	}

	/** True when the slots hold trimmed armor alone (template and material slots empty). */
	@Unique
	private boolean villagerework$stripping() {
		return inputSlots.getItem(0).isEmpty() && inputSlots.getItem(2).isEmpty()
				&& inputSlots.getItem(1).get(DataComponents.TRIM) != null;
	}

	/** The village panel, docked to the right of the screen (shown only at a station). */
	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
			at = @At("TAIL"))
	private void villagerework$dock(int id, Inventory inventory, ContainerLevelAccess access,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		dev.villagerework.ui.DockedStation.install(this, access);
	}

	@Inject(method = "createResult", at = @At("TAIL"))
	private void villagerework$result(CallbackInfo ci) {
		if (!inputSlots.getItem(0).isEmpty()) return;
		ItemStack base = inputSlots.getItem(1);
		ArmorTrim trim = base.get(DataComponents.TRIM);
		if (trim == null) return;
		int perk = villagerework$perk();

		if (villagerework$stripping()) {
			if (perk < 2) return;
			ItemStack result = base.copyWithCount(1);
			result.remove(DataComponents.TRIM);
			resultSlots.setItem(0, result);
			return;
		}

		Holder<TrimMaterial> material = inputSlots.getItem(2).get(DataComponents.PROVIDES_TRIM_MATERIAL);
		if (perk < 3 || material == null || trim.material().value() == material.value()) return;
		ItemStack result = base.copyWithCount(1);
		result.set(DataComponents.TRIM, new ArmorTrim(material, trim.pattern()));
		resultSlots.setItem(0, result);
	}

	/** Stripping a trim gives its material back (server side; the template is gone). */
	@Inject(method = "onTake", at = @At("HEAD"))
	private void villagerework$materialBack(Player player, ItemStack taken, CallbackInfo ci) {
		if (!(player instanceof ServerPlayer) || !villagerework$stripping() || taken.get(DataComponents.TRIM) != null) return;
		ArmorTrim trim = inputSlots.getItem(1).get(DataComponents.TRIM);
		ItemStack material = trim == null ? ItemStack.EMPTY : SmithingPerks.materialItem(trim.material());
		if (material.isEmpty()) return;
		if (!player.getInventory().add(material) && !material.isEmpty()) {
			Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), material);
		}
	}
}
