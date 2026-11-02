package dev.villagerework.client;

/** A container screen that may show a docked station panel (every one, through AbstractContainerScreenMixin). */
public interface DockScreen {
	/** True if (x, y) is on the docked panel. */
	boolean villagerework$onPanel(double x, double y);
}
