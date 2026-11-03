package dev.villagerework.village;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The first {@code size} slots of another container (e.g. only the unlocked rows of a barrel's extra storage). */
public final class ContainerSlice implements Container {
	private final Container inner;
	private final int size;

	public ContainerSlice(Container inner, int size) {
		this.inner = inner;
		this.size = Math.min(size, inner.getContainerSize());
	}

	@Override
	public int getContainerSize() {
		return size;
	}

	@Override
	public boolean isEmpty() {
		for (int i = 0; i < size; i++) if (!inner.getItem(i).isEmpty()) return false;
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return slot < size ? inner.getItem(slot) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		return slot < size ? inner.removeItem(slot, count) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return slot < size ? inner.removeItemNoUpdate(slot) : ItemStack.EMPTY;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (slot < size) inner.setItem(slot, stack);
	}

	@Override
	public void setChanged() {
		inner.setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return inner.stillValid(player);
	}

	@Override
	public void clearContent() {
		for (int i = 0; i < size; i++) inner.setItem(i, ItemStack.EMPTY);
	}
}
