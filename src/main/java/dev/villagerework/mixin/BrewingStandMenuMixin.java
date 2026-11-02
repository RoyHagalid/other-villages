package dev.villagerework.mixin;

import dev.villagerework.perk.Perks;
import dev.villagerework.ui.ActionRow;
import dev.villagerework.ui.DockedStation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Brewing stand: room for the docked station panel, and the cleric's Bottle XP row under the screen (level III). */
@Mixin(BrewingStandMenu.class)
public abstract class BrewingStandMenuMixin {
	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/Container;Lnet/minecraft/world/inventory/ContainerData;)V",
			at = @At("TAIL"))
	private void villagerework$dock(int id, Inventory inventory, Container container, ContainerData data, CallbackInfo ci) {
		AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
		DockedStation.install(menu);
		// Glass bottles shift-click into the Bottle XP row before the potion slots.
		ActionRow.install(menu, Perks.CLERIC, stack -> true);
	}
}
