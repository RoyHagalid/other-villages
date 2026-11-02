package dev.villagerework.village;

import net.minecraft.world.SimpleContainer;

/** The fisherman barrel's extra rows (see BarrelPerks). Reports changes so the village gets saved. */
public final class ExtraStorage extends SimpleContainer {
	private Runnable onChange = () -> {
	};

	ExtraStorage(int size) {
		super(size);
	}

	void setOnChange(Runnable onChange) {
		this.onChange = onChange;
	}

	@Override
	public void setChanged() {
		super.setChanged();
		onChange.run();
	}
}
