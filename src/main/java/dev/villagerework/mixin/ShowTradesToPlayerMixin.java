package dev.villagerework.mixin;

import dev.villagerework.config.VRConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.ShowTradesToPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 0.20.3: villagers no longer hold up their trade goods when a player looks at them with an emerald or a trade
 * item in hand. That's vanilla's "come and trade" cue, and there's nothing to trade. Comes back with vanilla
 * trading ({@code features.disableVanillaTrading = false}) or {@code features.villagersShowTrades = true}.
 */
@Mixin(ShowTradesToPlayer.class)
public abstract class ShowTradesToPlayerMixin {

	@Inject(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/villager/Villager;)Z",
			at = @At("HEAD"), cancellable = true)
	private void villagerework$noTradeShowing(ServerLevel level, Villager villager, CallbackInfoReturnable<Boolean> cir) {
		VRConfig.Features cfg = VRConfig.get().features;
		if (cfg.disableVanillaTrading && !cfg.villagersShowTrades) cir.setReturnValue(false);
	}
}
