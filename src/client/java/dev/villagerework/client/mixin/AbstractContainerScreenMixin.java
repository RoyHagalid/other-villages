package dev.villagerework.client.mixin;

import dev.villagerework.client.ActionStrip;
import dev.villagerework.client.DockPanel;
import dev.villagerework.client.DockScreen;
import dev.villagerework.client.RecipeBookHost;
import dev.villagerework.ui.ActionRow;
import dev.villagerework.ui.DockHost;
import dev.villagerework.ui.DockedStation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla screens whose menu carries a docked station panel (DockedStation) or a perk action row (ActionRow):
 * draws them, adds their buttons, and keeps clicks on them from counting as clicks outside the screen.
 *
 * Placement of the panel: if it would go past the window's right edge, the whole screen moves left until it
 * fits (all vanilla widgets move along). Next to an open recipe book the screen can't move, so the panel folds
 * into a bell tab; the tab closes the recipe book.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin extends Screen implements DockScreen {
	@Shadow
	protected int leftPos;
	@Shadow
	protected int topPos;
	@Shadow
	@Final
	protected int imageWidth;
	@Shadow
	@Final
	protected AbstractContainerMenu menu;

	@Unique
	private DockPanel villagerework$panel;
	@Unique
	private ActionStrip villagerework$strip;
	/** How far we moved the screen left (negative), and the leftPos we left it at (to notice vanilla moving it). */
	@Unique
	private int villagerework$shift;
	@Unique
	private int villagerework$lastLeft;

	protected AbstractContainerScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void villagerework$init(CallbackInfo ci) {
		DockedStation dock = DockHost.of(menu);
		villagerework$panel = dock == null ? null
				: new DockPanel(dock, menu.containerId, b -> addRenderableWidget(b), () -> villagerework$closeRecipeBook());
		ActionRow row = DockHost.rowOf(menu);
		villagerework$strip = row == null ? null : new ActionStrip(row, menu.containerId, b -> addRenderableWidget(b));
		villagerework$shift = 0;
		villagerework$lastLeft = leftPos;
	}

	@Inject(method = "extractContents", at = @At("HEAD"))
	private void villagerework$drawPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (villagerework$panel != null) {
			villagerework$place();
			villagerework$panel.draw(graphics, font, leftPos, topPos, imageWidth, mouseX, mouseY);
		}
		if (villagerework$strip != null) villagerework$strip.draw(graphics, leftPos, topPos);
	}

	@Inject(method = "extractContents", at = @At("TAIL"))
	private void villagerework$drawTab(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (villagerework$panel != null) villagerework$panel.drawTabIcon(graphics);
	}

	/** Moves the screen left so the panel fits, or folds the panel next to an open recipe book. */
	@Unique
	private void villagerework$place() {
		if (leftPos != villagerework$lastLeft) villagerework$shift = 0; // vanilla placed the screen anew (recipe book)
		int base = leftPos - villagerework$shift;
		boolean bookOpen = (Object) this instanceof RecipeBookHost host && host.villagerework$recipeBookOpen();
		boolean fits = base + DockPanel.reach() <= width;
		int shift = 0;
		boolean collapsed = false;
		if (villagerework$panel.shown() && !fits) {
			if (bookOpen) collapsed = true;
			else shift = Math.min(0, Math.max(0, width - DockPanel.reach()) - base);
		}
		if (shift != villagerework$shift) {
			villagerework$panel.moveOthers(children(), shift - villagerework$shift);
			villagerework$shift = shift;
			leftPos = base + shift;
		}
		villagerework$lastLeft = leftPos;
		villagerework$panel.setCollapsed(collapsed);
	}

	/** The bell tab: closes the recipe book (the screen is placed again, and the panel comes back if it fits). */
	@Unique
	private void villagerework$closeRecipeBook() {
		if (!((Object) this instanceof RecipeBookHost host) || !host.villagerework$recipeBookOpen()) return;
		ScreenPosition before = host.villagerework$recipeBookButtonPosition();
		host.villagerework$toggleRecipeBook();
		leftPos = host.villagerework$recipeBookLeftPos(width, imageWidth);
		ScreenPosition after = host.villagerework$recipeBookButtonPosition();
		for (Object child : children()) {
			if (child instanceof AbstractWidget w && w.getX() == before.x() && w.getY() == before.y()
					&& !villagerework$panel.owns(w)) {
				w.setPosition(after.x(), after.y());
			}
		}
	}

	@Inject(method = "hasClickedOutside", at = @At("RETURN"), cancellable = true)
	private void villagerework$notOutside(double x, double y, int left, int top, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue() && villagerework$onPanel(x, y)) cir.setReturnValue(false);
	}

	@Override
	public boolean villagerework$onPanel(double x, double y) {
		return (villagerework$panel != null && villagerework$panel.contains(x, y, leftPos, topPos, imageWidth))
				|| (villagerework$strip != null && villagerework$strip.contains(x, y, leftPos, topPos));
	}
}
