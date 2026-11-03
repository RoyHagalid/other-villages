package dev.villagerework.mixin;

import dev.villagerework.ui.DockedStation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.crafting.RecipePropertySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Furnace, smoker, blast furnace: room for the docked station panel (shown only at a station). */
@Mixin(AbstractFurnaceMenu.class)
public abstract class AbstractFurnaceMenuMixin {
	@Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/world/inventory/RecipeBookType;ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/Container;Lnet/minecraft/world/inventory/ContainerData;)V",
			at = @At("TAIL"))
	private void villagerework$dock(MenuType<?> type, ResourceKey<RecipePropertySet> recipes, RecipeBookType book, int id,
			Inventory inventory, Container container, ContainerData data, CallbackInfo ci) {
		DockedStation.install((AbstractContainerMenu) (Object) this);
	}
}
