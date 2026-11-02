package dev.villagerework.client.mixin;

import dev.villagerework.client.DockScreen;
import dev.villagerework.client.RecipeBookHost;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Smoker and blast furnace screens: their own outside-click check (for the recipe book) treats the docked panel
 * as inside, and the panel's bell tab can close the recipe book.
 */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin implements RecipeBookHost {
	@Shadow
	@Final
	private RecipeBookComponent<?> recipeBookComponent;

	@Shadow
	protected abstract ScreenPosition getRecipeBookButtonPosition();

	@Inject(method = "hasClickedOutside", at = @At("RETURN"), cancellable = true)
	private void villagerework$notOutside(double x, double y, int left, int top, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue() && ((DockScreen) this).villagerework$onPanel(x, y)) cir.setReturnValue(false);
	}

	@Override
	public boolean villagerework$recipeBookOpen() {
		return recipeBookComponent.isVisible();
	}

	@Override
	public void villagerework$toggleRecipeBook() {
		recipeBookComponent.toggleVisibility();
	}

	@Override
	public int villagerework$recipeBookLeftPos(int width, int imageWidth) {
		return recipeBookComponent.updateScreenPosition(width, imageWidth);
	}

	@Override
	public ScreenPosition villagerework$recipeBookButtonPosition() {
		return getRecipeBookButtonPosition();
	}
}
