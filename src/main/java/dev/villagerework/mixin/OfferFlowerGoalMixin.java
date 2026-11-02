package dev.villagerework.mixin;

import dev.villagerework.village.Prosperity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.OfferFlowerGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Iron golems handing poppies to villagers (0.18.3): in a content village about 4 times as often as vanilla, in an
 * abundant one about 10 times; in a miserable village never.
 */
@Mixin(OfferFlowerGoal.class)
public abstract class OfferFlowerGoalMixin {
	@Shadow
	@Final
	private IronGolem golem;

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void villagerework$noFlowersWhenMiserable(CallbackInfoReturnable<Boolean> cir) {
		if (golem.level() instanceof ServerLevel level && Prosperity.moodAt(level, golem.blockPosition()) == 0) {
			cir.setReturnValue(false);
		}
	}

	@ModifyConstant(method = "canUse", constant = @Constant(intValue = 8000))
	private int villagerework$flowerChance(int vanilla) {
		if (!(golem.level() instanceof ServerLevel level)) return vanilla;
		return switch (Prosperity.moodAt(level, golem.blockPosition())) {
			case 4 -> vanilla / 10;
			case 3 -> vanilla / 4;
			default -> vanilla;
		};
	}
}
