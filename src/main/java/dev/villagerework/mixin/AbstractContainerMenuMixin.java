package dev.villagerework.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.villagerework.ui.ActionRow;
import dev.villagerework.ui.DockHost;
import dev.villagerework.ui.DockedStation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets any menu carry a docked station panel (DockedStation) and a perk action row (ActionRow): shift-click
 * goes through them first, and the action row's temporary slots go back to the player when the menu closes.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin implements DockHost {
	@Unique
	private DockedStation villagerework$dock;

	@Override
	public DockedStation villagerework$dock() {
		return villagerework$dock;
	}

	@Override
	public void villagerework$setDock(DockedStation dock) {
		this.villagerework$dock = dock;
	}

	@Unique
	private ActionRow villagerework$actionRow;

	@Override
	public ActionRow villagerework$actionRow() {
		return villagerework$actionRow;
	}

	@Override
	public void villagerework$setActionRow(ActionRow row) {
		this.villagerework$actionRow = row;
	}

	@WrapOperation(method = "doClick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;quickMoveStack(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack villagerework$quickMove(AbstractContainerMenu menu, Player player, int index, Operation<ItemStack> original) {
		DockedStation dock = villagerework$dock;
		ActionRow row = villagerework$actionRow;
		Supplier<ItemStack> vanilla = () -> original.call(menu, player, index);
		Supplier<ItemStack> withRow = row == null ? vanilla : () -> row.quickMove(player, index, vanilla);
		return dock == null ? withRow.get() : dock.quickMove(player, index, withRow);
	}

	@Inject(method = "removed", at = @At("TAIL"))
	private void villagerework$removed(Player player, CallbackInfo ci) {
		if (villagerework$actionRow != null) villagerework$actionRow.removed(player);
	}
}
