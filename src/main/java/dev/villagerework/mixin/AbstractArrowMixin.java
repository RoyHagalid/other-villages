package dev.villagerework.mixin;

import dev.villagerework.item.ExplosiveArrows;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Explosive arrows explode on impact instead of sticking or hurting (see ExplosiveArrows). */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin {
	@Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true)
	private void villagerework$explodeOnBlock(BlockHitResult hit, CallbackInfo ci) {
		AbstractArrow self = (AbstractArrow) (Object) this;
		if (self.level().isClientSide() || !ExplosiveArrows.isExplosive(self)) return;
		ExplosiveArrows.explode(self, hit.getLocation());
		ci.cancel();
	}

	@Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true)
	private void villagerework$explodeOnEntity(EntityHitResult hit, CallbackInfo ci) {
		AbstractArrow self = (AbstractArrow) (Object) this;
		if (self.level().isClientSide() || !ExplosiveArrows.isExplosive(self)) return;
		ExplosiveArrows.explode(self, hit.getLocation());
		ci.cancel();
	}
}
