package dev.villagerework.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.villagerework.ui.ActionRow;
import dev.villagerework.ui.DockHost;
import dev.villagerework.ui.DockedStation;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Buttons of a docked station panel (ids from DockedStation.BUTTON_BASE) go to the panel, not the vanilla menu. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@WrapOperation(method = "handleContainerButtonClick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;clickMenuButton(Lnet/minecraft/world/entity/player/Player;I)Z"))
	private boolean villagerework$dockButton(AbstractContainerMenu menu, Player player, int id, Operation<Boolean> original) {
		ActionRow row = DockHost.rowOf(menu);
		if (row != null && id == ActionRow.BUTTON_ID) return row.click(player);
		DockedStation dock = DockHost.of(menu);
		if (dock != null && id >= DockedStation.BUTTON_BASE) return dock.click(player, id - DockedStation.BUTTON_BASE);
		return original.call(menu, player, id);
	}
}
