package dev.villagerework.mixin;

import dev.villagerework.perk.LoomPerks;
import net.minecraft.world.item.crafting.BannerDuplicateRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Banners with more than 6 layers (from a shepherd's loom) can still be copied in the crafting grid. */
@Mixin(BannerDuplicateRecipe.class)
public abstract class BannerDuplicateRecipeMixin {
	@ModifyConstant(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z",
			constant = @Constant(intValue = 6))
	private int villagerework$matchLimit(int vanilla) {
		return LoomPerks.highest();
	}

	@ModifyConstant(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;",
			constant = @Constant(intValue = 6))
	private int villagerework$assembleLimit(int vanilla) {
		return LoomPerks.highest();
	}
}
