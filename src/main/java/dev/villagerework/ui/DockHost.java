package dev.villagerework.ui;

/**
 * A menu that can carry a docked station panel and a perk action row (every menu, through
 * AbstractContainerMenuMixin; only some get them installed).
 */
public interface DockHost {
	DockedStation villagerework$dock();

	void villagerework$setDock(DockedStation dock);

	ActionRow villagerework$actionRow();

	void villagerework$setActionRow(ActionRow row);

	static DockedStation of(Object menu) {
		return menu instanceof DockHost host ? host.villagerework$dock() : null;
	}

	static ActionRow rowOf(Object menu) {
		return menu instanceof DockHost host ? host.villagerework$actionRow() : null;
	}
}
