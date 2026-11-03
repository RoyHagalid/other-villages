package dev.villagerework.mixin;

import dev.villagerework.ui.ActionRow;
import dev.villagerework.ui.DockHost;
import dev.villagerework.ui.DockedStation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Smoker, blast furnace, brewing stand: links the docked station panel (and the brewing stand's action row) of the menu the block just opened to
 * the block's position, before the menu's contents are first sent to the player.
 */
@Mixin(BaseContainerBlockEntity.class)
public abstract class BaseContainerBlockEntityMixin {
	@Inject(method = "createMenu(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/inventory/AbstractContainerMenu;",
			at = @At("RETURN"))
	private void villagerework$dock(int id, Inventory inventory, Player player, CallbackInfoReturnable<AbstractContainerMenu> cir) {
		DockedStation dock = DockHost.of(cir.getReturnValue());
		BlockEntity self = (BlockEntity) (Object) this;
		ActionRow row = DockHost.rowOf(cir.getReturnValue());
		if (self.getLevel() instanceof ServerLevel level) {
			if (dock != null) dock.attach(level, self.getBlockPos());
			if (row != null) row.attach(level, self.getBlockPos());
		}
	}
}
