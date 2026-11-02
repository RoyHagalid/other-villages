package dev.villagerework.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets our code add slots and synced values to vanilla menus (the station perk level, the docked panel). */
@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuInvoker {
	@Invoker("addDataSlot")
	DataSlot villagerework$addDataSlot(DataSlot slot);

	@Invoker("addDataSlots")
	void villagerework$addDataSlots(ContainerData data);

	@Invoker("addSlot")
	Slot villagerework$addSlot(Slot slot);

	@Invoker("moveItemStackTo")
	boolean villagerework$moveItemStackTo(ItemStack stack, int start, int end, boolean reverse);

	@Invoker("clearContainer")
	void villagerework$clearContainer(Player player, Container container);
}
