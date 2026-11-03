package dev.villagerework.mixin;

import dev.villagerework.village.StationTypes;
import dev.villagerework.village.Village;
import dev.villagerework.village.VillageRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Phase 5: hoppers see an active village station as its village inventory, like a furnace:
 * a hopper pointing into it fills the supply slots, a hopper underneath pulls the goods.
 *
 * This applies to every active village station (all thirteen professions since Phase 8).
 * While a barrel or composter is a village station, hoppers use the village slots instead
 * of the block's own storage; once it stops being a station, it's a normal block again.
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {

	@Inject(method = "getBlockContainer", at = @At("HEAD"), cancellable = true)
	private static void villagerework$stationContainer(Level level, BlockPos pos, BlockState state,
			CallbackInfoReturnable<Container> cir) {
		if (!(level instanceof ServerLevel serverLevel)) return;
		VillageRegistry registry = VillageRegistry.get(serverLevel);
		Optional<Village> village = registry.byStation(pos);
		if (village.isEmpty() || village.get().dormant()) return;
		village.get().professionAtStation(pos)
				.filter(profession -> StationTypes.get(profession).isPresent())
				.ifPresent(profession -> cir.setReturnValue(registry.inventory(village.get(), profession)));
	}
}
