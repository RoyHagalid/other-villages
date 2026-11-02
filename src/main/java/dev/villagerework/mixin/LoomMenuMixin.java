package dev.villagerework.mixin;

import dev.villagerework.perk.LoomMenuAccess;
import dev.villagerework.perk.LoomPerks;
import dev.villagerework.perk.Perks;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.LoomMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shepherd's loom: more banner layers at level II and III (see LoomPerks). */
@Mixin(LoomMenu.class)
public abstract class LoomMenuMixin implements LoomMenuAccess {
	@Unique
	private final DataSlot villagerework$maxLayers = DataSlot.standalone();

	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
			at = @At("TAIL"))
	private void villagerework$init(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
		int perk = access.evaluate((level, pos) -> Perks.level(level, pos, Perks.SHEPHERD)).orElse(0);
		villagerework$maxLayers.set(LoomPerks.maxLayers(perk));
		((AbstractContainerMenuInvoker) this).villagerework$addDataSlot(villagerework$maxLayers);
		// The village panel, docked to the right of the screen (shown only at a station).
		dev.villagerework.ui.DockedStation.install((net.minecraft.world.inventory.AbstractContainerMenu) (Object) this, access);
	}

	@ModifyConstant(method = "slotsChanged", constant = @Constant(intValue = 6))
	private int villagerework$layerLimit(int vanilla) {
		return villagerework$maxLayers();
	}

	@Override
	public int villagerework$maxLayers() {
		return Math.max(LoomPerks.VANILLA, villagerework$maxLayers.get());
	}
}
