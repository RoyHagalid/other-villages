package dev.villagerework.client.mixin;

import dev.villagerework.client.DockScreen;
import dev.villagerework.perk.LoomMenuAccess;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The loom screen's "banner is full" check uses the station's layer limit (synced in the menu).
 *
 * 0.20.1: the loom has its own "clicked outside the screen" check, so the general fix in
 * AbstractContainerScreenMixin never ran for it. A click on the docked panel next to a slot counted as outside,
 * which throws the carried item out (one at a time with a right-click): the shepherd's rewards fell on the ground.
 */
@Mixin(LoomScreen.class)
public abstract class LoomScreenMixin {
	@ModifyConstant(method = "containerChanged", constant = @Constant(intValue = 6))
	private int villagerework$layerLimit(int vanilla) {
		Object menu = ((AbstractContainerScreen<?>) (Object) this).getMenu();
		return menu instanceof LoomMenuAccess loom ? loom.villagerework$maxLayers() : vanilla;
	}

	@Inject(method = "hasClickedOutside", at = @At("RETURN"), cancellable = true)
	private void villagerework$notOutside(double x, double y, int left, int top, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue() && ((DockScreen) this).villagerework$onPanel(x, y)) cir.setReturnValue(false);
	}
}
