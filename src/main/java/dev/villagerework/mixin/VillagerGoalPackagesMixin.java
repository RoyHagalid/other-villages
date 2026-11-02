package dev.villagerework.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.villagerework.ai.GatedBehavior;
import dev.villagerework.config.VRConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Phase 2: villagers stop taking jobs on their own.
 *
 * Three vanilla behaviors in the villager's core AI are wrapped so they only run
 * while {@code features.disableVanillaJobClaiming} is false:
 *  - looking for a free workstation (the job-site AcquirePoi)
 *  - taking the profession of the workstation they reached (AssignProfessionFromJobSite)
 *  - losing their profession when their workstation is gone (ResetProfession)
 *
 * Blocking the last one is what keeps existing villagers' outfits as cosmetics.
 * WrapOperation (not Redirect) so this stacks with other mods, like Lithium.
 */
@Mixin(VillagerGoalPackages.class)
public abstract class VillagerGoalPackagesMixin {

	private static boolean vanillaJobsAllowed() {
		return !VRConfig.get().features.disableVanillaJobClaiming;
	}

	@WrapOperation(method = "getCorePackage", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ai/behavior/AcquirePoi;create(Ljava/util/function/Predicate;Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;ZLjava/util/Optional;Ljava/util/function/BiPredicate;)Lnet/minecraft/world/entity/ai/behavior/BehaviorControl;"))
	private static BehaviorControl<PathfinderMob> villagerework$gateJobSearch(
			Predicate<Holder<PoiType>> poiType, MemoryModuleType<GlobalPos> memory, MemoryModuleType<GlobalPos> potentialMemory,
			boolean onlyIfAdult, Optional<Byte> entityEvent, BiPredicate<ServerLevel, BlockPos> extraCheck,
			Operation<BehaviorControl<PathfinderMob>> original) {
		BehaviorControl<PathfinderMob> behavior = original.call(poiType, memory, potentialMemory, onlyIfAdult, entityEvent, extraCheck);
		return new GatedBehavior<>(behavior, VillagerGoalPackagesMixin::vanillaJobsAllowed);
	}

	@WrapOperation(method = "getCorePackage", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ai/behavior/AssignProfessionFromJobSite;create()Lnet/minecraft/world/entity/ai/behavior/BehaviorControl;"))
	private static BehaviorControl<Villager> villagerework$gateAssignProfession(Operation<BehaviorControl<Villager>> original) {
		return new GatedBehavior<>(original.call(), VillagerGoalPackagesMixin::vanillaJobsAllowed);
	}

	@WrapOperation(method = "getCorePackage", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ai/behavior/ResetProfession;create()Lnet/minecraft/world/entity/ai/behavior/BehaviorControl;"))
	private static BehaviorControl<Villager> villagerework$gateResetProfession(Operation<BehaviorControl<Villager>> original) {
		return new GatedBehavior<>(original.call(), VillagerGoalPackagesMixin::vanillaJobsAllowed);
	}

	/** 0.18.3: children in a miserable village don't play tag. */
	@WrapOperation(method = "getPlayPackage", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ai/behavior/PlayTagWithOtherKids;create()Lnet/minecraft/world/entity/ai/behavior/BehaviorControl;"))
	private static BehaviorControl<net.minecraft.world.entity.PathfinderMob> villagerework$gatePlay(
			Operation<BehaviorControl<net.minecraft.world.entity.PathfinderMob>> original) {
		return new dev.villagerework.ai.MoodGatedBehavior<>(original.call(),
				(level, kid) -> dev.villagerework.village.Prosperity.moodAt(level, kid.blockPosition()) > 0);
	}
}
