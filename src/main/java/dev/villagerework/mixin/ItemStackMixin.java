package dev.villagerework.mixin;

import dev.villagerework.perk.Honing;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Honed weapons count down per hit, honed tools per block mined (server side; see Honing). */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@Inject(method = "postHurtEnemy", at = @At("HEAD"))
	private void villagerework$honedHit(LivingEntity target, LivingEntity attacker, CallbackInfo ci) {
		ItemStack self = (ItemStack) (Object) this;
		if (!attacker.level().isClientSide() && Honing.isWeapon(self) && Honing.usesLeft(self) > 0) Honing.use(self);
	}

	@Inject(method = "mineBlock", at = @At("HEAD"))
	private void villagerework$honedBlock(Level level, BlockState state, BlockPos pos, Player player, CallbackInfo ci) {
		ItemStack self = (ItemStack) (Object) this;
		if (!level.isClientSide() && Honing.isTool(self) && Honing.usesLeft(self) > 0) Honing.use(self);
	}
}
