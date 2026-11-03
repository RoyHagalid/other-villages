package dev.villagerework.mixin;

import dev.villagerework.config.VRConfig;
import dev.villagerework.village.EmeraldDonations;
import dev.villagerework.village.Village;
import dev.villagerework.village.VillageRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Phase 2: no trade screen. Right-clicking a villager makes it look at you,
 * and tells you which village it lives in.
 *
 * Name tags and leads still work (vanilla handles them before this method runs),
 * and so do villager spawn eggs. The wandering trader is a different class and
 * keeps trading.
 */
@Mixin(Villager.class)
public abstract class VillagerMixin {

	@Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
	private void villagerework$noTrading(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (!VRConfig.get().features.disableVanillaTrading) return;

		Villager self = (Villager) (Object) this;
		if (player.getItemInHand(hand).is(Items.VILLAGER_SPAWN_EGG) || !self.isAlive() || self.isSleeping()) return;

		if (self.level() instanceof ServerLevel level && player instanceof ServerPlayer serverPlayer) {
			self.getBrain().setMemoryWithExpiry(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true), 60L);
			// Workers at a level II+ station sell their workstation as a take-home (0.17).
			if (!self.isBaby() && dev.villagerework.perk.TakeHomeShop.tryOpen(serverPlayer, self, level)) {
				cir.setReturnValue(InteractionResult.SUCCESS);
				return;
			}

			Optional<Village> village = VillageRegistry.get(level)
					.containing(self.blockPosition(), VRConfig.get().village.radius);
			// An unhappy village (0.18.3): the villager shakes its head with a "no".
			if (village.isPresent() && dev.villagerework.village.Prosperity.mood(level, village.get()) <= 1) {
				self.setUnhappyCounter(40);
				level.playSound(null, self.blockPosition(), net.minecraft.sounds.SoundEvents.VILLAGER_NO,
						net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
			}
			// 0.19.0: workers say what they do ("Farmer of Oakvale").
			Optional<String> job = self.isBaby() ? Optional.empty()
					: dev.villagerework.village.WorkforceManager.professionKey(self);
			Component message = village
					.map(v -> Component.literal(job.map(k -> dev.villagerework.village.StationManager.displayName(k) + " of ")
							.orElse("A resident of ") + v.name()).withStyle(ChatFormatting.GOLD))
					.orElse(Component.literal("A villager without a village").withStyle(ChatFormatting.GRAY));
			serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(message));
		}
		cir.setReturnValue(InteractionResult.SUCCESS);
	}

	/**
	 * 0.18.3: how much food a villager needs before it wants to breed follows the village's mood (vanilla 12):
	 * happier villages grow more easily, a miserable one not at all. The same amount is used up by breeding.
	 */
	@org.spongepowered.asm.mixin.injection.ModifyConstant(method = "canBreed",
			constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 12))
	private int villagerework$breedFood(int vanilla) {
		Villager self = (Villager) (Object) this;
		if (!(self.level() instanceof ServerLevel level)) return vanilla;
		int need = dev.villagerework.village.Prosperity.breedFood(level, self.blockPosition());
		return need < 0 ? Integer.MAX_VALUE : need;
	}

	@org.spongepowered.asm.mixin.injection.ModifyConstant(method = "eatAndDigestFood",
			constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 12))
	private int villagerework$breedFoodUsed(int vanilla) {
		Villager self = (Villager) (Object) this;
		if (!(self.level() instanceof ServerLevel level)) return vanilla;
		int need = dev.villagerework.village.Prosperity.breedFood(level, self.blockPosition());
		return need < 0 ? vanilla : need;
	}

	/** Phase 7: villagers inside a village want emeralds lying around (for the bell). */
	@Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
	private void villagerework$wantEmeralds(ServerLevel level, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		Villager self = (Villager) (Object) this;
		if (EmeraldDonations.wants(level, self, stack)) {
			cir.setReturnValue(true);
			return;
		}
		// 0.19.0: farmers leave bone meal alone (vanilla has them collect it), so it isn't taken from players.
		if (stack.is(Items.BONE_MEAL)
				&& self.getVillagerData().profession().is(net.minecraft.world.entity.npc.villager.VillagerProfession.FARMER)) {
			cir.setReturnValue(false);
		}
	}

	/** 0.19.0: villagers inside a village stop summoning golems once the village has enough (GolemCap). */
	@Inject(method = "spawnGolemIfNeeded", at = @At("HEAD"), cancellable = true)
	private void villagerework$golemCap(ServerLevel level, long timestamp, int villagersNeededToAgree, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (self.wantsToSpawnGolem(timestamp) && !dev.villagerework.village.GolemCap.allows(level, self)) ci.cancel();
	}

	/** Phase 7: a picked-up emerald goes to the village's bell, not the villager's inventory. */
	@Inject(method = "pickUpItem", at = @At("HEAD"), cancellable = true)
	private void villagerework$donateEmeralds(ServerLevel level, ItemEntity item, CallbackInfo ci) {
		if (EmeraldDonations.pickUp(level, (Villager) (Object) this, item)) ci.cancel();
	}
}
