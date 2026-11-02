package dev.villagerework.client;

import net.minecraft.client.gui.navigation.ScreenPosition;

/** Smoker and blast furnace screens (AbstractRecipeBookScreenMixin): lets the docked panel close the recipe book. */
public interface RecipeBookHost {
	boolean villagerework$recipeBookOpen();

	void villagerework$toggleRecipeBook();

	int villagerework$recipeBookLeftPos(int width, int imageWidth);

	ScreenPosition villagerework$recipeBookButtonPosition();
}
